package com.executiveos.di

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseModuleTest {
    @Test
    fun clockUsesTheDeviceTimeZoneSoTodayIsTheLocalDay() {
        assertEquals(ZoneId.systemDefault(), DatabaseModule.provideClock().zone)
    }
}
