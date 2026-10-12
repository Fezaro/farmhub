package com.farm_tech.farmhub.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

class WeatherDateTimeFormatterTest {
    @Test
    fun `formats an Open-Meteo local timestamp as an hourly time`() {
        withDeviceTimeZone("Africa/Nairobi") {
            assertEquals("09:00", WeatherDateTimeFormatter.hourlyTime("2026-10-12T09:00", "Africa/Nairobi"))
        }
    }

    @Test
    fun `converts an API local timestamp into the device timezone`() {
        withDeviceTimeZone("Africa/Nairobi") {
            assertEquals("10:00", WeatherDateTimeFormatter.hourlyTime("2026-10-12T09:00", "Africa/Johannesburg"))
        }
    }

    @Test
    fun `formats sunrise and sunset in twelve hour notation`() {
        withDeviceTimeZone("Africa/Nairobi") {
            assertEquals("06:15 AM", WeatherDateTimeFormatter.sunriseOrSunsetTime("2026-10-12T06:15", "Africa/Nairobi"))
            assertEquals("06:35 PM", WeatherDateTimeFormatter.sunriseOrSunsetTime("2026-10-12T18:35", "Africa/Nairobi"))
        }
    }

    @Test
    fun `returns null for missing or malformed timestamps`() {
        assertNull(WeatherDateTimeFormatter.hourlyTime(null, "Africa/Nairobi"))
        assertNull(WeatherDateTimeFormatter.hourlyTime("not-a-timestamp", "Africa/Nairobi"))
    }

    @Test
    fun `anchors the forecast at the current local hour`() {
        withDeviceTimeZone("Africa/Nairobi") {
            val currentHour = WeatherDateTimeFormatter.currentForecastHour("2026-10-12T13:47", "Africa/Nairobi")
            assertEquals(ZonedDateTime.parse("2026-10-12T13:00+03:00[Africa/Nairobi]"), currentHour)
        }
    }

    private fun withDeviceTimeZone(zoneId: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zoneId)))
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
