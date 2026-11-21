package com.example.paperly.ui.settings

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.paperly.data.prefs.PaperlySettings
import com.example.paperly.data.prefs.PdfAppearanceMode
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlyHeaderBanner
import com.example.paperly.theme.PaperlySurfaceCard
import com.example.paperly.theme.PaperlyThemeSpec
import com.example.paperly.theme.ThemeJsonCodec
import com.example.paperly.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  settings: PaperlySettings,
  activeTheme: PaperlyThemeSpec,
  documentRepo: DocumentRepository,
  onOpenThemeGallery: () -> Unit,
  onOpenThemeStudio: () -> Unit,
  onThemeModeChange: (ThemeMode) -> Unit,
  onReducedMotionChange: (Boolean) -> Unit,
  onFontScaleChange: (Float) -> Unit,
  onBlurEnabledChange: (Boolean) -> Unit,
  onUpdatePdfDefaults: (Float?, Boolean?, Boolean?, Boolean?, PdfAppearanceMode?, Boolean?) -> Unit,
  onUpdateSheetDefaults: (Float?, Boolean?, Boolean?) -> Unit,
  onUpdateImageDefaults: (String?, Int?, String?) -> Unit,
  onUpdatePdfWorkshopDefaults: (String?, String?, String?) -> Unit,
  onClearHistory: () -> Unit,
  onBack: () -> Unit
) {
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()

  PaperlyCanvasBackground(spec = spec) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(Modifier.height(10.dp))
      PaperlyHeaderBanner(
        title = "Settings",
        subtitle = "Appearance, readers, workshops & offline storage",
        trailing = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = spec.textPrimaryColor)
          }
        }
      )
      Spacer(Modifier.height(10.dp))

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Appearance Section
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, color = spec.textPrimaryColor)
            Text("Current Theme: ${activeTheme.name} (${activeTheme.collection.displayName})", style = MaterialTheme.typography.bodySmall, color = spec.accentColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              Button(
                onClick = onOpenThemeGallery,
                shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_theme_gallery_btn")
              ) {
                Icon(Icons.Default.Palette, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Theme Gallery")
              }
              OutlinedButton(
                onClick = onOpenThemeStudio,
                shape = RoundedCornerShape(spec.buttonRadiusDp.dp),
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_theme_studio_btn")
              ) {
                Icon(Icons.Default.Brush, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Theme Studio")
              }
            }

            Text("Appearance Polarity", style = MaterialTheme.typography.labelLarge, color = spec.textPrimaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ThemeMode.entries.forEach { mode ->
                FilterChip(
                  selected = settings.themeMode == mode,
                  onClick = { onThemeModeChange(mode) },
                  label = { Text(mode.displayName) }
                )
              }
            }

            Text("Typography Scale: ${"%.2f".format(settings.fontScaleMultiplier)}x", style = MaterialTheme.typography.labelMedium, color = spec.textPrimaryColor)
            Slider(value = settings.fontScaleMultiplier, onValueChange = onFontScaleChange, valueRange = 0.85f..1.25f)

            SettingSwitchRow("Reduced Motion", settings.reducedMotion, onReducedMotionChange)
            SettingSwitchRow("Glass Blur Effects", settings.blurEnabled, onBlurEnabledChange)
          }
        }

        // PDF Reader Settings
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PDF Reader", style = MaterialTheme.typography.titleMedium, color = spec.textPrimaryColor)
            Text("Default Zoom: ${(settings.pdfDefaultZoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
              value = settings.pdfDefaultZoom,
              onValueChange = { onUpdatePdfDefaults(it, null, null, null, null, null) },
              valueRange = 0.75f..2.25f
            )
            SettingSwitchRow("Continuous Vertical Scroll", settings.pdfContinuousScroll) {
              onUpdatePdfDefaults(null, it, !it, null, null, null)
            }
            SettingSwitchRow("Remember Last Reading Position", settings.pdfRememberPosition) {
              onUpdatePdfDefaults(null, null, null, it, null, null)
            }
            SettingSwitchRow("Start in Full-Screen Mode", settings.pdfFullScreenDefault) {
              onUpdatePdfDefaults(null, null, null, null, null, it)
            }
            Text("Default Reading Appearance", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              PdfAppearanceMode.entries.forEach { ap ->
                FilterChip(
                  selected = settings.pdfDefaultAppearance == ap,
                  onClick = { onUpdatePdfDefaults(null, null, null, null, ap, null) },
                  label = { Text(ap.label) }
                )
              }
            }
          }
        }

        // Spreadsheet Viewer Settings
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Spreadsheet Viewer", style = MaterialTheme.typography.titleMedium, color = spec.textPrimaryColor)
            Text("Default Grid Zoom: ${(settings.sheetDefaultZoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(
              value = settings.sheetDefaultZoom,
              onValueChange = { onUpdateSheetDefaults(it, null, null) },
              valueRange = 0.75f..1.75f
            )
            SettingSwitchRow("Show Grid Lines", settings.sheetShowGridLines) {
              onUpdateSheetDefaults(null, it, null)
            }
            SettingSwitchRow("Show Row & Column Headers", settings.sheetShowHeaders) {
              onUpdateSheetDefaults(null, null, it)
            }
          }
        }

        // Image Workshop Defaults
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Image Workshop", style = MaterialTheme.typography.titleMedium, color = spec.textPrimaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf("JPEG", "PNG", "WEBP").forEach { fmt ->
                FilterChip(
                  selected = settings.imageDefaultFormat == fmt,
                  onClick = { onUpdateImageDefaults(fmt, null, null) },
                  label = { Text(fmt) }
                )
              }
            }
            Text("Default Quality: ${settings.imageDefaultQuality}%", style = MaterialTheme.typography.labelMedium)
            Slider(
              value = settings.imageDefaultQuality.toFloat(),
              onValueChange = { onUpdateImageDefaults(null, it.toInt(), null) },
              valueRange = 20f..100f
            )
            OutlinedTextField(
              value = settings.imageSuffix,
              onValueChange = { onUpdateImageDefaults(null, null, it) },
              label = { Text("Default Output Suffix") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        // PDF Workshop Defaults
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PDF Workshop", style = MaterialTheme.typography.titleMedium, color = spec.textPrimaryColor)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("A4", "Letter", "Legal").forEach { ps ->
                FilterChip(
                  selected = settings.pdfDefaultPageSize == ps,
                  onClick = { onUpdatePdfWorkshopDefaults(ps, null, null) },
                  label = { Text(ps) }
                )
              }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("Light", "Balanced", "Strong").forEach { comp ->
                FilterChip(
                  selected = settings.pdfDefaultCompression == comp,
                  onClick = { onUpdatePdfWorkshopDefaults(null, comp, null) },
                  label = { Text("Compression: $comp") }
                )
              }
            }
            Text("Originals are always preserved on every PDF and image operation.", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
          }
        }

        // Storage & Theme Profiles
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Storage & Theme Profiles", style = MaterialTheme.typography.titleMedium, color = spec.textPrimaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
              OutlinedButton(
                onClick = {
                  scope.launch {
                    val freed = documentRepo.clearTemporaryCache()
                    Toast.makeText(context, "Cleared ${Formatter.formatShortFileSize(context, freed)} of temp files", Toast.LENGTH_SHORT).show()
                  }
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
              ) { Text("Clear Temp Cache") }
              OutlinedButton(
                onClick = {
                  onClearHistory()
                  Toast.makeText(context, "Cleared recent history (documents untouched)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("settings_clear_history_btn")
              ) {
                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear History")
              }
            }
            OutlinedButton(
              onClick = {
                clipboard.setText(AnnotatedString(ThemeJsonCodec.encode(activeTheme)))
                Toast.makeText(context, "Active theme profile JSON copied", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
            ) {
              Text("Export Active Theme Profile (JSON)")
            }
          }
        }

        // Offline Privacy Verification Card
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Lock, null, tint = spec.accentColor)
            Column {
              Text("Strictly Offline Architecture", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
              Text(
                "Zero internet permissions, zero analytics, zero cloud processing. Every document, font, and theme stays on your device.",
                style = MaterialTheme.typography.bodySmall,
                color = spec.textSecondaryColor
              )
            }
          }
        }

        Spacer(Modifier.height(88.dp))
      }
    }
  }
}

@Composable
private fun SettingSwitchRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  val spec = LocalPaperlyTheme.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = spec.textPrimaryColor, modifier = Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
