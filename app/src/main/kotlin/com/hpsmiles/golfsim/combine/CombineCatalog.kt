package com.hpsmiles.golfsim.combine

/** Driver station hole baseline in metres (scoring-only, invisible in the scene). */
const val DRIVER_HOLE_M = 340.0

enum class StationKind { APPROACH, DRIVER }

data class PinOffset(val dx: Double, val dy: Double)

data class CombineStation(
    val label: String,
    val startDistanceM: Double,
    val kind: StationKind,
    val greenRadiusM: Double = DEFAULT_GREEN_RADIUS_M,
    val pins: List<PinOffset> = emptyList(),
) {
    companion object {
        // ~26 m diameter so the green sits inside the 28 m fairway
        // (HALF_WIDTH_M = 14). This is BOTH the visual and the scoring/lie
        // radius — the app's "what you see is what you score" rule.
        const val DEFAULT_GREEN_RADIUS_M = 13.0
    }
}

data class CombineCatalogEntry(
    val id: String,
    val name: String,
    val blurb: String,
    val stations: List<CombineStation>,
    val layoutVersion: Int = 1,
)

object CombineCatalog {

    // Pins are always centred so shots can be aimed straight down the line on the
    // sim (an off-centre pin made the target impossible to aim at).
    val visitPins = listOf(PinOffset(0.0, 0.0))

    val entries: List<CombineCatalogEntry> = listOf(
        CombineCatalogEntry(
            id = "WEDGES",
            name = "WEDGES",
            blurb = "50 / 70 / 90 m scoring wedges against the pin",
            stations = listOf(
                CombineStation("50 m", 50.0, StationKind.APPROACH, pins = visitPins),
                CombineStation("70 m", 70.0, StationKind.APPROACH, pins = visitPins),
                CombineStation("90 m", 90.0, StationKind.APPROACH, pins = visitPins),
            ),
        ),
        CombineCatalogEntry(
            id = "APPROACH",
            name = "APPROACH",
            blurb = "100 m / 150 m approaches and one drive",
            stations = listOf(
                CombineStation("100 m", 100.0, StationKind.APPROACH, pins = visitPins),
                CombineStation("150 m", 150.0, StationKind.APPROACH, pins = visitPins),
                CombineStation("DRIVER", DRIVER_HOLE_M, StationKind.DRIVER),
            ),
        ),
    )

    fun byId(id: String): CombineCatalogEntry? = entries.firstOrNull { it.id == id }
}
