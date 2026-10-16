package com.hpsmiles.golfsim.core.data

import android.database.sqlite.SQLiteDatabase
import com.hpsmiles.golfsim.core.data.record.ClubRecord
import com.hpsmiles.golfsim.core.data.record.ClubType
import com.hpsmiles.golfsim.core.data.record.ShotSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * MIGRATION_7_8 against a hand-built v7 file (same pattern as the earlier
 * MigrationFrom*Tests — no MigrationTestHelper in this repo). Prior
 * sessions/shots/clubs/game results/bag_mapping/fitting AND combine data must
 * survive; the new nullable includeOverride column on bag_mapping_shots must
 * read back null (follow the auto gate) for pre-v8 rows and be settable
 * through the repository immediately after.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MigrationFrom7Test {

    @Test
    fun `v7 file migrates - include override usable - prior data intact`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // v7 DDL — sessions/shots/clubs/game_results copied verbatim from
        // MigrationFrom3Test; bag_mapping_* copied verbatim from MIGRATION_3_4;
        // fitting_* copied verbatim from MIGRATION_4_5; combine_* copied
        // verbatim from MIGRATION_5_6 + MIGRATION_6_7 (restXM/restYM present).
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
                "`restDistanceM` REAL NOT NULL, " +
                "`restXM` REAL, `restYM` REAL)",
        )
        raw.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_combine_shots_sessionId` ON `combine_shots` (`sessionId`)",
        )
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        raw.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, '39dcbc1f80f3838e32ca3a66455cbb61')",
        )
        raw.execSQL("INSERT INTO sessions(startedAtEpochMs, endedAtEpochMs, title, misreadCount) VALUES(1000, NULL, NULL, 0)")
        raw.execSQL("INSERT INTO clubs(name, sortOrder, type, isTemp) VALUES('7i', 7, 'IRON', 0)")
        raw.execSQL(
            "INSERT INTO shots(sessionId, seq, timestampMs, source, clubName, clubHeadSpeedMps, " +
                "ballSpeedMps, launchDirDeg, launchAngleDeg, spinAxisDeg, spinRpm, unknown1, unknown2, " +
                "carryM, totalM, sideM, apexM, flightTimeSec, excluded, clubWasTemp) " +
                "VALUES(1, 0, 1000, 0, '7i', 33.0, 48.0, -1.0, 12.0, -4.0, 8000, 5, 10, 140.0, 150.0, -3.0, 27.0, 6.0, 0, 0)",
        )
        // A pre-v8 completed bag-mapping session with one filtered shot — it must
        // survive and read back with a null includeOverride (follow the gate).
        raw.execSQL(
            "INSERT INTO bag_mapping_sessions(startedAtMs, completedAtMs, status, clubList) " +
                "VALUES(500, 600, 'COMPLETED', '7i:IRON')",
        )
        raw.execSQL(
            "INSERT INTO bag_mapping_shots(sessionId, clubName, clubType, timestampMs, clubHeadSpeedMps, " +
                "ballSpeedMps, launchAngleDeg, launchDirDeg, spinAxisDeg, totalSpinRpm, carryM, totalM, " +
                "filtered, filterReason) " +
                "VALUES(1, '7i', 'IRON', 550, 33.0, 20.0, 12.0, -1.0, -4.0, 8000, 100.0, 110.0, 1, 'DUFF_LOW_BALL_SPEED')",
        )
        raw.execSQL(
            "INSERT INTO combine_sessions(combineId, combineName, startedAtMs, completedAtMs, source, layoutVersion) " +
                "VALUES('WEDGES', 'WEDGES', 500, 600, 0, 1)",
        )
        raw.execSQL(
            "INSERT INTO combine_shots(sessionId, shotIndex, stationIndex, restLie, restDistanceM, restXM, restYM) " +
                "VALUES(1, 0, 0, 'FAIRWAY', 12.0, 1.5, 100.0)",
        )
        raw.version = 7
        raw.close()

        val repository = SessionRepository.open(context)
        repository.initializeAndRestore() // migration runs here; prior data must survive

        // Prior range data survives.
        val summaries = repository.summaries.first()
        assertEquals(1, summaries.size)
        assertEquals(1, summaries.single().shotCount)

        // The pre-v8 bag shot survives and reads back with a null override.
        val priorBagShots = repository.observeBagMappingShots(1).first()
        assertEquals(1, priorBagShots.size)
        assertNull(priorBagShots.single().includeOverride)
        assertTrue(priorBagShots.single().filtered)
        assertEquals("DUFF_LOW_BALL_SPEED", priorBagShots.single().filterReason)

        // The new column is usable through the repository immediately.
        val bagSession = repository.startBagMappingSession(
            listOf(ClubRecord(1, "7i", ClubType.IRON, false)),
            startedAtMs = 1_000L,
        )
        assertNotNull(bagSession)
        val shotId = repository.appendBagMappingShot(
            bagSession!!, "7i", ClubType.IRON,
            com.hpsmiles.golfsim.core.ble.BallData(
                clubHeadSpeed = 42.7, ballSpeed = 62.0, launchDirection = -1.0,
                launchAngle = 12.0, spinAxis = -4.0, totalSpin = 8000, unknown1 = 5, unknown2 = 10,
            ),
            carryM = 140.0, totalM = 150.0, timestampMs = 1_100L,
        )
        assertNotNull(shotId)
        assertNull(repository.observeBagMappingShots(bagSession).first().single().includeOverride)
        repository.setBagMappingShotIncludeOverride(shotId!!, true)
        val overridden = repository.observeBagMappingShots(bagSession).first().single()
        assertTrue(overridden.includeOverride!!)
        assertTrue(overridden.effectiveIncluded())
        repository.setBagMappingShotIncludeOverride(shotId, null)
        assertNull(repository.observeBagMappingShots(bagSession).first().single().includeOverride)
        assertFalse(repository.observeBagMappingShots(bagSession).first().single().filtered)

        // Pre-v8 combine data survives with coordinates intact.
        assertEquals(1, repository.combineHistory.first().size)
        val priorCombine = repository.observeCombineShots(1).first()
        assertEquals(1.5, priorCombine.single().restXM!!, 1e-9)
        assertEquals(100.0, priorCombine.single().restYM!!, 1e-9)

        // All prior tables intact: a fitting + game result write still lands.
        val fitSession = repository.startFittingSession(1_200L)
        assertNotNull(fitSession)
        repository.completeFittingSession(fitSession!!, 1_300L)
        assertEquals(1, repository.fittingHistory.first().size)
        repository.saveGameResult("TARGET", "EASY", 100.0, 50, ShotSource.LIVE, 1_400L)
        assertEquals(1, repository.gameResults.first().size)
    }
}
