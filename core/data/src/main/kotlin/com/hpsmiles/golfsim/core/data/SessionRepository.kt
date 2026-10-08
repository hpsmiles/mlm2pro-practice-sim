package com.hpsmiles.golfsim.core.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.bag.BagMappingStats
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingStatus
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.entity.FittingStatus
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity
import com.hpsmiles.golfsim.core.data.entity.SessionEntity
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.RestoredSession
import com.hpsmiles.golfsim.core.data.record.SessionSummary
import com.hpsmiles.golfsim.core.data.record.ShotRecord
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.data.record.makeShotEntity
import com.hpsmiles.golfsim.core.data.record.toRecord
import com.hpsmiles.golfsim.core.data.record.toSummary
import com.hpsmiles.golfsim.core.physics.ShotResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * The persistence facade `:app` talks to. All mutations are suspend; `:app`
 * owns the calling coroutine (single-writer discipline via main-thread
 * mediation, spec §7). Nothing here launches coroutines of its own.
 */
class SessionRepository private constructor(private val context: Context) {

    private var db: Mlm2proDatabase = build()
    private val sessionDao get() = db.sessionDao()
    private val shotDao get() = db.shotDao()
    private val clubDao get() = db.clubDao()
    private val gameResultDao get() = db.gameResultDao()
    private val bagMappingSessionDao get() = db.bagMappingSessionDao()
    private val bagMappingShotDao get() = db.bagMappingShotDao()
    private val fittingSessionDao get() = db.fittingSessionDao()
    private val fittingShotDao get() = db.fittingShotDao()

    /** True once a write/open failure has been seen; cleared on next success. */
    val persistError = MutableStateFlow(false)

    /** History filter: true = live shots only (default, spec D4). UI toggles. */
    val liveOnly = MutableStateFlow(true)

    /**
     * Session list, already mapped through the [liveOnly] filter.
     *
     * These three flows are property GETTERS, not stored fields: they must
     * re-derive from the CURRENT [db] so they survive a corrupt-DB recovery
     * ([recoverFromCorruptFile] swaps in a rebuilt database). Flows captured
     * from the closed database die with Room's cancelled SupervisorJob —
     * caught by the recovery mechanism test, the first code path to execute
     * real recovery.
     */
    val summaries: Flow<List<SessionSummary>>
        get() = combine(sessionDao.observeSummaries(), liveOnly) { rows, onlyLive ->
            // D4: LIVE ONLY drops sessions with no live shots (demo-only)
            // from the list entirely; toSummary() re-shapes the statistics
            // slice. ALL shows everything. (Task 9a)
            rows.filter { !onlyLive || it.liveCount > 0 }.map { it.toSummary(onlyLive) }
        }

    val hasOpenSession: Flow<Boolean>
        get() = sessionDao.observeOpen().map { it != null }

    val clubs: Flow<List<ClubRecord>>
        get() = clubDao.observeClubs().map { list ->
            // M5x E3: one sort site — bag order is TYPE first (enum
            // declaration: Driver…Putter), then insertion order within type.
            list.sortedWith(compareBy({ ClubType.fromName(it.type).ordinal }, { it.sortOrder }))
                .map { ClubRecord(it.id, it.name, ClubType.fromName(it.type), it.isTemp) }
        }

    /** Test seam: raw club rows, for reorder order/uniqueness assertions. */
    internal fun clubDao() = clubDao

    fun observeShots(sessionId: Long): Flow<List<ShotRecord>> =
        shotDao.observeShots(sessionId).map { list -> list.map { it.toRecord() } }

    /**
     * Seeds the default bag once and probes the DB by opening it eagerly.
     * A corrupt/broken file is archived and replaced with a fresh empty DB
     * with [persistError] set (spec §8) — returns null in that case.
     */
    suspend fun initializeAndRestore(): RestoredSession? = try {
        initialize()
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        recoverFromCorruptFile()
        null
    }

    private suspend fun initialize(): RestoredSession? {
        seedClubsIfEmpty()
        val open = sessionDao.findOpen() ?: return null
        return RestoredSession(
            sessionId = open.id,
            misreadCount = open.misreadCount,
            shots = shotDao.shotsForSession(open.id).map { it.toRecord() },
        )
    }

