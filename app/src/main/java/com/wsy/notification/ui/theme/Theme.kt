package com.wsy.notification.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF3E2723),
    secondary = Color(0xFFFF8A65),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFEF5350),
)

@Composable
fun NotificationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
