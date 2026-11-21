package com.example.paperly.ui.home

import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.data.db.DocumentKind
import com.example.paperly.data.db.RecentDocumentEntity
import com.example.paperly.data.prefs.DashboardSortOrder
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlyHeaderBanner
import com.example.paperly.theme.PaperlySurfaceCard
import com.example.paperly.theme.PaperlyThemeSpec

private enum class HomeDocFilter(val label: String) {
  ALL("All Recent"),
  PDFS("PDFs"),
  SHEETS("Spreadsheets"),
  FAVORITES("Favourites")
}

@Composable
fun HomeDashboardScreen(
  recentDocuments: List<RecentDocumentEntity>,
  isGridView: Boolean,
  sortOrder: DashboardSortOrder,
  onToggleGridView: (Boolean) -> Unit,
  onChangeSortOrder: (DashboardSortOrder) -> Unit,
  onOpenAnyFileUri: (Uri) -> Unit,
  onOpenDocumentEntity: (RecentDocumentEntity) -> Unit,
  onToggleFavorite: (RecentDocumentEntity) -> Unit,
  onRemoveHistory: (String) -> Unit,
  onNavigatePdfReader: () -> Unit,
  onNavigateSheets: () -> Unit,
  onNavigateImageWorkshop: () -> Unit,
  onNavigatePdfWorkshop: () -> Unit,
  onNavigatePrivacyStudio: () -> Unit,
  onNavigateThemes: () -> Unit,
  onNavigateSettings: () -> Unit
) {
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current

  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf(HomeDocFilter.ALL) }
  var showSortMenu by remember { mutableStateOf(false) }

  val openDocLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onOpenAnyFileUri(uri)
  }

  val filteredDocs = remember(recentDocuments, searchQuery, selectedFilter, sortOrder) {
    val q = searchQuery.trim().lowercase()
    val subset = when (selectedFilter) {
      HomeDocFilter.ALL -> recentDocuments
      HomeDocFilter.PDFS -> recentDocuments.filter { it.kind == DocumentKind.PDF }
      HomeDocFilter.SHEETS -> recentDocuments.filter { it.kind.isSpreadsheet }
      HomeDocFilter.FAVORITES -> recentDocuments.filter { it.isFavorite }
    }.filter { doc ->
      q.isEmpty() || doc.displayName.lowercase().contains(q) || doc.kind.label.lowercase().contains(q)
    }
    when (sortOrder) {
      DashboardSortOrder.DATE -> subset.sortedByDescending { it.lastOpenedAt }
      DashboardSortOrder.NAME -> subset.sortedBy { it.displayName.lowercase() }
      DashboardSortOrder.TYPE -> subset.sortedBy { it.fileType }
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
        title = "Paperly",
        subtitle = "${spec.name} • 100% Offline",
        trailing = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onNavigatePrivacyStudio,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("home_privacy_studio_button")
            ) {
              Icon(Icons.Default.Shield, contentDescription = "Privacy Studio", tint = spec.accentColor)
            }
            IconButton(
              onClick = onNavigateThemes,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("home_themes_button")
            ) {
              Icon(Icons.Default.Palette, contentDescription = "Theme Gallery", tint = spec.accentColor)
            }
            IconButton(
              onClick = onNavigateSettings,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("home_settings_button")
            ) {
              Icon(Icons.Default.Settings, contentDescription = "Settings", tint = spec.textPrimaryColor)
            }
          }
        }
      )

      Spacer(Modifier.height(10.dp))

      // Primary Open File CTA + Search
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          placeholder = { Text("Search documents...") },
          singleLine = true,
          shape = RoundedCornerShape(spec.cornerRadiusDp.dp),
          modifier = Modifier
            .weight(1f)
            .testTag("home_search_input")
        )
        Button(
          onClick = {
            openDocLauncher.launch(
              arrayOf(
                "application/pdf",
                "text/csv",
                "text/comma-separated-values",
                "text/tab-separated-values",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "image/*"
              )
            )
          },
          shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
          modifier = Modifier
            .height(54.dp)
            .minimumInteractiveComponentSize()
            .testTag("home_open_file_button")
        ) {
          Icon(Icons.Default.FolderOpen, contentDescription = "Open file")
          Spacer(Modifier.width(6.dp))
          Text("Open")
        }
      }

      Spacer(Modifier.height(12.dp))

      // Workspace Quick Launchers
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        WorkspaceTile("Privacy Studio", "Redact & blur", Icons.Default.Shield, spec, "tile_privacy_studio", onNavigatePrivacyStudio)
        WorkspaceTile("PDF Reader", "Read & search", Icons.Default.Description, spec, "tile_pdf_reader", onNavigatePdfReader)
        WorkspaceTile("Sheets", "XLSX & CSV", Icons.Default.TableChart, spec, "tile_sheets", onNavigateSheets)
        WorkspaceTile("Image Lab", "Resize & PDF", Icons.Default.Image, spec, "tile_image_lab", onNavigateImageWorkshop)
        WorkspaceTile("PDF Lab", "Merge & sign", Icons.Default.Build, spec, "tile_pdf_lab", onNavigatePdfWorkshop)
        WorkspaceTile("Themes", "280+ looks", Icons.Default.Palette, spec, "tile_themes", onNavigateThemes)
      }

      Spacer(Modifier.height(12.dp))

      // Filter & View Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          HomeDocFilter.entries.forEach { f ->
            FilterChip(
              selected = selectedFilter == f,
              onClick = { selectedFilter = f },
              label = { Text(f.label) },
              modifier = Modifier.testTag("home_filter_${f.name.lowercase()}")
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box {
            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.minimumInteractiveComponentSize()) {
              Icon(Icons.Default.Sort, contentDescription = "Sort documents", tint = spec.textPrimaryColor)
            }
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
              DashboardSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                  text = { Text("Sort by ${order.label}") },
                  onClick = {
                    onChangeSortOrder(order)
                    showSortMenu = false
                  }
                )
              }
            }
          }
          IconButton(
            onClick = { onToggleGridView(!isGridView) },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("home_layout_toggle")
          ) {
            Icon(
              imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
              contentDescription = "Toggle list or grid view",
              tint = spec.textPrimaryColor
            )
          }
        }
      }

      Spacer(Modifier.height(8.dp))

      if (filteredDocs.isEmpty()) {
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = spec.accentColor, modifier = Modifier.size(40.dp))
            Text(
              text = if (recentDocuments.isEmpty()) "Your offline workspace is ready" else "No matching documents",
              style = MaterialTheme.typography.titleMedium,
              color = spec.textPrimaryColor
            )
            Text(
              text = "Open a local PDF, Excel workbook, CSV, or image to get started. Everything stays strictly on your device.",
              style = MaterialTheme.typography.bodySmall,
              color = spec.textSecondaryColor
            )
          }
        }
      } else if (isGridView) {
        LazyVerticalGrid(
          columns = GridCells.Adaptive(minSize = 165.dp),
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 96.dp)
        ) {
          items(filteredDocs, key = { it.uriString }) { doc ->
            RecentDocCard(
              doc = doc,
              spec = spec,
              compact = true,
              formattedSize = if (doc.fileSizeBytes > 0) Formatter.formatShortFileSize(context, doc.fileSizeBytes) else null,
              formattedTime = DateUtils.getRelativeTimeSpanString(doc.lastOpenedAt).toString(),
              onOpen = { onOpenDocumentEntity(doc) },
              onToggleFavorite = { onToggleFavorite(doc) },
              onRemove = { onRemoveHistory(doc.uriString) }
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(bottom = 96.dp)
        ) {
          items(filteredDocs, key = { it.uriString }) { doc ->
            RecentDocCard(
              doc = doc,
              spec = spec,
              compact = false,
              formattedSize = if (doc.fileSizeBytes > 0) Formatter.formatShortFileSize(context, doc.fileSizeBytes) else null,
              formattedTime = DateUtils.getRelativeTimeSpanString(doc.lastOpenedAt).toString(),
              onOpen = { onOpenDocumentEntity(doc) },
              onToggleFavorite = { onToggleFavorite(doc) },
              onRemove = { onRemoveHistory(doc.uriString) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun WorkspaceTile(
  title: String,
  subtitle: String,
  icon: ImageVector,
  spec: PaperlyThemeSpec,
  testTag: String,
  onClick: () -> Unit
) {
  PaperlySurfaceCard(
    spec = spec,
    onClick = onClick,
    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    modifier = Modifier
      .width(128.dp)
      .minimumInteractiveComponentSize()
      .testTag(testTag)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(RoundedCornerShape(spec.buttonRadiusDp.dp))
          .background(spec.selectedContainerColor),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = title, tint = spec.accentColor, modifier = Modifier.size(19.dp))
      }
      Text(title, style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor, maxLines = 1)
    }
  }
}

@Composable
private fun RecentDocCard(
  doc: RecentDocumentEntity,
  spec: PaperlyThemeSpec,
  compact: Boolean,
  formattedSize: String?,
  formattedTime: String,
  onOpen: () -> Unit,
  onToggleFavorite: () -> Unit,
  onRemove: () -> Unit
) {
  val countText = when {
    doc.kind == DocumentKind.PDF && doc.pageOrSheetCount > 0 -> "${doc.pageOrSheetCount} pages"
    doc.kind.isSpreadsheet && doc.pageOrSheetCount > 0 -> "${doc.pageOrSheetCount} sheets"
    else -> null
  }
  val details = listOfNotNull(doc.kind.label, countText, formattedSize, formattedTime).joinToString(" • ")

  PaperlySurfaceCard(
    spec = spec,
    onClick = onOpen,
    modifier = Modifier.fillMaxWidth()
  ) {
    if (compact) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = when {
              doc.kind == DocumentKind.PDF -> Icons.Default.Description
              doc.kind.isSpreadsheet -> Icons.Default.TableChart
              else -> Icons.Default.Image
            },
            contentDescription = doc.kind.label,
            tint = spec.accentColor
          )
          Row {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
              Icon(
                if (doc.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favourite",
                tint = if (doc.isFavorite) spec.accentColor else spec.textSecondaryColor,
                modifier = Modifier.size(18.dp)
              )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
              Icon(Icons.Default.Close, contentDescription = "Remove from history", tint = spec.textSecondaryColor, modifier = Modifier.size(18.dp))
            }
          }
        }
        Text(doc.displayName, style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(details, style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
    } else {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(spec.buttonRadiusDp.dp))
            .background(spec.selectedContainerColor),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = when {
              doc.kind == DocumentKind.PDF -> Icons.Default.Description
              doc.kind.isSpreadsheet -> Icons.Default.TableChart
              else -> Icons.Default.Image
            },
            contentDescription = doc.kind.label,
            tint = spec.accentColor
          )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(doc.displayName, style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(2.dp))
          Text(details, style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(
            imageVector = if (doc.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = "Toggle favourite",
            tint = if (doc.isFavorite) spec.accentColor else spec.textSecondaryColor
          )
        }
        IconButton(onClick = onRemove, modifier = Modifier.minimumInteractiveComponentSize()) {
          Icon(Icons.Default.Close, contentDescription = "Remove history entry", tint = spec.textSecondaryColor)
        }
      }
    }
  }
}
