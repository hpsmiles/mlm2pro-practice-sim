package com.hpsmiles.golfsim.bag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BagMappingFormatsTest {

    @Test
    fun `carry rounds to whole metres`() {
        assertEquals("140 m", BagMappingFormats.carry(140.4))
        assertEquals("141 m", BagMappingFormats.carry(140.6))
    }

    @Test
    fun `smash factor two decimals - dash when club speed missing`() {
        assertEquals("1.45", BagMappingFormats.smash(33.0, 47.9))
        assertEquals("—", BagMappingFormats.smash(0.0, 47.9))
    }

    @Test
    fun `date time format`() {
        // SimpleDateFormat uses the JVM default timezone, so pin the shape
        // (same convention as GameHistoryFormatsTest) rather than a fixed string.
        assertTrue(BagMappingFormats.dateTime(1780770600000).matches(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""")))
    }
}
