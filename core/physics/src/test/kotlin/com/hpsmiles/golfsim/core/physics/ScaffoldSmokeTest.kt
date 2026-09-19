package com.hpsmiles.golfsim.core.physics

import org.junit.Assert.assertSame
import org.junit.Test

/**
 * M0 scaffold smoke test: proves the pure-JVM test infrastructure runs.
 * Real ball-flight ODE tests arrive in M2.
 */
class ScaffoldSmokeTest {
    @Test
    fun moduleCompilesAndTestsRun() {
        assertSame(BallFlight, BallFlight)
    }
}
