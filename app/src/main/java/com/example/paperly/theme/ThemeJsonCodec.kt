package com.example.paperly.theme

import org.json.JSONObject

object ThemeJsonCodec {

  fun encode(spec: PaperlyThemeSpec): String {
    val obj = JSONObject()
    obj.put("id", spec.id)
    obj.put("name", spec.name)
    obj.put("collection", spec.collection.name)
    obj.put("variantTag", spec.variantTag)
    obj.put("description", spec.description)
    obj.put("isDark", spec.isDark)
    obj.put("isCustom", spec.isCustom)

    obj.put("primaryBg", hex(spec.primaryBg))
    obj.put("secondaryBg", hex(spec.secondaryBg))
    obj.put("surface", hex(spec.surface))
    obj.put("surfaceVariant", hex(spec.surfaceVariant))
    obj.put("accent", hex(spec.accent))
    obj.put("secondaryAccent", hex(spec.secondaryAccent))
    obj.put("textPrimary", hex(spec.textPrimary))
    obj.put("textSecondary", hex(spec.textSecondary))
    obj.put("border", hex(spec.border))
    obj.put("selectedContainer", hex(spec.selectedContainer))
    obj.put("warning", hex(spec.warning))
    obj.put("error", hex(spec.error))

    obj.put("surfaceMaterial", spec.surfaceMaterial.name)
    obj.put("transparencyAlpha", spec.transparencyAlpha.toDouble())
    obj.put("blurRadiusDp", spec.blurRadiusDp.toDouble())
    obj.put("gradientStart", hex(spec.gradientStart))
    obj.put("gradientEnd", hex(spec.gradientEnd))
    obj.put("textureOverlay", spec.textureOverlay.name)

    obj.put("headingFont", spec.headingFont.name)
    obj.put("bodyFont", spec.bodyFont.name)
    obj.put("monoFont", spec.monoFont.name)
    obj.put("fontSizeScale", spec.fontSizeScale.toDouble())
    obj.put("lineHeightMultiplier", spec.lineHeightMultiplier.toDouble())
    obj.put("headingWeight", spec.headingWeight)

    obj.put("cornerRadiusDp", spec.cornerRadiusDp.toDouble())
    obj.put("buttonRadiusDp", spec.buttonRadiusDp.toDouble())
    obj.put("borderWidthDp", spec.borderWidthDp.toDouble())
    obj.put("hardShadowOffsetDp", spec.hardShadowOffsetDp.toDouble())
    obj.put("cardElevationDp", spec.cardElevationDp.toDouble())
    obj.put("density", spec.density.name)
    obj.put("navStyle", spec.navStyle.name)
    obj.put("headerStyle", spec.headerStyle.name)

    obj.put("animationIntensity", spec.animationIntensity.name)
    obj.put("transitionDurationMs", spec.transitionDurationMs)
    obj.put("reducedMotion", spec.reducedMotion)
    return obj.toString(2)
  }

