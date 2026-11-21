package com.example.paperly.ui.navigation

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.paperly.core.AppContainer
import com.example.paperly.data.db.DocumentKind
import com.example.paperly.theme.PaperlyBottomNavigation
import com.example.paperly.theme.PaperlyNavItem
import com.example.paperly.theme.PaperlyNavigationRail
import com.example.paperly.theme.PaperlyThemeRoot
import com.example.paperly.theme.PaperlyThemeSpec
import com.example.paperly.ui.PaperlyViewModel
import com.example.paperly.ui.home.HomeDashboardScreen
import com.example.paperly.ui.imageworkshop.ImageWorkshopScreen
import com.example.paperly.ui.onboarding.OnboardingScreen
import com.example.paperly.ui.pdfreader.PdfReaderScreen
import com.example.paperly.ui.pdfworkshop.PdfWorkshopScreen
import com.example.paperly.ui.privacystudio.PrivacyStudioHostScreen
import com.example.paperly.ui.settings.SettingsScreen
import com.example.paperly.ui.spreadsheet.SpreadsheetViewerScreen
import com.example.paperly.ui.themes.ThemeGalleryScreen
import com.example.paperly.ui.themes.ThemeStudioScreen
import kotlinx.coroutines.launch

object PaperlyRoutes {
  const val ONBOARDING = "onboarding"
  const val DASHBOARD = "dashboard"
  const val PDF_READER = "pdf_reader"
  const val SPREADSHEET = "spreadsheet"
  const val IMAGE_WORKSHOP = "image_workshop"
  const val PDF_WORKSHOP = "pdf_workshop"
  const val PRIVACY_STUDIO = "privacy_studio"
  const val THEME_GALLERY = "theme_gallery"
  const val THEME_STUDIO = "theme_studio"
  const val SETTINGS = "settings"
}

