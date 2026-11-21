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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RestartAlt
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.paperly.theme.AnimationIntensity
import com.example.paperly.theme.DensityMode
import com.example.paperly.theme.FontFamilyKey
import com.example.paperly.theme.HeaderStyle
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.NavStyle
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlyHeaderBanner
import com.example.paperly.theme.PaperlySurfaceCard
import com.example.paperly.theme.PaperlyThemeSpec
import com.example.paperly.theme.SurfaceMaterial
import com.example.paperly.theme.TextureOverlay
import com.example.paperly.theme.ThemeJsonCodec

private enum class StudioTab(val label: String) {
  COLORS("Colours"),
  MATERIALS("Materials"),
  TYPOGRAPHY("Typography"),
  GEOMETRY("Geometry"),
  BEHAVIOR("Behaviour")
}

private val curatedColorSwatches: List<Long> = listOf(
  0xFFFFFFFF, 0xFFFAF8F5, 0xFFF3F6FA, 0xFF0F172A, 0xFF000000, 0xFF141218,
  0xFF0284C7, 0xFF2563EB, 0xFF7C3AED, 0xFFDB2777, 0xFFDC2626, 0xFFD97706,
  0xFF15803D, 0xFF0D9488, 0xFF38BDF8, 0xFFF472B6, 0xFFA3E635, 0xFFFBBF24
)

