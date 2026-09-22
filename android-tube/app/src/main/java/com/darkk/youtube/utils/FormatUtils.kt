package com.darkk.youtube.utils

import java.util.Locale

object FormatUtils {
    fun formatCount(count: Long): String {
        return when {
            count >= 10_000_000 -> String.format(Locale.US, "%.1fCr", count / 10_000_000.0).replace(".0Cr", "Cr")
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0).replace(".0K", "K")
            else -> count.toString()
        }
    }

    private fun parseViewsString(text: String): Long? {
        var cleanText = text.replace("views", "", ignoreCase = true)
            .replace("watching", "", ignoreCase = true)
            .replace(",", "")
            .trim()
        
        var multiplier = 1.0
        when {
            cleanText.contains("K", ignoreCase = true) -> {
                multiplier = 1_000.0
                cleanText = cleanText.replace("K", "", ignoreCase = true)
            }
            cleanText.contains("M", ignoreCase = true) -> {
                multiplier = 1_000_000.0
                cleanText = cleanText.replace("M", "", ignoreCase = true)
            }
            cleanText.contains("B", ignoreCase = true) -> {
                multiplier = 1_000_000_000.0
                cleanText = cleanText.replace("B", "", ignoreCase = true)
            }
            cleanText.contains("Lakh", ignoreCase = true) || cleanText.endsWith("l", ignoreCase = true) -> {
                multiplier = 100_000.0
                cleanText = cleanText.replace("Lakh", "", ignoreCase = true).replace("l", "", ignoreCase = true)
            }
            cleanText.contains("Crore", ignoreCase = true) || cleanText.contains("Cr", ignoreCase = true) -> {
                multiplier = 10_000_000.0
                cleanText = cleanText.replace("Crore", "", ignoreCase = true).replace("Cr", "", ignoreCase = true)
            }
        }
        
        val numberPart = cleanText.trim().toDoubleOrNull() ?: return null
        return (numberPart * multiplier).toLong()
    }

    fun formatStringCount(text: String): String {
        if (text.isEmpty()) return text
        val parsedCount = parseViewsString(text)
        if (parsedCount != null) {
            val formatted = formatCount(parsedCount)
            return if (text.contains("views", ignoreCase = true)) {
                "$formatted views"
            } else if (text.contains("watching", ignoreCase = true)) {
                "$formatted watching"
            } else {
                formatted
            }
        }
        return text
    }

    fun parseRelativeTime(dateString: String): String {
        if (dateString.isEmpty()) return ""
        if (dateString.contains("ago", ignoreCase = true)) return dateString

        try {
            // Check if it's an ISO date like "2026-06-11T08:39:31Z"
            if (dateString.contains("T") && dateString.length >= 10) {
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = format.parse(dateString.substring(0, 19))
                if (date != null) {
                    return android.text.format.DateUtils.getRelativeTimeSpanString(
                        date.time,
                        System.currentTimeMillis(),
                        android.text.format.DateUtils.MINUTE_IN_MILLIS
                    ).toString()
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors and return original string
        }
        
        // If the date string doesn't contain "ago", check if it's a relative time string
        val lowerStr = dateString.lowercase(Locale.ROOT)
        val timeUnits = listOf("second", "minute", "hour", "day", "week", "month", "year")
        if (timeUnits.any { lowerStr.contains(it) } && !lowerStr.contains("in ")) {
            return "$dateString ago"
        }
        
        return dateString
    }
}
