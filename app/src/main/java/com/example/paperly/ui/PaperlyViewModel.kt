package com.example.paperly.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.paperly.core.AppContainer
import com.example.paperly.data.db.DocumentKind
import com.example.paperly.data.db.RecentDocumentEntity
import com.example.paperly.data.db.RecentPrivacyProjectEntity
import com.example.paperly.data.prefs.DashboardSortOrder
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.data.prefs.PdfAppearanceMode
import com.example.paperly.engines.privacy.PrivacyEditMode
import com.example.paperly.theme.PaperlyThemeSpec
import com.example.paperly.theme.ThemeJsonCodec
import com.example.paperly.theme.ThemeMode
import com.example.paperly.theme.ThemePresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaperlyAppUiState(
  val settings: PaperlySettings = PaperlySettings(),
  val customThemes: List<PaperlyThemeSpec> = emptyList(),
  val activeTheme: PaperlyThemeSpec = ThemePresets.defaultTheme,
  val recentDocuments: List<RecentDocumentEntity> = emptyList(),
  val favoriteDocuments: List<RecentDocumentEntity> = emptyList(),
  val recentPrivacyProjects: List<RecentPrivacyProjectEntity> = emptyList(),
  val allThemes: List<PaperlyThemeSpec> = ThemePresets.allPresets
)

class PaperlyViewModel(private val container: AppContainer) : ViewModel() {

  val settingsStore = container.settingsStore
  val documentRepo = container.documentRepository

  private val _activePdfUri = MutableStateFlow<Uri?>(null)
  val activePdfUri: StateFlow<Uri?> = _activePdfUri.asStateFlow()

  private val _activeSheetUri = MutableStateFlow<Uri?>(null)
  val activeSheetUri: StateFlow<Uri?> = _activeSheetUri.asStateFlow()

  private val _workshopPreloadPdfUri = MutableStateFlow<Uri?>(null)
  val workshopPreloadPdfUri: StateFlow<Uri?> = _workshopPreloadPdfUri.asStateFlow()

  private val _workshopPreloadImages = MutableStateFlow<List<Uri>>(emptyList())
  val workshopPreloadImages: StateFlow<List<Uri>> = _workshopPreloadImages.asStateFlow()

  private val _privacyPreloadPdfUri = MutableStateFlow<Uri?>(null)
  val privacyPreloadPdfUri: StateFlow<Uri?> = _privacyPreloadPdfUri.asStateFlow()

  private val _privacyPreloadImageUri = MutableStateFlow<Uri?>(null)
  val privacyPreloadImageUri: StateFlow<Uri?> = _privacyPreloadImageUri.asStateFlow()

  private val _privacyRequestedMode = MutableStateFlow<PrivacyEditMode?>(null)
  val privacyRequestedMode: StateFlow<PrivacyEditMode?> = _privacyRequestedMode.asStateFlow()

  private val _pendingNavigationRoute = MutableStateFlow<String?>(null)
  val pendingNavigationRoute: StateFlow<String?> = _pendingNavigationRoute.asStateFlow()

  fun consumePendingNavigation() {
    _pendingNavigationRoute.value = null
  }

