package com.hpsmiles.golfsim.core.scoring

/** Lies the combine scores. GREEN uses the putt table; TEE only ever starts a hole. */
enum class Lie {
    TEE,
    FAIRWAY,
    GREEN,
    ROUGH;

    val distances: DoubleArray
        get() = when (this) {
            TEE -> SgTables.TEE_DISTANCES
            FAIRWAY -> SgTables.FAIRWAY_DISTANCES
            GREEN -> SgTables.PUTT_DISTANCES
            ROUGH -> SgTables.ROUGH_DISTANCES
        }

    fun valuesFor(band: SkillBand): DoubleArray = when (this) {
        TEE -> when (band) {
            SkillBand.TOUR -> SgTables.TEE_TOUR
            SkillBand.SCRATCH -> SgTables.TEE_SCRATCH
            SkillBand.HCP10 -> SgTables.TEE_HCP10
            SkillBand.HCP18 -> SgTables.TEE_HCP18
        }
        FAIRWAY -> when (band) {
            SkillBand.TOUR -> SgTables.FAIRWAY_TOUR
            SkillBand.SCRATCH -> SgTables.FAIRWAY_SCRATCH
            SkillBand.HCP10 -> SgTables.FAIRWAY_HCP10
            SkillBand.HCP18 -> SgTables.FAIRWAY_HCP18
        }
        GREEN -> when (band) {
            SkillBand.TOUR -> SgTables.PUTT_TOUR
            SkillBand.SCRATCH -> SgTables.PUTT_SCRATCH
            SkillBand.HCP10 -> SgTables.PUTT_HCP10
            SkillBand.HCP18 -> SgTables.PUTT_HCP18
        }
        ROUGH -> when (band) {
            SkillBand.TOUR -> SgTables.ROUGH_TOUR
            SkillBand.SCRATCH -> SgTables.ROUGH_SCRATCH
            SkillBand.HCP10 -> SgTables.ROUGH_HCP10
            SkillBand.HCP18 -> SgTables.ROUGH_HCP18
        }
    }
}
