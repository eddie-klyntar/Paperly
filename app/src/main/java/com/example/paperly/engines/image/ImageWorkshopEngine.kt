package com.example.paperly.engines.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

enum class ImageFormatOption(val label: String, val ext: String, val mimeType: String, val supportsTransparency: Boolean) {
  JPEG("JPEG", "jpg", "image/jpeg", false),
  PNG("PNG", "png", "image/png", true),
  WEBP("WebP", "webp", "image/webp", true)
}

enum class ResizePreset(val label: String, val width: Int?, val height: Int?) {
  ORIGINAL("Original", null, null),
  HD_720("HD 1280×720", 1280, 720),
  FHD_1080("Full HD 1920×1080", 1920, 1080),
  SQUARE_1080("Social Square 1080×1080", 1080, 1080),
  PORTRAIT_STORY("Story 1080×1920", 1080, 1920),
  PASSPORT_PHOTO("Passport 600×600", 600, 600),
  DOC_THUMB("Document Compact 1200×1600", 1200, 1600)
}

enum class PdfPageSizePreset(val label: String, val widthPt: Int, val heightPt: Int) {
  A4("A4 (595×842)", 595, 842),
  LETTER("US Letter (612×792)", 612, 792),
  LEGAL("Legal (612×1008)", 612, 1008),
  FIT_IMAGE("Match Image Size", 0, 0)
}

enum class PdfOrientationOption(val label: String) {
  AUTO("Auto"),
  PORTRAIT("Portrait"),
  LANDSCAPE("Landscape")
}

enum class ImageFitMode(val label: String) {
  FIT("Fit Inside Page"),
  FILL("Fill Page"),
  CENTER("Center Original")
}

data class InspectedImage(
  val uri: Uri,
  val displayName: String,
  val detectedFormat: String,
  val originalSizeBytes: Long,
  val width: Int,
  val height: Int,
  val hasAlpha: Boolean
)

data class ImageProcessConfig(
  val targetWidth: Int? = null,
  val targetHeight: Int? = null,
  val maintainAspectRatio: Boolean = true,
  val scalePercent: Int = 100,
  val format: ImageFormatOption = ImageFormatOption.JPEG,
  val quality: Int = 85,
  val targetSizeKb: Int? = null,
  val outputPrefix: String = "",
  val outputSuffix: String = "_paperly"
)

data class ImageProcessResult(
  val outputFile: File,
  val displayName: String,
  val width: Int,
  val height: Int,
  val originalBytes: Long,
  val outputBytes: Long,
  val format: ImageFormatOption,
  val qualityUsed: Int,
  val transparencyWarning: Boolean,
  val previewBitmap: Bitmap?
) {
  val compressionRatioPercent: Int
    get() = if (originalBytes <= 0) 0 else (((originalBytes - outputBytes).toFloat() / originalBytes) * 100f).toInt()
}

data class BatchItemProgress(
  val index: Int,
  val total: Int,
  val fileName: String,
  val result: ImageProcessResult? = null,
  val error: String? = null
)

class ImageWorkshopEngine(private val context: Context) {

  suspend fun inspectImage(uri: Uri): Result<InspectedImage> = withContext(Dispatchers.IO) {
    runCatching {
      val name = queryName(uri) ?: "image"
      val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Unable to open image.")
      if (bytes.isEmpty()) error("This image file is empty (0 bytes).")
      val format = detectFormatByMagicBytes(bytes)
      val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
      if (opts.outWidth <= 0 || opts.outHeight <= 0 || (format == "Unknown" && opts.outMimeType.isNullOrBlank())) {
        error("Unsupported or corrupted image ($format).")
      }
      val sampleBmp = decodeNormalizedBitmap(bytes, maxDimension = 512)
      val alpha = (sampleBmp.hasAlpha() || sampleBmp.config == Bitmap.Config.ARGB_8888) && hasNonOpaquePixels(sampleBmp)
      val exifRot = readExifRotation(bytes)
      val w = if (exifRot == 90 || exifRot == 270) opts.outHeight else opts.outWidth
      val h = if (exifRot == 90 || exifRot == 270) opts.outWidth else opts.outHeight
      sampleBmp.recycle()
      InspectedImage(
        uri = uri,
        displayName = name,
        detectedFormat = format,
        originalSizeBytes = bytes.size.toLong(),
        width = w,
        height = h,
        hasAlpha = alpha
      )
    }
  }

  fun detectFormatByMagicBytes(bytes: ByteArray): String {
    if (bytes.size < 12) return "Unknown"
    return when {
      bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "JPEG"
      bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "PNG"
      bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "WebP"
      bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte() -> "BMP"
      (bytes[0] == 'I'.code.toByte() && bytes[1] == 'I'.code.toByte()) || (bytes[0] == 'M'.code.toByte() && bytes[1] == 'M'.code.toByte()) -> "TIFF"
      bytes[4] == 'f'.code.toByte() && bytes[5] == 't'.code.toByte() && bytes[6] == 'y'.code.toByte() && bytes[7] == 'p'.code.toByte() -> "HEIC/HEIF"
      bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() -> "GIF"
      else -> "Unknown"
    }
  }

