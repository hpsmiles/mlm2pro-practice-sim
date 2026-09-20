package com.hpsmiles.golfsim.core.connect

import android.Manifest
import androidx.annotation.RequiresPermission
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.ble.Characteristic
import com.hpsmiles.golfsim.core.ble.Mlm2proDecoder
import com.hpsmiles.golfsim.core.ble.Mlm2proCrypto
import com.hpsmiles.golfsim.core.ble.BallDataResult
import com.hpsmiles.golfsim.core.ble.Mlm2proMessage
import com.hpsmiles.golfsim.core.ble.Mlm2proEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

    /** Lifecycle state surfaced to the UI (rendered in the range status strip). */
    sealed interface ConnectionState {
        data object Disconnected : ConnectionState
        data object Connecting : ConnectionState
        /** Handshake in progress (auth -> token -> config). */
        data object Handshaking : ConnectionState
        data object Armed : ConnectionState
        /** M4b FIX 2: sequencer reached DISARMED after a disarm write. */
        data object Disarmed : ConnectionState
        data class Faulted(val reason: String) : ConnectionState
    }

/**
 * Owns the GATT connection to a bonded MLM2PRO and drives
 * [HandshakeSequencer] with real device events.
 *
 * PROTOCOL FACTS (from the reverse-engineered spec, verify in Phase B):
 * - service UUID daf9b2a4-e4db-4be4-816d-298a050f25cd
 * - notify characteristics: EVENTS 02E525FD-…, HEARTBEAT EF6A028E-…,
 *   MEASUREMENT 76830BCE-…, WRITE_RESPONSE CFBBCB0D-…
 * - notifications are AES-encrypted with the session key, except
 *   WRITE_RESPONSE and possibly raw event payloads — [maybeDecrypt]
 *   routes each notification accordingly.
 */
