package com.workoutapp.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WorkoutRed = Color(0xFFC43D3D)
private val WorkoutRedDark = Color(0xFF8F2E2E)
private val WorkoutBackground = Color(0xFFE7E9EC)
private val WorkoutSurface = Color(0xFFFBFBFC)
private val WorkoutSurfaceVariant = Color(0xFFF5F6F7)
private val WorkoutText = Color(0xFF202124)
private val WorkoutMuted = Color(0xFF6F737B)

private val WorkoutColors = lightColorScheme(
    primary = WorkoutRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8E3E3),
    onPrimaryContainer = WorkoutRedDark,
    secondary = WorkoutMuted,
    onSecondary = Color.White,
    background = WorkoutBackground,
    onBackground = WorkoutText,
    surface = WorkoutSurface,
    onSurface = WorkoutText,
    surfaceVariant = WorkoutSurfaceVariant,
    onSurfaceVariant = WorkoutMuted,
    outline = Color(0xFFD1D3D7),
    error = Color(0xFFB3261E),
)

@Composable
fun WorkoutAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WorkoutColors, content = content)
}
