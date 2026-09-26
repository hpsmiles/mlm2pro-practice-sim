package com.hpsmiles.golfsim.core.data.record

/**
 * Club types in bag order (long → short). Persisted as [name] in
 * `clubs.type`; declaration order drives type-first bag ordering.
 */
enum class ClubType(val label: String) {
    DRIVER("Driver"),
    WOOD("Wood"),
    HYBRID("Hybrid"),
    DRIVING_IRON("Driving Iron"),
    IRON("Iron"),
    WEDGE("Wedge"),
    PUTTER("Putter");

    companion object {
        /** Unknown strings (older rows, tampering) fall back to IRON. */
        fun fromName(value: String): ClubType = entries.firstOrNull { it.name == value } ?: IRON
    }
}
