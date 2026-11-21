package com.example.paperly.theme

object ThemePresets {

  private data class CollectionGrammar(
    val collection: ThemeCollection,
    val material: SurfaceMaterial,
    val texture: TextureOverlay,
    val headingFont: FontFamilyKey,
    val bodyFont: FontFamilyKey,
    val monoFont: FontFamilyKey,
    val cornerDp: Float,
    val buttonDp: Float,
    val borderDp: Float,
    val hardShadowDp: Float,
    val elevationDp: Float,
    val alpha: Float,
    val blurDp: Float,
    val density: DensityMode,
    val navStyle: NavStyle,
    val headerStyle: HeaderStyle,
    val anim: AnimationIntensity,
    val fontScale: Float = 1.0f,
    val lineHeight: Float = 1.35f,
    val headingWeight: Int = 700
  )

  private data class PaletteSeed(
    val idSuffix: String,
    val title: String,
    val variantTag: String,
    val isDark: Boolean,
    val bg1: Long,
    val bg2: Long,
    val surface: Long,
    val surfaceVar: Long,
    val accent: Long,
    val accent2: Long,
    val text1: Long,
    val text2: Long,
    val border: Long,
    val selected: Long,
    val grad1: Long = bg1,
    val grad2: Long = bg2,
    val warning: Long = if (isDark) 0xFFFBBF24 else 0xFFD97706,
    val error: Long = if (isDark) 0xFFF87171 else 0xFFDC2626
  )

