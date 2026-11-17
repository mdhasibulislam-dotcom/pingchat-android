package com.mdhasibulislam.pingchat.data

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Time labels, matching the web app (Messenger-style).
 * - Chat list: today -> "10:24 PM", yesterday -> "Yesterday",
 *   this week -> "Mon", older -> "Nov 16".
 * - Day separator inside a chat: "Today at 10:24 PM" / "Yesterday at 10:24 PM"
 *   / "Nov 16 at 10:24 PM". A plain "10:24 PM" label is shown after a 20+
 *   minute gap between messages on the same day.
 */
object TimeFormat {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val dayShortFmt = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    private val weekdayFmt = DateTimeFormatter.ofPattern("EEE", Locale.US)

    /**
     * The backend returns timestamps with a numeric offset, e.g.
     * "2026-11-17T02:53:10.123456+00:00" — Instant.parse() only accepts a
     * trailing "Z" and returns null for those, which blanked every date/time
     * label. Try OffsetDateTime first (handles +00:00 and Z), then Instant.
     */
    fun parse(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso).toInstant()
        } catch (e: Exception) {
            try {
                Instant.parse(iso)
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun fmtTime(iso: String?): String {
        val t = parse(iso) ?: return ""
        return timeFmt.format(t.atZone(zone))
    }

    fun fmtListTime(iso: String?): String {
        val t = parse(iso) ?: return ""
        val date = t.atZone(zone).toLocalDate()
        val today = Instant.now().atZone(zone).toLocalDate()
        return when {
            date == today -> fmtTime(iso)
            date == today.minusDays(1) -> "Yesterday"
            Duration.between(date.atStartOfDay(), today.atStartOfDay()).toDays() < 7 ->
                weekdayFmt.format(date)
            else -> dayShortFmt.format(date)
        }
    }

    /** "Today at 10:24 PM" / "Yesterday at 10:24 PM" / "Nov 16 at 10:24 PM". */
    fun dayLabel(iso: String?): String {
        val t = parse(iso) ?: return ""
        val date = t.atZone(zone).toLocalDate()
        val today = Instant.now().atZone(zone).toLocalDate()
        val day = when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            else -> dayShortFmt.format(date)
        }
        return "$day at ${fmtTime(iso)}"
    }

    fun isSameDay(a: String?, b: String?): Boolean {
        val ta = parse(a) ?: return false
        val tb = parse(b) ?: return false
        return ta.atZone(zone).toLocalDate() == tb.atZone(zone).toLocalDate()
    }

    /** Minutes between two ISO timestamps (null-safe). */
    fun minutesBetween(earlier: String?, later: String?): Long {
        val a = parse(earlier) ?: return 0
        val b = parse(later) ?: return 0
        return Duration.between(a, b).toMinutes()
    }
}
