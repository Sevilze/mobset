package com.mobset.ui.util

/**
 * Formats elapsed time in milliseconds to MM:SS.ss with two decimal seconds.
 */
fun formatElapsedTimeMs(elapsedTimeMs: Long): String {
    val totalSeconds = elapsedTimeMs / 1000.0
    val minutes = (totalSeconds / 60).toInt()
    val secondsWithMs = totalSeconds % 60
    return String.format("%02d:%05.2f", minutes, secondsWithMs)
}