  private val grammars: Map<ThemeCollection, CollectionGrammar> = listOf(
    CollectionGrammar(ThemeCollection.LIQUID_GLASS, SurfaceMaterial.LIQUID_GLASS, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 24f, 24f, 1.1f, 0f, 6f, 0.74f, 18f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.EXPRESSIVE),
    CollectionGrammar(ThemeCollection.GLASSMORPHISM, SurfaceMaterial.FROSTED_GLASS, TextureOverlay.NONE, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 20f, 18f, 1.0f, 0f, 4f, 0.76f, 16f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.BALANCED),
    CollectionGrammar(ThemeCollection.FROSTED_GLASS, SurfaceMaterial.FROSTED_GLASS, TextureOverlay.DOT_MATRIX, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 18f, 16f, 0.9f, 0f, 3f, 0.80f, 22f, DensityMode.SPACIOUS, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.NEUMORPHISM, SurfaceMaterial.RAISED_NEUMORPHIC, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 22f, 18f, 0f, 0f, 8f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.BOTTOM_BAR, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.MATERIAL_YOU, SurfaceMaterial.SOLID, TextureOverlay.NONE, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 24f, 50f, 0f, 0f, 2f, 1.0f, 0f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.EXPRESSIVE),
    CollectionGrammar(ThemeCollection.WINDOWS_FLUENT, SurfaceMaterial.ACRYLIC, TextureOverlay.NONE, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 8f, 6f, 1.0f, 0f, 3f, 0.88f, 14f, DensityMode.COMPACT, NavStyle.DOCKED_RAIL, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.BALANCED),
    CollectionGrammar(ThemeCollection.MACOS_INSPIRED, SurfaceMaterial.FROSTED_GLASS, TextureOverlay.NONE, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 14f, 10f, 0.8f, 0f, 4f, 0.86f, 16f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.BALANCED),
    CollectionGrammar(ThemeCollection.PURE_AMOLED, SurfaceMaterial.FLAT, TextureOverlay.NONE, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 14f, 12f, 0.8f, 0f, 0f, 1.0f, 0f, DensityMode.COMFORTABLE, NavStyle.BOTTOM_BAR, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.MIDNIGHT_BLUE, SurfaceMaterial.SOLID, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 16f, 14f, 0.8f, 0f, 3f, 0.96f, 0f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.CYBERPUNK, SurfaceMaterial.FLAT, TextureOverlay.TERMINAL_SCANLINES, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.JETBRAINS_MONO, 2f, 2f, 1.6f, 3f, 0f, 0.94f, 0f, DensityMode.COMPACT, NavStyle.BRUTALIST_BLOCK, HeaderStyle.TERMINAL_PROMPT, AnimationIntensity.EXPRESSIVE),
    CollectionGrammar(ThemeCollection.RETRO_TERMINAL, SurfaceMaterial.FLAT, TextureOverlay.TERMINAL_SCANLINES, FontFamilyKey.JETBRAINS_MONO, FontFamilyKey.JETBRAINS_MONO, FontFamilyKey.JETBRAINS_MONO, 0f, 0f, 1.2f, 0f, 0f, 1.0f, 0f, DensityMode.COMPACT, NavStyle.TERMINAL_TABS, HeaderStyle.TERMINAL_PROMPT, AnimationIntensity.NONE, fontScale = 0.95f),
    CollectionGrammar(ThemeCollection.SWISS_MINIMALISM, SurfaceMaterial.FLAT, TextureOverlay.EDITORIAL_RULES, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 0f, 0f, 1.0f, 0f, 0f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.EDITORIAL_UNDERLINE, HeaderStyle.EDITORIAL_SERIF, AnimationIntensity.SUBTLE, headingWeight = 800),
    CollectionGrammar(ThemeCollection.EDITORIAL, SurfaceMaterial.FLAT, TextureOverlay.EDITORIAL_RULES, FontFamilyKey.PLAYFAIR_DISPLAY, FontFamilyKey.LORA, FontFamilyKey.JETBRAINS_MONO, 4f, 4f, 0.8f, 0f, 0f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.EDITORIAL_UNDERLINE, HeaderStyle.EDITORIAL_SERIF, AnimationIntensity.SUBTLE, lineHeight = 1.48f),
    CollectionGrammar(ThemeCollection.VINTAGE_PAPER, SurfaceMaterial.SOLID, TextureOverlay.PAPER_GRAIN, FontFamilyKey.PLAYFAIR_DISPLAY, FontFamilyKey.LORA, FontFamilyKey.COURIER_PRIME, 6f, 6f, 1.0f, 0f, 1f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.EDITORIAL_UNDERLINE, HeaderStyle.EDITORIAL_SERIF, AnimationIntensity.SUBTLE, lineHeight = 1.48f),
    CollectionGrammar(ThemeCollection.TYPEWRITER, SurfaceMaterial.FLAT, TextureOverlay.PAPER_GRAIN, FontFamilyKey.COURIER_PRIME, FontFamilyKey.COURIER_PRIME, FontFamilyKey.COURIER_PRIME, 2f, 2f, 1.2f, 0f, 0f, 1.0f, 0f, DensityMode.COMFORTABLE, NavStyle.TERMINAL_TABS, HeaderStyle.TERMINAL_PROMPT, AnimationIntensity.NONE, lineHeight = 1.42f),
    CollectionGrammar(ThemeCollection.BRUTALIST, SurfaceMaterial.FLAT, TextureOverlay.NONE, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.JETBRAINS_MONO, 0f, 0f, 2.2f, 5f, 0f, 1.0f, 0f, DensityMode.COMFORTABLE, NavStyle.BRUTALIST_BLOCK, HeaderStyle.BRUTALIST_BANNER, AnimationIntensity.NONE, headingWeight = 800),
    CollectionGrammar(ThemeCollection.SCANDINAVIAN, SurfaceMaterial.SOLID, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 20f, 16f, 0.6f, 0f, 1f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.FLOATING_PILL, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.JAPANESE_MINIMALISM, SurfaceMaterial.FLAT, TextureOverlay.PAPER_GRAIN, FontFamilyKey.LORA, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 8f, 8f, 0.7f, 0f, 0f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.EDITORIAL_UNDERLINE, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE, lineHeight = 1.45f),
    CollectionGrammar(ThemeCollection.Y2K, SurfaceMaterial.GRADIENT, TextureOverlay.DOT_MATRIX, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.OUTFIT, FontFamilyKey.JETBRAINS_MONO, 18f, 50f, 1.5f, 2f, 4f, 0.94f, 6f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.EXPRESSIVE),
    CollectionGrammar(ThemeCollection.VAPORWAVE, SurfaceMaterial.GRADIENT, TextureOverlay.TERMINAL_SCANLINES, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.OUTFIT, FontFamilyKey.JETBRAINS_MONO, 12f, 6f, 1.4f, 3f, 4f, 0.90f, 8f, DensityMode.COMFORTABLE, NavStyle.TERMINAL_TABS, HeaderStyle.BRUTALIST_BANNER, AnimationIntensity.EXPRESSIVE),
    CollectionGrammar(ThemeCollection.SYNTHWAVE, SurfaceMaterial.GRADIENT, TextureOverlay.TERMINAL_SCANLINES, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 8f, 6f, 1.5f, 0f, 4f, 0.92f, 6f, DensityMode.COMFORTABLE, NavStyle.BRUTALIST_BLOCK, HeaderStyle.TERMINAL_PROMPT, AnimationIntensity.EXPRESSIVE),
    CollectionGrammar(ThemeCollection.PASTEL, SurfaceMaterial.SOLID, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 24f, 24f, 0.6f, 0f, 2f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.FLOATING_PILL, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.BALANCED),
    CollectionGrammar(ThemeCollection.MONOCHROME, SurfaceMaterial.FLAT, TextureOverlay.EDITORIAL_RULES, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 4f, 4f, 1.1f, 0f, 0f, 1.0f, 0f, DensityMode.COMFORTABLE, NavStyle.EDITORIAL_UNDERLINE, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.ORGANIC_NATURE, SurfaceMaterial.SOLID, TextureOverlay.PAPER_GRAIN, FontFamilyKey.LORA, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 18f, 16f, 0.8f, 0f, 2f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.FLOATING_PILL, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.SUNSET_GRADIENT, SurfaceMaterial.GRADIENT, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 20f, 20f, 0.8f, 0f, 4f, 0.92f, 10f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.BALANCED),
    CollectionGrammar(ThemeCollection.OCEAN, SurfaceMaterial.LIQUID_GLASS, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 20f, 20f, 0.9f, 0f, 4f, 0.82f, 14f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.BALANCED),
    CollectionGrammar(ThemeCollection.AURORA, SurfaceMaterial.FROSTED_GLASS, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 22f, 22f, 1.0f, 0f, 5f, 0.78f, 16f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.EXPRESSIVE),
    CollectionGrammar(ThemeCollection.INDUSTRIAL, SurfaceMaterial.FLAT, TextureOverlay.DOT_MATRIX, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 3f, 3f, 1.5f, 0f, 0f, 1.0f, 0f, DensityMode.COMPACT, NavStyle.DOCKED_RAIL, HeaderStyle.BRUTALIST_BANNER, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.METALLIC, SurfaceMaterial.ACRYLIC, TextureOverlay.NONE, FontFamilyKey.SPACE_GROTESK, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 10f, 8f, 1.1f, 0f, 4f, 0.92f, 8f, DensityMode.COMFORTABLE, NavStyle.BOTTOM_BAR, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.HOLOGRAPHIC, SurfaceMaterial.LIQUID_GLASS, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 24f, 28f, 1.2f, 0f, 6f, 0.76f, 18f, DensityMode.COMFORTABLE, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.EXPRESSIVE),
    CollectionGrammar(ThemeCollection.BLUEPRINT, SurfaceMaterial.FLAT, TextureOverlay.BLUEPRINT_GRID, FontFamilyKey.JETBRAINS_MONO, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 2f, 2f, 1.2f, 0f, 0f, 0.95f, 0f, DensityMode.COMPACT, NavStyle.TERMINAL_TABS, HeaderStyle.TERMINAL_PROMPT, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.RETRO_COMPUTING, SurfaceMaterial.SOLID, TextureOverlay.DOT_MATRIX, FontFamilyKey.COURIER_PRIME, FontFamilyKey.JETBRAINS_MONO, FontFamilyKey.JETBRAINS_MONO, 2f, 2f, 1.8f, 3f, 0f, 1.0f, 0f, DensityMode.COMPACT, NavStyle.BRUTALIST_BLOCK, HeaderStyle.TERMINAL_PROMPT, AnimationIntensity.NONE),
    CollectionGrammar(ThemeCollection.ACADEMIC, SurfaceMaterial.SOLID, TextureOverlay.EDITORIAL_RULES, FontFamilyKey.PLAYFAIR_DISPLAY, FontFamilyKey.LORA, FontFamilyKey.COURIER_PRIME, 6f, 6f, 0.8f, 0f, 1f, 1.0f, 0f, DensityMode.COMFORTABLE, NavStyle.EDITORIAL_UNDERLINE, HeaderStyle.EDITORIAL_SERIF, AnimationIntensity.SUBTLE, lineHeight = 1.46f),
    CollectionGrammar(ThemeCollection.MODERN_CORPORATE, SurfaceMaterial.SOLID, TextureOverlay.NONE, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 10f, 8f, 0.9f, 0f, 2f, 1.0f, 0f, DensityMode.COMPACT, NavStyle.BOTTOM_BAR, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.MINIMAL_WHITE, SurfaceMaterial.FLAT, TextureOverlay.NONE, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 14f, 12f, 0.7f, 0f, 0f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.FLOATING_PILL, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.SUBTLE),
    CollectionGrammar(ThemeCollection.DARK_EDITORIAL, SurfaceMaterial.FLAT, TextureOverlay.EDITORIAL_RULES, FontFamilyKey.PLAYFAIR_DISPLAY, FontFamilyKey.LORA, FontFamilyKey.JETBRAINS_MONO, 6f, 6f, 0.8f, 0f, 0f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.EDITORIAL_UNDERLINE, HeaderStyle.EDITORIAL_SERIF, AnimationIntensity.SUBTLE, lineHeight = 1.48f),
    CollectionGrammar(ThemeCollection.WARM_IVORY, SurfaceMaterial.SOLID, TextureOverlay.PAPER_GRAIN, FontFamilyKey.LORA, FontFamilyKey.LORA, FontFamilyKey.COURIER_PRIME, 14f, 12f, 0.8f, 0f, 1f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.EDITORIAL_UNDERLINE, HeaderStyle.EDITORIAL_SERIF, AnimationIntensity.SUBTLE, lineHeight = 1.46f),
    CollectionGrammar(ThemeCollection.SOFT_LAVENDER, SurfaceMaterial.SOLID, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 22f, 20f, 0.7f, 0f, 2f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.FLOATING_PILL, HeaderStyle.MINIMAL_CLEAN, AnimationIntensity.BALANCED),
    CollectionGrammar(ThemeCollection.CLAY, SurfaceMaterial.RAISED_NEUMORPHIC, TextureOverlay.NONE, FontFamilyKey.OUTFIT, FontFamilyKey.PLUS_JAKARTA_SANS, FontFamilyKey.JETBRAINS_MONO, 28f, 24f, 0f, 0f, 7f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.FLOATING_PILL, HeaderStyle.GLASS_CAPSULE, AnimationIntensity.EXPRESSIVE),
    CollectionGrammar(ThemeCollection.BOTANICAL, SurfaceMaterial.SOLID, TextureOverlay.PAPER_GRAIN, FontFamilyKey.PLAYFAIR_DISPLAY, FontFamilyKey.LORA, FontFamilyKey.JETBRAINS_MONO, 16f, 14f, 0.8f, 0f, 1f, 1.0f, 0f, DensityMode.SPACIOUS, NavStyle.EDITORIAL_UNDERLINE, HeaderStyle.EDITORIAL_SERIF, AnimationIntensity.SUBTLE)
  ).associateBy { it.collection }

