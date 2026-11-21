package com.example.paperly.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalPaperlyTheme = staticCompositionLocalOf { ThemePresets.defaultTheme }

@Composable
fun PaperlyThemeRoot(
  themeSpec: PaperlyThemeSpec,
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  fontScaleOverride: Float = 1.0f,
  reducedMotionOverride: Boolean = false,
  content: @Composable () -> Unit
) {
  val systemDark = isSystemInDarkTheme()
  val resolved = ThemePresets.resolveVariantForMode(themeSpec, themeMode, systemDark).let {
    if (reducedMotionOverride) it.copy(reducedMotion = true, animationIntensity = AnimationIntensity.NONE, transitionDurationMs = 0)
    else it
  }

  val colorScheme = buildColorScheme(resolved)
  val typography = buildTypography(resolved, fontScaleOverride)
  val shapes = Shapes(
    extraSmall = RoundedCornerShape((resolved.cornerRadiusDp * 0.4f).dp),
    small = RoundedCornerShape((resolved.buttonRadiusDp).coerceAtMost(24f).dp),
    medium = RoundedCornerShape(resolved.cornerRadiusDp.dp),
    large = RoundedCornerShape((resolved.cornerRadiusDp * 1.15f).coerceAtMost(32f).dp),
    extraLarge = RoundedCornerShape((resolved.cornerRadiusDp * 1.35f).coerceAtMost(36f).dp)
  )

  CompositionLocalProvider(LocalPaperlyTheme provides resolved) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = typography,
      shapes = shapes,
      content = content
    )
  }
}

private fun contrastingOn(bg: Color): Color =
  if (bg.luminance() > 0.42f) Color(0xFF0F172A) else Color(0xFFFFFFFF)

private fun buildColorScheme(spec: PaperlyThemeSpec): ColorScheme {
  val accent = spec.accentColor
  val secondary = spec.secondaryAccentColor
  val bg = spec.primaryBgColor
  val surface = spec.surfaceColor
  val surfaceVar = spec.surfaceVariantColor
  val text1 = spec.textPrimaryColor
  val text2 = spec.textSecondaryColor
  val border = spec.borderColor
  val selected = spec.selectedContainerColor
  val err = spec.errorColor

  return if (spec.isDark) {
    darkColorScheme(
      primary = accent,
      onPrimary = contrastingOn(accent),
      primaryContainer = selected,
      onPrimaryContainer = text1,
      secondary = secondary,
      onSecondary = contrastingOn(secondary),
      secondaryContainer = surfaceVar,
      onSecondaryContainer = text1,
      tertiary = spec.warningColor,
      onTertiary = contrastingOn(spec.warningColor),
      background = bg,
      onBackground = text1,
      surface = surface,
      onSurface = text1,
      surfaceVariant = surfaceVar,
      onSurfaceVariant = text2,
      outline = border,
      outlineVariant = border.copy(alpha = 0.55f),
      error = err,
      onError = contrastingOn(err)
    )
  } else {
    lightColorScheme(
      primary = accent,
      onPrimary = contrastingOn(accent),
      primaryContainer = selected,
      onPrimaryContainer = text1,
      secondary = secondary,
      onSecondary = contrastingOn(secondary),
      secondaryContainer = surfaceVar,
      onSecondaryContainer = text1,
      tertiary = spec.warningColor,
      onTertiary = contrastingOn(spec.warningColor),
      background = bg,
      onBackground = text1,
      surface = surface,
      onSurface = text1,
      surfaceVariant = surfaceVar,
      onSurfaceVariant = text2,
      outline = border,
      outlineVariant = border.copy(alpha = 0.55f),
      error = err,
      onError = contrastingOn(err)
    )
  }
}

private fun buildTypography(spec: PaperlyThemeSpec, extraScale: Float): Typography {
  val headingFamily = spec.headingFont.toComposeFontFamily()
  val bodyFamily = spec.bodyFont.toComposeFontFamily()
  val scale = (spec.fontSizeScale * extraScale).coerceIn(0.82f, 1.35f)
  val lh = spec.lineHeightMultiplier
  val hWeight = when (spec.headingWeight) {
    in 0..450 -> FontWeight.Normal
    in 451..550 -> FontWeight.Medium
    in 551..650 -> FontWeight.SemiBold
    in 651..750 -> FontWeight.Bold
    else -> FontWeight.ExtraBold
  }

  return Typography(
    displayLarge = TextStyle(fontFamily = headingFamily, fontWeight = hWeight, fontSize = (34f * scale).sp, lineHeight = (40f * scale).sp),
    headlineLarge = TextStyle(fontFamily = headingFamily, fontWeight = hWeight, fontSize = (28f * scale).sp, lineHeight = (34f * scale).sp),
    headlineMedium = TextStyle(fontFamily = headingFamily, fontWeight = hWeight, fontSize = (23f * scale).sp, lineHeight = (29f * scale).sp),
    headlineSmall = TextStyle(fontFamily = headingFamily, fontWeight = hWeight, fontSize = (20f * scale).sp, lineHeight = (26f * scale).sp),
    titleLarge = TextStyle(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = (18f * scale).sp, lineHeight = (24f * scale).sp),
    titleMedium = TextStyle(fontFamily = headingFamily, fontWeight = FontWeight.SemiBold, fontSize = (16f * scale).sp, lineHeight = (22f * scale).sp),
    titleSmall = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.SemiBold, fontSize = (14f * scale).sp, lineHeight = (20f * scale).sp),
    bodyLarge = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = (15.5f * scale).sp, lineHeight = (15.5f * scale * lh).sp),
    bodyMedium = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = (13.8f * scale).sp, lineHeight = (13.8f * scale * lh).sp),
    bodySmall = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = (12f * scale).sp, lineHeight = (16.5f * scale).sp),
    labelLarge = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.SemiBold, fontSize = (14f * scale).sp, lineHeight = (18f * scale).sp),
    labelMedium = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = (12f * scale).sp, lineHeight = (16f * scale).sp),
    labelSmall = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = (11f * scale).sp, lineHeight = (14f * scale).sp)
  )
}
