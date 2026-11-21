package com.example.paperly.ui.pdfreader

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.data.db.BookmarkEntity
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.data.prefs.PdfAppearanceMode
import com.example.paperly.data.repo.DocumentMetadata
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.engines.pdf.PdfDocumentMetadata
import com.example.paperly.engines.pdf.PdfOutlineNode
import com.example.paperly.engines.pdf.PdfReaderEngine
import com.example.paperly.engines.pdf.PdfSearchHit
import com.example.paperly.engines.pdf.PdfSession
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlySurfaceCard
import kotlinx.coroutines.launch

@Composable
fun PdfReaderScreen(
  pdfUri: Uri?,
  settings: PaperlySettings,
  pdfEngine: PdfReaderEngine,
  documentRepo: DocumentRepository,
  onSelectPdfUri: (Uri) -> Unit,
  onOpenInPdfWorkshop: (Uri) -> Unit
) {
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()

  val openPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onSelectPdfUri(uri)
  }

  var session by remember { mutableStateOf<PdfSession?>(null) }
  var fileMeta by remember { mutableStateOf<DocumentMetadata?>(null) }
  var pdfMeta by remember { mutableStateOf<PdfDocumentMetadata?>(null) }
  var outline by remember { mutableStateOf<List<PdfOutlineNode>>(emptyList()) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  var currentPage by remember { mutableIntStateOf(0) }
  var zoom by remember(pdfUri) { mutableFloatStateOf(settings.pdfDefaultZoom) }
  var rotation by remember(pdfUri) { mutableIntStateOf(0) }
  var continuousScroll by remember(pdfUri) { mutableStateOf(settings.pdfContinuousScroll && !settings.pdfHorizontalScroll) }
  var appearance by remember(pdfUri) { mutableStateOf(settings.pdfDefaultAppearance) }
  var fullScreen by remember(pdfUri) { mutableStateOf(settings.pdfFullScreenDefault) }

  var showSearchBar by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var searchHits by remember { mutableStateOf<List<PdfSearchHit>>(emptyList()) }
  var activeHitIdx by remember { mutableIntStateOf(0) }
  var isSearching by remember { mutableStateOf(false) }

  var showThumbnailsDialog by remember { mutableStateOf(false) }
  var showBookmarksDialog by remember { mutableStateOf(false) }
  var showOutlineDialog by remember { mutableStateOf(false) }
  var showInfoDialog by remember { mutableStateOf(false) }
  var showTextSheet by remember { mutableStateOf(false) }
  var extractedPageText by remember { mutableStateOf("") }
  var showJumpDialog by remember { mutableStateOf(false) }
  var jumpInput by remember { mutableStateOf("") }

  val bookmarks: List<BookmarkEntity> by remember(pdfUri) {
    if (pdfUri != null) documentRepo.observeBookmarks(pdfUri.toString())
    else kotlinx.coroutines.flow.flowOf(emptyList())
  }.collectAsState(initial = emptyList())

  val listState = rememberLazyListState()

  DisposableEffect(pdfUri) {
    onDispose {
      session?.close()
      session = null
    }
  }

  LaunchedEffect(pdfUri) {
    session?.close()
    session = null
    pdfMeta = null
    outline = emptyList()
    errorMsg = null
    searchHits = emptyList()

    if (pdfUri == null) return@LaunchedEffect
    isLoading = true
    val inspected = runCatching { documentRepo.inspectUri(pdfUri) }.getOrNull()
    fileMeta = inspected
    val opened = pdfEngine.openSession(pdfUri)
    if (opened.isSuccess) {
      val s = opened.getOrThrow()
      session = s
      val recent = documentRepo.findRecent(pdfUri.toString())
      if (inspected != null) {
        documentRepo.recordOpenedDocument(inspected, s.pageCount)
      }
      val startPage = if (settings.pdfRememberPosition && recent != null) {
        recent.lastPageIndex.coerceIn(0, (s.pageCount - 1).coerceAtLeast(0))
      } else 0
      currentPage = startPage
      if (settings.pdfRememberPosition && recent != null && recent.lastZoom > 0.5f) {
        zoom = recent.lastZoom
      }
      val (meta, toc) = s.readMetadataAndOutline()
      pdfMeta = meta
      outline = toc
      if (startPage > 0) {
        listState.scrollToItem(startPage)
      }
    } else {
      errorMsg = opened.exceptionOrNull()?.localizedMessage ?: "Failed to open PDF."
    }
    isLoading = false
  }

  // Track visible page in continuous mode and persist reading position
  val visibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
  LaunchedEffect(visibleIndex, continuousScroll, session) {
    if (continuousScroll && session != null && visibleIndex != currentPage) {
      currentPage = visibleIndex
    }
  }
  LaunchedEffect(currentPage, zoom, pdfUri, session) {
    if (pdfUri != null && session != null && settings.pdfRememberPosition) {
      documentRepo.updatePdfPosition(pdfUri.toString(), currentPage, zoom)
    }
  }

  val colorFilter = remember(appearance) {
    when (appearance) {
      PdfAppearanceMode.ORIGINAL -> null
      PdfAppearanceMode.INVERTED -> ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
          )
        )
      )
      PdfAppearanceMode.WARM_PAPER -> ColorFilter.colorMatrix(
        ColorMatrix(
          floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 0.96f, 0f, 0f, 0f,
            0f, 0f, 0.86f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
          )
        )
      )
      PdfAppearanceMode.GRAYSCALE -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    }
  }

  PaperlyCanvasBackground(spec = spec) {
    Column(modifier = Modifier.fillMaxSize()) {
      if (!fullScreen) {
        PaperlySurfaceCard(
          spec = spec,
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = fileMeta?.displayName ?: "PDF Reader",
                  style = MaterialTheme.typography.titleSmall,
                  color = spec.textPrimaryColor,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                val total = session?.pageCount ?: 0
                if (total > 0) {
                  val layerLabel = when (pdfMeta?.isTextBased) {
                    true -> "Text PDF"
                    false -> "Scanned / Image PDF"
                    null -> "Loading info..."
                  }
                  Text(
                    text = "Page ${currentPage + 1} / $total • ${(zoom * 100).toInt()}% • $layerLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = spec.textSecondaryColor
                  )
                }
              }
              Row {
                IconButton(
                  onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_open_button")
                ) {
                  Icon(Icons.Default.FolderOpen, contentDescription = "Open PDF", tint = spec.accentColor)
                }
                if (session != null) {
                  IconButton(
                    onClick = { showSearchBar = !showSearchBar },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_search_toggle")
                  ) {
                    Icon(Icons.Default.Search, contentDescription = "Search inside PDF", tint = spec.textPrimaryColor)
                  }
                  val isBookmarked = bookmarks.any { it.pageIndex == currentPage }
                  IconButton(
                    onClick = {
                      val u = pdfUri ?: return@IconButton
                      scope.launch {
                        if (isBookmarked) {
                          documentRepo.removeBookmarkAtPage(u.toString(), currentPage)
                        } else {
                          documentRepo.addBookmark(u.toString(), currentPage, "Page ${currentPage + 1}")
                        }
                      }
                    },
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_bookmark_toggle")
                  ) {
                    Icon(
                      if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                      contentDescription = "Toggle bookmark",
                      tint = if (isBookmarked) spec.accentColor else spec.textPrimaryColor
                    )
                  }
                  IconButton(
                    onClick = { fullScreen = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Full-screen mode", tint = spec.textPrimaryColor)
                  }
                }
              }
            }

            if (session != null) {
              Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                FilterChip(
                  selected = !continuousScroll,
                  onClick = { continuousScroll = !continuousScroll },
                  leadingIcon = { Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(16.dp)) },
                  label = { Text(if (continuousScroll) "Vertical" else "Single Page") }
                )
                FilterChip(
                  selected = false,
                  onClick = { showJumpDialog = true },
                  label = { Text("Go to Page") }
                )
                FilterChip(
                  selected = false,
                  onClick = { showThumbnailsDialog = true },
                  leadingIcon = { Icon(Icons.Default.GridView, null, modifier = Modifier.size(16.dp)) },
                  label = { Text("Pages") }
                )
                FilterChip(
                  selected = false,
                  onClick = {
                    scope.launch {
                      extractedPageText = session?.extractPageText(currentPage).orEmpty()
                      showTextSheet = true
                    }
                  },
                  leadingIcon = { Icon(Icons.Default.SelectAll, null, modifier = Modifier.size(16.dp)) },
                  label = { Text("Select Text") },
                  modifier = Modifier.testTag("pdf_select_text_chip")
                )
                FilterChip(
                  selected = false,
                  onClick = { showBookmarksDialog = true },
                  leadingIcon = { Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(16.dp)) },
                  label = { Text("Bookmarks (${bookmarks.size})") }
                )
                FilterChip(
                  selected = false,
                  onClick = { showOutlineDialog = true },
                  leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(16.dp)) },
                  label = { Text("Outline (${outline.size})") }
                )
                FilterChip(
                  selected = appearance != PdfAppearanceMode.ORIGINAL,
                  onClick = {
                    val vals = PdfAppearanceMode.entries
                    appearance = vals[(vals.indexOf(appearance) + 1) % vals.size]
                  },
                  leadingIcon = { Icon(Icons.Default.DarkMode, null, modifier = Modifier.size(16.dp)) },
                  label = { Text(appearance.label) }
                )
                FilterChip(
                  selected = false,
                  onClick = { rotation = (rotation + 90) % 360 },
                  leadingIcon = { Icon(Icons.Default.RotateRight, null, modifier = Modifier.size(16.dp)) },
                  label = { Text("Rotate") }
                )
                FilterChip(
                  selected = false,
                  onClick = { zoom = (zoom - 0.25f).coerceAtLeast(0.75f) },
                  leadingIcon = { Icon(Icons.Default.ZoomOut, null, modifier = Modifier.size(16.dp)) },
                  label = { Text("Zoom-") }
                )
                FilterChip(
                  selected = false,
                  onClick = { zoom = (zoom + 0.25f).coerceAtMost(3.0f) },
                  leadingIcon = { Icon(Icons.Default.ZoomIn, null, modifier = Modifier.size(16.dp)) },
                  label = { Text("Zoom+") }
                )
                FilterChip(
                  selected = false,
                  onClick = { zoom = 1.0f },
                  label = { Text("Fit") }
                )
                FilterChip(
                  selected = false,
                  onClick = { showInfoDialog = true },
                  leadingIcon = { Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp)) },
                  label = { Text("Info") }
                )
                if (pdfUri != null) {
                  FilterChip(
                    selected = false,
                    onClick = { onOpenInPdfWorkshop(pdfUri) },
                    leadingIcon = { Icon(Icons.Default.Build, null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Edit in Workshop") }
                  )
                }
              }
            }

            if (showSearchBar && session != null) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                OutlinedTextField(
                  value = searchQuery,
                  onValueChange = { searchQuery = it },
                  placeholder = { Text("Search text in PDF...") },
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("pdf_search_input")
                )
                Button(
                  onClick = {
                    val s = session ?: return@Button
                    scope.launch {
                      isSearching = true
                      searchHits = s.search(searchQuery)
                      activeHitIdx = 0
                      searchHits.firstOrNull()?.let { hit ->
                        currentPage = hit.pageIndex
                        if (continuousScroll) listState.scrollToItem(hit.pageIndex)
                      }
                      isSearching = false
                    }
                  },
                  enabled = !isSearching && searchQuery.isNotBlank(),
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Text(if (isSearching) "..." else "Find")
                }
              }
              if (searchHits.isNotEmpty()) {
                val hit = searchHits[activeHitIdx.coerceIn(0, searchHits.lastIndex)]
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "Match ${activeHitIdx + 1}/${searchHits.size} (p.${hit.pageIndex + 1}): \"${hit.snippet}\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = spec.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                  )
                  Row {
                    IconButton(onClick = {
                      activeHitIdx = if (activeHitIdx <= 0) searchHits.lastIndex else activeHitIdx - 1
                      val p = searchHits[activeHitIdx].pageIndex
                      currentPage = p
                      if (continuousScroll) scope.launch { listState.scrollToItem(p) }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous match") }
                    IconButton(onClick = {
                      activeHitIdx = (activeHitIdx + 1) % searchHits.size
                      val p = searchHits[activeHitIdx].pageIndex
                      currentPage = p
                      if (continuousScroll) scope.launch { listState.scrollToItem(p) }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next match") }
                  }
                }
              } else if (searchQuery.isNotBlank() && !isSearching && pdfMeta?.isTextBased == false) {
                Text(
                  text = "This PDF appears to be scanned images with no searchable text layer.",
                  style = MaterialTheme.typography.labelSmall,
                  color = spec.warningColor
                )
              }
            }
          }
        }
      }

      // Main Reading Canvas
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .pointerInput(Unit) {
            detectTransformGestures { _, _, zoomChange, _ ->
              if (zoomChange != 1f) {
                zoom = (zoom * zoomChange).coerceIn(0.75f, 3.0f)
              }
            }
          }
          .pointerInput(Unit) {
            detectTapGestures(
              onDoubleTap = {
                zoom = if (zoom > 1.25f) 1.0f else 1.85f
              }
            )
          }
      ) {
        when {
          isLoading -> {
            Column(
              modifier = Modifier.align(Alignment.Center),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              CircularProgressIndicator(color = spec.accentColor)
              Text("Opening PDF locally...", style = MaterialTheme.typography.bodyMedium, color = spec.textPrimaryColor)
            }
          }
          errorMsg != null -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
              Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Could not open PDF", style = MaterialTheme.typography.titleMedium, color = spec.errorColor)
                Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = spec.textSecondaryColor)
                Button(onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) }) {
                  Text("Choose Another PDF")
                }
              }
            }
          }
          session == null -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(12.dp)
              ) {
                Icon(Icons.Default.Description, null, tint = spec.accentColor, modifier = Modifier.size(48.dp))
                Text("Offline PDF Reader", style = MaterialTheme.typography.headlineSmall, color = spec.textPrimaryColor)
                Text(
                  "Open any PDF from your device with lazy page rendering, text selection, search, bookmarks, and reading-position memory.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = spec.textSecondaryColor
                )
                Button(
                  onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                  shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_empty_open_button")
                ) {
                  Icon(Icons.Default.FolderOpen, null)
                  Spacer(Modifier.width(8.dp))
                  Text("Open PDF Document")
                }
              }
            }
          }
          else -> {
            val activeSession = session!!
            if (continuousScroll) {
              LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                items(activeSession.pageCount) { pageIdx ->
                  PdfPageView(
                    session = activeSession,
                    pageIndex = pageIdx,
                    zoom = zoom,
                    rotation = rotation,
                    colorFilter = colorFilter
                  )
                }
              }
            } else {
              val pagerState = rememberPagerState(initialPage = currentPage.coerceIn(0, (activeSession.pageCount - 1).coerceAtLeast(0))) {
                activeSession.pageCount
              }
              LaunchedEffect(currentPage) {
                if (pagerState.currentPage != currentPage && currentPage in 0 until activeSession.pageCount) {
                  pagerState.scrollToPage(currentPage)
                }
              }
              LaunchedEffect(pagerState.currentPage) {
                currentPage = pagerState.currentPage
              }
              HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { pageIdx ->
                Box(
                  modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                  contentAlignment = Alignment.Center
                ) {
                  PdfPageView(
                    session = activeSession,
                    pageIndex = pageIdx,
                    zoom = zoom,
                    rotation = rotation,
                    colorFilter = colorFilter
                  )
                }
              }
            }

            if (fullScreen) {
              OutlinedButton(
                onClick = { fullScreen = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).minimumInteractiveComponentSize()
              ) {
                Icon(Icons.Default.FullscreenExit, contentDescription = "Exit full-screen")
                Spacer(Modifier.width(6.dp))
                Text("Exit")
              }
            }
          }
        }
      }
    }
  }

  // Modals: Text selection, Thumbnails, Bookmarks, Outline, Page Jump, Info
  if (showTextSheet) {
    AlertDialog(
      onDismissRequest = { showTextSheet = false },
      title = { Text("Page ${currentPage + 1} Text") },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (extractedPageText.isBlank()) {
            Text(
              "No selectable text found on this page. If this is a scanned PDF, it contains raster images rather than an embedded text layer.",
              style = MaterialTheme.typography.bodyMedium,
              color = spec.warningColor
            )
          } else {
            SelectionContainer {
              Text(extractedPageText, style = MaterialTheme.typography.bodyMedium)
            }
          }
        }
      },
      confirmButton = {
        if (extractedPageText.isNotBlank()) {
          Button(onClick = {
            clipboard.setText(AnnotatedString(extractedPageText))
            Toast.makeText(context, "Copied page text", Toast.LENGTH_SHORT).show()
          }) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Copy All")
          }
        }
      },
      dismissButton = { TextButton(onClick = { showTextSheet = false }) { Text("Close") } }
    )
  }

  if (showThumbnailsDialog && session != null) {
    val activeSession = session!!
    AlertDialog(
      onDismissRequest = { showThumbnailsDialog = false },
      title = { Text("Page Thumbnails (${activeSession.pageCount})") },
      text = {
        LazyVerticalGrid(
          columns = GridCells.Fixed(3),
          modifier = Modifier.height(340.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(activeSession.pageCount) { p ->
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (p == currentPage) spec.selectedContainerColor else spec.surfaceVariantColor)
                .clickable {
                  currentPage = p
                  if (continuousScroll) scope.launch { listState.scrollToItem(p) }
                  showThumbnailsDialog = false
                }
                .padding(6.dp)
            ) {
              PdfThumbnailImage(activeSession, p)
              Spacer(Modifier.height(4.dp))
              Text("P.${p + 1}", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { showThumbnailsDialog = false }) { Text("Close") } }
    )
  }

  if (showBookmarksDialog) {
    AlertDialog(
      onDismissRequest = { showBookmarksDialog = false },
      title = { Text("Bookmarks") },
      text = {
        if (bookmarks.isEmpty()) {
          Text("No bookmarks yet. Tap the bookmark icon in the toolbar to save a page.")
        } else {
          LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(bookmarks, key = { it.id }) { bm ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    currentPage = bm.pageIndex
                    if (continuousScroll) scope.launch { listState.scrollToItem(bm.pageIndex) }
                    showBookmarksDialog = false
                  }
                  .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("${bm.label} (Page ${bm.pageIndex + 1})", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { scope.launch { documentRepo.removeBookmark(bm.id) } }) { Text("Remove") }
              }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { showBookmarksDialog = false }) { Text("Close") } }
    )
  }

  if (showOutlineDialog) {
    AlertDialog(
      onDismissRequest = { showOutlineDialog = false },
      title = { Text("Document Outline") },
      text = {
        if (outline.isEmpty()) {
          Text("This PDF does not contain an embedded table of contents outline.")
        } else {
          LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(outline) { node ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    currentPage = node.pageIndex
                    if (continuousScroll) scope.launch { listState.scrollToItem(node.pageIndex) }
                    showOutlineDialog = false
                  }
                  .padding(start = (node.depth * 14).dp, top = 6.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(node.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("p.${node.pageIndex + 1}", style = MaterialTheme.typography.labelSmall, color = spec.accentColor)
              }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { showOutlineDialog = false }) { Text("Close") } }
    )
  }

  if (showJumpDialog && session != null) {
    AlertDialog(
      onDismissRequest = { showJumpDialog = false },
      title = { Text("Go to Page (1 - ${session!!.pageCount})") },
      text = {
        OutlinedTextField(
          value = jumpInput,
          onValueChange = { jumpInput = it.filter { ch -> ch.isDigit() } },
          label = { Text("Page number") },
          singleLine = true
        )
      },
      confirmButton = {
        Button(onClick = {
          val p = (jumpInput.toIntOrNull()?.minus(1) ?: 0).coerceIn(0, session!!.pageCount - 1)
          currentPage = p
          if (continuousScroll) scope.launch { listState.scrollToItem(p) }
          showJumpDialog = false
        }) { Text("Jump") }
      },
      dismissButton = { TextButton(onClick = { showJumpDialog = false }) { Text("Cancel") } }
    )
  }

  if (showInfoDialog) {
    val m = pdfMeta
    val f = fileMeta
    AlertDialog(
      onDismissRequest = { showInfoDialog = false },
      title = { Text("PDF Information") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          InfoRow("Filename", f?.displayName)
          InfoRow("File Size", f?.sizeBytes?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) })
          InfoRow("Pages", m?.pageCount?.toString())
          InfoRow("Text Layer", if (m?.isTextBased == true) "Text-based (searchable)" else "Scanned / Image-only")
          InfoRow("PDF Version", m?.pdfVersion)
          InfoRow("Title", m?.title)
          InfoRow("Author", m?.author)
          InfoRow("Creator", m?.creator)
          InfoRow("Producer", m?.producer)
          InfoRow("Created", m?.creationDate)
        }
      },
      confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } }
    )
  }
}

