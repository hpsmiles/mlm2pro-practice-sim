// core/connect/src/test/kotlin/com/hpsmiles/golfsim/core/connect/SecretProviderTest.kt
package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Well-formedness ONLY — the plaintext secret value is never printed or
 * asserted exactly (spec rule: plaintext never appears in tests or logs).
 */
class SecretProviderTest {

    @Test
    fun apiSecretIsWellFormed() {
        val secret = SecretProvider.apiSecret()
        assertTrue("length out of expected range", secret.length in 20..80)
        assertTrue(
            "contains non-printable characters",
            secret.matches(Regex("[A-Za-z0-9+/=_-]+")),
        )
    }

    @Test
    fun apiSecretIsStableAcrossCalls() {
        // Two calls return equal values (deterministic decrypt, no randomness).
        assertTrue(SecretProvider.apiSecret() == SecretProvider.apiSecret())
    }
}
