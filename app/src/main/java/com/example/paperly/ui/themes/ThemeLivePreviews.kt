package com.example.paperly.ui.themes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.paperly.theme.HeaderStyle
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlySurfaceCard
import com.example.paperly.theme.PaperlyThemeRoot
import com.example.paperly.theme.PaperlyThemeSpec

enum class PreviewScreenKind(val label: String) {
  HOME("Home"),
  PDF("PDF Reader"),
  SPREADSHEET("Sheets"),
  IMAGE_WORKSHOP("Image Lab"),
  PDF_WORKSHOP("PDF Lab")
}

@Composable
fun ThemeMiniWorkspacePreview(
  spec: PaperlyThemeSpec,
  screenKind: PreviewScreenKind = PreviewScreenKind.HOME,
  modifier: Modifier = Modifier
) {
  PaperlyThemeRoot(themeSpec = spec) {
    val frameShape = RoundedCornerShape(spec.cornerRadiusDp.coerceAtMost(20f).dp)
    Box(
      modifier = modifier
        .clip(frameShape)
        .border(BorderStroke(spec.borderWidthDp.coerceAtLeast(0.8f).dp, spec.borderColor), frameShape)
    ) {
      PaperlyCanvasBackground(spec = spec, modifier = Modifier.fillMaxWidth()) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Simulated top header reflecting HeaderStyle + Typography
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = when (spec.headerStyle) {
                HeaderStyle.TERMINAL_PROMPT -> "> paperly/${screenKind.label.lowercase()}"
                HeaderStyle.BRUTALIST_BANNER -> "PAPERLY // ${screenKind.label.uppercase()}"
                else -> "Paperly • ${screenKind.label}"
              },
              style = MaterialTheme.typography.titleSmall,
              fontFamily = spec.headingFont.toComposeFontFamily(),
              color = spec.textPrimaryColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(spec.buttonRadiusDp.dp))
                .background(spec.accentColor)
                .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
              Text(
                text = spec.surfaceMaterial.displayName,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
              )
            }
          }

          when (screenKind) {
            PreviewScreenKind.HOME -> {
              PaperlySurfaceCard(spec = spec, contentPadding = PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                  Icon(Icons.Default.Description, null, tint = spec.accentColor, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Column(Modifier.weight(1f)) {
                    Text("Annual_Report_2026.pdf", style = MaterialTheme.typography.labelMedium, color = spec.textPrimaryColor, maxLines = 1)
                    Text("24 pages • 3.2 MB", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                  }
                }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                PaperlySurfaceCard(spec = spec, selected = true, contentPadding = PaddingValues(8.dp), modifier = Modifier.weight(1f)) {
                  Text("PDF Reader", style = MaterialTheme.typography.labelSmall, color = spec.accentColor, fontWeight = FontWeight.Bold)
                }
                PaperlySurfaceCard(spec = spec, contentPadding = PaddingValues(8.dp), modifier = Modifier.weight(1f)) {
                  Text("Sheets", style = MaterialTheme.typography.labelSmall, color = spec.textPrimaryColor)
                }
              }
            }

            PreviewScreenKind.PDF -> {
              PaperlySurfaceCard(spec = spec, contentPadding = PaddingValues(10.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Page 4 of 18", style = MaterialTheme.typography.labelSmall, color = spec.accentColor)
                    Text("125%", fontFamily = spec.monoFont.toComposeFontFamily(), fontSize = 10.sp, color = spec.textSecondaryColor)
                  }
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(48.dp)
                      .clip(RoundedCornerShape(4.dp))
                      .background(Color.White)
                      .padding(6.dp)
                  ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                      Box(Modifier.fillMaxWidth(0.65f).height(5.dp).background(Color(0xFF1E293B)))
                      Box(Modifier.fillMaxWidth().height(3.dp).background(Color(0xFF64748B)))
                      Box(Modifier.fillMaxWidth(0.85f).height(3.dp).background(Color(0xFF94A3B8)))
                    }
                  }
                }
              }
            }

            PreviewScreenKind.SPREADSHEET -> {
              PaperlySurfaceCard(spec = spec, contentPadding = PaddingValues(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                  Row(Modifier.fillMaxWidth()) {
                    listOf("A", "Item", "Q4 Total").forEachIndexed { i, h ->
                      Box(
                        modifier = Modifier
                          .weight(if (i == 0) 0.5f else 1f)
                          .background(spec.selectedContainerColor)
                          .border(0.5.dp, spec.borderColor)
                          .padding(4.dp)
                      ) {
                        Text(h, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = spec.textPrimaryColor)
                      }
                    }
                  }
                  Row(Modifier.fillMaxWidth()) {
                    listOf("1", "Paper Stock", "$1,420.00").forEachIndexed { i, v ->
                      Box(
                        modifier = Modifier
                          .weight(if (i == 0) 0.5f else 1f)
                          .border(0.5.dp, spec.borderColor)
                          .padding(4.dp)
                      ) {
                        Text(v, fontSize = 9.sp, fontFamily = spec.monoFont.toComposeFontFamily(), color = spec.textPrimaryColor, maxLines = 1)
                      }
                    }
                  }
                }
              }
            }

            PreviewScreenKind.IMAGE_WORKSHOP -> {
              PaperlySurfaceCard(spec = spec, contentPadding = PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, null, tint = spec.accentColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                      Text("WebP • 80% Quality", style = MaterialTheme.typography.labelMedium, color = spec.textPrimaryColor)
                      Text("2.4 MB → 310 KB (-87%)", fontFamily = spec.monoFont.toComposeFontFamily(), fontSize = 10.sp, color = spec.secondaryAccentColor)
                    }
                  }
                }
              }
            }

            PreviewScreenKind.PDF_WORKSHOP -> {
              PaperlySurfaceCard(spec = spec, contentPadding = PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                  Icon(Icons.Default.Build, null, tint = spec.accentColor, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(8.dp))
                  Column {
                    Text("Merge • Split • Annotate", style = MaterialTheme.typography.labelMedium, color = spec.textPrimaryColor)
                    Text("3 documents queued locally", style = MaterialTheme.typography.labelSmall, color = spec.textSecondaryColor)
                  }
                }
              }
            }
          }

          // Mini nav preview strip reflecting NavStyle
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(spec.buttonRadiusDp.dp))
              .background(spec.surfaceColor.copy(alpha = spec.transparencyAlpha))
              .border(0.6.dp, spec.borderColor, RoundedCornerShape(spec.buttonRadiusDp.dp))
              .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Home, null, tint = spec.accentColor, modifier = Modifier.size(14.dp))
            Icon(Icons.Default.Description, null, tint = spec.textSecondaryColor, modifier = Modifier.size(14.dp))
            Icon(Icons.Default.TableChart, null, tint = spec.textSecondaryColor, modifier = Modifier.size(14.dp))
            Icon(Icons.Default.Image, null, tint = spec.textSecondaryColor, modifier = Modifier.size(14.dp))
            Text(spec.navStyle.displayName, fontSize = 9.sp, color = spec.textSecondaryColor)
          }
        }
      }
    }
  }
}