  private val basePalettes: Map<ThemeCollection, List<PaletteSeed>> = mapOf(
    ThemeCollection.LIQUID_GLASS to listOf(
      PaletteSeed("azure_day", "Liquid Azure", "Light", false, 0xFFEAF3FF, 0xFFD9EAFF, 0xD9FFFFFF, 0xCCF0F7FF, 0xFF0284C7, 0xFF38BDF8, 0xFF0F172A, 0xFF475569, 0x99FFFFFF, 0xFFD6EEFF, 0xFFE0F2FE, 0xFFF5F3FF),
      PaletteSeed("obsidian_drop", "Obsidian Dew", "Dark", true, 0xFF090F1A, 0xFF101B2D, 0xBA172338, 0xAD1E2E49, 0xFF38BDF8, 0xFF818CF8, 0xFFF1F5F9, 0xFF94A3B8, 0x45FFFFFF, 0xFF1E3A5F, 0xFF0B1324, 0xFF13243B),
      PaletteSeed("opal_mist", "Opal Mist", "Warm", false, 0xFFF8F4FA, 0xFFEEE8F6, 0xD9FFFFFF, 0xCCFAF5FF, 0xFF7C3AED, 0xFFEC4899, 0xFF1E1B2E, 0xFF5B5570, 0x99FFFFFF, 0xFFEDE9FE, 0xFFFDF2F8, 0xFFEDE9FE),
      PaletteSeed("glacier_clear", "Glacier Lens", "Cool", false, 0xFFECFDF5, 0xFFD1FAE5, 0xD9FFFFFF, 0xCCF0FDFA, 0xFF0D9488, 0xFF06B6D4, 0xFF0F2926, 0xFF4B6B66, 0x99FFFFFF, 0xFFCCFBF1, 0xFFECFDF5, 0xFFE0F2FE),
      PaletteSeed("prism_dark", "Prism Night", "Vivid", true, 0xFF0C0A1D, 0xFF1A1438, 0xBA211B47, 0xAD2D255E, 0xFF22D3EE, 0xFFF472B6, 0xFFF8FAFC, 0xFFA5B4FC, 0x40E2E8F0, 0xFF312E81, 0xFF0F172A, 0xFF2E1065),
      PaletteSeed("amber_crystal", "Amber Crystal", "Warm", false, 0xFFFFF8EB, 0xFFFEF0D1, 0xDCFFFFFF, 0xCCFFFBEB, 0xFFD97706, 0xFFF59E0B, 0xFF291B0C, 0xFF6B5335, 0x99FFFFFF, 0xFFFEF3C7, 0xFFFFFBEB, 0xFFFDE68A),
      PaletteSeed("graphite_glass", "Graphite Lens", "Muted", true, 0xFF121417, 0xFF1C2026, 0xC2242931, 0xB32D333D, 0xFF60A5FA, 0xFF94A3B8, 0xFFF3F4F6, 0xFF9CA3AF, 0x3DFFFFFF, 0xFF2B3A4D, 0xFF111827, 0xFF1F2937)
    ),
    ThemeCollection.EDITORIAL to listOf(
      PaletteSeed("morning_press", "Morning Press", "Light", false, 0xFFFAF8F5, 0xFFF2EFE9, 0xFFFFFFFF, 0xFFF5F2EC, 0xFF9F1239, 0xFF1E293B, 0xFF18181B, 0xFF52525B, 0xFFD6D3CD, 0xFFFCE7F3),
      PaletteSeed("broadsheet", "Broadsheet Ink", "High Contrast", false, 0xFFFFFFFF, 0xFFF4F4F5, 0xFFFFFFFF, 0xFFF4F4F5, 0xFF111827, 0xFFB91C1C, 0xFF09090B, 0xFF52525B, 0xFFE4E4E7, 0xFFE4E4E7),
      PaletteSeed("sepia_folio", "Sepia Folio", "Warm", false, 0xFFF7F1E5, 0xFFEDE4D3, 0xFFFBF7EE, 0xFFF0E8D8, 0xFF7C2D12, 0xFF9A3412, 0xFF292119, 0xFF6B5D4D, 0xFFD9CDB8, 0xFFEBDDD0),
      PaletteSeed("noir_review", "Noir Review", "Dark", true, 0xFF121110, 0xFF1B1917, 0xFF1F1D1B, 0xFF292623, 0xFFEAB308, 0xFFFCA5A5, 0xFFF5F5F4, 0xFFA8A29E, 0xFF332F2B, 0xFF362F1E),
      PaletteSeed("oxford_essay", "Oxford Essay", "Cool", false, 0xFFF8FAFC, 0xFFEFF4F9, 0xFFFFFFFF, 0xFFF1F5F9, 0xFF1D4ED8, 0xFF0F766E, 0xFF0F172A, 0xFF475569, 0xFFCBD5E1, 0xFFDBEAFE),
      PaletteSeed("sage_gazette", "Sage Gazette", "Muted", false, 0xFFF4F6F3, 0xFFE9EDE7, 0xFFFCFDFB, 0xFFEEF2EC, 0xFF166534, 0xFF3F6212, 0xFF1A241B, 0xFF526053, 0xFFCFD8CC, 0xFFDCFCE7),
      PaletteSeed("velvet_journal", "Velvet Journal", "Dark", true, 0xFF18131A, 0xFF231B26, 0xFF281F2C, 0xFF342938, 0xFFF472B6, 0xFFC084FC, 0xFFFAF5FF, 0xFFC4B5FD, 0xFF43354A, 0xFF4A2545)
    ),
    ThemeCollection.PURE_AMOLED to listOf(
      PaletteSeed("obsidian_ice", "Obsidian Ice", "Dark", true, 0xFF000000, 0xFF050505, 0xFF0A0A0A, 0xFF121212, 0xFF38BDF8, 0xFF0EA5E9, 0xFFFFFFFF, 0xFFA1A1AA, 0xFF222222, 0xFF0C2D42),
      PaletteSeed("crimson_oled", "Crimson OLED", "Vivid", true, 0xFF000000, 0xFF050505, 0xFF0A0A0A, 0xFF141010, 0xFFEF4444, 0xFFF87171, 0xFFFAFAFA, 0xFFA1A1AA, 0xFF261C1C, 0xFF3B1212),
      PaletteSeed("emerald_oled", "Emerald OLED", "Dark", true, 0xFF000000, 0xFF040705, 0xFF080D0A, 0xFF101914, 0xFF10B981, 0xFF34D399, 0xFFF8FAFC, 0xFF94A3B8, 0xFF1B2B22, 0xFF0D3325),
      PaletteSeed("amber_oled", "Amber OLED", "Warm", true, 0xFF000000, 0xFF070502, 0xFF0D0A06, 0xFF17120C, 0xFFF59E0B, 0xFFFBBF24, 0xFFFEF3C7, 0xFFA8A29E, 0xFF292218, 0xFF3D2A0D),
      PaletteSeed("violet_oled", "Violet OLED", "Vivid", true, 0xFF000000, 0xFF050408, 0xFF0B0912, 0xFF151221, 0xFFA855F7, 0xFFC084FC, 0xFFFAF5FF, 0xFFA1A1AA, 0xFF241D36, 0xFF2E1747),
      PaletteSeed("mono_oled", "Stark OLED", "High Contrast", true, 0xFF000000, 0xFF000000, 0xFF080808, 0xFF141414, 0xFFFFFFFF, 0xFFD4D4D8, 0xFFFFFFFF, 0xFFA1A1AA, 0xFF2E2E2E, 0xFF27272A),
      PaletteSeed("cobalt_oled", "Cobalt OLED", "Cool", true, 0xFF000000, 0xFF03050A, 0xFF090D16, 0xFF111827, 0xFF60A5FA, 0xFF3B82F6, 0xFFF1F5F9, 0xFF94A3B8, 0xFF1E293B, 0xFF172554)
    ),
    ThemeCollection.RETRO_TERMINAL to listOf(
      PaletteSeed("phosphor_green", "Phosphor P1", "Dark", true, 0xFF050E07, 0xFF09170C, 0xFF0B1C0F, 0xFF102816, 0xFF22C55E, 0xFF4ADE80, 0xFF86EFAC, 0xFF4ADE80, 0xFF1D4A28, 0xFF143D20),
      PaletteSeed("amber_vt220", "Amber VT220", "Warm", true, 0xFF100A02, 0xFF1A1104, 0xFF211606, 0xFF2D1E09, 0xFFF59E0B, 0xFFFBBF24, 0xFFFDE68A, 0xFFD97706, 0xFF4D340E, 0xFF452C09),
      PaletteSeed("cyan_bbs", "Cyan ANSI", "Cool", true, 0xFF040D12, 0xFF08161F, 0xFF0B1E29, 0xFF122C3B, 0xFF22D3EE, 0xFF06B6D4, 0xFFCFFAFE, 0xFF67E8F9, 0xFF1E4459, 0xFF164E63),
      PaletteSeed("ibm_paper", "Teletype Roll", "Light", false, 0xFFF3F4EE, 0xFFE7E9DF, 0xFFFAFBF7, 0xFFECEEE6, 0xFF15803D, 0xFF166534, 0xFF142117, 0xFF4B5E4E, 0xFFB8C2B4, 0xFFD6E6D4),
      PaletteSeed("crimson_sys", "Root Red", "Vivid", true, 0xFF120507, 0xFF1C090C, 0xFF240C10, 0xFF331117, 0xFFFB7185, 0xFFF43F5E, 0xFFFFE4E6, 0xFFFDA4AF, 0xFF541C26, 0xFF4C111E),
      PaletteSeed("solarized_tty", "Solarized TTY", "Muted", true, 0xFF002B36, 0xFF073642, 0xFF073642, 0xFF0D4452, 0xFF2AA198, 0xFFB58900, 0xFFEEE8D5, 0xFF93A1A1, 0xFF1F5461, 0xFF0B4F5C),
      PaletteSeed("monochrome_dos", "White Phosphor", "High Contrast", true, 0xFF09090B, 0xFF111114, 0xFF151519, 0xFF1F1F26, 0xFFE4E4E7, 0xFFA1A1AA, 0xFFFAFAFA, 0xFFA1A1AA, 0xFF3F3F46, 0xFF27272A)
    ),
    ThemeCollection.BRUTALIST to listOf(
      PaletteSeed("hazard_yellow", "Hazard Concrete", "High Contrast", false, 0xFFF4F4F0, 0xFFE5E5DF, 0xFFFFFFFF, 0xFFFEF08A, 0xFFE11D48, 0xFF000000, 0xFF09090B, 0xFF3F3F46, 0xFF09090B, 0xFFFDE047),
      PaletteSeed("cobalt_poster", "Poster Cobalt", "Vivid", false, 0xFFF8FAFC, 0xFFE2E8F0, 0xFFFFFFFF, 0xFFDBEAFE, 0xFF2563EB, 0xFFEA580C, 0xFF020617, 0xFF334155, 0xFF020617, 0xFFBFDBFE),
      PaletteSeed("acid_lime", "Acid Slab", "Dark", true, 0xFF0D0D0D, 0xFF171717, 0xFF1A1A1A, 0xFF262626, 0xFFA3E635, 0xFFFACC15, 0xFFFAFAFA, 0xFFD4D4D8, 0xFFA3E635, 0xFF365314),
      PaletteSeed("vermilion_block", "Vermilion Block", "Warm", false, 0xFFFFF5F2, 0xFFFFE4DC, 0xFFFFFFFF, 0xFFFECACA, 0xFFDC2626, 0xFF111827, 0xFF111827, 0xFF4B5563, 0xFF111827, 0xFFFCA5A5),
      PaletteSeed("raw_plaster", "Raw Plaster", "Light", false, 0xFFEBEAE4, 0xFFDCDAD2, 0xFFF7F6F2, 0xFFE5E3DC, 0xFF18181B, 0xFF52525B, 0xFF111111, 0xFF444444, 0xFF111111, 0xFFD4D4CE),
      PaletteSeed("night_concrete", "Night Concrete", "Dark", true, 0xFF111113, 0xFF1A1A1F, 0xFF1F1F24, 0xFF2C2C33, 0xFFFF6B35, 0xFFFACC15, 0xFFFFFFFF, 0xFFB8B8C2, 0xFFFFFFFF, 0xFF4A2511),
      PaletteSeed("magenta_zine", "Zine Magenta", "Vivid", false, 0xFFFDF2F8, 0xFFFCE7F3, 0xFFFFFFFF, 0xFFFBCFE8, 0xFFDB2777, 0xFF4F46E5, 0xFF18181B, 0xFF52525B, 0xFF18181B, 0xFFF9A8D4)
    ),
    ThemeCollection.BLUEPRINT to listOf(
      PaletteSeed("architect_cyan", "Architect Cyan", "Dark", true, 0xFF0A2442, 0xFF0E3057, 0xFF103866, 0xFF164780, 0xFF38BDF8, 0xFFBAE6FD, 0xFFF0F9FF, 0xFF93C5FD, 0xFF38BDF8, 0xFF1D4ED8),
      PaletteSeed("cad_day", "Drafting Vellum", "Light", false, 0xFFF0F7FF, 0xFFE0EEFF, 0xFFFFFFFF, 0xFFE8F2FF, 0xFF0369A1, 0xFF0284C7, 0xFF0C243D, 0xFF3E648A, 0xFF7DD3FC, 0xFFBAE6FD),
      PaletteSeed("marine_chart", "Marine Chart", "Dark", true, 0xFF071D26, 0xFF0B2936, 0xFF0F3545, 0xFF16485C, 0xFF2DD4BF, 0xFF5EEAD4, 0xFFECFEFF, 0xFF99F6E4, 0xFF2DD4BF, 0xFF115E59),
      PaletteSeed("graph_emerald", "Engineering Pad", "Light", false, 0xFFF1F8F3, 0xFFE3F0E7, 0xFFFAFDFB, 0xFFE6F4EA, 0xFF15803D, 0xFF0D9488, 0xFF0F291A, 0xFF486B56, 0xFF86EFAC, 0xFFBBF7D0),
      PaletteSeed("sepia_draft", "Archive Schematic", "Warm", false, 0xFFF7F2E8, 0xFFECE3D3, 0xFFFDFBF7, 0xFFF2EAD9, 0xFFB45309, 0xFF92400E, 0xFF2C1E10, 0xFF6B5640, 0xFFD9B88C, 0xFFFDE68A),
      PaletteSeed("indigo_grid", "Indigo Plotter", "Dark", true, 0xFF101336, 0xFF181D4D, 0xFF1E245E, 0xFF283078, 0xFFA5B4FC, 0xFF60A5FA, 0xFFEEF2FF, 0xFFA5B4FC, 0xFF6366F1, 0xFF3730A3),
      PaletteSeed("mono_cad", "Graphite CAD", "Muted", true, 0xFF16191D, 0xFF1E2228, 0xFF242930, 0xFF2F3640, 0xFFE2E8F0, 0xFF94A3B8, 0xFFF8FAFC, 0xFF94A3B8, 0xFF475569, 0xFF334155)
    ),
    ThemeCollection.VINTAGE_PAPER to listOf(
      PaletteSeed("parchment", "Aged Parchment", "Light", false, 0xFFF5ECD8, 0xFFEAE0C8, 0xFFFAF4E6, 0xFFF0E6CF, 0xFF8B4513, 0xFF6B3A1E, 0xFF2C2218, 0xFF695846, 0xFFCDBEA3, 0xFFE6D5B8),
      PaletteSeed("library_leather", "Library Leather", "Dark", true, 0xFF1F1812, 0xFF2B2119, 0xFF33271E, 0xFF403227, 0xFFD97706, 0xFFEAB308, 0xFFF5EBDC, 0xFFC4B39D, 0xFF524032, 0xFF4F3822),
      PaletteSeed("newsprint", "Sunday Newsprint", "Muted", false, 0xFFECEAE2, 0xFFE0DDD2, 0xFFF4F2EB, 0xFFE8E5DC, 0xFF334155, 0xFF991B1B, 0xFF1E1E1C, 0xFF57564E, 0xFFC8C4B6, 0xFFDCD8CA),
      PaletteSeed("tea_stain", "Tea Stained", "Warm", false, 0xFFF3E5D0, 0xFFE8D5BA, 0xFFFAF0E1, 0xFFEEDDC4, 0xFF9A3412, 0xFFB45309, 0xFF2D1E12, 0xFF6E5744, 0xFFD1B797, 0xFFEBD2B0),
      PaletteSeed("herbarium_sheet", "Herbarium Sheet", "Light", false, 0xFFF3F1E8, 0xFFE8E4D5, 0xFFFAF9F4, 0xFFEDEAE0, 0xFF3F6212, 0xFF713F12, 0xFF1F2419, 0xFF5A6150, 0xFFC9C4B1, 0xFFDFE6D1),
      PaletteSeed("evening_study", "Evening Study", "Dark", true, 0xFF181A17, 0xFF222521, 0xFF292D28, 0xFF343932, 0xFFCA8A04, 0xFF84CC16, 0xFFF2EFE9, 0xFFB3B0A6, 0xFF434940, 0xFF3C4233),
      PaletteSeed("linen_ledger", "Linen Ledger", "Cool", false, 0xFFF2F0EA, 0xFFE5E2D9, 0xFFFAF9F5, 0xFFECEAE2, 0xFF1E40AF, 0xFF0F766E, 0xFF1E2229, 0xFF58606E, 0xFFC7C4B8, 0xFFDBE4F0)
    )
  )

