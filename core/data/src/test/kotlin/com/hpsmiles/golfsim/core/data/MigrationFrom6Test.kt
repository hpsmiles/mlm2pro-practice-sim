package com.hpsmiles.golfsim.core.data

import android.database.sqlite.SQLiteDatabase
import com.hpsmiles.golfsim.core.data.record.ShotSource
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
 * MIGRATION_6_7 against a hand-built v6 file (same pattern as the earlier
 * MigrationFrom*Tests - no MigrationTestHelper in this repo). Prior
 * sessions/shots/clubs/game results/bag_mapping/fitting AND combine data must
 * survive; the new nullable restXM/restYM columns must be usable through the
 * repository immediately after (M8 post-ship: start -> append with coordinates
 * -> complete), and pre-v7 combine shots must read back with null coordinates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MigrationFrom6Test {

    @Test
    fun `v6 file migrates - rest coordinates usable - prior data intact`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // v6 DDL — sessions/shots/clubs/game_results copied verbatim from
        // MigrationFrom3Test; bag_mapping_* copied verbatim from MIGRATION_3_4;
        // fitting_* copied verbatim from MIGRATION_4_5; combine_* copied verbatim
        // from MIGRATION_5_6.
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
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `combine_sessions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`combineId` TEXT NOT NULL, " +
                "`combineName` TEXT NOT NULL, " +
                "`startedAtMs` INTEGER NOT NULL, " +
                "`completedAtMs` INTEGER, " +
                "`source` INTEGER NOT NULL, " +
                "`layoutVersion` INTEGER NOT NULL)",
        )
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `combine_shots` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`shotIndex` INTEGER NOT NULL, " +
                "`stationIndex` INTEGER NOT NULL, " +
                "`restLie` TEXT NOT NULL, " +
                "`restDistanceM` REAL NOT NULL)",
        )
        raw.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_combine_shots_sessionId` ON `combine_shots` (`sessionId`)",
        )
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        raw.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, '17cab40587ae91cd3eb47478667e832e')",
        )
        raw.execSQL("INSERT INTO sessions(startedAtEpochMs, endedAtEpochMs, title, misreadCount) VALUES(1000, NULL, NULL, 0)")
        raw.execSQL("INSERT INTO clubs(name, sortOrder, type, isTemp) VALUES('7i', 7, 'IRON', 0)")
        raw.execSQL(
            "INSERT INTO shots(sessionId, seq, timestampMs, source, clubName, clubHeadSpeedMps, " +
                "ballSpeedMps, launchDirDeg, launchAngleDeg, spinAxisDeg, spinRpm, unknown1, unknown2, " +
                "carryM, totalM, sideM, apexM, flightTimeSec, excluded, clubWasTemp) " +
                "VALUES(1, 0, 1000, 0, '7i', 33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10, 140.0, 150.0, -3.0, 27.0, 6.0, 0, 0)",
        )
        // A pre-v7 completed combine session with one shot that has no coordinates.
        raw.execSQL(
            "INSERT INTO combine_sessions(combineId, combineName, startedAtMs, completedAtMs, source, layoutVersion) " +
                "VALUES('WEDGES', 'WEDGES', 500, 600, 0, 1)",
        )
        raw.execSQL(
            "INSERT INTO combine_shots(sessionId, shotIndex, stationIndex, restLie, restDistanceM) " +
                "VALUES(1, 0, 0, 'FAIRWAY', 12.0)",
        )
        raw.version = 6
        raw.close()

        val repository = SessionRepository.open(context)
        repository.initializeAndRestore() // migration runs here; prior data must survive

        // Prior range data survives.
        val summaries = repository.summaries.first()
        assertEquals(1, summaries.size)
        assertEquals(1, summaries.single().shotCount)

        // The pre-v7 combine shot survives and reads back with null coordinates.
        val priorShots = repository.observeCombineShots(1).first()
        assertEquals(1, priorShots.size)
        assertNull(priorShots.single().restXM)
        assertNull(priorShots.single().restYM)

        // The new columns are usable through the repository immediately.
        val combineSession = repository.startCombineSession("APPROACH", "APPROACH", 1, 1_000L, ShotSource.LIVE)
        assertNotNull(combineSession)
        val combineId = combineSession!!
        for (index in 0 until 18) {
            repository.appendCombineShot(
                combineId, index, index / 6, "FAIRWAY", 12.0,
                restXM = 1.5 + index, restYM = 100.0 + index,
            )
        }
        val shots = repository.observeCombineShots(combineId).first()
        assertEquals(18, shots.size)
        assertEquals(1.5, shots.first().restXM!!, 1e-9)
        assertEquals(100.0, shots.first().restYM!!, 1e-9)
        repository.completeCombineSession(combineId, 2_000L)
        // The pre-v7 WEDGES session and the new APPROACH session are both complete.
        assertEquals(2, repository.combineHistory.first().size)
    }
}
