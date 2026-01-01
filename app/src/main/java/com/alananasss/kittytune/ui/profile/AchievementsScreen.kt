package com.alananasss.kittytune.ui.profile

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.Achievement
import com.alananasss.kittytune.data.AchievementManager
import java.util.Locale

private fun formatDisplayValue(value: Int, achievementId: String): String {
    val timeBasedIds = listOf("marathon", "night_shift_pro", "bass_addict", "speed_demon", "ghost")
    val isTimeBased = achievementId.startsWith("time_") || timeBasedIds.contains(achievementId)

    if (isTimeBased) {
        val hours = value / 3600
        val minutes = (value % 3600) / 60
        return when {
            hours >= 10 -> "${hours}h"
            hours > 0 -> {
                val decimal = (value % 3600) / 360.0f
                String.format(Locale.US, "%.1fh", hours + decimal)
            }
            minutes > 0 -> "${minutes}m"
            else -> "${value}s"
        }
    }

    return if (value >= 1000) String.format(Locale.US, "%.0fk", value / 1000f) else value.toString()
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(onBackClick: () -> Unit) {
    val progressMap by AchievementManager.progressFlow.collectAsState()
    val (level, currentXP, neededXP) = AchievementManager.getLevelInfo()
    val context = LocalContext.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val levelProgress = (currentXP.toFloat() / neededXP.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = levelProgress, animationSpec = tween(1500), label = "progress")

    // organize by category for cleaner display
    val groupedAchievements = remember { AchievementManager.definitions.groupBy { it.category } }

    var showResetDialog1 by remember { mutableStateOf(false) }
    var showResetDialog2 by remember { mutableStateOf(false) }

    if (showResetDialog1) {
        AlertDialog(
            onDismissRequest = { showResetDialog1 = false },
            icon = { Icon(Icons.Rounded.Warning, null) },
            title = { Text(stringResource(R.string.dialog_reset_achievements_title)) },
            text = { Text(stringResource(R.string.dialog_reset_achievements_msg)) },
            confirmButton = { TextButton(onClick = { showResetDialog1 = false; showResetDialog2 = true }) { Text(stringResource(R.string.dialog_reset_achievements_confirm), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showResetDialog1 = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showResetDialog2) {
        AlertDialog(
            onDismissRequest = { showResetDialog2 = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_reset_achievements_final_title)) },
            text = { Text(stringResource(R.string.dialog_reset_achievements_final_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        AchievementManager.resetAll()
                        showResetDialog2 = false
                        Toast.makeText(context, context.getString(R.string.achievements_reset_success), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.dialog_reset_achievements_final_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showResetDialog2 = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.achievements_title), fontWeight = FontWeight.Bold)
                        val unlockedCount = progressMap.values.count { it.isUnlocked }
                        val totalCount = AchievementManager.definitions.size
                        Text(stringResource(R.string.achievements_subtitle, unlockedCount, totalCount), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Normal)
                    }
                },
                navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_close)) } },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- LEVEL CARD ---
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.achievements_current_level), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$level", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text("$currentXP / $neededXP XP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            )
                        }

                        Spacer(Modifier.width(24.dp))

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    brush = Brush.sweepGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500), Color(0xFFFFD700))),
                                    style = Stroke(width = 4.dp.toPx())
                                )
                            }
                            Text("LVL\n$level", textAlign = TextAlign.Center, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }

            for ((category, achievements) in groupedAchievements) {
                item {
                    Column {
                        PaddingTitle(stringResource(category.titleResId))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(achievements) { def ->
                                val progress = progressMap[def.id]
                                val currentValue = progress?.currentValue ?: 0
                                val isUnlocked = progress?.isUnlocked == true

                                if (def.isSecret && !isUnlocked) {
                                    SecretAchievementCardHorizontal()
                                } else {
                                    AchievementCardHorizontal(def, currentValue, isUnlocked)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Button(
                        onClick = { showResetDialog1 = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.DeleteForever, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.achievements_reset_progress))
                    }
                }
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun PaddingTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun AchievementCardHorizontal(def: Achievement, current: Int, isUnlocked: Boolean) {
    val progress = (current.toFloat() / def.targetValue.toFloat()).coerceIn(0f, 1f)

    val cardWidth = 160.dp
    val containerColor = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (isUnlocked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isUnlocked) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderCheck = if (isUnlocked) 2.dp else 0.dp

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .width(cardWidth)
            .height(200.dp)
            .border(borderCheck, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = def.iconEmoji,
                    fontSize = 32.sp,
                    modifier = Modifier.alpha(if (isUnlocked) 1f else 0.5f)
                )
                if (isUnlocked) {
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        "${def.xpReward} XP",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column {
                Text(
                    text = stringResource(def.titleResId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(def.descriptionResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }

            Column {
                val displayCurrent = formatDisplayValue(current, def.id)
                val displayTarget = formatDisplayValue(def.targetValue, def.id)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = if(isUnlocked) stringResource(R.string.achievements_status_done) else displayCurrent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    if (!isUnlocked) {
                        Text(
                            text = displayTarget,
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.6f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = contentColor.copy(alpha = 0.2f),
                )
            }
        }
    }
}

@Composable
fun SecretAchievementCardHorizontal() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)),
        modifier = Modifier
            .width(160.dp)
            .height(200.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(Color.Black.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.achievements_secret_title),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.ach_secret_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}