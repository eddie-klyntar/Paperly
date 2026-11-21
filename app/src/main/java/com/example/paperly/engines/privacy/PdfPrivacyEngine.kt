package com.example.paperly.engines.privacy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSFloat
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSNumber
import com.tom_roush.pdfbox.cos.COSString
import com.tom_roush.pdfbox.pdfparser.PDFStreamParser
import com.tom_roush.pdfbox.pdfwriter.ContentStreamWriter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.PDStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class PdfPrivacyConfig(
  val operations: List<PrivacyOperation>,
  val strategy: PdfRedactionStrategy = PdfRedactionStrategy.CONTENT_STREAM_REWRITE,
  val stripMetadata: Boolean = true,
  val stripAnnotations: Boolean = true,
  val stripFormFields: Boolean = true,
  val stripAttachments: Boolean = true,
  val stripHiddenLayers: Boolean = true,
  val outputSuffix: String = "_redacted",
  val customOutputName: String? = null
)

data class PdfPrivacyExportResult(
  val outputFile: File,
  val displayName: String,
  val verificationReport: PrivacyVerificationReport
)

data class PdfPrivacyInspection(
  val pageCount: Int,
  val hasTextLayer: Boolean,
  val annotationCount: Int,
  val formFieldCount: Int,
  val attachmentCount: Int,
  val hasMetadata: Boolean,
  val hasOptionalLayers: Boolean,
  val suggestions: List<SensitiveSuggestion>
)

