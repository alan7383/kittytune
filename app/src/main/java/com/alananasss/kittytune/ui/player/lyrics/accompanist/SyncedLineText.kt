package com.alananasss.kittytune.ui.player.lyrics.accompanist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine

@Composable
fun SyncedLineText(
    line: SyncedLine,
    isLineRtl: Boolean,
    isRightAligned: Boolean = false,
    isCenterAligned: Boolean = false,
    textStyle: TextStyle,
    textColor: Color,
    modifier: Modifier = Modifier,
    showTranslation: Boolean = true,
    activeScale: Float = 1.0f
) {
    val scaleFactor = activeScale.coerceAtLeast(1.0f)
    val widthFraction = (1f / scaleFactor).coerceIn(0.5f, 1f)
    val hAlign = when {
        isRightAligned -> Alignment.End
        isCenterAligned -> Alignment.CenterHorizontally
        isLineRtl -> Alignment.End
        else -> Alignment.Start
    }
    val tAlign = when {
        isRightAligned -> TextAlign.End
        isCenterAligned -> TextAlign.Center
        isLineRtl -> TextAlign.End
        else -> TextAlign.Start
    }
    val boxAlignment = when {
        isRightAligned || isLineRtl -> Alignment.CenterEnd
        isCenterAligned -> Alignment.Center
        else -> Alignment.CenterStart
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = hAlign
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(widthFraction),
            contentAlignment = boxAlignment
        ) {
            Text(
                text = line.content,
                style = textStyle,
                color = textColor,
                textAlign = tAlign
            )
        }
        if (showTranslation) {
            line.translation?.let {
                Box(
                    modifier = Modifier.fillMaxWidth(widthFraction),
                    contentAlignment = boxAlignment
                ) {
                    Text(
                        text = it,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = tAlign
                    )
                }
            }
        }
    }
}
