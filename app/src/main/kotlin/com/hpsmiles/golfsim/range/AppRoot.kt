// app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
package com.hpsmiles.golfsim.range

import android.bluetooth.BluetoothManager
import android.content.Context
import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hpsmiles.golfsim.core.connect.ConnectionState
import com.hpsmiles.golfsim.core.connect.EnvironmentConfig
import com.hpsmiles.golfsim.core.connect.HandshakeSequencer
import com.hpsmiles.golfsim.core.connect.Mlm2proGattClient
import com.hpsmiles.golfsim.core.connect.Mlm2proScanner
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.NavRail
import com.hpsmiles.golfsim.core.designsystem.NavRailButton
import com.hpsmiles.golfsim.core.designsystem.StatusStrip
import com.hpsmiles.golfsim.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class RangeTab { RANGE, SETTINGS }

/** The bench device session key convention (same bytes the auth write carries raw). */
private fun benchSessionKey(): ByteArray = ByteArray(32) { it.toByte() }

@Composable
fun AppRoot() {
    val context = LocalContext.current
    // FIX 5: hoisted so SettingsScreen can toggle/export notification capture.
    val captureLog = remember { com.hpsmiles.golfsim.core.connect.CaptureLog() }
    val gattClient = remember(captureLog) {
        Mlm2proGattClient(context, HandshakeSequencer(benchSessionKey(), EnvironmentConfig()), captureLog)
            .apply { setSessionKey(benchSessionKey()) }
    }
    val connectionState by gattClient.state.collectAsState()
    val scope = rememberCoroutineScope()
    // M4d: shared Range shot list + no-read counter, owned at the composition
    // root so demo (RangeScreen fire) and live BLE callbacks append to one list.
    val session = remember { RangeSession() }

    // Live shot delivery: GATT callbacks fire on a binder thread; hop to main
    // before touching Compose snapshot state.
    DisposableEffect(gattClient) {
        gattClient.onMeasurement = { ballData ->
            scope.launch { session.add(ballData) }
        }
        gattClient.onMisread = {
            scope.launch { session.markMisread() }
        }
        onDispose {
            gattClient.onMeasurement = null
            gattClient.onMisread = null
        }
    }

    var scanning by remember { mutableStateOf(false) }
    // MODE toggle state lives here so the StatusStrip demo fallback mirrors it.
    var demo by remember { mutableStateOf(true) }
    val demoSource = remember { com.hpsmiles.golfsim.core.ble.DemoShotSource() }

    // FIRE lives in the rail (2026-09-24 user request); demo firing appends to
    // the shared session exactly like RangeScreen did.
    fun fireDemo() {
        if (demo) session.add(demoSource.nextShot())
    }

    // BLE permission gate hoisted from RangeScreen (CONNECT moved to the rail).
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionDenied = grants.values.any { !it }
    }

    // M4b bench connect flow: scan for the first MLM2- device, then GATT-connect.
    // Mlm2proScanner stops scanning when the collecting coroutine is cancelled.
    // Lint contract: RangeScreen's CONNECT button checks/request both runtime
    // permissions before this fires; the :app manifest declares SCAN+CONNECT.
    @Suppress("MissingPermission")
    fun onConnectRequested() {
        if (scanning) return
        // Bench finding (double-tap during handshake): the `scanning` flag
        // resets as soon as connect() returns, so a second tap mid-handshake
        // used to start a second scan + GATT link. Refuse unless the client
        // is actually down (Disconnected) or recoverable (Faulted).
        val s = connectionState
        if (s != ConnectionState.Disconnected && s !is ConnectionState.Faulted) return
        scanning = true
        scope.launch {
            try {
                val device = Mlm2proScanner(context).scan().first()
                Log.i(Mlm2proGattClient.TAG, "scan found device=${device.address}")
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                gattClient.connect(manager.adapter.getRemoteDevice(device.address))
            } catch (e: Exception) {
                // Bench diagnostics (attempt 3): this catch previously swallowed
                // every scan/connect-flow failure silently, leaving the strip
                // stuck on CONNECTING with no reason.
                Log.w(Mlm2proGattClient.TAG, "connect flow failed", e)
                gattClient.reportFault(e.javaClass.simpleName)
            } finally {
                scanning = false
            }
        }
    }

    // BLE permission gate for the rail CONNECT chip (hoisted from RangeScreen).
    fun connectTapped() {
        val scanGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_SCAN,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val connectGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (scanGranted && connectGranted) {
            permissionDenied = false
            onConnectRequested()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                ),
            )
        }
    }

    var tab by remember { mutableStateOf(RangeTab.RANGE) }
    GolfTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GolfColors.Base)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(modifier = Modifier.weight(1f)) {
                NavRail {
                    NavRailButton("RANGE", tab == RangeTab.RANGE, onClick = { tab = RangeTab.RANGE })
                    NavRailButton("SETTINGS", tab == RangeTab.SETTINGS, onClick = { tab = RangeTab.SETTINGS })
                    // M4d user request (2026-09-24): FIRE / MODE / CONNECT sit
                    // vertically under RANGE / SETTINGS in the same rail column;
                    // the range canvas is bordered by exactly one left column.
                    RailChip("FIRE", border = GolfColors.Amber, labelColor = GolfColors.Amber,
                        onClick = { fireDemo() })
                    RailChip(
                        label = if (demo) "MODE: DEMO" else "MODE: LIVE",
                        border = if (demo) GolfColors.Line else GolfColors.Teal,
                        onClick = { demo = !demo },
                    )
                    RailChip("CONNECT", border = GolfColors.Line, onClick = { connectTapped() })
                    if (permissionDenied) {
                        RailChip("NO PERM", border = GolfColors.AlertRed,
                            labelColor = GolfColors.AlertRed, onClick = {})
                    }

                    // M4d user request (2026-09-24): ARM/STANDBY and DISCONNECT
                    // live in the LEFT panel (bottom of the rail) instead of a
                    // floating bottom row. Visible whenever a link (or stale
                    // faulted handle) exists so the user can tear it down.
                    val armed = connectionState is ConnectionState.Armed
                    val linkVisible = connectionState is ConnectionState.Handshaking ||
                        armed || connectionState is ConnectionState.Disarmed ||
                        connectionState is ConnectionState.Faulted
                    if (linkVisible) {
                        Spacer(Modifier.weight(1f))
                        RailChip(
                            label = if (armed) "STANDBY" else "ARM",
                            border = GolfColors.Teal,
                            onClick = { if (armed) gattClient.disarm() else gattClient.arm() },
                        )
                        RailChip(
                            label = "DISCONNECT",
                            border = GolfColors.Line,
                            onClick = { disconnectClient(gattClient) },
                        )
                    }
                }
                when (tab) {
                    RangeTab.RANGE -> RangeScreen(Modifier.weight(1f), session = session)
                    RangeTab.SETTINGS -> SettingsScreen(captureLog = captureLog)
                }
            }
            StatusStrip(
                armed = demo || connectionState is ConnectionState.Armed,
                info = describe(connectionState, demo = demo, scanning = scanning),
            )
        }
    }
}