class PdfPrivacyEngine(
  private val context: Context,
  private val detector: LocalSensitiveDetector,
  private val imagePrivacyEngine: ImagePrivacyEngine
) {

  suspend fun inspectPdfForPrivacy(uri: Uri, customKeywords: List<String> = emptyList()): Result<PdfPrivacyInspection> =
    withContext(Dispatchers.IO) {
      runCatching {
        val (temp, _) = copyUriToTemp(uri, "priv_inspect")
        try {
          PDDocument.load(temp).use { doc ->
            val pages = doc.numberOfPages
            val textSample = runCatching {
              PDFTextStripper().apply { startPage = 1; endPage = min(pages, 5) }.getText(doc)
            }.getOrDefault("")
            val annotCount = doc.pages.sumOf { runCatching { it.annotations.size }.getOrDefault(0) }
            val formCount = runCatching { doc.documentCatalog?.acroForm?.fields?.size ?: 0 }.getOrDefault(0)
            val attachCount = runCatching { doc.documentCatalog?.names?.embeddedFiles?.names?.size ?: 0 }.getOrDefault(0)
            val info = doc.documentInformation
            val hasMeta = !info?.title.isNullOrBlank() || !info?.author.isNullOrBlank() ||
              !info?.creator.isNullOrBlank() || !info?.subject.isNullOrBlank() ||
              !info?.keywords.isNullOrBlank() || doc.documentCatalog?.metadata != null
            val hasOcg = doc.documentCatalog?.ocProperties != null
            val suggestions = detector.detectSensitiveInPdf(doc, customKeywords)
            PdfPrivacyInspection(
              pageCount = pages,
              hasTextLayer = textSample.trim().length >= 6,
              annotationCount = annotCount,
              formFieldCount = formCount,
              attachmentCount = attachCount,
              hasMetadata = hasMeta,
              hasOptionalLayers = hasOcg,
              suggestions = suggestions
            )
          }
        } finally {
          temp.delete()
        }
      }
    }

  suspend fun searchOccurrences(uri: Uri, query: String): Result<List<PdfSearchOccurrence>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val (temp, _) = copyUriToTemp(uri, "priv_search")
        try {
          PDDocument.load(temp).use { doc ->
            detector.searchInDocument(doc, query)
          }
        } finally {
          temp.delete()
        }
      }
    }

  suspend fun extractPageWords(uri: Uri, pageIndex: Int): Result<List<PdfPageWordBox>> =
    withContext(Dispatchers.IO) {
      runCatching {
        val (temp, _) = copyUriToTemp(uri, "priv_words")
        try {
          PDDocument.load(temp).use { doc ->
            detector.extractPageWords(doc, pageIndex)
          }
        } finally {
          temp.delete()
        }
      }
    }

  suspend fun applyAndExport(uri: Uri, config: PdfPrivacyConfig): Result<PdfPrivacyExportResult> =
    withContext(Dispatchers.IO) {
      runCatching {
        val origName = queryName(uri) ?: "document.pdf"
        val (temp, origBytes) = copyUriToTemp(uri, "priv_apply")
        val exportDir = File(context.filesDir, "paperly_exports").apply { mkdirs() }
        val base = (config.customOutputName?.trim()?.takeIf { it.isNotEmpty() }
          ?: "${origName.substringBeforeLast('.').ifBlank { "document" }}${config.outputSuffix}")
          .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val outName = if (base.endsWith(".pdf", true)) base else "$base.pdf"
        val outFile = File(exportDir, outName)

        var unstrippedFormXObjectsEncountered = false
        val rasterisedPages = mutableSetOf<Int>()

        try {
          PDDocument.load(temp).use { srcDoc ->
            if (srcDoc.isEncrypted && !srcDoc.currentAccessPermission.canModify()) {
              error("This PDF has modification restrictions or encryption that prevent safe redaction.")
            }
            srcDoc.isAllSecurityToBeRemoved = true

            val opsByPage = config.operations.groupBy { it.pageIndex }

            // Create a brand-new PDDocument for a full-document rewrite (never incremental save)
            PDDocument().use { outDoc ->
              for (pageIdx in 0 until srcDoc.numberOfPages) {
                val pageOps = opsByPage[pageIdx].orEmpty()
                val needsRaster = pageOps.any { it.mode == PrivacyEditMode.BLUR_PIXELATE } ||
                  (pageOps.isNotEmpty() && config.strategy == PdfRedactionStrategy.RASTERIZE_AFFECTED_PAGES)

                if (needsRaster && pageOps.isNotEmpty()) {
                  val rasterBmp = renderSinglePageFromFile(temp, pageIdx)
                  if (rasterBmp != null) {
                    imagePrivacyEngine.applyOperationsToBitmap(rasterBmp, pageOps)
                    val srcPage = srcDoc.getPage(pageIdx)
                    val box = srcPage.cropBox ?: srcPage.mediaBox
                    val newPage = PDPage(PDRectangle(box.width, box.height))
                    outDoc.addPage(newPage)
                    val pdImg = LosslessFactory.createFromImage(outDoc, rasterBmp)
                    PDPageContentStream(outDoc, newPage).use { cs ->
                      cs.drawImage(pdImg, 0f, 0f, box.width, box.height)
                    }
                    rasterBmp.recycle()
                    rasterisedPages.add(pageIdx)
                    continue
                  }
                }

                // Import page into clean rewritten document
                val importedPage = outDoc.importPage(srcDoc.getPage(pageIdx))

                // Remove annotations if requested or overlapping any redaction region
                cleanPageAnnotations(importedPage, pageOps, config.stripAnnotations)

                if (pageOps.isNotEmpty()) {
                  val redactionRects = pageOps.map { it.rect.normalized() }
                  // 1. Permanently strip matching text operators & overlapping embedded image pixels in content streams
                  val streamResult = stripContentStreamRegions(outDoc, importedPage, pageOps)
                  if (streamResult.encounteredComplexFormXObject) {
                    unstrippedFormXObjectsEncountered = true
                  }
                  // 2. Draw permanent opaque redaction bars / replacement placeholder boxes on top
                  drawPrivacyOverlaysOnPage(outDoc, importedPage, pageOps)
                }
              }

              // Document-level sanitization on the fresh output document
              if (config.stripMetadata) {
                sanitizeDocumentMetadata(outDoc)
              }
              if (config.stripFormFields) {
                outDoc.documentCatalog?.acroForm = null
              }
              if (config.stripAttachments) {
                outDoc.documentCatalog?.names?.embeddedFiles = null
              }
              if (config.stripHiddenLayers) {
                outDoc.documentCatalog?.ocProperties = null
              }

              // Full non-incremental save
              outDoc.save(outFile)
            }
          }

          val report = verifyExportedPdf(
            outputFile = outFile,
            displayName = outName,
            originalBytes = origBytes,
            config = config,
            rasterisedPages = rasterisedPages,
            complexXObjectWarning = unstrippedFormXObjectsEncountered
          )

          PdfPrivacyExportResult(
            outputFile = outFile,
            displayName = outName,
            verificationReport = report
          )
        } finally {
          temp.delete()
        }
      }
    }

  private data class StreamStripOutcome(
    val strippedOperatorsCount: Int,
    val modifiedImagesCount: Int,
    val encounteredComplexFormXObject: Boolean
  )

  private fun stripContentStreamRegions(
    doc: PDDocument,
    page: PDPage,
    pageOps: List<PrivacyOperation>
  ): StreamStripOutcome {
    val targetRects = pageOps.map { it.rect.normalized() }
    val parser = PDFStreamParser(page)
    parser.parse()
    val tokens = parser.tokens

    // First pass: run PDFTextStripper-based operator indexer over the page to identify which
    // top-level Tj / TJ / ' / " operator occurrences intersect targetRects.
    val opDecisions = mutableMapOf<Int, TextOperatorAction>()
    var encounteredForm = false

    val box = page.cropBox ?: page.mediaBox
    val pageW = box.width.takeIf { it > 0f } ?: 595f
    val pageH = box.height.takeIf { it > 0f } ?: 842f

    val tracker = object : PDFTextStripper() {
      private var topOpIdx = 0
      private var formDepth = 0
      private val capturedGlyphs = mutableListOf<TextPosition>()

      init {
        startPage = 1
        endPage = 1
        sortByPosition = false
      }

      override fun processTextPosition(text: TextPosition) {
        if (formDepth == 0) {
          capturedGlyphs.add(text)
        }
        super.processTextPosition(text)
      }

      override fun processOperator(operator: Operator, operands: MutableList<COSBase>) {
        val opName = operator.name
        if (formDepth == 0) {
          val myIdx = topOpIdx++
          val isTextShow = opName == "Tj" || opName == "TJ" || opName == "'" || opName == "\""
          if (isTextShow) {
            capturedGlyphs.clear()
            super.processOperator(operator, operands)
            val glyphs = capturedGlyphs.toList()
            if (glyphs.isNotEmpty()) {
              val redactedFlags = glyphs.map { g ->
                val gx = g.xDirAdj
                val gy = g.yDirAdj
                val gw = g.widthDirAdj.coerceAtLeast(1f)
                val gh = g.heightDir.coerceAtLeast(4f)
                val gRect = NormRect(
                  left = (gx / pageW).coerceIn(0f, 1f),
                  top = ((gy - gh * 1.0f) / pageH).coerceIn(0f, 1f),
                  right = ((gx + gw) / pageW).coerceIn(0f, 1f),
                  bottom = ((gy + gh * 0.2f) / pageH).coerceIn(0f, 1f)
                ).normalized()
                targetRects.any { r -> r.intersects(gRect, padding = 0.002f) }
              }
              val anyRedacted = redactedFlags.any { it }
              if (anyRedacted) {
                val allRedacted = redactedFlags.all { it }
                opDecisions[myIdx] = if (allRedacted) {
                  TextOperatorAction.DropAll
                } else {
                  TextOperatorAction.FilterGlyphs(redactedFlags, glyphs)
                }
              }
            }
            return
          }
          if (opName == "Do") {
            val xObjName = (operands.firstOrNull() as? COSName)
            val xObj = xObjName?.let { runCatching { page.resources?.getXObject(it) }.getOrNull() }
            if (xObj is PDFormXObject) {
              encounteredForm = true
              formDepth++
              super.processOperator(operator, operands)
              formDepth--
              return
            }
          }
        }
        super.processOperator(operator, operands)
      }
    }

    // Run tracker using a single-page temporary wrapper so PDFTextStripper reads just this page
    PDDocument().use { singleWrapper ->
      singleWrapper.importPage(page)
      tracker.writeText(singleWrapper, StringWriter())
    }

    // Also inspect embedded PDImageXObjects in page resources; if image overlaps a redaction rect,
    // overwrite those image pixels directly in the underlying PDImageXObject!
    var modifiedImages = 0
    val imageBBoxes = findImageBoundingBoxes(page, pageW, pageH)
    imageBBoxes.forEach { (cosName, imgNormRect) ->
      val overlappingOps = pageOps.filter { it.rect.normalized().intersects(imgNormRect, padding = 0.002f) }
      if (overlappingOps.isNotEmpty()) {
        val xObj = runCatching { page.resources?.getXObject(cosName) }.getOrNull()
        if (xObj is PDImageXObject) {
          val srcBmp = runCatching { xObj.image }.getOrNull()
          if (srcBmp != null) {
            val mutable = srcBmp.copy(Bitmap.Config.ARGB_8888, true)
            if (srcBmp != mutable) srcBmp.recycle()
            val mappedOps = overlappingOps.map { op ->
              val r = op.rect.normalized()
              val mappedRect = NormRect(
                left = ((r.left - imgNormRect.left) / imgNormRect.width.coerceAtLeast(0.0001f)).coerceIn(0f, 1f),
                top = ((r.top - imgNormRect.top) / imgNormRect.height.coerceAtLeast(0.0001f)).coerceIn(0f, 1f),
                right = ((r.right - imgNormRect.left) / imgNormRect.width.coerceAtLeast(0.0001f)).coerceIn(0f, 1f),
                bottom = ((r.bottom - imgNormRect.top) / imgNormRect.height.coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
              )
              op.copy(rect = mappedRect, shape = PrivacySelectionShape.RECTANGLE)
            }
            imagePrivacyEngine.applyOperationsToBitmap(mutable, mappedOps)
            val newPdImg = LosslessFactory.createFromImage(doc, mutable)
            page.resources?.put(cosName, newPdImg)
            mutable.recycle()
            modifiedImages++
          }
        }
      }
    }

    // Now rewrite the flat token list according to opDecisions
    val newTokens = mutableListOf<Any>()
    val operandBuffer = mutableListOf<COSBase>()
    var opIdx = 0
    var strippedCount = 0

    for (tok in tokens) {
      if (tok is Operator) {
        val opName = tok.name
        val decision = opDecisions[opIdx]
        val isTextShow = opName == "Tj" || opName == "TJ" || opName == "'" || opName == "\""
        if (isTextShow && decision != null) {
          strippedCount++
          when (decision) {
            is TextOperatorAction.DropAll -> {
              if (opName == "'" || opName == "\"") {
                // Preserve line movement without drawing any text
                newTokens.add(Operator.getOperator("T*"))
              }
              // Drop operandBuffer and text operator completely
            }
            is TextOperatorAction.FilterGlyphs -> {
              if (opName == "'" || opName == "\"") {
                newTokens.add(Operator.getOperator("T*"))
              }
              val rewritten = rewriteTextOperands(opName, operandBuffer, decision)
              if (rewritten != null) {
                newTokens.add(rewritten.first)
                newTokens.add(rewritten.second)
              }
            }
          }
        } else {
          newTokens.addAll(operandBuffer)
          newTokens.add(tok)
        }
        operandBuffer.clear()
        opIdx++
      } else if (tok is COSBase) {
        operandBuffer.add(tok)
      }
    }
    if (operandBuffer.isNotEmpty()) {
      newTokens.addAll(operandBuffer)
    }

    val updatedStream = PDStream(doc)
    updatedStream.createOutputStream().use { out ->
      ContentStreamWriter(out).writeTokens(newTokens)
    }
    page.setContents(updatedStream)

    return StreamStripOutcome(
      strippedOperatorsCount = strippedCount,
      modifiedImagesCount = modifiedImages,
      encounteredComplexFormXObject = encounteredForm
    )
  }

  private sealed interface TextOperatorAction {
    data object DropAll : TextOperatorAction
    data class FilterGlyphs(
      val isRedacted: List<Boolean>,
      val glyphs: List<TextPosition>
    ) : TextOperatorAction
  }

  private fun rewriteTextOperands(
    opName: String,
    operands: List<COSBase>,
    action: TextOperatorAction.FilterGlyphs
  ): Pair<COSBase, Operator>? {
    val glyphs = action.glyphs
    val flags = action.isRedacted
    if (glyphs.isEmpty() || flags.all { it }) return null

    // For Tj / ' / ", operand is the last COSString
    if (opName == "Tj" || opName == "'" || opName == "\"") {
      val cosStr = operands.lastOrNull() as? COSString ?: return null
      val bytes = cosStr.bytes
      if (bytes.size == glyphs.size) {
        val tjArr = COSArray()
        var i = 0
        while (i < glyphs.size) {
          if (flags[i]) {
            var redactedAdvance = 0f
            while (i < glyphs.size && flags[i]) {
              redactedAdvance += computeTjAdvanceUnits(glyphs[i])
              i++
            }
            if (redactedAdvance != 0f) {
              tjArr.add(COSFloat(-redactedAdvance))
            }
          } else {
            val chunkStart = i
            while (i < glyphs.size && !flags[i]) i++
            val slice = bytes.copyOfRange(chunkStart, i)
            tjArr.add(COSString(slice))
          }
        }
        return if (tjArr.size() == 0) null else tjArr to Operator.getOperator("TJ")
      }
      // Multi-byte fallback: omit entire string if unsafe to split raw bytes
      return null
    }

    // For TJ array, walk COSString and COSNumber items tracking glyph cursor
    if (opName == "TJ") {
      val arr = operands.lastOrNull() as? COSArray ?: return null
      val outArr = COSArray()
      var gCursor = 0
      for (item in arr) {
        when (item) {
          is COSString -> {
            val b = item.bytes
            if (gCursor + b.size <= glyphs.size) {
              var i = 0
              while (i < b.size) {
                val gIdx = gCursor + i
                if (flags[gIdx]) {
                  var adv = 0f
                  while (i < b.size && flags[gCursor + i]) {
                    adv += computeTjAdvanceUnits(glyphs[gCursor + i])
                    i++
                  }
                  if (adv != 0f) outArr.add(COSFloat(-adv))
                } else {
                  val start = i
                  while (i < b.size && !flags[gCursor + i]) i++
                  outArr.add(COSString(b.copyOfRange(start, i)))
                }
              }
              gCursor += b.size
            } else {
              // Multi-byte or unmatched length: drop string if any overlapping glyph is redacted
              val remainingFlags = flags.drop(gCursor)
              if (remainingFlags.any { it }) {
                return null
              } else {
                outArr.add(item)
              }
            }
          }
          is COSNumber -> outArr.add(item)
          else -> outArr.add(item)
        }
      }
      return if (outArr.size() == 0) null else outArr to Operator.getOperator("TJ")
    }
    return null
  }

  private fun computeTjAdvanceUnits(tp: TextPosition): Float {
    val fs = (tp.fontSizeInPt.takeIf { it > 0f } ?: tp.fontSize.takeIf { it > 0f } ?: 12f)
    return (tp.widthDirAdj / fs) * 1000f
  }

  private fun findImageBoundingBoxes(page: PDPage, pageW: Float, pageH: Float): Map<COSName, NormRect> {
    val result = mutableMapOf<COSName, NormRect>()
    val parser = PDFStreamParser(page)
    parser.parse()
    val tokens = parser.tokens
    val matrixStack = ArrayDeque<com.tom_roush.pdfbox.util.Matrix>()
    var ctm = com.tom_roush.pdfbox.util.Matrix()
    val operands = mutableListOf<COSBase>()
    val box = page.cropBox ?: page.mediaBox
    val ox = box.lowerLeftX
    val oy = box.lowerLeftY

    for (tok in tokens) {
      if (tok is Operator) {
        when (tok.name) {
          "q" -> matrixStack.addLast(ctm.clone())
          "Q" -> if (matrixStack.isNotEmpty()) ctm = matrixStack.removeLast()
          "cm" -> {
            if (operands.size >= 6) {
              val a = (operands[0] as? COSNumber)?.floatValue() ?: 1f
              val b = (operands[1] as? COSNumber)?.floatValue() ?: 0f
              val c = (operands[2] as? COSNumber)?.floatValue() ?: 0f
              val d = (operands[3] as? COSNumber)?.floatValue() ?: 1f
              val e = (operands[4] as? COSNumber)?.floatValue() ?: 0f
              val f = (operands[5] as? COSNumber)?.floatValue() ?: 0f
              val m = com.tom_roush.pdfbox.util.Matrix(a, b, c, d, e, f)
              ctm = com.tom_roush.pdfbox.util.Matrix.concatenate(m, ctm)
            }
          }
          "Do" -> {
            val name = operands.firstOrNull() as? COSName
            if (name != null) {
              val p0 = ctm.transformPoint(0f, 0f)
              val p1 = ctm.transformPoint(1f, 1f)
              val p2 = ctm.transformPoint(0f, 1f)
              val p3 = ctm.transformPoint(1f, 0f)
              val xs = listOf(p0.x, p1.x, p2.x, p3.x)
              val ys = listOf(p0.y, p1.y, p2.y, p3.y)
              val minX = ((xs.minOrNull() ?: 0f) - ox) / pageW
              val maxX = ((xs.maxOrNull() ?: 0f) - ox) / pageW
              val minPdfY = ((ys.minOrNull() ?: 0f) - oy) / pageH
              val maxPdfY = ((ys.maxOrNull() ?: 0f) - oy) / pageH
              result[name] = NormRect(
                left = minX.coerceIn(0f, 1f),
                top = (1f - maxPdfY).coerceIn(0f, 1f),
                right = maxX.coerceIn(0f, 1f),
                bottom = (1f - minPdfY).coerceIn(0f, 1f)
              ).normalized()
            }
          }
        }
        operands.clear()
      } else if (tok is COSBase) {
        operands.add(tok)
      }
    }
    return result
  }

  private fun drawPrivacyOverlaysOnPage(
    doc: PDDocument,
    page: PDPage,
    pageOps: List<PrivacyOperation>
  ) {
    val box = page.cropBox ?: page.mediaBox
    val pw = box.width
    val ph = box.height
    val ox = box.lowerLeftX
    val oy = box.lowerLeftY

    PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
      pageOps.forEach { op ->
        val nr = op.rect.normalized()
        val x = ox + nr.left * pw
        val y = oy + (1f - nr.bottom) * ph
        val w = (nr.width * pw).coerceAtLeast(2f)
        val h = (nr.height * ph).coerceAtLeast(2f)

        when (op.mode) {
          PrivacyEditMode.PERMANENT_REDACTION -> {
            val c = op.fillColorArgb
            cs.saveGraphicsState()
            cs.setNonStrokingColor(Color.red(c), Color.green(c), Color.blue(c))
            if (op.shape == PrivacySelectionShape.FREEHAND && op.points.size >= 3) {
              val first = op.points.first()
              cs.moveTo(ox + first.x * pw, oy + (1f - first.y) * ph)
              for (i in 1 until op.points.size) {
                val pt = op.points[i]
                cs.lineTo(ox + pt.x * pw, oy + (1f - pt.y) * ph)
              }
              cs.closePath()
              cs.fill()
            } else {
              cs.addRect(x, y, w, h)
              cs.fill()
            }
            if (op.optionalLabel.isNotBlank()) {
              val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
              val tc = if (lum > 135) Color.BLACK else Color.WHITE
              drawPdfBoxCenteredText(cs, op.optionalLabel, x, y, w, h, 10f, PDType1Font.HELVETICA_BOLD, tc, TextAlignOption.CENTER)
            }
            cs.restoreGraphicsState()
          }
          PrivacyEditMode.REPLACE_ANONYMISE -> {
            val cfg = op.replacementConfig
            val bg = cfg.backgroundColorArgb
            cs.saveGraphicsState()
            cs.setNonStrokingColor(Color.red(bg), Color.green(bg), Color.blue(bg))
            cs.addRect(x, y, w, h)
            cs.fill()
            val font = when (cfg.fontStyle) {
              ReplacementFontStyle.SANS -> PDType1Font.HELVETICA_BOLD
              ReplacementFontStyle.SERIF -> PDType1Font.TIMES_BOLD
              ReplacementFontStyle.MONO -> PDType1Font.COURIER_BOLD
            }
            drawPdfBoxCenteredText(
              cs = cs,
              rawText = cfg.text.ifBlank { "[REDACTED]" },
              x = x,
              y = y,
              w = w,
              h = h,
              requestedSize = cfg.fontSizeSp,
              font = font,
              textColorArgb = cfg.textColorArgb,
              align = cfg.alignment
            )
            cs.restoreGraphicsState()
          }
          PrivacyEditMode.BLUR_PIXELATE -> Unit // Handled by rasterisation path
        }
      }
    }
  }

  private fun drawPdfBoxCenteredText(
    cs: PDPageContentStream,
    rawText: String,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    requestedSize: Float,
    font: PDType1Font,
    textColorArgb: Int,
    align: TextAlignOption
  ) {
    val safe = rawText.replace(Regex("[^\\x20-\\x7E]"), "?")
    var size = requestedSize.coerceIn(6f, max(7f, h * 0.78f))
    val textW = runCatching { (font.getStringWidth(safe) / 1000f) * size }.getOrDefault(w * 0.8f)
    if (textW > w - 4f && textW > 0f) {
      size = (size * ((w - 4f) / textW)).coerceAtLeast(5.5f)
    }
    val finalW = runCatching { (font.getStringWidth(safe) / 1000f) * size }.getOrDefault(w * 0.5f)
    val tx = when (align) {
      TextAlignOption.LEFT -> x + 2f
      TextAlignOption.CENTER -> x + ((w - finalW) / 2f).coerceAtLeast(1f)
      TextAlignOption.RIGHT -> (x + w - finalW - 2f).coerceAtLeast(x + 1f)
    }
    val ty = y + ((h - size * 0.72f) / 2f).coerceAtLeast(1f)
    cs.beginText()
    cs.setFont(font, size)
    cs.setNonStrokingColor(Color.red(textColorArgb), Color.green(textColorArgb), Color.blue(textColorArgb))
    cs.newLineAtOffset(tx, ty)
    cs.showText(safe)
    cs.endText()
  }

  private fun cleanPageAnnotations(
    page: PDPage,
    pageOps: List<PrivacyOperation>,
    stripAllAnnotations: Boolean
  ) {
    val annots = runCatching { page.annotations }.getOrNull() ?: return
    if (stripAllAnnotations) {
      page.annotations = emptyList()
      return
    }
    if (pageOps.isEmpty()) return
    val box = page.cropBox ?: page.mediaBox
    val pw = box.width.takeIf { it > 0f } ?: 595f
    val ph = box.height.takeIf { it > 0f } ?: 842f
    val ox = box.lowerLeftX
    val oy = box.lowerLeftY
    val targetRects = pageOps.map { it.rect.normalized() }
    val kept = annots.filter { annot ->
      val r = annot.rectangle ?: return@filter true
      val norm = NormRect(
        left = ((r.lowerLeftX - ox) / pw).coerceIn(0f, 1f),
        top = (1f - ((r.upperRightY - oy) / ph)).coerceIn(0f, 1f),
        right = ((r.upperRightX - ox) / pw).coerceIn(0f, 1f),
        bottom = (1f - ((r.lowerLeftY - oy) / ph)).coerceIn(0f, 1f)
      ).normalized()
      targetRects.none { it.intersects(norm, padding = 0.01f) }
    }
    page.annotations = kept
  }

  private fun sanitizeDocumentMetadata(doc: PDDocument) {
    val info = doc.documentInformation
    if (info != null) {
      info.metadataKeys.toList().forEach { key -> info.setCustomMetadataValue(key, null) }
      info.title = null
      info.author = null
      info.subject = null
      info.keywords = null
      info.creator = null
      info.producer = null
      info.creationDate = null
      info.modificationDate = null
    }
    doc.documentCatalog?.metadata = null
  }

  fun verifyExportedPdf(
    outputFile: File,
    displayName: String,
    originalBytes: Long,
    config: PdfPrivacyConfig,
    rasterisedPages: Set<Int> = emptySet(),
    complexXObjectWarning: Boolean = false
  ): PrivacyVerificationReport {
    val checks = mutableListOf<PrivacyVerificationCheck>()
    val warnings = mutableListOf<String>()

    if (!outputFile.exists() || outputFile.length() == 0L) {
      checks.add(PrivacyVerificationCheck("Exported PDF file", VerificationStatus.REMOVAL_FAILED, "Output PDF missing."))
      return PrivacyVerificationReport(outputFile, displayName, originalBytes, 0L, "Verification failed", checks, false)
    }

    PDDocument.load(outputFile).use { exportedDoc ->
      // 1. Verify full document text extraction against every redacted/replaced string
      val allExtractedText = runCatching { PDFTextStripper().getText(exportedDoc) }.getOrDefault("")
      val normalizedText = allExtractedText.lowercase().replace(Regex("\\s+"), " ")
      val protectedTerms = config.operations
        .filter { it.mode != PrivacyEditMode.BLUR_PIXELATE && it.sourceTextToVerify.isNotBlank() }
        .map { it.sourceTextToVerify.trim() }
        .distinct()

      if (protectedTerms.isNotEmpty()) {
        val leaked = protectedTerms.filter { term ->
          normalizedText.contains(term.lowercase().replace(Regex("\\s+"), " "))
        }
        val status = if (leaked.isEmpty()) VerificationStatus.REMOVED_AND_VERIFIED else VerificationStatus.REMOVAL_FAILED
        checks.add(
          PrivacyVerificationCheck(
            itemName = "Redacted text strings (${protectedTerms.size})",
            status = status,
            detail = if (leaked.isEmpty()) {
              "0/${protectedTerms.size} protected strings found in extracted PDF text."
            } else {
              "${leaked.size} string(s) still detected in text extraction; consider Safe Page Rasterisation."
            }
          )
        )
      }

      // 2. Verify geometric regions on each page contain no remaining text positions from original content
      val geometricOps = config.operations.filter {
        it.mode != PrivacyEditMode.BLUR_PIXELATE && it.sourceTextToVerify.isBlank()
      }
      if (geometricOps.isNotEmpty()) {
        var remainingInRegions = 0
        geometricOps.groupBy { it.pageIndex }.forEach { (pIdx, ops) ->
          val words = detector.extractPageWords(exportedDoc, pIdx)
          ops.forEach { op ->
            val placeholder = op.replacementConfig.text.trim()
            val hits = words.filter { w ->
              op.rect.intersects(w.rect) && (op.mode != PrivacyEditMode.REPLACE_ANONYMISE || !placeholder.contains(w.text, ignoreCase = true))
            }
            remainingInRegions += hits.size
          }
        }
        checks.add(
          PrivacyVerificationCheck(
            itemName = "Manual redaction regions (${geometricOps.size})",
            status = when {
              remainingInRegions == 0 && !complexXObjectWarning -> VerificationStatus.REMOVED_AND_VERIFIED
              remainingInRegions == 0 && complexXObjectWarning -> VerificationStatus.REMOVAL_ATTEMPTED_UNVERIFIED
              else -> VerificationStatus.REMOVAL_FAILED
            },
            detail = if (remainingInRegions == 0) {
              "No underlying text spans detected inside redacted bounds."
            } else {
              "$remainingInRegions text token(s) still present inside redacted bounds."
            }
          )
        )
      }

      // 3. Metadata check
      val info = exportedDoc.documentInformation
      val hasMeta = !info?.title.isNullOrBlank() || !info?.author.isNullOrBlank() ||
        !info?.creator.isNullOrBlank() || !info?.producer.isNullOrBlank() ||
        exportedDoc.documentCatalog?.metadata != null
      checks.add(
        PrivacyVerificationCheck(
          itemName = "Document & XMP metadata",
          status = when {
            !config.stripMetadata && hasMeta -> VerificationStatus.RETAINED_INTENTIONALLY
            !hasMeta && config.stripMetadata -> VerificationStatus.REMOVED_AND_VERIFIED
            !hasMeta -> VerificationStatus.NOT_DETECTED
            else -> VerificationStatus.REMOVAL_FAILED
          },
          detail = if (!hasMeta) "No title, author, producer or XMP metadata present." else "Metadata fields present."
        )
      )

      // 4. Annotations, AcroForm, Attachments, Layers
      val annotCount = exportedDoc.pages.sumOf { runCatching { it.annotations.size }.getOrDefault(0) }
      checks.add(
        PrivacyVerificationCheck(
          itemName = "PDF annotations & comments",
          status = when {
            annotCount == 0 && config.stripAnnotations -> VerificationStatus.REMOVED_AND_VERIFIED
            annotCount == 0 -> VerificationStatus.NOT_DETECTED
            !config.stripAnnotations -> VerificationStatus.RETAINED_INTENTIONALLY
            else -> VerificationStatus.REMOVAL_FAILED
          },
          detail = if (annotCount == 0) "0 annotations retained." else "$annotCount annotation(s) retained."
        )
      )

      val formFields = runCatching { exportedDoc.documentCatalog?.acroForm?.fields?.size ?: 0 }.getOrDefault(0)
      checks.add(
        PrivacyVerificationCheck(
          itemName = "AcroForm fields",
          status = when {
            formFields == 0 && config.stripFormFields -> VerificationStatus.REMOVED_AND_VERIFIED
            formFields == 0 -> VerificationStatus.NOT_DETECTED
            else -> VerificationStatus.RETAINED_INTENTIONALLY
          },
          detail = if (formFields == 0) "No form field values present." else "$formFields field(s) retained."
        )
      )

      val attachments = runCatching { exportedDoc.documentCatalog?.names?.embeddedFiles?.names?.size ?: 0 }.getOrDefault(0)
      checks.add(
        PrivacyVerificationCheck(
          itemName = "Embedded file attachments",
          status = when {
            attachments == 0 && config.stripAttachments -> VerificationStatus.REMOVED_AND_VERIFIED
            attachments == 0 -> VerificationStatus.NOT_DETECTED
            else -> VerificationStatus.RETAINED_INTENTIONALLY
          },
          detail = if (attachments == 0) "0 embedded attachments." else "$attachments attachment(s) retained."
        )
      )

      checks.add(
        PrivacyVerificationCheck(
          itemName = "Incremental PDF revision history",
          status = VerificationStatus.REMOVED_AND_VERIFIED,
          detail = "Saved via full non-incremental document rewrite."
        )
      )

      if (rasterisedPages.isNotEmpty()) {
        warnings.add(
          "Page(s) ${rasterisedPages.sorted().joinToString(", ") { (it + 1).toString() }} were rasterised to images; text selection and vector crispness were removed on those pages."
        )
      }
      if (complexXObjectWarning) {
        warnings.add("Complex Form XObjects were detected; use Safe Page Rasterisation if visual graphics inside nested forms require guaranteed removal.")
      }
      if (config.operations.any { it.mode == PrivacyEditMode.BLUR_PIXELATE }) {
        warnings.add("Blur & pixelation provide visual concealment only, not high-security permanent redaction.")
      }

      val safe = checks.none { it.status == VerificationStatus.REMOVAL_FAILED || it.status == VerificationStatus.REMOVAL_ATTEMPTED_UNVERIFIED }
      val summary = if (safe) {
        "Verified ${config.operations.size} privacy action(s) across ${exportedDoc.numberOfPages} page(s)."
      } else {
        "Verification flagged items requiring attention before sharing."
      }
      return PrivacyVerificationReport(
        outputFile = outputFile,
        displayName = displayName,
        originalBytes = originalBytes,
        outputBytes = outputFile.length(),
        summaryText = summary,
        checks = checks,
        verifiedSafeToShare = safe,
        warnings = warnings
      )
    }
  }

  private fun renderSinglePageFromFile(pdfFile: File, pageIndex: Int): Bitmap? = runCatching {
    ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
      PdfRenderer(pfd).use { renderer ->
        if (pageIndex !in 0 until renderer.pageCount) return null
        renderer.openPage(pageIndex).use { page ->
          val targetW = 1440
          val aspect = page.height.toFloat() / page.width.coerceAtLeast(1)
          val targetH = (targetW * aspect).toInt().coerceIn(200, 3000)
          val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
          bmp.eraseColor(Color.WHITE)
          page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
          bmp
        }
      }
    }
  }.getOrNull()

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
    } ?: error("Cannot read PDF stream.")
    return out to totalBytes
  }

  private fun queryName(uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }
  }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
