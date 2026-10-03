package com.hpsmiles.golfsim.core.physics

/**
 * User-selectable off-green turf (Settings > TURF): the fairway surface used
 * everywhere the ball is not on a green — the range target surroundings and
 * both games' fairway. Greens have their own [GreenCondition].
 *
 * [FIRM] reproduces the historical range surface
 * ([Surface.FAIRWAY_NORMAL] with [Firmness.FIRM]) exactly, so the default
 * selection is a no-op for the range.
 */
enum class TurfCondition(
    val label: String,
    val blurb: String,
) {
    SOFT("Soft", "Lush fairway; shots stop quickly."),
    NORMAL("Normal", "Standard parkland fairway."),
    FIRM("Firm", "Firm practice fairway; punch shots release."),
    LINKS("Very firm \u2013 Links", "Baked-out links turf; maximum run-out.");

    /** The fairway ground-contact surface this condition simulates on. */
    fun surface(): Surface = when (this) {
        SOFT -> Surface.FAIRWAY_NORMAL.withFirmness(Firmness.SOFT)
        NORMAL -> Surface.FAIRWAY_NORMAL
        FIRM -> Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM)
        LINKS -> Surface.FAIRWAY_NORMAL.withFirmness(Firmness.FIRM).copy(
            name = "fairway (links)",
            cor = 0.46,
            thetaCritRad = 0.24,
            spinbackScale = 0.26,
            rollDecelMps2 = 0.55,
        )
    }

    companion object {
        val DEFAULT: TurfCondition = FIRM

        /** Persistence/restore helper: unknown or null names fall back to [DEFAULT]. */
        fun fromName(name: String?): TurfCondition =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
