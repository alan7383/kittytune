package com.alananasss.kittytune.ui.yearlyplayback

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R

/**
 * Bottom sheet modal for sharing story screenshots in SoundCloud Wrapped / Yearly Playback.
 *
 * Implements 100% exact parity with SoundCloud Android decompiled:
 * - Layout: share_action_sheet_view_screenshot.xml
 * - Bubble Cell: CellSlideMicroSocialBubbleKt
 * - Preview dimensions: 340dp height, 9:16 aspect ratio
 * - Horizontal carousel of social options with official branding icons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlyPlaybackShareSheet(
    bitmap: Bitmap?,
    onDismiss: () -> Unit,
    onSelectOption: (YearlyPlaybackShareManager.ShareTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shareOptions = remember(context) {
        YearlyPlaybackShareManager.getAvailableShareOptions(context)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161616),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0xFF555555)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Title & Subtitle (matching share_action_sheet_view_screenshot.xml)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.share_screenshot_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.share_screenshot_subtitle),
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
                    color = Color(0xFF9E9E9E)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Screenshot Preview Card (340dp height, 9:16 aspect ratio)
            if (bitmap != null) {
                Surface(
                    modifier = Modifier
                        .height(340.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF333333)),
                    shadowElevation = 8.dp,
                    color = Color.Black
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.share_screenshot_title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Horizontal row of CellSlideMicroSocialBubble items
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = shareOptions,
                    key = { it.target.name }
                ) { option ->
                    CellSlideMicroSocialBubble(
                        item = option,
                        onClick = { onSelectOption(option.target) }
                    )
                }
            }

            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/**
 * Reusable Micro Social Bubble item.
 * Exactly implements com.soundcloud.android.ui.components.compose.listviews.socialbubble.CellSlideMicroSocialBubbleKt:
 * - Width: 64dp
 * - Artwork size: 48dp
 * - Label: Max 2 lines, centered, ellipsis
 */
@Composable
private fun CellSlideMicroSocialBubble(
    item: YearlyPlaybackShareManager.ShareOptionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(item.iconRes),
                contentDescription = stringResource(item.titleRes),
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = stringResource(item.titleRes),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
