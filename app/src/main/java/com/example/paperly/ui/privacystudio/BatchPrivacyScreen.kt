package com.example.paperly.ui.privacystudio

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.data.db.DocumentKind
import com.example.paperly.data.db.RecentPrivacyProjectEntity
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.engines.image.ImageFormatOption
import com.example.paperly.engines.privacy.ImagePrivacyEngine
import com.example.paperly.engines.privacy.LocalSensitiveDetector
import com.example.paperly.engines.privacy.PdfPrivacyConfig
import com.example.paperly.engines.privacy.PdfPrivacyEngine
import com.example.paperly.engines.privacy.PrivacyEditMode
import com.example.paperly.engines.privacy.PrivacyOperation
import com.example.paperly.engines.privacy.PrivacySelectionShape
import com.example.paperly.engines.privacy.PrivacyVerificationReport
import com.example.paperly.engines.privacy.SensitiveSuggestion
import com.example.paperly.engines.privacy.TextReplacementConfig
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlyHeaderBanner
import com.example.paperly.theme.PaperlySurfaceCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private data class BatchPrivacyQueueItem(
  val id: String,
  val uri: Uri,
  val displayName: String,
  val kind: DocumentKind,
  val suggestions: List<SensitiveSuggestion> = emptyList(),
  val scanned: Boolean = false
)

private data class BatchPrivacyItemResult(
  val displayName: String,
  val isPdf: Boolean,
  val report: PrivacyVerificationReport? = null,
  val error: String? = null
)

