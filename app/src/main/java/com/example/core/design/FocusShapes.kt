package com.example.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * FocusShield standardized shape tokens for Clean Minimalism design.
 */
object FocusShapes {
    val small = RoundedCornerShape(10.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(32.dp) // rounded-[2rem]
    val pill = RoundedCornerShape(50) // rounded-full
    val card = RoundedCornerShape(24.dp) // rounded-3xl
    val button = RoundedCornerShape(14.dp) // rounded-xl
}