  private data class AccentScheme(
    val suffix: String,
    val label: String,
    val tag: String,
    val isDark: Boolean,
    val primaryBg: Long,
    val secondaryBg: Long,
    val surface: Long,
    val surfaceVariant: Long,
    val accent: Long,
    val secondaryAccent: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    val border: Long,
    val selected: Long,
    val grad1: Long = primaryBg,
    val grad2: Long = secondaryBg
  )

  private val synthesizedCollectionPalettes: Map<ThemeCollection, List<AccentScheme>> = mapOf(
    ThemeCollection.GLASSMORPHISM to schemes(0xFFEBF4FF, 0xFFFCE7F3, 0xFF6366F1, 0xFFEC4899, 0xFF0F0E17, 0xFF1C1933, 0xFF38BDF8, 0xFFF472B6),
    ThemeCollection.FROSTED_GLASS to schemes(0xFFF1F5F9, 0xFFE2E8F0, 0xFF0EA5E9, 0xFF14B8A6, 0xFF0E141B, 0xFF17212B, 0xFF7DD3FC, 0xFF5EEAD4),
    ThemeCollection.NEUMORPHISM to schemes(0xFFE6EBF2, 0xFFDCE3EC, 0xFF4F46E5, 0xFF0EA5E9, 0xFF21262E, 0xFF29303A, 0xFF818CF8, 0xFF38BDF8),
    ThemeCollection.MATERIAL_YOU to schemes(0xFFF6F2FF, 0xFFECE6FA, 0xFF6750A4, 0xFF006A6A, 0xFF141218, 0xFF211F26, 0xFFD0BCFF, 0xFF7DD3FC),
    ThemeCollection.WINDOWS_FLUENT to schemes(0xFFF3F6FA, 0xFFE8EEF5, 0xFF0067C0, 0xFF0F766E, 0xFF1B1D21, 0xFF24272C, 0xFF60CDFF, 0xFF4CC2FF),
    ThemeCollection.MACOS_INSPIRED to schemes(0xFFF5F5F7, 0xFFE8E8ED, 0xFF007AFF, 0xFF5856D6, 0xFF1C1C1E, 0xFF2C2C2E, 0xFF0A84FF, 0xFF5E5CE6),
    ThemeCollection.MIDNIGHT_BLUE to schemes(0xFFEFF4FF, 0xFFE0EAFF, 0xFF1D4ED8, 0xFF0284C7, 0xFF0B1120, 0xFF111A2E, 0xFF60A5FA, 0xFF38BDF8, preferDarkFirst = true),
    ThemeCollection.CYBERPUNK to schemes(0xFFFFF7E6, 0xFFFEF08A, 0xFFD946EF, 0xFF0284C7, 0xFF0A0612, 0xFF140B24, 0xFFF43F5E, 0xFF00F0FF, preferDarkFirst = true),
    ThemeCollection.SWISS_MINIMALISM to schemes(0xFFFFFFFF, 0xFFF4F4F4, 0xFFE11D48, 0xFF111827, 0xFF111111, 0xFF1D1D1F, 0xFFFF3B30, 0xFFE5E5E5),
    ThemeCollection.TYPEWRITER to schemes(0xFFF6F2E9, 0xFFEBE5D8, 0xFF991B1B, 0xFF1F2937, 0xFF181614, 0xFF24211D, 0xFFF59E0B, 0xFFD6D3D1),
    ThemeCollection.SCANDINAVIAN to schemes(0xFFF7F6F2, 0xFFECEAE3, 0xFF2B5B6C, 0xFFB45309, 0xFF181C1E, 0xFF22282B, 0xFF7CA5B8, 0xFFD9A066),
    ThemeCollection.JAPANESE_MINIMALISM to schemes(0xFFF9F7F2, 0xFFEEEAE0, 0xFF8C2D19, 0xFF4A5D4E, 0xFF161614, 0xFF21211E, 0xFFD97757, 0xFF8AA899),
    ThemeCollection.Y2K to schemes(0xFFEBF8FF, 0xFFFCE7F3, 0xFF0284C7, 0xFFEC4899, 0xFF111322, 0xFF1C2038, 0xFF38BDF8, 0xFFF472B6),
    ThemeCollection.VAPORWAVE to schemes(0xFFFFEEF8, 0xFFE0F2FE, 0xFFDB2777, 0xFF0891B2, 0xFF190B28, 0xFF26123D, 0xFFFF71CE, 0xFF01CDFE, preferDarkFirst = true),
    ThemeCollection.SYNTHWAVE to schemes(0xFFFDF2F8, 0xFFFCE7F3, 0xFF9333EA, 0xFFEA580C, 0xFF0D041A, 0xFF1B0933, 0xFFFF2A85, 0xFF22D3EE, preferDarkFirst = true),
    ThemeCollection.PASTEL to schemes(0xFFFFF5F7, 0xFFF0FDF4, 0xFFEC4899, 0xFF10B981, 0xFF1E1B24, 0xFF292533, 0xFFF9A8D4, 0xFF6EE7B7),
    ThemeCollection.MONOCHROME to schemes(0xFFFAFAFA, 0xFFEFEFEF, 0xFF18181B, 0xFF52525B, 0xFF0E0E10, 0xFF18181B, 0xFFFAFAFA, 0xFFA1A1AA),
    ThemeCollection.ORGANIC_NATURE to schemes(0xFFF4F7F2, 0xFFE6EDE1, 0xFF2F6B3F, 0xFF8C5A3C, 0xFF131B15, 0xFF1D2920, 0xFF6EE7B7, 0xFFD99B66),
    ThemeCollection.SUNSET_GRADIENT to schemes(0xFFFFF4ED, 0xFFFEE2E2, 0xFFEA580C, 0xFFDB2777, 0xFF1E1014, 0xFF2E171D, 0xFFFB923C, 0xFFF472B6),
    ThemeCollection.OCEAN to schemes(0xFFECFEFF, 0xFFDFF4FF, 0xFF0284C7, 0xFF0D9488, 0xFF071926, 0xFF0D273A, 0xFF38BDF8, 0xFF2DD4BF),
    ThemeCollection.AURORA to schemes(0xFFECFDF5, 0xFFEFF6FF, 0xFF059669, 0xFF7C3AED, 0xFF071318, 0xFF0E222B, 0xFF34D399, 0xFFA78BFA, preferDarkFirst = true),
    ThemeCollection.INDUSTRIAL to schemes(0xFFECEEEF, 0xFFDDE1E4, 0xFFD97706, 0xFF334155, 0xFF16181A, 0xFF202326, 0xFFF59E0B, 0xFF94A3B8),
    ThemeCollection.METALLIC to schemes(0xFFF2F3F5, 0xFFE5E7EB, 0xFF475569, 0xFFB45309, 0xFF15171B, 0xFF1F2329, 0xFFD4AF37, 0xFF94A3B8),
    ThemeCollection.HOLOGRAPHIC to schemes(0xFFF5F3FF, 0xFFECFEFF, 0xFF7C3AED, 0xFF06B6D4, 0xFF110F1D, 0xFF1B1730, 0xFFC084FC, 0xFF22D3EE),
    ThemeCollection.RETRO_COMPUTING to schemes(0xFFEFECE4, 0xFFE0DCCE, 0xFF1D4ED8, 0xFF0F766E, 0xFF16181D, 0xFF22252D, 0xFF60A5FA, 0xFF34D399),
    ThemeCollection.ACADEMIC to schemes(0xFFFAF8F3, 0xFFEEE9DC, 0xFF7F1D1D, 0xFF1E3A8A, 0xFF15181E, 0xFF20242D, 0xFFFCA5A5, 0xFF93C5FD),
    ThemeCollection.MODERN_CORPORATE to schemes(0xFFF8FAFC, 0xFFEFF3F8, 0xFF0F4C81, 0xFF0D9488, 0xFF0F172A, 0xFF1E293B, 0xFF38BDF8, 0xFF60A5FA),
    ThemeCollection.MINIMAL_WHITE to schemes(0xFFFFFFFF, 0xFFF7F7F8, 0xFF111827, 0xFF2563EB, 0xFF141416, 0xFF1F1F23, 0xFFE2E8F0, 0xFF60A5FA),
    ThemeCollection.DARK_EDITORIAL to schemes(0xFFF5F3EF, 0xFFEAE6DF, 0xFF9F1239, 0xFF78350F, 0xFF121214, 0xFF1B1B1F, 0xFFE5B869, 0xFFFDA4AF, preferDarkFirst = true),
    ThemeCollection.WARM_IVORY to schemes(0xFFFDF8EE, 0xFFF5ECD9, 0xFFB45309, 0xFF4D7C0F, 0xFF1F1A14, 0xFF2B241C, 0xFFFBBF24, 0xFFA3E635),
    ThemeCollection.SOFT_LAVENDER to schemes(0xFFF7F4FF, 0xFFEDE8FC, 0xFF7C3AED, 0xFFDB2777, 0xFF181424, 0xFF231D36, 0xFFC4B5FD, 0xFFF472B6),
    ThemeCollection.CLAY to schemes(0xFFF7EFEA, 0xFFEDE0D8, 0xFFC2410C, 0xFF0D9488, 0xFF1F1916, 0xFF2C231F, 0xFFFB923C, 0xFF5EEAD4),
    ThemeCollection.BOTANICAL to schemes(0xFFF4F7F1, 0xFFE7EFE1, 0xFF15803D, 0xFFB45309, 0xFF121B14, 0xFF1B281E, 0xFF4ADE80, 0xFFFBBF24)
  )