    private suspend fun seedClubsIfEmpty() {
        if (clubDao.count() == 0) {
            DEFAULT_CLUBS.forEachIndexed { i, (name, type) ->
                clubDao.insert(ClubEntity(name = name, sortOrder = i, type = type.name))
            }
        }
    }

    /**
     * Appends one accepted shot; auto-creates the open session if none.
     * Never throws — persistence trouble surfaces via [persistError] so the
     * live UI keeps rendering from memory (spec §8). [clubWasTemp] snapshots
     * the TEST-club flag at capture so the history badge survives the
     * session-end purge.
     */
    suspend fun appendShot(
        ballData: BallData,
        result: ShotResult,
        source: ShotSource,
        clubName: String?,
        timestampMs: Long,
        clubWasTemp: Boolean = false,
    ) {
        try {
            db.withTransaction {
                val sessionId = sessionDao.findOpen()?.id
                    ?: sessionDao.insert(SessionEntity(startedAtEpochMs = timestampMs))
                val seq = shotDao.countForSession(sessionId)
                shotDao.insert(
                    makeShotEntity(sessionId, seq, timestampMs, source, clubName, ballData, result, clubWasTemp),
                )
            }
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "appendShot failed", t)
            persistError.value = true
        }
    }

    /**
     * Coalesced misread (RangeSession already coalesces the 0x05 pair).
     * Never throws — same contract as [appendShot] (spec §8).
     */
    suspend fun incrementMisread() {
        try {
            val open = sessionDao.findOpen() ?: return
            sessionDao.incrementMisread(open.id)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "incrementMisread failed", t)
            persistError.value = true
        }
    }

    /** Never throws. M5x E3: every END SESSION also purges TEST clubs. */
    suspend fun endSession() {
        try {
            db.withTransaction {
                sessionDao.findOpen()?.let { sessionDao.end(it.id, System.currentTimeMillis()) }
                clubDao.deleteTempClubs()
            }
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "endSession failed", t)
            persistError.value = true
        }
    }

    /** Never throws — same contract as [appendShot] (Task 9a). */
    suspend fun renameSession(id: Long, title: String) {
        val trimmed = title.trim().take(MAX_TITLE).ifBlank { null }
        try {
            sessionDao.rename(id, trimmed)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "renameSession failed", t)
            persistError.value = true
        }
    }

    /** Never throws — same contract as [appendShot] (Task 9a). */
    suspend fun retagShots(ids: List<Long>, clubName: String?) {
        if (ids.isEmpty()) return
        val trimmed = clubName?.trim()?.take(MAX_CLUB)
        try {
            shotDao.retagShotIds(ids, trimmed)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "retagShots failed", t)
            persistError.value = true
        }
    }

    /** Never throws (M5x E5); batch-flips the excluded flag, flows re-emit. */
    suspend fun setShotsExcluded(ids: List<Long>, excluded: Boolean) {
        if (ids.isEmpty()) return
        try {
            shotDao.setShotsExcluded(ids, excluded)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "setShotsExcluded failed", t)
            persistError.value = true
        }
    }

    /**
     * False on blank, comma-containing (summary CSV separator), or duplicate
     * name. Never throws (Task 8a): a lost unique-name race (double-tap)
     * returns false like a sequential duplicate; a real I/O failure flags
     * [persistError], clears on success.
     */
    suspend fun addClub(name: String, type: ClubType = ClubType.IRON, isTest: Boolean = false): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_CLUB) return false
        if (trimmed.contains(',')) return false
        return try {
            if (clubDao.findByName(trimmed) != null) return false
            clubDao.insert(
                ClubEntity(
                    name = trimmed,
                    sortOrder = clubDao.maxSortOrder() + 1,
                    type = type.name,
                    isTemp = isTest,
                ),
            )
            persistError.value = false
            true
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // TOCTOU on the unique-name index (double-tap): the club exists
            // by now — same outcome as the duplicate check above, so this is
            // a rejection, not a DB failure. Room's Android driver throws the
            // FRAMEWORK exception class. (Task 8a)
            if (t is android.database.sqlite.SQLiteConstraintException) return false
            Log.e(TAG, "addClub failed", t)
            persistError.value = true
            false
        }
    }

    /**
     * False on blank, comma-containing, or duplicate name (including the
     * club's own current name — no self-exclusion, pinned by the Task 8a
     * test). Never throws; mirrors [addClub]'s failure contract.
     */
    suspend fun renameClub(id: Long, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_CLUB) return false
        if (trimmed.contains(',')) return false
        return try {
            if (clubDao.findByName(trimmed) != null) return false
            clubDao.rename(id, trimmed)
            persistError.value = false
            true
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Lost the unique-name race (double-tap) — mirrors addClub: a
            // rejection, not a DB failure. (Task 8a)
            if (t is android.database.sqlite.SQLiteConstraintException) return false
            Log.e(TAG, "renameClub failed", t)
            persistError.value = true
            false
        }
    }

    /** Never throws (Task 8a); flags [persistError] on failure, clears on success. */
    suspend fun deleteClub(id: Long) {
        try {
            clubDao.delete(id)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "deleteClub failed", t)
            persistError.value = true
        }
    }

    /**
     * Moves a club to a 0-based slot within its own type group. The slot is a
     * final position and is clamped to 0..k-1. Renumbers only that group's
     * sortOrder to 0..k-1. Same-slot drop is a no-op. Never throws — same
     * contract as [deleteClub]; a failed write leaves the old order in the
     * [clubs] flow.
     */
    suspend fun moveClub(id: Long, toIndexInType: Int) {
        try {
            db.withTransaction {
                val club = clubDao.findById(id) ?: return@withTransaction
                val type = ClubType.fromName(club.type)
                val group = clubDao.all()
                    .filter { ClubType.fromName(it.type) == type }
                    .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                val from = group.indexOfFirst { it.id == id }
                if (from < 0) return@withTransaction
                val reordered = group.toMutableList()
                reordered.removeAt(from)
                reordered.add(toIndexInType.coerceIn(0, reordered.size), club)
                if (reordered.map { it.id } == group.map { it.id }) return@withTransaction
                reordered.forEachIndexed { index, entity ->
                    if (entity.sortOrder != index) clubDao.updateSortOrder(entity.id, index)
                }
            }
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "moveClub failed", t)
            persistError.value = true
        }
    }

    /** Completed game summaries, newest first. Gated like [summaries]. */
    val gameResults: Flow<List<GameResultEntity>>
        get() = gameResultDao.observeAll()

    fun gameResultDao() = gameResultDao

    /**
     * One summary row per completed game (M5.5 spec S7). Never throws -
     * same contract as [appendShot]. Game shots themselves are never persisted.
     */
    suspend fun saveGameResult(
        mode: String,
        difficulty: String?,
        distanceM: Double,
        score: Int,
        source: ShotSource,
        playedAtEpochMs: Long,
    ) {
        try {
            gameResultDao.insert(
                GameResultEntity(
                    mode = mode,
                    difficulty = difficulty,
                    distanceBin = distanceBin(distanceM),
                    score = score,
                    source = source.code,
                    playedAtEpochMs = playedAtEpochMs,
                ),
            )
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "saveGameResult failed", t)
            persistError.value = true
        }
    }

    /**
     * Prior best for a record key (mode + difficulty + nearest-10m bin), or
     * null when no matching row exists. MUST be read BEFORE [saveGameResult]
     * for the same game, or it reads the row just written. Never throws — a
     * query failure also yields null (no celebration), same contract as
     * [appendShot].
     */
    suspend fun bestGameScore(
        mode: String,
        difficulty: String,
        distanceM: Double,
        lowerIsBetter: Boolean,
    ): Int? = try {
        val bin = distanceBin(distanceM)
        if (lowerIsBetter) {
            gameResultDao.bestLowScore(mode, difficulty, bin)
        } else {
            gameResultDao.bestHighScore(mode, difficulty, bin)
        }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "bestGameScore failed", t)
        null
    }

    private fun build(): Mlm2proDatabase =
        Room.databaseBuilder(context, Mlm2proDatabase::class.java, DB_NAME)
            .addMigrations(
                Mlm2proDatabase.MIGRATION_1_2,
                Mlm2proDatabase.MIGRATION_2_3,
                Mlm2proDatabase.MIGRATION_3_4,
                Mlm2proDatabase.MIGRATION_4_5,
            )
            .build()

    /**
     * Spec §8: archive the broken file (with SQLite sidecars), start fresh.
     * internal (not private) so the corrupt-DB mechanism test can drive it
     * directly — Robolectric's SQLite won't throw on forged files, so the
     * real-throw detection path is verified on-device (Task 10, Step 5b).
     */
    internal suspend fun recoverFromCorruptFile() {
        Log.e(TAG, "database unreadable - recreating; old file archived")
        try {
            db.close()
        } catch (_: Exception) {
        }
        val parent = context.getDatabasePath(DB_NAME).parentFile
        for (suffix in listOf("-journal", "-wal", "-shm")) {
            val side = File(parent, DB_NAME + suffix)
            if (side.exists()) side.delete()
        }
        val main = context.getDatabasePath(DB_NAME)
        if (main.exists() && parent != null) {
            main.renameTo(File(parent, "corrupt-${System.currentTimeMillis()}-$DB_NAME"))
        }
        db = build()
        persistError.value = true
        try {
            seedClubsIfEmpty()
        } catch (t: Exception) {
            // Same cancellation discipline as appendShot / initializeAndRestore:
            // a cancelled caller must not have its CancellationException eaten
            // on this suspend path (Task 4b, review finding).
            if (t is CancellationException) throw t
        }
    }

    // --- Bag mapping (M6, spec §7) -----------------------------------------

    /** Open (in-progress) mapping session, or null. Getter-flow — survives corrupt-file recovery. */
    val bagMappingActive: Flow<BagMappingSessionEntity?>
        get() = bagMappingSessionDao.observeInProgress()

    /** Latest completed mapping result (the active result), or null. Getter-flow. */
    val bagMappingLatestCompleted: Flow<BagMappingSessionEntity?>
        get() = bagMappingSessionDao.observeLatestCompleted()

    /** All completed mapping results, newest first. Getter-flow. */
    val bagMappingHistory: Flow<List<BagMappingSessionEntity>>
        get() = bagMappingSessionDao.observeHistory()

    /** Every stored shot (kept + filtered) of one mapping session. */
    fun observeBagMappingShots(sessionId: Long): Flow<List<BagMappingShotEntity>> =
        bagMappingShotDao.observeShots(sessionId)

    /** Mapping shots per session id, for history rows. Empty map on failure (never throws). */
    suspend fun bagMappingShotCounts(): Map<Long, Int> = try {
        bagMappingShotDao.countsBySession().associate { it.sessionId to it.count }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "bagMappingShotCounts failed", t)
        emptyMap()
    }

    /**
     * Creates the IN_PROGRESS session (spec §4), snapshotting [clubs] (the
     * mapping-eligible bag, already putter/TEST-free) into clubList. At most
     * one IN_PROGRESS session exists (repository-enforced): when one is open
     * its id is returned untouched — START is idempotent, never destructive.
     * Returns null only on a persistence failure (persistError flags; never
     * throws — appendShot contract).
     */
    suspend fun startBagMappingSession(clubs: List<ClubRecord>, startedAtMs: Long): Long? =
        try {
            db.withTransaction {
                val existing = bagMappingSessionDao.findInProgress()
                existing?.id
                    ?: bagMappingSessionDao.insert(
                        BagMappingSessionEntity(
                            startedAtMs = startedAtMs,
                            completedAtMs = null,
                            status = BagMappingStatus.IN_PROGRESS,
                            clubList = BagMappingSessionEntity.encodeClubSnapshot(
                                clubs.map { it.name to it.type },
                            ),
                        ),
                    )
            }.also { persistError.value = false }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "startBagMappingSession failed", t)
            persistError.value = true
            null
        }

    /**
     * Write-through append for one mapping shot (spec §4) — this is what
     * makes resume-after-kill work. Inside the same transaction the duff
     * filter is recomputed over the club's whole shot set (BagMappingStats)
     * so kept-set, stats and plots stay consistent on every write, including
     * retroactively re-judging early shots. Returns the new row id, or null
     * on failure (persistError flags; never throws).
     */
    suspend fun appendBagMappingShot(
        sessionId: Long,
        clubName: String,
        clubType: ClubType,
        ballData: BallData,
        carryM: Double,
        totalM: Double,
        timestampMs: Long,
    ): Long? = try {
        db.withTransaction {
            val shotId = bagMappingShotDao.insert(
                BagMappingShotEntity(
                    sessionId = sessionId,
                    clubName = clubName,
                    clubType = clubType.name,
                    timestampMs = timestampMs,
                    clubHeadSpeedMps = ballData.clubHeadSpeed,
                    ballSpeedMps = ballData.ballSpeed,
                    launchAngleDeg = ballData.launchAngle,
                    launchDirDeg = ballData.launchDirection,
                    spinAxisDeg = ballData.spinAxis,
                    totalSpinRpm = ballData.totalSpin,
                    carryM = carryM,
                    totalM = totalM,
                ),
            )
            recomputeDuffFilter(sessionId, clubName)
            shotId
        }.also { persistError.value = false }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "appendBagMappingShot failed", t)
        persistError.value = true
        null
    }

    /** Recomputes the duff filter for one club and persists changed verdicts. Call inside a transaction. */
    private suspend fun recomputeDuffFilter(sessionId: Long, clubName: String) {
        val shots = bagMappingShotDao.shotsForClub(sessionId, clubName)
        val byId = shots.associateBy { it.id }
        for (verdict in BagMappingStats.applyDuffFilter(shots)) {
            val current = byId[verdict.id] ?: continue
            if (current.filtered != verdict.filtered || current.filterReason != verdict.reason) {
                bagMappingShotDao.setFiltered(verdict.id, verdict.filtered, verdict.reason)
            }
        }
    }

    /** Marks the session COMPLETED — its result becomes the active result. Never throws. */
    suspend fun completeBagMappingSession(id: Long, completedAtMs: Long) {
        try {
            bagMappingSessionDao.complete(id, completedAtMs)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "completeBagMappingSession failed", t)
            persistError.value = true
        }
    }

    // --- Club fitting (M7, spec §2) --------------------------------------

    /**
     * Room-v5 fitting surface. Write-through shot storage with club
     * snapshots, manual exclusion toggles, and an at-most-one-IN_PROGRESS
     * session lifecycle (bag-mapping pattern). Aggregates/stats live in
     * FittingStats (pure), not here.
     */

    /** Open (in-progress) fitting session, or null. Getter-flow — survives corrupt-file recovery. */
    val fittingActive: Flow<FittingSessionEntity?>
        get() = fittingSessionDao.observeInProgress()

    /** All completed fitting sessions, newest first. Getter-flow. */
    val fittingHistory: Flow<List<FittingSessionEntity>>
        get() = fittingSessionDao.observeHistory()

    /**
     * M7 task 10: sessionId -> ordered (clubName, shotCount) rows for HISTORY
     * list rows. Order = each session's club first-appearance order (the DAO
     * groups by MIN(id)), so colour-dot index 0 is that session's first club.
     * Getter-flow.
     */
    val fittingHistoryDetails: Flow<Map<Long, List<Pair<String, Int>>>>
        get() = fittingShotDao.observeClubCounts().map { rows ->
            rows.groupBy({ it.sessionId }, { it.clubName to it.shotCount })
        }

    /** Every stored shot of one fitting session, in insertion order. */
    fun observeFittingShots(sessionId: Long): Flow<List<FittingShotEntity>> =
        fittingShotDao.observeBySession(sessionId)

    /**
     * Creates the IN_PROGRESS fitting session (M7 spec §2). At most one
     * IN_PROGRESS session exists (repository-enforced): when one is open its
     * id is returned untouched — START is idempotent, never destructive
     * (bag-mapping pattern). Returns null only on a persistence failure
     * (persistError flags; never throws — appendShot contract).
     */
    suspend fun startFittingSession(startedAtMs: Long): Long? =
        try {
            db.withTransaction {
                val existing = fittingSessionDao.findInProgress()
                existing?.id
                    ?: fittingSessionDao.insert(
                        FittingSessionEntity(
                            startedAtMs = startedAtMs,
                            completedAtMs = null,
                            status = FittingStatus.IN_PROGRESS,
                        ),
                    )
            }.also { persistError.value = false }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "startFittingSession failed", t)
            persistError.value = true
            null
        }

    /**
     * Write-through append for one fitting shot (M7 spec §2). Club fields
     * are snapshotted from [club] (id/name/type/isTemp) so the end-of-
     * session temp-club purge never rewrites fitting history; the physics
     * scalars come from [result] (ShotResult), written once at capture.
     * Returns the new row id, or null on failure (persistError flags; never
     * throws).
     */
    suspend fun appendFittingShot(
        sessionId: Long,
        club: ClubRecord,
        ballData: BallData,
        result: ShotResult,
        timestampMs: Long,
    ): Long? = try {
        fittingShotDao.insert(
            FittingShotEntity(
                sessionId = sessionId,
                clubId = club.id,
                clubName = club.name,
                clubType = club.type.name,
                clubWasTemp = club.isTemp,
                timestampMs = timestampMs,
                clubHeadSpeedMps = ballData.clubHeadSpeed,
                ballSpeedMps = ballData.ballSpeed,
                launchAngleDeg = ballData.launchAngle,
                launchDirDeg = ballData.launchDirection,
                spinAxisDeg = ballData.spinAxis,
                totalSpinRpm = ballData.totalSpin,
                carryM = result.carryM,
                totalM = result.totalM,
                sideM = result.sideM,
                apexM = result.apexM,
                flightTimeSec = result.flightTimeSec,
            ),
        ).also { persistError.value = false }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Log.e(TAG, "appendFittingShot failed", t)
        persistError.value = true
        null
    }

    /**
     * Batch-flips the manual exclusion flag for a set of fitting shots.
     * Fitting has no auto-filter — exclusion is manual or one-tap via the
     * UI (M7 spec §2). Never throws — bag-mapping contract; flows re-emit.
     * [ids] are global row ids by design under the single-writer discipline
     * (the fitting UI only ever passes ids from the session being viewed).
     */
    suspend fun setFittingShotsExcluded(ids: List<Long>, excluded: Boolean) {
        if (ids.isEmpty()) return
        try {
            fittingShotDao.setExcluded(ids, excluded)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "setFittingShotsExcluded failed", t)
            persistError.value = true
        }
    }

    /** Marks the session COMPLETED. Never throws — bag-mapping contract. */
    suspend fun completeFittingSession(id: Long, completedAtMs: Long) {
        try {
            fittingSessionDao.complete(id, completedAtMs)
            persistError.value = false
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "completeFittingSession failed", t)
            persistError.value = true
        }
    }

    companion object {
        private const val TAG = "SessionRepository"
        private const val DB_NAME = "golfsim.db"
        private const val MAX_TITLE = 40
        private const val MAX_CLUB = 20

        /** Target distance rounded to the nearest 10 m — the game record key. */
        internal fun distanceBin(distanceM: Double): Int = (Math.round(distanceM / 10.0) * 10).toInt()

        val DEFAULT_CLUBS = listOf(
            "D" to ClubType.DRIVER,
            "3W" to ClubType.WOOD, "5W" to ClubType.WOOD,
            "4H" to ClubType.HYBRID,
            "4i" to ClubType.IRON, "5i" to ClubType.IRON, "6i" to ClubType.IRON,
            "7i" to ClubType.IRON, "8i" to ClubType.IRON, "9i" to ClubType.IRON,
            "PW" to ClubType.WEDGE, "GW" to ClubType.WEDGE, "SW" to ClubType.WEDGE,
            "LW" to ClubType.WEDGE,
        )

        fun open(context: Context): SessionRepository = SessionRepository(context.applicationContext)
    }
}
