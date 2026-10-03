package com.hpsmiles.golfsim.core.physics

/**
 * User-selectable green condition (Settings > GREEN). Blends Stimpmeter speed
 * (roll decel = 5.49/stimp) with firmness (bounce COR + spin-back grip) so the
 * same green can range from a soft receptive parkland surface up to a baked-out
 * Royal-Melbourne links surface.
 *
 * [NORMAL] reproduces the historical [Surface.GREEN_NORMAL] exactly, so the
 * default selection is a no-op change. thetaCrit / spinRetention / the
 * spin-dominance gate are held constant across presets — cor, spinbackScale and
 * stimp are the three feel levers (bounce/reception, wedge bite, roll speed).
 */
enum class GreenCondition(
    val label: String,
    val blurb: String,
    private val stimp: Double,
    private val cor: Double,
    private val spinbackScale: Double,
) {
    SOFT(
        label = "Soft & receptive",
        blurb = "Holds approach shots; wedges check and spin.",
        stimp = 10.4,
        cor = 0.26,
        spinbackScale = 1.30,
    ),
    NORMAL(
        label = "Normal",
        blurb = "Balanced parkland green.",
        stimp = 11.0,
        cor = 0.30,
        spinbackScale = 1.12,
    ),
    SANDBELT(
        label = "Firm \u2013 Sandbelt",
        blurb = "Firm and quick; approaches release a little.",
        stimp = 11.5,
        cor = 0.34,
        spinbackScale = 0.88,
    ),
    ROYAL_MELBOURNE(
        label = "Very firm \u2013 Royal Melbourne",
        blurb = "Baked out; balls release and run off.",
        stimp = 12.5,
        cor = 0.38,
        spinbackScale = 0.70,
    );

    /** The ground-contact surface this condition simulates on. */
    fun surface(): Surface = Surface.green(stimp).copy(
        cor = cor,
        spinbackScale = spinbackScale,
    )

    companion object {
        val DEFAULT: GreenCondition = NORMAL

        /** Persistence/restore helper: unknown or null names fall back to [DEFAULT]. */
        fun fromName(name: String?): GreenCondition =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