  private fun schemes(
    lBg1: Long, lBg2: Long, lAcc1: Long, lAcc2: Long,
    dBg1: Long, dBg2: Long, dAcc1: Long, dAcc2: Long,
    preferDarkFirst: Boolean = false
  ): List<AccentScheme> {
    val lightPrimary = AccentScheme("day", "Daylight", "Light", false, lBg1, lBg2, 0xFFFFFFFF, lBg2, lAcc1, lAcc2, 0xFF111827, 0xFF4B5563, mix(lBg2, 0xFF94A3B8, 0.45f), mix(0xFFFFFFFF, lAcc1, 0.16f), lBg1, lBg2)
    val darkPrimary = AccentScheme("night", "Nightfall", "Dark", true, dBg1, dBg2, mix(dBg2, 0xFFFFFFFF, 0.04f), mix(dBg2, 0xFFFFFFFF, 0.09f), dAcc1, dAcc2, 0xFFF8FAFC, 0xFFA1A1AA, mix(dBg2, 0xFFFFFFFF, 0.14f), mix(dBg2, dAcc1, 0.25f), dBg1, dBg2)
    val warmLight = AccentScheme("warm", "Sunlit", "Warm", false, mix(lBg1, 0xFFFFF7E6, 0.5f), mix(lBg2, 0xFFFEF3C7, 0.4f), 0xFFFFFCF7, mix(lBg2, 0xFFFDE68A, 0.28f), lAcc2, lAcc1, 0xFF1F1914, 0xFF6B5C4E, mix(lBg2, 0xFFD6C4A8, 0.5f), mix(0xFFFFFFFF, lAcc2, 0.16f), mix(lBg1, 0xFFFFF7E6, 0.5f), lBg2)
    val coolMist = AccentScheme("mist", "Mist", "Cool", false, mix(lBg1, 0xFFEFF6FF, 0.5f), mix(lBg2, 0xFFDBEAFE, 0.4f), 0xFFFAFDFF, mix(lBg2, 0xFFBFDBFE, 0.28f), mix(lAcc1, 0xFF0284C7, 0.4f), lAcc2, 0xFF0F172A, 0xFF475569, mix(lBg2, 0xFF94A3B8, 0.4f), mix(0xFFFFFFFF, lAcc1, 0.18f), lBg1, mix(lBg2, 0xFFE0F2FE, 0.5f))
    val deepSlate = AccentScheme("slate", "Deep Slate", "Dark", true, mix(dBg1, 0xFF0F172A, 0.45f), mix(dBg2, 0xFF1E293B, 0.45f), mix(dBg2, 0xFF1E293B, 0.55f), mix(dBg2, 0xFF334155, 0.55f), dAcc2, dAcc1, 0xFFF1F5F9, 0xFF94A3B8, mix(dBg2, 0xFF475569, 0.6f), mix(dBg2, dAcc2, 0.26f), dBg1, mix(dBg2, 0xFF1E293B, 0.5f))
    val vividMix = AccentScheme("vivid", "Vivid", "Vivid", preferDarkFirst, if (preferDarkFirst) dBg1 else lBg1, if (preferDarkFirst) mix(dBg2, dAcc1, 0.14f) else mix(lBg2, lAcc1, 0.12f), if (preferDarkFirst) mix(dBg2, dAcc1, 0.08f) else 0xFFFFFFFF, if (preferDarkFirst) mix(dBg2, dAcc2, 0.16f) else mix(lBg2, lAcc2, 0.14f), if (preferDarkFirst) dAcc1 else lAcc1, if (preferDarkFirst) dAcc2 else lAcc2, if (preferDarkFirst) 0xFFFFFFFF else 0xFF09090B, if (preferDarkFirst) 0xFFCBD5E1 else 0xFF3F3F46, if (preferDarkFirst) mix(dAcc1, 0xFFFFFFFF, 0.35f) else mix(lAcc1, 0xFF000000, 0.2f), if (preferDarkFirst) mix(dBg2, dAcc1, 0.32f) else mix(0xFFFFFFFF, lAcc1, 0.22f))
    val contrastVariant = AccentScheme("contrast", "High Contrast", "High Contrast", !preferDarkFirst, if (!preferDarkFirst) 0xFF08090B else 0xFFFFFFFF, if (!preferDarkFirst) 0xFF14161A else 0xFFF1F5F9, if (!preferDarkFirst) 0xFF121418 else 0xFFFFFFFF, if (!preferDarkFirst) 0xFF1E2229 else 0xFFE2E8F0, if (!preferDarkFirst) dAcc1 else lAcc1, if (!preferDarkFirst) dAcc2 else lAcc2, if (!preferDarkFirst) 0xFFFFFFFF else 0xFF000000, if (!preferDarkFirst) 0xFFD4D4D8 else 0xFF334155, if (!preferDarkFirst) 0xFF52525B else 0xFF94A3B8, if (!preferDarkFirst) mix(0xFF14161A, dAcc1, 0.30f) else mix(0xFFFFFFFF, lAcc1, 0.20f))
    return if (preferDarkFirst) {
      listOf(darkPrimary, lightPrimary, deepSlate, vividMix, warmLight, coolMist, contrastVariant)
    } else {
      listOf(lightPrimary, darkPrimary, warmLight, coolMist, deepSlate, vividMix, contrastVariant)
    }
  }

