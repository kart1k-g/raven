package com.kart1kg.raven.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RavenDarkColorScheme = darkColorScheme(
    primary = RavenPrimary,
    onPrimary = RavenOnPrimary,
    primaryContainer = RavenPrimaryContainer,
    onPrimaryContainer = RavenPrimary,
    secondary = RavenSecondary,
    secondaryContainer = RavenSecondaryContainer,
    onSecondary = RavenBlack,
    tertiary = RavenTertiary,
    background = RavenBlack,
    onBackground = RavenOnSurface,
    surface = RavenSurface,
    onSurface = RavenOnSurface,
    surfaceVariant = RavenSurfaceVariant,
    onSurfaceVariant = RavenOnSurfaceVariant,
    outline = RavenOutline,
    outlineVariant = RavenOutlineVariant,
    error = RavenError,
    onError = RavenBlack
)

@Composable
fun RavenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RavenDarkColorScheme,
        typography = Typography,
        content = content
    )
}