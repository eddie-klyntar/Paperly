package com.example.paperly.engines.spreadsheet

import android.content.Context
import android.net.Uri
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

enum class CellAlign { LEFT, CENTER, RIGHT }

data class MergedRange(
  val startRow: Int,
  val startCol: Int,
  val endRow: Int,
  val endCol: Int
)

data class CellData(
  val row: Int,
  val col: Int,
  val displayText: String,
  val formula: String? = null,
  val rawValue: String? = null,
  val bgColorArgb: Long? = null,
  val textColorArgb: Long? = null,
  val isBold: Boolean = false,
  val isItalic: Boolean = false,
  val align: CellAlign = CellAlign.LEFT
)

data class WorksheetData(
  val name: String,
  val rows: List<List<CellData>>,
  val columnCount: Int,
  val columnWidthsDp: List<Float>,
  val rowHeightsDp: List<Float>,
  val mergedRanges: List<MergedRange>,
  val freezeRows: Int = 0,
  val freezeCols: Int = 0,
  val truncatedNotice: String? = null
)

data class SpreadsheetWorkbook(
  val fileName: String,
  val formatLabel: String,
  val worksheets: List<WorksheetData>,
  val limitationNotice: String? = null
)

data class CellSearchMatch(
  val sheetIndex: Int,
  val row: Int,
  val col: Int,
  val preview: String
)

class SpreadsheetEngine(private val context: Context) {

  companion object {
    private const val MAX_ROWS = 4000
    private const val MAX_COLS = 80
  }

