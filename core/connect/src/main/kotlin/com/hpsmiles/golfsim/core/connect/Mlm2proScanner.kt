package com.hpsmiles.golfsim.core.connect

import android.Manifest
import androidx.annotation.RequiresPermission
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A discovered MLM2PRO. `name` includes the MLM2- prefix. */
data class Mlm2proDevice(val name: String, val address: String, val rssi: Int)

/**
 * Scans for MLM2PRO launch monitors (BLE names prefixed "MLM2-").
 * Thin Android wrapper; filtering/found-dedup logic lives with callers.
 * Requires BLUETOOTH_SCAN (neverForLocation) — declared in the :app manifest.
 */
class Mlm2proScanner(private val context: Context) {

    // Lint contract: the Phase B app layer owns the runtime BLUETOOTH_SCAN/
    // BLUETOOTH_CONNECT request; the :app manifest declares both permissions.
    // These annotations are permission documentation, not runtime guards.
    @Suppress("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scan(): Flow<Mlm2proDevice> = callbackFlow {
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: throw IllegalStateException("no Bluetooth adapter")
        val scanner = adapter.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth is off")
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: return
                if (!name.startsWith(NAME_PREFIX)) return
                trySend(Mlm2proDevice(name, result.device.address, result.rssi))
            }
        }
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
            == PackageManager.PERMISSION_GRANTED
        ) {
            scanner.startScan(filters, settings, callback)
        }
        awaitClose { try { scanner.stopScan(callback) } catch (ignored: Exception) { } }
    }

    companion object {
        const val NAME_PREFIX = "MLM2-"
        const val SERVICE_UUID = "daf9b2a4-e4db-4be4-816d-298a050f25cd"
    }
}
