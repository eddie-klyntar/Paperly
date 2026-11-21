package com.example.paperly.engines.pdfworkshop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

enum class PdfCompressionPreset(val label: String, val jpegQuality: Float, val maxImageDim: Int, val note: String) {
  LIGHT("Light (High Quality)", 0.82f, 2200, "Lossy JPEG recompression of embedded bitmaps at 82% quality."),
  BALANCED("Balanced", 0.62f, 1500, "Lossy JPEG recompression of embedded bitmaps at 62% quality + metadata trim."),
  STRONG("Strong (Smallest File)", 0.40f, 1100, "Aggressive lossy bitmap recompression at 40% quality; best for photo-heavy PDFs.")
}

data class PageEditDescriptor(
  val id: String,
  val originalPageIndex: Int,
  val rotationDegrees: Int = 0
)

enum class AnnotationTool(val label: String) {
  FREEHAND("Draw"),
  HIGHLIGHT("Highlight"),
  UNDERLINE("Underline"),
  TEXT("Text Note"),
  RECTANGLE("Rectangle"),
  CIRCLE("Circle"),
  LINE("Line"),
  SIGNATURE("Signature")
}

data class NormalizedPoint(val x: Float, val y: Float)

data class PdfAnnotationStroke(
  val pageIndex: Int,
  val tool: AnnotationTool,
  val colorArgb: Int,
  val strokeWidthRatio: Float,
  val points: List<NormalizedPoint>,
  val textContent: String = ""
)

data class PdfCompressionReport(
  val outputFile: File,
  val originalBytes: Long,
  val outputBytes: Long,
  val recompressedImagesCount: Int,
  val isLossy: Boolean
)

class PdfWorkshopEngine(private val context: Context) {

  private fun exportsDir(): File = File(context.filesDir, "paperly_exports").apply { mkdirs() }

  private fun copyUriToTemp(uri: Uri, tag: String): Pair<File, Long> {
    val tempDir = File(context.cacheDir, "paperly_temp").apply { mkdirs() }
    val out = File(tempDir, "${tag}_${System.nanoTime()}.pdf")
    var totalBytes = 0L
    context.contentResolver.openInputStream(uri)?.use { input ->
      FileOutputStream(out).use { output ->
        val buf = ByteArray(16 * 1024)
        while (true) {
          val n = input.read(buf)
          if (n <= 0) break
          output.write(buf, 0, n)
          totalBytes += n
        }
      }
    } ?: error("Cannot read PDF URI.")
    return out to totalBytes
  }

