package com.example.paperly.ui.privacystudio

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.data.db.RecentPrivacyProjectEntity
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.engines.pdf.PdfReaderEngine
import com.example.paperly.engines.pdf.PdfSession
import com.example.paperly.engines.privacy.BlurStyle
import com.example.paperly.engines.privacy.NormPoint
import com.example.paperly.engines.privacy.NormRect
import com.example.paperly.engines.privacy.PdfPageWordBox
import com.example.paperly.engines.privacy.PdfPrivacyConfig
import com.example.paperly.engines.privacy.PdfPrivacyEngine
import com.example.paperly.engines.privacy.PdfPrivacyExportResult
import com.example.paperly.engines.privacy.PdfPrivacyInspection
import com.example.paperly.engines.privacy.PdfRedactionStrategy
import com.example.paperly.engines.privacy.PdfSearchOccurrence
import com.example.paperly.engines.privacy.PrivacyEditMode
import com.example.paperly.engines.privacy.PrivacyOperation
import com.example.paperly.engines.privacy.PrivacySelectionShape
import com.example.paperly.engines.privacy.SensitiveSuggestion
import com.example.paperly.engines.privacy.TextReplacementConfig
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlyHeaderBanner
import com.example.paperly.theme.PaperlySurfaceCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class PdfToolTab(val label: String) {
  CANVAS("Draw / Tap Text"),
  SEARCH("Search & Redact"),
  DETECT("Auto-Detect")
}

