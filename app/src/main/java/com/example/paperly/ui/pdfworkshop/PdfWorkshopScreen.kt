package com.example.paperly.ui.pdfworkshop

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.engines.pdf.PdfReaderEngine
import com.example.paperly.engines.pdf.PdfSession
import com.example.paperly.engines.pdfworkshop.AnnotationTool
import com.example.paperly.engines.pdfworkshop.NormalizedPoint
import com.example.paperly.engines.pdfworkshop.PageEditDescriptor
import com.example.paperly.engines.pdfworkshop.PdfAnnotationStroke
import com.example.paperly.engines.pdfworkshop.PdfCompressionPreset
import com.example.paperly.engines.pdfworkshop.PdfCompressionReport
import com.example.paperly.engines.pdfworkshop.PdfWorkshopEngine
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlyHeaderBanner
import com.example.paperly.theme.PaperlySurfaceCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class PdfWorkshopTool(val label: String) {
  MERGE("Merge"),
  SPLIT("Split"),
  PAGES("Organise Pages"),
  COMPRESS("Compress"),
  ANNOTATE("Annotate & Sign")
}

private val annotationPalette = listOf(
  0xFFDC2626.toInt(), // Red
  0xFF2563EB.toInt(), // Blue
  0xFFFACC15.toInt(), // Highlighter Yellow
  0xFF16A34A.toInt(), // Green
  0xFF0F172A.toInt(), // Ink Black
  0xFF9333EA.toInt()  // Violet
)