  private fun mix(c1: Long, c2: Long, ratio: Float): Long {
    val r1 = ((c1 shr 16) and 0xFF).toInt()
    val g1 = ((c1 shr 8) and 0xFF).toInt()
    val b1 = (c1 and 0xFF).toInt()
    val r2 = ((c2 shr 16) and 0xFF).toInt()
    val g2 = ((c2 shr 8) and 0xFF).toInt()
    val b2 = (c2 and 0xFF).toInt()
    val r = (r1 + (r2 - r1) * ratio).toInt().coerceIn(0, 255)
    val g = (g1 + (g2 - g1) * ratio).toInt().coerceIn(0, 255)
    val b = (b1 + (b2 - b1) * ratio).toInt().coerceIn(0, 255)
    return 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
  }

  val allPresets: List<PaperlyThemeSpec> by lazy {
    val list = mutableListOf<PaperlyThemeSpec>()
    ThemeCollection.entries.forEach { col ->
      val g = grammars.getValue(col)
      val explicit = basePalettes[col]
      if (explicit != null) {
        explicit.forEachIndexed { idx, p ->
          list += buildPreset(col, g, idx, p.idSuffix, p.title, p.variantTag, p.isDark, p.bg1, p.bg2, p.surface, p.surfaceVar, p.accent, p.accent2, p.text1, p.text2, p.border, p.selected, p.grad1, p.grad2, p.warning, p.error)
        }
      } else {
        val syn = synthesizedCollectionPalettes.getValue(col)
        syn.forEachIndexed { idx, s ->
          val title = "${col.displayName} ${s.label}"
          list += buildPreset(col, g, idx, s.suffix, title, s.tag, s.isDark, s.primaryBg, s.secondaryBg, s.surface, s.surfaceVariant, s.accent, s.secondaryAccent, s.textPrimary, s.textSecondary, s.border, s.selected, s.grad1, s.grad2)
        }
      }
    }
    list
  }

