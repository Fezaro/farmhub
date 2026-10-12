package com.farm_tech.farmhub.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Converts Open-Meteo timestamps to the time zone configured on the device. */
internal object WeatherDateTimeFormatter {
    private val hourlyFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val sunriseSunsetFormat = DateTimeFormatter.ofPattern("hh:mm a", Locale.US)

    fun hourlyTime(timestamp: String?, sourceTimeZone: String?): String? =
        parse(timestamp, sourceTimeZone)?.format(hourlyFormat)

    fun sunriseOrSunsetTime(timestamp: String?, sourceTimeZone: String?): String? =
        parse(timestamp, sourceTimeZone)?.format(sunriseSunsetFormat)

    fun forecastDateTime(timestamp: String?, sourceTimeZone: String?): ZonedDateTime? =
        parse(timestamp, sourceTimeZone)

    fun currentForecastHour(timestamp: String?, sourceTimeZone: String?): ZonedDateTime =
        (forecastDateTime(timestamp, sourceTimeZone) ?: ZonedDateTime.now()).truncatedTo(ChronoUnit.HOURS)

    fun dayLabel(timestamp: ZonedDateTime): String =
        timestamp.format(DateTimeFormatter.ofPattern("EEE", Locale.US))

    private fun parse(timestamp: String?, sourceTimeZone: String?): ZonedDateTime? {
        val value = timestamp?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val deviceZone = ZoneId.systemDefault()

        // Offset-bearing timestamps are already absolute instants. Open-Meteo's
        // timezone=auto response normally has local timestamps without an offset.
        runCatching { OffsetDateTime.parse(value).atZoneSameInstant(deviceZone) }
            .getOrNull()
            ?.let { return it }
        runCatching { Instant.parse(value).atZone(deviceZone) }
            .getOrNull()
            ?.let { return it }

        val sourceZone = sourceTimeZone
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: deviceZone
        return try {
            LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(sourceZone)
                .withZoneSameInstant(deviceZone)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
