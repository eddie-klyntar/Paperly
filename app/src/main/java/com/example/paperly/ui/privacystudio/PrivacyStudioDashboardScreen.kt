package com.example.paperly.ui.privacystudio

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.paperly.data.db.RecentPrivacyProjectEntity
import com.example.paperly.data.repo.DocumentRepository
import com.example.paperly.engines.privacy.PrivacyEditMode
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlyHeaderBanner
import com.example.paperly.theme.PaperlySurfaceCard
import com.example.paperly.theme.PaperlyThemeSpec
import java.io.File

@Composable
fun PrivacyStudioDashboardScreen(
  recentProjects: List<RecentPrivacyProjectEntity>,
  documentRepo: DocumentRepository,
  onOpenPdfEditor: (Uri?, PrivacyEditMode) -> Unit,
  onOpenImageEditor: (Uri?, PrivacyEditMode) -> Unit,
  onOpenBatch: () -> Unit,
  onDeleteProject: (String) -> Unit,
  onClearProjects: () -> Unit,
  onBack: (() -> Unit)? = null
) {
  val spec = LocalPaperlyTheme.current
  val context = LocalContext.current
  var pendingModeForChoice by remember { mutableStateOf<PrivacyEditMode?>(null) }

  val pickPdfForRedact = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) onOpenPdfEditor(uri, PrivacyEditMode.PERMANENT_REDACTION)
  }
  val pickPhotoForRedact = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) onOpenImageEditor(uri, PrivacyEditMode.PERMANENT_REDACTION)
  }
  val pickPhotoForBlur = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) onOpenImageEditor(uri, PrivacyEditMode.BLUR_PIXELATE)
  }
  val pickPdfForChoice = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    val mode = pendingModeForChoice ?: PrivacyEditMode.REPLACE_ANONYMISE
    pendingModeForChoice = null
    if (uri != null) onOpenPdfEditor(uri, mode)
  }
  val pickPhotoForChoice = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    val mode = pendingModeForChoice ?: PrivacyEditMode.REPLACE_ANONYMISE
    pendingModeForChoice = null
    if (uri != null) onOpenImageEditor(uri, mode)
  }

  var showSourceChooserForMode by remember { mutableStateOf<PrivacyEditMode?>(null) }

  PaperlyCanvasBackground(spec = spec) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Spacer(Modifier.height(10.dp))
      PaperlyHeaderBanner(
        title = "Privacy Studio",
        subtitle = "100% On-Device Redaction, Concealment & Anonymisation",
        trailing = if (onBack != null) {
          {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = spec.textPrimaryColor)
            }
          }
        } else null
      )

      // Commitment Strip
      PaperlySurfaceCard(spec = spec, selected = true, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Icon(Icons.Default.Security, contentDescription = null, tint = spec.accentColor, modifier = Modifier.size(26.dp))
          Column(Modifier.weight(1f)) {
            Text("Local Zero-Upload Guarantee", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
            Text(
              "All inspection, stream stripping, pixel replacement and verification execute strictly on this device. Original files are never overwritten.",
              style = MaterialTheme.typography.bodySmall,
              color = spec.textSecondaryColor
            )
          }
        }
      }

      // 6 Core Dashboard Options
      Text("Privacy Tools", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PrivacyDashboardCard(
          title = "Redact PDF",
          subtitle = "Strip text, metadata & forms permanently",
          icon = Icons.Default.PictureAsPdf,
          spec = spec,
          testTag = "ps_card_redact_pdf",
          modifier = Modifier.weight(1f),
          onClick = { pickPdfForRedact.launch(arrayOf("application/pdf")) }
        )
        PrivacyDashboardCard(
          title = "Redact Image",
          subtitle = "Opaque flattened fill & EXIF strip",
          icon = Icons.Default.HideImage,
          spec = spec,
          testTag = "ps_card_redact_image",
          modifier = Modifier.weight(1f),
          onClick = { pickPhotoForRedact.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PrivacyDashboardCard(
          title = "Blur & Pixelate",
          subtitle = "Gaussian blur & mosaic brush",
          icon = Icons.Default.BlurOn,
          spec = spec,
          testTag = "ps_card_blur_pixelate",
          modifier = Modifier.weight(1f),
          onClick = { showSourceChooserForMode = PrivacyEditMode.BLUR_PIXELATE }
        )
        PrivacyDashboardCard(
          title = "Replace Personal Info",
          subtitle = "Fictional placeholders & styling",
          icon = Icons.Default.FindReplace,
          spec = spec,
          testTag = "ps_card_replace_info",
          modifier = Modifier.weight(1f),
          onClick = { showSourceChooserForMode = PrivacyEditMode.REPLACE_ANONYMISE }
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        PrivacyDashboardCard(
          title = "Batch Privacy Editing",
          subtitle = "Multi-file queue & per-doc check",
          icon = Icons.Default.DynamicFeed,
          spec = spec,
          testTag = "ps_card_batch",
          modifier = Modifier.weight(1f),
          onClick = onOpenBatch
        )
        PrivacyDashboardCard(
          title = "Open Blank Workspace",
          subtitle = "Start in PDF or Image editor",
          icon = Icons.Default.Shield,
          spec = spec,
          testTag = "ps_card_workspace",
          modifier = Modifier.weight(1f),
          onClick = { showSourceChooserForMode = PrivacyEditMode.PERMANENT_REDACTION }
        )
      }

      // Honest Comparison & 8-Step Workflow Card
      PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Choosing the Right Mode", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
          Text(
            "• Permanent Redaction removes PDF text/graphic streams, metadata, forms and image pixels irreversibly.",
            style = MaterialTheme.typography.bodySmall,
            color = spec.textSecondaryColor
          )
          Text(
            "• Blur & Pixelate visually obscure faces or backgrounds, but are NOT equivalent to secure redaction.",
            style = MaterialTheme.typography.bodySmall,
            color = spec.warningColor
          )
          Text(
            "• Replace & Anonymise removes original content and places non-identifying placeholders like [PERSON A] or user@example.invalid.",
            style = MaterialTheme.typography.bodySmall,
            color = spec.textSecondaryColor
          )
          Spacer(Modifier.height(2.dp))
          Text(
            "Workflow: 1. Select file → 2. Open workspace → 3. Choose mode → 4. Mark sensitive regions → 5. Preview → 6. Export copy → 7. Verify → 8. Share.",
            style = MaterialTheme.typography.labelSmall,
            color = spec.accentColor
          )
        }
      }

      // Recent Privacy Projects
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Recent Privacy Projects (${recentProjects.size})", style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor)
        if (recentProjects.isNotEmpty()) {
          TextButton(onClick = onClearProjects) {
            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Clear History")
          }
        }
      }

      if (recentProjects.isEmpty()) {
        PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
          Text(
            "No privacy exports yet. Processed files appear here without storing redacted text or secrets.",
            style = MaterialTheme.typography.bodySmall,
            color = spec.textSecondaryColor
          )
        }
      } else {
        recentProjects.forEach { proj ->
          val fileExists = File(proj.outputFilePath).exists()
          PaperlySurfaceCard(spec = spec, modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(
                imageVector = if (proj.sourceKind == "PDF") Icons.Default.Description else Icons.Default.HideImage,
                contentDescription = proj.sourceKind,
                tint = if (proj.verifiedSafe) spec.accentColor else spec.warningColor
              )
              Column(Modifier.weight(1f)) {
                Text(proj.outputDisplayName, style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  "${proj.lastMode.replace('_', ' ')} • ${proj.operationCount} edits • ${DateUtils.getRelativeTimeSpanString(proj.updatedAt)}",
                  style = MaterialTheme.typography.labelSmall,
                  color = spec.textSecondaryColor,
                  maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  Icon(Icons.Default.CheckCircle, null, tint = spec.accentColor, modifier = Modifier.size(13.dp))
                  Text(proj.verificationSummary, style = MaterialTheme.typography.labelSmall, color = spec.accentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
              if (fileExists) {
                IconButton(
                  onClick = {
                    val f = File(proj.outputFilePath)
                    val mime = if (proj.sourceKind == "PDF") "application/pdf" else "image/*"
                    context.startActivity(documentRepo.createShareIntent(f, mime, "Share Sanitised File"))
                  },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Default.Share, contentDescription = "Share exported copy", tint = spec.accentColor)
                }
              }
              IconButton(
                onClick = { onDeleteProject(proj.id) },
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Icon(Icons.Default.Close, contentDescription = "Remove project entry", tint = spec.textSecondaryColor)
              }
            }
          }
        }
      }

      Spacer(Modifier.height(92.dp))
    }
  }

  showSourceChooserForMode?.let { mode ->
    AlertDialog(
      onDismissRequest = { showSourceChooserForMode = null },
      title = { Text(mode.label) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Choose a document type to open in ${mode.label} mode:")
          Button(
            onClick = {
              pendingModeForChoice = mode
              showSourceChooserForMode = null
              pickPdfForChoice.launch(arrayOf("application/pdf"))
            },
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
          ) {
            Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Select PDF Document")
          }
          OutlinedButton(
            onClick = {
              pendingModeForChoice = mode
              showSourceChooserForMode = null
              if (mode == PrivacyEditMode.BLUR_PIXELATE) {
                pickPhotoForBlur.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              } else {
                pickPhotoForChoice.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              }
            },
            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()
          ) {
            Icon(Icons.Default.HideImage, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Select Image from Gallery")
          }
        }
      },
      confirmButton = {
        TextButton(onClick = {
          showSourceChooserForMode = null
          onOpenImageEditor(null, mode)
        }) { Text("Open Empty Image Editor") }
      },
      dismissButton = {
        TextButton(onClick = { showSourceChooserForMode = null }) { Text("Cancel") }
      }
    )
  }
}

@Composable
private fun PrivacyDashboardCard(
  title: String,
  subtitle: String,
  icon: ImageVector,
  spec: PaperlyThemeSpec,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  PaperlySurfaceCard(
    spec = spec,
    onClick = onClick,
    contentPadding = PaddingValues(12.dp),
    modifier = modifier
      .minimumInteractiveComponentSize()
      .testTag(testTag)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(spec.buttonRadiusDp.dp))
          .background(spec.selectedContainerColor),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = title, tint = spec.accentColor, modifier = Modifier.size(20.dp))
      }
      Text(title, style = MaterialTheme.typography.titleSmall, color = spec.textPrimaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(subtitle, style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
  }
}
