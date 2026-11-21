package com.example.paperly.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.paperly.theme.ThemeMode
import com.example.paperly.theme.ThemePresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.paperlyDataStore: DataStore<Preferences> by preferencesDataStore(name = "paperly_prefs")

enum class PdfAppearanceMode(val label: String) {
  ORIGINAL("Original"),
  WARM_PAPER("Warm Paper"),
  INVERTED("Inverted Dark"),
  GRAYSCALE("Ink Grayscale")
}

enum class DashboardSortOrder(val label: String) {
  DATE("Date Opened"),
  NAME("Filename"),
  TYPE("File Type")
}

data class PaperlySettings(
  val hasCompletedOnboarding: Boolean = false,
  val activeThemeId: String = ThemePresets.defaultTheme.id,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val favoriteThemeIds: Set<String> = emptySet(),
  val recentThemeIds: List<String> = emptyList(),
  val reducedMotion: Boolean = false,
  val fontScaleMultiplier: Float = 1.0f,
  val blurEnabled: Boolean = true,
  // PDF Reader defaults
  val pdfDefaultZoom: Float = 1.0f,
  val pdfContinuousScroll: Boolean = true,
  val pdfHorizontalScroll: Boolean = false,
  val pdfRememberPosition: Boolean = true,
  val pdfDefaultAppearance: PdfAppearanceMode = PdfAppearanceMode.ORIGINAL,
  val pdfFullScreenDefault: Boolean = false,
  // Spreadsheet defaults
  val sheetDefaultZoom: Float = 1.0f,
  val sheetShowGridLines: Boolean = true,
  val sheetShowHeaders: Boolean = true,
  // Image Workshop defaults
  val imageDefaultFormat: String = "JPEG",
  val imageDefaultQuality: Int = 85,
  val imageSuffix: String = "_paperly",
  // PDF Workshop defaults
  val pdfDefaultPageSize: String = "A4",
  val pdfDefaultCompression: String = "Balanced",
  val pdfSuffix: String = "_edited",
  // Dashboard
  val dashboardGridLayout: Boolean = false,
  val dashboardSortOrder: DashboardSortOrder = DashboardSortOrder.DATE
)

class SettingsStore(private val context: Context) {

  private object Keys {
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val ACTIVE_THEME_ID = stringPreferencesKey("active_theme_id")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val FAVORITE_THEMES = stringPreferencesKey("favorite_themes")
    val RECENT_THEMES = stringPreferencesKey("recent_themes")
    val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    val FONT_SCALE = floatPreferencesKey("font_scale")
    val BLUR_ENABLED = booleanPreferencesKey("blur_enabled")

    val PDF_ZOOM = floatPreferencesKey("pdf_zoom")
    val PDF_CONTINUOUS = booleanPreferencesKey("pdf_continuous")
    val PDF_HORIZONTAL = booleanPreferencesKey("pdf_horizontal")
    val PDF_REMEMBER_POS = booleanPreferencesKey("pdf_remember_pos")
    val PDF_APPEARANCE = stringPreferencesKey("pdf_appearance")
    val PDF_FULLSCREEN = booleanPreferencesKey("pdf_fullscreen")

    val SHEET_ZOOM = floatPreferencesKey("sheet_zoom")
    val SHEET_GRID = booleanPreferencesKey("sheet_grid")
    val SHEET_HEADERS = booleanPreferencesKey("sheet_headers")

    val IMG_FORMAT = stringPreferencesKey("img_format")
    val IMG_QUALITY = intPreferencesKey("img_quality")
    val IMG_SUFFIX = stringPreferencesKey("img_suffix")

    val PDF_PAGE_SIZE = stringPreferencesKey("pdf_page_size")
    val PDF_COMPRESSION = stringPreferencesKey("pdf_compression")
    val PDF_SUFFIX = stringPreferencesKey("pdf_suffix")

    val DASH_GRID = booleanPreferencesKey("dash_grid")
    val DASH_SORT = stringPreferencesKey("dash_sort")
  }

