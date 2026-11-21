package com.example.paperly.ui.imageworkshop

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.engines.image.BatchItemProgress
import com.example.paperly.engines.image.ImageFitMode
import com.example.paperly.engines.image.ImageFormatOption
import com.example.paperly.engines.image.ImageProcessConfig
import com.example.paperly.engines.image.ImageProcessResult
import com.example.paperly.engines.image.ImageWorkshopEngine
import com.example.paperly.engines.image.InspectedImage
import com.example.paperly.engines.image.PdfOrientationOption
import com.example.paperly.engines.image.PdfPageSizePreset
import com.example.paperly.engines.image.ResizePreset
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlyHeaderBanner
import com.example.paperly.theme.PaperlySurfaceCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ImageWorkshopMode(val label: String) {
  SINGLE("Resize & Convert"),
  BATCH("Batch Queue"),
  TO_PDF("Images to PDF")
}

@Composable
fun ImageWorkshopScreen(
  preloadedUris: List<Uri>,
  settings: PaperlySettings,
  imageEngine: ImageWorkshopEngine,
  documentRepo: DocumentRepository,
  onOpenPdfUri: (Uri) -> Unit,
  onOpenInPrivacyStudio: (Uri?) -> Unit
) {
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var activeMode by remember { mutableStateOf(ImageWorkshopMode.SINGLE) }
  var selectedSingleUri by remember { mutableStateOf<Uri?>(preloadedUris.firstOrNull()) }
  var inspected by remember { mutableStateOf<InspectedImage?>(null) }

  // Config state
  var selectedPreset by remember { mutableStateOf(ResizePreset.ORIGINAL) }
  var customW by remember { mutableStateOf("") }
  var customH by remember { mutableStateOf("") }
  var lockAspect by remember { mutableStateOf(true) }
  var scalePercent by remember { mutableFloatStateOf(100f) }
  var outputFormat by remember {
    mutableStateOf(
      ImageFormatOption.entries.firstOrNull { it.name == settings.imageDefaultFormat } ?: ImageFormatOption.JPEG
    )
  }
  var quality by remember { mutableFloatStateOf(settings.imageDefaultQuality.toFloat()) }
  var useTargetSize by remember { mutableStateOf(false) }
  var targetSizeKb by remember { mutableStateOf("250") }
  var outputSuffix by remember { mutableStateOf(settings.imageSuffix) }

  var isProcessing by remember { mutableStateOf(false) }
  var singleResult by remember { mutableStateOf<ImageProcessResult?>(null) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  // Batch & Image-to-PDF lists
  val queueUris = remember { mutableStateListOf<Uri>() }
  val batchProgress = remember { mutableStateListOf<BatchItemProgress>() }
  var batchRunning by remember { mutableStateOf(false) }

  // Image-to-PDF config
  var pdfPageSize by remember { mutableStateOf(PdfPageSizePreset.A4) }
  var pdfOrientation by remember { mutableStateOf(PdfOrientationOption.AUTO) }
  var pdfMargin by remember { mutableIntStateOf(24) }
  var pdfFitMode by remember { mutableStateOf(ImageFitMode.FIT) }
  var pdfOutName by remember { mutableStateOf("paperly_images") }
  var generatedPdfFile by remember { mutableStateOf<File?>(null) }

  var fileToSaveAs by remember { mutableStateOf<Pair<File, String>?>(null) }
  val saveDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
    val pending = fileToSaveAs ?: return@rememberLauncherForActivityResult
    if (destUri != null) {
      scope.launch(Dispatchers.IO) {
        val ok = runCatching {
          context.contentResolver.openOutputStream(destUri)?.use { out ->
            pending.first.inputStream().use { it.copyTo(out) }
          }
        }.isSuccess
        withContext(Dispatchers.Main) {
          Toast.makeText(context, if (ok) "Saved copy successfully" else "Export failed", Toast.LENGTH_SHORT).show()
        }
      }
    }
    fileToSaveAs = null
  }

  val pickSinglePhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      selectedSingleUri = uri
      singleResult = null
    }
  }
  val pickSingleDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      selectedSingleUri = uri
      singleResult = null
    }
  }
  val pickMultiPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(40)) { uris ->
    if (uris.isNotEmpty()) {
      queueUris.addAll(uris)
      batchProgress.clear()
      generatedPdfFile = null
    }
  }
  val pickMultiDocs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    if (uris.isNotEmpty()) {
      queueUris.addAll(uris)
      batchProgress.clear()
      generatedPdfFile = null
    }
  }

  LaunchedEffect(preloadedUris) {
    if (preloadedUris.isNotEmpty()) {
      selectedSingleUri = preloadedUris.first()
      queueUris.clear()
      queueUris.addAll(preloadedUris)
    }
  }

  LaunchedEffect(selectedSingleUri) {
    inspected = null
    errorMsg = null
    val u = selectedSingleUri ?: return@LaunchedEffect
    val res = imageEngine.inspectImage(u)
    if (res.isSuccess) {
      val info = res.getOrThrow()
      inspected = info
      customW = info.width.toString()
      customH = info.height.toString()
    } else {
      errorMsg = res.exceptionOrNull()?.localizedMessage
    }
  }

  fun buildConfig(): ImageProcessConfig {
    val w = selectedPreset.width ?: customW.toIntOrNull()
    val h = selectedPreset.height ?: customH.toIntOrNull()
    return ImageProcessConfig(
      targetWidth = if (selectedPreset == ResizePreset.ORIGINAL && scalePercent.toInt() != 100) null else w,
      targetHeight = if (selectedPreset == ResizePreset.ORIGINAL && scalePercent.toInt() != 100) null else h,
      maintainAspectRatio = lockAspect,
      scalePercent = scalePercent.toInt(),
      format = outputFormat,
      quality = quality.toInt(),
      targetSizeKb = if (useTargetSize) targetSizeKb.toIntOrNull() else null,
      outputSuffix = outputSuffix
    )
  }

  PaperlyCanvasBackground(spec = spec) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(Modifier.height(10.dp))
      PaperlyHeaderBanner(
        title = "Image Workshop",
        subtitle = "Resize, compress, convert & create PDFs locally",
        trailing = {
          IconButton(
            onClick = { onOpenInPrivacyStudio(selectedSingleUri) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("img_ws_privacy_button")
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
        ImageWorkshopMode.entries.forEach { mode ->
          FilterChip(
            selected = activeMode == mode,
            onClick = { activeMode = mode },
            label = { Text(mode.label) },
            modifier = Modifier.testTag("image_mode_${mode.name.lowercase()}")
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
        when (activeMode) {
          ImageWorkshopMode.SINGLE -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  Button(
                    onClick = { pickSinglePhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("image_pick_photo_button")
                  ) {
                    Icon(Icons.Default.AddPhotoAlternate, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Photo Picker")
                  }
                  OutlinedButton(
                    onClick = { pickSingleDoc.launch(arrayOf("image/*")) },
                    shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Text("Files")
                  }
                }
                inspected?.let { info ->
                  Text(
                    text = "${info.displayName} • ${info.detectedFormat} • ${info.width}×${info.height} • ${Formatter.formatShortFileSize(context, info.originalSizeBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = spec.accentColor
                  )
                  if (info.hasAlpha && !outputFormat.supportsTransparency) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      Icon(Icons.Default.Warning, null, tint = spec.warningColor, modifier = Modifier.size(16.dp))
                      Text(
                        text = "Source has transparency. Converting to JPEG flattens alpha onto white.",
                        style = MaterialTheme.typography.labelSmall,
                        color = spec.warningColor
                      )
                    }
                  }
                }
                errorMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = spec.errorColor) }
              }
            }

            // Resize Card
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Resize & Resolution", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  ResizePreset.entries.forEach { preset ->
                    FilterChip(
                      selected = selectedPreset == preset,
                      onClick = {
                        selectedPreset = preset
                        if (preset.width != null && preset.height != null) {
                          customW = preset.width.toString()
                          customH = preset.height.toString()
                        }
                      },
                      label = { Text(preset.label) }
                    )
                  }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  OutlinedTextField(
                    value = customW,
                    onValueChange = { customW = it.filter(Char::isDigit); selectedPreset = ResizePreset.ORIGINAL },
                    label = { Text("Width px") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                  )
                  OutlinedTextField(
                    value = customH,
                    onValueChange = { customH = it.filter(Char::isDigit); selectedPreset = ResizePreset.ORIGINAL },
                    label = { Text("Height px") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                  )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                  Text("Maintain Aspect Ratio", style = MaterialTheme.typography.bodySmall, color = spec.textPrimaryColor)
                  Switch(checked = lockAspect, onCheckedChange = { lockAspect = it })
                }
                Text("Percentage Scale: ${scalePercent.toInt()}%", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                Slider(value = scalePercent, onValueChange = { scalePercent = it }, valueRange = 20f..150f)
              }
            }

            // Format & Compression Card
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Format & Compression", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  ImageFormatOption.entries.forEach { fmt ->
                    FilterChip(
                      selected = outputFormat == fmt,
                      onClick = { outputFormat = fmt },
                      label = { Text(fmt.label) },
                      modifier = Modifier.testTag("img_fmt_${fmt.name.lowercase()}")
                    )
                  }
                }
                if (outputFormat != ImageFormatOption.PNG) {
                  Text("Quality: ${quality.toInt()}%", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                  Slider(value = quality, onValueChange = { quality = it }, valueRange = 15f..100f)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Web 65" to 65f, "Balanced 85" to 85f, "Archive 95" to 95f).forEach { (lbl, q) ->
                      FilterChip(selected = quality.toInt() == q.toInt(), onClick = { quality = q }, label = { Text(lbl) })
                    }
                  }
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Target File Size (KB)", style = MaterialTheme.typography.bodySmall, color = spec.textPrimaryColor)
                    Switch(checked = useTargetSize, onCheckedChange = { useTargetSize = it })
                  }
                  if (useTargetSize) {
                    OutlinedTextField(
                      value = targetSizeKb,
                      onValueChange = { targetSizeKb = it.filter(Char::isDigit) },
                      label = { Text("Target KB (objective, preserves legibility)") },
                      singleLine = true,
                      modifier = Modifier.fillMaxWidth()
                    )
                  }
                }
                OutlinedTextField(
                  value = outputSuffix,
                  onValueChange = { outputSuffix = it },
                  label = { Text("Filename Suffix") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
              }
            }

            Button(
              onClick = {
                val u = selectedSingleUri ?: return@Button
                scope.launch {
                  isProcessing = true
                  val res = imageEngine.processSingle(u, buildConfig())
                  singleResult = res.getOrNull()
                  errorMsg = res.exceptionOrNull()?.localizedMessage
                  isProcessing = false
                }
              },
              enabled = selectedSingleUri != null && !isProcessing,
              shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("image_process_button")
            ) {
              if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(18.dp))
              else Text("Process Image Locally")
            }

            singleResult?.let { res ->
              PaperlySurfaceCard(spec = spec, selected = true, modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text("Output Ready: ${res.displayName}", style = MaterialTheme.typography.titleSmall, color = spec.accentColor)
                  Text(
                    "${res.width}×${res.height} • ${res.format.label} (Q${res.qualityUsed}) • " +
                      "${Formatter.formatShortFileSize(context, res.originalBytes)} → ${Formatter.formatShortFileSize(context, res.outputBytes)} (${res.compressionRatioPercent}% saved)",
                    style = MaterialTheme.typography.bodySmall,
                    color = spec.textPrimaryColor
                  )
                  res.previewBitmap?.let { bmp ->
                    Image(
                      bitmap = bmp.asImageBitmap(),
                      contentDescription = "Processed image preview",
                      contentScale = ContentScale.Fit,
                      modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(8.dp))
                    )
                  }
                  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                      onClick = { context.startActivity(documentRepo.createShareIntent(res.outputFile, res.format.mimeType, "Share Image")) },
                      modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                      Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Share")
                    }
                    OutlinedButton(
                      onClick = {
                        fileToSaveAs = res.outputFile to res.format.mimeType
                        saveDocumentLauncher.launch(res.displayName)
                      },
                      modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                    ) {
                      Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Save As")
                    }
                  }
                }
              }
            }
          }

          ImageWorkshopMode.BATCH -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Batch Queue (${queueUris.size} images)", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  Button(
                    onClick = { pickMultiPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                  ) { Text("Add Photos") }
                  OutlinedButton(
                    onClick = { pickMultiDocs.launch(arrayOf("image/*")) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) { Text("Add Files") }
                  if (queueUris.isNotEmpty()) {
                    OutlinedButton(onClick = { queueUris.clear(); batchProgress.clear() }) { Text("Clear") }
                  }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  ImageFormatOption.entries.forEach { fmt ->
                    FilterChip(selected = outputFormat == fmt, onClick = { outputFormat = fmt }, label = { Text(fmt.label) })
                  }
                }
                Text("Batch Quality: ${quality.toInt()}% • Scale: ${scalePercent.toInt()}%", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                Slider(value = quality, onValueChange = { quality = it }, valueRange = 25f..100f)

                Button(
                  onClick = {
                    val targets = queueUris.toList()
                    batchProgress.clear()
                    batchRunning = true
                    scope.launch {
                      imageEngine.processBatch(targets, buildConfig()).collect { item ->
                        batchProgress.add(item)
                      }
                      batchRunning = false
                    }
                  },
                  enabled = queueUris.isNotEmpty() && !batchRunning,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("image_batch_run_button")
                ) {
                  Text(if (batchRunning) "Processing ${batchProgress.size}/${queueUris.size}..." else "Run Batch Processing")
                }
              }
            }

            if (batchRunning && queueUris.isNotEmpty()) {
              LinearProgressIndicator(
                progress = { batchProgress.size.toFloat() / queueUris.size },
                modifier = Modifier.fillMaxWidth()
              )
            }

            batchProgress.forEach { item ->
              PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                  Column(Modifier.weight(1f)) {
                    Text(item.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val msg = item.result?.let {
                      "${it.width}×${it.height} • ${Formatter.formatShortFileSize(context, it.outputBytes)} (${it.compressionRatioPercent}% saved)"
                    } ?: "Error: ${item.error}"
                    Text(msg, style = MaterialTheme.typography.labelSmall, color = if (item.error != null) spec.errorColor else spec.textSecondaryColor)
                  }
                  if (item.result != null) {
                    IconButton(onClick = {
                      context.startActivity(documentRepo.createShareIntent(item.result.outputFile, item.result.format.mimeType, "Share Image"))
                    }) { Icon(Icons.Default.Share, "Share", tint = spec.accentColor) }
                  }
                }
              }
            }
          }

          ImageWorkshopMode.TO_PDF -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Images to Multipage PDF (${queueUris.size} pages)", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                  Button(
                    onClick = { pickMultiPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                  ) { Text("Select Images") }
                  OutlinedButton(onClick = { pickMultiDocs.launch(arrayOf("image/*")) }) { Text("Files") }
                }

                queueUris.forEachIndexed { idx, uri ->
                  val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Image ${idx + 1}"
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clip(RoundedCornerShape(8.dp))
                      .background(spec.surfaceVariantColor)
                      .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text("${idx + 1}. $name", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { if (idx > 0) queueUris.add(idx - 1, queueUris.removeAt(idx)) }, enabled = idx > 0) {
                      Icon(Icons.Default.ArrowUpward, "Move up", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { if (idx < queueUris.lastIndex) queueUris.add(idx + 1, queueUris.removeAt(idx)) }, enabled = idx < queueUris.lastIndex) {
                      Icon(Icons.Default.ArrowDownward, "Move down", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { queueUris.removeAt(idx) }) {
                      Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp))
                    }
                  }
                }

                Text("Page Size", style = MaterialTheme.typography.labelLarge, color = spec.textPrimaryColor)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  PdfPageSizePreset.entries.forEach { ps ->
                    FilterChip(selected = pdfPageSize == ps, onClick = { pdfPageSize = ps }, label = { Text(ps.label) })
                  }
                }
                Text("Orientation & Fit", style = MaterialTheme.typography.labelLarge, color = spec.textPrimaryColor)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  PdfOrientationOption.entries.forEach { o ->
                    FilterChip(selected = pdfOrientation == o, onClick = { pdfOrientation = o }, label = { Text(o.label) })
                  }
                  ImageFitMode.entries.forEach { f ->
                    FilterChip(selected = pdfFitMode == f, onClick = { pdfFitMode = f }, label = { Text(f.label) })
                  }
                }
                Text("Page Margin: $pdfMargin pt", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                Slider(value = pdfMargin.toFloat(), onValueChange = { pdfMargin = it.toInt() }, valueRange = 0f..56f)

                OutlinedTextField(
                  value = pdfOutName,
                  onValueChange = { pdfOutName = it },
                  label = { Text("Output PDF Filename") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )

                Button(
                  onClick = {
                    val list = queueUris.toList()
                    scope.launch {
                      isProcessing = true
                      val res = imageEngine.createPdfFromImages(list, pdfPageSize, pdfOrientation, pdfMargin, pdfFitMode, pdfOutName)
                      generatedPdfFile = res.getOrNull()
                      errorMsg = res.exceptionOrNull()?.localizedMessage
                      isProcessing = false
                    }
                  },
                  enabled = queueUris.isNotEmpty() && !isProcessing,
                  modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("image_to_pdf_button")
                ) {
                  Icon(Icons.Default.PictureAsPdf, null)
                  Spacer(Modifier.width(8.dp))
                  Text(if (isProcessing) "Generating PDF..." else "Generate Multipage PDF")
                }

                generatedPdfFile?.let { file ->
                  PaperlySurfaceCard(spec = spec, selected = true, modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = spec.accentColor)
                        Text("${file.name} (${Formatter.formatShortFileSize(context, file.length())})", style = MaterialTheme.typography.titleSmall)
                      }
                      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                          onClick = { onOpenPdfUri(documentRepo.contentUriForFile(file)) },
                          modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                          Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
                          Spacer(Modifier.width(4.dp))
                          Text("Open in Paperly")
                        }
                        OutlinedButton(
                          onClick = { context.startActivity(documentRepo.createShareIntent(file, "application/pdf", "Share PDF")) },
                          modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
                        ) {
                          Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                          Spacer(Modifier.width(4.dp))
                          Text("Share")
                        }
                      }
                    }
                  }
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
