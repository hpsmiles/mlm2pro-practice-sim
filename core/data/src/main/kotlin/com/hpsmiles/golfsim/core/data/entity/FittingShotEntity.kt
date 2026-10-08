package com.hpsmiles.golfsim.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stored fitting shot (M7 spec §2). ALL shots are stored — excluded ones
 * stay visible (greyed) in the drill-down. Club snapshot columns mean the
 * end-of-session temp-club purge never rewrites fitting history (same trick
 * as ShotEntity.clubWasTemp). No hard FK on sessionId — the repository
 * manages lifecycle (existing entity conventions).
 */
@Entity(tableName = "fitting_shots", indices = [Index("sessionId")])
data class FittingShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val clubId: Long,              // snapshot; the club row may be purged later
    val clubName: String,
    val clubType: String,          // ClubType.name snapshot
    val clubWasTemp: Boolean,
    val timestampMs: Long,
    // raw block (BallData field-for-field)
    val clubHeadSpeedMps: Double,
    val ballSpeedMps: Double,
    val launchAngleDeg: Double,
    val launchDirDeg: Double,
    val spinAxisDeg: Double,
    val totalSpinRpm: Int,
    // physics caches
    val carryM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
    /** Excluded = outlier/mishit; drops from every aggregate, dot and ring. */
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
)