@Composable
private fun PdfPageView(
  session: PdfSession,
  pageIndex: Int,
  zoom: Float,
  rotation: Int,
  colorFilter: ColorFilter?
) {
  var bitmap by remember(session, pageIndex, zoom, rotation) { mutableStateOf<Bitmap?>(null) }
  val targetW = (900f * zoom).toInt().coerceIn(500, 2100)

  LaunchedEffect(session, pageIndex, targetW, rotation) {
    bitmap = session.renderPage(pageIndex, targetW, rotation)
  }

  val bmp = bitmap
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(6.dp))
      .background(Color.White)
      .graphicsLayer(scaleX = 1f, scaleY = 1f),
    contentAlignment = Alignment.Center
  ) {
    if (bmp != null && !bmp.isRecycled) {
      Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = "PDF page ${pageIndex + 1}",
        contentScale = ContentScale.FillWidth,
        colorFilter = colorFilter,
        modifier = Modifier.fillMaxWidth()
      )
    } else {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(0.707f),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
      }
    }
  }
}

@Composable
private fun PdfThumbnailImage(session: PdfSession, pageIndex: Int) {
  var bmp by remember(session, pageIndex) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(session, pageIndex) {
    bmp = session.renderPage(pageIndex, 220, 0)
  }
  val b = bmp
  if (b != null && !b.isRecycled) {
    Image(
      bitmap = b.asImageBitmap(),
      contentDescription = "Thumbnail page ${pageIndex + 1}",
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxWidth().height(90.dp)
    )
  } else {
    Box(modifier = Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
      CircularProgressIndicator(modifier = Modifier.size(16.dp))
    }
  }
}

@Composable
private fun InfoRow(label: String, value: String?) {
  val spec = LocalPaperlyTheme.current
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = spec.textSecondaryColor)
    Spacer(Modifier.width(12.dp))
    Text(value ?: "Not specified", style = MaterialTheme.typography.bodySmall, color = spec.textPrimaryColor)
  }
}
