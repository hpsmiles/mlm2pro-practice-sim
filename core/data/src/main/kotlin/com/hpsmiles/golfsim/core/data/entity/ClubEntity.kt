package com.hpsmiles.golfsim.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The editable bag. Shot rows snapshot names; renaming never rewrites history.
 * v2: type drives type-first bag ordering; isTemp marks a TEST club purged
 * when END SESSION runs (shots keep their own snapshots).
 */
@Entity(tableName = "clubs", indices = [Index(value = ["name"], unique = true)])
data class ClubEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int,
    @ColumnInfo(defaultValue = "IRON") val type: String = "IRON",
    @ColumnInfo(defaultValue = "0") val isTemp: Boolean = false,
)
