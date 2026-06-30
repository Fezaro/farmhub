package com.farm_tech.farmhub.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object FriendlyDateTimeFormatter {
    private val isoLocalFallback = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    private val dateOnlyFallback = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())

    private fun parseUtcOrOffset(value: String): ZonedDateTime? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null

        return try {
            Instant.parse(trimmed).atZone(ZoneId.systemDefault())
        } catch (_: DateTimeParseException) {
            try {
                ZonedDateTime.parse(trimmed).withZoneSameInstant(ZoneId.systemDefault())
            } catch (_: DateTimeParseException) {
                try {
                    val local = java.time.LocalDateTime.parse(trimmed, isoLocalFallback)
                    local.atZone(ZoneId.systemDefault())
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
    }

    fun toRelativeOrDateTime(rawTimestamp: String?): String {
        if (rawTimestamp.isNullOrBlank()) return ""
        val parsed = parseUtcOrOffset(rawTimestamp) ?: return rawTimestamp
        val now = ZonedDateTime.now(ZoneId.systemDefault())

        val secondsAgo = Duration.between(parsed, now).seconds
        if (secondsAgo < 0) return toDateTime(rawTimestamp)
        if (secondsAgo < 60) return "Just now"
        if (secondsAgo < 60 * 60) return "${secondsAgo / 60} minutes ago"
        if (secondsAgo < 60 * 60 * 24) return "${secondsAgo / 3600} hours ago"

        val today = now.toLocalDate()
        val date = parsed.toLocalDate()
        if (date == today.minusDays(1)) return "Yesterday"
        if (date.isAfter(today.minusDays(7))) {
            return "Last ${parsed.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())}"
        }
        return parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }

    fun toDateTime(rawTimestamp: String?): String {
        if (rawTimestamp.isNullOrBlank()) return ""
        val parsed = parseUtcOrOffset(rawTimestamp) ?: return rawTimestamp
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val today = now.toLocalDate()
        val date = parsed.toLocalDate()
        val time = parsed.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        return when {
            date == today -> "Today • $time"
            date == today.minusDays(1) -> "Yesterday • $time"
            else -> "${parsed.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))} • $time"
        }
    }

    fun toLocalClock(rawTimestamp: String?): String {
        if (rawTimestamp.isNullOrBlank()) return ""
        val parsed = parseUtcOrOffset(rawTimestamp) ?: return rawTimestamp
        return parsed.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    }

    fun toShortDate(rawDate: String?): String {
        if (rawDate.isNullOrBlank()) return ""
        val trimmed = rawDate.trim()
        return try {
            val date = LocalDate.parse(trimmed, dateOnlyFallback)
            val today = LocalDate.now(ZoneId.systemDefault())
            when {
                date == today -> "Today"
                date == today.minusDays(1) -> "Yesterday"
                ChronoUnit.DAYS.between(date, today) in 2..6 ->
                    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            }
        } catch (_: DateTimeParseException) {
            trimmed
        }
    }
}