class Mlm2proGattClient(
    // Plan deviation (mechanical): needs val — constructor params without
    // val/Var are invisible in member functions, and connect()/subscribe()
    // pass it to connectGatt().
    private val context: Context,
    private val sequencer: HandshakeSequencer,
    /** M4b notification capture the UI can enable/export (default off). */
    val captureLog: CaptureLog = CaptureLog(),
    /** M4b FIX 1 (spec §3e): token fetch provider (default HTTP). */
    private val tokenProvider: RapsodoTokenProvider = HttpRapsodoTokenProvider(),
    ) {

    private val gattQueue = GattOpQueue()

    /** FIX 1: scope for the async token fetch (cancelled on disconnect). */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** FIX 3: heartbeat/resubscribe poll ticker (started on services discovered). */
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    /** Fired for every decoded shot measurement. */
    var onMeasurement: ((BallData) -> Unit)? = null

    private var gatt: BluetoothGatt? = null
    private var clockMs: () -> Long = { System.currentTimeMillis() }

    // Lint contract (all @RequiresPermission members): the Phase B app layer
    // owns the runtime BLUETOOTH_CONNECT/BLUETOOTH_SCAN request; the :app
    // manifest declares both permissions. Annotations document the contract.
    @Suppress("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice) {
        _state.value = ConnectionState.Connecting
        gatt = device.connectGatt(
            context,
            /* autoConnect = */ false,
            object : BluetoothGattCallback() {
                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        _state.value = ConnectionState.Faulted("service discovery $status")
                        return
                    }
                    subscribe(g, EVENTS_UUID)
                    subscribe(g, HEARTBEAT_UUID)
                    subscribe(g, MEASUREMENT_UUID)
                    subscribe(g, WRITE_RESPONSE_UUID)
                    _state.value = ConnectionState.Handshaking
                    performWrite(sequencer.onSubscriptionsComplete(clockMs()))
                    startTicker()   // FIX 3: heartbeat + resubscribe cadence
                }

                override fun onDescriptorWrite(
                    g: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int,
                ) {
                    gattQueue.onOperationComplete()
                }

                override fun onCharacteristicWrite(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int,
                ) {
                    // Single surface at compileSdk 37: both the legacy 3-arg
                    // writeCharacteristic and the API-33 two-arg overload
                    // report through this callback.
                    gattQueue.onOperationComplete()
                }

                @Deprecated("Deprecated in Java")
                @Suppress("DEPRECATION")
                override fun onCharacteristicChanged(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    // API 31-32 fires this deprecated 2-arg variant only.
                    handleNotification(characteristic.uuid.toString(), characteristic.value ?: return)
                }

                override fun onCharacteristicChanged(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) {
                    handleNotification(characteristic.uuid.toString(), value)
                }
            },
        )
    }

    @Suppress("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        tickerJob?.cancel()
        tickerJob = null
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = ConnectionState.Disconnected
    }

    /**
     * FIX 3 (spec §3c): while connected, poll the sequencer every ~500 ms so
     * heartbeats (2 s cadence), the second CONFIG write (200 ms gap) and
     * resubscriptions (~20 s) fire from real time; resubscription re-writes
     * the EVENTS CCCD through the queue and refreshes the notification clock.
     */
    @Suppress("MissingPermission")
    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(500)
                val g = gatt ?: break
                sequencer.poll(clockMs()).forEach { performWrite(it) }
                if (sequencer.isResubscribeDue(clockMs())) {
                    val events = g.getService(java.util.UUID.fromString(SERVICE_UUID))
                        ?.getCharacteristic(java.util.UUID.fromString(EVENTS_UUID))
                    if (events != null) {
                        g.setCharacteristicNotification(events, true)
                        subscribe(g, EVENTS_UUID) // re-queues the CCCD write
                        sequencer.onNotification(clockMs())
                    }
                }
            }
        }
    }

    /**
     * FIX 2 (spec §3c): queue the ARM command. Valid only when the sequencer
     * is READY/DISARMED (guarded by [HandshakeSequencer.arm]); the LED state
     * flips to [ConnectionState.Armed] on the transition.
     */
    fun arm() {
        val write = sequencer.arm(clockMs()) ?: return
        performWrite(write)
        if (sequencer.state == HandshakeState.ARMED) {
            _state.value = ConnectionState.Armed
        }
    }

    /** FIX 2: queue the DISARM command (valid only from ARMED). */
    fun disarm() {
        val write = sequencer.disarm(clockMs()) ?: return
        performWrite(write)
        if (sequencer.state == HandshakeState.DISARMED) {
            _state.value = ConnectionState.Disarmed
        }
    }

    /** Route one decrypted/raw notification through the M1 decoder. */
    internal fun handleNotification(uuid: String, value: ByteArray) {
        // M4b capture: record the raw payload (decrypted variant recorded after
        // a successful decode below).
        captureLog.record(uuid = uuid.take(8).uppercase(), encrypted = value, decrypted = null)
        sequencer.onNotification(clockMs())
        // Plan deviation (mechanical): the M1 Characteristic enum deliberately
        // excludes WRITE_RESPONSE (M4 scope there), and adding an enum constant
        // would alter the frozen M1 decoder surface — so WRITE_RESPONSE is
        // routed by UUID string here; everything else goes through the enum.
        if (uuid.equals(WRITE_RESPONSE_UUID, ignoreCase = true)) {
            sequencer.onWriteResponse(maybeDecrypt(value, sessionKeyBytes), clockMs())
            // FIX 1 (spec §3e): on WRITE_RESPONSE accept the sequencer parks in
            // TOKEN_WAIT with the authed userId parsed — fetch the token async.
            val uid = sequencer.userId
            if (sequencer.state == HandshakeState.TOKEN_WAIT && uid >= 0) {
                scope.launch {
                    when (val result = tokenProvider.fetch(uid, SecretProvider.apiSecret())) {
                        is TokenResult.Success -> {
                            // A second WRITE_RESPONSE may have raced the fetch.
                            if (sequencer.state == HandshakeState.TOKEN_WAIT) {
                                sequencer.onToken(result.token, clockMs())?.let { performWrite(it) }
                            }
                        }
                        is TokenResult.Failure ->
                            _state.value = ConnectionState.Faulted("token fetch: ${result.reason}")
                    }
                }
            }
            sequencer.poll(clockMs()).forEach { performWrite(it) }
            return
        }
        val fromUuid = Characteristic.fromUuid(uuid) ?: return
        val plain = maybeDecrypt(value, sessionKeyBytes)
        val msg = Mlm2proDecoder.decode(fromUuid, value, sessionKeyBytes)
        // M4b capture: decode succeeded — record with the decrypted bytes.
        captureLog.record(uuid = uuid.take(8).uppercase(), encrypted = value, decrypted = plain)
        when (msg) {
            is Mlm2proMessage.Measurement ->
                (msg.result as? BallDataResult.Shot)?.let { onMeasurement?.invoke(it.data) }
            is Mlm2proMessage.Event ->
                if (msg.event is Mlm2proEvent.ShotDetected ||
                    msg.event is Mlm2proEvent.Ready
                ) _state.value = _state.value // state kept; sequencer owns protocol
            else -> Unit
        }
        sequencer.poll(clockMs()).forEach { performWrite(it) }
    }

    @Suppress("MissingPermission")
    private fun performWrite(write: WriteCommand) {
        val g = gatt ?: return
        val characteristic = when (write.target) {
            CommandTarget.AUTH_REQUEST -> findCharacteristic(g, AUTH_UUID)
            CommandTarget.COMMAND -> findCharacteristic(g, COMMAND_UUID)
            CommandTarget.CONFIGURE -> findCharacteristic(g, CONFIGURE_UUID)
            CommandTarget.HEARTBEAT -> findCharacteristic(g, HEARTBEAT_UUID)
        } ?: return
        // Serialized: Android allows one in-flight GATT operation; the queue
        // starts this write only after the previous operation's callback.
        gattQueue.enqueue {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(characteristic, write.plaintext, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = write.plaintext
                @Suppress("DEPRECATION")
                g.writeCharacteristic(characteristic)
            }
        }
    }

    @Suppress("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun subscribe(g: BluetoothGatt, uuid: String) {
        val characteristic = findCharacteristic(g, uuid) ?: return
        g.setCharacteristicNotification(characteristic, true)
        // CCCD (0x2902) ENABLE_NOTIFICATION descriptor write — queued so it
        // cannot overlap another in-flight operation.
        val descriptor = characteristic.getDescriptor(java.util.UUID.fromString(CCCD_UUID)) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gattQueue.enqueue {
                g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gattQueue.enqueue {
                @Suppress("DEPRECATION")
                g.writeDescriptor(descriptor)
            }
        }
    }

    private fun findCharacteristic(g: BluetoothGatt, uuid: String): BluetoothGattCharacteristic? =
        g.getService(java.util.UUID.fromString(SERVICE_UUID))
            ?.getCharacteristic(java.util.UUID.fromString(uuid))

    private var sessionKeyBytes: ByteArray = ByteArray(32)

    /** Session key generated per connection; the auth write carries it raw. */
    fun setSessionKey(key: ByteArray) {
        require(key.size == 32)
        sessionKeyBytes = key
    }

    companion object {
        const val SERVICE_UUID = "daf9b2a4-e4db-4be4-816d-298a050f25cd"
        const val EVENTS_UUID = "02e525fd-7960-4ef0-bfb7-de0f514518ff"
        const val HEARTBEAT_UUID = "ef6a028e-f78b-47a4-b56c-dda6dae85cbf"
        const val MEASUREMENT_UUID = "76830bce-b9a7-4f69-aeaa-fd5b9f6b0965"
        const val WRITE_RESPONSE_UUID = "cfbbcb0d-7121-4bc2-bf54-8284166d61f0"
        const val AUTH_UUID = "b1e9ce5b-48c8-4a28-89dd-12ffd779f5e1"
        const val COMMAND_UUID = "1ea0fa51-1649-4603-9c5f-59c940323471"
        const val CONFIGURE_UUID = "df5990cf-47fb-4115-8fdd-40061d40af84"
        const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        /**
         * BLE payloads from this device are usually AES-encrypted (multiple of
         * 16 bytes with the session key); raw ones (auth accept, unencrypted
         * events) pass through. An encrypted-looking blob that fails to decrypt
         * passes through raw so the decoder can report Malformed.
         * Pure function — unit tested in GattCryptoTest.
         */
        fun maybeDecrypt(payload: ByteArray, key: ByteArray): ByteArray {
            if (payload.isEmpty() || payload.size % 16 != 0) return payload
            return try {
                Mlm2proCrypto.decrypt(payload, key)
            } catch (ignored: java.security.GeneralSecurityException) {
                payload
            }
        }
    }
}
