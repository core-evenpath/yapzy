package com.example.yapzy.utils

/**
 * Utility functions for time and duration formatting
 */

/**
 * Format duration in seconds to a human-readable string (MM:SS or HH:MM:SS)
 *
 * Examples:
 * - 45 seconds -> "00:45"
 * - 90 seconds -> "01:30"
 * - 3661 seconds -> "01:01:01"
 */
fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

/**
 * Format duration in milliseconds to a human-readable string
 */
fun formatDuration(millis: Long): String {
    return formatDuration((millis / 1000).toInt())
}