    package com.alananasss.kittytune.ui.profile
    
    import android.content.Context
    import android.widget.Toast
    import androidx.compose.animation.AnimatedVisibility
    import androidx.compose.animation.core.animateFloatAsState
    import androidx.compose.animation.core.tween
    import androidx.compose.animation.fadeIn
    import androidx.compose.animation.fadeOut
    import androidx.compose.foundation.background
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.LazyRow
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.ArrowBack
    import androidx.compose.material.icons.rounded.CheckCircle
    import androidx.compose.material.icons.rounded.DeleteForever
    import androidx.compose.material.icons.rounded.EmojiEvents
    import androidx.compose.material.icons.rounded.Lock
    import androidx.compose.material.icons.rounded.PlayArrow
    import androidx.compose.material.icons.rounded.Warning
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.alpha
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.StrokeCap
    import androidx.compose.ui.input.nestedscroll.nestedScroll
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.compose.ui.zIndex
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.Achievement
    import com.alananasss.kittytune.data.AchievementManager
    import com.alananasss.kittytune.ui.common.UltimateCompletionOverlay
    import java.util.Locale
    
    // Utility for formatting achievement values
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
    
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun AchievementsScreen(onBackClick: () -> Unit) {
        val progressMap by AchievementManager.progressFlow.collectAsState()
        val isAllUnlocked by AchievementManager.isAllUnlocked.collectAsState()
        val (level, currentXP, neededXP) = AchievementManager.getLevelInfo()
        val context = LocalContext.current
    
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
        val levelProgress = (currentXP.toFloat() / neededXP.toFloat()).coerceIn(0f, 1f)
        val animatedProgress by animateFloatAsState(
            targetValue = levelProgress,
            animationSpec = tween(1500),
            label = "progress"
        )
    
        val groupedAchievements = remember { AchievementManager.definitions.groupBy { it.category } }
    
        var showResetDialog1 by remember { mutableStateOf(false) }
        var showResetDialog2 by remember { mutableStateOf(false) }
    
        // Variable to trigger the overlay (manually or automatically)
        var showCompletionOverlay by remember { mutableStateOf(false) }
    
        // --- Automatic display logic on completion ---
        // We use SharedPreferences to show it only once automatically
        LaunchedEffect(isAllUnlocked) {
            if (isAllUnlocked) {
                val prefs = context.getSharedPreferences("achievements_meta", Context.MODE_PRIVATE)
                val hasSeenAnim = prefs.getBoolean("seen_completion_anim", false)
                if (!hasSeenAnim) {
                    showCompletionOverlay = true
                    prefs.edit().putBoolean("seen_completion_anim", true).apply()
                }
            }
        }
    
        if (showResetDialog1) {
            AlertDialog(
                onDismissRequest = { showResetDialog1 = false },
                icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.dialog_reset_achievements_title)) },
                text = { Text(stringResource(R.string.dialog_reset_achievements_msg)) },
                confirmButton = {
                    TextButton(
                        onClick = { showResetDialog1 = false; showResetDialog2 = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.dialog_reset_achievements_confirm)) }
                },
                dismissButton = { TextButton(onClick = { showResetDialog1 = false }) { Text(stringResource(R.string.btn_cancel)) } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    
        if (showResetDialog2) {
            AlertDialog(
                onDismissRequest = { showResetDialog2 = false },
                icon = { Icon(Icons.Rounded.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.dialog_reset_achievements_final_title), fontWeight = FontWeight.Bold) },
                text = { Text(stringResource(R.string.dialog_reset_achievements_final_msg)) },
                confirmButton = {
                    Button(
                        onClick = {
                            AchievementManager.resetAll()
                            // Reset the flag to see the animation again if the game is restarted
                            context.getSharedPreferences("achievements_meta", Context.MODE_PRIVATE)
                                .edit().putBoolean("seen_completion_anim", false).apply()
                            showResetDialog2 = false
                            Toast.makeText(context, context.getString(R.string.achievements_reset_success), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.dialog_reset_achievements_final_confirm)) }
                },
                dismissButton = { TextButton(onClick = { showResetDialog2 = false }) { Text(stringResource(R.string.btn_cancel)) } },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }
    
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = {
                            Column {
                                Text(
                                    stringResource(R.string.achievements_title),
                                    fontWeight = FontWeight.Bold
                                )
                                val unlockedCount = progressMap.values.count { it.isUnlocked }
                                val totalCount = AchievementManager.definitions.size
                                Text(
                                    text = stringResource(R.string.achievements_subtitle, unlockedCount, totalCount),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            FilledTonalIconButton(
                                onClick = onBackClick,
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Rounded.ArrowBack, stringResource(R.string.btn_close))
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    // Ensure padding bottom is sufficient to avoid overlap with MiniPlayer
                    contentPadding = PaddingValues(bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // --- 1. Hero Level Card ---
                    item {
                        LevelProgressCard(
                            level = level,
                            currentXP = currentXP,
                            neededXP = neededXP,
                            progress = animatedProgress
                        )
                    }
    
                    // --- 2. Categories ---
                    for ((category, achievements) in groupedAchievements) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
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
                                            SecretAchievementTile()
                                        } else {
                                            AchievementTile(def, currentValue, isUnlocked)
                                        }
                                    }
                                }
                            }
                        }
                    }
    
                    // --- 3. Buttons ---
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Spacer
                            Spacer(Modifier.height(8.dp))
    
                            // REPLAY BUTTON: Only visible if everything is unlocked
                            if (isAllUnlocked) {
                                Button(
                                    onClick = { showCompletionOverlay = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFFD700), // Gold
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.achievements_replay_legend),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
    
                            // BOUTON RESET
                            OutlinedButton(
                                onClick = { showResetDialog1 = true },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.achievements_reset_progress))
                            }
                        }
                    }
                }
            }
    
            // --- OVERLAY WITH SMOOTH TRANSITION ---
            AnimatedVisibility(
                visible = showCompletionOverlay,
                enter = fadeIn(animationSpec = tween(1000)), // Smooth appearance (1s)
                exit = fadeOut(animationSpec = tween(1000)), // Smooth disappearance (1s)
                modifier = Modifier.zIndex(100f).fillMaxSize()
            ) {
                UltimateCompletionOverlay(onDismiss = { showCompletionOverlay = false })
            }
        }
    }
    
    @Composable
    fun LevelProgressCard(level: Int, currentXP: Int, neededXP: Int, progress: Float) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.achievements_current_level).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
    
                    Text(
                        text = "$level",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
    
                    Spacer(Modifier.height(4.dp))
    
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(50),
                    ) {
                        Text(
                            text = stringResource(R.string.xp_format, currentXP, neededXP),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
    
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        strokeWidth = 10.dp,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
    
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 10.dp,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
    
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
    
    @Composable
    fun PaddingTitle(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp, top = 8.dp)
        )
    }
    
    @Composable
    fun AchievementTile(def: Achievement, current: Int, isUnlocked: Boolean) {
        val progress = (current.toFloat() / def.targetValue.toFloat()).coerceIn(0f, 1f)
    
        val containerColor = MaterialTheme.colorScheme.surfaceContainer
        val titleColor = MaterialTheme.colorScheme.onSurface
        val alpha = if (isUnlocked) 1f else 0.4f
        val iconColor = if (isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    
        val borderStroke = if (isUnlocked) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)) else null
    
        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = borderStroke,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .width(170.dp)
                .height(210.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = def.iconEmoji,
                        fontSize = 32.sp,
                        modifier = Modifier.alpha(alpha)
                    )
    
                    if (isUnlocked) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            null,
                            tint = iconColor,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                stringResource(R.string.xp_reward, def.xpReward),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
    
                // Middle
                Column {
                    Text(
                        text = stringResource(def.titleResId),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(def.descriptionResId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
    
                // Bottom
                Column {
                    val displayCurrent = formatDisplayValue(current, def.id)
                    val displayTarget = formatDisplayValue(def.targetValue, def.id)
    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if(isUnlocked) stringResource(R.string.achievements_status_done) else displayCurrent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if(isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (!isUnlocked) {
                            Text(
                                text = "/ $displayTarget",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
    
                    Spacer(Modifier.height(8.dp))
    
                    if (isUnlocked) {
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape))
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun SecretAchievementTile() {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
            modifier = Modifier
                .width(170.dp)
                .height(210.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.achievements_secret_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.ach_secret_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }


