package com.example.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

/** Timers may wrap at a unit separator instead of clipping or defeating the user's font scale. */
@Composable
fun FocusTimerText(
    time: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    Text(
        text = time.replace(":", ":\u200B"),
        style = style.copy(fontFeatureSettings = "tnum"),
        textAlign = TextAlign.Center,
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(time) }
    )
}
