package com.example.paperly.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.R

enum class ThemeCollection(val displayName: String, val tagline: String) {
  LIQUID_GLASS("Liquid Glass", "Translucent layers, specular edges, luminous depth"),
  GLASSMORPHISM("Glassmorphism", "Layered frosted panels over vibrant washes"),
  FROSTED_GLASS("Frosted Glass", "Soft diffused frost with crisp hairline rims"),
  NEUMORPHISM("Neumorphism", "Tactile extruded and recessed soft surfaces"),
  MATERIAL_YOU("Material You", "Tonal expressive surfaces and pill geometry"),
  WINDOWS_FLUENT("Windows Fluent", "Acrylic mica depth and crisp focus strokes"),
  MACOS_INSPIRED("macOS Inspired", "Refined desktop clarity and balanced translucency"),
  PURE_AMOLED("Pure AMOLED", "True black OLED surfaces with razor highlights"),
  MIDNIGHT_BLUE("Midnight Blue", "Deep nocturnal indigo for focused reading"),
  CYBERPUNK("Cyberpunk", "High-voltage neon accents on dark urban grids"),
  RETRO_TERMINAL("Retro Terminal", "Monospace phosphor glow and command tabs"),
  SWISS_MINIMALISM("Swiss Minimalism", "International typographic grid and bold contrast"),
  EDITORIAL("Editorial", "Magazine-grade serif hierarchy and hairline rules"),
  VINTAGE_PAPER("Vintage Paper", "Warm archival stock and classic book typography"),
  TYPEWRITER("Typewriter", "Mechanical courier rhythm and ink-on-bond contrast"),
  BRUTALIST("Brutalist", "Hard offset shadows, thick strokes, zero radius"),
  SCANDINAVIAN("Scandinavian", "Airy spacing, muted birch tones, calm geometry"),
  JAPANESE_MINIMALISM("Japanese Minimalism", "Wabi-sabi restraint, washi warmth, quiet ink"),
  Y2K("Y2K", "Glossy chrome, tech-pop bevels, kinetic energy"),
  VAPORWAVE("Vaporwave", "Dreamy sunset pastels and retro digital horizon"),
  SYNTHWAVE("Synthwave", "Electric magenta-cyan dusk over obsidian"),
  PASTEL("Pastel", "Gentle powdery tints with soft friendly cards"),
  MONOCHROME("Monochrome", "Pure grayscale discipline and tonal precision"),
  ORGANIC_NATURE("Organic Nature", "Forest moss, river stone, sun-warmed bark"),
  SUNSET_GRADIENT("Sunset Gradient", "Amber twilight washes and warm terracotta ink"),
  OCEAN("Ocean", "Deep marine currents and seafoam clarity"),
  AURORA("Aurora", "Boreal emerald-violet luminescence on night sky"),
  INDUSTRIAL("Industrial", "Anodized steel, hazard amber, utilitarian grid"),
  METALLIC("Metallic", "Brushed titanium, champagne gold, satin sheen"),
  HOLOGRAPHIC("Holographic", "Iridescent prismatic edges on pearl or obsidian"),
  BLUEPRINT("Blueprint", "Architectural cyan gridlines and technical drafting"),
  RETRO_COMPUTING("Retro Computing", "Classic beige/CRT workstation nostalgia"),
  ACADEMIC("Academic", "University press serif clarity and marginalia ink"),
  MODERN_CORPORATE("Modern Corporate", "Crisp executive slate and structured density"),
  MINIMAL_WHITE("Minimal White", "Gallery-pure daylight paper with quiet chrome"),
  DARK_EDITORIAL("Dark Editorial", "Evening journal serif elegance on charcoal"),
  WARM_IVORY("Warm Ivory", "Eye-friendly cream parchment for long reading"),
  SOFT_LAVENDER("Soft Lavender", "Calm lilac-mist surfaces and plum ink"),
  CLAY("Clay", "Soft puffed matte geometry and earthy ceramics"),
  BOTANICAL("Botanical", "Herbarium sage, pressed-leaf cream, serif notes")
}

enum class SurfaceMaterial(val displayName: String) {
  LIQUID_GLASS("Liquid Glass"),
  FROSTED_GLASS("Frosted Glass"),
  ACRYLIC("Acrylic"),
  SOLID("Solid"),
  FLAT("Flat"),
  RAISED_NEUMORPHIC("Raised Surface"),
  RECESSED_NEUMORPHIC("Recessed Surface"),
  TRANSPARENT("Transparent"),
  GRADIENT("Gradient")
}

enum class TextureOverlay(val displayName: String) {
  NONE("Clean"),
  PAPER_GRAIN("Paper Grain"),
  BLUEPRINT_GRID("Technical Grid"),
  TERMINAL_SCANLINES("Scanlines"),
  DOT_MATRIX("Dot Matrix"),
  EDITORIAL_RULES("Editorial Rules")
}

