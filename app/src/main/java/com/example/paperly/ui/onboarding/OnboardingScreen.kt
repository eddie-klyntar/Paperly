package com.example.paperly.ui.onboarding

import com.example.paperly.ui.themes.PreviewScreenKind
import com.example.paperly.ui.themes.ThemeMiniWorkspacePreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.paperly.theme.LocalPaperlyTheme
import com.example.paperly.theme.PaperlyCanvasBackground
import com.example.paperly.theme.PaperlySurfaceCard
import com.example.paperly.theme.ThemePresets

@Composable
fun OnboardingScreen(
  onExploreThemes: () -> Unit,
  onCreateTheme: () -> Unit,
  onSkip: () -> Unit
) {
  val spec = LocalPaperlyTheme.current
  val featuredThemes = remember {
    listOf(
      ThemePresets.findById("editorial_morning_press"),
      ThemePresets.findById("liquid_glass_azure_day"),
      ThemePresets.findById("retro_terminal_phosphor_green"),
      ThemePresets.findById("brutalist_hazard_yellow"),
      ThemePresets.findById("blueprint_architect_cyan")
    ).filterNotNull()
  }
  var previewIndex by remember { mutableStateOf(0) }
  val previewSpec = featuredThemes.getOrElse(previewIndex) { spec }

  PaperlyCanvasBackground(spec = previewSpec) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Offline & privacy pill
        PaperlySurfaceCard(
          spec = previewSpec,
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = previewSpec.accentColor, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.offline_badge),
              style = MaterialTheme.typography.labelSmall,
              color = previewSpec.textSecondaryColor
            )
          }
        }

        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.displayLarge,
          color = previewSpec.textPrimaryColor
        )
        Text(
          text = stringResource(R.string.tagline),
          style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
          color = previewSpec.accentColor
        )
        Text(
          text = stringResource(R.string.onboarding_subtitle),
          style = MaterialTheme.typography.bodyMedium,
          color = previewSpec.textSecondaryColor
        )

        // Option A: Explore Themes
        Button(
          onClick = onExploreThemes,
          shape = RoundedCornerShape(previewSpec.buttonRadiusDp.dp),
          colors = ButtonDefaults.buttonColors(containerColor = previewSpec.accentColor),
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .height(50.dp)
            .testTag("onboarding_explore_themes")
        ) {
          Icon(Icons.Default.Palette, contentDescription = null)
          Spacer(Modifier.width(10.dp))
          Text(
            text = "${stringResource(R.string.action_explore_themes)} (280+)",
            style = MaterialTheme.typography.labelLarge
          )
        }

        // Option B: Create My Own Theme
        OutlinedButton(
          onClick = onCreateTheme,
          shape = RoundedCornerShape(previewSpec.buttonRadiusDp.dp),
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .testTag("onboarding_create_theme")
        ) {
          Icon(Icons.Default.Brush, contentDescription = null, tint = previewSpec.textPrimaryColor)
          Spacer(Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.action_create_theme),
            style = MaterialTheme.typography.labelLarge,
            color = previewSpec.textPrimaryColor
          )
        }

        // Option C: Skip for Now (Mandatory & prominent)
        TextButton(
          onClick = onSkip,
          modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .testTag("onboarding_skip")
        ) {
          Text(
            text = stringResource(R.string.action_skip_now),
            style = MaterialTheme.typography.labelLarge,
            color = previewSpec.textPrimaryColor
          )
          Spacer(Modifier.width(6.dp))
          Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = previewSpec.textPrimaryColor, modifier = Modifier.size(18.dp))
        }

        // Interactive live preview switcher
        ThemeMiniWorkspacePreview(
          spec = previewSpec,
          screenKind = PreviewScreenKind.HOME,
          modifier = Modifier.fillMaxWidth()
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          featuredThemes.forEachIndexed { idx, theme ->
            val selected = idx == previewIndex
            PaperlySurfaceCard(
              spec = previewSpec,
              selected = selected,
              onClick = { previewIndex = idx },
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
            ) {
              Text(
                text = theme.collection.displayName.split(" ").first(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) previewSpec.accentColor else previewSpec.textSecondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }
    }
  }
}
