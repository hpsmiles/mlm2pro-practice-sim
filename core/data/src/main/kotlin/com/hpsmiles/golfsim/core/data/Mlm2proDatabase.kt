package com.hpsmiles.golfsim.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hpsmiles.golfsim.core.data.dao.BagMappingSessionDao
import com.hpsmiles.golfsim.core.data.dao.BagMappingShotDao
import com.hpsmiles.golfsim.core.data.dao.ClubDao
import com.hpsmiles.golfsim.core.data.dao.FittingSessionDao
import com.hpsmiles.golfsim.core.data.dao.FittingShotDao
import com.hpsmiles.golfsim.core.data.dao.GameResultDao
import com.hpsmiles.golfsim.core.data.dao.SessionDao
import com.hpsmiles.golfsim.core.data.dao.ShotDao
import com.hpsmiles.golfsim.core.data.entity.BagMappingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.BagMappingShotEntity
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import com.hpsmiles.golfsim.core.data.entity.FittingSessionEntity
import com.hpsmiles.golfsim.core.data.entity.FittingShotEntity
import com.hpsmiles.golfsim.core.data.entity.GameResultEntity
import com.hpsmiles.golfsim.core.data.entity.SessionEntity
import com.hpsmiles.golfsim.core.data.entity.ShotEntity

@Database(
    entities = [
        SessionEntity::class, ShotEntity::class, ClubEntity::class, GameResultEntity::class,
        BagMappingSessionEntity::class, BagMappingShotEntity::class,
        FittingSessionEntity::class, FittingShotEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class Mlm2proDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun shotDao(): ShotDao
    abstract fun clubDao(): ClubDao
    abstract fun gameResultDao(): GameResultDao
    abstract fun bagMappingSessionDao(): BagMappingSessionDao
    abstract fun bagMappingShotDao(): BagMappingShotDao
    abstract fun fittingSessionDao(): FittingSessionDao
    abstract fun fittingShotDao(): FittingShotDao

    companion object {
        /**
         * M5x spec §3: all four columns ride one migration. The UPDATE block
         * maps the seeded default bag to types; DEFAULT 'IRON' covers anything
         * unmatched. Corrupt-file recovery reuses build(), so it inherits the
         * migration automatically.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clubs ADD COLUMN type TEXT NOT NULL DEFAULT 'IRON'")
                db.execSQL("ALTER TABLE clubs ADD COLUMN isTemp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shots ADD COLUMN excluded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE shots ADD COLUMN clubWasTemp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE clubs SET type='DRIVER' WHERE name='D'")
                db.execSQL("UPDATE clubs SET type='WOOD' WHERE name IN ('3W','5W')")
                db.execSQL("UPDATE clubs SET type='HYBRID' WHERE name='4H'")
                db.execSQL("UPDATE clubs SET type='IRON' WHERE name IN ('4i','5i','6i','7i','8i','9i')")
                db.execSQL("UPDATE clubs SET type='WEDGE' WHERE name IN ('PW','GW','SW','LW')")
            }
        }

        /**
         * M5.5 spec S7: game_results summary table. No DEFAULT clauses -
         * difficulty is genuinely nullable and the rest are NOT NULL, so the
         * entity needs no @ColumnInfo defaultValue (there is nothing to match).
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `game_results` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`mode` TEXT NOT NULL, `difficulty` TEXT, `distanceBin` INTEGER NOT NULL, " +
                        "`score` INTEGER NOT NULL, `source` INTEGER NOT NULL, " +
                        "`playedAtEpochMs` INTEGER NOT NULL)",
                )
            }
        }

        /**
         * M6 spec §7: bag_mapping_sessions + bag_mapping_shots. Existing
         * tables untouched. No DEFAULT clauses — fresh tables, every column
         * is NOT NULL or genuinely nullable, so the entities need no
         * @ColumnInfo defaultValue (game_results pattern). Corrupt-file
         * recovery reuses build(), so it inherits the migration.
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bag_mapping_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startedAtMs` INTEGER NOT NULL, `completedAtMs` INTEGER, " +
                        "`status` TEXT NOT NULL, `clubList` TEXT NOT NULL)",
                )
                db.execSQL(
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bag_mapping_shots_sessionId` " +
                        "ON `bag_mapping_shots` (`sessionId`)",
                )
            }
        }

        /**
         * M7 spec §2: fitting_sessions + fitting_shots (club-fitting mode).
         * Existing tables untouched. Fresh tables — every column NOT NULL or
         * genuinely nullable, except `excluded` which carries DEFAULT 0 (and
         * the entity's @ColumnInfo(defaultValue = "0") must match it). Corrupt-
         * file recovery reuses build(), so it inherits the migration.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fitting_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startedAtMs` INTEGER NOT NULL, `completedAtMs` INTEGER, " +
                        "`status` TEXT NOT NULL)",
                )
                db.execSQL(
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
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_fitting_shots_sessionId` " +
                        "ON `fitting_shots` (`sessionId`)",
                )
            }
        }
    }
}
