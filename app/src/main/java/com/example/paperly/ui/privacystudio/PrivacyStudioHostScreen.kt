package com.example.paperly.ui.privacystudio

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.paperly.core.AppContainer
import com.example.paperly.data.db.RecentPrivacyProjectEntity
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.engines.privacy.PrivacyEditMode

private enum class PrivacyStudioPage {
  DASHBOARD,
  PDF_EDITOR,
  IMAGE_EDITOR,
  BATCH
}

@Composable
fun PrivacyStudioHostScreen(
  preloadedPdfUri: Uri?,
  preloadedImageUri: Uri?,
  requestedMode: PrivacyEditMode?,
  settings: PaperlySettings,
  recentProjects: List<RecentPrivacyProjectEntity>,
  container: AppContainer,
  onClearPreloads: () -> Unit,
  onOpenPdfInReader: (Uri) -> Unit,
  onDeleteProject: (String) -> Unit,
  onClearProjects: () -> Unit,
  onExitToDashboard: () -> Unit
) {
  var currentPage by remember { mutableStateOf(PrivacyStudioPage.DASHBOARD) }
  var editorPdfUri by remember { mutableStateOf<Uri?>(null) }
  var editorImageUri by remember { mutableStateOf<Uri?>(null) }
  var editorMode by remember { mutableStateOf(PrivacyEditMode.PERMANENT_REDACTION) }

  LaunchedEffect(preloadedPdfUri, preloadedImageUri, requestedMode) {
    when {
      preloadedPdfUri != null -> {
        editorPdfUri = preloadedPdfUri
        editorMode = requestedMode ?: PrivacyEditMode.PERMANENT_REDACTION
        currentPage = PrivacyStudioPage.PDF_EDITOR
        onClearPreloads()
      }
      preloadedImageUri != null -> {
        editorImageUri = preloadedImageUri
        editorMode = requestedMode ?: PrivacyEditMode.PERMANENT_REDACTION
        currentPage = PrivacyStudioPage.IMAGE_EDITOR
        onClearPreloads()
      }
    }
  }

  when (currentPage) {
    PrivacyStudioPage.DASHBOARD -> {
      PrivacyStudioDashboardScreen(
        recentProjects = recentProjects,
        documentRepo = container.documentRepository,
        onOpenPdfEditor = { uri, mode ->
          editorPdfUri = uri
          editorMode = mode
          currentPage = PrivacyStudioPage.PDF_EDITOR
        },
        onOpenImageEditor = { uri, mode ->
          editorImageUri = uri
          editorMode = mode
          currentPage = PrivacyStudioPage.IMAGE_EDITOR
        },
        onOpenBatch = { currentPage = PrivacyStudioPage.BATCH },
        onDeleteProject = onDeleteProject,
        onClearProjects = onClearProjects,
        onBack = onExitToDashboard
      )
    }

    PrivacyStudioPage.PDF_EDITOR -> {
      PdfPrivacyEditorScreen(
        initialUri = editorPdfUri,
        initialMode = editorMode,
        settings = settings,
        pdfReaderEngine = container.pdfReaderEngine,
        pdfPrivacyEngine = container.pdfPrivacyEngine,
        documentRepo = container.documentRepository,
        onOpenPdfInReader = onOpenPdfInReader,
        onBack = { currentPage = PrivacyStudioPage.DASHBOARD }
      )
    }

    PrivacyStudioPage.IMAGE_EDITOR -> {
      ImagePrivacyEditorScreen(
        initialUri = editorImageUri,
        initialMode = editorMode,
        settings = settings,
        imagePrivacyEngine = container.imagePrivacyEngine,
        detector = container.localSensitiveDetector,
        documentRepo = container.documentRepository,
        onBack = { currentPage = PrivacyStudioPage.DASHBOARD }
      )
    }

    PrivacyStudioPage.BATCH -> {
      BatchPrivacyScreen(
        settings = settings,
        documentRepo = container.documentRepository,
        pdfPrivacyEngine = container.pdfPrivacyEngine,
        imagePrivacyEngine = container.imagePrivacyEngine,
        detector = container.localSensitiveDetector,
        onInspectPdf = { uri, mode ->
          editorPdfUri = uri
          editorMode = mode
          currentPage = PrivacyStudioPage.PDF_EDITOR
        },
        onInspectImage = { uri, mode ->
          editorImageUri = uri
          editorMode = mode
          currentPage = PrivacyStudioPage.IMAGE_EDITOR
        },
        onBack = { currentPage = PrivacyStudioPage.DASHBOARD }
      )
    }
  }
}
