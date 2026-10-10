package com.hpsmiles.golfsim.core.data

import android.database.sqlite.SQLiteDatabase
import com.hpsmiles.golfsim.core.ble.BallData
import com.hpsmiles.golfsim.core.data.entity.FittingStatus
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.ShotSource
import com.hpsmiles.golfsim.core.physics.ShotResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * MIGRATION_5_6 against a hand-built v5 file (same pattern as the earlier
 * MigrationFrom*Tests - no MigrationTestHelper in this repo). Prior
 * sessions/shots/clubs/game results/bag_mapping data AND fitting data must
 * survive; the new combine tables must be usable through the repository
 * immediately after (M8: start → append → complete).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MigrationFrom5Test {

    @Test
    fun `v5 file migrates - combine tables usable - prior data intact`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // v5 DDL — sessions/shots/clubs/game_results copied verbatim from
        // MigrationFrom3Test; bag_mapping_* copied verbatim from MIGRATION_3_4;
        // fitting_* copied verbatim from MIGRATION_4_5.
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
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `bag_mapping_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startedAtMs` INTEGER NOT NULL, `completedAtMs` INTEGER, " +
                "`status` TEXT NOT NULL, `clubList` TEXT NOT NULL)",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `bag_mapping_shots` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, `clubName` TEXT NOT NULL, " +
                "`clubType` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, " +
                "`clubHeadSpeedMps` REAL NOT NULL, `ballSpeedMps` REAL NOT NULL, " +
                "`launchAngleDeg` REAL NOT NULL, `launchDirDeg` REAL NOT NULL, " +
                "`spinAxisDeg` REAL NOT NULL, `totalSpinRpm` INTEGER NOT NULL, " +
                "`carryM` REAL NOT NULL, `totalM` REAL NOT NULL, " +
                "`filtered` INTEGER NOT NULL, `filterReason` TEXT)",
        )
        raw.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_bag_mapping_shots_sessionId` " +
                "ON `bag_mapping_shots` (`sessionId`)",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `fitting_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`startedAtMs` INTEGER NOT NULL, `completedAtMs` INTEGER, " +
                "`status` TEXT NOT NULL)",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `fitting_shots` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, `clubId` INTEGER NOT NULL, " +
                "`clubName` TEXT NOT NULL, `clubType` TEXT NOT NULL, " +
                "`clubWasTemp` INTEGER NOT NULL, `timestampMs` INTEGER NOT NULL, " +
                "`clubHeadSpeedMps` REAL NOT NULL, `ballSpeedMps` REAL NOT NULL, " +
                "`launchAngleDeg` REAL NOT NULL, `launchDirDeg` REAL NOT NULL, " +
                "`spinAxisDeg` REAL NOT NULL, `totalSpinRpm` INTEGER NOT NULL, " +
                "`carryM` REAL NOT NULL, `totalM` REAL NOT NULL, `sideM` REAL NOT NULL, " +
                "`apexM` REAL NOT NULL, `flightTimeSec` REAL NOT NULL, " +
                "`excluded` INTEGER NOT NULL DEFAULT 0)",
        )
        raw.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_fitting_shots_sessionId` " +
                "ON `fitting_shots` (`sessionId`)",
        )
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        raw.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, '10534aeb76d0cd662d5454dbaed2b27a')",
        )
        raw.execSQL("INSERT INTO sessions(startedAtEpochMs, endedAtEpochMs, title, misreadCount) VALUES(1000, NULL, NULL, 0)")
        raw.execSQL("INSERT INTO clubs(name, sortOrder, type, isTemp) VALUES('7i', 7, 'IRON', 0)")
        raw.execSQL(
            "INSERT INTO shots(sessionId, seq, timestampMs, source, clubName, clubHeadSpeedMps, " +
                "ballSpeedMps, launchDirDeg, launchAngleDeg, spinAxisDeg, spinRpm, unknown1, unknown2, " +
                "carryM, totalM, sideM, apexM, flightTimeSec, excluded, clubWasTemp) " +
                "VALUES(1, 0, 1000, 0, '7i', 33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10, 140.0, 150.0, -3.0, 27.0, 6.0, 0, 0)",
        )
        raw.version = 5
        raw.close()

        val repository = SessionRepository.open(context)
        repository.initializeAndRestore() // migration runs here; prior data must survive

        // Prior range data survives.
        val summaries = repository.summaries.first()
        assertEquals(1, summaries.size)
        assertEquals(1, summaries.single().shotCount)

        // Fitting tables (already present in v5) remain usable immediately.
        val fitId = repository.startFittingSession(startedAtMs = 5000)!!
        assertEquals(FittingStatus.IN_PROGRESS, repository.fittingActive.first()?.status)
        repository.appendFittingShot(
            sessionId = fitId,
            club = ClubRecord(1, "7i", ClubType.IRON, false),
            ballData = BallData(33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10),
            result = ShotResult(
                carryM = 140.0, rolloutM = 10.0, totalM = 150.0,
                sideM = -3.0, apexM = 27.0, flightTimeSec = 6.0,
            ),
            timestampMs = 5001,
        )
        assertEquals(1, repository.observeFittingShots(fitId).first().size)
        repository.completeFittingSession(fitId, completedAtMs = 9000)
        assertNull(repository.fittingActive.first())
        assertEquals(1, repository.fittingHistory.first().size)
        assertNotNull(repository.fittingHistory.first().single().completedAtMs)

        // combine tables created by MIGRATION_5_6 are usable
        val combineSession = repository.startCombineSession("APPROACH", "APPROACH", 1, 1_000L, ShotSource.LIVE)
        assertNotNull(combineSession)
        val combineId = combineSession!!
        for (index in 0 until 18) {
            repository.appendCombineShot(combineId, index, index / 6, "FAIRWAY", 12.0)
        }
        assertEquals(18, repository.observeCombineShots(combineId).first().size)
        repository.completeCombineSession(combineId, 2_000L)
        assertEquals(1, repository.combineHistory.first().size)
    }
}
