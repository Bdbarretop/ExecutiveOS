package com.executiveos.core.database

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun instantSurvivesRoundTripAtMillisecondPrecision() {
        val instant = Instant.parse("2026-11-08T13:45:30.123Z")

        val stored = converters.instantToEpochMillis(instant)

        assertEquals(instant, converters.epochMillisToInstant(stored))
    }

    @Test
    fun localDateIsStoredAsIsoText() {
        val date = LocalDate.of(2026, 2, 28)

        val stored = converters.localDateToIsoString(date)

        assertEquals("2026-02-28", stored)
        assertEquals(date, converters.isoStringToLocalDate(stored))
    }

    @Test
    fun absentValuesStayAbsent() {
        assertNull(converters.instantToEpochMillis(null))
        assertNull(converters.epochMillisToInstant(null))
        assertNull(converters.localDateToIsoString(null))
        assertNull(converters.isoStringToLocalDate(null))
    }
}