  suspend fun processSingle(uri: Uri, config: ImageProcessConfig, saveToExports: Boolean = true): Result<ImageProcessResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val originalName = queryName(uri) ?: "image"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("Cannot read source image.")
        val sourceBitmap = decodeNormalizedBitmap(bytes, maxDimension = 4096)
        val hasTransparency = (sourceBitmap.hasAlpha() || sourceBitmap.config == Bitmap.Config.ARGB_8888) && hasNonOpaquePixels(sourceBitmap)
        val transparencyWarn = hasTransparency && !config.format.supportsTransparency

        val (outW, outH) = computeTargetDimensions(sourceBitmap.width, sourceBitmap.height, config)
        val resized = if (outW != sourceBitmap.width || outH != sourceBitmap.height) {
          Bitmap.createScaledBitmap(sourceBitmap, outW, outH, true).also {
            if (it != sourceBitmap) sourceBitmap.recycle()
          }
        } else {
          sourceBitmap
        }

        // Flatten alpha on white when encoding to non-transparent format like JPEG
        val encodable = if (!config.format.supportsTransparency && resized.hasAlpha()) {
          val flat = Bitmap.createBitmap(resized.width, resized.height, Bitmap.Config.ARGB_8888)
          val c = Canvas(flat)
          c.drawColor(Color.WHITE)
          c.drawBitmap(resized, 0f, 0f, null)
          resized.recycle()
          flat
        } else {
          resized
        }

        var qualityUsed = config.quality.coerceIn(10, 100)
        val compressedBytes: ByteArray = if (config.targetSizeKb != null && config.targetSizeKb > 0 && config.format != ImageFormatOption.PNG) {
          val targetBytes = config.targetSizeKb * 1024L
          var lo = 18
          var hi = 96
          var bestBytes = encodeBitmap(encodable, config.format, qualityUsed)
          var bestQ = qualityUsed
          while (lo <= hi) {
            val mid = (lo + hi) / 2
            val attempt = encodeBitmap(encodable, config.format, mid)
            if (attempt.size <= targetBytes) {
              bestBytes = attempt
              bestQ = mid
              lo = mid + 1
            } else {
              hi = mid - 1
            }
          }
          qualityUsed = bestQ
          bestBytes
        } else {
          encodeBitmap(encodable, config.format, qualityUsed)
        }

        val dir = if (saveToExports) File(context.filesDir, "paperly_exports").apply { mkdirs() }
        else File(context.cacheDir, "paperly_temp").apply { mkdirs() }
        val base = originalName.substringBeforeLast('.').ifBlank { "image" }
        val outName = "${config.outputPrefix}${base}${config.outputSuffix}.${config.format.ext}"
        val outFile = File(dir, outName)
        FileOutputStream(outFile).use { it.write(compressedBytes) }

