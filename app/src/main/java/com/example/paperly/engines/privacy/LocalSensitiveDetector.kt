package com.example.paperly.engines.privacy

import android.graphics.Bitmap
import android.media.FaceDetector
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.StringWriter
import kotlin.math.max
import kotlin.math.min

class LocalSensitiveDetector {

  private val emailRegex = Regex("""\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b""")
  private val phoneRegex = Regex("""(?<!\w)(?:\+?\d{1,3}[\s.\-]?)?(?:\(?\d{2,4}\)?[\s.\-])?\d{3,4}[\s.\-]\d{4}(?!\w)""")
  private val ssnIdRegex = Regex("""\b\d{3}[\s\-]\d{2}[\s\-]\d{4}\b|\b[A-Z]{2}\d{6,9}\b""")
  private val accountCardRegex = Regex("""\b(?:\d{4}[\s\-]?){3,4}\d{2,4}\b|\b\d{10,16}\b""")
  private val addressRegex = Regex(
    """\b\d{1,5}\s+[A-Za-z0-9.'\-]+(?:\s+[A-Za-z0-9.'\-]+){0,3}\s+(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Court|Ct|Way|Place|Pl)\b\.?""",
    RegexOption.IGNORE_CASE
  )

  data class PageCharStream(
    val fullText: String,
    val charBoxes: List<NormRect?>
  )

  fun extractPageCharStream(doc: PDDocument, pageIndex: Int): PageCharStream {
    if (pageIndex < 0 || pageIndex >= doc.numberOfPages) {
      return PageCharStream("", emptyList())
    }
    val sb = StringBuilder()
    val boxes = mutableListOf<NormRect?>()

    val stripper = object : PDFTextStripper() {
      init {
        startPage = pageIndex + 1
        endPage = pageIndex + 1
        sortByPosition = true
      }

      override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
        if (text == null || textPositions == null) return
        val maxIdx = min(text.length, textPositions.size)
        for (i in 0 until maxIdx) {
          val ch = text[i]
          val tp = textPositions[i]
          val pw = tp.pageWidth.takeIf { it > 0f } ?: 595f
          val ph = tp.pageHeight.takeIf { it > 0f } ?: 842f
          val x = tp.xDirAdj
          val yBaseline = tp.yDirAdj
          val w = tp.widthDirAdj.coerceAtLeast(1f)
          val h = tp.heightDir.coerceAtLeast(4f)
          val norm = NormRect(
            left = (x / pw).coerceIn(0f, 1f),
            top = ((yBaseline - h * 1.05f) / ph).coerceIn(0f, 1f),
            right = ((x + w) / pw).coerceIn(0f, 1f),
            bottom = ((yBaseline + h * 0.25f) / ph).coerceIn(0f, 1f)
          ).normalized()
          sb.append(ch)
          boxes.add(norm)
        }
        if (text.length > textPositions.size) {
          for (i in textPositions.size until text.length) {
            sb.append(text[i])
            boxes.add(null)
          }
        }
      }

      override fun writeWordSeparator() {
        sb.append(' ')
        boxes.add(null)
      }

      override fun writeLineSeparator() {
        sb.append('\n')
        boxes.add(null)
      }
    }

