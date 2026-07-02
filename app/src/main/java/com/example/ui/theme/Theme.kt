package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val CosmicDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9E86FF),     // Elegant Violet
    onPrimary = Color(0xFF12005C),
    secondary = Color(0xFF5AB2FF),   // Tech Blue
    onSecondary = Color(0xFF002B55),
    tertiary = Color(0xFFFF8E9E),    // Vibrant Coral accent
    background = Color(0xFF131314),  // Obsidian dark surface
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF1E1F20),     // Clean card slate
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF28292A),
    onSurfaceVariant = Color(0xFFC4C7C5)
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = CosmicDarkColorScheme,
    typography = Typography,
    content = content
  )
}