  suspend fun loadPageThumbnails(uri: Uri, maxPages: Int = 80): Result<List<Bitmap>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val (temp, _) = copyUriToTemp(uri, "thumbs")
        try {
          ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
              val count = minOf(renderer.pageCount, maxPages)
              val list = mutableListOf<Bitmap>()
              for (i in 0 until count) {
                renderer.openPage(i).use { page ->
                  val w = 180
                  val h = (180f * page.height / page.width.coerceAtLeast(1)).toInt().coerceIn(80, 320)
                  val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                  bmp.eraseColor(Color.WHITE)
                  page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                  list.add(bmp)
                }
              }
              list
            }
          }
        } finally {
          temp.delete()
        }
      }
    }

  suspend fun mergePdfs(uris: List<Uri>, outputName: String): Result<File> =
    withContext(Dispatchers.IO) {
      runCatching {
        if (uris.size < 2) error("Select at least two PDF documents to merge.")
        val temps = uris.mapIndexed { i, u -> copyUriToTemp(u, "merge_$i").first }
        try {
          val cleanName = ensurePdfExt(outputName.ifBlank { "merged_document" })
          val outFile = File(exportsDir(), cleanName)
          val merger = PDFMergerUtility()
          merger.destinationFileName = outFile.absolutePath
          temps.forEach { merger.addSource(it) }
          merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
          outFile
        } finally {
          temps.forEach { it.delete() }
        }
      }
    }

  suspend fun splitPdfByRanges(
    uri: Uri,
    rangeExpression: String,
    outputPrefix: String
  ): Result<List<File>> = withContext(Dispatchers.IO) {
    runCatching {
      val (temp, _) = copyUriToTemp(uri, "split")
      try {
        PDDocument.load(temp).use { src ->
          val total = src.numberOfPages
          val groups = parseRangeGroups(rangeExpression, total)
          if (groups.isEmpty()) error("No valid page numbers found (1-$total).")
          val outFiles = mutableListOf<File>()
          groups.forEachIndexed { idx, pages ->
            PDDocument().use { outDoc ->
              pages.forEach { p -> outDoc.importPage(src.getPage(p)) }
              val outFile = File(exportsDir(), "${outputPrefix}_part${idx + 1}.pdf")
              outDoc.save(outFile)
              outFiles.add(outFile)
            }
          }
          outFiles
        }
      } finally {
        temp.delete()
      }
    }
  }

  suspend fun splitPdfEveryN(
    uri: Uri,
    everyN: Int,
    outputPrefix: String
  ): Result<List<File>> = withContext(Dispatchers.IO) {
    runCatching {
      val n = everyN.coerceAtLeast(1)
      val (temp, _) = copyUriToTemp(uri, "splitn")
      try {
        PDDocument.load(temp).use { src ->
          val total = src.numberOfPages
          val outFiles = mutableListOf<File>()
          var start = 0
          var part = 1
          while (start < total) {
            val end = minOf(start + n, total)
            PDDocument().use { outDoc ->
              for (p in start until end) outDoc.importPage(src.getPage(p))
              val outFile = File(exportsDir(), "${outputPrefix}_part$part.pdf")
              outDoc.save(outFile)
              outFiles.add(outFile)
            }
            start = end
            part++
          }
          outFiles
        }
      } finally {
        temp.delete()
      }
    }
  }

  suspend fun applyPageOperations(
    uri: Uri,
    descriptors: List<PageEditDescriptor>,
    outputName: String
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      if (descriptors.isEmpty()) error("At least one page must remain in the output PDF.")
      val (temp, _) = copyUriToTemp(uri, "pages")
      try {
        PDDocument.load(temp).use { src ->
          PDDocument().use { outDoc ->
            descriptors.forEach { desc ->
              if (desc.originalPageIndex in 0 until src.numberOfPages) {
                val imported = outDoc.importPage(src.getPage(desc.originalPageIndex))
                val baseRot = imported.rotation
                imported.rotation = (baseRot + desc.rotationDegrees + 360) % 360
              }
            }
            val outFile = File(exportsDir(), ensurePdfExt(outputName.ifBlank { "organized_pages" }))
            outDoc.save(outFile)
            outFile
          }
        }
      } finally {
        temp.delete()
      }
    }
  }

  suspend fun compressPdf(
    uri: Uri,
    preset: PdfCompressionPreset,
    stripMetadata: Boolean,
    outputName: String
  ): Result<PdfCompressionReport> = withContext(Dispatchers.IO) {
    runCatching {
      val (temp, origBytes) = copyUriToTemp(uri, "compress")
      try {
        var recompressedCount = 0
        val outFile = File(exportsDir(), ensurePdfExt(outputName.ifBlank { "compressed_doc" }))
        PDDocument.load(temp).use { doc ->
          if (stripMetadata) {
            doc.documentInformation.title = null
            doc.documentInformation.author = null
            doc.documentInformation.subject = null
            doc.documentInformation.keywords = null
            doc.documentInformation.creator = null
            doc.documentInformation.producer = "Paperly"
            doc.documentCatalog.metadata = null
          }
          for (page in doc.pages) {
            val resources = page.resources ?: continue
            val names = resources.xObjectNames.toList()
            for (xName in names) {
              val xObj = runCatching { resources.getXObject(xName) }.getOrNull()
              if (xObj is PDImageXObject) {
                val bmp = runCatching { xObj.image }.getOrNull() ?: continue
                val scaled = scaleBitmapIfNeeded(bmp, preset.maxImageDim)
                val newImg = JPEGFactory.createFromImage(doc, scaled, preset.jpegQuality)
                resources.put(xName, newImg)
                if (scaled != bmp) scaled.recycle()
                bmp.recycle()
                recompressedCount++
              }
            }
          }
          doc.save(outFile)
        }
        PdfCompressionReport(
          outputFile = outFile,
          originalBytes = origBytes,
          outputBytes = outFile.length(),
          recompressedImagesCount = recompressedCount,
          isLossy = recompressedCount > 0
        )
      } finally {
        temp.delete()
      }
    }
  }

  suspend fun saveAnnotatedPdf(
    uri: Uri,
    annotations: List<PdfAnnotationStroke>,
    outputName: String
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val (temp, _) = copyUriToTemp(uri, "annotate")
      try {
        val outFile = File(exportsDir(), ensurePdfExt(outputName.ifBlank { "annotated_doc" }))
        PDDocument.load(temp).use { doc ->
          annotations.groupBy { it.pageIndex }.forEach { (pageIdx, pageAnnots) ->
            if (pageIdx in 0 until doc.numberOfPages) {
              val page: PDPage = doc.getPage(pageIdx)
              val mediaBox = page.cropBox ?: page.mediaBox
              val pw = mediaBox.width
              val ph = mediaBox.height
              val ox = mediaBox.lowerLeftX
              val oy = mediaBox.lowerLeftY

              PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                pageAnnots.forEach { item ->
                  val r = Color.red(item.colorArgb)
                  val g = Color.green(item.colorArgb)
                  val b = Color.blue(item.colorArgb)
                  val lineWidth = (item.strokeWidthRatio * pw).coerceIn(1f, 28f)

                  when (item.tool) {
                    AnnotationTool.HIGHLIGHT -> {
                      if (item.points.size >= 2) {
                        cs.saveGraphicsState()
                        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f; strokingAlphaConstant = 0.35f }
                        cs.setGraphicsStateParameters(gs)
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth((lineWidth * 3.2f).coerceIn(8f, 36f))
                        drawNormalizedPolyline(cs, item.points, ox, oy, pw, ph)
                        cs.restoreGraphicsState()
                      }
                    }
                    AnnotationTool.FREEHAND, AnnotationTool.SIGNATURE -> {
                      if (item.points.size >= 2) {
                        cs.saveGraphicsState()
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth(lineWidth)
                        cs.setLineCapStyle(1)
                        cs.setLineJoinStyle(1)
                        drawNormalizedPolyline(cs, item.points, ox, oy, pw, ph)
                        cs.restoreGraphicsState()
                      }
                    }
                    AnnotationTool.UNDERLINE, AnnotationTool.LINE -> {
                      if (item.points.size >= 2) {
                        val p0 = item.points.first()
                        val p1 = item.points.last()
                        cs.saveGraphicsState()
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth(lineWidth)
                        cs.moveTo(ox + p0.x * pw, oy + (1f - p0.y) * ph)
                        cs.lineTo(ox + p1.x * pw, oy + (1f - p1.y) * ph)
                        cs.stroke()
                        cs.restoreGraphicsState()
                      }
                    }
                    AnnotationTool.RECTANGLE -> {
                      if (item.points.size >= 2) {
                        val p0 = item.points.first()
                        val p1 = item.points.last()
                        val x = ox + minOf(p0.x, p1.x) * pw
                        val y = oy + (1f - maxOf(p0.y, p1.y)) * ph
                        val w = kotlin.math.abs(p1.x - p0.x) * pw
                        val h = kotlin.math.abs(p1.y - p0.y) * ph
                        cs.saveGraphicsState()
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth(lineWidth)
                        cs.addRect(x, y, w, h)
                        cs.stroke()
                        cs.restoreGraphicsState()
                      }
                    }
                    AnnotationTool.CIRCLE -> {
                      if (item.points.size >= 2) {
                        val p0 = item.points.first()
                        val p1 = item.points.last()
                        val cx = ox + ((p0.x + p1.x) / 2f) * pw
                        val cy = oy + (1f - (p0.y + p1.y) / 2f) * ph
                        val rx = (kotlin.math.abs(p1.x - p0.x) / 2f) * pw
                        val ry = (kotlin.math.abs(p1.y - p0.y) / 2f) * ph
                        val k = 0.5522847f
                        cs.saveGraphicsState()
                        cs.setStrokingColor(r, g, b)
                        cs.setLineWidth(lineWidth)
                        cs.moveTo(cx + rx, cy)
                        cs.curveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry)
                        cs.curveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy)
                        cs.curveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry)
                        cs.curveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy)
                        cs.stroke()
                        cs.restoreGraphicsState()
                      }
                    }
                    AnnotationTool.TEXT -> {
                      val p0 = item.points.firstOrNull()
                      if (p0 != null && item.textContent.isNotBlank()) {
                        cs.saveGraphicsState()
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 13f)
                        cs.setNonStrokingColor(r, g, b)
                        cs.newLineAtOffset(ox + p0.x * pw, oy + (1f - p0.y) * ph)
                        val safeText = item.textContent.replace(Regex("[^\\x20-\\x7E]"), "?")
                        cs.showText(safeText)
                        cs.endText()
                        cs.restoreGraphicsState()
                      }
                    }
                  }
                }
              }
            }
          }
          doc.save(outFile)
        }
        outFile
      } finally {
        temp.delete()
      }
    }
  }

  private fun drawNormalizedPolyline(
    cs: PDPageContentStream,
    points: List<NormalizedPoint>,
    ox: Float,
    oy: Float,
    pw: Float,
    ph: Float
  ) {
    val first = points.first()
    cs.moveTo(ox + first.x * pw, oy + (1f - first.y) * ph)
    for (i in 1 until points.size) {
      val pt = points[i]
      cs.lineTo(ox + pt.x * pw, oy + (1f - pt.y) * ph)
    }
    cs.stroke()
  }

  private fun scaleBitmapIfNeeded(bmp: Bitmap, maxDim: Int): Bitmap {
    val m = maxOf(bmp.width, bmp.height)
    if (m <= maxDim) return bmp
    val scale = maxDim.toFloat() / m
    val nw = (bmp.width * scale).toInt().coerceAtLeast(32)
    val nh = (bmp.height * scale).toInt().coerceAtLeast(32)
    return Bitmap.createScaledBitmap(bmp, nw, nh, true)
  }

  fun parseRangeGroups(expression: String, totalPages: Int): List<List<Int>> {
    val result = mutableListOf<List<Int>>()
    expression.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { token ->
      if (token.contains("-")) {
        val parts = token.split("-")
        val start = parts.getOrNull(0)?.trim()?.toIntOrNull()
        val end = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (start != null && end != null) {
          val lo = minOf(start, end).coerceIn(1, totalPages)
          val hi = maxOf(start, end).coerceIn(1, totalPages)
          result += (lo..hi).map { it - 1 }
        }
      } else {
        val p = token.toIntOrNull()
        if (p != null && p in 1..totalPages) {
          result += listOf(p - 1)
        }
      }
    }
    return result
  }

  private fun ensurePdfExt(name: String): String {
    val clean = name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
    return if (clean.endsWith(".pdf", true)) clean else "$clean.pdf"
  }
}
