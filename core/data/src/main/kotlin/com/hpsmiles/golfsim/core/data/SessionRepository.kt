package com.hpsmiles.golfsim.core.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
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
            list.map { ClubRecord(it.id, it.name, ClubType.fromName(it.type), it.isTemp) }
        }

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
            DEFAULT_CLUBS.forEachIndexed { i, name ->
                clubDao.insert(ClubEntity(name = name, sortOrder = i))
            }
        }
    }

    /**
     * Appends one accepted shot; auto-creates the open session if none.
     * Never throws — persistence trouble surfaces via [persistError] so the
     * live UI keeps rendering from memory (spec §8).
     */
    suspend fun appendShot(
        ballData: BallData,
        result: ShotResult,
        source: ShotSource,
        clubName: String?,
        timestampMs: Long,
    ) {
        try {
            db.withTransaction {
                val sessionId = sessionDao.findOpen()?.id
                    ?: sessionDao.insert(SessionEntity(startedAtEpochMs = timestampMs))
                val seq = shotDao.countForSession(sessionId)
                shotDao.insert(makeShotEntity(sessionId, seq, timestampMs, source, clubName, ballData, result))
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

    /** Never throws — same contract as [appendShot] (spec §8). */
    suspend fun endSession() {
        try {
            val open = sessionDao.findOpen() ?: return
            sessionDao.end(open.id, System.currentTimeMillis())
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

    /**
     * False on blank, comma-containing (summary CSV separator), or duplicate
     * name. Never throws (Task 8a): a lost unique-name race (double-tap)
     * returns false like a sequential duplicate; a real I/O failure flags
     * [persistError], clears on success.
     */
    suspend fun addClub(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank() || trimmed.length > MAX_CLUB) return false
        if (trimmed.contains(',')) return false
        return try {
            if (clubDao.findByName(trimmed) != null) return false
            clubDao.insert(ClubEntity(name = trimmed, sortOrder = clubDao.maxSortOrder() + 1))
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

    private fun build(): Mlm2proDatabase =
        Room.databaseBuilder(context, Mlm2proDatabase::class.java, DB_NAME)
            .addMigrations(Mlm2proDatabase.MIGRATION_1_2)
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

    companion object {
        private const val TAG = "SessionRepository"
        private const val DB_NAME = "golfsim.db"
        private const val MAX_TITLE = 40
        private const val MAX_CLUB = 20
        val DEFAULT_CLUBS = listOf(
            "D", "3W", "5W", "4H", "4i", "5i", "6i", "7i", "8i", "9i",
            "PW", "GW", "SW", "LW",
        )

        fun open(context: Context): SessionRepository = SessionRepository(context.applicationContext)
    }
}
