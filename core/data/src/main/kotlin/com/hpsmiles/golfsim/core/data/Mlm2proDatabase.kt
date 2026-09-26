package com.hpsmiles.golfsim.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hpsmiles.golfsim.core.data.dao.ClubDao
import com.hpsmiles.golfsim.core.data.dao.SessionDao
import com.hpsmiles.golfsim.core.data.dao.ShotDao
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import com.hpsmiles.golfsim.core.data.entity.SessionEntity
import com.hpsmiles.golfsim.core.data.entity.ShotEntity

@Database(
    entities = [SessionEntity::class, ShotEntity::class, ClubEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class Mlm2proDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun shotDao(): ShotDao
    abstract fun clubDao(): ClubDao

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
    }
}
