package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MaxPerformanceDarkColorScheme = darkColorScheme(
  primary = Primary,
  onPrimary = OnPrimary,
  primaryContainer = PrimaryContainer,
  background = BackgroundStart,
  onBackground = TextPrimary,
  surface = Surface,
  onSurface = OnSurface,
  surfaceVariant = SurfaceBright,
  onSurfaceVariant = OnSurfaceDim,
  outline = Outline,
  outlineVariant = OutlineVariant,
  error = Error
)

private val MaxPerformanceLightColorScheme = lightColorScheme(
  primary = Primary,
  onPrimary = Color.White,
  primaryContainer = Primary.copy(alpha = 0.9f),
  background = Color(0xFFF2F2F7),
  onBackground = Color(0xFF1C1C1E),
  surface = Color.White,
  onSurface = Color(0xFF1C1C1E),
  surfaceVariant = Color(0xFFE5E5EA),
  onSurfaceVariant = Color(0xFF3A3A3C),
  outline = Color(0xFFD1D1D6),
  outlineVariant = Color(0xFFC7C7CC),
  error = Error
)

val MaxShapes = Shapes(
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(20.dp),
  extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Premium default dark theme
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) MaxPerformanceDarkColorScheme else MaxPerformanceLightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = MaxShapes,
    content = content
  )
}
