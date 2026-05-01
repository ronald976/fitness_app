package com.fitness.app.ui.util

/** Coarse duration for session totals: "1h 12m", "48m", "<1m". */
fun formatSessionDuration(ms: Long): String {
    if (ms <= 0L) return "<1m"
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        totalMinutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

/** Fine-grained gap for between-set rest: "1m 18s", "45s", "<1s". */
fun formatRestGap(ms: Long): String {
    if (ms <= 0L) return "<1s"
    val totalSec = ms / 1000L
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return when {
        minutes > 0 -> "${minutes}m ${seconds}s"
        totalSec > 0 -> "${seconds}s"
        else -> "<1s"
    }
}
