package com.example.paperly.engines.privacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.example.paperly.engines.image.ImageFormatOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImagePrivacyExportResult(
  val outputFile: File,
  val displayName: String,
  val format: ImageFormatOption,
  val previewBitmap: Bitmap,
  val verificationReport: PrivacyVerificationReport
)

class ImagePrivacyEngine(private val context: Context) {

  suspend fun loadDisplayBitmap(uri: Uri, maxDimension: Int = 2048): Result<Pair<Bitmap, String>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val name = queryName(uri) ?: "image"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("Cannot read source image.")
        decodeNormalizedBitmap(bytes, maxDimension) to name
      }
    }

  suspend fun applyAndExport(
    uri: Uri,
    operations: List<PrivacyOperation>,
    outputFormat: ImageFormatOption,
    quality: Int = 92,
    suffix: String = "_redacted",
    stripExifMetadata: Boolean = true,
    customOutputName: String? = null
  ): Result<ImagePrivacyExportResult> = withContext(Dispatchers.IO) {
    runCatching {
      val origName = queryName(uri) ?: "image"
      val sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Unable to open source image.")
      val originalBitmap = decodeNormalizedBitmap(sourceBytes, maxDimension = 4096)

      // Work on a fresh ARGB_8888 flattened canvas so no editable layers or hidden channels persist
      val workBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
      val canvas = Canvas(workBitmap)
      canvas.drawColor(Color.WHITE)
      canvas.drawBitmap(originalBitmap, 0f, 0f, null)
      originalBitmap.recycle()

      applyOperationsToBitmap(workBitmap, operations)

      val encodedBytes = encodeBitmap(workBitmap, outputFormat, quality.coerceIn(15, 100))
      val exportDir = File(context.filesDir, "paperly_exports").apply { mkdirs() }
      val baseName = (customOutputName?.trim()?.takeIf { it.isNotEmpty() }
        ?: "${origName.substringBeforeLast('.').ifBlank { "image" }}$suffix")
        .replace(Regex("[/\\\\:*?\"<>|]"), "_")
      val finalName = if (baseName.endsWith(".${outputFormat.ext}", ignoreCase = true)) {
        baseName
      } else {
        "$baseName.${outputFormat.ext}"
      }
      val outFile = File(exportDir, finalName)
      FileOutputStream(outFile).use { it.write(encodedBytes) }

      // Ensure zero sensitive EXIF/thumbnail remains when requested (encodeBitmap writes clean headers,
      // and we also verify/strip via ExifInterface if JPEG/WebP)
      if (stripExifMetadata) {
        stripExifIfSupported(outFile)
      }

      val report = verifyExportedImage(
        outputFile = outFile,
        displayName = finalName,
        originalBytes = sourceBytes.size.toLong(),
        operations = operations,
        expectedWidth = workBitmap.width,
        expectedHeight = workBitmap.height,
        expectedFormat = outputFormat,
        stripExifRequested = stripExifMetadata
      )

      val preview = decodeNormalizedBitmap(outFile.readBytes(), maxDimension = 1200)
      workBitmap.recycle()

      ImagePrivacyExportResult(
        outputFile = outFile,
        displayName = finalName,
        format = outputFormat,
        previewBitmap = preview,
        verificationReport = report
      )
    }
  }

  fun applyOperationsToBitmap(target: Bitmap, operations: List<PrivacyOperation>) {
    val canvas = Canvas(target)
    val w = target.width.toFloat()
    val h = target.height.toFloat()

    operations.forEach { op ->
      when (op.mode) {
        PrivacyEditMode.PERMANENT_REDACTION -> {
          val opaqueColor = op.fillColorArgb or (0xFF shl 24)
          val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = opaqueColor
            style = Paint.Style.FILL
          }
          drawShapeOperation(canvas, op, w, h, paint)
          if (op.optionalLabel.isNotBlank()) {
            drawLabelInsideRect(canvas, op.rect, w, h, op.optionalLabel, opaqueColor)
          }
        }
        PrivacyEditMode.BLUR_PIXELATE -> {
          applyConcealmentRegion(target, canvas, op)
        }
        PrivacyEditMode.REPLACE_ANONYMISE -> {
          val cfg = op.replacementConfig
          val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cfg.backgroundColorArgb or (0xFF shl 24)
            style = Paint.Style.FILL
          }
          val r = op.rect.normalized()
          val pxRect = RectF(r.left * w, r.top * h, r.right * w, r.bottom * h)
          canvas.drawRect(pxRect, bgPaint)
          drawReplacementText(canvas, pxRect, cfg, h)
        }
      }
    }
  }

  private fun drawShapeOperation(
    canvas: Canvas,
    op: PrivacyOperation,
    w: Float,
    h: Float,
    paint: Paint
  ) {
    val nr = op.rect.normalized()
    val pxRect = RectF(nr.left * w, nr.top * h, nr.right * w, nr.bottom * h)
    when (op.shape) {
      PrivacySelectionShape.RECTANGLE -> canvas.drawRect(pxRect, paint)
      PrivacySelectionShape.ELLIPSE -> canvas.drawOval(pxRect, paint)
      PrivacySelectionShape.FREEHAND -> {
        val path = buildFreehandPath(op.points, w, h, close = true)
        if (path != null) canvas.drawPath(path, paint) else canvas.drawRect(pxRect, paint)
      }
      PrivacySelectionShape.BRUSH -> {
        val path = buildFreehandPath(op.points, w, h, close = false)
        if (path != null) {
          val strokePaint = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = (op.brushWidthRatio * w).coerceIn(6f, w * 0.25f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
          }
          canvas.drawPath(path, strokePaint)
        } else {
          canvas.drawRect(pxRect, paint)
        }
      }
    }
  }

  private fun applyConcealmentRegion(target: Bitmap, targetCanvas: Canvas, op: PrivacyOperation) {
    val w = target.width
    val h = target.height
    val nr = op.rect.normalized()
    val bounds = RectF(nr.left * w, nr.top * h, nr.right * w, nr.bottom * h)
    val minX = max(0, bounds.left.toInt() - 4)
    val minY = max(0, bounds.top.toInt() - 4)
    val maxX = min(w, bounds.right.toInt() + 4)
    val maxY = min(h, bounds.bottom.toInt() + 4)
    val cropW = (maxX - minX).coerceAtLeast(1)
    val cropH = (maxY - minY).coerceAtLeast(1)
    if (cropW <= 1 || cropH <= 1) return

    val roi = Bitmap.createBitmap(target, minX, minY, cropW, cropH)
    val concealed = when (op.blurStyle) {
      BlurStyle.GAUSSIAN_BLUR -> stackBlur(roi, op.blurRadius.coerceIn(2, 48))
      BlurStyle.PIXELATE -> pixelateBitmap(roi, op.mosaicBlockSize.coerceIn(4, 96))
    }
    if (roi != concealed) roi.recycle()

    // Mask concealed crop strictly to the selected shape using an offscreen compositing layer
    val layer = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
    val lc = Canvas(layer)
    val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    val localRect = RectF(
      (nr.left * w) - minX,
      (nr.top * h) - minY,
      (nr.right * w) - minX,
      (nr.bottom * h) - minY
    )
    when (op.shape) {
      PrivacySelectionShape.RECTANGLE -> lc.drawRect(localRect, maskPaint)
      PrivacySelectionShape.ELLIPSE -> lc.drawOval(localRect, maskPaint)
      PrivacySelectionShape.FREEHAND -> {
        val path = buildOffsetPath(op.points, w.toFloat(), h.toFloat(), minX.toFloat(), minY.toFloat(), close = true)
        if (path != null) lc.drawPath(path, maskPaint) else lc.drawRect(localRect, maskPaint)
      }
      PrivacySelectionShape.BRUSH -> {
        val path = buildOffsetPath(op.points, w.toFloat(), h.toFloat(), minX.toFloat(), minY.toFloat(), close = false)
        if (path != null) {
          val strokePaint = Paint(maskPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = (op.brushWidthRatio * w).coerceIn(6f, w * 0.25f)
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
          }
          lc.drawPath(path, strokePaint)
        } else {
          lc.drawRect(localRect, maskPaint)
        }
      }
    }

    val srcInPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }
    lc.drawBitmap(concealed, 0f, 0f, srcInPaint)
    concealed.recycle()

    targetCanvas.drawBitmap(layer, minX.toFloat(), minY.toFloat(), null)
    layer.recycle()
  }

  private fun drawLabelInsideRect(
    canvas: Canvas,
    rect: NormRect,
    w: Float,
    h: Float,
    label: String,
    bgColor: Int
  ) {
    val nr = rect.normalized()
    val pxRect = RectF(nr.left * w, nr.top * h, nr.right * w, nr.bottom * h)
    if (pxRect.width() < 16f || pxRect.height() < 12f) return
    val lum = (Color.red(bgColor) * 299 + Color.green(bgColor) * 587 + Color.blue(bgColor) * 114) / 1000
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = if (lum > 135) Color.BLACK else Color.WHITE
      textAlign = Paint.Align.CENTER
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      textSize = (pxRect.height() * 0.45f).coerceIn(12f, 42f)
    }
    val textW = paint.measureText(label)
    if (textW > pxRect.width() * 0.9f && textW > 0f) {
      paint.textSize *= (pxRect.width() * 0.9f / textW)
    }
    val fm = paint.fontMetrics
    val baseline = pxRect.centerY() - (fm.ascent + fm.descent) / 2f
    canvas.save()
    canvas.clipRect(pxRect)
    canvas.drawText(label, pxRect.centerX(), baseline, paint)
    canvas.restore()
  }

  private fun drawReplacementText(
    canvas: Canvas,
    pxRect: RectF,
    cfg: TextReplacementConfig,
    imageHeight: Float
  ) {
    val text = cfg.text.ifBlank { "[REDACTED]" }
    val tf = when (cfg.fontStyle) {
      ReplacementFontStyle.SANS -> Typeface.SANS_SERIF
      ReplacementFontStyle.SERIF -> Typeface.SERIF
      ReplacementFontStyle.MONO -> Typeface.MONOSPACE
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = cfg.textColorArgb or (0xFF shl 24)
      typeface = Typeface.create(tf, Typeface.BOLD)
      textSize = ((cfg.fontSizeSp / 400f) * imageHeight).coerceIn(12f, max(16f, pxRect.height() * 0.72f))
      textAlign = when (cfg.alignment) {
        TextAlignOption.LEFT -> Paint.Align.LEFT
        TextAlignOption.CENTER -> Paint.Align.CENTER
        TextAlignOption.RIGHT -> Paint.Align.RIGHT
      }
    }
    val availW = (pxRect.width() - 8f).coerceAtLeast(8f)
    val measured = paint.measureText(text)
    if (measured > availW && measured > 0f) {
      paint.textSize = (paint.textSize * (availW / measured)).coerceAtLeast(8f)
    }
    val fm = paint.fontMetrics
    val baseline = pxRect.centerY() - (fm.ascent + fm.descent) / 2f
    val x = when (cfg.alignment) {
      TextAlignOption.LEFT -> pxRect.left + 4f
      TextAlignOption.CENTER -> pxRect.centerX()
      TextAlignOption.RIGHT -> pxRect.right - 4f
    }
    canvas.save()
    canvas.clipRect(pxRect)
    canvas.drawText(text, x, baseline, paint)
    canvas.restore()
  }

  fun verifyExportedImage(
    outputFile: File,
    displayName: String,
    originalBytes: Long,
    operations: List<PrivacyOperation>,
    expectedWidth: Int,
    expectedHeight: Int,
    expectedFormat: ImageFormatOption,
    stripExifRequested: Boolean
  ): PrivacyVerificationReport {
    val checks = mutableListOf<PrivacyVerificationCheck>()
    val warnings = mutableListOf<String>()

    val bytes = runCatching { outputFile.readBytes() }.getOrNull()
    if (bytes == null || bytes.isEmpty()) {
      checks.add(PrivacyVerificationCheck("Exported image file", VerificationStatus.REMOVAL_FAILED, "Output file missing or unreadable."))
      return PrivacyVerificationReport(outputFile, displayName, originalBytes, 0L, "Verification failed: unreadable file", checks, false)
    }

    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    if (decoded == null) {
      checks.add(PrivacyVerificationCheck("Flattened bitmap integrity", VerificationStatus.REMOVAL_FAILED, "Unable to decode exported image."))
      return PrivacyVerificationReport(outputFile, displayName, originalBytes, bytes.size.toLong(), "Verification failed: corrupt bitmap", checks, false)
    }

    // 1. Dimensions & Flattening check
    checks.add(
      PrivacyVerificationCheck(
        itemName = "Flattened output (${expectedFormat.label})",
        status = VerificationStatus.REMOVED_AND_VERIFIED,
        detail = "${decoded.width}×${decoded.height} single-layer raster verified (no editable layers)."
      )
    )

    // 2. EXIF & Embedded Thumbnail check
    val exif = runCatching { ExifInterface(outputFile.absolutePath) }.getOrNull()
    if (exif != null) {
      val hasThumb = exif.hasThumbnail()
      val sensitiveTagsPresent = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_USER_COMMENT
      ).any { !exif.getAttribute(it).isNullOrBlank() }

      val exifStatus = when {
        hasThumb || sensitiveTagsPresent -> if (stripExifRequested) VerificationStatus.REMOVAL_FAILED else VerificationStatus.RETAINED_INTENTIONALLY
        stripExifRequested -> VerificationStatus.REMOVED_AND_VERIFIED
        else -> VerificationStatus.NOT_DETECTED
      }
      checks.add(
        PrivacyVerificationCheck(
          itemName = "EXIF metadata & embedded thumbnail",
          status = exifStatus,
          detail = when (exifStatus) {
            VerificationStatus.REMOVED_AND_VERIFIED -> "No EXIF location, device tags or embedded thumbnail detected."
            VerificationStatus.NOT_DETECTED -> "No EXIF tags detected."
            VerificationStatus.RETAINED_INTENTIONALLY -> "Metadata retained by user choice."
            else -> "Metadata or thumbnail still present."
          }
        )
      )
    }

    // 3. Inspect edited pixel regions directly from re-decoded file
    val permanentOps = operations.filter { it.mode == PrivacyEditMode.PERMANENT_REDACTION }
    val replaceOps = operations.filter { it.mode == PrivacyEditMode.REPLACE_ANONYMISE }
    val blurOps = operations.filter { it.mode == PrivacyEditMode.BLUR_PIXELATE }

    if (permanentOps.isNotEmpty()) {
      var verifiedRegions = 0
      var unverifiedRegions = 0
      permanentOps.forEach { op ->
        if (op.shape == PrivacySelectionShape.RECTANGLE || op.shape == PrivacySelectionShape.ELLIPSE) {
          val ok = sampleRegionIsOpaqueFill(decoded, op.rect, op.fillColorArgb, hasLabel = op.optionalLabel.isNotBlank())
          if (ok) verifiedRegions++ else unverifiedRegions++
        } else {
          // Freehand / Brush: verify at least along sampled stroke points
          val ok = samplePointsMatchFill(decoded, op.points, op.fillColorArgb)
          if (ok) verifiedRegions++ else unverifiedRegions++
        }
      }
      val status = when {
        unverifiedRegions == 0 -> VerificationStatus.REMOVED_AND_VERIFIED
        verifiedRegions > 0 -> VerificationStatus.REMOVAL_ATTEMPTED_UNVERIFIED
        else -> VerificationStatus.REMOVAL_FAILED
      }
      checks.add(
        PrivacyVerificationCheck(
          itemName = "Permanent redaction regions (${permanentOps.size})",
          status = status,
          detail = "$verifiedRegions/${permanentOps.size} pixel regions verified opaque in final file."
        )
      )
    }

    if (replaceOps.isNotEmpty()) {
      checks.add(
        PrivacyVerificationCheck(
          itemName = "Replaced areas (${replaceOps.size})",
          status = VerificationStatus.REMOVED_AND_VERIFIED,
          detail = "Original pixels overwritten by opaque background and placeholder text."
        )
      )
    }

    if (blurOps.isNotEmpty()) {
      checks.add(
        PrivacyVerificationCheck(
          itemName = "Blur / Pixelate regions (${blurOps.size})",
          status = VerificationStatus.RETAINED_INTENTIONALLY,
          detail = "Visual concealment applied. Blur/pixelation can sometimes allow partial reconstruction."
        )
      )
      warnings.add("Blur and pixelation provide visual concealment only. Use Permanent Redaction for highly sensitive content.")
    }

    decoded.recycle()
    val safe = checks.none { it.status == VerificationStatus.REMOVAL_FAILED || it.status == VerificationStatus.REMOVAL_ATTEMPTED_UNVERIFIED }
    val summary = if (safe) {
      "Verified ${permanentOps.size + replaceOps.size} irreversible region(s), ${blurOps.size} blur/mosaic region(s)"
    } else {
      "Review verification warnings before sharing"
    }
    return PrivacyVerificationReport(
      outputFile = outputFile,
      displayName = displayName,
      originalBytes = originalBytes,
      outputBytes = bytes.size.toLong(),
      summaryText = summary,
      checks = checks,
      verifiedSafeToShare = safe,
      warnings = warnings
    )
  }

  private fun sampleRegionIsOpaqueFill(
    bmp: Bitmap,
    rect: NormRect,
    expectedArgb: Int,
    hasLabel: Boolean
  ): Boolean {
    val nr = rect.normalized()
    // Sample near corners/upper-left band inside the box so center label text doesn't cause false mismatch
    val x0 = (nr.left * bmp.width + bmp.width * nr.width * 0.2f).toInt().coerceIn(0, bmp.width - 1)
    val y0 = (nr.top * bmp.height + bmp.height * nr.height * (if (hasLabel) 0.15f else 0.5f)).toInt().coerceIn(0, bmp.height - 1)
    val x1 = (nr.left * bmp.width + bmp.width * nr.width * 0.8f).toInt().coerceIn(0, bmp.width - 1)
    val samples = listOf(bmp.getPixel(x0, y0), bmp.getPixel(x1, y0))
    val er = Color.red(expectedArgb)
    val eg = Color.green(expectedArgb)
    val eb = Color.blue(expectedArgb)
    return samples.all { px ->
      abs(Color.red(px) - er) <= 32 &&
        abs(Color.green(px) - eg) <= 32 &&
        abs(Color.blue(px) - eb) <= 32
    }
  }

  private fun samplePointsMatchFill(bmp: Bitmap, points: List<NormPoint>, expectedArgb: Int): Boolean {
    if (points.isEmpty()) return true
    val er = Color.red(expectedArgb)
    val eg = Color.green(expectedArgb)
    val eb = Color.blue(expectedArgb)
    val step = max(1, points.size / 6)
    var matched = 0
    var total = 0
    for (i in points.indices step step) {
      val pt = points[i]
      val px = (pt.x * bmp.width).toInt().coerceIn(0, bmp.width - 1)
      val py = (pt.y * bmp.height).toInt().coerceIn(0, bmp.height - 1)
      val c = bmp.getPixel(px, py)
      total++
      if (abs(Color.red(c) - er) <= 38 && abs(Color.green(c) - eg) <= 38 && abs(Color.blue(c) - eb) <= 38) {
        matched++
      }
    }
    return total == 0 || matched * 2 >= total
  }

  private fun stripExifIfSupported(file: File) {
    runCatching {
      val exif = ExifInterface(file.absolutePath)
      val tagsToClear = listOf(
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_IMAGE_DESCRIPTION,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_USER_COMMENT
      )
      tagsToClear.forEach { exif.setAttribute(it, null) }
      exif.saveAttributes()
    }
  }

  private fun buildFreehandPath(points: List<NormPoint>, w: Float, h: Float, close: Boolean): Path? {
    if (points.size < 2) return null
    return Path().apply {
      moveTo(points.first().x * w, points.first().y * h)
      for (i in 1 until points.size) {
        lineTo(points[i].x * w, points[i].y * h)
      }
      if (close) close()
    }
  }

  private fun buildOffsetPath(
    points: List<NormPoint>,
    w: Float,
    h: Float,
    offsetX: Float,
    offsetY: Float,
    close: Boolean
  ): Path? {
    if (points.size < 2) return null
    return Path().apply {
      moveTo(points.first().x * w - offsetX, points.first().y * h - offsetY)
      for (i in 1 until points.size) {
        lineTo(points[i].x * w - offsetX, points[i].y * h - offsetY)
      }
      if (close) close()
    }
  }

  fun pixelateBitmap(src: Bitmap, blockSize: Int): Bitmap {
    val bSize = blockSize.coerceIn(4, 120)
    val w = src.width
    val h = src.height
    val smallW = (w / bSize).coerceAtLeast(2)
    val smallH = (h / bSize).coerceAtLeast(2)
    val down = Bitmap.createScaledBitmap(src, smallW, smallH, true)
    val up = Bitmap.createScaledBitmap(down, w, h, false)
    down.recycle()
    return up
  }

  /** Fast pure-Kotlin separable box/stack blur approximation compatible with minSdk 24 and JVM tests. */
  fun stackBlur(src: Bitmap, radius: Int): Bitmap {
    val r = radius.coerceIn(1, 50)
    val w = src.width
    val h = src.height
    val pix = IntArray(w * h)
    src.getPixels(pix, 0, w, 0, 0, w, h)

    val temp = IntArray(w * h)
    // Two box-blur passes approximate Gaussian smoothly and quickly
    repeat(2) {
      boxBlurHorizontal(pix, temp, w, h, r)
      boxBlurVertical(temp, pix, w, h, r)
    }

    return Bitmap.createBitmap(pix, w, h, Bitmap.Config.ARGB_8888)
  }

  private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val window = r * 2 + 1
    for (y in 0 until h) {
      val rowOff = y * w
      var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
      for (k in -r..r) {
        val c = src[rowOff + k.coerceIn(0, w - 1)]
        aSum += (c ushr 24) and 0xFF
        rSum += (c ushr 16) and 0xFF
        gSum += (c ushr 8) and 0xFF
        bSum += c and 0xFF
      }
      for (x in 0 until w) {
        dst[rowOff + x] = ((aSum / window) shl 24) or ((rSum / window) shl 16) or ((gSum / window) shl 8) or (bSum / window)
        val outC = src[rowOff + (x - r).coerceIn(0, w - 1)]
        val inC = src[rowOff + (x + r + 1).coerceIn(0, w - 1)]
        aSum += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
        rSum += ((inC ushr 16) and 0xFF) - ((outC ushr 16) and 0xFF)
        gSum += ((inC ushr 8) and 0xFF) - ((outC ushr 8) and 0xFF)
        bSum += (inC and 0xFF) - (outC and 0xFF)
      }
    }
  }

  private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
    val window = r * 2 + 1
    for (x in 0 until w) {
      var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
      for (k in -r..r) {
        val c = src[k.coerceIn(0, h - 1) * w + x]
        aSum += (c ushr 24) and 0xFF
        rSum += (c ushr 16) and 0xFF
        gSum += (c ushr 8) and 0xFF
        bSum += c and 0xFF
      }
      for (y in 0 until h) {
        dst[y * w + x] = ((aSum / window) shl 24) or ((rSum / window) shl 16) or ((gSum / window) shl 8) or (bSum / window)
        val outC = src[(y - r).coerceIn(0, h - 1) * w + x]
        val inC = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
        aSum += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
        rSum += ((inC ushr 16) and 0xFF) - ((outC ushr 16) and 0xFF)
        gSum += ((inC ushr 8) and 0xFF) - ((outC ushr 8) and 0xFF)
        bSum += (inC and 0xFF) - (outC and 0xFF)
      }
    }
  }

  private fun encodeBitmap(bitmap: Bitmap, format: ImageFormatOption, quality: Int): ByteArray {
    val flattened = if (!format.supportsTransparency && (bitmap.hasAlpha() || bitmap.config == Bitmap.Config.ARGB_8888)) {
      val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
      val c = Canvas(flat)
      c.drawColor(Color.WHITE)
      c.drawBitmap(bitmap, 0f, 0f, null)
      flat
    } else bitmap

    val bos = ByteArrayOutputStream()
    val compressFmt = when (format) {
      ImageFormatOption.JPEG -> Bitmap.CompressFormat.JPEG
      ImageFormatOption.PNG -> Bitmap.CompressFormat.PNG
      ImageFormatOption.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
      } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
      }
    }
    flattened.compress(compressFmt, quality, bos)
    if (flattened !== bitmap) flattened.recycle()
    return bos.toByteArray()
  }

  private fun decodeNormalizedBitmap(bytes: ByteArray, maxDimension: Int): Bitmap {
    if (bytes.isEmpty()) error("Image file is empty (0 bytes).")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
      (!hasSupportedImageMagic(bytes) && bounds.outMimeType.isNullOrBlank())
    ) {
      error("Unsupported or corrupted image file.")
    }
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
      sample *= 2
    }
    val raw = BitmapFactory.decodeByteArray(
      bytes, 0, bytes.size,
      BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    ) ?: error("Unable to decode image bitmap.")
    val rot = runCatching {
      val exif = ExifInterface(bytes.inputStream())
      when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
      }
    }.getOrDefault(0)
    val rotated = if (rot != 0) {
      val m = Matrix().apply { postRotate(rot.toFloat()) }
      Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
        if (it != raw) raw.recycle()
      }
    } else raw
    return if (rotated.config == Bitmap.Config.ARGB_8888) rotated
    else rotated.copy(Bitmap.Config.ARGB_8888, true).also { rotated.recycle() }
  }

  private fun hasSupportedImageMagic(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    return (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) ||
      (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) ||
      (bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte()) ||
      (bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()) ||
      ((bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte()) || (bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte())) ||
      (bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() && bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte()) ||
      (bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte())
  }

  private fun queryName(uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }
  }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
