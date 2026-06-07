package com.example.gemgemgen

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatCountParserTest {
    @Test
    fun parse_returnsDefaultForBlankOrInvalidText() {
        assertEquals(10, RepeatCountParser.parse(""))
        assertEquals(10, RepeatCountParser.parse("abc"))
    }

    @Test
    fun parse_clampsValueToSupportedRange() {
        assertEquals(1, RepeatCountParser.parse("0"))
        assertEquals(999, RepeatCountParser.parse("1000"))
    }

    @Test
    fun parse_returnsValidNumber() {
        assertEquals(25, RepeatCountParser.parse("25"))
    }

    @Test
    fun normalizeInput_keepsOnlyDigits() {
        assertEquals("1234", RepeatCountParser.normalizeInput("a1b2c3d4"))
    }
}
