package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.core.Toaster

@Composable
fun Tip(
    text: String,
    enabled: Boolean = true,
    instant: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (!enabled || text.isBlank()) {
        content()
        return
    }
    Box(modifier = Modifier.clickable { Toaster.show(text) }) {
        content()
    }
}

@Composable
fun FunFactDot(
    fact: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Tip(fact) {
        Box(modifier.size(16.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(5.dp)
                    .background(tint.copy(alpha = 0.55f), CircleShape)
            )
        }
    }
}