        val previewBmp = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size,
          BitmapFactory.Options().apply { inSampleSize = if (encodable.width > 1200) 2 else 1 })
        encodable.recycle()

        ImageProcessResult(
          outputFile = outFile,
          displayName = outName,
          width = outW,
          height = outH,
          originalBytes = bytes.size.toLong(),
          outputBytes = compressedBytes.size.toLong(),
          format = config.format,
          qualityUsed = qualityUsed,
          transparencyWarning = transparencyWarn,
          previewBitmap = previewBmp
        )
      }
    }

  fun processBatch(uris: List<Uri>, config: ImageProcessConfig): Flow<BatchItemProgress> = flow {
    uris.forEachIndexed { idx, uri ->
      val name = queryName(uri) ?: "Image ${idx + 1}"
      val res = processSingle(uri, config.copy(outputSuffix = "${config.outputSuffix}_${idx + 1}"), saveToExports = true)
      emit(
        BatchItemProgress(
          index = idx,
          total = uris.size,
          fileName = name,
          result = res.getOrNull(),
          error = res.exceptionOrNull()?.localizedMessage
        )
      )
    }
  }.flowOn(Dispatchers.IO)

  suspend fun createPdfFromImages(
    uris: List<Uri>,
    pageSizePreset: PdfPageSizePreset,
    orientation: PdfOrientationOption,
    marginPt: Int,
    fitMode: ImageFitMode,
    outputFileName: String
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      if (uris.isEmpty()) error("Select at least one image.")
      com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
        uris.forEachIndexed { idx, uri ->
          val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read image #${idx + 1}")
          val bmp = decodeNormalizedBitmap(bytes, maxDimension = 2400)
          val (pageW, pageH) = resolvePdfPageSize(bmp.width, bmp.height, pageSizePreset, orientation)
          val page = com.tom_roush.pdfbox.pdmodel.PDPage(
            com.tom_roush.pdfbox.pdmodel.common.PDRectangle(pageW.toFloat(), pageH.toFloat())
          )
          doc.addPage(page)

          val availW = (pageW - marginPt * 2).coerceAtLeast(40).toFloat()
          val availH = (pageH - marginPt * 2).coerceAtLeast(40).toFloat()
          val scale = when (fitMode) {
            ImageFitMode.FIT -> minOf(availW / bmp.width, availH / bmp.height)
            ImageFitMode.FILL -> maxOf(availW / bmp.width, availH / bmp.height)
            ImageFitMode.CENTER -> minOf(1f, minOf(availW / bmp.width, availH / bmp.height))
          }
          val drawW = bmp.width * scale
          val drawH = bmp.height * scale
          val left = marginPt + (availW - drawW) / 2f
          val bottom = marginPt + (availH - drawH) / 2f

          val pdImg = if (bmp.hasAlpha() || bmp.config == Bitmap.Config.ARGB_8888) {
            com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bmp)
          } else {
            com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromImage(doc, bmp, 0.90f)
          }
          com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(pdImg, left, bottom, drawW, drawH)
          }
          bmp.recycle()
        }
        val dir = File(context.filesDir, "paperly_exports").apply { mkdirs() }
        val cleanName = outputFileName.trim().ifBlank { "images_bundle" }.let {
          if (it.endsWith(".pdf", true)) it else "$it.pdf"
        }
        val outFile = File(dir, cleanName)
        doc.save(outFile)
        outFile
      }
    }
  }

  private fun resolvePdfPageSize(
    imgW: Int,
    imgH: Int,
    preset: PdfPageSizePreset,
    orientation: PdfOrientationOption
  ): Pair<Int, Int> {
    if (preset == PdfPageSizePreset.FIT_IMAGE) {
      val maxDim = 1200f
      val scale = minOf(1f, maxDim / maxOf(imgW, imgH))
      return (imgW * scale).toInt().coerceAtLeast(100) to (imgH * scale).toInt().coerceAtLeast(100)
    }
    val w = preset.widthPt
    val h = preset.heightPt
    return when (orientation) {
      PdfOrientationOption.PORTRAIT -> minOf(w, h) to maxOf(w, h)
      PdfOrientationOption.LANDSCAPE -> maxOf(w, h) to minOf(w, h)
      PdfOrientationOption.AUTO -> if (imgW > imgH) maxOf(w, h) to minOf(w, h) else minOf(w, h) to maxOf(w, h)
    }
  }

  private fun computeTargetDimensions(srcW: Int, srcH: Int, config: ImageProcessConfig): Pair<Int, Int> {
    if (config.scalePercent != 100 && config.targetWidth == null && config.targetHeight == null) {
      val factor = config.scalePercent.coerceIn(10, 200) / 100f
      return (srcW * factor).toInt().coerceAtLeast(16) to (srcH * factor).toInt().coerceAtLeast(16)
    }
    val reqW = config.targetWidth?.coerceIn(16, 8000)
    val reqH = config.targetHeight?.coerceIn(16, 8000)
    return when {
      reqW != null && reqH != null -> {
        if (config.maintainAspectRatio) {
          val scale = minOf(reqW.toFloat() / srcW, reqH.toFloat() / srcH)
          (srcW * scale).toInt().coerceAtLeast(16) to (srcH * scale).toInt().coerceAtLeast(16)
        } else reqW to reqH
      }
      reqW != null -> reqW to (reqW * (srcH.toFloat() / srcW)).toInt().coerceAtLeast(16)
      reqH != null -> (reqH * (srcW.toFloat() / srcH)).toInt().coerceAtLeast(16) to reqH
      else -> srcW to srcH
    }
  }

  private fun encodeBitmap(bitmap: Bitmap, format: ImageFormatOption, quality: Int): ByteArray {
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
    bitmap.compress(compressFmt, quality, bos)
    return bos.toByteArray()
  }

  private fun decodeNormalizedBitmap(bytes: ByteArray, maxDimension: Int): Bitmap {
    if (bytes.isEmpty()) error("Image file is empty (0 bytes).")
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
      (detectFormatByMagicBytes(bytes) == "Unknown" && bounds.outMimeType.isNullOrBlank())
    ) {
      error("Unsupported or corrupted image file.")
    }
    var sample = 1
    while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
      sample *= 2
    }
    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
      ?: error("Unable to decode image bitmap.")
    val rot = readExifRotation(bytes)
    if (rot == 0) return raw
    val m = Matrix().apply { postRotate(rot.toFloat()) }
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    if (rotated != raw) raw.recycle()
    return rotated
  }

  private fun readExifRotation(bytes: ByteArray): Int = runCatching {
    val exif = ExifInterface(bytes.inputStream())
    when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
      ExifInterface.ORIENTATION_ROTATE_90 -> 90
      ExifInterface.ORIENTATION_ROTATE_180 -> 180
      ExifInterface.ORIENTATION_ROTATE_270 -> 270
      else -> 0
    }
  }.getOrDefault(0)

  private fun hasNonOpaquePixels(bitmap: Bitmap): Boolean {
    val stepX = (bitmap.width / 16).coerceAtLeast(1)
    val stepY = (bitmap.height / 16).coerceAtLeast(1)
    var y = 0
    while (y < bitmap.height) {
      var x = 0
      while (x < bitmap.width) {
        if ((bitmap.getPixel(x, y) ushr 24) < 250) return true
        x += stepX
      }
      y += stepY
    }
    return false
  }

  private fun queryName(uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }
  }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
