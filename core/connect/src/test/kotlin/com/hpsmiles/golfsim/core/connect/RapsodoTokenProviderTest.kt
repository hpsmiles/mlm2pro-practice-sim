package com.hpsmiles.golfsim.core.connect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * parseToken against the REAL Rapsodo API response shape, verified in the
 * bench session (attempt 5) against the documented reference
 * (Duwaynef/MLM2PRO-BT-APP mlm2pro.md "API information"):
 * {"success":true,"user":{"id":123456,"token":"1043255814","expireDate":...}}
 * The token arrives as a QUOTED numeric string.
 */
class RapsodoTokenProviderTest {

    @Test
    fun parsesQuotedTokenStringFromReferenceResponseShape() {
        val body = """{"success":true,"user":{"id":319952,"token":"1043255814","expireDate":1711562523}}"""
        assertEquals(1043255814, HttpRapsodoTokenProvider.parseToken(body))
    }

    @Test
    fun parsesUnquotedIntegerToken() {
        val body = """{"token": 4711}"""
        assertEquals(4711, HttpRapsodoTokenProvider.parseToken(body))
    }

    @Test
    fun parsesTokenAboveIntMaxAsUnsigned32BitPattern() {
        // Real shed capture 2026-09-24: token 2951339326 fits unsigned-32 but
        // overflows Int.MAX; parse must preserve the uint32 bit pattern (the
        // wire encoding in CommandEncoder is LE32 of the same bits).
        val body = """{"success":true,"user":{"id":76087,"token":"2951339326","expireDate":1790320999}}"""
        assertEquals(2951339326L.toLong().toInt(), HttpRapsodoTokenProvider.parseToken(body))
    }

    @Test
    fun returnsNullWhenNoTokenField() {
        val body = """{"success":false,"message":"not authorized"}"""
        assertNull(HttpRapsodoTokenProvider.parseToken(body))
    }

    @Test
    fun returnsNullForNonNumericToken() {
        val body = """{"user":{"token":"not-a-number"}}"""
        assertNull(HttpRapsodoTokenProvider.parseToken(body))
    }
}
