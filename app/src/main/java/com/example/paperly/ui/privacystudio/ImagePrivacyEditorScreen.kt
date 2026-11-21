package com.example.paperly.ui.privacystudio

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.Preview
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.data.db.RecentPrivacyProjectEntity
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.engines.image.ImageFormatOption
import com.example.paperly.engines.privacy.BlurStyle
import com.example.paperly.engines.privacy.ImagePrivacyEngine
import com.example.paperly.engines.privacy.ImagePrivacyExportResult
import com.example.paperly.engines.privacy.LocalSensitiveDetector
import com.example.paperly.engines.privacy.NormPoint
import com.example.paperly.engines.privacy.NormRect
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
import kotlin.math.abs

@Composable
fun ImagePrivacyEditorScreen(
  initialUri: Uri?,
  initialMode: PrivacyEditMode,
  settings: PaperlySettings,
  imagePrivacyEngine: ImagePrivacyEngine,
  detector: LocalSensitiveDetector,
  documentRepo: DocumentRepository,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  var selectedUri by remember { mutableStateOf(initialUri) }
  var sourceName by remember { mutableStateOf("No image selected") }
  var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var previewRenderedBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var isLivePreview by remember { mutableStateOf(false) }

  var activeMode by remember { mutableStateOf(initialMode) }
  var activeShape by remember { mutableStateOf(PrivacySelectionShape.RECTANGLE) }
  var blurStyle by remember { mutableStateOf(BlurStyle.GAUSSIAN_BLUR) }
  var blurRadius by remember { mutableFloatStateOf(18f) }
  var mosaicSize by remember { mutableFloatStateOf(24f) }
  var brushWidth by remember { mutableFloatStateOf(0.04f) }
  var redactionColor by remember { mutableIntStateOf(AndroidColor.BLACK) }
  var redactionLabel by remember { mutableStateOf("") }
  var replacementConfig by remember { mutableStateOf(TextReplacementConfig()) }

  var zoom by remember { mutableFloatStateOf(1f) }
  val operations = remember { mutableStateListOf<PrivacyOperation>() }
  val redoStack = remember { mutableStateListOf<PrivacyOperation>() }
  val draftPoints = remember { mutableStateListOf<NormPoint>() }

  var faceSuggestions by remember { mutableStateOf<List<SensitiveSuggestion>>(emptyList()) }
  var stripExif by remember { mutableStateOf(true) }
  var outputFormat by remember { mutableStateOf(ImageFormatOption.JPEG) }
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
  var exportResult by remember { mutableStateOf<ImagePrivacyExportResult?>(null) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  var fileToSaveAs by remember { mutableStateOf<File?>(null) }
  val saveCopyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/*")) { dest ->
    val f = fileToSaveAs ?: return@rememberLauncherForActivityResult
    if (dest != null) {
      scope.launch(Dispatchers.IO) {
        val ok = runCatching {
          context.contentResolver.openOutputStream(dest)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        }.isSuccess
        withContext(Dispatchers.Main) {
          Toast.makeText(context, if (ok) "Saved cleaned image" else "Save failed", Toast.LENGTH_SHORT).show()
        }
      }
    }
    fileToSaveAs = null
  }

  val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) {
      selectedUri = uri
      exportResult = null
      operations.clear()
      redoStack.clear()
      faceSuggestions = emptyList()
      isLivePreview = false
    }
  }
  val pickDocImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      selectedUri = uri
      exportResult = null
      operations.clear()
      redoStack.clear()
      faceSuggestions = emptyList()
      isLivePreview = false
    }
  }

  LaunchedEffect(selectedUri) {
    val u = selectedUri ?: return@LaunchedEffect
    isBusy = true
    errorMsg = null
    val res = imagePrivacyEngine.loadDisplayBitmap(u, maxDimension = 1600)
    if (res.isSuccess) {
      val (bmp, name) = res.getOrThrow()
      sourceBitmap = bmp
      sourceName = name
      previewRenderedBitmap = null
    } else {
      errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Failed to decode image."
    }
    isBusy = false
  }

  LaunchedEffect(isLivePreview, operations.size, sourceBitmap) {
    val src = sourceBitmap
    if (!isLivePreview || src == null || src.isRecycled) {
      previewRenderedBitmap = null
      return@LaunchedEffect
    }
    val opsCopy = operations.toList()
    withContext(Dispatchers.Default) {
      val copy = src.copy(Bitmap.Config.ARGB_8888, true)
      imagePrivacyEngine.applyOperationsToBitmap(copy, opsCopy)
      previewRenderedBitmap = copy
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
        title = "Image Privacy Editor",
        subtitle = "$sourceName • ${operations.size} pending edit(s)",
        trailing = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize().testTag("img_priv_back")) {
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

        // Source Picker + Undo/Redo/Preview Bar
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              Button(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("img_priv_pick_photo")
              ) {
                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Photo Picker")
              }
              OutlinedButton(
                onClick = { pickDocImage.launch(arrayOf("image/*")) },
                modifier = Modifier.minimumInteractiveComponentSize()
              ) { Text("Files") }
              OutlinedButton(
                onClick = {
                  val bmp = sourceBitmap ?: return@OutlinedButton
                  scope.launch(Dispatchers.Default) {
                    val found = detector.detectFacesInBitmap(bmp)
                    withContext(Dispatchers.Main) {
                      faceSuggestions = found
                      if (found.isEmpty()) {
                        Toast.makeText(context, "No clear front-facing faces detected offline; draw manually instead.", Toast.LENGTH_SHORT).show()
                      }
                    }
                  }
                },
                enabled = sourceBitmap != null,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("img_priv_detect_faces")
              ) {
                Icon(Icons.Default.FaceRetouchingOff, contentDescription = "Detect faces", modifier = Modifier.size(16.dp))
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              IconButton(
                onClick = {
                  if (operations.isNotEmpty()) {
                    redoStack.add(operations.removeAt(operations.lastIndex))
                    isLivePreview = false
                  }
                },
                enabled = operations.isNotEmpty(),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("img_priv_undo")
              ) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }

              IconButton(
                onClick = {
                  if (redoStack.isNotEmpty()) {
                    operations.add(redoStack.removeAt(redoStack.lastIndex))
                    isLivePreview = false
                  }
                },
                enabled = redoStack.isNotEmpty(),
                modifier = Modifier.minimumInteractiveComponentSize().testTag("img_priv_redo")
              ) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }

              IconButton(
                onClick = { operations.clear(); redoStack.clear(); isLivePreview = false },
                enabled = operations.isNotEmpty(),
                modifier = Modifier.minimumInteractiveComponentSize()
              ) { Icon(Icons.Default.DeleteOutline, "Clear pending edits") }

              Spacer(Modifier.weight(1f))

              FilterChip(
                selected = isLivePreview,
                onClick = { isLivePreview = !isLivePreview },
                leadingIcon = { Icon(Icons.Default.Preview, null, modifier = Modifier.size(16.dp)) },
                label = { Text(if (isLivePreview) "Preview On" else "Preview") },
                modifier = Modifier.testTag("img_priv_preview_toggle")
              )
            }
          }
        }

        // Face Suggestions awaiting explicit user confirmation
        if (faceSuggestions.isNotEmpty()) {
          PaperlySurfaceCard(spec = spec, selected = true, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Offline Face Suggestions (Review & Approve)", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
              Text(
                "Note: Photo text, badges or angled faces are not automatically OCR-scanned offline; use manual tools for anything else.",
                style = MaterialTheme.typography.labelSmall,
                color = spec.textSecondaryColor
              )
              faceSuggestions.forEachIndexed { idx, sug ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                  Checkbox(
                    checked = sug.selected,
                    onCheckedChange = { checked ->
                      faceSuggestions = faceSuggestions.toMutableList().also { it[idx] = sug.copy(selected = checked) }
                    }
                  )
                  Text(sug.displayHint, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
              }
              Button(
                onClick = {
                  val approved = faceSuggestions.filter { it.selected }
                  approved.forEach { sug ->
                    operations.add(
                      PrivacyOperation(
                        id = "face_op_${System.nanoTime()}",
                        pageIndex = 0,
                        mode = activeMode,
                        shape = PrivacySelectionShape.ELLIPSE,
                        rect = sug.rect,
                        blurStyle = blurStyle,
                        blurRadius = blurRadius.toInt(),
                        mosaicBlockSize = mosaicSize.toInt(),
                        fillColorArgb = redactionColor,
                        optionalLabel = redactionLabel,
                        replacementConfig = replacementConfig
                      )
                    )
                  }
                  redoStack.clear()
                  faceSuggestions = emptyList()
                },
                enabled = faceSuggestions.any { it.selected },
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
              ) { Text("Apply ${activeMode.label} to Selected Faces") }
            }
          }
        }

        // Interactive Canvas
        val activeBmp = if (isLivePreview && previewRenderedBitmap != null) previewRenderedBitmap else sourceBitmap
        if (activeBmp != null && !activeBmp.isRecycled) {
          val aspect = activeBmp.width.toFloat() / activeBmp.height.coerceAtLeast(1)
          PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = if (isLivePreview) "Previewing cleaned pixels" else "Drag on image to mark regions",
                  style = MaterialTheme.typography.labelMedium,
                  color = spec.accentColor
                )
                Text("Zoom: ${String.format("%.1fx", zoom)}", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
              }
              Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..2.5f)

              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .border(1.dp, spec.borderColor, RoundedCornerShape(8.dp))
                  .graphicsLayer(scaleX = zoom, scaleY = zoom)
              ) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                ) {
                  Image(
                    bitmap = activeBmp.asImageBitmap(),
                    contentDescription = "Image privacy workspace",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                  )

                  if (!isLivePreview) {
                    Canvas(
                      modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(activeMode, activeShape, blurStyle, blurRadius, mosaicSize, brushWidth, redactionColor, redactionLabel, replacementConfig) {
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
                                val rect = if (activeShape == PrivacySelectionShape.RECTANGLE || activeShape == PrivacySelectionShape.ELLIPSE) {
                                  val a = pts.first(); val b = pts.last()
                                  NormRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)).normalized()
                                } else {
                                  val minX = pts.minOf { it.x }
                                  val minY = pts.minOf { it.y }
                                  val maxX = pts.maxOf { it.x }
                                  val maxY = pts.maxOf { it.y }
                                  NormRect(minX, minY, maxX, maxY).normalized()
                                }
                                if (rect.width > 0.008f || rect.height > 0.008f || activeShape == PrivacySelectionShape.BRUSH) {
                                  operations.add(
                                    PrivacyOperation(
                                      id = "img_op_${System.nanoTime()}",
                                      pageIndex = 0,
                                      mode = activeMode,
                                      shape = activeShape,
                                      rect = rect,
                                      points = pts,
                                      brushWidthRatio = brushWidth,
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
                    ) {
                      fun drawPendingOp(op: PrivacyOperation, isDraft: Boolean) {
                        val color = when (op.mode) {
                          PrivacyEditMode.PERMANENT_REDACTION -> Color(op.fillColorArgb).copy(alpha = if (isDraft) 0.65f else 0.88f)
                          PrivacyEditMode.BLUR_PIXELATE -> Color(0xFF0EA5E9).copy(alpha = 0.45f)
                          PrivacyEditMode.REPLACE_ANONYMISE -> Color(op.replacementConfig.backgroundColorArgb).copy(alpha = 0.88f)
                        }
                        val nr = op.rect.normalized()
                        val tl = Offset(nr.left * size.width, nr.top * size.height)
                        val sz = Size(nr.width * size.width, nr.height * size.height)
                        when (op.shape) {
                          PrivacySelectionShape.RECTANGLE -> {
                            drawRect(color, topLeft = tl, size = sz)
                            drawRect(Color.White.copy(alpha = 0.7f), topLeft = tl, size = sz, style = Stroke(width = 2f))
                          }
                          PrivacySelectionShape.ELLIPSE -> {
                            drawOval(color, topLeft = tl, size = sz)
                            drawOval(Color.White.copy(alpha = 0.7f), topLeft = tl, size = sz, style = Stroke(width = 2f))
                          }
                          PrivacySelectionShape.FREEHAND -> if (op.points.size >= 2) {
                            val p = Path().apply {
                              moveTo(op.points.first().x * size.width, op.points.first().y * size.height)
                              for (i in 1 until op.points.size) lineTo(op.points[i].x * size.width, op.points[i].y * size.height)
                              close()
                            }
                            drawPath(p, color)
                          }
                          PrivacySelectionShape.BRUSH -> if (op.points.size >= 2) {
                            val p = Path().apply {
                              moveTo(op.points.first().x * size.width, op.points.first().y * size.height)
                              for (i in 1 until op.points.size) lineTo(op.points[i].x * size.width, op.points[i].y * size.height)
                            }
                            drawPath(
                              p,
                              color,
                              style = Stroke(width = op.brushWidthRatio * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                          }
                        }
                      }

                      operations.forEach { drawPendingOp(it, isDraft = false) }
                      if (draftPoints.size >= 2) {
                        val a = draftPoints.first(); val b = draftPoints.last()
                        val r = NormRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
                        drawPendingOp(
                          PrivacyOperation(
                            id = "draft",
                            mode = activeMode,
                            shape = activeShape,
                            rect = r,
                            points = draftPoints.toList(),
                            brushWidthRatio = brushWidth,
                            fillColorArgb = redactionColor,
                            replacementConfig = replacementConfig
                          ),
                          isDraft = true
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        } else if (!isBusy) {
          PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
            Text(
              "Pick an image from the Photo Picker or Files above to start redacting, blurring or anonymising locally.",
              style = MaterialTheme.typography.bodySmall,
              color = spec.textSecondaryColor
            )
          }
        }

        // Editing Mode & Shape Controls
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Editing Mode", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
            PrivacyModeSelectorBar(selectedMode = activeMode, onSelectMode = { activeMode = it }, spec = spec)

            Text("Selection Tool", style = MaterialTheme.typography.labelLarge, color = spec.textPrimaryColor)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              PrivacySelectionShape.entries.forEach { shape ->
                FilterChip(
                  selected = activeShape == shape,
                  onClick = { activeShape = shape },
                  label = { Text(shape.label) },
                  modifier = Modifier.testTag("img_shape_${shape.name.lowercase()}")
                )
              }
            }

            if (activeShape == PrivacySelectionShape.BRUSH) {
              Text("Brush Width: ${(brushWidth * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
              Slider(value = brushWidth, onValueChange = { brushWidth = it }, valueRange = 0.015f..0.12f)
            }

            when (activeMode) {
              PrivacyEditMode.PERMANENT_REDACTION -> {
                Text("Opaque Redaction Colour", style = MaterialTheme.typography.labelMedium, color = spec.textSecondaryColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  privacyRedactionSwatches.forEach { c ->
                    SwatchDot(c, redactionColor == c, spec) { redactionColor = c }
                  }
                }
                OutlinedTextField(
                  value = redactionLabel,
                  onValueChange = { redactionLabel = it },
                  label = { Text("Optional Redaction Label (e.g. [REDACTED])") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
              }
              PrivacyEditMode.BLUR_PIXELATE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  BlurStyle.entries.forEach { style ->
                    FilterChip(
                      selected = blurStyle == style,
                      onClick = { blurStyle = style },
                      label = { Text(style.label) },
                      modifier = Modifier.testTag("img_blur_style_${style.name.lowercase()}")
                    )
                  }
                }
                if (blurStyle == BlurStyle.GAUSSIAN_BLUR) {
                  Text("Blur Intensity: ${blurRadius.toInt()}", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                  Slider(value = blurRadius, onValueChange = { blurRadius = it }, valueRange = 6f..42f)
                } else {
                  Text("Mosaic Block Size: ${mosaicSize.toInt()} px", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                  Slider(value = mosaicSize, onValueChange = { mosaicSize = it }, valueRange = 8f..64f)
                }
              }
              PrivacyEditMode.REPLACE_ANONYMISE -> {
                ReplacementStylePanel(config = replacementConfig, onChange = { replacementConfig = it }, spec = spec)
              }
            }
          }
        }

        // Export & Metadata Settings
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Export & Sanitisation Settings", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text("Strip EXIF Metadata & Thumbnail", style = MaterialTheme.typography.bodySmall, color = spec.textPrimaryColor)
              Switch(checked = stripExif, onCheckedChange = { stripExif = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ImageFormatOption.entries.forEach { fmt ->
                FilterChip(selected = outputFormat == fmt, onClick = { outputFormat = fmt }, label = { Text(fmt.label) })
              }
            }
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
                label = { Text("Custom Name (optional)") },
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
                  val res = imagePrivacyEngine.applyAndExport(
                    uri = u,
                    operations = ops,
                    outputFormat = outputFormat,
                    quality = settings.imageDefaultQuality,
                    suffix = outputSuffix,
                    stripExifMetadata = stripExif,
                    customOutputName = customFileName.takeIf { it.isNotBlank() }
                  )
                  if (res.isSuccess) {
                    val exported = res.getOrThrow()
                    exportResult = exported
                    documentRepo.recordPrivacyProject(
                      RecentPrivacyProjectEntity(
                        id = "img_${System.currentTimeMillis()}",
                        sourceUriString = u.toString(),
                        displayName = sourceName,
                        sourceKind = "IMAGE",
                        lastMode = activeMode.name,
                        outputFilePath = exported.outputFile.absolutePath,
                        outputDisplayName = exported.displayName,
                        verificationSummary = exported.verificationReport.summaryText,
                        verifiedSafe = exported.verificationReport.verifiedSafeToShare,
                        operationCount = ops.size
                      )
                    )
                  } else {
                    errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Export failed."
                  }
                  isBusy = false
                }
              },
              enabled = selectedUri != null && (operations.isNotEmpty() || stripExif) && !isBusy,
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("img_priv_export_button")
            ) {
              Icon(Icons.Default.Shield, null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text(if (isBusy) "Flattening & Verifying..." else "Export Cleaned Copy & Verify")
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
            mimeType = res.format.mimeType,
            spec = spec,
            onSaveAs = { file ->
              fileToSaveAs = file
              saveCopyLauncher.launch(file.name)
            },
            onShare = { file ->
              context.startActivity(documentRepo.createShareIntent(file, res.format.mimeType, "Share Sanitised Image"))
            }
          )
        }

        Spacer(Modifier.height(88.dp))
      }
    }
  }
}