  fun decode(json: String, fallbackId: String? = null): PaperlyThemeSpec {
    val obj = JSONObject(json)
    val defaults = ThemePresets.defaultTheme
    val id = fallbackId ?: obj.optString("id", "custom_${System.currentTimeMillis()}")
    return PaperlyThemeSpec(
      id = id,
      name = obj.optString("name", "Imported Theme"),
      collection = enumOr(obj.optString("collection"), defaults.collection),
      variantTag = obj.optString("variantTag", "Custom"),
      description = obj.optString("description", "User custom theme configuration"),
      isDark = obj.optBoolean("isDark", defaults.isDark),
      isCustom = obj.optBoolean("isCustom", true),
      primaryBg = parseHex(obj.optString("primaryBg"), defaults.primaryBg),
      secondaryBg = parseHex(obj.optString("secondaryBg"), defaults.secondaryBg),
      surface = parseHex(obj.optString("surface"), defaults.surface),
      surfaceVariant = parseHex(obj.optString("surfaceVariant"), defaults.surfaceVariant),
      accent = parseHex(obj.optString("accent"), defaults.accent),
      secondaryAccent = parseHex(obj.optString("secondaryAccent"), defaults.secondaryAccent),
      textPrimary = parseHex(obj.optString("textPrimary"), defaults.textPrimary),
      textSecondary = parseHex(obj.optString("textSecondary"), defaults.textSecondary),
      border = parseHex(obj.optString("border"), defaults.border),
      selectedContainer = parseHex(obj.optString("selectedContainer"), defaults.selectedContainer),
      warning = parseHex(obj.optString("warning"), defaults.warning),
      error = parseHex(obj.optString("error"), defaults.error),
      surfaceMaterial = enumOr(obj.optString("surfaceMaterial"), defaults.surfaceMaterial),
      transparencyAlpha = obj.optDouble("transparencyAlpha", defaults.transparencyAlpha.toDouble()).toFloat().coerceIn(0.35f, 1f),
      blurRadiusDp = obj.optDouble("blurRadiusDp", defaults.blurRadiusDp.toDouble()).toFloat().coerceIn(0f, 32f),
      gradientStart = parseHex(obj.optString("gradientStart"), defaults.gradientStart),
      gradientEnd = parseHex(obj.optString("gradientEnd"), defaults.gradientEnd),
      textureOverlay = enumOr(obj.optString("textureOverlay"), defaults.textureOverlay),
      headingFont = enumOr(obj.optString("headingFont"), defaults.headingFont),
      bodyFont = enumOr(obj.optString("bodyFont"), defaults.bodyFont),
      monoFont = enumOr(obj.optString("monoFont"), defaults.monoFont),
      fontSizeScale = obj.optDouble("fontSizeScale", defaults.fontSizeScale.toDouble()).toFloat().coerceIn(0.8f, 1.35f),
      lineHeightMultiplier = obj.optDouble("lineHeightMultiplier", defaults.lineHeightMultiplier.toDouble()).toFloat().coerceIn(1.1f, 1.7f),
      headingWeight = obj.optInt("headingWeight", defaults.headingWeight).coerceIn(400, 800),
      cornerRadiusDp = obj.optDouble("cornerRadiusDp", defaults.cornerRadiusDp.toDouble()).toFloat().coerceIn(0f, 32f),
      buttonRadiusDp = obj.optDouble("buttonRadiusDp", defaults.buttonRadiusDp.toDouble()).toFloat().coerceIn(0f, 40f),
      borderWidthDp = obj.optDouble("borderWidthDp", defaults.borderWidthDp.toDouble()).toFloat().coerceIn(0f, 4f),
      hardShadowOffsetDp = obj.optDouble("hardShadowOffsetDp", defaults.hardShadowOffsetDp.toDouble()).toFloat().coerceIn(0f, 10f),
      cardElevationDp = obj.optDouble("cardElevationDp", defaults.cardElevationDp.toDouble()).toFloat().coerceIn(0f, 16f),
      density = enumOr(obj.optString("density"), defaults.density),
      navStyle = enumOr(obj.optString("navStyle"), defaults.navStyle),
      headerStyle = enumOr(obj.optString("headerStyle"), defaults.headerStyle),
      animationIntensity = enumOr(obj.optString("animationIntensity"), defaults.animationIntensity),
      transitionDurationMs = obj.optInt("transitionDurationMs", defaults.transitionDurationMs).coerceIn(0, 600),
      reducedMotion = obj.optBoolean("reducedMotion", defaults.reducedMotion)
    )
  }

  private fun hex(value: Long): String = "#" + java.lang.Long.toHexString(value and 0xFFFFFFFFL).uppercase().padStart(8, '0')

  private fun parseHex(raw: String?, fallback: Long): Long {
    if (raw.isNullOrBlank()) return fallback
    val cleaned = raw.trim().removePrefix("#")
    return try {
      when (cleaned.length) {
        6 -> (0xFF000000L or cleaned.toLong(16))
        8 -> cleaned.toLong(16)
        else -> fallback
      }
    } catch (_: Exception) {
      fallback
    }
  }

  private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T {
    if (name.isNullOrBlank()) return fallback
    return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
  }
}