@Composable
fun PaperlyRootHost(
  viewModel: PaperlyViewModel,
  container: AppContainer
) {
  val appState by viewModel.uiState.collectAsStateWithLifecycle()
  val activePdfUri by viewModel.activePdfUri.collectAsStateWithLifecycle()
  val activeSheetUri by viewModel.activeSheetUri.collectAsStateWithLifecycle()
  val workshopPdfUri by viewModel.workshopPreloadPdfUri.collectAsStateWithLifecycle()
  val workshopImages by viewModel.workshopPreloadImages.collectAsStateWithLifecycle()
  val privacyPdfUri by viewModel.privacyPreloadPdfUri.collectAsStateWithLifecycle()
  val privacyImageUri by viewModel.privacyPreloadImageUri.collectAsStateWithLifecycle()
  val privacyRequestedMode by viewModel.privacyRequestedMode.collectAsStateWithLifecycle()
  val pendingRoute by viewModel.pendingNavigationRoute.collectAsStateWithLifecycle()

  val navController = rememberNavController()
  val scope = rememberCoroutineScope()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  var studioSeedTheme by remember { mutableStateOf<PaperlyThemeSpec?>(null) }

  androidx.compose.runtime.LaunchedEffect(pendingRoute) {
    val target = pendingRoute ?: return@LaunchedEffect
    navController.navigate(target) {
      launchSingleTop = true
    }
    viewModel.consumePendingNavigation()
  }

  val primaryNavItems = remember {
    listOf(
      PaperlyNavItem(PaperlyRoutes.DASHBOARD, "Workspace", Icons.Default.Home, "nav_workspace"),
      PaperlyNavItem(PaperlyRoutes.PDF_READER, "Reader", Icons.Default.Description, "nav_pdf_reader"),
      PaperlyNavItem(PaperlyRoutes.SPREADSHEET, "Sheets", Icons.Default.TableChart, "nav_sheets"),
      PaperlyNavItem(PaperlyRoutes.IMAGE_WORKSHOP, "Images", Icons.Default.Image, "nav_image_workshop"),
      PaperlyNavItem(PaperlyRoutes.PDF_WORKSHOP, "PDF Lab", Icons.Default.Build, "nav_pdf_workshop")
    )
  }

  fun navigateTopLevel(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  fun handleAnyOpenedUri(uri: Uri) {
    scope.launch {
      when (viewModel.classifyAndRouteUri(uri)) {
        DocumentKind.PDF -> navigateTopLevel(PaperlyRoutes.PDF_READER)
        DocumentKind.XLSX, DocumentKind.CSV, DocumentKind.TSV, DocumentKind.XLS -> navigateTopLevel(PaperlyRoutes.SPREADSHEET)
        DocumentKind.IMAGE -> navigateTopLevel(PaperlyRoutes.IMAGE_WORKSHOP)
      }
    }
  }

  val showMainChrome = currentRoute in primaryNavItems.map { it.route }

  PaperlyThemeRoot(
    themeSpec = appState.activeTheme,
    themeMode = appState.settings.themeMode,
    fontScaleOverride = appState.settings.fontScaleMultiplier,
    reducedMotionOverride = appState.settings.reducedMotion
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useRail = maxWidth >= 720.dp

      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
          if (showMainChrome && !useRail) {
            PaperlyBottomNavigation(
              items = primaryNavItems,
              currentRoute = currentRoute,
              onNavigate = ::navigateTopLevel
            )
          }
        }
      ) { innerPadding ->
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          if (showMainChrome && useRail) {
            PaperlyNavigationRail(
              items = primaryNavItems,
              currentRoute = currentRoute,
              onNavigate = ::navigateTopLevel
            )
          }

          Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            val startDest = if (appState.settings.hasCompletedOnboarding) {
              PaperlyRoutes.DASHBOARD
            } else {
              PaperlyRoutes.ONBOARDING
            }

            NavHost(
              navController = navController,
              startDestination = startDest
            ) {
              composable(PaperlyRoutes.ONBOARDING) {
                OnboardingScreen(
                  onExploreThemes = {
                    viewModel.completeOnboarding()
                    navController.navigate(PaperlyRoutes.THEME_GALLERY) {
                      popUpTo(PaperlyRoutes.ONBOARDING) { inclusive = true }
                    }
                  },
                  onCreateTheme = {
                    viewModel.completeOnboarding()
                    studioSeedTheme = appState.activeTheme
                    navController.navigate(PaperlyRoutes.THEME_STUDIO) {
                      popUpTo(PaperlyRoutes.ONBOARDING) { inclusive = true }
                    }
                  },
                  onSkip = {
                    viewModel.completeOnboarding()
                    navController.navigate(PaperlyRoutes.DASHBOARD) {
                      popUpTo(PaperlyRoutes.ONBOARDING) { inclusive = true }
                    }
                  }
                )
              }

              composable(PaperlyRoutes.DASHBOARD) {
                HomeDashboardScreen(
                  recentDocuments = appState.recentDocuments,
                  isGridView = appState.settings.dashboardGridLayout,
                  sortOrder = appState.settings.dashboardSortOrder,
                  onToggleGridView = { viewModel.updateDashboardLayout(isGrid = it) },
                  onChangeSortOrder = { viewModel.updateDashboardLayout(sortOrder = it) },
                  onOpenAnyFileUri = ::handleAnyOpenedUri,
                  onOpenDocumentEntity = { doc ->
                    val uri = Uri.parse(doc.uriString)
                    when {
                      doc.kind == DocumentKind.PDF -> {
                        viewModel.openPdfUri(uri)
                        navigateTopLevel(PaperlyRoutes.PDF_READER)
                      }
                      doc.kind.isSpreadsheet -> {
                        viewModel.openSheetUri(uri)
                        navigateTopLevel(PaperlyRoutes.SPREADSHEET)
                      }
                      else -> {
                        viewModel.sendImagesToWorkshop(listOf(uri))
                        navigateTopLevel(PaperlyRoutes.IMAGE_WORKSHOP)
                      }
                    }
                  },
                  onToggleFavorite = viewModel::toggleDocumentFavorite,
                  onRemoveHistory = viewModel::removeRecentDocument,
                  onNavigatePdfReader = { navigateTopLevel(PaperlyRoutes.PDF_READER) },
                  onNavigateSheets = { navigateTopLevel(PaperlyRoutes.SPREADSHEET) },
                  onNavigateImageWorkshop = { navigateTopLevel(PaperlyRoutes.IMAGE_WORKSHOP) },
                  onNavigatePdfWorkshop = { navigateTopLevel(PaperlyRoutes.PDF_WORKSHOP) },
                  onNavigatePrivacyStudio = { navController.navigate(PaperlyRoutes.PRIVACY_STUDIO) },
                  onNavigateThemes = { navController.navigate(PaperlyRoutes.THEME_GALLERY) },
                  onNavigateSettings = { navController.navigate(PaperlyRoutes.SETTINGS) }
                )
              }

              composable(PaperlyRoutes.PDF_READER) {
                PdfReaderScreen(
                  pdfUri = activePdfUri,
                  settings = appState.settings,
                  pdfEngine = container.pdfReaderEngine,
                  documentRepo = container.documentRepository,
                  onSelectPdfUri = { viewModel.openPdfUri(it) },
                  onOpenInPdfWorkshop = { uri ->
                    viewModel.sendPdfToWorkshop(uri)
                    navigateTopLevel(PaperlyRoutes.PDF_WORKSHOP)
                  },
                  onOpenInPrivacyStudio = { uri ->
                    viewModel.sendPdfToPrivacyStudio(uri)
                    navController.navigate(PaperlyRoutes.PRIVACY_STUDIO)
                  }
                )
              }

              composable(PaperlyRoutes.SPREADSHEET) {
                SpreadsheetViewerScreen(
                  sheetUri = activeSheetUri,
                  settings = appState.settings,
                  spreadsheetEngine = container.spreadsheetEngine,
                  documentRepo = container.documentRepository,
                  onSelectSheetUri = { viewModel.openSheetUri(it) }
                )
              }

              composable(PaperlyRoutes.IMAGE_WORKSHOP) {
                ImageWorkshopScreen(
                  preloadedUris = workshopImages,
                  settings = appState.settings,
                  imageEngine = container.imageWorkshopEngine,
                  documentRepo = container.documentRepository,
                  onOpenPdfUri = { uri ->
                    viewModel.openPdfUri(uri)
                    navigateTopLevel(PaperlyRoutes.PDF_READER)
                  },
                  onOpenInPrivacyStudio = { uri ->
                    if (uri != null) viewModel.sendImageToPrivacyStudio(uri)
                    navController.navigate(PaperlyRoutes.PRIVACY_STUDIO)
                  }
                )
              }

              composable(PaperlyRoutes.PDF_WORKSHOP) {
                PdfWorkshopScreen(
                  preloadedPdfUri = workshopPdfUri,
                  settings = appState.settings,
                  pdfWorkshopEngine = container.pdfWorkshopEngine,
                  pdfReaderEngine = container.pdfReaderEngine,
                  documentRepo = container.documentRepository,
                  onOpenPdfUri = { uri ->
                    viewModel.openPdfUri(uri)
                    navigateTopLevel(PaperlyRoutes.PDF_READER)
                  },
                  onOpenInPrivacyStudio = { uri ->
                    if (uri != null) viewModel.sendPdfToPrivacyStudio(uri)
                    navController.navigate(PaperlyRoutes.PRIVACY_STUDIO)
                  }
                )
              }

              composable(PaperlyRoutes.PRIVACY_STUDIO) {
                BackHandler {
                  if (!navController.popBackStack()) navigateTopLevel(PaperlyRoutes.DASHBOARD)
                }
                PrivacyStudioHostScreen(
                  preloadedPdfUri = privacyPdfUri,
                  preloadedImageUri = privacyImageUri,
                  requestedMode = privacyRequestedMode,
                  settings = appState.settings,
                  recentProjects = appState.recentPrivacyProjects,
                  container = container,
                  onClearPreloads = viewModel::clearPrivacyPreloads,
                  onOpenPdfInReader = { uri ->
                    viewModel.openPdfUri(uri)
                    navigateTopLevel(PaperlyRoutes.PDF_READER)
                  },
                  onDeleteProject = viewModel::deletePrivacyProject,
                  onClearProjects = viewModel::clearPrivacyProjects,
                  onExitToDashboard = {
                    if (!navController.popBackStack()) navigateTopLevel(PaperlyRoutes.DASHBOARD)
                  }
                )
              }

              composable(PaperlyRoutes.THEME_GALLERY) {
                BackHandler {
                  if (!navController.popBackStack()) navigateTopLevel(PaperlyRoutes.DASHBOARD)
                }
                ThemeGalleryScreen(
                  allThemes = appState.allThemes,
                  activeTheme = appState.activeTheme,
                  favoriteIds = appState.settings.favoriteThemeIds,
                  recentIds = appState.settings.recentThemeIds,
                  onApplyTheme = { viewModel.selectTheme(it.id) },
                  onToggleFavorite = viewModel::toggleFavoriteTheme,
                  onRandomTheme = viewModel::selectRandomTheme,
                  onOpenStudioWith = { seed ->
                    studioSeedTheme = seed
                    navController.navigate(PaperlyRoutes.THEME_STUDIO)
                  },
                  onImportThemeJson = viewModel::importThemeJson,
                  onRestoreDefaults = viewModel::restoreThemeDefaults,
                  onBack = {
                    if (!navController.popBackStack()) navigateTopLevel(PaperlyRoutes.DASHBOARD)
                  }
                )
              }

              composable(PaperlyRoutes.THEME_STUDIO) {
                BackHandler {
                  if (!navController.popBackStack()) navigateTopLevel(PaperlyRoutes.DASHBOARD)
                }
                ThemeStudioScreen(
                  initialTheme = studioSeedTheme ?: appState.activeTheme,
                  onSaveTheme = { saved -> viewModel.saveCustomTheme(saved, activate = true) },
                  onBack = {
                    if (!navController.popBackStack()) navigateTopLevel(PaperlyRoutes.DASHBOARD)
                  }
                )
              }

              composable(PaperlyRoutes.SETTINGS) {
                BackHandler {
                  if (!navController.popBackStack()) navigateTopLevel(PaperlyRoutes.DASHBOARD)
                }
                SettingsScreen(
                  settings = appState.settings,
                  activeTheme = appState.activeTheme,
                  documentRepo = container.documentRepository,
                  onOpenThemeGallery = { navController.navigate(PaperlyRoutes.THEME_GALLERY) },
                  onOpenThemeStudio = {
                    studioSeedTheme = appState.activeTheme
                    navController.navigate(PaperlyRoutes.THEME_STUDIO)
                  },
                  onThemeModeChange = viewModel::updateThemeMode,
                  onReducedMotionChange = viewModel::updateReducedMotion,
                  onFontScaleChange = viewModel::updateFontScale,
                  onBlurEnabledChange = viewModel::updateBlurEnabled,
                  onUpdatePdfDefaults = viewModel::updatePdfDefaults,
                  onUpdateSheetDefaults = viewModel::updateSpreadsheetDefaults,
                  onUpdateImageDefaults = viewModel::updateImageDefaults,
                  onUpdatePdfWorkshopDefaults = viewModel::updatePdfWorkshopDefaults,
                  onUpdatePrivacySuffixes = viewModel::updatePrivacySuffixes,
                  onClearHistory = viewModel::clearRecentHistory,
                  onBack = {
                    if (!navController.popBackStack()) navigateTopLevel(PaperlyRoutes.DASHBOARD)
                  }
                )
              }
            }
          }
        }
      }
    }
  }
}