  private fun buildPreset(
    col: ThemeCollection,
    g: CollectionGrammar,
    idx: Int,
    suffix: String,
    title: String,
    variantTag: String,
    isDark: Boolean,
    bg1: Long,
    bg2: Long,
    surface: Long,
    surfaceVar: Long,
    accent: Long,
    accent2: Long,
    text1: Long,
    text2: Long,
    border: Long,
    selected: Long,
    grad1: Long,
    grad2: Long,
    warning: Long = if (isDark) 0xFFFBBF24 else 0xFFD97706,
    error: Long = if (isDark) 0xFFF87171 else 0xFFDC2626
  ): PaperlyThemeSpec {
    // Vary density/scale slightly across variants so light/compact/high-contrast variants differ structurally too
    val variantDensity = when (idx) {
      5 -> DensityMode.COMPACT
      6 -> DensityMode.SPACIOUS
      else -> g.density
    }
    val variantRadius = if (idx == 6 && g.cornerDp > 0f) (g.cornerDp - 4f).coerceAtLeast(2f) else g.cornerDp
    return PaperlyThemeSpec(
      id = "${col.name.lowercase()}_$suffix",
      name = title,
      collection = col,
      variantTag = variantTag,
      description = "${col.tagline} • $variantTag",
      isDark = isDark,
      isCustom = false,
      primaryBg = bg1,
      secondaryBg = bg2,
      surface = surface,
      surfaceVariant = surfaceVar,
      accent = accent,
      secondaryAccent = accent2,
      textPrimary = text1,
      textSecondary = text2,
      border = border,
      selectedContainer = selected,
      warning = warning,
      error = error,
      surfaceMaterial = g.material,
      transparencyAlpha = g.alpha,
      blurRadiusDp = g.blurDp,
      gradientStart = grad1,
      gradientEnd = grad2,
      textureOverlay = g.texture,
      headingFont = g.headingFont,
      bodyFont = g.bodyFont,
      monoFont = g.monoFont,
      fontSizeScale = g.fontScale,
      lineHeightMultiplier = g.lineHeight,
      headingWeight = g.headingWeight,
      cornerRadiusDp = variantRadius,
      buttonRadiusDp = g.buttonDp,
      borderWidthDp = if (variantTag == "High Contrast") (g.borderDp + 0.6f).coerceAtLeast(1.2f) else g.borderDp,
      hardShadowOffsetDp = g.hardShadowDp,
      cardElevationDp = g.elevationDp,
      density = variantDensity,
      navStyle = g.navStyle,
      headerStyle = g.headerStyle,
      animationIntensity = g.anim,
      transitionDurationMs = when (g.anim) {
        AnimationIntensity.NONE -> 0
        AnimationIntensity.SUBTLE -> 160
        AnimationIntensity.BALANCED -> 220
        AnimationIntensity.EXPRESSIVE -> 300
      },
      reducedMotion = g.anim == AnimationIntensity.NONE
    )
  }

