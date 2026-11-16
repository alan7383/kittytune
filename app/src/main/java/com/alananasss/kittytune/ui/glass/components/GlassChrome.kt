package com.alananasss.kittytune.ui.glass.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.ui.glass.GlassLevel
import com.alananasss.kittytune.ui.glass.GlassShadow
import com.alananasss.kittytune.ui.glass.LocalGlassBackdrop
import com.alananasss.kittytune.ui.glass.glass
import com.alananasss.kittytune.ui.glass.rememberGlassColors
import com.alananasss.kittytune.ui.glass.rememberGlassHighlight
import com.alananasss.kittytune.ui.glass.rememberGlassShape
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** A navigation destination, independent of the bar that draws it. */
data class GlassNavItem(
    val key: String,
    val title: String,
    val icon: ImageVector,
)

/**
 * The iOS 26 style tab bar: a floating glass capsule, icon above a small label, with a draggable
 * selection lens that shows the selected tab in the accent colour.
 */
@Composable
fun GlassNavigationTabs(
    items: List<GlassNavItem>,
    selectedKey: String?,
    onSelect: (GlassNavItem) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    showLabels: Boolean = true,
) {
    if (items.isEmpty()) return
    val colors = rememberGlassColors()
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    GlassTabs(
        selectedIndex = selectedIndex,
        onTabSelected = { index -> items.getOrNull(index)?.let(onSelect) },
        tabsCount = items.size,
        modifier = modifier,
        height = height,
    ) {
        items.forEach { item ->
            GlassTab(onClick = { onSelect(item) }) {
                CompositionLocalProvider(LocalContentColor provides colors.content) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = if (showLabels) null else item.title,
                        modifier = Modifier.size(24.dp)
                    )
                    if (showLabels) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * The round glass action that sits next to the tab bar (search, profile...), as tall as the bar.
 */
@Composable
fun GlassChromeButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val colors = rememberGlassColors()
    val highlight = rememberGlassHighlight()
    Box(
        modifier
            .size(size)
            .glass(
                shape = CircleShape,
                level = GlassLevel.Chrome,
                highlight = highlight,
                pressExpansion = 8.dp,
                shadow = GlassShadow.Floating,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(highlight?.gestureModifier ?: Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.content, modifier = Modifier.size(26.dp))
    }
}

/**
 * A floating glass panel for chrome content (mini player, rail). Its own glass is exported as the
 * backdrop of its children, so a glass button inside it refracts the panel and not the page.
 */
@Composable
fun GlassChromePanel(
    shape: Shape,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val panelBackdrop = rememberLayerBackdrop()
    val highlight = rememberGlassHighlight(interactive)
    val glassShape = rememberGlassShape(shape)
    val hasBackdrop = LocalGlassBackdrop.current != null
    Box(
        modifier
            .glass(
                shape = glassShape,
                level = GlassLevel.Chrome,
                highlight = highlight,
                pressExpansion = 3.dp,
                shadow = GlassShadow.Floating,
                exportedBackdrop = if (hasBackdrop) panelBackdrop else null,
            )
            .then(highlight?.gestureModifier ?: Modifier)
            .clip(glassShape)
            .then(contentModifier)
    ) {
        CompositionLocalProvider(LocalGlassBackdrop provides if (hasBackdrop) panelBackdrop else null) {
            content()
        }
    }
}

/** A glass column for a vertical navigation rail or a side panel. */
@Composable
fun GlassChromeColumn(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassShape = rememberGlassShape(shape)
    Column(
        modifier
            .glass(shape = glassShape, level = GlassLevel.Chrome, shadow = GlassShadow.Floating)
            .clip(glassShape),
        content = content
    )
}
