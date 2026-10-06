package com.hpsmiles.golfsim.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stored bag-mapping shot (M6 spec §7). ALL session shots are stored —
 * kept AND filtered — so the RESULT drill-down can show every strike.
 * Raw block = BallData field-for-field; carry/total are physics caches
 * written once at capture (same discipline as `shots`). Snapshot columns
 * (`clubName`, `clubType`) mean club renames/deletes never rewrite mapping
 * history. `sessionId` is a plain indexed column, no hard FK — the
 * repository manages lifecycle (existing entity conventions).
 */
@Entity(tableName = "bag_mapping_shots", indices = [Index("sessionId")])
data class BagMappingShotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val clubName: String,
    val clubType: String,          // ClubType.name snapshot
    val timestampMs: Long,
    // raw block
    val clubHeadSpeedMps: Double,
    val ballSpeedMps: Double,
    val launchAngleDeg: Double,
    val launchDirDeg: Double,
    val spinAxisDeg: Double,
    val totalSpinRpm: Int,
    // physics caches
    val carryM: Double,
    val totalM: Double,
    /** Recomputed duff-filter cache (BagMappingStats); rewritten on every club write. */
    val filtered: Boolean = false,
    /** Null when kept; DUFF_LOW_BALL_SPEED when filtered. */
    val filterReason: String? = null,
)
