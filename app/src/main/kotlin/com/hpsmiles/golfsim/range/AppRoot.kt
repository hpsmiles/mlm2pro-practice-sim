// app/src/main/kotlin/com/hpsmiles/golfsim/range/AppRoot.kt
package com.hpsmiles.golfsim.range

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    var scanning by remember { mutableStateOf(false) }
    // MODE toggle state lives here so the StatusStrip demo fallback mirrors it.
    var demo by remember { mutableStateOf(true) }

    // M4b bench connect flow: scan for the first MLM2- device, then GATT-connect.
    // Mlm2proScanner stops scanning when the collecting coroutine is cancelled.
    // Lint contract: RangeScreen's CONNECT button checks/request both runtime
    // permissions before this fires; the :app manifest declares SCAN+CONNECT.
    @Suppress("MissingPermission")
    fun onConnectRequested() {
        if (scanning) return
        scanning = true
        scope.launch {
            try {
                val device = Mlm2proScanner(context).scan().first()
                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                gattClient.connect(manager.adapter.getRemoteDevice(device.address))
            } catch (ignored: Exception) {
                // scan cancelled or no adapter — status text falls back to describe().
            } finally {
                scanning = false
            }
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
                }
                when (tab) {
                    RangeTab.RANGE -> RangeScreen(
                        Modifier.weight(1f),
                        demo = demo,
                        onDemoChanged = { demo = it },
                        onConnectRequested = { onConnectRequested() },
                    )
                    RangeTab.SETTINGS -> SettingsScreen(captureLog = captureLog)
                }
            }
            // M4b FIX 2: ARM/STANDBY control — visible once the handshake has
            // subscriptions+auth complete (Handshaking) and through Armed/Disarmed.
            val armed = connectionState is ConnectionState.Armed
            if (connectionState is ConnectionState.Handshaking ||
                armed || connectionState is ConnectionState.Disarmed
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = GolfSpacing.Md, vertical = GolfSpacing.Xs),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = if (armed) "STANDBY" else "ARM",
                        color = if (armed) GolfColors.TextSecondary else GolfColors.TextPrimary,
                        style = GolfTypography.MetricLabel,
                        modifier = Modifier
                            .border(1.dp, GolfColors.Teal, RoundedCornerShape(50))
                            .clickable {
                                if (armed) gattClient.disarm() else gattClient.arm()
                            }
                            .padding(horizontal = GolfSpacing.Lg, vertical = GolfSpacing.Sm),
                    )
                }
            }
            StatusStrip(
                armed = demo || connectionState is ConnectionState.Armed,
                info = describe(connectionState, demo = demo, scanning = scanning),
            )
        }
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
