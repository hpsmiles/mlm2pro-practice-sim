package com.hpsmiles.golfsim.core.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventParserTest {

    @Test
    fun shotDetected() {
        assertEquals(Mlm2proEvent.ShotDetected, EventParser.parse(Hex.parse("00")))
    }

    @Test
    fun processing() {
        assertEquals(Mlm2proEvent.Processing, EventParser.parse(Hex.parse("01")))
    }

    @Test
    fun ready() {
        assertEquals(Mlm2proEvent.Ready, EventParser.parse(Hex.parse("02")))
    }

    @Test
    fun batteryPercentPassesThrough() {
        assertEquals(Mlm2proEvent.Battery(100), EventParser.parse(Hex.parse("0364")))
        assertEquals(Mlm2proEvent.Battery(0), EventParser.parse(Hex.parse("0300")))
    }

    @Test
    fun misreadAlert() {
        assertEquals(Mlm2proEvent.MisreadAlert, EventParser.parse(Hex.parse("0500")))
    }

    @Test
    fun disarmed() {
        assertEquals(Mlm2proEvent.Disarmed, EventParser.parse(Hex.parse("0501")))
    }

    @Test
    fun unknownCodesArePreserved() {
        assertEquals(Mlm2proEvent.Unknown(0x04, null), EventParser.parse(Hex.parse("04")))
        assertEquals(Mlm2proEvent.Unknown(0x99, 0x12), EventParser.parse(Hex.parse("9912")))
        assertEquals(Mlm2proEvent.Unknown(0x05, 0x09), EventParser.parse(Hex.parse("0509")))
    }

    @Test
    fun emptyPayloadIsNull() {
        assertNull(EventParser.parse(ByteArray(0)))
    }
}
