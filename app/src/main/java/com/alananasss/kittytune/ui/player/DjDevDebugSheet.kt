/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.utils.makeTimeString

/**
 * Isolated, self-contained developer debug sheet for testing and validating the
 * DJ Flow / Non-Stop Mode engine live on device.
 *
 * Designed with zero architectural coupling: removing this file and the single call site
 * in [PlayerScreen] cleanly wipes the debug UI without leaving any orphan dependencies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjDevDebugSheet(viewModel: PlayerViewModel) {
    if (!viewModel.showDjDebugSheet) return

    val flowState by viewModel.djFlowController.flowState.collectAsState()
    val currentTrack = viewModel.currentTrack

    val tempoMs = if (flowState.currentBpm > 0f) (60000f / flowState.currentBpm).toInt().coerceIn(250, 1500) else 500
    val beatTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "DjBeatPulse")
    val pulseScale by beatTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.025f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(tempoMs / 2, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by beatTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(tempoMs / 2, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    KittyModalBottomSheet(
        onDismissRequest = { viewModel.showDjDebugSheet = false },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- 1. Header Bar ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DJ Flow Console",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Non-Stop Autonomous AI-DJ Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = flowState.isActive,
                            onCheckedChange = { viewModel.djFlowController.enableDjMode(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.showDjDebugSheet = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        }
                    }
                }
            }

            // --- 2. HERO CARD: NÄCHSTER TRACK & ÜBERGANGS-TIMING ---
            item {
                NextTrackTimingHeroCard(
                    flowState = flowState,
                    currentPosition = viewModel.currentPosition,
                    onTriggerTransition = { viewModel.djFlowController.triggerTransition() }
                )
            }

            // --- 3. Auto-DJ Autonomous Banner ---
            item {
                Surface(
                    color = if (flowState.isActive && flowState.isAutonomousEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (flowState.isActive && flowState.isAutonomousEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (flowState.isActive && flowState.isAutonomousEnabled) "AUTO-DJ: VOLLAUTOMATISCH" else "AUTO-DJ: MANUELL",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = if (flowState.isActive && flowState.isAutonomousEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (flowState.beatsUntilTransition != null && flowState.beatsUntilTransition!! > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "in ${flowState.barsUntilTransition ?: 0} Takten (${flowState.beatsUntilTransition} Beats)",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (flowState.plannedTriggerTimeMs != null) {
                                    "Übergang bei ${makeTimeString(flowState.plannedTriggerTimeMs!!)} • Drop bei ${flowState.incomingDropPointMs?.let { makeTimeString(it) } ?: "00:00"}"
                                } else {
                                    "Analysiere Songs & berechne ideale Cue-Points..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // --- 4. Live Takt & Beat Grid Visualizer ---
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = if (flowState.isDownbeat) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = null,
                                    tint = if (flowState.isDownbeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "TAKT-ANZEIGE & BEAT GRID",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Text(
                                text = if (flowState.currentBpm > 0f) {
                                    "Takt ${flowState.currentBar} • Beat ${flowState.beatInBar}/4 • ${String.format(java.util.Locale.US, "%.1f", flowState.currentBpm)} BPM"
                                } else {
                                    "Takt-Erkennung läuft..."
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (flowState.currentBpm > 0f) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        if (flowState.currentBpm <= 0f) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            "Takt & BPM werden analysiert...",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            "DSP Onset-Detection scannt den Track nach Beat-Grid & Drop-Punkten",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            // 4-Beat Bar Pads with Downbeat accent
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (1..4).forEach { beatNumber ->
                                    val isCurrentBeat = flowState.beatInBar == beatNumber
                                    val isDownbeat = beatNumber == 1
                                    val activeBg = if (isDownbeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    val inactiveBg = MaterialTheme.colorScheme.surfaceVariant
                                    val textColor = if (isCurrentBeat) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                                    Surface(
                                        color = if (isCurrentBeat) activeBg else inactiveBg,
                                        shape = RoundedCornerShape(10.dp),
                                        border = if (isCurrentBeat && isDownbeat) {
                                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                        } else null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "BEAT $beatNumber",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = if (isCurrentBeat) FontWeight.ExtraBold else FontWeight.Normal,
                                                        fontSize = 11.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = textColor
                                                )
                                                if (isDownbeat) {
                                                    Text(
                                                        text = "DROP",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = if (isCurrentBeat) textColor else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Live Beat Progress Sub-Bar (continuous sweep 0 -> 1 on every beat)
                            LinearProgressIndicator(
                                progress = { flowState.beatProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(CircleShape),
                                color = if (flowState.isDownbeat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // 16-Beat Phrase Progress Indicator (Club/Dance Phrasen-Meter)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "16-Beat Phrase (4 Takte)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Takt ${flowState.barInPhrase} von 4 (Beat ${flowState.beatInPhrase}/16)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                (1..16).forEach { b ->
                                    val isPastOrCurrent = b <= flowState.beatInPhrase
                                    val isCurrent = b == flowState.beatInPhrase

                                    val barColor = when {
                                        isCurrent -> MaterialTheme.colorScheme.primary
                                        isPastOrCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(if (isCurrent) 8.dp else 5.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(barColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 5. Currently Playing Status Card ---
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Transition Phase Badge
                            val phaseColor = when (flowState.transitionPhase) {
                                TransitionPhase.CROSSFADING -> MaterialTheme.colorScheme.error
                                TransitionPhase.PREPARING -> MaterialTheme.colorScheme.tertiary
                                TransitionPhase.IDLE -> MaterialTheme.colorScheme.outline
                            }
                            Surface(
                                color = phaseColor.copy(alpha = 0.15f),
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(1.dp, phaseColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = flowState.transitionPhase.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    color = phaseColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = currentTrack?.title ?: "No track playing",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack?.displayArtist ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("TEMPO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (flowState.currentBpm > 0f) "%.1f BPM".format(flowState.currentBpm) else "Analyzing...",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("CAMELOT KEY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = flowState.currentKey?.let { "${it.code} (${it.standardName})" } ?: "Analyzing...",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        if (flowState.transitionPhase == TransitionPhase.CROSSFADING) {
                            Spacer(Modifier.height(8.dp))
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("DJ Constant-Energy Blend (Kein Dip)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    Text(
                                        text = "${(flowState.transitionProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { flowState.transitionProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // --- 5b. REAL-TIME DJ STEM ISOLATOR ---
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "LIVE STEM ISOLATOR",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            TextButton(
                                onClick = { viewModel.djFlowController.resetStems() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Reset (1.0x)", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Text(
                            "Echtzeit PCM-Trennung für Vocals, Beat/Drums & Bass. Frequenzen live muten oder boosten!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(10.dp))

                        // Stem 1: Vocals (Mute 0f / Normal 1f / Solo 2f)
                        StemControlRow(
                            title = "🎤 Vocals / Stimme",
                            currentLevel = flowState.vocalStemLevel,
                            options = listOf(
                                Triple(0.0f, "Mute", "Instrumental"),
                                Triple(1.0f, "Normal", "1.0x"),
                                Triple(2.0f, "Solo", "Acapella")
                            ),
                            onSelect = { viewModel.djFlowController.setVocalStem(it) }
                        )

                        Spacer(Modifier.height(8.dp))

                        // Stem 2: Drums / Beat (Kill 0f / Normal 1f / Boost 1.5f)
                        StemControlRow(
                            title = "🥁 Beat / Drums",
                            currentLevel = flowState.drumStemLevel,
                            options = listOf(
                                Triple(0.0f, "Kill", "No Beat"),
                                Triple(1.0f, "Normal", "1.0x"),
                                Triple(1.5f, "Boost", "+3dB Punch")
                            ),
                            onSelect = { viewModel.djFlowController.setDrumStem(it) }
                        )

                        Spacer(Modifier.height(8.dp))

                        // Stem 3: Bass (Cut 0f / Normal 1f / Boost 1.5f)
                        StemControlRow(
                            title = "🎸 Bass / Sub",
                            currentLevel = flowState.bassStemLevel,
                            options = listOf(
                                Triple(0.0f, "Cut", "0 Hz Sub Kill"),
                                Triple(1.0f, "Normal", "1.0x"),
                                Triple(1.5f, "Boost", "Deep Club")
                            ),
                            onSelect = { viewModel.djFlowController.setBassStem(it) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Auto Stem Drop Cut Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Stem Drop Cut", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("Schneidet den Bass des alten Songs direkt beim Drop auf 0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = flowState.isAutoStemDropCutEnabled,
                                onCheckedChange = { viewModel.djFlowController.setAutoStemDropCutEnabled(it) }
                            )
                        }
                    }
                }
            }

            // --- 6. Engine Switches Card ---
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Switch 1: Autonomous Auto-DJ
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Vollautomatischer Auto-DJ", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("System entscheidet autonom wann gewechselt wird", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = flowState.isAutonomousEnabled,
                                onCheckedChange = { viewModel.djFlowController.setAutonomousEnabled(it) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Switch 2: DJ Constant Energy (No volume dip)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DJ Constant Energy (Kein Dip)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("100% Lautstärke im Mix + Sub-Bass Ducking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = flowState.isConstantEnergyEnabled,
                                onCheckedChange = { viewModel.djFlowController.setConstantEnergyEnabled(it) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Switch 3: Harmonic Queue Reordering
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Harmonische Queue-Optimierung", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("Zieht den am besten passenden Song an Platz 1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = flowState.isAutoReorderEnabled,
                                onCheckedChange = { viewModel.djFlowController.setAutoReorderEnabled(it) }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Switch 4: Loop Extension / Phrase Repeating
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Takt-Wiederholung (Loop Extension)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("Wiederholt Phrasen nahtlos, bis der Drop passt", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = flowState.isLoopExtensionEnabled,
                                onCheckedChange = { viewModel.djFlowController.setLoopExtensionEnabled(it) }
                            )
                        }

                        if (flowState.isLoopExtensionEnabled) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(4 to "4 Beats", 8 to "8 Beats", 16 to "16 Beats", 32 to "32 Beats").forEach { (beats, label) ->
                                    val isSelected = flowState.loopBeats == beats
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.djFlowController.setLoopBeats(beats) },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 7. Energy Mode Selector ---
            item {
                Column {
                    Text(
                        text = "ENERGY PROGRESSION",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EnergyMode.entries.forEach { mode ->
                            val isSelected = flowState.energyMode == mode
                            val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                            Surface(
                                color = containerColor,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.djFlowController.setEnergyMode(mode) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.displayName,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 8. Manual Actions ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.djFlowController.triggerTransition() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                        enabled = flowState.transitionPhase == TransitionPhase.IDLE && flowState.suggestedMatches.isNotEmpty()
                    ) {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Jetzt Übergang auslösen", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalIconButton(
                        onClick = { viewModel.djFlowController.evaluateQueueAsync() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Rescore Queue")
                    }
                }
            }

            // --- 8b. ENDLOSER DJ-STREAM & GENRE-MIX ---
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    var textInput by remember(flowState.selectedCategory) { mutableStateOf(flowState.selectedCategory) }
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "ENDLOSER DJ-STREAM & GENRE-MIX",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (flowState.isFetchingInfiniteCandidates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Text(
                            "Gib ein Genre, Vibe oder Stichwort ein. Kittytune sucht automatisch passende Songs (oder aus deinen Likes) und erzeugt einen unendlichen Mix ohne Stop.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(10.dp))

                        // Text input row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                label = { Text("Kategorie / Vibe / Genre") },
                                placeholder = { Text("z.B. Tech House, Synthwave...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    viewModel.djFlowController.searchAndPopulateCategory(textInput)
                                },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text("Laden", fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Quick Preset Chips
                        Text(
                            "Schnellauswahl:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("❤️ Meine Likes", "⚡ Tech House", "🌌 Synthwave").forEach { cat ->
                                val isSelected = flowState.selectedCategory.equals(cat, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        textInput = cat
                                        viewModel.djFlowController.searchAndPopulateCategory(cat)
                                    },
                                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("☕ Chill Beats", "💥 EDM", "🎤 Hip-Hop").forEach { cat ->
                                val isSelected = flowState.selectedCategory.equals(cat, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        textInput = cat
                                        viewModel.djFlowController.searchAndPopulateCategory(cat)
                                    },
                                    label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Infinite Stream Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Endloser Stream (Auto-Nachladen)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    if (flowState.isInfiniteStreamEnabled) "Aktiv: Warteschlange füllt sich automatisch nach" else "Deaktiviert: Spielt nur bis Queue-Ende",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (flowState.isInfiniteStreamEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = flowState.isInfiniteStreamEnabled,
                                onCheckedChange = { viewModel.djFlowController.setInfiniteStreamEnabled(it) }
                            )
                        }
                    }
                }
            }

            // --- 9. 15-Song Queue Radar Header ---
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "15-SONG QUEUE RADAR",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${flowState.analyzedCandidateCount} / ${flowState.totalCandidateCount} analysiert",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (flowState.totalCandidateCount > 0) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = {
                                if (flowState.totalCandidateCount > 0) {
                                    (flowState.analyzedCandidateCount.toFloat() / flowState.totalCandidateCount.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // --- 10. List of Candidate Matches ---
            if (flowState.suggestedMatches.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Keine anstehenden Songs in der Queue oder Analyse läuft...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(flowState.suggestedMatches) { index, match ->
                    CandidateMatchRow(
                        rank = index + 1,
                        match = match,
                        onTransitionTo = {
                            viewModel.djFlowController.triggerTransition(match.track)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NextTrackTimingHeroCard(
    flowState: DjFlowState,
    currentPosition: Long,
    onTriggerTransition: () -> Unit
) {
    val topMatch = flowState.nextTrackMatch
    val triggerTime = flowState.plannedTriggerTimeMs
    val remainingMs = flowState.timeUntilTransitionMs ?: 0L
    val remainingSec = (remainingMs / 1000).coerceAtLeast(0)
    val barsLeft = flowState.barsUntilTransition ?: 0
    val beatsLeft = flowState.beatsUntilTransition ?: 0

    val countdownText = if (remainingMs > 0L) {
        "in %02d:%02d Min (%d Takte / %d Beats)".format(remainingSec / 60, remainingSec % 60, barsLeft, beatsLeft)
    } else if (flowState.transitionPhase == TransitionPhase.CROSSFADING) {
        "Übergang läuft jetzt!"
    } else {
        "Berechne Takt-Countdown..."
    }

    val progress = if (triggerTime != null && triggerTime > 0L) {
        (currentPosition.toFloat() / triggerTime.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "NÄCHSTER SONG & ÜBERGANG",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = if (remainingMs in 1L..15000L) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (remainingMs in 1L..15000L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                ) {
                    Text(
                        text = if (remainingMs > 0L) "%02d:%02d".format(remainingSec / 60, remainingSec % 60) else "--:--",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (remainingMs in 1L..15000L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (topMatch != null) {
                Text(
                    text = topMatch.track.title ?: "Untitled",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = topMatch.track.displayArtist ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Spacer(Modifier.height(10.dp))

                // Metadata Pill Row: Score, Key/BPM, Energy, Transition Archetype
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Match Score Pill
                    val scoreColor = when {
                        topMatch.score >= 85f -> Color(0xFF4CAF50)
                        topMatch.score >= 70f -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Surface(
                        color = scoreColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, scoreColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${topMatch.score.toInt()}% Match",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Key & BPM
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${topMatch.key?.code ?: "?"} • %.1f BPM".format(topMatch.bpm),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Energy
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "⚡ ${topMatch.energyLevel}/10",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Transition Timing & Cue points details
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Timing:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = countdownText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mix-Out: ${triggerTime?.let { makeTimeString(it) } ?: "--:--"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Drop-In: ${flowState.incomingDropPointMs?.let { makeTimeString(it) } ?: "00:00"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Stil: ${topMatch.transitionStyle.description}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onTriggerTransition,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = flowState.transitionPhase == TransitionPhase.IDLE
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Jetzt direkt zu ${topMatch.track.title?.take(20) ?: "Track"} überblenden", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Text(
                    text = "Kein nächster Track in der Warteschlange. Füge Songs zur Queue hinzu, damit der DJ die beste Reihenfolge planen kann.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CandidateMatchRow(
    rank: Int,
    match: TrackMatch,
    onTransitionTo: () -> Unit
) {
    val scoreColor = when {
        match.score >= 85f -> Color(0xFF4CAF50) // Green
        match.score >= 70f -> Color(0xFFFF9800) // Amber
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = if (rank == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = match.track.title ?: "Untitled",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = match.matchDescription,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (match.mixInPointMs != null && match.mixInPointMs > 0L) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Drop: ${makeTimeString(match.mixInPointMs)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "⚡ ${match.energyLevel}/10",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = match.transitionStyle.title,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Score Badge
            Surface(
                color = scoreColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, scoreColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "${match.score.toInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = scoreColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.width(6.dp))

            TextButton(
                onClick = onTransitionTo,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Mix", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun StemControlRow(
    title: String,
    currentLevel: Float,
    options: List<Triple<Float, String, String>>,
    onSelect: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
            Text(
                when {
                    kotlin.math.abs(currentLevel - 0f) < 0.05f -> "MUTED"
                    currentLevel > 1.2f -> "BOOST (${String.format(java.util.Locale.US, "%.1f", currentLevel)}x)"
                    else -> "AKTIV (${String.format(java.util.Locale.US, "%.1f", currentLevel)}x)"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = when {
                    kotlin.math.abs(currentLevel - 0f) < 0.05f -> MaterialTheme.colorScheme.error
                    currentLevel > 1.2f -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (level, label, sub) ->
                val isSelected = kotlin.math.abs(currentLevel - level) < 0.05f
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(level) },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            Text(sub, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
