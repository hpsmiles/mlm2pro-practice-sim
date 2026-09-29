package com.hpsmiles.golfsim.core.data

import android.database.sqlite.SQLiteDatabase
import com.hpsmiles.golfsim.core.data.entity.GameModes
import com.hpsmiles.golfsim.core.data.record.ShotSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * MIGRATION_2_3 against a hand-built v2 file (same pattern as
 * MigrationFrom1Test - no MigrationTestHelper in this repo). After opening,
 * a game result can be saved and read back through Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MigrationFrom2Test {

    @Test
    fun `v2 file migrates - game_results usable - prior data intact`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        // v2 DDL (sessions, shots, clubs) - mirrors schemas/.../2.json
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
        raw.version = 2
        raw.close()

        val repo = SessionRepository.open(context)
        repo.initializeAndRestore() // migration runs here; prior data must survive

        repo.saveGameResult(
            mode = GameModes.TARGET_PRACTICE,
            difficulty = "MEDIUM",
            distanceM = 143.0,
            score = 85,
            source = ShotSource.LIVE,
            playedAtEpochMs = 2000,
        )
        repo.saveGameResult(
            mode = GameModes.BREAK_PANE,
            difficulty = null,
            distanceM = 137.0,
            score = 12,
            source = ShotSource.DEMO,
            playedAtEpochMs = 3000,
        )

        val all = repo.gameResultDao().getAll()
        assertEquals(2, all.size)
        val tp = all.first { it.mode == GameModes.TARGET_PRACTICE }
        assertEquals("MEDIUM", tp.difficulty)
        assertEquals(140, tp.distanceBin) // 143 rounds to nearest 10
        assertEquals(85, tp.score)
        assertEquals(0, tp.source)
        val bp = all.first { it.mode == GameModes.BREAK_PANE }
        assertEquals(null, bp.difficulty)
        assertEquals(140, bp.distanceBin) // 137 rounds to 140
        assertEquals(12, bp.score)
        assertEquals(1, bp.source)
    }
}