@Composable
fun ThemeStudioScreen(
  initialTheme: PaperlyThemeSpec,
  onSaveTheme: (PaperlyThemeSpec) -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val clipboard = LocalClipboardManager.current

  var draft by remember(initialTheme.id) {
    mutableStateOf(
      if (initialTheme.isCustom) initialTheme
      else initialTheme.copy(
        id = "custom_${System.currentTimeMillis()}",
        name = "${initialTheme.name} Studio",
        isCustom = true
      )
    )
  }
  var activeTab by remember { mutableStateOf(StudioTab.COLORS) }
  var previewScreen by remember { mutableStateOf(PreviewScreenKind.HOME) }

  PaperlyCanvasBackground(spec = draft) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      Spacer(Modifier.height(10.dp))
      PaperlyHeaderBanner(
        title = "Theme Studio",
        subtitle = "Live-preview every change across Paperly",
        spec = draft,
        trailing = {
          Row {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = draft.textPrimaryColor)
            }
            IconButton(
              onClick = {
                clipboard.setText(AnnotatedString(ThemeJsonCodec.encode(draft)))
                Toast.makeText(context, "Exported theme JSON to clipboard", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = "Export theme JSON", tint = draft.textPrimaryColor)
            }
            IconButton(
              onClick = { draft = initialTheme.copy(id = draft.id, isCustom = true) },
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(Icons.Default.RestartAlt, contentDescription = "Reset draft", tint = draft.textSecondaryColor)
            }
          }
        }
      )

      Spacer(Modifier.height(8.dp))

      // Live screen preview switcher
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        PreviewScreenKind.entries.forEach { kind ->
          FilterChip(
            selected = previewScreen == kind,
            onClick = { previewScreen = kind },
            label = { Text(kind.label) }
          )
        }
      }

      ThemeMiniWorkspacePreview(
        spec = draft,
        screenKind = previewScreen,
        modifier = Modifier.fillMaxWidth()
      )

      Spacer(Modifier.height(10.dp))

      // Studio category tabs
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        StudioTab.entries.forEach { tab ->
          FilterChip(
            selected = activeTab == tab,
            onClick = { activeTab = tab },
            label = { Text(tab.label) },
            modifier = Modifier.testTag("studio_tab_${tab.name.lowercase()}")
          )
        }
      }

      Spacer(Modifier.height(8.dp))

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        OutlinedTextField(
          value = draft.name,
          onValueChange = { draft = draft.copy(name = it) },
          label = { Text("Theme Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("studio_theme_name_input")
        )

        when (activeTab) {
          StudioTab.COLORS -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Dark Theme Polarity", style = MaterialTheme.typography.titleSmall, color = draft.textPrimaryColor)
              Switch(checked = draft.isDark, onCheckedChange = { draft = draft.copy(isDark = it) })
            }
            ColorRoleRow("Primary Background", draft.primaryBg) { draft = draft.copy(primaryBg = it, gradientStart = it) }
            ColorRoleRow("Secondary Background", draft.secondaryBg) { draft = draft.copy(secondaryBg = it, gradientEnd = it) }
            ColorRoleRow("Surface Card", draft.surface) { draft = draft.copy(surface = it) }
            ColorRoleRow("Accent Colour", draft.accent) { draft = draft.copy(accent = it) }
            ColorRoleRow("Secondary Accent", draft.secondaryAccent) { draft = draft.copy(secondaryAccent = it) }
            ColorRoleRow("Primary Text", draft.textPrimary) { draft = draft.copy(textPrimary = it) }
            ColorRoleRow("Secondary Text", draft.textSecondary) { draft = draft.copy(textSecondary = it) }
            ColorRoleRow("Borders", draft.border) { draft = draft.copy(border = it) }
            ColorRoleRow("Selected Container", draft.selectedContainer) { draft = draft.copy(selectedContainer = it) }
            ColorRoleRow("Warning", draft.warning) { draft = draft.copy(warning = it) }
            ColorRoleRow("Error", draft.error) { draft = draft.copy(error = it) }
          }

          StudioTab.MATERIALS -> {
            Text("Surface Material", style = MaterialTheme.typography.titleSmall, color = draft.textPrimaryColor)
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              SurfaceMaterial.entries.forEach { mat ->
                FilterChip(
                  selected = draft.surfaceMaterial == mat,
                  onClick = { draft = draft.copy(surfaceMaterial = mat) },
                  label = { Text(mat.displayName) }
                )
              }
            }
            Text("Texture Overlay", style = MaterialTheme.typography.titleSmall, color = draft.textPrimaryColor)
            Row(
              modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              TextureOverlay.entries.forEach { tex ->
                FilterChip(
                  selected = draft.textureOverlay == tex,
                  onClick = { draft = draft.copy(textureOverlay = tex) },
                  label = { Text(tex.displayName) }
                )
              }
            }
            Text("Surface Opacity: ${(draft.transparencyAlpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = draft.textPrimaryColor)
            Slider(
              value = draft.transparencyAlpha,
              onValueChange = { draft = draft.copy(transparencyAlpha = it) },
              valueRange = 0.5f..1.0f
            )
            Text("Blur Intensity: ${draft.blurRadiusDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, color = draft.textPrimaryColor)
            Slider(
              value = draft.blurRadiusDp,
              onValueChange = { draft = draft.copy(blurRadiusDp = it) },
              valueRange = 0f..28f
            )
          }

          StudioTab.TYPOGRAPHY -> {
            FontPickerRow("Heading Font", draft.headingFont) { draft = draft.copy(headingFont = it) }
            FontPickerRow("Body Font", draft.bodyFont) { draft = draft.copy(bodyFont = it) }
            FontPickerRow("Monospace Font", draft.monoFont) { draft = draft.copy(monoFont = it) }
            Text("Font Scale: ${"%.2f".format(draft.fontSizeScale)}x", style = MaterialTheme.typography.labelMedium, color = draft.textPrimaryColor)
            Slider(
              value = draft.fontSizeScale,
              onValueChange = { draft = draft.copy(fontSizeScale = it) },
              valueRange = 0.85f..1.25f
            )
            Text("Line Height: ${"%.2f".format(draft.lineHeightMultiplier)}x", style = MaterialTheme.typography.labelMedium, color = draft.textPrimaryColor)
            Slider(
              value = draft.lineHeightMultiplier,
              onValueChange = { draft = draft.copy(lineHeightMultiplier = it) },
              valueRange = 1.15f..1.65f
            )
          }

          StudioTab.GEOMETRY -> {
            Text("Corner Radius: ${draft.cornerRadiusDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, color = draft.textPrimaryColor)
            Slider(
              value = draft.cornerRadiusDp,
              onValueChange = { draft = draft.copy(cornerRadiusDp = it) },
              valueRange = 0f..28f
            )
            Text("Button Radius: ${draft.buttonRadiusDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, color = draft.textPrimaryColor)
            Slider(
              value = draft.buttonRadiusDp,
              onValueChange = { draft = draft.copy(buttonRadiusDp = it) },
              valueRange = 0f..36f
            )
            Text("Border Width: ${"%.1f".format(draft.borderWidthDp)} dp", style = MaterialTheme.typography.labelMedium, color = draft.textPrimaryColor)
            Slider(
              value = draft.borderWidthDp,
              onValueChange = { draft = draft.copy(borderWidthDp = it) },
              valueRange = 0f..3f
            )
            Text("Hard Offset Shadow: ${draft.hardShadowOffsetDp.toInt()} dp", style = MaterialTheme.typography.labelMedium, color = draft.textPrimaryColor)
            Slider(
              value = draft.hardShadowOffsetDp,
              onValueChange = { draft = draft.copy(hardShadowOffsetDp = it) },
              valueRange = 0f..8f
            )
            Text("Interface Density", style = MaterialTheme.typography.titleSmall, color = draft.textPrimaryColor)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              DensityMode.entries.forEach { d ->
                FilterChip(selected = draft.density == d, onClick = { draft = draft.copy(density = d) }, label = { Text(d.displayName) })
              }
            }
            Text("Navigation Style", style = MaterialTheme.typography.titleSmall, color = draft.textPrimaryColor)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              NavStyle.entries.forEach { ns ->
                FilterChip(selected = draft.navStyle == ns, onClick = { draft = draft.copy(navStyle = ns) }, label = { Text(ns.displayName) })
              }
            }
            Text("Header Style", style = MaterialTheme.typography.titleSmall, color = draft.textPrimaryColor)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              HeaderStyle.entries.forEach { hs ->
                FilterChip(selected = draft.headerStyle == hs, onClick = { draft = draft.copy(headerStyle = hs) }, label = { Text(hs.displayName) })
              }
            }
          }

          StudioTab.BEHAVIOR -> {
            Text("Animation Intensity", style = MaterialTheme.typography.titleSmall, color = draft.textPrimaryColor)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              AnimationIntensity.entries.forEach { a ->
                FilterChip(selected = draft.animationIntensity == a, onClick = { draft = draft.copy(animationIntensity = a) }, label = { Text(a.displayName) })
              }
            }
            Text("Transition Duration: ${draft.transitionDurationMs} ms", style = MaterialTheme.typography.labelMedium, color = draft.textPrimaryColor)
            Slider(
              value = draft.transitionDurationMs.toFloat(),
              onValueChange = { draft = draft.copy(transitionDurationMs = it.toInt()) },
              valueRange = 0f..450f
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Reduced Motion Mode", style = MaterialTheme.typography.titleSmall, color = draft.textPrimaryColor)
              Switch(checked = draft.reducedMotion, onCheckedChange = { draft = draft.copy(reducedMotion = it) })
            }
          }
        }
        Spacer(Modifier.height(12.dp))
      }

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = {
            val dup = draft.copy(id = "custom_${System.currentTimeMillis()}", name = "${draft.name} Copy", isCustom = true)
            onSaveTheme(dup)
            Toast.makeText(context, "Saved copy '${dup.name}'", Toast.LENGTH_SHORT).show()
          },
          shape = RoundedCornerShape(draft.buttonRadiusDp.dp),
          modifier = Modifier.minimumInteractiveComponentSize()
        ) {
          Text("Save Copy")
        }
        Button(
          onClick = {
            val finalTheme = draft.copy(name = draft.name.trim().ifBlank { "My Custom Theme" }, isCustom = true)
            onSaveTheme(finalTheme)
            Toast.makeText(context, "Saved & applied '${finalTheme.name}'", Toast.LENGTH_SHORT).show()
          },
          shape = RoundedCornerShape(draft.buttonRadiusDp.dp),
          modifier = Modifier
            .weight(1f)
            .minimumInteractiveComponentSize()
            .testTag("studio_save_theme_button")
        ) {
          Icon(Icons.Default.Check, null)
          Spacer(Modifier.width(8.dp))
          Text("Save & Apply Theme")
        }
      }
    }
  }
}

@Composable
private fun ColorRoleRow(
  label: String,
  currentColor: Long,
  onPick: (Long) -> Unit
) {
  val spec = LocalPaperlyTheme.current
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(label, style = MaterialTheme.typography.labelLarge, color = spec.textPrimaryColor)
      Box(
        modifier = Modifier
          .size(22.dp)
          .clip(CircleShape)
          .background(Color(currentColor))
          .border(1.dp, spec.borderColor, CircleShape)
      )
    }
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      curatedColorSwatches.forEach { swatch ->
        val selected = swatch == currentColor
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(swatch))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) spec.accentColor else spec.borderColor, CircleShape)
            .clickable { onPick(swatch) }
        )
      }
    }
  }
}

@Composable
private fun FontPickerRow(
  label: String,
  selectedFont: FontFamilyKey,
  onSelect: (FontFamilyKey) -> Unit
) {
  val spec = LocalPaperlyTheme.current
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
    Row(
      modifier = Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FontFamilyKey.entries.forEach { f ->
        FilterChip(
          selected = selectedFont == f,
          onClick = { onSelect(f) },
          label = { Text(f.displayName, fontFamily = f.toComposeFontFamily(), fontWeight = FontWeight.Medium) }
        )
      }
    }
  }
}
