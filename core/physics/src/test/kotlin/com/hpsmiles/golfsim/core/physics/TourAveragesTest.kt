package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Properties

/**
 * The M2 exit criterion (spec §8): the engine reproduces the pinned prototype
 * table (locked config: tau=12, sdCoeff=8, sdCap=1.55, msBoostMax=0, fairway
 * roll mu=0.030, spin-back scale=0.35) for all 8 Tour shots. Tolerances are
 * printed-precision only: +/-0.1 yd on carry/rollout/total, +/-0.05 m apex,
 * +/-0.01 s flight time. Any shift is a deliberate recalibration requiring a
 * fixture update plus a spec note.
 */
@RunWith(Parameterized::class)
class TourAveragesTest(private val fixture: File) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): Collection<File> {
            val files = mutableListOf<File>()
            val urls = TourAveragesTest::class.java.classLoader.getResources("tour")
            while (urls.hasMoreElements()) {
                // toURI() URL-decodes the '%20' in the workspace path (M1 lesson).
                val dir = File(urls.nextElement().toURI())
                files += dir.listFiles()!!.filter { it.name.endsWith(".properties") }
            }
            return files.sortedBy { it.name }
        }
    }

    @Test
    fun matchesPinnedPrototypeNumbers() {
        val props = Properties()
        fixture.inputStream().use { props.load(it) }

        val launch = LaunchConditions(
            ballSpeedMps = props.getProperty("ballSpeedMph").toDouble() * 0.44704,
            launchAngleDeg = props.getProperty("launchAngleDeg").toDouble(),
            spinRpm = props.getProperty("spinRpm").toInt(),
        )
        val result = BallFlightEngine.simulate(launch)

        assertEquals(
            "${fixture.name} carry",
            props.getProperty("expectedCarryYd").toDouble(),
            result.carryM / BallPhysical.METERS_PER_YARD,
            0.1,
        )
        assertEquals(
            "${fixture.name} rollout",
            props.getProperty("expectedRolloutYd").toDouble(),
            result.rolloutM / BallPhysical.METERS_PER_YARD,
            0.1,
        )
        assertEquals(
            "${fixture.name} total",
            props.getProperty("expectedTotalYd").toDouble(),
            result.totalM / BallPhysical.METERS_PER_YARD,
            0.1,
        )
        assertEquals(
            "${fixture.name} apex",
            props.getProperty("expectedApexM").toDouble(),
            result.apexM,
            0.05,
        )
        assertEquals(
            "${fixture.name} flight time",
            props.getProperty("expectedFlightTimeSec").toDouble(),
            result.flightTimeSec,
            0.01,
        )
    }
}
