package com.hpsmiles.golfsim.core.data

import android.database.sqlite.SQLiteDatabase
import com.hpsmiles.golfsim.core.data.record.ClubType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * MIGRATION_1_2 against a hand-built v1 file. This repo deliberately has no
 * androidx.test dependency, so no MigrationTestHelper: the v1 database is
 * created with raw SQLite (DDL + identity hash copied from the committed
 * schemas/com.hpsmiles.golfsim.core.data.Mlm2proDatabase/1.json), then
 * SessionRepository.open() must migrate it in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class MigrationFrom1Test {

    @Test
    fun `v1 file migrates - seed types map - shots survive`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val dbFile = context.getDatabasePath("golfsim.db")
        dbFile.parentFile!!.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
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
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        raw.execSQL("CREATE INDEX IF NOT EXISTS `index_shots_sessionId` ON `shots` (`sessionId`)")
        raw.execSQL(
            "CREATE TABLE IF NOT EXISTS `clubs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL)",
        )
        raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_clubs_name` ON `clubs` (`name`)")
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        raw.execSQL(
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, '81cb00e84749894bed35d33f1dd256c4')",
        )
        raw.execSQL(
            "INSERT INTO sessions(startedAtEpochMs, endedAtEpochMs, title, misreadCount) " +
                "VALUES(1000, NULL, NULL, 0)",
        )
        raw.execSQL("INSERT INTO clubs(name, sortOrder) VALUES('D',0),('3W',1),('7i',7),('PW',10),('MyWedge',99)")
        raw.execSQL(
            "INSERT INTO shots(sessionId, seq, timestampMs, source, clubName, clubHeadSpeedMps, " +
                "ballSpeedMps, launchDirDeg, launchAngleDeg, spinAxisDeg, spinRpm, unknown1, unknown2, " +
                "carryM, totalM, sideM, apexM, flightTimeSec) VALUES(1, 0, 1000, 0, '7i', 33.0, 48.0, " +
                "-1.0, 12.0, -4.0, 8000, 5, 10, 140.0, 150.0, -3.0, 27.0, 6.0)",
        )
        raw.version = 1
        raw.close()

        val repo = SessionRepository.open(context)
        // Open session exists (endedAt NULL): restore must return its shot intact.
        val restored = repo.initializeAndRestore()
        assertEquals(1, restored!!.shots.size)
        assertEquals("7i", restored.shots.single().clubName)
        assertFalse(restored.shots.single().excluded)     // new column default 0
        assertFalse(restored.shots.single().clubWasTemp)  // new column default 0

        // v1 seed mapping: D→DRIVER, 3W→WOOD, 7i→IRON (explicit UPDATE),
        // PW→WEDGE; MyWedge (unmatched) covers the column-DEFAULT 'IRON'
        // population + fromName fallback path.
        val types = repo.clubs.first().associate { it.name to it.type }
        assertEquals(ClubType.DRIVER, types["D"])
        assertEquals(ClubType.WOOD, types["3W"])
        assertEquals(ClubType.IRON, types["7i"])
        assertEquals(ClubType.WEDGE, types["PW"])
        assertEquals(ClubType.IRON, types["MyWedge"])
        assertEquals(ClubType.IRON, ClubType.fromName("zgarbage")) // safe fallback
        assertFalse(repo.persistError.value)
    }
}