  val defaultTheme: PaperlyThemeSpec by lazy {
    allPresets.firstOrNull { it.id == "editorial_morning_press" } ?: allPresets.first()
  }

  fun findById(id: String): PaperlyThemeSpec? = allPresets.firstOrNull { it.id == id }

  fun resolveVariantForMode(base: PaperlyThemeSpec, mode: ThemeMode, systemIsDark: Boolean): PaperlyThemeSpec {
    val wantDark = when (mode) {
      ThemeMode.SYSTEM -> return base
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }
    if (base.isDark == wantDark) return base
    val matchingInCollection = allPresets.firstOrNull { it.collection == base.collection && it.isDark == wantDark }
    if (matchingInCollection != null && !base.isCustom) return matchingInCollection
    // For custom themes or fallback, adapt polarity while keeping user's fonts, material, and geometry
    return if (wantDark) {
      base.copy(
        isDark = true,
        primaryBg = 0xFF0E1117,
        secondaryBg = 0xFF161B22,
        surface = 0xFF1C222B,
        surfaceVariant = 0xFF252D38,
        textPrimary = 0xFFF3F4F6,
        textSecondary = 0xFF9CA3AF,
        border = 0xFF323C4A,
        selectedContainer = 0xFF24344D,
        gradientStart = 0xFF0E1117,
        gradientEnd = 0xFF161B22
      )
    } else {
      base.copy(
        isDark = false,
        primaryBg = 0xFFF9F9FB,
        secondaryBg = 0xFFEEF1F5,
        surface = 0xFFFFFFFF,
        surfaceVariant = 0xFFF1F5F9,
        textPrimary = 0xFF111827,
        textSecondary = 0xFF4B5563,
        border = 0xFFD1D5DB,
        selectedContainer = 0xFFE0ECFF,
        gradientStart = 0xFFF9F9FB,
        gradientEnd = 0xFFEEF1F5
      )
    }
  }
}
