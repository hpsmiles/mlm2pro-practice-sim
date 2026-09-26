package com.hpsmiles.golfsim.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.hpsmiles.golfsim.core.data.dao.ClubDao
import com.hpsmiles.golfsim.core.data.dao.SessionDao
import com.hpsmiles.golfsim.core.data.dao.ShotDao
import com.hpsmiles.golfsim.core.data.entity.ClubEntity
import com.hpsmiles.golfsim.core.data.entity.SessionEntity
import com.hpsmiles.golfsim.core.data.entity.ShotEntity

@Database(
    entities = [SessionEntity::class, ShotEntity::class, ClubEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class Mlm2proDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun shotDao(): ShotDao
    abstract fun clubDao(): ClubDao
}
