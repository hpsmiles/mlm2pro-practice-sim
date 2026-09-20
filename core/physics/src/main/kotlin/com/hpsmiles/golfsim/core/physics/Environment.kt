package com.hpsmiles.golfsim.core.physics

/**
 * Ambient conditions. Air density via ideal-gas law: rho = P / (287.05 · T).
 * Wind is horizontal in the scene frame (x lateral, y down-range).
 */
data class Environment(
    val windXmps: Double = 0.0,
    val windYmps: Double = 0.0,
    val temperatureC: Double = 15.0,
    val pressureHpa: Double = 1013.25,
) {
    init {
        require(Math.hypot(windXmps, windYmps) <= 40.0) {
            "wind ${Math.hypot(windXmps, windYmps)} m/s exceeds 40"
        }
        require(temperatureC > -40.0 && temperatureC < 60.0) { "temperature $temperatureC C outside [-40, 60]" }
        require(pressureHpa > 0.0) { "pressure must be positive, got $pressureHpa hPa" }
    }

    val airDensity: Double
        get() = (pressureHpa * 100.0) / (287.05 * (temperatureC + 273.15))
}
