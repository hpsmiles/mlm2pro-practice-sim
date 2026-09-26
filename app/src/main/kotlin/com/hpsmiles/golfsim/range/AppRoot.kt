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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import com.hpsmiles.golfsim.core.connect.ConnectionState
import com.hpsmiles.golfsim.core.connect.EnvironmentConfig
import com.hpsmiles.golfsim.core.connect.HandshakeSequencer
import com.hpsmiles.golfsim.core.connect.Mlm2proGattClient
import com.hpsmiles.golfsim.core.connect.Mlm2proScanner
import com.hpsmiles.golfsim.core.data.SessionRepository
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.NavRail
import com.hpsmiles.golfsim.core.designsystem.NavRailButton
import com.hpsmiles.golfsim.core.designsystem.StatusStrip
import com.hpsmiles.golfsim.history.HistoryScreen
import com.hpsmiles.golfsim.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private enum class RangeTab { RANGE, SETTINGS, HISTORY }

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

    // M5: persistence facade — one per composition. Bag seeding, DB probe and
    // open-session restore run once at startup; appends ride the existing
    // main-thread mediation (single-writer discipline, spec §7).
    val sessionRepository = remember { SessionRepository.open(context) }
    val activeClubStore = remember { ActiveClubStore(context) }
    var activeClubName by remember { mutableStateOf(activeClubStore.get()) }

    fun selectClub(name: String?) {
        activeClubName = name
        activeClubStore.set(name)
    }

    // Post-merge fix (final-review follow-up): the Room-backed getter-flow
    // captures below wait for this gate so they can never bind to a database
    // that restore's corrupt-file recovery is about to swap out.
    var repoReady by remember { mutableStateOf(false) }

    LaunchedEffect(sessionRepository) {
        try {
            val restored = sessionRepository.initializeAndRestore()
            if (restored != null) {
                val skipped = session.restore(restored)
                // Guard-skipped rows are persisted data we could not rebuild —
                // surfaced in logcat rather than dropped silently (Task 5b).
                if (skipped > 0) Log.w("AppRoot", "restore skipped $skipped invalid shot row(s)")
            }
        } finally {
            // Open the gate even if restore itself failed — a stuck-closed
            // gate would freeze History / picker / END SESSION forever.
            repoReady = true
        }
    }

    // M5: one gated club capture for all three tabs (replaces the three
    // per-tab captures). Also feeds the bag-miss guard and capture stamping.
    val clubRecords by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.clubs }, flowOf(emptyList()))
    }.collectAsState(initial = emptyList())

    // M5x E3/TRIGGER: if the active club is no longer in the bag — TEST club
    // purged by END SESSION, or a deletion — the active club falls back to
    // "—". An EMPTY bag is never a miss: on cold start the persisted club is
    // restored synchronously from SharedPreferences while clubRecords is
    // still the gated empty flow (repoReady awaits Room I/O), so treating it
    // as a miss would wipe the saved selection before the real bag arrives.
    // Genuine empty-bag paths (delete-active) already self-correct in the
    // rename/delete handlers before emission.
    LaunchedEffect(clubRecords) {
        if (clubRecords.isEmpty()) return@LaunchedEffect // gate closed / fresh install — not a miss
        val name = activeClubName ?: return@LaunchedEffect
        if (clubRecords.none { it.name == name }) selectClub(null)
    }

    // M5: persist every accepted shot. Fire-and-forget — the repository never
    // throws and flags StatusStrip via persistError (spec §8).
    fun persist(shot: DisplayShot?, source: ShotSource) {
        if (shot == null) return
        val club = activeClubName
        val clubWasTemp = clubRecords.find { it.name == club }?.isTemp ?: false
        scope.launch {
            sessionRepository.appendShot(shot.ballData, shot.shotResult, source, club, shot.timestampMs, clubWasTemp)
        }
    }

    // Live shot delivery: GATT callbacks fire on a binder thread; hop to main
    // before touching Compose snapshot state.
    DisposableEffect(gattClient) {
        gattClient.onMeasurement = { ballData ->
            scope.launch { persist(session.add(ballData), ShotSource.LIVE) }
        }
        gattClient.onMisread = {
            scope.launch {
                // Only the coalesce owner persists, or each mishit would
                // count twice in the DB (EVENTS + MEASUREMENT pair).
                if (session.markMisread()) sessionRepository.incrementMisread()
            }
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
        if (demo) persist(session.add(demoSource.nextShot()), ShotSource.DEMO)
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

    // M5 Task 9: history tab state.
    // summaries is a getter flow — remember-capture once (Task 4b), and only
    // after the restore gate opened (post-merge fix): captured earlier it
    // races corrupt-DB recovery and can die with the closed old database.
    val summaries by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.summaries }, flowOf(emptyList()))
    }.collectAsState(initial = emptyList())
    val liveOnly by sessionRepository.liveOnly.collectAsState()
    var historySelectedId by remember { mutableStateOf<Long?>(null) }
    val historyShots by remember(historySelectedId) {
        historySelectedId?.let { sessionRepository.observeShots(it) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Newest session is selected by default; an absent selection falls back
    // to newest so the detail pane never points at a ghost row.
    LaunchedEffect(summaries) {
        val ids = summaries.map { it.id }
        if (historySelectedId == null || historySelectedId !in ids) {
            historySelectedId = summaries.firstOrNull()?.id
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
                    NavRailButton("HISTORY", tab == RangeTab.HISTORY, onClick = { tab = RangeTab.HISTORY })

                    // M4d user request (2026-09-24, revised): FIRE / MODE /
                    // CONNECT live at the BOTTOM of the rail, under a flexible
                    // spacer; ARM/STANDBY + DISCONNECT follow beneath them.
                    Spacer(Modifier.weight(1f))
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

                    // ARM/STANDBY and DISCONNECT stay bottom-most, visible only
                    // when a link (or stale faulted handle) exists.
                    val armed = connectionState is ConnectionState.Armed
                    val linkVisible = connectionState is ConnectionState.Handshaking ||
                        armed || connectionState is ConnectionState.Disarmed ||
                        connectionState is ConnectionState.Faulted
                    if (linkVisible) {
                        Spacer(Modifier.height(4.dp))
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

                    // M5: explicit session end (spec D1). Non-destructive — the
                    // session stays fully browsable in HISTORY (Task 9).
                    // Getter flow (new Flow per access) — remember-capture it
                    // so collectAsState doesn't restart collection on every
                    // recomposition (Task 4b review finding). Gated behind the
                    // restore flag like the other getter captures (post-merge fix).
                    val hasOpenSession by remember(sessionRepository, repoReady) {
                        gatedFlow(repoReady, { sessionRepository.hasOpenSession }, flowOf(false))
                    }.collectAsState(false)
                    if (hasOpenSession) {
                        Spacer(Modifier.height(4.dp))
                        RailChip(
                            label = "END SESSION",
                            border = GolfColors.Amber,
                            labelColor = GolfColors.Amber,
                            onClick = { scope.launch { sessionRepository.endSession() } },
                        )
                    }
                }
                when (tab) {
                    RangeTab.RANGE -> {
                        RangeScreen(
                            Modifier.weight(1f),
                            session = session,
                            activeClubName = activeClubName,
                            clubs = clubRecords,
                            onSelectClub = ::selectClub,
                            onAddClub = sessionRepository::addClub,
                        )
                    }
                    RangeTab.SETTINGS -> {
                        SettingsScreen(
                            captureLog = captureLog,
                            clubs = clubRecords,
                            onAddClub = sessionRepository::addClub,
                            onRenameClub = { id, name ->
                                val renamed = sessionRepository.renameClub(id, name)
                                if (renamed) {
                                    // Renamed club was active → the pill follows
                                    // the new name; delete clears selection.
                                    val wasActive =
                                        clubRecords.find { it.id == id }?.name == activeClubName
                                    if (wasActive) selectClub(name.trim())
                                }
                                renamed
                            },
                            onDeleteClub = { id ->
                                val deletedName = clubRecords.find { it.id == id }?.name
                                scope.launch {
                                    sessionRepository.deleteClub(id)
                                    if (deletedName == activeClubName) selectClub(null)
                                }
                            },
                        )
                    }
                    RangeTab.HISTORY -> {
                        HistoryScreen(
                            Modifier.weight(1f),
                            summaries = summaries,
                            liveOnly = liveOnly,
                            onToggleLiveOnly = {
                                sessionRepository.liveOnly.value = !sessionRepository.liveOnly.value
                            },
                            shots = historyShots,
                            selectedSessionId = historySelectedId,
                            onSelectSession = { historySelectedId = it },
                            clubNames = clubRecords.map { it.name },
                            onRetag = { ids, club ->
                                scope.launch { sessionRepository.retagShots(ids, club) }
                            },
                            onRenameSession = { id, title ->
                                scope.launch { sessionRepository.renameSession(id, title) }
                            },
                        )
                    }
                }
            }
            val persistError by sessionRepository.persistError.collectAsState()
            StatusStrip(
                armed = demo || connectionState is ConnectionState.Armed,
                info = if (persistError) "DB WRITE FAILING" else describe(connectionState, demo = demo, scanning = scanning),
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
            .width(GolfSpacing.NavRailWidth - GolfSpacing.Sm * 2)
            .border(1.dp, border, RoundedCornerShape(GolfSpacing.Sm))
            .clickable(onClick = onClick)
            .padding(vertical = GolfSpacing.Sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = GolfTypography.Status.copy(fontSize = 15.sp),
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

/**
 * Cold-start flow gate (post-M5 final-review follow-up): the Room-backed
 * getter flows are captured from the first composition frame, racing the
 * restore effect — a corrupt-DB recovery that swaps the database leaves
 * captured flows bound to the closed old DB, so History / club picker /
 * END SESSION stay empty until the next restart. The getter stays untouched
 * ([live] is a deferred producer) until [ready]; the pre-restore window
 * serves [fallback]. Internal so the unit test can pin the deferral.
 */
internal fun <T> gatedFlow(ready: Boolean, live: () -> Flow<T>, fallback: Flow<T>): Flow<T> =
    if (ready) live() else fallback
