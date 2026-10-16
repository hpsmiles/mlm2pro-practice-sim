package com.hpsmiles.golfsim.core.physics

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * One-off engine-vs-engine comparison harness (ours vs OpenGolfSim/fuse,
 * 2026-10-16). Reads launch rows from the FUSE_COMPARE_IN TSV and writes our
 * simulated outcomes to the FUSE_COMPARE_OUT TSV. Vacuous pass unless BOTH
 * env vars are set, so the normal unit-test suite is unaffected.
 *
 * Input columns (0-based, matches WS/convert-benchmark.mjs):
 * 0 id, 1 clubName, 2 ballSpeedMps, 3 launchAngleDeg, 4 launchDirDeg,
 * 5 spinAxisDeg, 6 totalSpinRpm, 7 cachedCarryM, 8 cachedTotalM,
 * 9 filtered, 10 includeOverride, then optional v2 env/apex columns:
 * 11 tempC, 12 pressureHpa, 13 airDensityKgM3 (unused here - fuse only),
 * 14 measuredApexM. Bag TSVs stop at col 10 -> default env.
 *
 * Output columns: id, status, carryM, rolloutM, totalM, sideM, apexM,
 * flightTimeSec, carryCacheDeltaM, totalCacheDeltaM. status = OK, or
 * ERROR:<reason> (LaunchConditions guard rejected the row) with empty metrics.
 * Deltas are blank when col 7/8 is blank (e.g. Mevo has no total distance).
 *
 * Conditions pinned to match the fuse harness: fairway everywhere,
 * zero wind, dt = engine default 10 ms. Ambient = per-shot columns 11/12
 * when present (benchmark TSVs), else default (15 C / 1013.25 hPa).
 */
class FuseCompareHarnessTest {

    @Test
    fun runsOnlyWhenEnvVarsSet() {
        val inPath = System.getenv("FUSE_COMPARE_IN")
        val outPath = System.getenv("FUSE_COMPARE_OUT")
        assumeTrue(
            "FUSE_COMPARE_IN/OUT not set - one-off harness idle",
            !inPath.isNullOrBlank() && !outPath.isNullOrBlank(),
        )

        val lines = File(inPath).readLines()
        val out = StringBuilder()
        out.appendLine(
            "id\tstatus\tcarryM\trolloutM\ttotalM\tsideM\tapexM\tflightTimeSec\t" +
                "carryCacheDeltaM\ttotalCacheDeltaM",
        )
        for (line in lines.drop(1)) {
            if (line.isBlank()) continue
            val c = line.split('\t')
            val id = c.getOrElse(0) { "?" }
            if (c.size < 9) {
                out.appendLine("$id\tERROR:bad-row\t\t\t\t\t\t\t\t")
                continue
            }
            try {
                val launch = LaunchConditions(
                    ballSpeedMps = c[2].toDouble(),
                    launchAngleDeg = c[3].toDouble(),
                    launchDirDeg = c[4].toDouble(),
                    spinAxisDeg = c[5].toDouble(),
                    spinRpm = c[6].toDouble().toInt(),
                )
                val env = if (c.size >= 13 && c[11].isNotBlank() && c[12].isNotBlank()) {
                    Environment(temperatureC = c[11].toDouble(), pressureHpa = c[12].toDouble())
                } else {
                    Environment()
                }
                val r = BallFlightEngine.simulate(launch, env)
                val cc = c[7].toDoubleOrNull()
                val ct = c[8].toDoubleOrNull()
                val carryDelta = cc?.let { r.carryM - it } ?: ""
                val totalDelta = ct?.let { r.totalM - it } ?: ""
                out.appendLine(
                    listOf(
                        id, "OK",
                        r.carryM, r.rolloutM, r.totalM, r.sideM, r.apexM, r.flightTimeSec,
                        carryDelta, totalDelta,
                    ).joinToString("\t"),
                )
            } catch (e: IllegalArgumentException) {
                val reason = (e.message ?: "rejected").replace('\t', ' ').replace('\n', ' ')
                out.appendLine("$id\tERROR:$reason\t\t\t\t\t\t\t\t")
            }
        }
        val outFile = File(outPath)
        outFile.parentFile?.mkdirs()
        outFile.writeText(out.toString())
    }
}
