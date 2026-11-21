package com.example.paperly.ui.spreadsheet

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.engines.spreadsheet.CellAlign
import com.example.paperly.engines.spreadsheet.CellData
import com.example.paperly.engines.spreadsheet.CellSearchMatch
import com.example.paperly.engines.spreadsheet.SpreadsheetEngine
import com.example.paperly.engines.spreadsheet.SpreadsheetWorkbook
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlySurfaceCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpreadsheetViewerScreen(
  sheetUri: Uri?,
  settings: PaperlySettings,
  spreadsheetEngine: SpreadsheetEngine,
  documentRepo: DocumentRepository,
  onSelectSheetUri: (Uri) -> Unit
) {
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  val openSheetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onSelectSheetUri(uri)
  }

  var workbook by remember { mutableStateOf<SpreadsheetWorkbook?>(null) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMsg by remember { mutableStateOf<String?>(null) }

  var activeSheetIdx by remember(sheetUri) { mutableIntStateOf(0) }
  var zoom by remember(sheetUri) { mutableFloatStateOf(settings.sheetDefaultZoom) }
  var showGrid by remember(sheetUri) { mutableStateOf(settings.sheetShowGridLines) }
  var showHeaders by remember(sheetUri) { mutableStateOf(settings.sheetShowHeaders) }

  var selectedCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
  var rangeAnchor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
  var showCellDetailDialog by remember { mutableStateOf(false) }

  var showSearch by remember { mutableStateOf(false) }
  var searchQuery by remember { mutableStateOf("") }
  var searchMatches by remember { mutableStateOf<List<CellSearchMatch>>(emptyList()) }
  var activeMatchIdx by remember { mutableIntStateOf(0) }
  var isSearching by remember { mutableStateOf(false) }
  var hasSearched by remember(sheetUri) { mutableStateOf(false) }
  var showInfoDialog by remember { mutableStateOf(false) }

  val verticalListState = rememberLazyListState()
  val horizontalScrollState = rememberScrollState()

  suspend fun scrollToCellMatch(wb: SpreadsheetWorkbook, hit: CellSearchMatch) {
    val ws = wb.worksheets.getOrNull(hit.sheetIndex) ?: return
    val startRow = ws.freezeRows.coerceIn(0, ws.rows.size)
    if (hit.row >= startRow) {
      verticalListState.scrollToItem((hit.row - startRow).coerceAtLeast(0))
    } else {
      verticalListState.scrollToItem(0)
    }
    var xDp = 0f
    for (c in 0 until hit.col.coerceAtMost(ws.columnWidthsDp.size)) {
      xDp += ws.columnWidthsDp[c] * zoom
    }
    val xPx = with(density) { xDp.dp.toPx() }.toInt()
    horizontalScrollState.scrollTo(xPx)
  }

  LaunchedEffect(sheetUri) {
    workbook = null
    errorMsg = null
    selectedCell = null
    rangeAnchor = null
    searchMatches = emptyList()
    if (sheetUri == null) return@LaunchedEffect

    isLoading = true
    val meta = runCatching { documentRepo.inspectUri(sheetUri) }.getOrNull()
    val res = spreadsheetEngine.loadWorkbook(sheetUri, meta?.displayName ?: "spreadsheet.xlsx")
    if (res.isSuccess) {
      val wb = res.getOrThrow()
      workbook = wb
      if (meta != null) {
        documentRepo.recordOpenedDocument(meta, wb.worksheets.size)
      }
      val recent = documentRepo.findRecent(sheetUri.toString())
      activeSheetIdx = (recent?.lastSheetIndex ?: 0).coerceIn(0, (wb.worksheets.size - 1).coerceAtLeast(0))
    } else {
      errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Unable to parse spreadsheet."
    }
    isLoading = false
  }

  LaunchedEffect(activeSheetIdx, sheetUri, workbook) {
    if (sheetUri != null && workbook != null) {
      documentRepo.updateSheetPosition(sheetUri.toString(), activeSheetIdx)
    }
  }

  PaperlyCanvasBackground(spec = spec) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Top Toolbar
      PaperlySurfaceCard(
        spec = spec,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = workbook?.fileName ?: "Spreadsheet Viewer",
                style = MaterialTheme.typography.titleSmall,
                color = spec.textPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = workbook?.let { "${it.formatLabel} • ${it.worksheets.size} worksheet(s) • ${(zoom * 100).toInt()}%" }
                  ?: "XLSX, CSV, TSV & XLS (read-only)",
                style = MaterialTheme.typography.labelSmall,
                color = spec.textSecondaryColor
              )
            }
            Row {
              IconButton(
                onClick = {
                  openSheetLauncher.launch(
                    arrayOf(
                      "text/csv",
                      "text/comma-separated-values",
                      "text/tab-separated-values",
                      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                      "application/vnd.ms-excel",
                      "*/*"
                    )
                  )
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("sheet_open_button")
              ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Open spreadsheet", tint = spec.accentColor)
              }
              if (workbook != null) {
                IconButton(onClick = { showSearch = !showSearch }, modifier = Modifier.minimumInteractiveComponentSize()) {
                  Icon(Icons.Default.Search, contentDescription = "Search worksheet", tint = spec.textPrimaryColor)
                }
                IconButton(onClick = { showGrid = !showGrid }, modifier = Modifier.minimumInteractiveComponentSize()) {
                  Icon(Icons.Default.GridOn, contentDescription = "Toggle gridlines", tint = if (showGrid) spec.accentColor else spec.textSecondaryColor)
                }
                IconButton(onClick = { zoom = (zoom - 0.15f).coerceAtLeast(0.7f) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                  Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out", tint = spec.textPrimaryColor)
                }
                IconButton(onClick = { zoom = (zoom + 0.15f).coerceAtMost(2.0f) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                  Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in", tint = spec.textPrimaryColor)
                }
                IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
                  Icon(Icons.Default.Info, contentDescription = "Workbook info", tint = spec.textPrimaryColor)
                }
              }
            }
          }

          if (workbook != null) {
            // Worksheet tabs
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              workbook!!.worksheets.forEachIndexed { idx, ws ->
                FilterChip(
                  selected = activeSheetIdx == idx,
                  onClick = {
                    activeSheetIdx = idx
                    selectedCell = null
                    rangeAnchor = null
                  },
                  label = { Text(ws.name) },
                  modifier = Modifier.testTag("sheet_tab_$idx")
                )
              }
            }

            if (showSearch) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                OutlinedTextField(
                  value = searchQuery,
                  onValueChange = {
                    searchQuery = it
                    hasSearched = false
                  },
                  placeholder = { Text("Search cells or formulas...") },
                  singleLine = true,
                  modifier = Modifier.weight(1f).testTag("sheet_search_input")
                )
                Button(
                  onClick = {
                    val wb = workbook ?: return@Button
                    scope.launch {
                      isSearching = true
                      val matches = withContext(Dispatchers.Default) {
                        spreadsheetEngine.searchWorkbook(wb, searchQuery)
                      }
                      searchMatches = matches
                      activeMatchIdx = 0
                      hasSearched = true
                      isSearching = false
                      matches.firstOrNull()?.let { m ->
                        activeSheetIdx = m.sheetIndex
                        selectedCell = m.row to m.col
                        scrollToCellMatch(wb, m)
                      }
                    }
                  },
                  enabled = !isSearching && searchQuery.isNotBlank(),
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) { Text(if (isSearching) "..." else "Find") }
              }
              if (searchMatches.isNotEmpty()) {
                val m = searchMatches[activeMatchIdx.coerceIn(0, searchMatches.lastIndex)]
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "Hit ${activeMatchIdx + 1}/${searchMatches.size}: ${spreadsheetEngine.columnLabel(m.col)}${m.row + 1} (${m.preview})",
                    style = MaterialTheme.typography.labelSmall,
                    color = spec.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                  )
                  Row {
                    IconButton(
                      onClick = {
                        val wb = workbook ?: return@IconButton
                        activeMatchIdx = if (activeMatchIdx <= 0) searchMatches.lastIndex else activeMatchIdx - 1
                        val hit = searchMatches[activeMatchIdx]
                        activeSheetIdx = hit.sheetIndex
                        selectedCell = hit.row to hit.col
                        scope.launch { scrollToCellMatch(wb, hit) }
                      },
                      modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous hit") }
                    IconButton(
                      onClick = {
                        val wb = workbook ?: return@IconButton
                        activeMatchIdx = (activeMatchIdx + 1) % searchMatches.size
                        val hit = searchMatches[activeMatchIdx]
                        activeSheetIdx = hit.sheetIndex
                        selectedCell = hit.row to hit.col
                        scope.launch { scrollToCellMatch(wb, hit) }
                      },
                      modifier = Modifier.minimumInteractiveComponentSize()
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next hit") }
                  }
                }
              } else if (hasSearched && !isSearching && searchQuery.isNotBlank()) {
                Text(
                  text = "No matching cells found for \"$searchQuery\".",
                  style = MaterialTheme.typography.labelSmall,
                  color = spec.textSecondaryColor
                )
              }
            }

            // Selected Cell Inspector Bar (tap to inspect full cell text)
            val ws = workbook!!.worksheets.getOrNull(activeSheetIdx)
            val sel = selectedCell
            if (ws != null && sel != null) {
              val cell = ws.rows.getOrNull(sel.first)?.getOrNull(sel.second)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(spec.selectedContainerColor, RoundedCornerShape(8.dp))
                  .clickable { showCellDetailDialog = true }
                  .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  val ref = "${spreadsheetEngine.columnLabel(sel.second)}${sel.first + 1}"
                  Text(
                    text = if (cell?.formula != null) "$ref  fx ${cell.formula}" else "$ref (tap to expand)",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = spec.monoFont.toComposeFontFamily(),
                    color = spec.accentColor
                  )
                  Text(
                    text = cell?.displayText?.ifBlank { "(empty)" } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = spec.textPrimaryColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Row {
                  TextButton(onClick = {
                    val txt = cell?.displayText ?: ""
                    clipboard.setText(AnnotatedString(txt))
                    Toast.makeText(context, "Copied cell value", Toast.LENGTH_SHORT).show()
                  }) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cell")
                  }
                  TextButton(onClick = {
                    val anchor = rangeAnchor ?: sel
                    val rMin = minOf(anchor.first, sel.first)
                    val rMax = maxOf(anchor.first, sel.first)
                    val cMin = minOf(anchor.second, sel.second)
                    val cMax = maxOf(anchor.second, sel.second)
                    val tsv = (rMin..rMax).joinToString("\n") { r ->
                      (cMin..cMax).joinToString("\t") { c -> ws.rows.getOrNull(r)?.getOrNull(c)?.displayText.orEmpty() }
                    }
                    clipboard.setText(AnnotatedString(tsv))
                    Toast.makeText(context, "Copied range (${rMax - rMin + 1}x${cMax - cMin + 1})", Toast.LENGTH_SHORT).show()
                  }) { Text("Range") }
                }
              }
            }
          }
        }
      }

      // Main Virtualized Spreadsheet Grid
      Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        when {
          isLoading -> {
            Column(
              modifier = Modifier.align(Alignment.Center),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              CircularProgressIndicator(color = spec.accentColor)
              Text("Parsing workbook locally...", color = spec.textPrimaryColor)
            }
          }
          errorMsg != null -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
              Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Could not open spreadsheet", style = MaterialTheme.typography.titleMedium, color = spec.errorColor)
                Text(errorMsg.orEmpty(), style = MaterialTheme.typography.bodySmall, color = spec.textSecondaryColor)
                Button(onClick = {
                  openSheetLauncher.launch(arrayOf("text/csv", "text/tab-separated-values", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*"))
                }) { Text("Choose Another File") }
              }
            }
          }
          workbook == null -> {
            PaperlySurfaceCard(spec = spec, modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(12.dp)
              ) {
                Icon(Icons.Default.TableChart, null, tint = spec.accentColor, modifier = Modifier.size(48.dp))
                Text("Offline Spreadsheet Viewer", style = MaterialTheme.typography.headlineSmall, color = spec.textPrimaryColor)
                Text(
                  "Inspect XLSX workbooks, CSV, TSV, and legacy XLS files with virtualized scrolling, cell formatting, formulas, and range copying.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = spec.textSecondaryColor
                )
                Button(
                  onClick = {
                    openSheetLauncher.launch(
                      arrayOf(
                        "text/csv",
                        "text/tab-separated-values",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "*/*"
                      )
                    )
                  },
                  shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
                  modifier = Modifier.minimumInteractiveComponentSize().testTag("sheet_empty_open_button")
                ) {
                  Icon(Icons.Default.FolderOpen, null)
                  Spacer(Modifier.width(8.dp))
                  Text("Open Spreadsheet")
                }
              }
            }
          }
          else -> {
            val wb = workbook!!
            val ws = wb.worksheets.getOrNull(activeSheetIdx)
            if (ws != null) {
              Column(modifier = Modifier.fillMaxSize()) {
                val notice = ws.truncatedNotice ?: wb.limitationNotice
                if (notice != null) {
                  Text(
                    text = notice,
                    style = MaterialTheme.typography.labelSmall,
                    color = spec.textSecondaryColor,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    maxLines = 2
                  )
                }

                Box(
                  modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                ) {
                  Column(modifier = Modifier.fillMaxSize()) {
                    // Sticky Column Headers
                    if (showHeaders) {
                      Row(modifier = Modifier.background(spec.surfaceVariantColor)) {
                        Box(
                          modifier = Modifier
                            .width((46f * zoom).dp)
                            .height((32f * zoom).dp)
                            .border(0.5.dp, spec.borderColor),
                          contentAlignment = Alignment.Center
                        ) {
                          Text("#", fontSize = (11f * zoom).sp, fontWeight = FontWeight.Bold, color = spec.textSecondaryColor)
                        }
                        for (c in 0 until ws.columnCount) {
                          val colW = (ws.columnWidthsDp.getOrElse(c) { 110f } * zoom).dp
                          Box(
                            modifier = Modifier
                              .width(colW)
                              .height((32f * zoom).dp)
                              .border(0.5.dp, spec.borderColor),
                            contentAlignment = Alignment.Center
                          ) {
                            Text(
                              text = spreadsheetEngine.columnLabel(c),
                              fontSize = (11f * zoom).sp,
                              fontWeight = FontWeight.Bold,
                              color = spec.textPrimaryColor
                            )
                          }
                        }
                      }
                    }

                    // Frozen Top Rows if any
                    if (ws.freezeRows > 0) {
                      for (fr in 0 until minOf(ws.freezeRows, ws.rows.size)) {
                        SpreadsheetRowView(
                          rowIndex = fr,
                          cells = ws.rows[fr],
                          colWidths = ws.columnWidthsDp,
                          rowHeight = ws.rowHeightsDp.getOrElse(fr) { 38f },
                          zoom = zoom,
                          showHeaders = showHeaders,
                          showGrid = showGrid,
                          selectedCell = selectedCell,
                          rangeAnchor = rangeAnchor,
                          isFrozenHeader = true,
                          onSelectCell = { r, c ->
                            rangeAnchor = selectedCell ?: (r to c)
                            selectedCell = r to c
                          }
                        )
                      }
                    }

                    // Virtualized Remaining Rows
                    val startRow = ws.freezeRows.coerceIn(0, ws.rows.size)
                    LazyColumn(
                      state = verticalListState,
                      modifier = Modifier.weight(1f),
                      contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                      itemsIndexed(ws.rows.drop(startRow), key = { idx, _ -> idx + startRow }) { offsetIdx, rowCells ->
                        val actualRow = offsetIdx + startRow
                        SpreadsheetRowView(
                          rowIndex = actualRow,
                          cells = rowCells,
                          colWidths = ws.columnWidthsDp,
                          rowHeight = ws.rowHeightsDp.getOrElse(actualRow) { 38f },
                          zoom = zoom,
                          showHeaders = showHeaders,
                          showGrid = showGrid,
                          selectedCell = selectedCell,
                          rangeAnchor = rangeAnchor,
                          isFrozenHeader = false,
                          onSelectCell = { r, c ->
                            rangeAnchor = selectedCell ?: (r to c)
                            selectedCell = r to c
                          }
                        )
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showInfoDialog && workbook != null) {
    val wb = workbook!!
    val ws = wb.worksheets.getOrNull(activeSheetIdx)
    AlertDialog(
      onDismissRequest = { showInfoDialog = false },
      title = { Text("Workbook Information") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("File: ${wb.fileName}", style = MaterialTheme.typography.bodySmall)
          Text("Format: ${wb.formatLabel}", style = MaterialTheme.typography.bodySmall)
          Text("Worksheets: ${wb.worksheets.size}", style = MaterialTheme.typography.bodySmall)
          if (ws != null) {
            Text("Active Sheet: ${ws.name} (${ws.rows.size} rows × ${ws.columnCount} cols)", style = MaterialTheme.typography.bodySmall)
            Text("Merged Ranges: ${ws.mergedRanges.size} • Frozen Rows: ${ws.freezeRows}", style = MaterialTheme.typography.bodySmall)
          }
          wb.limitationNotice?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
          }
        }
      },
      confirmButton = { TextButton(onClick = { showInfoDialog = false }) { Text("Close") } }
    )
  }
  if (showCellDetailDialog && workbook != null && selectedCell != null) {
    val ws = workbook!!.worksheets.getOrNull(activeSheetIdx)
    val sel = selectedCell!!
    val cell = ws?.rows?.getOrNull(sel.first)?.getOrNull(sel.second)
    val ref = "${spreadsheetEngine.columnLabel(sel.second)}${sel.first + 1}"
    AlertDialog(
      onDismissRequest = { showCellDetailDialog = false },
      title = { Text("Cell $ref (${ws?.name ?: "Sheet"})") },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (cell?.formula != null) {
            Text("Formula", style = MaterialTheme.typography.labelMedium, color = spec.accentColor)
            SelectionContainer {
              Text(cell.formula, style = MaterialTheme.typography.bodySmall, fontFamily = spec.monoFont.toComposeFontFamily())
            }
          }
          Text("Value", style = MaterialTheme.typography.labelMedium, color = spec.accentColor)
          SelectionContainer {
            Text(cell?.displayText?.ifBlank { "(empty)" } ?: "(empty)", style = MaterialTheme.typography.bodyMedium)
          }
        }
      },
      confirmButton = {
        Button(onClick = {
          clipboard.setText(AnnotatedString(cell?.displayText ?: ""))
          Toast.makeText(context, "Copied cell value", Toast.LENGTH_SHORT).show()
        }) {
          Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Copy")
        }
      },
      dismissButton = { TextButton(onClick = { showCellDetailDialog = false }) { Text("Close") } }
    )
  }
}

fun resolveSpreadsheetCellTextColor(
  isSelected: Boolean,
  bgColorArgb: Long?,
  textColorArgb: Long?,
  fallbackThemeTextColor: Color
): Color = when {
  isSelected -> fallbackThemeTextColor
  textColorArgb != null && bgColorArgb != null -> Color(textColorArgb)
  bgColorArgb != null -> if (Color(bgColorArgb).luminance() > 0.45f) Color(0xFF0F172A) else Color(0xFFF8FAFC)
  else -> fallbackThemeTextColor
}

@Composable
private fun SpreadsheetRowView(
  rowIndex: Int,
  cells: List<CellData>,
  colWidths: List<Float>,
  rowHeight: Float,
  zoom: Float,
  showHeaders: Boolean,
  showGrid: Boolean,
  selectedCell: Pair<Int, Int>?,
  rangeAnchor: Pair<Int, Int>?,
  isFrozenHeader: Boolean,
  onSelectCell: (Int, Int) -> Unit
) {
  val spec = LocalPaperlyTheme.current
  val h = (rowHeight * zoom).dp
  Row {
    if (showHeaders) {
      Box(
        modifier = Modifier
          .width((46f * zoom).dp)
          .height(h)
          .background(spec.surfaceVariantColor)
          .border(0.5.dp, spec.borderColor),
        contentAlignment = Alignment.Center
      ) {
        Text("${rowIndex + 1}", fontSize = (11f * zoom).sp, color = spec.textSecondaryColor)
      }
    }
    cells.forEachIndexed { colIdx, cell ->
      val w = (colWidths.getOrElse(colIdx) { 110f } * zoom).dp
      val isSelected = selectedCell == (rowIndex to colIdx)
      val bgColor = when {
        isSelected -> spec.selectedContainerColor
        cell.bgColorArgb != null -> Color(cell.bgColorArgb)
        isFrozenHeader -> spec.surfaceVariantColor.copy(alpha = 0.7f)
        else -> spec.surfaceColor
      }
      val txtColor = resolveSpreadsheetCellTextColor(
        isSelected = isSelected,
        bgColorArgb = cell.bgColorArgb,
        textColorArgb = cell.textColorArgb,
        fallbackThemeTextColor = spec.textPrimaryColor
      )
      Box(
        modifier = Modifier
          .width(w)
          .height(h)
          .background(bgColor)
          .then(
            if (isSelected) Modifier.border(1.8.dp, spec.accentColor)
            else if (showGrid) Modifier.border(0.5.dp, spec.borderColor.copy(alpha = 0.65f))
            else Modifier
          )
          .clickable { onSelectCell(rowIndex, colIdx) }
          .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = when (cell.align) {
          CellAlign.LEFT -> Alignment.CenterStart
          CellAlign.CENTER -> Alignment.Center
          CellAlign.RIGHT -> Alignment.CenterEnd
        }
      ) {
        Text(
          text = cell.displayText,
          fontSize = (12.5f * zoom).sp,
          fontWeight = if (cell.isBold || isFrozenHeader) FontWeight.Bold else FontWeight.Normal,
          fontStyle = if (cell.isItalic) FontStyle.Italic else FontStyle.Normal,
          color = txtColor,
          textAlign = when (cell.align) {
            CellAlign.LEFT -> TextAlign.Start
            CellAlign.CENTER -> TextAlign.Center
            CellAlign.RIGHT -> TextAlign.End
          },
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