/**
 * DISCONNECT chip handler. The runtime BLUETOOTH_CONNECT permission is
 * requested by RangeScreen's CONNECT flow before any link exists; a live link
 * therefore implies the permission was granted for this process lifetime.
 * Local suppression keeps the lint contract documented at the call site.
 */
@SuppressLint("MissingPermission")
private fun disconnectClient(gattClient: Mlm2proGattClient) {
    gattClient.disconnect()
}

/**
 * Control chip for the left NavRail (ARM/STANDBY, DISCONNECT). 56 dp wide to
 * match the rail button boxes, centered two-line-tolerant label.
 */
@Composable
private fun RailChip(
    label: String,
    border: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    labelColor: androidx.compose.ui.graphics.Color = GolfColors.TextPrimary,
) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .border(1.dp, border, RoundedCornerShape(GolfSpacing.Sm))
            .clickable(onClick = onClick)
            .padding(vertical = GolfSpacing.Xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = GolfTypography.Status,
            color = labelColor,
            textAlign = TextAlign.Center,
        )
    }
}

/** StatusStrip text mapping per the M4b plan (demo fallback first). */
private fun describe(state: ConnectionState, demo: Boolean, scanning: Boolean): String = when {
    demo -> "DEMO MODE - FIRE TO SHOOT"
    scanning -> "SCANNING\u2026"
    else -> when (state) {
        ConnectionState.Disconnected -> "BLE DISCONNECTED"
        ConnectionState.Connecting -> "CONNECTING\u2026"
        ConnectionState.Handshaking -> "HANDSHAKING\u2026"
        ConnectionState.Armed -> "ARMED"
        ConnectionState.Disarmed -> "DISARMED - STANDBY"
        is ConnectionState.Faulted -> "BLE FAULTED - SEE CAPTURE"
    }
}
