package com.alananasss.kittytune.ui.glass.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.glass.GlassLevel
import com.alananasss.kittytune.ui.glass.GlassShadow
import com.alananasss.kittytune.ui.glass.glass
import com.alananasss.kittytune.ui.glass.rememberGlassColors
import com.kyant.shapes.Capsule

/** A floating glass capsule toast with an optional accent action. */
@Composable
fun GlassSnackbar(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = rememberGlassColors()
    val shape = Capsule()
    Row(
        modifier
            .heightIn(min = 52.dp)
            .glass(shape, GlassLevel.Chrome, shadow = GlassShadow.Floating)
            .clip(shape)
            .padding(start = 20.dp, end = if (actionLabel != null) 6.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            color = colors.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(vertical = 14.dp)
        )
        if (actionLabel != null) {
            GlassButton(
                onClick = onAction,
                style = GlassButtonStyle.Prominent,
                tint = colors.accent,
                contentColor = colors.onAccent,
                minHeight = 40.dp,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