    stripper.writeText(doc, StringWriter())
    return PageCharStream(sb.toString(), boxes)
  }

  fun extractPageWords(doc: PDDocument, pageIndex: Int): List<PdfPageWordBox> {
    val stream = extractPageCharStream(doc, pageIndex)
    if (stream.fullText.isBlank()) return emptyList()
    val words = mutableListOf<PdfPageWordBox>()
    val tokenRegex = Regex("""\S+""")
    tokenRegex.findAll(stream.fullText).forEach { m ->
      val rect = mergeBoxesForRange(stream.charBoxes, m.range.first, m.range.last + 1)
      if (rect != null) {
        words.add(PdfPageWordBox(text = m.value, rect = rect))
      }
    }
    return words
  }

  fun searchInDocument(doc: PDDocument, query: String): List<PdfSearchOccurrence> {
    val clean = query.trim()
    if (clean.isEmpty()) return emptyList()
    val results = mutableListOf<PdfSearchOccurrence>()
    for (p in 0 until doc.numberOfPages) {
      val stream = extractPageCharStream(doc, p)
      if (stream.fullText.isBlank()) continue
      val lowerText = stream.fullText.lowercase()
      val lowerQ = clean.lowercase()
      var searchFrom = 0
      var occurrenceIndex = 0
      while (true) {
        val idx = lowerText.indexOf(lowerQ, searchFrom)
        if (idx < 0) break
        val endIdx = idx + clean.length
        val lineRects = splitBoxesByLine(stream.charBoxes, idx, endIdx)
        val snippetStart = (idx - 28).coerceAtLeast(0)
        val snippetEnd = (endIdx + 40).coerceAtMost(stream.fullText.length)
        val snippet = stream.fullText.substring(snippetStart, snippetEnd).replace('\n', ' ').trim()
        val matchedExact = stream.fullText.substring(idx, endIdx)
        if (lineRects.isNotEmpty()) {
          results.add(
            PdfSearchOccurrence(
              id = "occ_${p}_${idx}_$occurrenceIndex",
              pageIndex = p,
              matchedText = matchedExact,
              snippet = snippet,
              rects = lineRects,
              selected = false
            )
          )
        }
        occurrenceIndex++
        searchFrom = endIdx
      }
    }
    return results
  }

  fun detectSensitiveInPdf(doc: PDDocument, customKeywords: List<String> = emptyList()): List<SensitiveSuggestion> {
    val suggestions = mutableListOf<SensitiveSuggestion>()
    val seenKeys = mutableSetOf<String>()

    fun addMatches(
      regex: Regex,
      category: SensitiveCategory,
      preset: PlaceholderPreset,
      pageIndex: Int,
      stream: PageCharStream
    ) {
      regex.findAll(stream.fullText).forEachIndexed { idx, match ->
        val raw = match.value.trim()
        if (raw.length < 3) return@forEachIndexed
        val box = mergeBoxesForRange(stream.charBoxes, match.range.first, match.range.last + 1) ?: return@forEachIndexed
        val dedupeKey = "$pageIndex|${raw.lowercase()}|${(box.left * 100).toInt()}|${(box.top * 100).toInt()}"
        if (!seenKeys.add(dedupeKey)) return@forEachIndexed
        suggestions.add(
          SensitiveSuggestion(
            id = "sug_${category.name}_${pageIndex}_${match.range.first}_$idx",
            category = category,
            pageIndex = pageIndex,
            rect = box,
            matchedText = raw,
            displayHint = maskForPreview(raw, category),
            recommendedPlaceholder = preset
          )
        )
      }
    }

    val cleanKeywords = customKeywords.map { it.trim() }.filter { it.length >= 2 }
    for (p in 0 until doc.numberOfPages) {
      val stream = extractPageCharStream(doc, p)
      if (stream.fullText.isBlank()) continue
      addMatches(emailRegex, SensitiveCategory.EMAIL, PlaceholderPreset.EMAIL, p, stream)
      addMatches(ssnIdRegex, SensitiveCategory.ID_NUMBER, PlaceholderPreset.PASSWORD, p, stream)
      addMatches(accountCardRegex, SensitiveCategory.ACCOUNT_NUMBER, PlaceholderPreset.ACCOUNT, p, stream)
      addMatches(phoneRegex, SensitiveCategory.PHONE, PlaceholderPreset.PHONE, p, stream)
      addMatches(addressRegex, SensitiveCategory.ADDRESS, PlaceholderPreset.PASSWORD, p, stream)

      cleanKeywords.forEach { kw ->
        val kwRegex = Regex(Regex.escape(kw), RegexOption.IGNORE_CASE)
        addMatches(kwRegex, SensitiveCategory.CUSTOM_KEYWORD, PlaceholderPreset.PASSWORD, p, stream)
      }
    }
    return suggestions
  }

  fun detectFacesInBitmap(bitmap: Bitmap): List<SensitiveSuggestion> {
    return runCatching {
      val maxDim = 960f
      val scale = min(1f, maxDim / max(bitmap.width, bitmap.height).coerceAtLeast(1))
      val detW = ((bitmap.width * scale).toInt() and 0xFFFFFFFE.toInt()).coerceAtLeast(16)
      val detH = (bitmap.height * scale).toInt().coerceAtLeast(16)
      val scaled = Bitmap.createScaledBitmap(bitmap, detW, detH, true)
      val rgb565 = scaled.copy(Bitmap.Config.RGB_565, false)
      if (scaled != bitmap) scaled.recycle()

      val maxFaces = 8
      val faces = arrayOfNulls<FaceDetector.Face>(maxFaces)
      val detector = FaceDetector(detW, detH, maxFaces)
      val count = detector.findFaces(rgb565, faces)
      rgb565.recycle()

      val results = mutableListOf<SensitiveSuggestion>()
      val mid = android.graphics.PointF()
      for (i in 0 until count) {
        val f = faces[i] ?: continue
        if (f.confidence() < 0.35f) continue
        f.getMidPoint(mid)
        val eyeDist = f.eyesDistance().coerceAtLeast(detW * 0.03f)
        val halfW = eyeDist * 1.45f
        val topExpand = eyeDist * 1.35f
        val botExpand = eyeDist * 1.85f
        val rect = NormRect(
          left = ((mid.x - halfW) / detW).coerceIn(0f, 1f),
          top = ((mid.y - topExpand) / detH).coerceIn(0f, 1f),
          right = ((mid.x + halfW) / detW).coerceIn(0f, 1f),
          bottom = ((mid.y + botExpand) / detH).coerceIn(0f, 1f)
        ).normalized()
        results.add(
          SensitiveSuggestion(
            id = "face_$i",
            category = SensitiveCategory.FACE,
            pageIndex = 0,
            rect = rect,
            matchedText = "",
            displayHint = "Detected face region #${i + 1} (${(f.confidence() * 100).toInt()}%)",
            recommendedPlaceholder = PlaceholderPreset.PERSON
          )
        )
      }
      results
    }.getOrDefault(emptyList())
  }

  private fun maskForPreview(raw: String, category: SensitiveCategory): String {
    if (raw.length <= 4) return "${category.label}: ${raw.first()}***"
    val prefix = raw.take(2)
    val suffix = raw.takeLast(2)
    return "${category.label}: $prefix•••$suffix"
  }

  private fun mergeBoxesForRange(boxes: List<NormRect?>, start: Int, endExclusive: Int): NormRect? {
    var minL = 1f
    var minT = 1f
    var maxR = 0f
    var maxB = 0f
    var found = false
    for (i in start.coerceAtLeast(0) until endExclusive.coerceAtMost(boxes.size)) {
      val b = boxes[i] ?: continue
      found = true
      minL = min(minL, b.left)
      minT = min(minT, b.top)
      maxR = max(maxR, b.right)
      maxB = max(maxB, b.bottom)
    }
    return if (found) NormRect(minL, minT, maxR, maxB).normalized() else null
  }

  private fun splitBoxesByLine(boxes: List<NormRect?>, start: Int, endExclusive: Int): List<NormRect> {
    val lineGroups = mutableListOf<NormRect>()
    var cur: NormRect? = null
    for (i in start.coerceAtLeast(0) until endExclusive.coerceAtMost(boxes.size)) {
      val b = boxes[i] ?: continue
      val existing = cur
      if (existing == null) {
        cur = b
      } else {
        val midYCur = (existing.top + existing.bottom) / 2f
        val midYB = (b.top + b.bottom) / 2f
        val tol = max(existing.height, b.height) * 0.75f + 0.005f
        if (kotlin.math.abs(midYCur - midYB) <= tol) {
          cur = NormRect(
            left = min(existing.left, b.left),
            top = min(existing.top, b.top),
            right = max(existing.right, b.right),
            bottom = max(existing.bottom, b.bottom)
          )
        } else {
          lineGroups.add(existing)
          cur = b
        }
      }
    }
    cur?.let { lineGroups.add(it) }
    return lineGroups
  }
}
