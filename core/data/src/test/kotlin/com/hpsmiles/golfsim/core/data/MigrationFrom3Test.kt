package com.hpsmiles.golfsim.core.data

import android.database.sqlite.SQLiteDatabase
import com.hpsmiles.golfsim.core.data.entity.BagMappingStatus
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.physics.ShotResult
import com.hpsmiles.golfsim.core.ble.BallData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * MIGRATION_3_4 against a hand-built v3 file (same pattern as
 * MigrationFrom1Test/2Test - no MigrationTestHelper in this repo). Prior
 * sessions/shots/clubs/game results must survive; the new bag tables must
 * be usable through the repository immediately after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MigrationFrom3Test {

    @Test
    fun `v3 file migrates - bag tables usable - prior data intact`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // v3 DDL (sessions, shots, clubs, game_results) - mirrors schemas/.../3.json
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startedAtEpochMs` INTEGER NOT NULL, `endedAtEpochMs` INTEGER, `title` TEXT, " +
                "`misreadCount` INTEGER NOT NULL)",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `shots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `timestampMs` INTEGER NOT NULL, " +
                "`source` INTEGER NOT NULL, `clubName` TEXT, `clubHeadSpeedMps` REAL NOT NULL, " +
                "`ballSpeedMps` REAL NOT NULL, `launchDirDeg` REAL NOT NULL, `launchAngleDeg` REAL NOT NULL, " +
                "`spinAxisDeg` REAL NOT NULL, `spinRpm` INTEGER NOT NULL, `unknown1` INTEGER NOT NULL, " +
                "`unknown2` INTEGER NOT NULL, `carryM` REAL NOT NULL, `totalM` REAL NOT NULL, " +
                "`sideM` REAL NOT NULL, `apexM` REAL NOT NULL, `flightTimeSec` REAL NOT NULL, " +
                "`excluded` INTEGER NOT NULL DEFAULT 0, `clubWasTemp` INTEGER NOT NULL DEFAULT 0, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_shots_sessionId` ON `shots` (`sessionId`)")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `clubs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `type` TEXT NOT NULL DEFAULT 'IRON', " +
                "`isTemp` INTEGER NOT NULL DEFAULT 0)",
        )
        raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clubs_name` ON `clubs` (`name`)")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `game_results` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`mode` TEXT NOT NULL, `difficulty` TEXT, `distanceBin` INTEGER NOT NULL, " +
                "`score` INTEGER NOT NULL, `source` INTEGER NOT NULL, " +
                "`playedAtEpochMs` INTEGER NOT NULL)",
        )
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        raw.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, '94014c8b5c8f2a1fda6d5bef72244606')",
        )
        raw.execSQL("INSERT INTO sessions(startedAtEpochMs, endedAtEpochMs, title, misreadCount) VALUES(1000, NULL, NULL, 0)")
        raw.execSQL("INSERT INTO clubs(name, sortOrder, type, isTemp) VALUES('7i', 7, 'IRON', 0)")
        raw.execSQL(
            "INSERT INTO shots(sessionId, seq, timestampMs, source, clubName, clubHeadSpeedMps, " +
                "ballSpeedMps, launchDirDeg, launchAngleDeg, spinAxisDeg, spinRpm, unknown1, unknown2, " +
                "carryM, totalM, sideM, apexM, flightTimeSec, excluded, clubWasTemp) " +
                "VALUES(1, 0, 1000, 0, '7i', 33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10, 140.0, 150.0, -3.0, 27.0, 6.0, 0, 0)",
        )
        raw.execSQL(
            "INSERT INTO game_results(mode, difficulty, distanceBin, score, source, playedAtEpochMs) " +
                "VALUES('TARGET_PRACTICE', 'MEDIUM', 140, 85, 0, 2000)",
        )
        raw.version = 3
        raw.close()

        val repo = SessionRepository.open(context)
        repo.initializeAndRestore() // migration runs here; prior data must survive

        // Prior range data survives.
        val summaries = repo.summaries.first()
        assertEquals(1, summaries.size)
        assertEquals(1, summaries.single().shotCount)
        assertEquals(1, repo.gameResultDao().getAll().size)

        // New bag tables are usable immediately: start → append → complete.
        val bagId = repo.startBagMappingSession(
            listOf(ClubRecord(1, "7i", ClubType.IRON, false)),
            startedAtMs = 5000,
        )!!
        assertNotNull(repo.bagMappingActive.first())
        assertEquals(
            BagMappingStatus.IN_PROGRESS,
            repo.bagMappingActive.first()?.status,
        )
        repo.appendBagMappingShot(
            sessionId = bagId,
            clubName = "7i",
            clubType = ClubType.IRON,
            ballData = BallData(33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10),
            carryM = 140.0,
            totalM = 150.0,
            timestampMs = 5001,
        )
        assertEquals(1, repo.observeBagMappingShots(bagId).first().size)
        repo.completeBagMappingSession(bagId, completedAtMs = 9000)
        assertNull(repo.bagMappingActive.first())
        assertEquals(BagMappingStatus.COMPLETED, repo.bagMappingLatestCompleted.first()?.status)
        assertTrue(repo.bagMappingHistory.first().isNotEmpty())
    }
}
