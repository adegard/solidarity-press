package org.solidarity.press.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeText {

    private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val shortDayFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun relative(timestamp: Long): String {
        if (timestamp <= 0L) return "no date"
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / 60_000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1_440 -> "${minutes / 60}h ago"
            minutes < 10_080 -> "${minutes / 1_440}d ago"
            else -> full(timestamp)
        }
    }

    fun full(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        val day = LocalDate.now()
        return when (date.toLocalDate()) {
            day -> "Today ${clockFormat.format(date)}"
            day.minusDays(1) -> "Yesterday ${clockFormat.format(date)}"
            else -> dayFormat.format(date)
        }
    }

    fun dayHeader(timestamp: Long): String {
        if (timestamp <= 0L) return "Undated"
        val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> if (date.year == today.year) shortDayFormat.format(date) else dayFormat.format(date)
        }
    }
}
