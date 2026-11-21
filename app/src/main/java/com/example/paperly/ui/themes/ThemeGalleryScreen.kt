package com.example.paperly.ui.themes

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlyHeaderBanner
import com.example.paperly.theme.PaperlySurfaceCard
import com.example.paperly.theme.PaperlyThemeSpec
import com.example.paperly.theme.ThemeCollection
import com.example.paperly.theme.ThemeJsonCodec

private enum class ThemeFilterTab(val label: String) {
  ALL("All"),
  FAVORITES("Favourites"),
  RECENT("Recent"),
  CUSTOM("Custom"),
  LIGHT("Light"),
  DARK("Dark")
}

@Composable
fun ThemeGalleryScreen(
  allThemes: List<PaperlyThemeSpec>,
  activeTheme: PaperlyThemeSpec,
  favoriteIds: Set<String>,
  recentIds: List<String>,
  onApplyTheme: (PaperlyThemeSpec) -> Unit,
  onToggleFavorite: (String) -> Unit,
  onRandomTheme: () -> Unit,
  onOpenStudioWith: (PaperlyThemeSpec) -> Unit,
  onImportThemeJson: (String) -> Result<PaperlyThemeSpec>,
  onRestoreDefaults: () -> Unit,
  onBack: (() -> Unit)? = null
) {
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  var searchQuery by remember { mutableStateOf("") }
  var selectedTab by remember { mutableStateOf(ThemeFilterTab.ALL) }
  var selectedCollection by remember { mutableStateOf<ThemeCollection?>(null) }
  var previewScreenKind by remember { mutableStateOf(PreviewScreenKind.HOME) }
  var inspectedTheme by remember { mutableStateOf<PaperlyThemeSpec?>(null) }
  var showImportDialog by remember { mutableStateOf(false) }
  var importJsonText by remember { mutableStateOf("") }

  val filteredThemes = remember(allThemes, searchQuery, selectedTab, selectedCollection, favoriteIds, recentIds) {
    val base = when (selectedTab) {
      ThemeFilterTab.ALL -> allThemes
      ThemeFilterTab.FAVORITES -> allThemes.filter { it.id in favoriteIds }
      ThemeFilterTab.RECENT -> recentIds.mapNotNull { id -> allThemes.firstOrNull { it.id == id } }
      ThemeFilterTab.CUSTOM -> allThemes.filter { it.isCustom }
      ThemeFilterTab.LIGHT -> allThemes.filter { !it.isDark }
      ThemeFilterTab.DARK -> allThemes.filter { it.isDark }
    }
    base.filter { t ->
      val matchCol = selectedCollection == null || t.collection == selectedCollection
      val q = searchQuery.trim().lowercase()
      val matchQ = q.isEmpty() || t.name.lowercase().contains(q) ||
        t.collection.displayName.lowercase().contains(q) ||
        t.surfaceMaterial.displayName.lowercase().contains(q) ||
        t.variantTag.lowercase().contains(q)
      matchCol && matchQ
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
        title = "Theme Gallery",
        subtitle = "${allThemes.size} visual identities • 40 design collections",
        trailing = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
              IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = spec.textPrimaryColor)
              }
            }
            IconButton(
              onClick = onRandomTheme,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("random_theme_button")
            ) {
              Icon(Icons.Default.Shuffle, contentDescription = "Random theme", tint = spec.accentColor)
            }
            IconButton(
              onClick = { showImportDialog = true },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("import_theme_button")
            ) {
              Icon(Icons.Default.FileUpload, contentDescription = "Import theme JSON", tint = spec.textPrimaryColor)
            }
            IconButton(
              onClick = {
                onRestoreDefaults()
                Toast.makeText(context, "Restored default Paperly theme", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("restore_defaults_button")
            ) {
              Icon(Icons.Default.RestartAlt, contentDescription = "Restore defaults", tint = spec.textSecondaryColor)
            }
          }
        }
      )

      Spacer(Modifier.height(10.dp))

      // Search box
      OutlinedTextField(
        value = searchQuery,
        onValueChange = { searchQuery = it },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        placeholder = { Text("Search 280+ themes, styles, materials...") },
        singleLine = true,
        shape = RoundedCornerShape(spec.cornerRadiusDp.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("theme_search_input")
      )

      Spacer(Modifier.height(8.dp))

      // Filter tabs
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        ThemeFilterTab.entries.forEach { tab ->
          FilterChip(
            selected = selectedTab == tab,
            onClick = { selectedTab = tab },
            label = { Text(tab.label) },
            modifier = Modifier.testTag("theme_tab_${tab.name.lowercase()}")
          )
        }
      }

      // 40 Collections horizontal chip strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FilterChip(
          selected = selectedCollection == null,
          onClick = { selectedCollection = null },
          label = { Text("All Collections (40)") }
        )
        ThemeCollection.entries.forEach { col ->
          FilterChip(
            selected = selectedCollection == col,
            onClick = { selectedCollection = if (selectedCollection == col) null else col },
            label = { Text(col.displayName) }
          )
        }
      }

      Spacer(Modifier.height(6.dp))

      // Preview screen selector strip
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Preview:", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
        PreviewScreenKind.entries.forEach { kind ->
          FilterChip(
            selected = previewScreenKind == kind,
            onClick = { previewScreenKind = kind },
            label = { Text(kind.label) }
          )
        }
      }

      Spacer(Modifier.height(8.dp))

      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
      ) {
        items(filteredThemes, key = { it.id }) { theme ->
          val isActive = theme.id == activeTheme.id
          val isFav = theme.id in favoriteIds
          PaperlySurfaceCard(
            spec = spec,
            selected = isActive,
            onClick = { inspectedTheme = theme },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("theme_card_${theme.id}")
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                      text = theme.name,
                      style = MaterialTheme.typography.titleMedium,
                      color = spec.textPrimaryColor,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                      Icon(Icons.Default.Check, contentDescription = "Active", tint = spec.accentColor, modifier = Modifier.size(16.dp))
                    }
                  }
                  Text(
                    text = "${theme.collection.displayName} • ${theme.surfaceMaterial.displayName} • ${theme.headingFont.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = spec.textSecondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { onToggleFavorite(theme.id) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Icon(
                      imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                      contentDescription = "Favourite theme",
                      tint = if (isFav) spec.accentColor else spec.textSecondaryColor
                    )
                  }
                  // Palette dots
                  Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(theme.primaryBgColor, theme.surfaceColor, theme.accentColor, theme.secondaryAccentColor).forEach { c ->
                      Box(
                        modifier = Modifier
                          .size(12.dp)
                          .clip(CircleShape)
                          .background(c)
                          .border(0.6.dp, spec.borderColor, CircleShape)
                      )
                    }
                  }
                }
              }

              // Real miniature application preview
              ThemeMiniWorkspacePreview(
                spec = theme,
                screenKind = previewScreenKind,
                modifier = Modifier.fillMaxWidth()
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Button(
                  onClick = { onApplyTheme(theme) },
                  shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
                  modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("apply_theme_${theme.id}")
                ) {
                  Text(if (isActive) "Applied" else "Apply Theme")
                }
                OutlinedButton(
                  onClick = {
                    val dup = theme.copy(
                      id = "custom_${System.currentTimeMillis()}",
                      name = "${theme.name} Copy",
                      isCustom = true
                    )
                    onOpenStudioWith(dup)
                  },
                  shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(4.dp))
                  Text("Customize")
                }
              }
            }
          }
        }
      }
    }
  }

  // Detailed preview & export dialog
  inspectedTheme?.let { t ->
    AlertDialog(
      onDismissRequest = { inspectedTheme = null },
      title = { Text(t.name) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(t.description, style = MaterialTheme.typography.bodySmall)
          Text(
            "Typography: ${t.headingFont.displayName} / ${t.bodyFont.displayName}\n" +
              "Material: ${t.surfaceMaterial.displayName} • Nav: ${t.navStyle.displayName}\n" +
              "Geometry: ${t.cornerRadiusDp.toInt()}dp radius • ${t.density.displayName} density",
            style = MaterialTheme.typography.labelSmall
          )
          ThemeMiniWorkspacePreview(spec = t, screenKind = previewScreenKind)
        }
      },
      confirmButton = {
        Button(onClick = {
          onApplyTheme(t)
          inspectedTheme = null
        }) { Text("Apply") }
      },
      dismissButton = {
        Row {
          TextButton(onClick = {
            clipboard.setText(AnnotatedString(ThemeJsonCodec.encode(t)))
            Toast.makeText(context, "Theme JSON copied to clipboard", Toast.LENGTH_SHORT).show()
          }) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Export JSON")
          }
          TextButton(onClick = { inspectedTheme = null }) { Text("Close") }
        }
      }
    )
  }

  if (showImportDialog) {
    AlertDialog(
      onDismissRequest = { showImportDialog = false },
      title = { Text("Import Theme JSON") },
      text = {
        OutlinedTextField(
          value = importJsonText,
          onValueChange = { importJsonText = it },
          label = { Text("Paste exported Paperly theme JSON") },
          minLines = 5,
          maxLines = 10,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(onClick = {
          val res = onImportThemeJson(importJsonText)
          if (res.isSuccess) {
            Toast.makeText(context, "Imported '${res.getOrNull()?.name}'", Toast.LENGTH_SHORT).show()
            importJsonText = ""
            showImportDialog = false
          } else {
            Toast.makeText(context, "Invalid theme JSON", Toast.LENGTH_SHORT).show()
          }
        }) {
          Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Import & Apply")
        }
      },
      dismissButton = {
        TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
      }
    )
  }
}
