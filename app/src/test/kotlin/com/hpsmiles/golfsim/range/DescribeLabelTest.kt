package com.hpsmiles.golfsim.range

import com.hpsmiles.golfsim.core.connect.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DescribeLabelTest {

    @Test
    fun `demo suppresses the failure reason`() {
        assertEquals(
            "DEMO MODE - FIRE TO SHOOT",
            describe(
                ConnectionState.Faulted("gatt 133"),
                demo = true,
                scanning = false,
                failureReason = "CONNECT REJECTED (GATT 133)",
            ),
        )
    }

    @Test
    fun `retry label combines with the reason`() {
        assertEquals(
            "RETRYING (2/3)… NO MONITOR FOUND",
            describe(
                ConnectionState.Disconnected,
                demo = false,
                scanning = false,
                retryLabel = "RETRYING (2/3)…",
                failureReason = "NO MONITOR FOUND",
            ),
        )
    }

    @Test
    fun `retry label without reason stays bare`() {
        assertEquals(
            "RETRYING (2/3)…",
            describe(
                ConnectionState.Disconnected,
                demo = false,
                scanning = false,
                retryLabel = "RETRYING (2/3)…",
            ),
        )
    }

    @Test
    fun `scanning hides the reason`() {
        assertEquals(
            "SCANNING…",
            describe(
                ConnectionState.Disconnected,
                demo = false,
                scanning = true,
                failureReason = "NO MONITOR FOUND",
            ),
        )
    }

    @Test
    fun `faulted shows the reason and never SEE CAPTURE`() {
        assertEquals(
            "BLE FAULTED - CONNECT REJECTED (GATT 133)",
            describe(
                ConnectionState.Faulted("gatt 133"),
                demo = false,
                scanning = false,
                failureReason = "CONNECT REJECTED (GATT 133)",
            ),
        )
        val bare = describe(ConnectionState.Faulted("gatt 133"), demo = false, scanning = false)
        assertEquals("BLE FAULTED", bare)
        assertFalse(bare.contains("SEE CAPTURE"))
    }

    @Test
    fun `disconnected after giveup keeps the reason`() {
        assertEquals(
            "BLE DISCONNECTED - NO MONITOR FOUND",
            describe(
                ConnectionState.Disconnected,
                demo = false,
                scanning = false,
                failureReason = "NO MONITOR FOUND",
            ),
        )
        assertEquals(
            "BLE DISCONNECTED",
            describe(ConnectionState.Disconnected, demo = false, scanning = false),
        )
    }

    @Test
    fun `armed and standby labels are untouched`() {
        assertEquals(
            "ARMED",
            describe(ConnectionState.Armed, demo = false, scanning = false),
        )
        assertEquals(
            "DISARMED - STANDBY",
            describe(ConnectionState.Disarmed, demo = false, scanning = false),
        )
    }
}
