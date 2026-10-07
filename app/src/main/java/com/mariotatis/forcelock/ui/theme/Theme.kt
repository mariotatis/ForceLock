package com.mariotatis.forcelock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ForceLockColors = darkColorScheme(
    primary = Periwinkle,
    onPrimary = OnPeriwinkle,
    primaryContainer = PeriwinkleDeep,
    onPrimaryContainer = Mist,
    secondary = Mint,
    secondaryContainer = MintContainer,
    onSecondaryContainer = Mint,
    tertiary = Amber,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Amber,
    background = Night,
    onBackground = Mist,
    surface = Night,
    onSurface = Mist,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = MistMuted,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    surfaceContainerHighest = NightSurfaceHighest,
    outline = NightOutline,
    outlineVariant = NightOutline,
)

/** ForceLock is always dark: it lives on a handheld and is mostly seen right before sleep. */
@Composable
fun ForceLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ForceLockColors, typography = Typography, content = content)
}