@Composable
fun PdfPrivacyEditorScreen(
  initialUri: Uri?,
  initialMode: PrivacyEditMode,
  settings: PaperlySettings,
  pdfReaderEngine: PdfReaderEngine,
  pdfPrivacyEngine: PdfPrivacyEngine,
  documentRepo: DocumentRepository,
  onOpenPdfInReader: (Uri) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var selectedUri by remember { mutableStateOf(initialUri) }
  var pdfName by remember { mutableStateOf("No PDF selected") }
  var session by remember { mutableStateOf<PdfSession?>(null) }
  var inspection by remember { mutableStateOf<PdfPrivacyInspection?>(null) }
  var currentPage by remember { mutableIntStateOf(0) }
  var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var pageWords by remember { mutableStateOf<List<PdfPageWordBox>>(emptyList()) }
  var zoom by remember { mutableFloatStateOf(1f) }

  var activeTab by remember { mutableStateOf(PdfToolTab.CANVAS) }
  var activeMode by remember { mutableStateOf(initialMode) }
  var activeShape by remember { mutableStateOf(PrivacySelectionShape.RECTANGLE) }
  var tapWordMode by remember { mutableStateOf(false) }
  var strategy by remember { mutableStateOf(PdfRedactionStrategy.CONTENT_STREAM_REWRITE) }

  var blurStyle by remember { mutableStateOf(BlurStyle.GAUSSIAN_BLUR) }
  var blurRadius by remember { mutableFloatStateOf(18f) }
  var mosaicSize by remember { mutableFloatStateOf(24f) }
  var redactionColor by remember { mutableIntStateOf(AndroidColor.BLACK) }
  var redactionLabel by remember { mutableStateOf("") }
  var replacementConfig by remember { mutableStateOf(TextReplacementConfig()) }

  val operations = remember { mutableStateListOf<PrivacyOperation>() }
  val redoStack = remember { mutableStateListOf<PrivacyOperation>() }
  val draftPoints = remember { mutableStateListOf<NormPoint>() }

  // Search-based redaction state
  var searchQuery by remember { mutableStateOf("") }
  var searchMatches by remember { mutableStateOf<List<PdfSearchOccurrence>>(emptyList()) }
  var isSearching by remember { mutableStateOf(false) }

  // Auto-detect sensitive info state
  var customKeywords by remember { mutableStateOf("") }
  var suggestions by remember { mutableStateOf<List<SensitiveSuggestion>>(emptyList()) }

  // Sanitisation toggles
  var stripMeta by remember { mutableStateOf(true) }
  var stripAnnots by remember { mutableStateOf(true) }
  var stripForms by remember { mutableStateOf(true) }
  var stripAttachments by remember { mutableStateOf(true) }
  var stripLayers by remember { mutableStateOf(true) }

  var customFileName by remember { mutableStateOf("") }
  var outputSuffix by remember(activeMode, settings) {
    mutableStateOf(
      when (activeMode) {
        PrivacyEditMode.PERMANENT_REDACTION -> settings.privacyRedactSuffix
        PrivacyEditMode.BLUR_PIXELATE -> settings.privacyBlurSuffix
        PrivacyEditMode.REPLACE_ANONYMISE -> settings.privacyAnonymiseSuffix
      }
    )
  }

  var isBusy by remember { mutableStateOf(false) }
  var exportResult by remember { mutableStateOf<PdfPrivacyExportResult?>(null) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  var fileToSaveAs by remember { mutableStateOf<File?>(null) }
  val saveCopyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { dest ->
    val f = fileToSaveAs ?: return@rememberLauncherForActivityResult
    if (dest != null) {
      scope.launch(Dispatchers.IO) {
        val ok = runCatching {
          context.contentResolver.openOutputStream(dest)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        }.isSuccess
        withContext(Dispatchers.Main) {
          Toast.makeText(context, if (ok) "Saved sanitised PDF" else "Save failed", Toast.LENGTH_SHORT).show()
        }
      }
    }
    fileToSaveAs = null
  }

  val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      selectedUri = uri
      operations.clear()
      redoStack.clear()
      searchMatches = emptyList()
      suggestions = emptyList()
      exportResult = null
    }
  }

  DisposableEffect(selectedUri) {
    onDispose {
      session?.close()
      session = null
    }
  }

  LaunchedEffect(selectedUri) {
    session?.close()
    session = null
    pageBitmap = null
    inspection = null
    val u = selectedUri ?: return@LaunchedEffect
    isBusy = true
    errorMsg = null
    pdfName = runCatching { documentRepo.inspectUri(u).displayName }.getOrDefault("Selected PDF")
    session = pdfReaderEngine.openSession(u).getOrNull()
    val insp = pdfPrivacyEngine.inspectPdfForPrivacy(u)
    if (insp.isSuccess) {
      val info = insp.getOrThrow()
      inspection = info
      suggestions = info.suggestions
      if (!info.hasTextLayer) {
        strategy = PdfRedactionStrategy.RASTERIZE_AFFECTED_PAGES
      }
    } else {
      errorMsg = insp.exceptionOrNull()?.localizedMessage
    }
    currentPage = 0
    isBusy = false
  }

  LaunchedEffect(selectedUri, currentPage, session) {
    val u = selectedUri ?: return@LaunchedEffect
    pageBitmap = session?.renderPage(currentPage, 1100, 0)
    pageWords = pdfPrivacyEngine.extractPageWords(u, currentPage).getOrDefault(emptyList())
  }

  PaperlyCanvasBackground(spec = spec) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(Modifier.height(10.dp))
      PaperlyHeaderBanner(
        title = "PDF Privacy Editor",
        subtitle = "$pdfName • ${operations.size} uncommitted edit(s)",
        trailing = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_priv_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = spec.textPrimaryColor)
          }
        }
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Spacer(Modifier.height(4.dp))

        // Source & Inspection Summary
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
              Column(Modifier.weight(1f)) {
                Text(pdfName, style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inspection?.let { insp ->
                  Text(
                    "${insp.pageCount} pages • ${if (insp.hasTextLayer) "Text PDF" else "Scanned PDF"} • ${insp.annotationCount} annots • ${insp.formFieldCount} form fields • ${insp.attachmentCount} attachments",
                    style = MaterialTheme.typography.labelSmall,
                    color = spec.textSecondaryColor
                  )
                }
              }
              Button(
                onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_priv_pick_button")
              ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("PDF")
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = { if (operations.isNotEmpty()) redoStack.add(operations.removeAt(operations.lastIndex)) },
                enabled = operations.isNotEmpty(),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_priv_undo")
              ) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }

              IconButton(
                onClick = { if (redoStack.isNotEmpty()) operations.add(redoStack.removeAt(redoStack.lastIndex)) },
                enabled = redoStack.isNotEmpty(),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_priv_redo")
              ) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }

              IconButton(
                onClick = { operations.clear(); redoStack.clear() },
                enabled = operations.isNotEmpty(),
                modifier = Modifier.minimumInteractiveComponentSize()
              ) { Icon(Icons.Default.DeleteOutline, "Clear edits") }

              Spacer(Modifier.weight(1f))

              val totalPages = inspection?.pageCount ?: session?.pageCount ?: 0
              if (totalPages > 0) {
                IconButton(onClick = { if (currentPage > 0) currentPage-- }, enabled = currentPage > 0) {
                  Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous page")
                }
                Text("P.${currentPage + 1}/$totalPages", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { if (currentPage < totalPages - 1) currentPage++ }, enabled = currentPage < totalPages - 1) {
                  Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next page")
                }
              }
            }
          }
        }

        // Mode & Tool Tabs
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrivacyModeSelectorBar(selectedMode = activeMode, onSelectMode = { activeMode = it }, spec = spec)

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              PdfToolTab.entries.forEach { tab ->
                FilterChip(
                  selected = activeTab == tab,
                  onClick = { activeTab = tab },
                  label = { Text(tab.label) },
                  modifier = Modifier.testTag("pdf_priv_tab_${tab.name.lowercase()}")
                )
              }
            }

            when (activeTab) {
              PdfToolTab.CANVAS -> {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  FilterChip(selected = !tapWordMode && activeShape == PrivacySelectionShape.RECTANGLE, onClick = { tapWordMode = false; activeShape = PrivacySelectionShape.RECTANGLE }, label = { Text("Rectangle") })
                  FilterChip(selected = !tapWordMode && activeShape == PrivacySelectionShape.FREEHAND, onClick = { tapWordMode = false; activeShape = PrivacySelectionShape.FREEHAND }, label = { Text("Freehand") })
                  FilterChip(selected = tapWordMode, onClick = { tapWordMode = true }, label = { Text("Tap Text Word (${pageWords.size})") }, modifier = Modifier.testTag("pdf_priv_tap_words"))
                }
              }
              PdfToolTab.SEARCH -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                  OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Word or phrase to find") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("pdf_priv_search_input")
                  )
                  Button(
                    onClick = {
                      val u = selectedUri ?: return@Button
                      scope.launch {
                        isSearching = true
                        searchMatches = pdfPrivacyEngine.searchOccurrences(u, searchQuery).getOrDefault(emptyList())
                        isSearching = false
                      }
                    },
                    enabled = selectedUri != null && searchQuery.isNotBlank() && !isSearching,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_priv_search_run")
                  ) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                  }
                }
                if (searchMatches.isNotEmpty()) {
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${searchMatches.size} occurrence(s) found", style = MaterialTheme.typography.labelMedium, color = spec.accentColor)
                    Row {
                      TextButton(onClick = {
                        searchMatches = searchMatches.map { it.copy(selected = true) }
                      }) { Text("Select All") }
                      TextButton(onClick = {
                        searchMatches = searchMatches.map { it.copy(selected = false) }
                      }) { Text("None") }
                    }
                  }
                  searchMatches.take(25).forEachIndexed { idx, occ ->
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(spec.surfaceVariantColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Checkbox(
                        checked = occ.selected,
                        onCheckedChange = { checked ->
                          searchMatches = searchMatches.toMutableList().also { it[idx] = occ.copy(selected = checked) }
                        }
                      )
                      Column(Modifier.weight(1f)) {
                        Text("Page ${occ.pageIndex + 1}: \"${occ.matchedText}\"", style = MaterialTheme.typography.labelMedium, color = spec.textPrimaryColor)
                        Text(occ.snippet, style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      }
                      TextButton(onClick = { currentPage = occ.pageIndex }) { Text("View") }
                    }
                  }
                  val chosenCount = searchMatches.count { it.selected }
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                      onClick = {
                        val chosen = searchMatches.filter { it.selected }
                        chosen.forEach { occ ->
                          occ.rects.forEach { r ->
                            operations.add(
                              PrivacyOperation(
                                id = "search_${System.nanoTime()}",
                                pageIndex = occ.pageIndex,
                                mode = activeMode,
                                shape = PrivacySelectionShape.RECTANGLE,
                                rect = r,
                                fillColorArgb = redactionColor,
                                optionalLabel = redactionLabel,
                                replacementConfig = replacementConfig,
                                sourceTextToVerify = occ.matchedText
                              )
                            )
                          }
                        }
                        redoStack.clear()
                        Toast.makeText(context, "Queued $chosenCount match(es) for review", Toast.LENGTH_SHORT).show()
                      },
                      enabled = chosenCount > 0,
                      modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("pdf_priv_apply_search")
                    ) { Text("Queue $chosenCount Match(es)") }
                  }
                }
              }
              PdfToolTab.DETECT -> {
                Text(
                  "Local pattern scan checks emails, phones, accounts, IDs, addresses and your custom keywords. Usernames, passwords and contextual secrets cannot always be auto-detected; always inspect manually too.",
                  style = MaterialTheme.typography.labelSmall,
                  color = spec.textSecondaryColor
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                  OutlinedTextField(
                    value = customKeywords,
                    onValueChange = { customKeywords = it },
                    label = { Text("Custom keywords (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                  )
                  OutlinedButton(
                    onClick = {
                      val u = selectedUri ?: return@OutlinedButton
                      scope.launch {
                        val kws = customKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val res = pdfPrivacyEngine.inspectPdfForPrivacy(u, kws)
                        res.getOrNull()?.let { suggestions = it.suggestions }
                      }
                    },
                    enabled = selectedUri != null,
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = "Scan")
                  }
                }
                if (suggestions.isEmpty()) {
                  Text("No pattern matches detected on text layer.", style = MaterialTheme.typography.bodySmall, color = spec.textSecondaryColor)
                } else {
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${suggestions.size} suggestion(s) — confirm before applying", style = MaterialTheme.typography.labelMedium, color = spec.accentColor)
                    TextButton(onClick = { suggestions = suggestions.map { it.copy(selected = true) } }) { Text("Select All") }
                  }
                  suggestions.take(25).forEachIndexed { idx, sug ->
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(spec.surfaceVariantColor)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Checkbox(
                        checked = sug.selected,
                        onCheckedChange = { checked ->
                          suggestions = suggestions.toMutableList().also { it[idx] = sug.copy(selected = checked) }
                        }
                      )
                      Column(Modifier.weight(1f)) {
                        Text("Page ${sug.pageIndex + 1} • ${sug.displayHint}", style = MaterialTheme.typography.labelMedium, color = spec.textPrimaryColor)
                        Text("Suggested: ${sug.recommendedPlaceholder.placeholder}", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                      }
                      TextButton(onClick = { currentPage = sug.pageIndex }) { Text("View") }
                    }
                  }
                  val chosenSugs = suggestions.filter { it.selected }
                  Button(
                    onClick = {
                      chosenSugs.forEach { sug ->
                        val rep = if (activeMode == PrivacyEditMode.REPLACE_ANONYMISE) {
                          replacementConfig.copy(text = sug.recommendedPlaceholder.placeholder)
                        } else replacementConfig
                        operations.add(
                          PrivacyOperation(
                            id = "sug_op_${System.nanoTime()}",
                            pageIndex = sug.pageIndex,
                            mode = activeMode,
                            shape = PrivacySelectionShape.RECTANGLE,
                            rect = sug.rect,
                            fillColorArgb = redactionColor,
                            optionalLabel = redactionLabel,
                            replacementConfig = rep,
                            sourceTextToVerify = sug.matchedText
                          )
                        )
                      }
                      redoStack.clear()
                    },
                    enabled = chosenSugs.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pdf_priv_apply_suggestions")
                  ) { Text("Queue ${chosenSugs.size} Approved Suggestion(s)") }
                }
              }
            }
          }
        }

        // PDF Interactive Page Canvas
        val bmp = pageBitmap
        if (bmp != null && !bmp.isRecycled) {
          val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
          PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                  if (tapWordMode) "Tap any highlighted word box to queue edit" else "Drag on page to select redaction area",
                  style = MaterialTheme.typography.labelSmall,
                  color = spec.accentColor
                )
                Text("Zoom ${String.format("%.1fx", zoom)}", style = MaterialTheme.typography.labelSmall)
              }
              Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..2.5f)
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .border(1.dp, spec.borderColor, RoundedCornerShape(8.dp))
                  .graphicsLayer(scaleX = zoom, scaleY = zoom)
              ) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(aspect)) {
                  Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "PDF privacy page ${currentPage + 1}",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                  )
                  Canvas(
                    modifier = Modifier
                      .fillMaxSize()
                      .pointerInput(tapWordMode, pageWords, currentPage, activeMode, activeShape, blurStyle, blurRadius, mosaicSize, redactionColor, redactionLabel, replacementConfig) {
                        if (tapWordMode) {
                          detectTapGestures { pos ->
                            val nx = (pos.x / size.width).coerceIn(0f, 1f)
                            val ny = (pos.y / size.height).coerceIn(0f, 1f)
                            val hit = pageWords.firstOrNull { w ->
                              nx in w.rect.left..w.rect.right && ny in w.rect.top..w.rect.bottom
                            }
                            if (hit != null) {
                              operations.add(
                                PrivacyOperation(
                                  id = "word_${System.nanoTime()}",
                                  pageIndex = currentPage,
                                  mode = activeMode,
                                  shape = PrivacySelectionShape.RECTANGLE,
                                  rect = hit.rect,
                                  blurStyle = blurStyle,
                                  blurRadius = blurRadius.toInt(),
                                  mosaicBlockSize = mosaicSize.toInt(),
                                  fillColorArgb = redactionColor,
                                  optionalLabel = redactionLabel,
                                  replacementConfig = replacementConfig,
                                  sourceTextToVerify = hit.text
                                )
                              )
                              redoStack.clear()
                              exportResult = null
                            }
                          }
                        } else {
                          detectDragGestures(
                            onDragStart = { start ->
                              draftPoints.clear()
                              draftPoints.add(NormPoint((start.x / size.width).coerceIn(0f, 1f), (start.y / size.height).coerceIn(0f, 1f)))
                            },
                            onDrag = { change, _ ->
                              change.consume()
                              draftPoints.add(NormPoint((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f)))
                            },
                            onDragEnd = {
                              if (draftPoints.size >= 2) {
                                val pts = draftPoints.toList()
                                val rect = if (activeShape == PrivacySelectionShape.RECTANGLE) {
                                  val a = pts.first(); val b = pts.last()
                                  NormRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)).normalized()
                                } else {
                                  NormRect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y }).normalized()
                                }
                                if (rect.width > 0.008f && rect.height > 0.008f) {
                                  operations.add(
                                    PrivacyOperation(
                                      id = "pdf_op_${System.nanoTime()}",
                                      pageIndex = currentPage,
                                      mode = activeMode,
                                      shape = activeShape,
                                      rect = rect,
                                      points = pts,
                                      blurStyle = blurStyle,
                                      blurRadius = blurRadius.toInt(),
                                      mosaicBlockSize = mosaicSize.toInt(),
                                      fillColorArgb = redactionColor,
                                      optionalLabel = redactionLabel,
                                      replacementConfig = replacementConfig
                                    )
                                  )
                                  redoStack.clear()
                                  exportResult = null
                                }
                              }
                              draftPoints.clear()
                            }
                          )
                        }
                      }
                  ) {
                    if (tapWordMode) {
                      pageWords.forEach { w ->
                        drawRect(
                          color = Color(0xFF2563EB).copy(alpha = 0.16f),
                          topLeft = Offset(w.rect.left * size.width, w.rect.top * size.height),
                          size = Size(w.rect.width * size.width, w.rect.height * size.height)
                        )
                      }
                    }
                    operations.filter { it.pageIndex == currentPage }.forEach { op ->
                      val c = when (op.mode) {
                        PrivacyEditMode.PERMANENT_REDACTION -> Color(op.fillColorArgb).copy(alpha = 0.85f)
                        PrivacyEditMode.BLUR_PIXELATE -> Color(0xFF0EA5E9).copy(alpha = 0.45f)
                        PrivacyEditMode.REPLACE_ANONYMISE -> Color(op.replacementConfig.backgroundColorArgb).copy(alpha = 0.88f)
                      }
                      if (op.shape == PrivacySelectionShape.FREEHAND && op.points.size >= 2) {
                        val p = Path().apply {
                          moveTo(op.points.first().x * size.width, op.points.first().y * size.height)
                          for (i in 1 until op.points.size) lineTo(op.points[i].x * size.width, op.points[i].y * size.height)
                          close()
                        }
                        drawPath(p, c)
                      } else {
                        val nr = op.rect.normalized()
                        drawRect(c, Offset(nr.left * size.width, nr.top * size.height), Size(nr.width * size.width, nr.height * size.height))
                        drawRect(Color.White.copy(alpha = 0.7f), Offset(nr.left * size.width, nr.top * size.height), Size(nr.width * size.width, nr.height * size.height), style = Stroke(2f))
                      }
                    }
                    if (draftPoints.size >= 2) {
                      val a = draftPoints.first(); val b = draftPoints.last()
                      drawRect(
                        color = Color(redactionColor).copy(alpha = 0.55f),
                        topLeft = Offset(minOf(a.x, b.x) * size.width, minOf(a.y, b.y) * size.height),
                        size = Size(kotlin.math.abs(b.x - a.x) * size.width, kotlin.math.abs(b.y - a.y) * size.height)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        // Mode-Specific Styling & Strategy
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (activeMode) {
              PrivacyEditMode.PERMANENT_REDACTION -> {
                Text("Redaction Appearance", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  privacyRedactionSwatches.forEach { c -> SwatchDot(c, redactionColor == c, spec) { redactionColor = c } }
                }
                OutlinedTextField(
                  value = redactionLabel,
                  onValueChange = { redactionLabel = it },
                  label = { Text("Optional Redaction Label") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
              }
              PrivacyEditMode.BLUR_PIXELATE -> {
                Text("Visual Concealment (Rasterises Affected Pages)", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  BlurStyle.entries.forEach { style ->
                    FilterChip(selected = blurStyle == style, onClick = { blurStyle = style }, label = { Text(style.label) })
                  }
                }
                Slider(
                  value = if (blurStyle == BlurStyle.GAUSSIAN_BLUR) blurRadius else mosaicSize,
                  onValueChange = { if (blurStyle == BlurStyle.GAUSSIAN_BLUR) blurRadius = it else mosaicSize = it },
                  valueRange = 8f..48f
                )
              }
              PrivacyEditMode.REPLACE_ANONYMISE -> {
                ReplacementStylePanel(config = replacementConfig, onChange = { replacementConfig = it }, spec = spec)
              }
            }

            Text("PDF Removal Strategy", style = MaterialTheme.typography.labelLarge, color = spec.textPrimaryColor)
            PdfRedactionStrategy.entries.forEach { s ->
              FilterChip(
                selected = strategy == s,
                onClick = { strategy = s },
                label = { Text(s.label) },
                modifier = Modifier.fillMaxWidth()
              )
            }
            Text(strategy.description, style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
          }
        }

        // Document-Wide Sanitisation & Export
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Deep PDF Sanitisation (Full Rewrite)", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
            SanitisationSwitch("Strip Document & XMP Metadata", stripMeta) { stripMeta = it }
            SanitisationSwitch("Strip All Annotations & Comments", stripAnnots) { stripAnnots = it }
            SanitisationSwitch("Strip Form Fields (AcroForm)", stripForms) { stripForms = it }
            SanitisationSwitch("Strip Embedded Attachments", stripAttachments) { stripAttachments = it }
            SanitisationSwitch("Strip Optional Hidden Layers", stripLayers) { stripLayers = it }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              OutlinedTextField(
                value = outputSuffix,
                onValueChange = { outputSuffix = it },
                label = { Text("Suffix") },
                singleLine = true,
                modifier = Modifier.weight(1f)
              )
              OutlinedTextField(
                value = customFileName,
                onValueChange = { customFileName = it },
                label = { Text("Custom Name") },
                singleLine = true,
                modifier = Modifier.weight(1.4f)
              )
            }

            Button(
              onClick = {
                val u = selectedUri ?: return@Button
                val ops = operations.toList()
                scope.launch {
                  isBusy = true
                  errorMsg = null
                  val res = pdfPrivacyEngine.applyAndExport(
                    uri = u,
                    config = PdfPrivacyConfig(
                      operations = ops,
                      strategy = strategy,
                      stripMetadata = stripMeta,
                      stripAnnotations = stripAnnots,
                      stripFormFields = stripForms,
                      stripAttachments = stripAttachments,
                      stripHiddenLayers = stripLayers,
                      outputSuffix = outputSuffix,
                      customOutputName = customFileName.takeIf { it.isNotBlank() }
                    )
                  )
                  if (res.isSuccess) {
                    val exported = res.getOrThrow()
                    exportResult = exported
                    documentRepo.recordPrivacyProject(
                      RecentPrivacyProjectEntity(
                        id = "pdf_${System.currentTimeMillis()}",
                        sourceUriString = u.toString(),
                        displayName = pdfName,
                        sourceKind = "PDF",
                        lastMode = activeMode.name,
                        outputFilePath = exported.outputFile.absolutePath,
                        outputDisplayName = exported.displayName,
                        verificationSummary = exported.verificationReport.summaryText,
                        verifiedSafe = exported.verificationReport.verifiedSafeToShare,
                        operationCount = ops.size
                      )
                    )
                  } else {
                    errorMsg = res.exceptionOrNull()?.localizedMessage ?: "PDF redaction failed."
                  }
                  isBusy = false
                }
              },
              enabled = selectedUri != null && !isBusy,
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pdf_priv_export_button")
            ) {
              Icon(Icons.Default.Shield, null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(if (isBusy) "Rewriting & Verifying PDF..." else "Export Sanitised PDF & Verify")
            }
          }
        }

        if (isBusy) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = spec.accentColor)
          }
        }
        errorMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.errorColor) }

        exportResult?.let { res ->
          PrivacyVerificationCard(
            report = res.verificationReport,
            mimeType = "application/pdf",
            spec = spec,
            onOpenPdf = { f -> onOpenPdfInReader(documentRepo.contentUriForFile(f)) },
            onSaveAs = { f -> fileToSaveAs = f; saveCopyLauncher.launch(f.name) },
            onShare = { f -> context.startActivity(documentRepo.createShareIntent(f, "application/pdf", "Share Sanitised PDF")) }
          )
        }

        Spacer(Modifier.height(88.dp))
      }
    }
  }
}

@Composable
private fun SanitisationSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
