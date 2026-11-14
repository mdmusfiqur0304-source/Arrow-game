package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ArrowSwipeColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = EmeraldPulse,
    onSecondary = ObsidianBg,
    secondaryContainer = DeepEmeraldContainer,
    onSecondaryContainer = EmeraldPulse,
    tertiary = GemGold,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = CoralAlert,
    onError = TextPrimary,
    errorContainer = DeepCoralContainer,
    onErrorContainer = CoralAlert
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ArrowSwipeColorScheme,
        typography = Typography,
        content = content
    )
}
