package com.hpsmiles.golfsim.range

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectFailureHintsTest {

    @Test
    fun `scan timeout sentinel maps to no monitor found`() {
        assertEquals(
            "NO MONITOR FOUND",
            ConnectFailureHints.phrase(ConnectFailureHints.SCAN_TIMEOUT),
        )
    }

    @Test
    fun `gatt 8 is monitor not responding`() {
        assertEquals("MONITOR NOT RESPONDING (GATT 8)", ConnectFailureHints.phrase("gatt 8"))
    }

    @Test
    fun `gatt 19 is monitor closed link`() {
        assertEquals("MONITOR CLOSED LINK (GATT 19)", ConnectFailureHints.phrase("gatt 19"))
    }

    @Test
    fun `gatt 133 is connect rejected`() {
        assertEquals("CONNECT REJECTED (GATT 133)", ConnectFailureHints.phrase("gatt 133"))
    }

    @Test
    fun `unmapped gatt status falls back to link error`() {
        assertEquals("LINK ERROR (GATT 34)", ConnectFailureHints.phrase("gatt 34"))
    }

    @Test
    fun `service discovery failures collapse to one phrase`() {
        assertEquals("SERVICE DISCOVERY FAILED", ConnectFailureHints.phrase("service discovery 8"))
        assertEquals("SERVICE DISCOVERY FAILED", ConnectFailureHints.phrase("discoverServices rejected"))
    }

    @Test
    fun `connectGatt null refusal maps to stack refused`() {
        assertEquals("BLUETOOTH STACK REFUSED", ConnectFailureHints.phrase("connectGatt returned null"))
    }

    @Test
    fun `token fetch keeps the underlying cause`() {
        assertEquals("TOKEN FETCH FAILED: HTTP 503", ConnectFailureHints.phrase("token fetch: HTTP 503"))
    }

    @Test
    fun `SecurityException maps to missing permission`() {
        assertEquals("BLUETOOTH PERMISSION MISSING", ConnectFailureHints.phrase("SecurityException"))
    }

    @Test
    fun `unknown reasons fall back with the raw text`() {
        assertEquals("CONNECT FAILED: something odd", ConnectFailureHints.phrase("something odd"))
    }

    @Test
    fun `blank reasons map to bare connect failed`() {
        assertEquals("CONNECT FAILED", ConnectFailureHints.phrase(""))
        assertEquals("CONNECT FAILED", ConnectFailureHints.phrase("   "))
    }
}
