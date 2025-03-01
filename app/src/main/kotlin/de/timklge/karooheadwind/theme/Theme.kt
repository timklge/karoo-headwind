package de.timklge.karooheadwind.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    val scheme = lightColorScheme(
        primary = Color(0xFF214559),
        secondary = Color(0xFF636363),
        tertiary = Color(0xFFFEF69A),
    )

    MaterialTheme(
        content = content,
        colorScheme = scheme
    )
}