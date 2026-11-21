package com.example.paperly.ui.privacystudio

import android.graphics.Color as AndroidColor
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.engines.privacy.PlaceholderPreset
import com.example.paperly.engines.privacy.PrivacyEditMode
import com.example.paperly.engines.privacy.PrivacyVerificationReport
import com.example.paperly.engines.privacy.ReplacementFontStyle
import com.example.paperly.engines.privacy.TextAlignOption
import com.example.paperly.engines.privacy.TextReplacementConfig
import com.example.paperly.engines.privacy.VerificationStatus
import com.example.paperly.theme.PaperlySurfaceCard
import com.example.paperly.theme.PaperlyThemeSpec
import java.io.File

val privacyRedactionSwatches = listOf(
  AndroidColor.BLACK,
  0xFF1E293B.toInt(),
  0xFFFFFFFF.toInt(),
  0xFF991B1B.toInt(),
  0xFF1E3A8A.toInt()
)

val replacementBgSwatches = listOf(
  0xFFF1F5F9.toInt(),
  0xFFFFFFFF.toInt(),
  0xFFFEF3C7.toInt(),
  0xFF0F172A.toInt()
)

val replacementTextSwatches = listOf(
  0xFF0F172A.toInt(),
  0xFF1E3A8A.toInt(),
  0xFF991B1B.toInt(),
  0xFFFFFFFF.toInt()
)

@Composable
fun PrivacyModeSelectorBar(
  selectedMode: PrivacyEditMode,
  onSelectMode: (PrivacyEditMode) -> Unit,
  spec: PaperlyThemeSpec
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      PrivacyEditMode.entries.forEach { mode ->
        FilterChip(
          selected = selectedMode == mode,
          onClick = { onSelectMode(mode) },
          label = { Text(mode.label) },
          modifier = Modifier.testTag("privacy_mode_${mode.name.lowercase()}")
        )
      }
    }
    if (selectedMode == PrivacyEditMode.BLUR_PIXELATE) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(spec.warningColor.copy(alpha = 0.14f))
          .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(Icons.Default.Warning, contentDescription = "Blur warning", tint = spec.warningColor, modifier = Modifier.size(18.dp))
        Text(
          text = "Visual concealment only: blur and pixelation are NOT equivalent to permanent redaction. Use Permanent Redaction for highly sensitive records.",
          style = MaterialTheme.typography.labelSmall,
          color = spec.textPrimaryColor
        )
      }
    }
  }
}

@Composable
fun ReplacementStylePanel(
  config: TextReplacementConfig,
  onChange: (TextReplacementConfig) -> Unit,
  spec: PaperlyThemeSpec
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Placeholder Presets (Fictional Only)", style = MaterialTheme.typography.labelLarge, color = spec.textPrimaryColor)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      PlaceholderPreset.entries.forEach { preset ->
        FilterChip(
          selected = config.text == preset.placeholder,
          onClick = { onChange(config.copy(text = preset.placeholder)) },
          label = { Text("${preset.label}: ${preset.placeholder}") },
          modifier = Modifier.testTag("preset_${preset.name.lowercase()}")
        )
      }
    }
    OutlinedTextField(
      value = config.text,
      onValueChange = { onChange(config.copy(text = it)) },
      label = { Text("Custom Replacement Placeholder") },
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("replacement_custom_input")
    )
    Text("Typography & Alignment", style = MaterialTheme.typography.labelMedium, color = spec.textSecondaryColor)
    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      ReplacementFontStyle.entries.forEach { font ->
        FilterChip(
          selected = config.fontStyle == font,
          onClick = { onChange(config.copy(fontStyle = font)) },
          label = { Text(font.label) }
        )
      }
      TextAlignOption.entries.forEach { align ->
        FilterChip(
          selected = config.alignment == align,
          onClick = { onChange(config.copy(alignment = align)) },
          label = { Text(align.label) }
        )
      }
    }
    Text("Text Size: ${config.fontSizeSp.toInt()} pt", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
    Slider(
      value = config.fontSizeSp,
      onValueChange = { onChange(config.copy(fontSizeSp = it)) },
      valueRange = 8f..26f
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Text Colour", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          replacementTextSwatches.forEach { col ->
            SwatchDot(col, config.textColorArgb == col, spec) { onChange(config.copy(textColorArgb = col)) }
          }
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Fill Colour", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          replacementBgSwatches.forEach { col ->
            SwatchDot(col, config.backgroundColorArgb == col, spec) { onChange(config.copy(backgroundColorArgb = col)) }
          }
        }
      }
    }
  }
}

