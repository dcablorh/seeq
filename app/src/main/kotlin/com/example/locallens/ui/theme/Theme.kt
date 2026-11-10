package com.example.locallens.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// The look is a single light "paper" theme; there is no dark variant.
private val PaperColorScheme = lightColorScheme(
  primary = Ink,
  onPrimary = Paper,
  secondary = Ink,
  onSecondary = Paper,
  background = Canvas,
  onBackground = Ink,
  surface = Canvas,
  onSurface = Ink,
  surfaceVariant = Mist,
  onSurfaceVariant = Ink,
  outline = Hairline,
  outlineVariant = Hairline,
  error = ErrorRed,
)

@Composable
fun LocalLensTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = PaperColorScheme, typography = Typography, content = content)
}
