package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertSame
import org.junit.Test

/**
 * M0 scaffold smoke test: proves the pure-JVM test infrastructure runs.
 * Real decoder tests arrive in M1 with the golden BLE captures.
 */
class ScaffoldSmokeTest {
    @Test
    fun moduleCompilesAndTestsRun() {
        assertSame(Mlm2proBle, Mlm2proBle)
    }
}
