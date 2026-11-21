package com.example.paperly.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PaperlyCanvasBackground(
  modifier: Modifier = Modifier,
  spec: PaperlyThemeSpec = LocalPaperlyTheme.current,
  content: @Composable BoxScope.() -> Unit
) {
  val bgBrush = Brush.verticalGradient(
    colors = listOf(spec.gradientStartColor, spec.gradientEndColor, spec.primaryBgColor)
  )
  val textureColor = if (spec.isDark) Color.White.copy(alpha = 0.045f) else Color.Black.copy(alpha = 0.04f)
  val gridAccent = spec.accentColor.copy(alpha = if (spec.isDark) 0.14f else 0.10f)

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(bgBrush)
      .drawBehind {
        when (spec.textureOverlay) {
          TextureOverlay.BLUEPRINT_GRID -> {
            val step = 28.dp.toPx()
            var x = 0f
            while (x < size.width) {
              drawLine(gridAccent, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
              x += step
            }
            var y = 0f
            while (y < size.height) {
              drawLine(gridAccent, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
              y += step
            }
          }
          TextureOverlay.TERMINAL_SCANLINES -> {
            val step = 4.dp.toPx()
            var y = 0f
            while (y < size.height) {
              drawLine(textureColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
              y += step
            }
          }
          TextureOverlay.DOT_MATRIX -> {
            val step = 18.dp.toPx()
            var x = step / 2f
            while (x < size.width) {
              var y = step / 2f
              while (y < size.height) {
                drawCircle(textureColor, radius = 1.2.dp.toPx(), center = Offset(x, y))
                y += step
              }
              x += step
            }
          }
          TextureOverlay.EDITORIAL_RULES -> {
            drawLine(spec.borderColor.copy(alpha = 0.45f), Offset(0f, 4.dp.toPx()), Offset(size.width, 4.dp.toPx()), strokeWidth = 1.5f)
          }
          TextureOverlay.PAPER_GRAIN -> {
            val step = 24.dp.toPx()
            var y = step
            while (y < size.height) {
              drawLine(textureColor.copy(alpha = 0.028f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
              y += step
            }
          }
          TextureOverlay.NONE -> {
            if (spec.surfaceMaterial == SurfaceMaterial.LIQUID_GLASS || spec.surfaceMaterial == SurfaceMaterial.FROSTED_GLASS) {
              drawCircle(
                brush = Brush.radialGradient(
                  colors = listOf(spec.accentColor.copy(alpha = 0.16f), Color.Transparent),
                  center = Offset(size.width * 0.85f, size.height * 0.12f),
                  radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.85f, size.height * 0.12f),
                radius = size.width * 0.65f
              )
              drawCircle(
                brush = Brush.radialGradient(
                  colors = listOf(spec.secondaryAccentColor.copy(alpha = 0.12f), Color.Transparent),
                  center = Offset(size.width * 0.15f, size.height * 0.78f),
                  radius = size.width * 0.55f
                ),
                center = Offset(size.width * 0.15f, size.height * 0.78f),
                radius = size.width * 0.55f
              )
            }
          }
        }
      },
    content = content
  )
}

@Composable
fun PaperlySurfaceCard(
  modifier: Modifier = Modifier,
  spec: PaperlyThemeSpec = LocalPaperlyTheme.current,
  selected: Boolean = false,
  onClick: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(
    horizontal = (16f * spec.density.multiplier).dp,
    vertical = (14f * spec.density.multiplier).dp
  ),
  content: @Composable BoxScope.() -> Unit
) {
  val shape = RoundedCornerShape(spec.cornerRadiusDp.dp)
  val fillColor = when {
    selected -> spec.selectedContainerColor
    spec.surfaceMaterial == SurfaceMaterial.LIQUID_GLASS ||
      spec.surfaceMaterial == SurfaceMaterial.FROSTED_GLASS ||
      spec.surfaceMaterial == SurfaceMaterial.ACRYLIC ||
      spec.surfaceMaterial == SurfaceMaterial.TRANSPARENT ->
      spec.surfaceColor.copy(alpha = spec.transparencyAlpha)
    else -> spec.surfaceColor
  }

  val borderColor = if (selected) spec.accentColor else spec.borderColor
  val borderWidth = if (selected) (spec.borderWidthDp.coerceAtLeast(1f) + 0.5f).dp else spec.borderWidthDp.dp

  val cardBody: @Composable () -> Unit = {
    Box(
      modifier = Modifier
        .clip(shape)
        .background(
          if (spec.surfaceMaterial == SurfaceMaterial.GRADIENT && !selected) {
            Brush.linearGradient(
              listOf(
                spec.surfaceColor.copy(alpha = spec.transparencyAlpha),
                spec.surfaceVariantColor.copy(alpha = spec.transparencyAlpha)
              )
            )
          } else {
            Brush.linearGradient(listOf(fillColor, fillColor))
          }
        )
        .then(
          if (spec.surfaceMaterial == SurfaceMaterial.LIQUID_GLASS) {
            Modifier.drawBehind {
              drawLine(
                color = Color.White.copy(alpha = if (spec.isDark) 0.22f else 0.75f),
                start = Offset(0f, 1f),
                end = Offset(size.width, 1f),
                strokeWidth = 2f
              )
            }
          } else Modifier
        )
        .then(
          if (borderWidth > 0.dp) Modifier.border(BorderStroke(borderWidth, borderColor), shape)
          else if (spec.surfaceMaterial == SurfaceMaterial.RAISED_NEUMORPHIC) {
            Modifier.border(
              1.dp,
              Brush.verticalGradient(
                listOf(
                  Color.White.copy(alpha = if (spec.isDark) 0.12f else 0.8f),
                  Color.Black.copy(alpha = if (spec.isDark) 0.4f else 0.12f)
                )
              ),
              shape
            )
          } else Modifier
        )
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(contentPadding),
      content = content
    )
  }

  if (spec.hardShadowOffsetDp > 0f) {
    Box(modifier = modifier) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .offset(x = spec.hardShadowOffsetDp.dp, y = spec.hardShadowOffsetDp.dp)
          .background(spec.borderColor, shape)
      )
      cardBody()
    }
  } else {
    Surface(
      modifier = modifier,
      shape = shape,
      color = Color.Transparent,
      tonalElevation = 0.dp,
      shadowElevation = if (spec.surfaceMaterial == SurfaceMaterial.FLAT) 0.dp else spec.cardElevationDp.dp
    ) {
      cardBody()
    }
  }
}

@Composable
fun PaperlyHeaderBanner(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  spec: PaperlyThemeSpec = LocalPaperlyTheme.current,
  trailing: @Composable (() -> Unit)? = null
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column(modifier = Modifier.weight(1f)) {
        val displayTitle = when (spec.headerStyle) {
          HeaderStyle.TERMINAL_PROMPT -> "> ${title.lowercase().replace(' ', '_')}"
          HeaderStyle.BRUTALIST_BANNER -> title.uppercase()
          else -> title
        }
        Text(
          text = displayTitle,
          style = MaterialTheme.typography.headlineMedium,
          color = spec.textPrimaryColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
              fontFamily = if (spec.headerStyle == HeaderStyle.TERMINAL_PROMPT) spec.monoFont.toComposeFontFamily()
              else MaterialTheme.typography.bodySmall.fontFamily
            ),
            color = spec.textSecondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
      if (trailing != null) {
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
      }
    }
    if (spec.headerStyle == HeaderStyle.EDITORIAL_SERIF || spec.headerStyle == HeaderStyle.BRUTALIST_BANNER) {
      Spacer(modifier = Modifier.height(8.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(if (spec.headerStyle == HeaderStyle.BRUTALIST_BANNER) 2.dp else 1.dp)
          .background(spec.borderColor)
      )
    }
  }
}

data class PaperlyNavItem(
  val route: String,
  val label: String,
  val icon: ImageVector,
  val testTag: String
)

@Composable
fun PaperlyBottomNavigation(
  items: List<PaperlyNavItem>,
  currentRoute: String?,
  onNavigate: (String) -> Unit,
  spec: PaperlyThemeSpec = LocalPaperlyTheme.current
) {
  when (spec.navStyle) {
    NavStyle.FLOATING_PILL -> {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
      ) {
        PaperlySurfaceCard(
          spec = spec,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            items.forEach { item ->
              val selected = currentRoute == item.route
              val pillShape = RoundedCornerShape(spec.buttonRadiusDp.dp)
              Column(
                modifier = Modifier
                  .weight(1f)
                  .minimumInteractiveComponentSize()
                  .clip(pillShape)
                  .background(if (selected) spec.selectedContainerColor else Color.Transparent)
                  .clickable { onNavigate(item.route) }
                  .padding(vertical = 6.dp, horizontal = 4.dp)
                  .testTag(item.testTag),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(
                  imageVector = item.icon,
                  contentDescription = item.label,
                  tint = if (selected) spec.accentColor else spec.textSecondaryColor,
                  modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = item.label,
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color = if (selected) spec.accentColor else spec.textSecondaryColor,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
    }

    NavStyle.TERMINAL_TABS, NavStyle.BRUTALIST_BLOCK, NavStyle.EDITORIAL_UNDERLINE -> {
      Surface(
        color = spec.surfaceColor.copy(alpha = spec.transparencyAlpha),
        border = BorderStroke(spec.borderWidthDp.coerceAtLeast(1f).dp, spec.borderColor),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          items.forEach { item ->
            val selected = currentRoute == item.route
            val isTerminal = spec.navStyle == NavStyle.TERMINAL_TABS
            val isBrutalist = spec.navStyle == NavStyle.BRUTALIST_BLOCK
            Column(
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(if (isBrutalist || isTerminal) 0.dp else 8.dp))
                .background(
                  when {
                    selected && isBrutalist -> spec.accentColor
                    selected && isTerminal -> spec.selectedContainerColor
                    else -> Color.Transparent
                  }
                )
                .then(
                  if (selected && isTerminal) Modifier.border(1.dp, spec.accentColor) else Modifier
                )
                .clickable { onNavigate(item.route) }
                .padding(vertical = 6.dp, horizontal = 4.dp)
                .testTag(item.testTag),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = when {
                  selected && isBrutalist -> MaterialTheme.colorScheme.onPrimary
                  selected -> spec.accentColor
                  else -> spec.textSecondaryColor
                },
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = if (isTerminal) "[${item.label.take(6)}]" else item.label,
                fontSize = 10.sp,
                fontFamily = if (isTerminal) spec.monoFont.toComposeFontFamily() else MaterialTheme.typography.labelSmall.fontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                  selected && isBrutalist -> MaterialTheme.colorScheme.onPrimary
                  selected -> spec.accentColor
                  else -> spec.textSecondaryColor
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              if (selected && spec.navStyle == NavStyle.EDITORIAL_UNDERLINE) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(modifier = Modifier.width(24.dp).height(2.dp).background(spec.accentColor))
              }
            }
          }
        }
      }
    }

    else -> {
      NavigationBar(
        containerColor = spec.surfaceColor.copy(alpha = spec.transparencyAlpha),
        contentColor = spec.textPrimaryColor,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.navigationBarsPadding()
      ) {
        items.forEach { item ->
          val selected = currentRoute == item.route
          NavigationBarItem(
            selected = selected,
            onClick = { onNavigate(item.route) },
            icon = { Icon(item.icon, contentDescription = item.label) },
            label = {
              Text(
                text = item.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            modifier = Modifier.testTag(item.testTag),
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = spec.accentColor,
              selectedTextColor = spec.accentColor,
              indicatorColor = spec.selectedContainerColor,
              unselectedIconColor = spec.textSecondaryColor,
              unselectedTextColor = spec.textSecondaryColor
            )
          )
        }
      }
    }
  }
}

@Composable
fun PaperlyNavigationRail(
  items: List<PaperlyNavItem>,
  currentRoute: String?,
  onNavigate: (String) -> Unit,
  spec: PaperlyThemeSpec = LocalPaperlyTheme.current
) {
  NavigationRail(
    containerColor = spec.surfaceColor.copy(alpha = spec.transparencyAlpha),
    contentColor = spec.textPrimaryColor
  ) {
    Spacer(modifier = Modifier.height(12.dp))
    items.forEach { item ->
      val selected = currentRoute == item.route
      NavigationRailItem(
        selected = selected,
        onClick = { onNavigate(item.route) },
        icon = { Icon(item.icon, contentDescription = item.label) },
        label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = Modifier.testTag(item.testTag)
      )
    }
  }
}
