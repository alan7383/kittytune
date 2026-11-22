@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.alananasss.kittytune.ui.common

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.theme.GoogleSansRounded
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * The one-time "what's new" carousel shown to people who update to a version with features worth
 * presenting (Listen Together, Discord presence…). Fresh installs never see it: they discover the
 * app through the setup instead.
 *
 * Bump [EDITION] to present a new set of slides to everyone who updates again.
 */
object WhatsNew {
    const val EDITION = "2026-11-together"
    private const val PREFS = "whats_new"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether to show the carousel. Decided once per edition, on the first launch that knows it:
     * someone who had not finished the setup yet is a new user, so the edition is marked seen.
     */
    fun shouldShow(context: Context, setupCompleted: Boolean): Boolean {
        if (prefs(context).getString("seen", null) == EDITION) return false
        if (!setupCompleted) {
            markSeen(context)
            return false
        }
        return true
    }

    fun markSeen(context: Context) = prefs(context).edit().putString("seen", EDITION).apply()
}

/** Where a slide's own button leads. */
enum class WhatsNewAction { TOGETHER, DISCORD }

private enum class Accent { PRIMARY, TERTIARY, SECONDARY }

private class WhatsNewSlide(
    val title: Int,
    val body: Int,
    val shape: RoundedPolygon,
    val accent: Accent,
    val icon: @Composable () -> Painter,
    val action: WhatsNewAction? = null,
    val actionLabel: Int = 0,
)

private val slides = listOf(
    WhatsNewSlide(
        R.string.whats_new_together_title, R.string.whats_new_together_body,
        MaterialShapes.Cookie9Sided, Accent.PRIMARY, { rememberVectorPainter(Icons.Rounded.Groups) },
        WhatsNewAction.TOGETHER, R.string.whats_new_together_action,
    ),
    WhatsNewSlide(
        R.string.whats_new_reactions_title, R.string.whats_new_reactions_body,
        MaterialShapes.SoftBurst, Accent.TERTIARY, { rememberVectorPainter(Icons.Rounded.EmojiEmotions) },
    ),
    WhatsNewSlide(
        R.string.whats_new_playlists_title, R.string.whats_new_playlists_body,
        MaterialShapes.Clover4Leaf, Accent.SECONDARY, { rememberVectorPainter(Icons.AutoMirrored.Rounded.QueueMusic) },
    ),
    WhatsNewSlide(
        R.string.whats_new_discord_title, R.string.whats_new_discord_body,
        MaterialShapes.Sunny, Accent.PRIMARY, { painterResource(R.drawable.ic_discord) },
        WhatsNewAction.DISCORD, R.string.whats_new_discord_action,
    ),
)

@Composable
private fun Accent.container(): Color = when (this) {
    Accent.PRIMARY -> MaterialTheme.colorScheme.primaryContainer
    Accent.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer
    Accent.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer
}

@Composable
private fun Accent.onContainer(): Color = when (this) {
    Accent.PRIMARY -> MaterialTheme.colorScheme.onPrimaryContainer
    Accent.SECONDARY -> MaterialTheme.colorScheme.onSecondaryContainer
    Accent.TERTIARY -> MaterialTheme.colorScheme.onTertiaryContainer
}

@Composable
private fun Accent.strong(): Color = when (this) {
    Accent.PRIMARY -> MaterialTheme.colorScheme.primary
    Accent.SECONDARY -> MaterialTheme.colorScheme.secondary
    Accent.TERTIARY -> MaterialTheme.colorScheme.tertiary
}

/**
 * The carousel itself, in a bottom sheet. [onDone] runs for "Got it", "Skip" and a swipe down
 * alike; [onAction] for a slide's own button. Both end the announcement for good.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhatsNewSheet(onDone: () -> Unit, onAction: (WhatsNewAction) -> Unit) {
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    KittyModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.whats_new_badge),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) { page ->
                val slide = slides[page]
                val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .graphicsLayer {
                            alpha = 1f - offset * 0.6f
                            val scale = 1f - offset * 0.08f
                            scaleX = scale
                            scaleY = scale
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    WhatsNewHero(slide, Modifier.widthIn(max = 420.dp).fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(slide.title),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansRounded, fontSize = 26.sp),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(slide.body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 460.dp).heightIn(min = 72.dp)
                    )
                    val action = slide.action
                    if (action != null) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = { onAction(action) }, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(slide.actionLabel))
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
                        }
                    } else {
                        // Keeps every page the same height, so the sheet does not jump while swiping.
                        Spacer(Modifier.height(12.dp + ButtonDefaults.MinHeight))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            WhatsNewIndicator(pager.currentPage, slides.size, slides[pager.currentPage].accent)
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = pager.currentPage == 0,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "whatsNewSecondary"
                ) { first ->
                    if (first) {
                        TextButton(onClick = onDone, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(R.string.whats_new_skip))
                        }
                    } else {
                        TextButton(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(stringResource(R.string.whats_new_back))
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.height(52.dp)
                ) {
                    AnimatedContent(
                        targetState = last,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "whatsNewPrimary"
                    ) { done ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(if (done) R.string.whats_new_done else R.string.whats_new_next),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(if (done) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

/** A slowly turning Material shape holding the slide's icon, with a few shapes drifting around it. */
@Composable
private fun WhatsNewHero(slide: WhatsNewSlide, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "whatsNewHero")
    val spin by motion.animateFloat(
        0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "spin"
    )
    val drift by motion.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(2_600), RepeatMode.Reverse), label = "drift"
    )
    val container = slide.accent.container()
    val strong = slide.accent.strong()
    Box(
        modifier = modifier
            .height(220.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .offset(x = (-118).dp, y = (-58 + drift * 6).dp)
                .size(44.dp)
                .graphicsLayer { rotationZ = -spin * 1.5f }
                .clip(MaterialShapes.Pill.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )
        Box(
            Modifier
                .offset(x = 124.dp, y = (50 - drift * 8).dp)
                .size(56.dp)
                .graphicsLayer { rotationZ = spin }
                .clip(MaterialShapes.Cookie6Sided.toShape())
                .background(MaterialTheme.colorScheme.tertiaryContainer)
        )
        Box(
            Modifier
                .offset(x = 104.dp, y = (-70 + drift * 4).dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(strong.copy(alpha = 0.55f))
        )
        Box(
            Modifier
                .offset(x = (-96).dp, y = (72 - drift * 5).dp)
                .size(24.dp)
                .graphicsLayer { rotationZ = spin * 2f }
                .clip(MaterialShapes.Gem.toShape())
                .background(strong.copy(alpha = 0.35f))
        )
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(156.dp)
                    .graphicsLayer { rotationZ = spin }
                    .clip(slide.shape.toShape())
                    .background(container)
            )
            Icon(slide.icon(), null, Modifier.size(64.dp), tint = slide.accent.onContainer())
        }
    }
}

/** Pills that stretch to show the current page, in the current slide's colour. */
@Composable
private fun WhatsNewIndicator(current: Int, count: Int, accent: Accent) {
    val activeColor by animateColorAsState(accent.strong(), label = "indicatorColor")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "indicatorWidth")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (selected) activeColor else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}
