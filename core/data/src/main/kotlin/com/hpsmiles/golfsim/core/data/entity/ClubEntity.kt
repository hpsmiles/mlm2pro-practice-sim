package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The editable bag. Shot rows snapshot names; renaming never rewrites history. */
@Entity(tableName = "clubs", indices = [Index(value = ["name"], unique = true)])
data class ClubEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
)