@Composable
fun SwatchDot(
  argb: Int,
  selected: Boolean,
  spec: PaperlyThemeSpec,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(28.dp)
      .clip(CircleShape)
      .background(Color(argb))
      .border(if (selected) 2.5.dp else 1.dp, if (selected) spec.accentColor else spec.borderColor, CircleShape)
      .clickable(onClick = onClick)
  )
}

@Composable
fun PrivacyVerificationCard(
  report: PrivacyVerificationReport,
  mimeType: String,
  spec: PaperlyThemeSpec,
  onOpenPdf: ((File) -> Unit)? = null,
  onSaveAs: (File) -> Unit,
  onShare: (File) -> Unit
) {
  val context = LocalContext.current
  PaperlySurfaceCard(spec = spec, selected = report.verifiedSafeToShare, modifier = Modifier.fillMaxWidth().testTag("privacy_verification_card")) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
          imageVector = if (report.verifiedSafeToShare) Icons.Default.Shield else Icons.Default.Warning,
          contentDescription = "Verification status",
          tint = if (report.verifiedSafeToShare) spec.accentColor else spec.warningColor
        )
        Column(Modifier.weight(1f)) {
          Text(
            text = if (report.verifiedSafeToShare) "Preflight Verification Complete" else "Verification Requires Review",
            style = MaterialTheme.typography.titleSmall,
            color = spec.textPrimaryColor
          )
          Text(
            text = "${report.displayName} • ${Formatter.formatShortFileSize(context, report.outputBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = spec.textSecondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Text(report.summaryText, style = MaterialTheme.typography.bodySmall, color = spec.textPrimaryColor)

      report.checks.forEach { check ->
        val (badgeColor, badgeIcon) = when (check.status) {
          VerificationStatus.REMOVED_AND_VERIFIED -> spec.accentColor to Icons.Default.CheckCircle
          VerificationStatus.NOT_DETECTED -> spec.textSecondaryColor to Icons.Default.Info
          VerificationStatus.RETAINED_INTENTIONALLY -> spec.warningColor to Icons.Default.Warning
          VerificationStatus.UNSUPPORTED -> spec.warningColor to Icons.Default.Info
          VerificationStatus.REMOVAL_ATTEMPTED_UNVERIFIED -> spec.warningColor to Icons.Default.Warning
          VerificationStatus.REMOVAL_FAILED -> spec.errorColor to Icons.Default.Error
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(spec.surfaceVariantColor)
            .padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(badgeIcon, contentDescription = check.status.label, tint = badgeColor, modifier = Modifier.size(18.dp))
          Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
              Text(check.itemName, style = MaterialTheme.typography.labelMedium, color = spec.textPrimaryColor, fontWeight = FontWeight.SemiBold)
              Text(check.status.label, style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
            }
            Text(check.detail, style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
          }
        }
      }

      report.warnings.forEach { w ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(spec.warningColor.copy(alpha = 0.14f))
            .padding(8.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Warning, contentDescription = null, tint = spec.warningColor, modifier = Modifier.size(16.dp))
          Text(w, style = MaterialTheme.typography.labelSmall, color = spec.textPrimaryColor)
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (onOpenPdf != null && mimeType == "application/pdf") {
          OutlinedButton(
            onClick = { onOpenPdf(report.outputFile) },
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize()
          ) {
            Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Open")
          }
        }
        OutlinedButton(
          onClick = { onSaveAs(report.outputFile) },
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("privacy_save_as_button")
        ) {
          Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Save As")
        }
        Button(
          onClick = { onShare(report.outputFile) },
          modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("privacy_share_button")
        ) {
          Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Share")
        }
      }
    }
  }
}
