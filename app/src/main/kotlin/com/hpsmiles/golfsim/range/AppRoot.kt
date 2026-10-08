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
import com.hpsmiles.golfsim.audio.GameAudio
import com.hpsmiles.golfsim.bag.BagMappingCollector
import com.hpsmiles.golfsim.bag.BagMappingScreen
import com.hpsmiles.golfsim.bag.BagOrderMode
import com.hpsmiles.golfsim.bag.BagPlanClub
import com.hpsmiles.golfsim.bag.ClubQualityGate
import com.hpsmiles.golfsim.bag.orderPlan
import com.hpsmiles.golfsim.core.connect.AutoConnectPolicy
import com.hpsmiles.golfsim.core.connect.ConnectionState
import com.hpsmiles.golfsim.core.connect.EnvironmentConfig
import com.hpsmiles.golfsim.core.connect.HandshakeSequencer
import com.hpsmiles.golfsim.core.connect.Mlm2proGattClient
import com.hpsmiles.golfsim.core.connect.Mlm2proScanner
import com.hpsmiles.golfsim.core.data.SessionRepository
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.designsystem.GolfColors
import com.hpsmiles.golfsim.core.designsystem.GolfSpacing
import com.hpsmiles.golfsim.core.designsystem.GolfTheme
import com.hpsmiles.golfsim.core.designsystem.GolfTypography
import com.hpsmiles.golfsim.core.designsystem.NavRail
import com.hpsmiles.golfsim.core.designsystem.NavRailButton
import com.hpsmiles.golfsim.core.designsystem.StatusStrip
import com.hpsmiles.golfsim.core.physics.GreenCondition
import com.hpsmiles.golfsim.core.physics.TurfCondition
import com.hpsmiles.golfsim.fitting.FittingController
import com.hpsmiles.golfsim.fitting.FittingPdfData
import com.hpsmiles.golfsim.fitting.FittingPdfWriter
import com.hpsmiles.golfsim.fitting.FittingScreen
import com.hpsmiles.golfsim.fitting.FittingView
import com.hpsmiles.golfsim.games.BreakThePaneGame
import com.hpsmiles.golfsim.games.GameMode
import com.hpsmiles.golfsim.games.GameRecord
import com.hpsmiles.golfsim.games.GameResultPayload
import com.hpsmiles.golfsim.games.GameLeavePolicy
import com.hpsmiles.golfsim.games.GamesScreen
import com.hpsmiles.golfsim.games.RecordComparison
import com.hpsmiles.golfsim.games.TargetPracticeGame
import com.hpsmiles.golfsim.history.HistoryScreen
import com.hpsmiles.golfsim.settings.GreenConditionStore
import com.hpsmiles.golfsim.settings.SettingsScreen
import com.hpsmiles.golfsim.settings.SoundPrefStore
import com.hpsmiles.golfsim.settings.TurfConditionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private enum class RangeTab { RANGE, GAMES, BAG, FIT, SETTINGS, HISTORY }

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
    val batteryPercent by gattClient.batteryPercent.collectAsState()
    val scope = rememberCoroutineScope()
    // M4d: shared Range shot list + no-read counter, owned at the composition
    // root so demo (RangeScreen fire) and live BLE callbacks append to one list.
    val session = remember { RangeSession() }

    // M5.5: tab + game mode hoisted above the shot dispatcher so
    // onMeasurement/fireDemo can route before first composition.
    var tab by remember { mutableStateOf(RangeTab.RANGE) }
    val targetPractice = remember { TargetPracticeGame() }
    val breakPane = remember { BreakThePaneGame() }
    var activeGame by remember { mutableStateOf(GameMode.NONE) }

    // M6: bag mapping. collector is session-ephemeral guidance state; the
    // shots are Room truth (write-through persisted, resume-after-kill).
    val bagCollector = remember { BagMappingCollector() }
    var bagCollecting by remember { mutableStateOf(false) }
    var bagViewedSessionId by remember { mutableStateOf<Long?>(null) }
    // M6: the latest accepted bag shot, rendered on the bag collecting view's
    // live range tracer. Display-only — bag shots never enter session.shots.
    var bagDisplayShot by remember { mutableStateOf<DisplayShot?>(null) }

    // M7: club fitting. Same architecture as bag mapping — Room truth, plain
    // UI state, write-through per shot, never enters range sessions.
    // fitSession is a separate RangeSession instance used ONLY for physics +
    // live display; its shots never reach persist().
    val fittingController = remember { FittingController() }
    val fitSession = remember { RangeSession() }
    // M7 PDF export (2026-10-08): re-tap guard while a render is in flight.
    var exportingPdf by remember { mutableStateOf(false) }

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

    val greenConditionStore = remember { GreenConditionStore(context) }
    var greenCondition by remember { mutableStateOf(greenConditionStore.get()) }

    fun selectGreenCondition(condition: GreenCondition) {
        greenCondition = condition
        greenConditionStore.set(condition)
    }

    val turfConditionStore = remember { TurfConditionStore(context) }
    var turfCondition by remember { mutableStateOf(turfConditionStore.get()) }

    fun selectTurfCondition(condition: TurfCondition) {
        turfCondition = condition
        turfConditionStore.set(condition)
    }

    val gameAudio = remember { GameAudio(context) }
    DisposableEffect(Unit) {
        onDispose { gameAudio.release() }
    }

    val soundPrefStore = remember { SoundPrefStore(context) }
    var soundsEnabled by remember { mutableStateOf(soundPrefStore.get()) }
    LaunchedEffect(soundsEnabled) { gameAudio.enabled = soundsEnabled }

    fun selectSoundsEnabled(enabled: Boolean) {
        soundsEnabled = enabled
        soundPrefStore.set(enabled)
    }

    // Keep the simulated surfaces in sync with Settings > GREEN / TURF for the
    // range oval and both games (their default simulators read these fields).
    // M7 (task 5 review): fitSession gets the same sync so FIT physics match
    // the user's surfaces exactly like RANGE.
    LaunchedEffect(greenCondition) {
        val surface = greenCondition.surface()
        session.greenSurface = surface
        targetPractice.greenSurface = surface
        breakPane.greenSurface = surface
        fitSession.greenSurface = surface
    }
    LaunchedEffect(turfCondition) {
        val surface = turfCondition.surface()
        session.turfSurface = surface
        targetPractice.fairwaySurface = surface
        breakPane.fairwaySurface = surface
        fitSession.turfSurface = surface
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

    /**
     * 2026-09-30: a game just completed. Snapshot the PRIOR best for the
     * record key, publish the comparison to the game holder, then persist the
     * new row. The query must precede the insert or it reads its own write.
     */
    fun completeGame(
        payload: GameResultPayload,
        onRecord: (RecordComparison) -> Unit,
        clockMs: () -> Long,
        source: ShotSource,
    ) {
        val lowerIsBetter = GameRecord.lowerIsBetter(payload.mode)
        val difficulty = payload.difficulty
        scope.launch {
            if (difficulty != null) {
                val prevBest = sessionRepository.bestGameScore(payload.mode, difficulty, payload.targetM, lowerIsBetter)
                onRecord(GameRecord.compare(prevBest, payload.score, lowerIsBetter))
            }
            sessionRepository.saveGameResult(
                payload.mode, payload.difficulty, payload.targetM, payload.score, source, clockMs(),
            )
        }
    }

    /**
     * M5.5 dispatch (spec §3): a shot goes to the active game OR the range
     * session — never both. Game shots are never range-persisted; completed
     * games emit exactly one summary row via takeResult().
     */
    fun routeShot(ballData: com.hpsmiles.golfsim.core.ble.BallData, source: com.hpsmiles.golfsim.core.data.record.ShotSource) {
        // M7: fitting captures whenever the FIT tab is up (any sub-view) —
        // one comparison per session; shots never enter range sessions and
        // are write-through persisted to fitting_shots with a club snapshot.
        if (tab == RangeTab.FIT) {
            val club = clubRecords.find { it.name == activeClubName }
            if (club == null) return  // no active club → nothing to compare
            val shot = fitSession.add(ballData) ?: return
            scope.launch {
                // M7 (task 5 review): sessionId is bound from Room by a
                // LaunchedEffect — never written back from here, so a late
                // append can never re-bind a COMPLETED session after END
                // COMPARISON.
                val id = fittingController.sessionId.takeIf { it > 0 }
                    ?: sessionRepository.startFittingSession(System.currentTimeMillis())
                if (id != null) {
                    sessionRepository.appendFittingShot(id, club, ballData, shot.shotResult, shot.timestampMs)
                }
            }
            return
        }
        // M6: mapping collection captures ONLY while the guided COLLECTING
        // view is up on the BAG tab (spec §4). Leaving the tab pauses
        // capture; progress persists and the range keeps its own shots.
        if (tab == RangeTab.BAG && bagCollecting) {
            val shot = bagCollector.add(ballData)
            if (shot != null) {
                val sessionId = bagCollector.sessionId
                scope.launch {
                    if (sessionId > 0) {
                        sessionRepository.appendBagMappingShot(
                            sessionId, shot.clubName, shot.clubType, shot.ballData,
                            shot.carryM, shot.totalM, shot.timestampMs,
                        )
                    }
                }
                // Display-only: render this shot on the bag view's tracer
                // immediately (Room persistence rides the coroutine above).
                bagDisplayShot = DisplayShot(
                    shot.ballData, shot.launch, shot.result, timestampMs = shot.timestampMs,
                )
            }
            return
        }
        when (activeGame) {
            GameMode.NONE -> persist(session.add(ballData), source)
            GameMode.TARGET_PRACTICE -> {
                targetPractice.add(ballData)
                targetPractice.takeResult()?.let {
                    completeGame(it, targetPractice::setRecord, { targetPractice.clockMs() }, source)
                }
            }
            GameMode.BREAK_PANE -> {
                breakPane.add(ballData)
                breakPane.takeResult()?.let {
                    completeGame(it, breakPane::setRecord, { breakPane.clockMs() }, source)
                }
            }
        }
    }

    // Live shot delivery: GATT callbacks fire on a binder thread; hop to main
    // before touching Compose snapshot state.
    DisposableEffect(gattClient) {
        gattClient.onMeasurement = { ballData ->
            scope.launch { routeShot(ballData, ShotSource.LIVE) }
        }
        gattClient.onMisread = {
            scope.launch {
                // M6: while collecting, a mishit feeds the guided "no read"
                // pill (in-memory, coalesced) and never the range session.
                if (tab == RangeTab.BAG && bagCollecting) {
                    bagCollector.markNoRead()
                } else if (session.markMisread()) sessionRepository.incrementMisread()
            }
        }
        onDispose {
            gattClient.onMeasurement = null
            gattClient.onMisread = null
        }
    }

    var scanning by remember { mutableStateOf(false) }
    // MODE toggle state lives here so the StatusStrip demo fallback mirrors it.
    // Auto-connect boot (2026-09-30 spec §1): the app boots LIVE, connects and
    // auto-arms on its own; MODE: DEMO is an explicit opt-in for the FIRE
    // shot simulator.
    var demo by remember { mutableStateOf(false) }
    val demoSource = remember { com.hpsmiles.golfsim.core.ble.DemoShotSource() }

    // FIRE lives in the rail (2026-09-24 user request); demo firing routes to
    // the active game or the range session.
    fun fireDemo() {
        if (demo) routeShot(demoSource.nextShot(), ShotSource.DEMO)
    }

    // --- Auto-connect boot cycle (spec 2026-09-30 §2) ------------------------
    // Pure policy decides; this block only executes its actions. All event
    // routing happens on the main thread (collectors, launcher callback and
    // chip onClicks), so there are no cross-thread races on the policy.
    val autoPolicy = remember { AutoConnectPolicy() }
    var autoRetryLabel by remember { mutableStateOf<String?>(null) }
    // Last failure cause, display-ready (spec 2026-09-30 connect-failure-reason
    // §3): set on scan timeout and on every Faulted; cleared when a new attempt
    // starts, the link establishes, or the user disconnects. GiveUp keeps it so
    // the final BLE DISCONNECTED label explains itself.
    var lastFailure by remember { mutableStateOf<String?>(null) }
    // Once-per-link latch: Arm fires on the first Armed/Disarmed observation
    // of a link; manual STANDBY afterwards never re-triggers it.
    var linkEstablishedOnce by remember { mutableStateOf(false) }
    var retryJob by remember { mutableStateOf<Job?>(null) }
    var attemptJob by remember { mutableStateOf<Job?>(null) }

    var permissionDenied by remember { mutableStateOf(false) }

    /**
     * One cycle attempt: scan with a 10 s budget, then GATT-connect.
     * Returns true only when the caller must report a failure to the policy
     * (scan timeout — no state transition follows). Every other failure
     * arrives later as a Faulted/Disconnected state and is routed by the
     * observer; connect() itself is guarded against forked links.
     */
    @Suppress("MissingPermission")
    suspend fun runConnectAttempt(): Boolean {
        val s = connectionState
        if (s != ConnectionState.Disconnected && s !is ConnectionState.Faulted) return false
        linkEstablishedOnce = false
        scanning = true
        try {
            val device = withTimeoutOrNull(10_000L) {
                Mlm2proScanner(context).scan().first()
            }
            if (device == null) {
                Log.w(Mlm2proGattClient.TAG, "auto-connect: scan timeout")
                lastFailure = ConnectFailureHints.phrase(ConnectFailureHints.SCAN_TIMEOUT)
                return true
            }
            if (autoPolicy.stopped) return false // user disconnected mid-scan
            Log.i(Mlm2proGattClient.TAG, "scan found device=${device.address}")
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            gattClient.connect(manager.adapter.getRemoteDevice(device.address))
            return false
        } catch (e: kotlinx.coroutines.CancellationException) {
            // DISCONNECT/connect-restart cancelled this attempt mid-scan — the
            // job owner already tore the link down; never surface a fault or
            // report a policy failure for our own cancellation.
            throw e
        } catch (e: Exception) {
            Log.w(Mlm2proGattClient.TAG, "connect flow failed", e)
            gattClient.reportFault(e.javaClass.simpleName) // observer routes the retry
            return false
        } finally {
            scanning = false
        }
    }

    /** Execute one policy action on the main thread. */
    fun handleAutoAction(action: AutoConnectPolicy.Action?) {
        when (action) {
            is AutoConnectPolicy.Action.StartAttempt -> {
                retryJob?.cancel(); retryJob = null
                autoRetryLabel = null
                lastFailure = null
                attemptJob?.cancel()
                attemptJob = scope.launch {
                    if (runConnectAttempt()) handleAutoAction(autoPolicy.attemptFailed())
                }
            }
            is AutoConnectPolicy.Action.WaitThenAttempt -> {
                attemptJob?.cancel()
                autoRetryLabel = "RETRYING (${action.retryNumber}/${autoPolicy.maxRetries})\u2026"
                retryJob = scope.launch {
                    delay(action.delayMs)
                    autoRetryLabel = null
                    attemptJob = scope.launch {
                        if (runConnectAttempt()) handleAutoAction(autoPolicy.attemptFailed())
                    }
                }
            }
            // The client's ticker already arms 500 ms after READY; this is a
            // guarded no-op belt (sequencer.arm() returns null when invalid).
            AutoConnectPolicy.Action.Arm -> gattClient.arm()
            AutoConnectPolicy.Action.GiveUp -> autoRetryLabel = null
            AutoConnectPolicy.Action.Stop -> {
                retryJob?.cancel(); retryJob = null
                attemptJob?.cancel(); attemptJob = null
                autoRetryLabel = null
                lastFailure = null
            }
            null -> Unit
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val allGranted = grants.values.all { it }
        permissionDenied = !allGranted
        // Grant (first boot or after a deny) begins a fresh cycle immediately —
        // no second CONNECT tap (spec §1). Same behavior for a CONNECT tap that
        // had to request permissions first.
        if (allGranted) handleAutoAction(autoPolicy.manualConnect())
    }

    /** Route client state transitions into policy events (spec §2 observer). */
    fun routeConnectionState(s: ConnectionState) {
        when (s) {
            is ConnectionState.Armed, is ConnectionState.Disarmed ->
                if (!linkEstablishedOnce) {
                    linkEstablishedOnce = true
                    lastFailure = null
                    handleAutoAction(autoPolicy.linkEstablished())
                }
            is ConnectionState.Faulted -> {
                lastFailure = ConnectFailureHints.phrase(s.reason)
                if (linkEstablishedOnce) {
                    linkEstablishedOnce = false
                    handleAutoAction(autoPolicy.linkDropped())
                } else {
                    handleAutoAction(autoPolicy.attemptFailed())
                }
            }
            is ConnectionState.Disconnected ->
                if (linkEstablishedOnce) {
                    linkEstablishedOnce = false
                    handleAutoAction(autoPolicy.linkDropped())
                }
            ConnectionState.Connecting, ConnectionState.Handshaking -> Unit
        }
    }

    LaunchedEffect(gattClient, autoPolicy) {
        gattClient.state.collect { routeConnectionState(it) }
    }

    // Boot: connect as soon as the app opens (spec §1). Missing permissions
    // are requested immediately; the launcher callback starts the cycle on
    // grant. Runs once per composition lifetime (key Unit).
    LaunchedEffect(Unit) {
        val scanGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_SCAN,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val connectGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (scanGranted && connectGranted) {
            handleAutoAction(autoPolicy.start())
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                ),
            )
        }
    }

    fun connectTapped() {
        val scanGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_SCAN,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val connectGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (scanGranted && connectGranted) {
            permissionDenied = false
            handleAutoAction(autoPolicy.manualConnect())
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
    // Amendment 2: completed game scores in history.
    val gameResults by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.gameResults }, flowOf(emptyList()))
    }.collectAsState(initial = emptyList())

    // M6: bag mapping state — getter-flows captured gated like the others
    // (they re-derive from the current db, so they survive corrupt-file
    // recovery).
    val bagActive by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.bagMappingActive }, flowOf(null))
    }.collectAsState(initial = null)
    val bagLatest by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.bagMappingLatestCompleted }, flowOf(null))
    }.collectAsState(initial = null)
    val bagHistory by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.bagMappingHistory }, flowOf(emptyList()))
    }.collectAsState(initial = emptyList())
    val bagActiveShots by remember(sessionRepository, repoReady, bagActive?.id) {
        val id = bagActive?.id
        if (id == null) flowOf(emptyList())
        else gatedFlow(repoReady, { sessionRepository.observeBagMappingShots(id) }, flowOf(emptyList()))
    }.collectAsState(initial = emptyList())
    val bagViewedShots by remember(bagViewedSessionId) {
        bagViewedSessionId?.let { sessionRepository.observeBagMappingShots(it) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    var bagShotCounts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(bagHistory) {
        bagShotCounts = sessionRepository.bagMappingShotCounts()
    }

    // M7: fitting flows — Room truth, gated like the bag captures. Kill/resume
    // is FREE: these re-derive from the current db, so they survive process
    // death (and the corrupt-file recovery swap).
    val fitActive by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.fittingActive }, flowOf(null))
    }.collectAsState(initial = null)
    val fitHistory by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.fittingHistory }, flowOf(emptyList()))
    }.collectAsState(initial = emptyList())
    val fitHistoryDetails by remember(sessionRepository, repoReady) {
        gatedFlow(repoReady, { sessionRepository.fittingHistoryDetails }, flowOf(emptyMap()))
    }.collectAsState(initial = emptyMap())
    val fitShots by remember(sessionRepository, repoReady, fitActive?.id) {
        gatedFlow(
            repoReady,
            { fitActive?.let { sessionRepository.observeFittingShots(it.id) } ?: flowOf(emptyList()) },
            flowOf(emptyList()),
        )
    }.collectAsState(initial = emptyList())
    val fitViewedShots by remember(sessionRepository, repoReady, fittingController.viewedSessionId) {
        gatedFlow(
            repoReady,
            { fittingController.viewedSessionId?.let { sessionRepository.observeFittingShots(it) } ?: flowOf(emptyList()) },
            flowOf(emptyList()),
        )
    }.collectAsState(initial = emptyList())

    // M7 (task 5 review): sessionId is derived from Room truth, never cached
    // across completion — prevents a late write-back from re-binding a
    // COMPLETED session after END COMPARISON.
    LaunchedEffect(fitActive?.id) {
        fittingController.sessionId = fitActive?.id ?: 0
    }

    // M6: auto-bind the collector to an open session (process death, tab
    // re-entry). Resume index = first club below the 5-shot target; when
    // every club has >= 5 kept, re-run the gate on the last club. The shot
    // set is read fresh inside the effect — the gated bagActiveShots capture
    // may still be empty on the first composition after process death.
    LaunchedEffect(bagActive?.id) {
        val active = bagActive ?: return@LaunchedEffect
        if (bagCollector.sessionId != active.id) {
            val shots = sessionRepository.observeBagMappingShots(active.id).first()
            val plan = active.clubSnapshot().map { BagPlanClub(it.first, it.second) }
            val keptByClub = shots.filter { !it.filtered }.groupingBy { it.clubName }.eachCount()
            val idx = plan.indexOfFirst { (keptByClub[it.name] ?: 0) < ClubQualityGate.TARGET_KEPT }
            bagCollector.beginAt(active.id, plan, if (idx == -1) plan.size - 1 else idx)
        }
    }

    /**
     * M6: START TEST (also RETEST). Creates the IN_PROGRESS session with a
     * bag snapshot and binds the collector. Idempotent in the repo: if a
     * test is already running its session id comes back untouched (the UI
     * only offers START when none does — spec §4).
     */
    fun startBagTest(orderMode: BagOrderMode) {
        scope.launch {
            val eligible = clubRecords.filter { !it.isTemp && it.type != ClubType.PUTTER }
            if (eligible.isEmpty()) return@launch
            val ordered = orderPlan(eligible, orderMode)
            val id = sessionRepository.startBagMappingSession(ordered, System.currentTimeMillis()) ?: return@launch
            bagCollector.begin(id, ordered.map { BagPlanClub(it.name, it.type) })
        }
    }

    /** M6: END TEST / final-club completion → the result becomes the active result. */
    fun completeBagTest() {
        val active = bagActive ?: return
        bagDisplayShot = null
        scope.launch {
            sessionRepository.completeBagMappingSession(active.id, System.currentTimeMillis())
            bagCollector.reset()
        }
    }

    /** M7: END COMPARISON → the session completes; compare view resets. */
    fun completeFittingTest() {
        val active = fitActive ?: return
        scope.launch {
            sessionRepository.completeFittingSession(active.id, System.currentTimeMillis())
            fittingController.sessionId = 0
            fittingController.baselineClubId = null
            fittingController.view = FittingView.COMPARE
            fitSession.clearForEndedSession()
        }
    }

    /**
     * M7 PDF export (2026-10-08): builds + renders the currently viewed
     * session (live or read-only history) off the main thread, then fires the
     * share sheet on the main dispatcher. Re-taps are ignored while running;
     * failures are logged and swallowed (no UI surface — spec).
     */
    fun exportFittingPdf() {
        if (exportingPdf) return
        val shots = if (fittingController.viewedSessionId != null) fitViewedShots else fitShots
        if (shots.isEmpty()) return
        exportingPdf = true
        scope.launch {
            try {
                val file = withContext(Dispatchers.Default) {
                    FittingPdfWriter.writeToCache(context, FittingPdfData.build(shots, System.currentTimeMillis()))
                }
                if (file != null) {
                    FittingPdfWriter.share(context, file)
                }
            } catch (t: Throwable) {
                Log.w("FittingPdf", "export failed", t)
            } finally {
                exportingPdf = false
            }
        }
    }

    // M6: the last club advancing past the plan completes the session.
    LaunchedEffect(bagCollector.done, bagActive?.id) {
        if (bagCollector.done && bagActive != null) completeBagTest()
    }

    // Newest session is selected by default; an absent selection falls back
    // to newest so the detail pane never points at a ghost row.
    LaunchedEffect(summaries) {
        val ids = summaries.map { it.id }
        if (historySelectedId == null || historySelectedId !in ids) {
            historySelectedId = summaries.firstOrNull()?.id
        }
    }

    var pendingTab by remember { mutableStateOf<RangeTab?>(null) }
    var confirmLeaveGame by remember { mutableStateOf(false) }
    fun requestTab(to: RangeTab) {
        val active = activeGame
        val activeShots = when (active) {
            GameMode.TARGET_PRACTICE -> targetPractice.shots.isNotEmpty()
            GameMode.BREAK_PANE -> breakPane.shots.isNotEmpty()
            GameMode.NONE -> false
        }
        val activeComplete = when (active) {
            GameMode.TARGET_PRACTICE -> targetPractice.complete
            GameMode.BREAK_PANE -> breakPane.complete
            GameMode.NONE -> true
        }
        val midGame = GameLeavePolicy.confirmRequired(active, activeShots, activeComplete)
        if (midGame && to != RangeTab.GAMES) {
            pendingTab = to
            confirmLeaveGame = true
        } else {
            if (to != RangeTab.GAMES) activeGame = GameMode.NONE
            tab = to
        }
    }

    GolfTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GolfColors.Base)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Collected above the tab switch so every tab (incl. FIT) can
            // surface the DB-write-failure override in its status info.
            val persistError by sessionRepository.persistError.collectAsState()
            Row(modifier = Modifier.weight(1f)) {
                NavRail {
                    NavRailButton("RANGE", tab == RangeTab.RANGE, onClick = { requestTab(RangeTab.RANGE) })
                    NavRailButton("GAMES", tab == RangeTab.GAMES, onClick = { tab = RangeTab.GAMES })
                    NavRailButton("BAG", tab == RangeTab.BAG, onClick = { requestTab(RangeTab.BAG) })
                    NavRailButton("FIT", tab == RangeTab.FIT, onClick = { requestTab(RangeTab.FIT) })
                    NavRailButton("SETTINGS", tab == RangeTab.SETTINGS, onClick = { requestTab(RangeTab.SETTINGS) })
                    NavRailButton("HISTORY", tab == RangeTab.HISTORY, onClick = { requestTab(RangeTab.HISTORY) })

                    // M4d user request (2026-09-24, revised): FIRE / MODE /
                    // CONNECT live at the BOTTOM of the rail, under a flexible
                    // spacer; ARM/STANDBY + DISCONNECT follow beneath them.
                    Spacer(Modifier.weight(1f))
                    // FIRE only makes sense in DEMO (2026-09-30 user request):
                    // in LIVE mode shots arrive from the monitor, so hide it.
                    if (demo) {
                        RailChip("FIRE", border = GolfColors.Amber, labelColor = GolfColors.Amber,
                            onClick = { fireDemo() })
                    }
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
                            onClick = {
                                // Stop the cycle BEFORE tearing the link down so a
                                // pending wait/attempt cannot resurrect the connection.
                                handleAutoAction(autoPolicy.userDisconnect())
                                linkEstablishedOnce = false
                                disconnectClient(gattClient)
                            },
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
                            onClick = {
                                scope.launch {
                                    sessionRepository.endSession()
                                    // Clear the in-memory list too, or the range
                                    // keeps showing the ended session's shots and
                                    // its SESSION summary (user report 2026-10-01).
                                    session.clearForEndedSession()
                                }
                            },
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
                    RangeTab.GAMES -> {
                        GamesScreen(
                            modifier = Modifier.weight(1f),
                            targetPractice = targetPractice,
                            breakPane = breakPane,
                            activeGame = activeGame,
                            onActiveGameChange = { activeGame = it },
                            gameAudio = gameAudio,
                            soundsEnabled = soundsEnabled,
                            onSoundsChange = ::selectSoundsEnabled,
                        )
                    }
                    RangeTab.BAG -> {
                        BagMappingScreen(
                            modifier = Modifier.weight(1f),
                            clubs = clubRecords,
                            activeSession = bagActive,
                            latestCompleted = bagLatest,
                            history = bagHistory,
                            shotCounts = bagShotCounts,
                            activeShots = bagActiveShots,
                            viewedShots = bagViewedShots,
                            collector = bagCollector,
                            onViewedSessionChange = { bagViewedSessionId = it },
                            onCollectingChange = { bagCollecting = it },
                            onStartTest = ::startBagTest,
                            onOpenSettings = { requestTab(RangeTab.SETTINGS) },
                            onCompleteSession = ::completeBagTest,
                            latestShot = bagDisplayShot,
                        )
                    }
                    RangeTab.FIT -> {
                        FittingScreen(
                            modifier = Modifier.weight(1f),
                            controller = fittingController,
                            clubs = clubRecords,
                            activeClubName = activeClubName,
                            activeSession = fitActive,
                            history = fitHistory,
                            historyDetails = fitHistoryDetails,
                            liveShots = fitSession.shots,
                            sessionShots = fitShots,
                            viewedShots = fitViewedShots,
                            armed = demo || connectionState is ConnectionState.Armed,
                            info = if (persistError) {
                                "DB WRITE FAILING"
                            } else {
                                describe(
                                    connectionState,
                                    demo = demo,
                                    scanning = scanning,
                                    retryLabel = autoRetryLabel,
                                    failureReason = lastFailure,
                                )
                            },
                            onSelectClub = ::selectClub,
                            onAddClub = { name, type, isTest ->
                                val ok = sessionRepository.addClub(name, type, isTest)
                                if (ok) selectClub(name.trim())   // spec §3: auto-active on add
                                ok
                            },
                            onComplete = ::completeFittingTest,
                            onSetExcluded = { ids, excluded ->
                                scope.launch { sessionRepository.setFittingShotsExcluded(ids, excluded) }
                            },
                            onExportPdf = ::exportFittingPdf,
                        )
                    }
                    RangeTab.SETTINGS -> {
                        SettingsScreen(
                            captureLog = captureLog,
                            greenCondition = greenCondition,
                            onGreenConditionChange = ::selectGreenCondition,
                            turfCondition = turfCondition,
                            onTurfConditionChange = ::selectTurfCondition,
                            soundsEnabled = soundsEnabled,
                            onSoundsChange = ::selectSoundsEnabled,
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
                            onMoveClub = { id, index ->
                                scope.launch { sessionRepository.moveClub(id, index) }
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
                            clubs = clubRecords,
                            onRetag = { ids, club ->
                                scope.launch { sessionRepository.retagShots(ids, club) }
                            },
                            onRenameSession = { id, title ->
                                scope.launch { sessionRepository.renameSession(id, title) }
                            },
                            onToggleExcluded = { id, excluded ->
                                scope.launch { sessionRepository.setShotsExcluded(listOf(id), excluded) }
                            },
                            gameResults = gameResults,
                        )
                    }
                }
            }
            StatusStrip(
                armed = demo || connectionState is ConnectionState.Armed,
                info = if (persistError) {
                    "DB WRITE FAILING"
                } else {
                    describe(
                        connectionState,
                        demo = demo,
                        scanning = scanning,
                        retryLabel = autoRetryLabel,
                        failureReason = lastFailure,
                    )
                },
                batteryPercent = batteryPercent,
            )
        }

        if (confirmLeaveGame) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmLeaveGame = false },
                title = { Text("LEAVE GAME?") },
                text = { Text("Shots already taken in this game will be discarded.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        confirmLeaveGame = false
                        activeGame = GameMode.NONE
                        tab = pendingTab ?: RangeTab.RANGE
                        pendingTab = null
                    }) { Text("LEAVE") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmLeaveGame = false }) { Text("STAY") }
                },
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

/**
 * StatusStrip text mapping (M4b plan; failure reason per the 2026-09-30
 * connect-failure-reason spec §5 — SEE CAPTURE removed, raw reasons live in
 * logcat/capture). Retry labels append the reason with a space; state labels
 * with " - " matching existing style.
 */
internal fun describe(
    state: ConnectionState,
    demo: Boolean,
    scanning: Boolean,
    retryLabel: String? = null,
    failureReason: String? = null,
): String = when {
    demo -> "DEMO MODE - FIRE TO SHOOT"
    retryLabel != null ->
        if (failureReason != null) "$retryLabel $failureReason" else retryLabel
    scanning -> "SCANNING\u2026"
    else -> when (state) {
        ConnectionState.Disconnected ->
            if (failureReason != null) "BLE DISCONNECTED - $failureReason" else "BLE DISCONNECTED"
        ConnectionState.Connecting -> "CONNECTING\u2026"
        ConnectionState.Handshaking -> "HANDSHAKING\u2026"
        ConnectionState.Armed -> "ARMED"
        ConnectionState.Disarmed -> "DISARMED - STANDBY"
        is ConnectionState.Faulted ->
            if (failureReason != null) "BLE FAULTED - $failureReason" else "BLE FAULTED"
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