  val settingsFlow: Flow<PaperlySettings> = context.paperlyDataStore.data.map { p ->
    PaperlySettings(
      hasCompletedOnboarding = p[Keys.ONBOARDING_DONE] ?: false,
      activeThemeId = p[Keys.ACTIVE_THEME_ID] ?: ThemePresets.defaultTheme.id,
      themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "") }.getOrDefault(ThemeMode.SYSTEM),
      favoriteThemeIds = (p[Keys.FAVORITE_THEMES] ?: "").split("|").filter { it.isNotBlank() }.toSet(),
      recentThemeIds = (p[Keys.RECENT_THEMES] ?: "").split("|").filter { it.isNotBlank() },
      reducedMotion = p[Keys.REDUCED_MOTION] ?: false,
      fontScaleMultiplier = p[Keys.FONT_SCALE] ?: 1.0f,
      blurEnabled = p[Keys.BLUR_ENABLED] ?: true,
      pdfDefaultZoom = p[Keys.PDF_ZOOM] ?: 1.0f,
      pdfContinuousScroll = p[Keys.PDF_CONTINUOUS] ?: true,
      pdfHorizontalScroll = p[Keys.PDF_HORIZONTAL] ?: false,
      pdfRememberPosition = p[Keys.PDF_REMEMBER_POS] ?: true,
      pdfDefaultAppearance = runCatching { PdfAppearanceMode.valueOf(p[Keys.PDF_APPEARANCE] ?: "") }.getOrDefault(PdfAppearanceMode.ORIGINAL),
      pdfFullScreenDefault = p[Keys.PDF_FULLSCREEN] ?: false,
      sheetDefaultZoom = p[Keys.SHEET_ZOOM] ?: 1.0f,
      sheetShowGridLines = p[Keys.SHEET_GRID] ?: true,
      sheetShowHeaders = p[Keys.SHEET_HEADERS] ?: true,
      imageDefaultFormat = p[Keys.IMG_FORMAT] ?: "JPEG",
      imageDefaultQuality = p[Keys.IMG_QUALITY] ?: 85,
      imageSuffix = p[Keys.IMG_SUFFIX] ?: "_paperly",
      pdfDefaultPageSize = p[Keys.PDF_PAGE_SIZE] ?: "A4",
      pdfDefaultCompression = p[Keys.PDF_COMPRESSION] ?: "Balanced",
      pdfSuffix = p[Keys.PDF_SUFFIX] ?: "_edited",
      dashboardGridLayout = p[Keys.DASH_GRID] ?: false,
      dashboardSortOrder = runCatching { DashboardSortOrder.valueOf(p[Keys.DASH_SORT] ?: "") }.getOrDefault(DashboardSortOrder.DATE)
    )
  }

  suspend fun completeOnboarding() {
    context.paperlyDataStore.edit { it[Keys.ONBOARDING_DONE] = true }
  }

  suspend fun setActiveTheme(themeId: String) {
    context.paperlyDataStore.edit { prefs ->
      prefs[Keys.ACTIVE_THEME_ID] = themeId
      val existing = (prefs[Keys.RECENT_THEMES] ?: "").split("|").filter { it.isNotBlank() && it != themeId }
      prefs[Keys.RECENT_THEMES] = (listOf(themeId) + existing).take(16).joinToString("|")
    }
  }

  suspend fun toggleFavoriteTheme(themeId: String) {
    context.paperlyDataStore.edit { prefs ->
      val current = (prefs[Keys.FAVORITE_THEMES] ?: "").split("|").filter { it.isNotBlank() }.toMutableSet()
      if (!current.add(themeId)) current.remove(themeId)
      prefs[Keys.FAVORITE_THEMES] = current.joinToString("|")
    }
  }

  suspend fun updateThemeMode(mode: ThemeMode) {
    context.paperlyDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun updateReducedMotion(enabled: Boolean) {
    context.paperlyDataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
  }

  suspend fun updateFontScale(scale: Float) {
    context.paperlyDataStore.edit { it[Keys.FONT_SCALE] = scale.coerceIn(0.85f, 1.3f) }
  }

  suspend fun updateBlurEnabled(enabled: Boolean) {
    context.paperlyDataStore.edit { it[Keys.BLUR_ENABLED] = enabled }
  }

  suspend fun updatePdfDefaults(
    zoom: Float? = null,
    continuous: Boolean? = null,
    horizontal: Boolean? = null,
    rememberPos: Boolean? = null,
    appearance: PdfAppearanceMode? = null,
    fullScreen: Boolean? = null
  ) {
    context.paperlyDataStore.edit { prefs ->
      zoom?.let { prefs[Keys.PDF_ZOOM] = it.coerceIn(0.75f, 3.0f) }
      continuous?.let { prefs[Keys.PDF_CONTINUOUS] = it }
      horizontal?.let { prefs[Keys.PDF_HORIZONTAL] = it }
      rememberPos?.let { prefs[Keys.PDF_REMEMBER_POS] = it }
      appearance?.let { prefs[Keys.PDF_APPEARANCE] = it.name }
      fullScreen?.let { prefs[Keys.PDF_FULLSCREEN] = it }
    }
  }

  suspend fun updateSpreadsheetDefaults(zoom: Float? = null, grid: Boolean? = null, headers: Boolean? = null) {
    context.paperlyDataStore.edit { prefs ->
      zoom?.let { prefs[Keys.SHEET_ZOOM] = it.coerceIn(0.7f, 2.5f) }
      grid?.let { prefs[Keys.SHEET_GRID] = it }
      headers?.let { prefs[Keys.SHEET_HEADERS] = it }
    }
  }

  suspend fun updateImageDefaults(format: String? = null, quality: Int? = null, suffix: String? = null) {
    context.paperlyDataStore.edit { prefs ->
      format?.let { prefs[Keys.IMG_FORMAT] = it }
      quality?.let { prefs[Keys.IMG_QUALITY] = it.coerceIn(10, 100) }
      suffix?.let { prefs[Keys.IMG_SUFFIX] = it }
    }
  }

  suspend fun updatePdfWorkshopDefaults(pageSize: String? = null, compression: String? = null, suffix: String? = null) {
    context.paperlyDataStore.edit { prefs ->
      pageSize?.let { prefs[Keys.PDF_PAGE_SIZE] = it }
      compression?.let { prefs[Keys.PDF_COMPRESSION] = it }
      suffix?.let { prefs[Keys.PDF_SUFFIX] = it }
    }
  }

  suspend fun updateDashboardLayout(isGrid: Boolean? = null, sortOrder: DashboardSortOrder? = null) {
    context.paperlyDataStore.edit { prefs ->
      isGrid?.let { prefs[Keys.DASH_GRID] = it }
      sortOrder?.let { prefs[Keys.DASH_SORT] = it.name }
    }
  }

  suspend fun restoreThemeDefaults() {
    context.paperlyDataStore.edit { prefs ->
      prefs[Keys.ACTIVE_THEME_ID] = ThemePresets.defaultTheme.id
      prefs[Keys.THEME_MODE] = ThemeMode.SYSTEM.name
      prefs[Keys.REDUCED_MOTION] = false
      prefs[Keys.FONT_SCALE] = 1.0f
      prefs[Keys.BLUR_ENABLED] = true
    }
  }
}