enum class NavStyle(val displayName: String) {
  FLOATING_PILL("Floating Pill"),
  BOTTOM_BAR("Classic Bar"),
  DOCKED_RAIL("Docked Rail"),
  TERMINAL_TABS("Terminal Tabs"),
  EDITORIAL_UNDERLINE("Editorial Tabs"),
  BRUTALIST_BLOCK("Brutalist Blocks")
}

enum class HeaderStyle(val displayName: String) {
  MINIMAL_CLEAN("Minimal Clean"),
  EDITORIAL_SERIF("Editorial Masthead"),
  TERMINAL_PROMPT("Terminal Prompt"),
  GLASS_CAPSULE("Glass Capsule"),
  BRUTALIST_BANNER("Brutalist Banner")
}

enum class DensityMode(val displayName: String, val multiplier: Float) {
  COMPACT("Compact", 0.85f),
  COMFORTABLE("Comfortable", 1.0f),
  SPACIOUS("Spacious", 1.18f)
}

enum class FontFamilyKey(val displayName: String) {
  PLUS_JAKARTA_SANS("Plus Jakarta Sans"),
  PLAYFAIR_DISPLAY("Playfair Display"),
  JETBRAINS_MONO("JetBrains Mono"),
  SPACE_GROTESK("Space Grotesk"),
  LORA("Lora"),
  COURIER_PRIME("Courier Prime"),
  OUTFIT("Outfit");

  fun toComposeFontFamily(): FontFamily = when (this) {
    PLUS_JAKARTA_SANS -> FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
    PLAYFAIR_DISPLAY -> FontFamily(Font(R.font.playfair_display, FontWeight.Normal))
    JETBRAINS_MONO -> FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))
    SPACE_GROTESK -> FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
    LORA -> FontFamily(Font(R.font.lora, FontWeight.Normal))
    COURIER_PRIME -> FontFamily(Font(R.font.courier_prime, FontWeight.Normal))
    OUTFIT -> FontFamily(Font(R.font.outfit, FontWeight.Normal))
  }
}

enum class AnimationIntensity(val displayName: String) {
  NONE("None"),
  SUBTLE("Subtle"),
  BALANCED("Balanced"),
  EXPRESSIVE("Expressive")
}

enum class ThemeMode(val displayName: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark")
}

data class PaperlyThemeSpec(
  val id: String,
  val name: String,
  val collection: ThemeCollection,
  val variantTag: String,
  val description: String,
  val isDark: Boolean,
  val isCustom: Boolean = false,
  // Colors (ARGB Long)
  val primaryBg: Long,
  val secondaryBg: Long,
  val surface: Long,
  val surfaceVariant: Long,
  val accent: Long,
  val secondaryAccent: Long,
  val textPrimary: Long,
  val textSecondary: Long,
  val border: Long,
  val selectedContainer: Long,
  val warning: Long,
  val error: Long,
  // Material & Effects
  val surfaceMaterial: SurfaceMaterial,
  val transparencyAlpha: Float,
  val blurRadiusDp: Float,
  val gradientStart: Long,
  val gradientEnd: Long,
  val textureOverlay: TextureOverlay,
  // Typography
  val headingFont: FontFamilyKey,
  val bodyFont: FontFamilyKey,
  val monoFont: FontFamilyKey,
  val fontSizeScale: Float = 1.0f,
  val lineHeightMultiplier: Float = 1.35f,
  val headingWeight: Int = 700,
  // Geometry
  val cornerRadiusDp: Float,
  val buttonRadiusDp: Float,
  val borderWidthDp: Float,
  val hardShadowOffsetDp: Float = 0f,
  val cardElevationDp: Float = 2f,
  val density: DensityMode = DensityMode.COMFORTABLE,
  val navStyle: NavStyle = NavStyle.FLOATING_PILL,
  val headerStyle: HeaderStyle = HeaderStyle.MINIMAL_CLEAN,
  // Behavior
  val animationIntensity: AnimationIntensity = AnimationIntensity.BALANCED,
  val transitionDurationMs: Int = 220,
  val reducedMotion: Boolean = false
) {
  val primaryBgColor: Color get() = Color(primaryBg)
  val secondaryBgColor: Color get() = Color(secondaryBg)
  val surfaceColor: Color get() = Color(surface)
  val surfaceVariantColor: Color get() = Color(surfaceVariant)
  val accentColor: Color get() = Color(accent)
  val secondaryAccentColor: Color get() = Color(secondaryAccent)
  val textPrimaryColor: Color get() = Color(textPrimary)
  val textSecondaryColor: Color get() = Color(textSecondary)
  val borderColor: Color get() = Color(border)
  val selectedContainerColor: Color get() = Color(selectedContainer)
  val warningColor: Color get() = Color(warning)
  val errorColor: Color get() = Color(error)
  val gradientStartColor: Color get() = Color(gradientStart)
  val gradientEndColor: Color get() = Color(gradientEnd)
}