@Composable
fun BatchPrivacyScreen(
  settings: PaperlySettings,
  documentRepo: DocumentRepository,
  pdfPrivacyEngine: PdfPrivacyEngine,
  imagePrivacyEngine: ImagePrivacyEngine,
  detector: LocalSensitiveDetector,
  onInspectPdf: (Uri, PrivacyEditMode) -> Unit,
  onInspectImage: (Uri, PrivacyEditMode) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val queue = remember { mutableStateListOf<BatchPrivacyQueueItem>() }
  val results = remember { mutableStateListOf<BatchPrivacyItemResult>() }

  var batchMode by remember { mutableStateOf(PrivacyEditMode.PERMANENT_REDACTION) }
  var stripMetadataExif by remember { mutableStateOf(true) }
  var stripPdfExtras by remember { mutableStateOf(true) }
  var customKeywords by remember { mutableStateOf("") }
  var suffix by remember { mutableStateOf(settings.privacyRedactSuffix) }

  var isScanning by remember { mutableStateOf(false) }
  var isRunning by remember { mutableStateOf(false) }
  var processedCount by remember { mutableIntStateOf(0) }
  var activeJob by remember { mutableStateOf<Job?>(null) }

  val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isNotEmpty()) {
      scope.launch {
        uris.forEach { u ->
          val meta = documentRepo.inspectUri(u)
          queue.add(BatchPrivacyQueueItem("b_${System.nanoTime()}", u, meta.displayName, meta.kind))
        }
      }
    }
  }

  val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
    if (uris.isNotEmpty()) {
      scope.launch {
        uris.forEach { u ->
          val meta = documentRepo.inspectUri(u)
          queue.add(BatchPrivacyQueueItem("b_${System.nanoTime()}", u, meta.displayName, DocumentKind.IMAGE))
        }
      }
    }
  }

  PaperlyCanvasBackground(spec = spec) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(Modifier.height(10.dp))
      PaperlyHeaderBanner(
        title = "Batch Privacy",
        subtitle = "Shared presets + individual per-document review & verification",
        trailing = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("batch_priv_back")) {
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

        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Queue Documents & Images (${queue.size})", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
            Text(
              "Manual redaction coordinates are never copied blindly across different files. Scan each file locally to review detections or open any file individually.",
              style = MaterialTheme.typography.labelSmall,
              color = spec.textSecondaryColor
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              Button(
                onClick = { pickPdfs.launch(arrayOf("application/pdf")) },
                enabled = !isRunning,
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("batch_priv_add_pdfs")
              ) {
                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add PDFs")
              }
              OutlinedButton(
                onClick = { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !isRunning,
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("batch_priv_add_images")
              ) {
                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Images")
              }
            }

            if (queue.isNotEmpty()) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                  onClick = {
                    val kws = customKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    scope.launch {
                      isScanning = true
                      for (i in queue.indices) {
                        val item = queue[i]
                        val detected = if (item.kind == DocumentKind.PDF) {
                          pdfPrivacyEngine.inspectPdfForPrivacy(item.uri, kws).getOrNull()?.suggestions.orEmpty()
                        } else {
                          val bmpRes = imagePrivacyEngine.loadDisplayBitmap(item.uri, 1024).getOrNull()
                          if (bmpRes != null) {
                            val faces = detector.detectFacesInBitmap(bmpRes.first)
                            bmpRes.first.recycle()
                            faces
                          } else emptyList()
                        }
                        queue[i] = item.copy(suggestions = detected, scanned = true)
                      }
                      isScanning = false
                    }
                  },
                  enabled = !isScanning && !isRunning,
                  modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("batch_priv_scan_all")
                ) {
                  Icon(Icons.Default.AutoFixHigh, null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(6.dp))
                  Text(if (isScanning) "Scanning..." else "Scan All Locally")
                }
                TextButton(onClick = { queue.clear(); results.clear() }, enabled = !isRunning) { Text("Clear") }
              }
            }
          }
        }

        // Batch Presets
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Common Batch Settings", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(PrivacyEditMode.PERMANENT_REDACTION, PrivacyEditMode.REPLACE_ANONYMISE).forEach { m ->
                FilterChip(selected = batchMode == m, onClick = { batchMode = m }, label = { Text(m.label) })
              }
            }
            OutlinedTextField(
              value = customKeywords,
              onValueChange = { customKeywords = it },
              label = { Text("Custom Keywords to Detect (comma separated)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = suffix,
              onValueChange = { suffix = it },
              label = { Text("Output Suffix") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text("Strip Metadata & EXIF / Thumbnails", style = MaterialTheme.typography.bodySmall)
              Switch(checked = stripMetadataExif, onCheckedChange = { stripMetadataExif = it })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text("Strip PDF Annotations, Forms & Attachments", style = MaterialTheme.typography.bodySmall)
              Switch(checked = stripPdfExtras, onCheckedChange = { stripPdfExtras = it })
            }
          }
        }

        // Individual Review Queue
        queue.forEachIndexed { qIdx, item ->
          PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                  Icon(
                    if (item.kind == DocumentKind.PDF) Icons.Default.Description else Icons.Default.AddPhotoAlternate,
                    contentDescription = item.kind.label,
                    tint = spec.accentColor
                  )
                  Column(Modifier.weight(1f)) {
                    Text(item.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                      if (!item.scanned) "Not scanned yet (tap Scan or Review individually)"
                      else "${item.suggestions.size} item(s) detected • ${item.suggestions.count { it.selected }} approved",
                      style = MaterialTheme.typography.labelSmall,
                      color = spec.textSecondaryColor
                    )
                  }
                }
                TextButton(onClick = {
                  if (item.kind == DocumentKind.PDF) onInspectPdf(item.uri, batchMode)
                  else onInspectImage(item.uri, batchMode)
                }) { Text("Edit") }
                IconButton(onClick = { queue.removeAt(qIdx) }, enabled = !isRunning) {
                  Icon(Icons.Default.Close, "Remove from queue")
                }
              }

              if (item.suggestions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Approve detected items for ${item.displayName}:", style = MaterialTheme.typography.labelSmall, color = spec.accentColor)
                  TextButton(onClick = {
                    queue[qIdx] = item.copy(suggestions = item.suggestions.map { it.copy(selected = true) })
                  }) { Text("Approve All") }
                }
                item.suggestions.take(10).forEachIndexed { sIdx, sug ->
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(6.dp))
                      .background(spec.surfaceVariantColor)
                      .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Checkbox(
                      checked = sug.selected,
                      onCheckedChange = { checked ->
                        val updated = item.suggestions.toMutableList()
                        updated[sIdx] = sug.copy(selected = checked)
                        queue[qIdx] = item.copy(suggestions = updated)
                      }
                    )
                    Text("p.${sug.pageIndex + 1} ${sug.displayHint}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  }
                }
              }
            }
          }
        }

        // Run / Cancel Batch Controls
        if (queue.isNotEmpty()) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
              onClick = {
                results.clear()
                processedCount = 0
                isRunning = true
                val snapshot = queue.toList()
                activeJob = scope.launch {
                  try {
                    snapshot.forEachIndexed { idx, item ->
                      ensureActive()
                      val ops = item.suggestions.filter { it.selected }.map { sug ->
                        val rep = if (batchMode == PrivacyEditMode.REPLACE_ANONYMISE) {
                          TextReplacementConfig(text = sug.recommendedPlaceholder.placeholder)
                        } else TextReplacementConfig()
                        PrivacyOperation(
                          id = "batch_${idx}_${System.nanoTime()}",
                          pageIndex = sug.pageIndex,
                          mode = batchMode,
                          shape = if (item.kind == DocumentKind.PDF) PrivacySelectionShape.RECTANGLE else PrivacySelectionShape.ELLIPSE,
                          rect = sug.rect,
                          fillColorArgb = AndroidColor.BLACK,
                          replacementConfig = rep,
                          sourceTextToVerify = sug.matchedText
                        )
                      }
                      val baseNoExt = item.displayName.substringBeforeLast('.').ifBlank { "item_${idx + 1}" }
                      val hasDupName = snapshot.count {
                        it.displayName.substringBeforeLast('.').equals(baseNoExt, ignoreCase = true)
                      } > 1
                      val itemSuffix = if (hasDupName) "${suffix}_${idx + 1}" else suffix
                      if (item.kind == DocumentKind.PDF) {
                        val res = pdfPrivacyEngine.applyAndExport(
                          uri = item.uri,
                          config = PdfPrivacyConfig(
                            operations = ops,
                            stripMetadata = stripMetadataExif,
                            stripAnnotations = stripPdfExtras,
                            stripFormFields = stripPdfExtras,
                            stripAttachments = stripPdfExtras,
                            stripHiddenLayers = stripPdfExtras,
                            outputSuffix = itemSuffix
                          )
                        )
                        val exp = res.getOrNull()
                        if (exp != null) {
                          documentRepo.recordPrivacyProject(
                            RecentPrivacyProjectEntity(
                              id = "batch_pdf_${System.nanoTime()}",
                              sourceUriString = item.uri.toString(),
                              displayName = item.displayName,
                              sourceKind = "PDF",
                              lastMode = batchMode.name,
                              outputFilePath = exp.outputFile.absolutePath,
                              outputDisplayName = exp.displayName,
                              verificationSummary = exp.verificationReport.summaryText,
                              verifiedSafe = exp.verificationReport.verifiedSafeToShare,
                              operationCount = ops.size
                            )
                          )
                          results.add(BatchPrivacyItemResult(item.displayName, true, report = exp.verificationReport))
                        } else {
                          results.add(BatchPrivacyItemResult(item.displayName, true, error = res.exceptionOrNull()?.localizedMessage))
                        }
                      } else {
                        val res = imagePrivacyEngine.applyAndExport(
                          uri = item.uri,
                          operations = ops,
                          outputFormat = ImageFormatOption.JPEG,
                          quality = settings.imageDefaultQuality,
                          suffix = itemSuffix,
                          stripExifMetadata = stripMetadataExif
                        )
                        val exp = res.getOrNull()
                        if (exp != null) {
                          exp.previewBitmap.recycle()
                          documentRepo.recordPrivacyProject(
                            RecentPrivacyProjectEntity(
                              id = "batch_img_${System.nanoTime()}",
                              sourceUriString = item.uri.toString(),
                              displayName = item.displayName,
                              sourceKind = "IMAGE",
                              lastMode = batchMode.name,
                              outputFilePath = exp.outputFile.absolutePath,
                              outputDisplayName = exp.displayName,
                              verificationSummary = exp.verificationReport.summaryText,
                              verifiedSafe = exp.verificationReport.verifiedSafeToShare,
                              operationCount = ops.size
                            )
                          )
                          results.add(BatchPrivacyItemResult(item.displayName, false, report = exp.verificationReport))
                        } else {
                          results.add(BatchPrivacyItemResult(item.displayName, false, error = res.exceptionOrNull()?.localizedMessage))
                        }
                      }
                      processedCount = idx + 1
                    }
                  } catch (_: CancellationException) {
                    Toast.makeText(context, "Batch processing cancelled safely", Toast.LENGTH_SHORT).show()
                  } finally {
                    documentRepo.clearTemporaryCache()
                    isRunning = false
                    activeJob = null
                  }
                }
              },
              enabled = !isRunning,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("batch_priv_run")
            ) {
              Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(if (isRunning) "Processing $processedCount/${queue.size}" else "Run & Verify Batch")
            }

            if (isRunning) {
              OutlinedButton(
                onClick = { activeJob?.cancel() },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("batch_priv_cancel")
              ) {
                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
              }
            }
          }
        }

        if (isRunning && queue.isNotEmpty()) {
          LinearProgressIndicator(
            progress = { processedCount.toFloat() / queue.size.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
          )
        }

        // Individual Verification Results
        results.forEach { res ->
          PaperlySurfaceCard(spec = spec, selected = res.report?.verifiedSafeToShare == true, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
              Column(Modifier.weight(1f)) {
                Text(res.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (res.report != null) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = spec.accentColor, modifier = Modifier.size(14.dp))
                    Text(res.report.summaryText, style = MaterialTheme.typography.labelSmall, color = spec.accentColor)
                  }
                } else {
                  Text("Error: ${res.error ?: "Failed"}", style = MaterialTheme.typography.labelSmall, color = spec.errorColor)
                }
              }
              res.report?.let { rep ->
                IconButton(onClick = {
                  val mime = if (res.isPdf) "application/pdf" else "image/*"
                  context.startActivity(documentRepo.createShareIntent(rep.outputFile, mime, "Share Sanitised File"))
                }) {
                  Icon(Icons.Default.Share, "Share", tint = spec.accentColor)
                }
              }
            }
          }
        }

        Spacer(Modifier.height(88.dp))
      }
    }
  }
}
