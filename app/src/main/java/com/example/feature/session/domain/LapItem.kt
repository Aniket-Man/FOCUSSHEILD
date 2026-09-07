package com.example.feature.session.domain

/**
 * Domain model representing a single recorded lap in a Stopwatch session.
 */
data class LapItem(
    val lapNumber: Int,
    val lapTimeMillis: Long,
    val totalTimeMillis: Long,
    val formattedLapTime: String,
    val formattedTotalTime: String
)
