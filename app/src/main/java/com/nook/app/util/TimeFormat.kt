package com.nook.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFormat {
    private fun cal(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = cal(a); val cb = cal(b)
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    /** Chat-list timestamp: "14:05", "Yesterday", "Mon", "12 Mar". */
    fun listTime(ms: Long, now: Long = System.currentTimeMillis()): String {
        if (ms <= 0) return ""
        val days = daysBetween(ms, now)
        return when {
            days == 0L -> clock(ms)
            days == 1L -> "Yesterday"
            days < 7 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(ms))
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))
        }
    }

    fun clock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

    /** Date separator inside a chat: "Today", "Yesterday", "Monday, 12 March". */
    fun separator(ms: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysBetween(ms, now)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days < 7 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(ms))
            else -> SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(ms))
        }
    }

    fun lastSeen(ms: Long, now: Long = System.currentTimeMillis()): String {
        if (ms <= 0) return "Offline"
        val diff = now - ms
        return when {
            diff < TimeUnit.MINUTES.toMillis(1) -> "Last seen just now"
            diff < TimeUnit.HOURS.toMillis(1) -> "Last seen ${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            isSameDay(ms, now) -> "Last seen today at ${clock(ms)}"
            daysBetween(ms, now) == 1L -> "Last seen yesterday at ${clock(ms)}"
            else -> "Last seen ${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(ms))}"
        }
    }

    fun duration(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    fun daysBetween(from: Long, to: Long): Long {
        val a = cal(from).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val b = cal(to).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return TimeUnit.MILLISECONDS.toDays(b.timeInMillis - a.timeInMillis)
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
}
