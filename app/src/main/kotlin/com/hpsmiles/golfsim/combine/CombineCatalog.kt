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
        const val DEFAULT_GREEN_RADIUS_M = 15.0
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

    val visitPins = listOf(
        PinOffset(0.0, 0.0),
        PinOffset(2.2, -1.4),
        PinOffset(-1.8, 1.9),
    )

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
