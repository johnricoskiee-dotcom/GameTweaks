package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PulseDarkColorScheme =
  darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = VoltAmber,
    onSecondary = OnVoltAmber,
    secondaryContainer = VoltAmberContainer,
    onSecondaryContainer = VoltAmber,
    tertiary = ShizukuIndigo,
    onTertiary = Color(0xFF0A0D2B),
    tertiaryContainer = ShizukuIndigoContainer,
    onTertiaryContainer = ShizukuIndigo,
    error = ThermalCoral,
    onError = Color(0xFF2B000B),
    errorContainer = ThermalCoralContainer,
    onErrorContainer = ThermalCoral,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = CarbonSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = CarbonSurfaceVariant,
    surfaceContainerHigh = CarbonElevated,
    outline = CarbonOutline,
    outlineVariant = Color(0xFF1A2538),
  )

private val PulseLightColorScheme =
  lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = CyanDeepContainer,
    onPrimaryContainer = Color(0xFF002026),
    secondary = AmberDeep,
    onSecondary = Color.White,
    secondaryContainer = AmberDeepContainer,
    onSecondaryContainer = Color(0xFF2B1900),
    tertiary = Color(0xFF4338CA),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E7FF),
    onTertiaryContainer = Color(0xFF1E1B4B),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBg,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightOutline,
  )

private val PulseShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun PulseTuneTheme(
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) PulseDarkColorScheme else PulseLightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = PulseShapes,
    content = content,
  )
}
