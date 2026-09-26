package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One accepted shot. Raw block = source of truth (BallData field-for-field,
 * unknowns preserved for the offsets-12–15 distance experiment); scalar
 * block = physics cache written once at capture, never recomputed for lists.
 */
@Entity(
    tableName = "shots",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId")],
)
data class ShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val seq: Int,
    val timestampMs: Long,
    val source: Int,               // ShotSource.code
    val clubName: String?,         // null = untagged
    // raw block
    val clubHeadSpeedMps: Double,
    val ballSpeedMps: Double,
    val launchDirDeg: Double,
    val launchAngleDeg: Double,
    val spinAxisDeg: Double,
    val spinRpm: Int,
    val unknown1: Int,
    val unknown2: Int,
    // cached scalars
    val carryM: Double,
    val totalM: Double,
    val sideM: Double,
    val apexM: Double,
    val flightTimeSec: Double,
)