@Composable
fun PdfWorkshopScreen(
  preloadedPdfUri: Uri?,
  settings: PaperlySettings,
  pdfWorkshopEngine: PdfWorkshopEngine,
  pdfReaderEngine: PdfReaderEngine,
  documentRepo: DocumentRepository,
  onOpenPdfUri: (Uri) -> Unit,
  onOpenInPrivacyStudio: (Uri?) -> Unit
) {
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var activeTool by remember { mutableStateOf(PdfWorkshopTool.MERGE) }
  var primaryPdfUri by remember { mutableStateOf(preloadedPdfUri) }
  var primaryPdfName by remember { mutableStateOf("") }
  var isBusy by remember { mutableStateOf(false) }
  var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
  var compressionReport by remember { mutableStateOf<PdfCompressionReport?>(null) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  // Merge queue
  val mergeUris = remember { mutableStateListOf<Uri>() }
  var mergeOutName by remember { mutableStateOf("merged_document") }

  // Split state
  var splitEveryNMode by remember { mutableStateOf(false) }
  var splitRanges by remember { mutableStateOf("1-2, 3") }
  var splitEveryN by remember { mutableStateOf("2") }
  var splitPrefix by remember { mutableStateOf("split_doc") }

  // Page management state
  var pageThumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
  val pageEdits = remember { mutableStateListOf<PageEditDescriptor>() }
  var pagesOutName by remember { mutableStateOf("organized_document") }

  // Compression state
  var compPreset by remember {
    mutableStateOf(
      PdfCompressionPreset.entries.firstOrNull { it.label.startsWith(settings.pdfDefaultCompression) }
        ?: PdfCompressionPreset.BALANCED
    )
  }
  var stripMetadata by remember { mutableStateOf(true) }
  var compOutName by remember { mutableStateOf("compressed_document") }

  // Annotate state
  var annotSession by remember { mutableStateOf<PdfSession?>(null) }
  var annotPageIdx by remember { mutableIntStateOf(0) }
  var annotPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var annotTool by remember { mutableStateOf(AnnotationTool.FREEHAND) }
  var annotColor by remember { mutableIntStateOf(annotationPalette[0]) }
  var annotThickness by remember { mutableFloatStateOf(0.005f) }
  val strokes = remember { mutableStateListOf<PdfAnnotationStroke>() }
  val liveDraftPoints = remember { mutableStateListOf<NormalizedPoint>() }
  var pendingTextCoord by remember { mutableStateOf<NormalizedPoint?>(null) }
  var pendingTextInput by remember { mutableStateOf("") }
  var annotOutName by remember { mutableStateOf("annotated_document") }

  var fileToSaveAs by remember { mutableStateOf<File?>(null) }
  val saveCopyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destUri ->
    val f = fileToSaveAs ?: return@rememberLauncherForActivityResult
    if (destUri != null) {
      scope.launch(Dispatchers.IO) {
        val ok = runCatching {
          context.contentResolver.openOutputStream(destUri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        }.isSuccess
        withContext(Dispatchers.Main) {
          Toast.makeText(context, if (ok) "Exported PDF copy" else "Export failed", Toast.LENGTH_SHORT).show()
        }
      }
    }
    fileToSaveAs = null
  }

  val pickMultiPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isNotEmpty()) mergeUris.addAll(uris)
  }
  val pickSinglePdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      primaryPdfUri = uri
      resultFiles = emptyList()
      compressionReport = null
      errorMsg = null
    }
  }

  LaunchedEffect(preloadedPdfUri) {
    if (preloadedPdfUri != null) {
      primaryPdfUri = preloadedPdfUri
      if (mergeUris.none { it == preloadedPdfUri }) mergeUris.add(preloadedPdfUri)
    }
  }

  LaunchedEffect(primaryPdfUri, activeTool) {
    val u = primaryPdfUri ?: return@LaunchedEffect
    primaryPdfName = runCatching { documentRepo.inspectUri(u).displayName }.getOrDefault("Selected PDF")
    if (activeTool == PdfWorkshopTool.PAGES) {
      isBusy = true
      val thumbs = pdfWorkshopEngine.loadPageThumbnails(u)
      if (thumbs.isSuccess) {
        val list = thumbs.getOrThrow()
        pageThumbnails = list
        pageEdits.clear()
        list.indices.forEach { idx -> pageEdits.add(PageEditDescriptor("p_$idx", idx, 0)) }
      } else {
        errorMsg = thumbs.exceptionOrNull()?.localizedMessage
      }
      isBusy = false
    } else if (activeTool == PdfWorkshopTool.ANNOTATE) {
      annotSession?.close()
      annotSession = pdfReaderEngine.openSession(u).getOrNull()
      annotPageIdx = 0
      strokes.clear()
      annotPageBitmap = annotSession?.renderPage(0, 1000, 0)
    }
  }

  LaunchedEffect(annotPageIdx, annotSession) {
    annotPageBitmap = annotSession?.renderPage(annotPageIdx, 1000, 0)
  }

  PaperlyCanvasBackground(spec = spec) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(Modifier.height(10.dp))
      PaperlyHeaderBanner(
        title = "PDF Workshop",
        subtitle = "Non-destructive merge, split, page edits, compress & sign",
        trailing = {
          IconButton(
            onClick = { onOpenInPrivacyStudio(primaryPdfUri) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_ws_privacy_button")
          ) {
            Icon(Icons.Default.Shield, contentDescription = "Open in Privacy Studio", tint = spec.accentColor)
          }
        }
      )
      Spacer(Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        PdfWorkshopTool.entries.forEach { tool ->
          FilterChip(
            selected = activeTool == tool,
            onClick = { activeTool = tool; resultFiles = emptyList(); compressionReport = null; errorMsg = null },
            label = { Text(tool.label) },
            modifier = Modifier.testTag("pdf_tool_${tool.name.lowercase()}")
          )
        }
      }

      Spacer(Modifier.height(8.dp))

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        if (activeTool != PdfWorkshopTool.MERGE) {
          PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(Modifier.weight(1f)) {
                Text("Source PDF", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                Text(
                  text = if (primaryPdfUri != null) primaryPdfName.ifBlank { "PDF Selected" } else "No PDF chosen yet",
                  style = MaterialTheme.typography.titleSmall,
                  color = spec.textPrimaryColor,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Button(
                onClick = { pickSinglePdf.launch(arrayOf("application/pdf")) },
                shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("pdf_ws_pick_button")
              ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Choose PDF")
              }
            }
          }
        }

        when (activeTool) {
          PdfWorkshopTool.MERGE -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Merge PDFs (${mergeUris.size} queued)", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                Button(
                  onClick = { pickMultiPdfs.launch(arrayOf("application/pdf")) },
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pdf_merge_add_button")
                ) {
                  Icon(Icons.Default.Add, null)
                  Spacer(Modifier.width(6.dp))
                  Text("Add PDF Documents")
                }
                mergeUris.forEachIndexed { idx, uri ->
                  val name = uri.lastPathSegment?.substringAfterLast('/') ?: "PDF ${idx + 1}"
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(spec.surfaceVariantColor)
                      .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text("${idx + 1}. $name", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { if (idx > 0) mergeUris.add(idx - 1, mergeUris.removeAt(idx)) }, enabled = idx > 0) {
                      Icon(Icons.Default.ArrowUpward, "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { if (idx < mergeUris.lastIndex) mergeUris.add(idx + 1, mergeUris.removeAt(idx)) }, enabled = idx < mergeUris.lastIndex) {
                      Icon(Icons.Default.ArrowDownward, "Move down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { mergeUris.removeAt(idx) }) {
                      Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp))
                    }
                  }
                }
                OutlinedTextField(
                  value = mergeOutName,
                  onValueChange = { mergeOutName = it },
                  label = { Text("Output Filename") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                Button(
                  onClick = {
                    val uris = mergeUris.toList()
                    scope.launch {
                      isBusy = true; errorMsg = null
                      val res = pdfWorkshopEngine.mergePdfs(uris, mergeOutName)
                      resultFiles = res.getOrNull()?.let { listOf(it) } ?: emptyList()
                      errorMsg = res.exceptionOrNull()?.localizedMessage
                      isBusy = false
                    }
                  },
                  enabled = mergeUris.size >= 2 && !isBusy,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pdf_merge_run_button")
                ) {
                  Text(if (isBusy) "Merging..." else "Merge into New PDF")
                }
              }
            }
          }

          PdfWorkshopTool.SPLIT -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Split PDF", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  FilterChip(selected = !splitEveryNMode, onClick = { splitEveryNMode = false }, label = { Text("Ranges / Pages") })
                  FilterChip(selected = splitEveryNMode, onClick = { splitEveryNMode = true }, label = { Text("Every N Pages") })
                }
                if (!splitEveryNMode) {
                  OutlinedTextField(
                    value = splitRanges,
                    onValueChange = { splitRanges = it },
                    label = { Text("Ranges (e.g. 1-3, 4, 5-8)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                  )
                } else {
                  OutlinedTextField(
                    value = splitEveryN,
                    onValueChange = { splitEveryN = it.filter(Char::isDigit) },
                    label = { Text("Split every N pages") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                  )
                }
                OutlinedTextField(
                  value = splitPrefix,
                  onValueChange = { splitPrefix = it },
                  label = { Text("Output Filename Prefix") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                Button(
                  onClick = {
                    val u = primaryPdfUri ?: return@Button
                    scope.launch {
                      isBusy = true; errorMsg = null
                      val res = if (splitEveryNMode) {
                        pdfWorkshopEngine.splitPdfEveryN(u, splitEveryN.toIntOrNull() ?: 2, splitPrefix)
                      } else {
                        pdfWorkshopEngine.splitPdfByRanges(u, splitRanges, splitPrefix)
                      }
                      resultFiles = res.getOrNull() ?: emptyList()
                      errorMsg = res.exceptionOrNull()?.localizedMessage
                      isBusy = false
                    }
                  },
                  enabled = primaryPdfUri != null && !isBusy,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pdf_split_run_button")
                ) { Text(if (isBusy) "Splitting..." else "Split into New PDFs") }
              }
            }
          }

          PdfWorkshopTool.PAGES -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Organise, Rotate, Duplicate & Extract (${pageEdits.size} pages)", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                pageEdits.forEachIndexed { idx, item ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(spec.surfaceVariantColor)
                      .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    val thumb = pageThumbnails.getOrNull(item.originalPageIndex)
                    if (thumb != null && !thumb.isRecycled) {
                      Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "Page ${item.originalPageIndex + 1}",
                        modifier = Modifier
                          .size(44.dp, 58.dp)
                          .graphicsLayer(rotationZ = item.rotationDegrees.toFloat())
                      )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                      Text("Page ${item.originalPageIndex + 1}", style = MaterialTheme.typography.labelLarge)
                      Text("Rotation: ${item.rotationDegrees}°", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                    }
                    IconButton(onClick = { pageEdits[idx] = item.copy(rotationDegrees = (item.rotationDegrees + 90) % 360) }) {
                      Icon(Icons.Default.RotateRight, "Rotate 90°")
                    }
                    IconButton(onClick = { if (idx > 0) pageEdits.add(idx - 1, pageEdits.removeAt(idx)) }, enabled = idx > 0) {
                      Icon(Icons.Default.ArrowUpward, "Move up")
                    }
                    IconButton(onClick = { if (idx < pageEdits.lastIndex) pageEdits.add(idx + 1, pageEdits.removeAt(idx)) }, enabled = idx < pageEdits.lastIndex) {
                      Icon(Icons.Default.ArrowDownward, "Move down")
                    }
                    IconButton(onClick = {
                      pageEdits.add(idx + 1, item.copy(id = "dup_${System.nanoTime()}"))
                    }) {
                      Icon(Icons.Default.ContentCopy, "Duplicate page")
                    }
                    IconButton(onClick = { if (pageEdits.size > 1) pageEdits.removeAt(idx) }, enabled = pageEdits.size > 1) {
                      Icon(Icons.Default.Delete, "Delete page")
                    }
                  }
                }
                OutlinedTextField(
                  value = pagesOutName,
                  onValueChange = { pagesOutName = it },
                  label = { Text("Output Filename") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                Button(
                  onClick = {
                    val u = primaryPdfUri ?: return@Button
                    val ops = pageEdits.toList()
                    scope.launch {
                      isBusy = true; errorMsg = null
                      val res = pdfWorkshopEngine.applyPageOperations(u, ops, pagesOutName)
                      resultFiles = res.getOrNull()?.let { listOf(it) } ?: emptyList()
                      errorMsg = res.exceptionOrNull()?.localizedMessage
                      isBusy = false
                    }
                  },
                  enabled = primaryPdfUri != null && pageEdits.isNotEmpty() && !isBusy,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pdf_pages_save_button")
                ) { Text(if (isBusy) "Saving..." else "Save New PDF (Preserves Original)") }
              }
            }
          }

          PdfWorkshopTool.COMPRESS -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PDF Compression (Lossy Image Recompression)", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  PdfCompressionPreset.entries.forEach { p ->
                    FilterChip(selected = compPreset == p, onClick = { compPreset = p }, label = { Text(p.label) })
                  }
                }
                Text(compPreset.note, style = MaterialTheme.typography.labelSmall, color = spec.warningColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                  Text("Strip Document Metadata", style = MaterialTheme.typography.bodySmall)
                  Switch(checked = stripMetadata, onCheckedChange = { stripMetadata = it })
                }
                OutlinedTextField(
                  value = compOutName,
                  onValueChange = { compOutName = it },
                  label = { Text("Output Filename") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                Button(
                  onClick = {
                    val u = primaryPdfUri ?: return@Button
                    scope.launch {
                      isBusy = true; errorMsg = null; compressionReport = null
                      val res = pdfWorkshopEngine.compressPdf(u, compPreset, stripMetadata, compOutName)
                      val report = res.getOrNull()
                      compressionReport = report
                      if (report != null) resultFiles = listOf(report.outputFile)
                      errorMsg = res.exceptionOrNull()?.localizedMessage
                      isBusy = false
                    }
                  },
                  enabled = primaryPdfUri != null && !isBusy,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pdf_compress_run_button")
                ) { Text(if (isBusy) "Compressing..." else "Compress PDF") }

                compressionReport?.let { rep ->
                  val savedPct = if (rep.originalBytes > 0) (((rep.originalBytes - rep.outputBytes).toFloat() / rep.originalBytes) * 100f).toInt() else 0
                  Text(
                    text = "Original: ${Formatter.formatShortFileSize(context, rep.originalBytes)} → Output: ${Formatter.formatShortFileSize(context, rep.outputBytes)} ($savedPct% change) • Recompressed ${rep.recompressedImagesCount} embedded images (${if (rep.isLossy) "Lossy" else "Structure only"}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = spec.accentColor
                  )
                }
              }
            }
          }

          PdfWorkshopTool.ANNOTATE -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                  Text("Annotate & Sign", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                  val total = annotSession?.pageCount ?: 0
                  if (total > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      IconButton(onClick = { if (annotPageIdx > 0) annotPageIdx-- }, enabled = annotPageIdx > 0) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous page")
                      }
                      Text("${annotPageIdx + 1}/$total", style = MaterialTheme.typography.labelMedium)
                      IconButton(onClick = { if (annotPageIdx < total - 1) annotPageIdx++ }, enabled = annotPageIdx < total - 1) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next page")
                      }
                    }
                  }
                }
                Text(
                  text = "Visual annotations & handwritten signatures only (not a cryptographic digital signature).",
                  style = MaterialTheme.typography.labelSmall,
                  color = spec.textSecondaryColor
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  AnnotationTool.entries.forEach { t ->
                    FilterChip(selected = annotTool == t, onClick = { annotTool = t }, label = { Text(t.label) })
                  }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  annotationPalette.forEach { col ->
                    val sel = annotColor == col
                    Box(
                      modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(col))
                        .border(if (sel) 2.5.dp else 1.dp, if (sel) spec.textPrimaryColor else spec.borderColor, CircleShape)
                        .clickable { annotColor = col }
                    )
                  }
                  Spacer(Modifier.weight(1f))
                  IconButton(onClick = {
                    val lastIdx = strokes.indexOfLast { it.pageIndex == annotPageIdx }
                    if (lastIdx >= 0) strokes.removeAt(lastIdx)
                  }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo annotation") }
                }
                Slider(value = annotThickness, onValueChange = { annotThickness = it }, valueRange = 0.002f..0.016f)

                val bmp = annotPageBitmap
                if (bmp != null && !bmp.isRecycled) {
                  val aspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .aspectRatio(aspect)
                      .clip(RoundedCornerShape(6.dp))
                      .border(1.dp, spec.borderColor, RoundedCornerShape(6.dp))
                  ) {
                    Image(
                      bitmap = bmp.asImageBitmap(),
                      contentDescription = "PDF page annotation canvas",
                      contentScale = ContentScale.FillBounds,
                      modifier = Modifier.fillMaxSize()
                    )
                    Canvas(
                      modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(annotTool, annotColor, annotThickness, annotPageIdx) {
                          if (annotTool == AnnotationTool.TEXT) {
                            detectTapGestures { offset ->
                              pendingTextCoord = NormalizedPoint(
                                (offset.x / size.width).coerceIn(0f, 1f),
                                (offset.y / size.height).coerceIn(0f, 1f)
                              )
                            }
                          } else {
                            detectDragGestures(
                              onDragStart = { start ->
                                liveDraftPoints.clear()
                                liveDraftPoints.add(NormalizedPoint((start.x / size.width).coerceIn(0f, 1f), (start.y / size.height).coerceIn(0f, 1f)))
                              },
                              onDrag = { change, _ ->
                                change.consume()
                                val pt = NormalizedPoint((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                                liveDraftPoints.add(pt)
                              },
                              onDragEnd = {
                                if (liveDraftPoints.size >= 2) {
                                  strokes.add(
                                    PdfAnnotationStroke(
                                      pageIndex = annotPageIdx,
                                      tool = annotTool,
                                      colorArgb = annotColor,
                                      strokeWidthRatio = annotThickness,
                                      points = liveDraftPoints.toList()
                                    )
                                  )
                                }
                                liveDraftPoints.clear()
                              }
                            )
                          }
                        }
                    ) {
                      fun renderStroke(item: PdfAnnotationStroke) {
                        val color = if (item.tool == AnnotationTool.HIGHLIGHT) Color(item.colorArgb).copy(alpha = 0.35f) else Color(item.colorArgb)
                        val w = (item.strokeWidthRatio * size.width) * (if (item.tool == AnnotationTool.HIGHLIGHT) 3.2f else 1f)
                        when (item.tool) {
                          AnnotationTool.FREEHAND, AnnotationTool.HIGHLIGHT, AnnotationTool.SIGNATURE -> {
                            if (item.points.size >= 2) {
                              val path = Path().apply {
                                moveTo(item.points.first().x * size.width, item.points.first().y * size.height)
                                for (i in 1 until item.points.size) lineTo(item.points[i].x * size.width, item.points[i].y * size.height)
                              }
                              drawPath(path, color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                          }
                          AnnotationTool.UNDERLINE, AnnotationTool.LINE -> if (item.points.size >= 2) {
                            val a = item.points.first(); val b = item.points.last()
                            drawLine(color, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), strokeWidth = w)
                          }
                          AnnotationTool.RECTANGLE -> if (item.points.size >= 2) {
                            val a = item.points.first(); val b = item.points.last()
                            drawRect(
                              color = color,
                              topLeft = Offset(minOf(a.x, b.x) * size.width, minOf(a.y, b.y) * size.height),
                              size = Size(kotlin.math.abs(b.x - a.x) * size.width, kotlin.math.abs(b.y - a.y) * size.height),
                              style = Stroke(width = w)
                            )
                          }
                          AnnotationTool.CIRCLE -> if (item.points.size >= 2) {
                            val a = item.points.first(); val b = item.points.last()
                            drawOval(
                              color = color,
                              topLeft = Offset(minOf(a.x, b.x) * size.width, minOf(a.y, b.y) * size.height),
                              size = Size(kotlin.math.abs(b.x - a.x) * size.width, kotlin.math.abs(b.y - a.y) * size.height),
                              style = Stroke(width = w)
                            )
                          }
                          AnnotationTool.TEXT -> {
                            item.points.firstOrNull()?.let { p ->
                              drawCircle(color, radius = 6.dp.toPx(), center = Offset(p.x * size.width, p.y * size.height))
                            }
                          }
                        }
                      }
                      strokes.filter { it.pageIndex == annotPageIdx }.forEach { renderStroke(it) }
                      if (liveDraftPoints.size >= 2) {
                        renderStroke(PdfAnnotationStroke(annotPageIdx, annotTool, annotColor, annotThickness, liveDraftPoints.toList()))
                      }
                    }
                  }
                } else {
                  Text("Choose a PDF above to start annotating or inserting your signature.", style = MaterialTheme.typography.bodySmall, color = spec.textSecondaryColor)
                }

                OutlinedTextField(
                  value = annotOutName,
                  onValueChange = { annotOutName = it },
                  label = { Text("Annotated Output Filename") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                Button(
                  onClick = {
                    val u = primaryPdfUri ?: return@Button
                    val allStrokes = strokes.toList()
                    scope.launch {
                      isBusy = true; errorMsg = null
                      val res = pdfWorkshopEngine.saveAnnotatedPdf(u, allStrokes, annotOutName)
                      resultFiles = res.getOrNull()?.let { listOf(it) } ?: emptyList()
                      errorMsg = res.exceptionOrNull()?.localizedMessage
                      isBusy = false
                    }
                  },
                  enabled = primaryPdfUri != null && strokes.isNotEmpty() && !isBusy,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("pdf_annotate_save_button")
                ) { Text(if (isBusy) "Exporting..." else "Export Annotated Copy") }
              }
            }
          }
        }

        if (isBusy) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator(color = spec.accentColor)
          }
        }

        errorMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.errorColor) }

        resultFiles.forEach { file ->
          PaperlySurfaceCard(spec = spec, selected = true, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("${file.name} (${Formatter.formatShortFileSize(context, file.length())})", style = MaterialTheme.typography.titleSmall, color = spec.accentColor)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                  onClick = { onOpenPdfUri(documentRepo.contentUriForFile(file)) },
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Open")
                }
                OutlinedButton(
                  onClick = { context.startActivity(documentRepo.createShareIntent(file, "application/pdf", "Share PDF")) },
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Share")
                }
                OutlinedButton(
                  onClick = {
                    fileToSaveAs = file
                    saveCopyLauncher.launch(file.name)
                  },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Default.Save, "Save As")
                }
              }
            }
          }
        }

        Spacer(Modifier.height(88.dp))
      }
    }
  }

  if (pendingTextCoord != null) {
    AlertDialog(
      onDismissRequest = { pendingTextCoord = null },
      title = { Text("Add Text Note") },
      text = {
        OutlinedTextField(
          value = pendingTextInput,
          onValueChange = { pendingTextInput = it },
          label = { Text("Annotation text") },
          singleLine = true
        )
      },
      confirmButton = {
        Button(onClick = {
          val pt = pendingTextCoord
          if (pt != null && pendingTextInput.isNotBlank()) {
            strokes.add(
              PdfAnnotationStroke(
                pageIndex = annotPageIdx,
                tool = AnnotationTool.TEXT,
                colorArgb = annotColor,
                strokeWidthRatio = annotThickness,
                points = listOf(pt),
                textContent = pendingTextInput.trim()
              )
            )
          }
          pendingTextInput = ""
          pendingTextCoord = null
        }) { Text("Place Note") }
      },
      dismissButton = { TextButton(onClick = { pendingTextCoord = null }) { Text("Cancel") } }
    )
  }
}
