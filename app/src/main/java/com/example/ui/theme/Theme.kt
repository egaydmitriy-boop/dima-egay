package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MossLightColorScheme = lightColorScheme(
    primary = MossInk,
    onPrimary = MossLime,
    primaryContainer = MossLime,
    onPrimaryContainer = MossInk,
    secondary = MossInk2,
    onSecondary = MossPaper,
    secondaryContainer = MossMist,
    onSecondaryContainer = MossInk,
    tertiary = MossLime2,
    onTertiary = MossInk,
    background = MossMist,
    onBackground = MossInk,
    surface = MossPaper,
    onSurface = MossInk,
    surfaceVariant = MossWhite,
    onSurfaceVariant = MossMuted,
    outline = MossLine,
    error = MossCoral,
    onError = MossWhite
)

private val MossShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MossNodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MossLightColorScheme,
        typography = Typography,
        shapes = MossShapes,
        content = content
    )
}