  fun handleIncomingExternalUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      uris.forEach { documentRepo.persistUriPermission(it) }
      if (uris.size > 1) {
        val firstKind = documentRepo.inspectUri(uris.first()).kind
        if (firstKind == DocumentKind.IMAGE) {
          _workshopPreloadImages.value = uris
          _pendingNavigationRoute.value = "image_workshop"
        } else {
          _workshopPreloadPdfUri.value = uris.first()
          _pendingNavigationRoute.value = "pdf_workshop"
        }
      } else {
        val kind = classifyAndRouteUri(uris.first())
        _pendingNavigationRoute.value = when (kind) {
          DocumentKind.PDF -> "pdf_reader"
          DocumentKind.XLSX, DocumentKind.CSV, DocumentKind.TSV, DocumentKind.XLS -> "spreadsheet"
          DocumentKind.IMAGE -> "image_workshop"
        }
      }
      settingsStore.completeOnboarding()
    }
  }

  val uiState: StateFlow<PaperlyAppUiState> = combine(
    settingsStore.settingsFlow,
    documentRepo.customThemes,
    documentRepo.recentDocuments,
    documentRepo.favoriteDocuments,
    documentRepo.recentPrivacyProjects
  ) { settings, customThemes, recents, favs, privacyProjects ->
    val all = customThemes + ThemePresets.allPresets
    val active = all.firstOrNull { it.id == settings.activeThemeId } ?: ThemePresets.defaultTheme
    PaperlyAppUiState(
      settings = settings,
      customThemes = customThemes,
      activeTheme = active,
      recentDocuments = recents,
      favoriteDocuments = favs,
      recentPrivacyProjects = privacyProjects,
      allThemes = all
    )
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = PaperlyAppUiState()
  )

  fun completeOnboarding() {
    viewModelScope.launch { settingsStore.completeOnboarding() }
  }

  fun selectTheme(themeId: String) {
    viewModelScope.launch { settingsStore.setActiveTheme(themeId) }
  }

  fun selectRandomTheme() {
    val all = uiState.value.allThemes
    val currentId = uiState.value.activeTheme.id
    val pick = all.filter { it.id != currentId }.randomOrNull() ?: ThemePresets.defaultTheme
    selectTheme(pick.id)
  }

  fun toggleFavoriteTheme(themeId: String) {
    viewModelScope.launch { settingsStore.toggleFavoriteTheme(themeId) }
  }

  fun saveCustomTheme(spec: PaperlyThemeSpec, activate: Boolean = true) {
    viewModelScope.launch {
      documentRepo.saveCustomTheme(spec)
      if (activate) settingsStore.setActiveTheme(spec.id)
    }
  }

  fun deleteCustomTheme(themeId: String) {
    viewModelScope.launch {
      documentRepo.deleteCustomTheme(themeId)
      if (uiState.value.settings.activeThemeId == themeId) {
        settingsStore.setActiveTheme(ThemePresets.defaultTheme.id)
      }
    }
  }

  fun importThemeJson(rawJson: String): Result<PaperlyThemeSpec> {
    return runCatching {
      val parsed = ThemeJsonCodec.decode(rawJson).copy(
        id = "custom_imp_${System.currentTimeMillis()}",
        isCustom = true
      )
      saveCustomTheme(parsed, activate = true)
      parsed
    }
  }

  fun restoreThemeDefaults() {
    viewModelScope.launch { settingsStore.restoreThemeDefaults() }
  }

  fun updateThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settingsStore.updateThemeMode(mode) }
  }

  fun updateReducedMotion(enabled: Boolean) {
    viewModelScope.launch { settingsStore.updateReducedMotion(enabled) }
  }

  fun updateFontScale(scale: Float) {
    viewModelScope.launch { settingsStore.updateFontScale(scale) }
  }

  fun updateBlurEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsStore.updateBlurEnabled(enabled) }
  }

  fun updateDashboardLayout(isGrid: Boolean? = null, sortOrder: DashboardSortOrder? = null) {
    viewModelScope.launch { settingsStore.updateDashboardLayout(isGrid, sortOrder) }
  }

  fun updatePdfDefaults(
    zoom: Float? = null,
    continuous: Boolean? = null,
    horizontal: Boolean? = null,
    rememberPos: Boolean? = null,
    appearance: PdfAppearanceMode? = null,
    fullScreen: Boolean? = null
  ) {
    viewModelScope.launch { settingsStore.updatePdfDefaults(zoom, continuous, horizontal, rememberPos, appearance, fullScreen) }
  }

  fun updateSpreadsheetDefaults(zoom: Float? = null, grid: Boolean? = null, headers: Boolean? = null) {
    viewModelScope.launch { settingsStore.updateSpreadsheetDefaults(zoom, grid, headers) }
  }

  fun updateImageDefaults(format: String? = null, quality: Int? = null, suffix: String? = null) {
    viewModelScope.launch { settingsStore.updateImageDefaults(format, quality, suffix) }
  }

  fun updatePdfWorkshopDefaults(pageSize: String? = null, compression: String? = null, suffix: String? = null) {
    viewModelScope.launch { settingsStore.updatePdfWorkshopDefaults(pageSize, compression, suffix) }
  }

  fun toggleDocumentFavorite(doc: RecentDocumentEntity) {
    viewModelScope.launch { documentRepo.toggleDocumentFavorite(doc.uriString, doc.isFavorite) }
  }

  fun removeRecentDocument(uriString: String) {
    viewModelScope.launch { documentRepo.removeHistoryEntry(uriString) }
  }

  fun clearRecentHistory() {
    viewModelScope.launch { documentRepo.clearRecentHistory() }
  }

  fun openPdfUri(uri: Uri) {
    documentRepo.persistUriPermission(uri)
    _activePdfUri.value = uri
  }

  fun openSheetUri(uri: Uri) {
    documentRepo.persistUriPermission(uri)
    _activeSheetUri.value = uri
  }

  fun sendPdfToWorkshop(uri: Uri) {
    documentRepo.persistUriPermission(uri)
    _workshopPreloadPdfUri.value = uri
  }

  fun sendImagesToWorkshop(uris: List<Uri>) {
    uris.forEach { documentRepo.persistUriPermission(it) }
    _workshopPreloadImages.value = uris
  }

  fun sendPdfToPrivacyStudio(uri: Uri, mode: PrivacyEditMode = PrivacyEditMode.PERMANENT_REDACTION) {
    documentRepo.persistUriPermission(uri)
    _privacyPreloadPdfUri.value = uri
    _privacyRequestedMode.value = mode
  }

  fun sendImageToPrivacyStudio(uri: Uri, mode: PrivacyEditMode = PrivacyEditMode.PERMANENT_REDACTION) {
    documentRepo.persistUriPermission(uri)
    _privacyPreloadImageUri.value = uri
    _privacyRequestedMode.value = mode
  }

  fun clearPrivacyPreloads() {
    _privacyPreloadPdfUri.value = null
    _privacyPreloadImageUri.value = null
    _privacyRequestedMode.value = null
  }

  fun updatePrivacySuffixes(redactSuffix: String?, blurSuffix: String?, anonymiseSuffix: String?) {
    viewModelScope.launch {
      settingsStore.updatePrivacySuffixes(redactSuffix, blurSuffix, anonymiseSuffix)
    }
  }

  fun deletePrivacyProject(id: String) {
    viewModelScope.launch { documentRepo.deletePrivacyProject(id) }
  }

  fun clearPrivacyProjects() {
    viewModelScope.launch { documentRepo.clearPrivacyProjects() }
  }

  suspend fun classifyAndRouteUri(uri: Uri): DocumentKind {
    documentRepo.persistUriPermission(uri)
    val meta = documentRepo.inspectUri(uri)
    when (meta.kind) {
      DocumentKind.PDF -> _activePdfUri.value = uri
      DocumentKind.XLSX, DocumentKind.CSV, DocumentKind.TSV, DocumentKind.XLS -> _activeSheetUri.value = uri
      DocumentKind.IMAGE -> _workshopPreloadImages.value = listOf(uri)
    }
    return meta.kind
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PaperlyViewModel(container) as T
  }
}