  suspend fun loadWorkbook(uri: Uri, fileName: String): Result<SpreadsheetWorkbook> =
    withContext(Dispatchers.IO) {
      runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
          ?: error("Unable to read spreadsheet file.")
        if (bytes.isEmpty()) error("This file is empty.")

        val lower = fileName.lowercase()
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        val isOle2 = bytes.size >= 8 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() &&
          bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()

        when {
          isZip || lower.endsWith(".xlsx") -> parseXlsx(bytes, fileName)
          isOle2 || lower.endsWith(".xls") -> parseLegacyXls(bytes, fileName)
          lower.endsWith(".tsv") -> parseDelimited(bytes, fileName, '\t', "TSV")
          else -> parseDelimited(bytes, fileName, ',', "CSV")
        }
      }
    }

  private fun parseDelimited(
    bytes: ByteArray,
    fileName: String,
    delimiter: Char,
    formatLabel: String
  ): SpreadsheetWorkbook {
    // Detect UTF-8 BOM and strip if present
    val offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
    val text = String(bytes, offset, bytes.size - offset, Charsets.UTF_8)
    val parsedRows = mutableListOf<List<String>>()
    var currentRow = mutableListOf<String>()
    val cellBuf = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length && parsedRows.size < MAX_ROWS) {
      val ch = text[i]
      if (inQuotes) {
        if (ch == '"') {
          if (i + 1 < text.length && text[i + 1] == '"') {
            cellBuf.append('"')
            i++
          } else {
            inQuotes = false
          }
        } else {
          cellBuf.append(ch)
        }
      } else {
        when (ch) {
          '"' -> inQuotes = true
          delimiter -> {
            if (currentRow.size < MAX_COLS) currentRow.add(cellBuf.toString())
            cellBuf.clear()
          }
          '\r' -> {
            if (i + 1 < text.length && text[i + 1] == '\n') i++
            if (currentRow.size < MAX_COLS) currentRow.add(cellBuf.toString())
            cellBuf.clear()
            parsedRows.add(currentRow)
            currentRow = mutableListOf()
          }
          '\n' -> {
            if (currentRow.size < MAX_COLS) currentRow.add(cellBuf.toString())
            cellBuf.clear()
            parsedRows.add(currentRow)
            currentRow = mutableListOf()
          }
          else -> cellBuf.append(ch)
        }
      }
      i++
    }
    if ((cellBuf.isNotEmpty() || currentRow.isNotEmpty()) && parsedRows.size < MAX_ROWS) {
      if (currentRow.size < MAX_COLS) currentRow.add(cellBuf.toString())
      parsedRows.add(currentRow)
    }

    val colCount = parsedRows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
    val cells = parsedRows.mapIndexed { rIdx, rowList ->
      List(colCount) { cIdx ->
        val raw = rowList.getOrElse(cIdx) { "" }
        val isNum = raw.toDoubleOrNull() != null
        CellData(
          row = rIdx,
          col = cIdx,
          displayText = raw,
          rawValue = raw,
          isBold = rIdx == 0,
          align = if (isNum && rIdx > 0) CellAlign.RIGHT else CellAlign.LEFT
        )
      }
    }
    val widths = List(colCount) { c ->
      val maxLen = cells.take(60).maxOfOrNull { it[c].displayText.length } ?: 8
      (maxLen * 9f + 26f).coerceIn(88f, 240f)
    }
    val truncated = if (i < text.length) "Showing first $MAX_ROWS rows for smooth performance." else null
    return SpreadsheetWorkbook(
      fileName = fileName,
      formatLabel = formatLabel,
      worksheets = listOf(
        WorksheetData(
          name = fileName.substringBeforeLast('.').ifBlank { formatLabel },
          rows = cells,
          columnCount = colCount,
          columnWidthsDp = widths,
          rowHeightsDp = List(cells.size) { 38f },
          mergedRanges = emptyList(),
          freezeRows = if (cells.size > 1) 1 else 0,
          truncatedNotice = truncated
        )
      )
    )
  }

  private fun parseLegacyXls(bytes: ByteArray, fileName: String): SpreadsheetWorkbook {
    // Lightweight OLE2/BIFF8 text extraction fallback with honest limitation disclosure
    val extractedLines = mutableListOf<List<CellData>>()
    val asciiRuns = mutableListOf<String>()
    val sb = StringBuilder()
    for (b in bytes) {
      val ch = b.toInt() and 0xFF
      if (ch in 32..126) {
        sb.append(ch.toChar())
      } else {
        if (sb.length >= 3) {
          val candidate = sb.toString().trim()
          if (!candidate.startsWith("Root Entry") && !candidate.startsWith("Workbook") && !candidate.contains("Microsoft")) {
            asciiRuns.add(candidate)
          }
        }
        sb.clear()
      }
    }
    val previewRows = asciiRuns.distinct().take(120).chunked(4)
    previewRows.forEachIndexed { rIdx, chunk ->
      extractedLines += chunk.mapIndexed { cIdx, v ->
        CellData(row = rIdx, col = cIdx, displayText = v, rawValue = v)
      }
    }
    val colCount = extractedLines.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
    val normalized = extractedLines.mapIndexed { r, list ->
      List(colCount) { c -> list.getOrElse(c) { CellData(r, c, "") } }
    }
    return SpreadsheetWorkbook(
      fileName = fileName,
      formatLabel = "XLS (Legacy Binary)",
      worksheets = listOf(
        WorksheetData(
          name = "Extracted Strings",
          rows = normalized,
          columnCount = colCount,
          columnWidthsDp = List(colCount) { 150f },
          rowHeightsDp = List(normalized.size) { 38f },
          mergedRanges = emptyList()
        )
      ),
      limitationNotice = "Legacy binary .xls (BIFF8) has limited offline structural support. Re-save as .xlsx or .csv for full cell formatting, multi-sheet tabs, and accurate columns."
    )
  }

  private data class XfStyle(
    val numFmtId: Int = 0,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val bgArgb: Long? = null,
    val textArgb: Long? = null,
    val align: CellAlign? = null
  )

  private fun parseXlsx(bytes: ByteArray, fileName: String): SpreadsheetWorkbook {
    val zipEntries = mutableMapOf<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zis ->
      var entry = zis.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          zipEntries[entry.name.trimStart('/')] = zis.readBytes()
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }

    val sharedStrings = zipEntries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it.inputStream()) } ?: emptyList()
    val (customNumFmts, xfStyles) = zipEntries["xl/styles.xml"]?.let { parseStyles(it.inputStream()) }
      ?: (emptyMap<Int, String>() to emptyList())
    val rels = zipEntries["xl/_rels/workbook.xml.rels"]?.let { parseWorkbookRels(it.inputStream()) } ?: emptyMap()
    val sheetsMeta = zipEntries["xl/workbook.xml"]?.let { parseWorkbookSheets(it.inputStream()) }
      ?: emptyList()

    val worksheets = mutableListOf<WorksheetData>()
    val orderedSheetEntries = if (sheetsMeta.isNotEmpty()) {
      sheetsMeta.mapNotNull { (name, rId) ->
        val target = rels[rId]?.let { if (it.startsWith("xl/")) it else "xl/${it.trimStart('/')}" }
        val data = target?.let { zipEntries[it] }
        if (data != null) name to data else null
      }
    } else {
      zipEntries.entries
        .filter { it.key.startsWith("xl/worksheets/sheet") && it.key.endsWith(".xml") }
        .sortedBy { it.key }
        .mapIndexed { idx, e -> "Sheet ${idx + 1}" to e.value }
    }

    orderedSheetEntries.forEach { (sheetName, xmlBytes) ->
      worksheets += parseWorksheetXml(sheetName, xmlBytes.inputStream(), sharedStrings, customNumFmts, xfStyles)
    }

    if (worksheets.isEmpty()) error("No readable worksheets found in this XLSX workbook.")

    return SpreadsheetWorkbook(
      fileName = fileName,
      formatLabel = "XLSX",
      worksheets = worksheets,
      limitationNotice = "Read-only offline viewer: displays cached formula results and formula expressions without recalculating or running macros."
    )
  }

  private fun parseSharedStrings(stream: InputStream): List<String> {
    val result = mutableListOf<String>()
    val parser = Xml.newPullParser().apply { setInput(BufferedReader(InputStreamReader(stream, Charsets.UTF_8))) }
    var event = parser.eventType
    var inSi = false
    var inT = false
    val sb = StringBuilder()
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> when (parser.name) {
          "si" -> { inSi = true; sb.clear() }
          "t" -> if (inSi) inT = true
        }
        XmlPullParser.TEXT -> if (inSi && inT) sb.append(parser.text)
        XmlPullParser.END_TAG -> when (parser.name) {
          "t" -> inT = false
          "si" -> { inSi = false; result.add(sb.toString()) }
        }
      }
      event = parser.next()
    }
    return result
  }

  private fun parseWorkbookRels(stream: InputStream): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val parser = Xml.newPullParser().apply { setInput(BufferedReader(InputStreamReader(stream, Charsets.UTF_8))) }
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
        val id = parser.getAttributeValue(null, "Id")
        val target = parser.getAttributeValue(null, "Target")
        if (id != null && target != null) map[id] = target
      }
      event = parser.next()
    }
    return map
  }

  private fun parseWorkbookSheets(stream: InputStream): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    val parser = Xml.newPullParser().apply { setInput(BufferedReader(InputStreamReader(stream, Charsets.UTF_8))) }
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
        val name = parser.getAttributeValue(null, "name") ?: "Sheet"
        val rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
          ?: parser.getAttributeValue(null, "r:id")
        if (rId != null) list.add(name to rId)
      }
      event = parser.next()
    }
    return list
  }

  private fun parseStyles(stream: InputStream): Pair<Map<Int, String>, List<XfStyle>> {
    val customFmts = mutableMapOf<Int, String>()
    val fonts = mutableListOf<Triple<Boolean, Boolean, Long?>>() // bold, italic, color
    val fills = mutableListOf<Long?>()
    val xfs = mutableListOf<XfStyle>()

    val parser = Xml.newPullParser().apply { setInput(BufferedReader(InputStreamReader(stream, Charsets.UTF_8))) }
    var event = parser.eventType
    var section = ""
    var curFontBold = false
    var curFontItalic = false
    var curFontColor: Long? = null
    var curFillBg: Long? = null
    var curXf: XfStyle? = null

    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          when (parser.name) {
            "numFmts", "fonts", "fills", "cellXfs" -> section = parser.name
            "numFmt" -> if (section == "numFmts") {
              val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull()
              val code = parser.getAttributeValue(null, "formatCode")
              if (id != null && code != null) customFmts[id] = code
            }
            "font" -> if (section == "fonts") {
              curFontBold = false; curFontItalic = false; curFontColor = null
            }
            "b" -> if (section == "fonts") curFontBold = parser.getAttributeValue(null, "val") != "0"
            "i" -> if (section == "fonts") curFontItalic = parser.getAttributeValue(null, "val") != "0"
            "color" -> if (section == "fonts") {
              curFontColor = parseArgbAttr(parser.getAttributeValue(null, "rgb"))
            }
            "fill" -> if (section == "fills") curFillBg = null
            "fgColor" -> if (section == "fills") {
              curFillBg = parseArgbAttr(parser.getAttributeValue(null, "rgb"))
            }
            "xf" -> if (section == "cellXfs") {
              val numFmtId = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
              val fontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
              val fillId = parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
              val f = fonts.getOrNull(fontId)
              val bg = fills.getOrNull(fillId)
              curXf = XfStyle(
                numFmtId = numFmtId,
                bold = f?.first ?: false,
                italic = f?.second ?: false,
                textArgb = f?.third,
                bgArgb = bg
              )
            }
            "alignment" -> curXf?.let { xf ->
              val h = parser.getAttributeValue(null, "horizontal")
              val align = when (h) {
                "center" -> CellAlign.CENTER
                "right" -> CellAlign.RIGHT
                "left" -> CellAlign.LEFT
                else -> xf.align
              }
              curXf = xf.copy(align = align)
            }
          }
        }
        XmlPullParser.END_TAG -> {
          when (parser.name) {
            "font" -> if (section == "fonts") fonts.add(Triple(curFontBold, curFontItalic, curFontColor))
            "fill" -> if (section == "fills") fills.add(curFillBg)
            "xf" -> if (section == "cellXfs") curXf?.let { xfs.add(it); curXf = null }
            "numFmts", "fonts", "fills", "cellXfs" -> section = ""
          }
        }
      }
      event = parser.next()
    }
    return customFmts to xfs
  }

  private fun parseArgbAttr(rgb: String?): Long? {
    if (rgb.isNullOrBlank()) return null
    val clean = rgb.trim().removePrefix("#")
    return runCatching {
      when (clean.length) {
        8 -> clean.toLong(16)
        6 -> 0xFF000000L or clean.toLong(16)
        else -> null
      }
    }.getOrNull()?.takeIf { it != 0x00000000L }
  }

  private fun parseWorksheetXml(
    sheetName: String,
    stream: InputStream,
    sharedStrings: List<String>,
    customFmts: Map<Int, String>,
    xfs: List<XfStyle>
  ): WorksheetData {
    val grid = mutableMapOf<Int, MutableMap<Int, CellData>>()
    val colWidths = mutableMapOf<Int, Float>()
    val rowHeights = mutableMapOf<Int, Float>()
    val merged = mutableListOf<MergedRange>()
    var freezeRows = 0
    var freezeCols = 0
    var maxRow = 0
    var maxCol = 0
    var truncated = false

    val parser = Xml.newPullParser().apply { setInput(BufferedReader(InputStreamReader(stream, Charsets.UTF_8))) }
    var event = parser.eventType
    var curRowIdx = 0
    var curCellRow = 0
    var curCellCol = 0
    var curCellType = ""
    var curStyleIdx = 0
    var curFormula: String? = null
    var curValue: String? = null
    var curInlineStr: String? = null
    var inTag = ""

    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          inTag = parser.name
          when (parser.name) {
            "pane" -> {
              val state = parser.getAttributeValue(null, "state")
              if (state == "frozen" || state == "frozenSplit") {
                freezeCols = parser.getAttributeValue(null, "xSplit")?.toDoubleOrNull()?.toInt() ?: 0
                freezeRows = parser.getAttributeValue(null, "ySplit")?.toDoubleOrNull()?.toInt() ?: 0
              }
            }
            "col" -> {
              val minC = (parser.getAttributeValue(null, "min")?.toIntOrNull() ?: 1) - 1
              val maxC = (parser.getAttributeValue(null, "max")?.toIntOrNull() ?: (minC + 1)) - 1
              val widthChars = parser.getAttributeValue(null, "width")?.toFloatOrNull()
              if (widthChars != null) {
                val dp = (widthChars * 8.5f + 16f).coerceIn(68f, 280f)
                for (c in minC..minOf(maxC, MAX_COLS - 1)) colWidths[c] = dp
              }
            }
            "row" -> {
              val rAttr = parser.getAttributeValue(null, "r")?.toIntOrNull()
              curRowIdx = if (rAttr != null) rAttr - 1 else curRowIdx
              val ht = parser.getAttributeValue(null, "ht")?.toFloatOrNull()
              if (ht != null && curRowIdx < MAX_ROWS) rowHeights[curRowIdx] = (ht * 1.45f).coerceIn(30f, 120f)
            }
            "c" -> {
              val ref = parser.getAttributeValue(null, "r")
              val (r, c) = if (ref != null) parseCellRef(ref) else (curRowIdx to 0)
              curCellRow = r
              curCellCol = c
              curCellType = parser.getAttributeValue(null, "t") ?: "n"
              curStyleIdx = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
              curFormula = null
              curValue = null
              curInlineStr = null
            }
            "mergeCell" -> {
              val ref = parser.getAttributeValue(null, "ref")
              if (ref != null && ref.contains(":")) {
                val parts = ref.split(":")
                if (parts.size == 2) {
                  val (r1, c1) = parseCellRef(parts[0])
                  val (r2, c2) = parseCellRef(parts[1])
                  if (r1 < MAX_ROWS && c1 < MAX_COLS) {
                    merged += MergedRange(r1, c1, minOf(r2, MAX_ROWS - 1), minOf(c2, MAX_COLS - 1))
                  }
                }
              }
            }
          }
        }
        XmlPullParser.TEXT -> {
          when (inTag) {
            "f" -> curFormula = (curFormula ?: "") + parser.text
            "v" -> curValue = (curValue ?: "") + parser.text
            "t" -> curInlineStr = (curInlineStr ?: "") + parser.text
          }
        }
        XmlPullParser.END_TAG -> {
          when (parser.name) {
            "c" -> {
              if (curCellRow >= MAX_ROWS || curCellCol >= MAX_COLS) {
                truncated = true
              } else {
                val xf = xfs.getOrNull(curStyleIdx)
                val display = resolveCellDisplay(curCellType, curValue, curInlineStr, sharedStrings, xf, customFmts)
                val align = xf?.align ?: if (curCellType == "n" && curValue?.toDoubleOrNull() != null) CellAlign.RIGHT else CellAlign.LEFT
                if (display.isNotEmpty() || curFormula != null || xf?.bgArgb != null) {
                  maxRow = maxOf(maxRow, curCellRow)
                  maxCol = maxOf(maxCol, curCellCol)
                  grid.getOrPut(curCellRow) { mutableMapOf() }[curCellCol] = CellData(
                    row = curCellRow,
                    col = curCellCol,
                    displayText = display,
                    formula = curFormula?.let { "=$it" },
                    rawValue = curValue ?: curInlineStr,
                    bgColorArgb = xf?.bgArgb,
                    textColorArgb = xf?.textArgb,
                    isBold = xf?.bold ?: false,
                    isItalic = xf?.italic ?: false,
                    align = align
                  )
                }
              }
            }
            "row" -> curRowIdx++
          }
          inTag = ""
        }
      }
      event = parser.next()
    }

    val rowCount = (maxRow + 1).coerceAtLeast(1)
    val colCount = (maxCol + 1).coerceAtLeast(1)
    val rows = List(rowCount) { r ->
      val rowMap = grid[r]
      List(colCount) { c -> rowMap?.get(c) ?: CellData(r, c, "") }
    }
    val widths = List(colCount) { c ->
      colWidths[c] ?: run {
        val sampleMax = rows.take(40).maxOfOrNull { it[c].displayText.length } ?: 6
        (sampleMax * 8.8f + 24f).coerceIn(84f, 220f)
      }
    }
    val heights = List(rowCount) { r -> rowHeights[r] ?: 38f }
    return WorksheetData(
      name = sheetName,
      rows = rows,
      columnCount = colCount,
      columnWidthsDp = widths,
      rowHeightsDp = heights,
      mergedRanges = merged,
      freezeRows = freezeRows.coerceIn(0, rowCount),
      freezeCols = freezeCols.coerceIn(0, colCount),
      truncatedNotice = if (truncated) "Worksheet exceeded ${MAX_ROWS}x${MAX_COLS} limit and was capped for performance." else null
    )
  }

  private fun parseCellRef(ref: String): Pair<Int, Int> {
    var col = 0
    var i = 0
    while (i < ref.length && ref[i].isLetter()) {
      col = col * 26 + (ref[i].uppercaseChar() - 'A' + 1)
      i++
    }
    val row = ref.substring(i).toIntOrNull()?.minus(1) ?: 0
    return row.coerceAtLeast(0) to (col - 1).coerceAtLeast(0)
  }

  private fun resolveCellDisplay(
    type: String,
    value: String?,
    inlineStr: String?,
    sharedStrings: List<String>,
    xf: XfStyle?,
    customFmts: Map<Int, String>
  ): String {
    if (type == "s") {
      val idx = value?.toIntOrNull() ?: return ""
      return sharedStrings.getOrElse(idx) { "" }
    }
    if (type == "inlineStr") return inlineStr ?: ""
    if (type == "b") return if (value == "1") "TRUE" else "FALSE"
    if (type == "str" || type == "e") return value ?: ""
    val num = value?.toDoubleOrNull() ?: return value ?: ""
    val fmtId = xf?.numFmtId ?: 0
    val fmtCode = customFmts[fmtId] ?: ""
    return formatExcelNumber(num, fmtId, fmtCode)
  }

  private fun formatExcelNumber(num: Double, fmtId: Int, fmtCode: String): String {
    val isDate = fmtId in 14..22 || fmtCode.contains("yy", true) || fmtCode.contains("mm/dd", true) || fmtCode.contains("dd/mm", true)
    if (isDate && num > 1.0 && num < 100000.0) {
      val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, num.toInt())
      }
      return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
    val isPercent = fmtId in 9..10 || fmtCode.contains("%")
    if (isPercent) {
      return NumberFormat.getPercentInstance(Locale.US).apply { maximumFractionDigits = 2 }.format(num)
    }
    val isCurrency = fmtId in listOf(5, 6, 7, 8, 42, 44) || fmtCode.contains("$") || fmtCode.contains("€") || fmtCode.contains("£") || fmtCode.contains("₹")
    if (isCurrency) {
      return NumberFormat.getCurrencyInstance(Locale.US).format(num)
    }
    if (num == num.toLong().toDouble() && kotlin.math.abs(num) < 1e12) {
      return NumberFormat.getIntegerInstance(Locale.US).format(num.toLong())
    }
    return NumberFormat.getNumberInstance(Locale.US).apply { maximumFractionDigits = 4 }.format(num)
  }

  fun searchWorkbook(workbook: SpreadsheetWorkbook, query: String): List<CellSearchMatch> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val hits = mutableListOf<CellSearchMatch>()
    workbook.worksheets.forEachIndexed { sIdx, sheet ->
      for (r in sheet.rows.indices) {
        val row = sheet.rows[r]
        for (c in row.indices) {
          val cell = row[c]
          if (cell.displayText.lowercase().contains(q) || (cell.formula?.lowercase()?.contains(q) == true)) {
            hits += CellSearchMatch(sIdx, r, c, cell.displayText.ifBlank { cell.formula.orEmpty() })
            if (hits.size >= 250) return hits
          }
        }
      }
    }
    return hits
  }

  fun columnLabel(colIndex: Int): String {
    var n = colIndex + 1
    val sb = StringBuilder()
    while (n > 0) {
      val rem = (n - 1) % 26
      sb.append(('A' + rem))
      n = (n - 1) / 26
    }
    return sb.reverse().toString()
  }
}
