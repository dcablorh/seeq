package com.example.locallens.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// System fonts stand in for the reference's Swizzy (display) and PP Neue Montreal Mono (labels).
val DisplayFont = FontFamily.SansSerif
val MonoFont = FontFamily.Monospace

val Display = TextStyle(
  fontFamily = DisplayFont,
  fontWeight = FontWeight.Medium,
  fontSize = 72.sp,
  lineHeight = 62.sp,
  letterSpacing = (-1.5).sp,
)

/** Small uppercase tracked label; callers uppercase the text. */
val Label = TextStyle(
  fontFamily = MonoFont,
  fontWeight = FontWeight.Normal,
  fontSize = 12.sp,
  lineHeight = 14.sp,
  letterSpacing = 0.72.sp,
)

val LabelLarge = Label.copy(fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = 0.9.sp)

val Typography =
  Typography(
    headlineSmall = TextStyle(
      fontFamily = DisplayFont,
      fontWeight = FontWeight.Medium,
      fontSize = 27.sp,
      lineHeight = 30.sp,
      letterSpacing = (-0.5).sp,
    ),
    bodyLarge = TextStyle(
      fontFamily = FontFamily.SansSerif,
      fontWeight = FontWeight.Normal,
      fontSize = 16.sp,
      lineHeight = 22.sp,
    ),
    bodySmall = Label,
  )
