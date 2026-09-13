package me.bmax.apatch.ui.repo

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ReleaseDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/**
 * Repository timestamps are seconds since the epoch, and an index that never published one leaves
 * zero behind. Such a date is left out instead of being rendered as 1970.
 */
internal fun formatReleaseDate(timestamp: Double): String {
    if (timestamp <= 0.0) return ""
    return ReleaseDateFormatter.withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochSecond(timestamp.toLong()))
}
