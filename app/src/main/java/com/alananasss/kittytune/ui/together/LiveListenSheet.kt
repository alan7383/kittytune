@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.alananasss.kittytune.ui.together

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.PlaybackService
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.common.SettingsSwitch
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
fun LiveListenSheet(
    playerViewModel: PlayerViewModel,
    onOpenRoom: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val prefs = remember { PlayerPreferences(context) }
    val activeCode by Together.active.collectAsState()
    val rooms by Together.rooms.collectAsState()
    val isHostLocked by Together.isHostLocked.collectAsState()
    var discordListenAlong by remember { mutableStateOf(prefs.getDiscordListenAlongEnabled()) }
    var joinInput by remember { mutableStateOf("") }

    val activeRoom = activeCode?.let { rooms[it] ?: Together.Room(it, "") }
    val isHost = activeCode?.let { Together.isHostOf(it) } == true
    val currentTrack = playerViewModel.uiCurrentTrack

    KittyModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = cs.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Expressive Scalloped Cookie Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ExpressiveCookieBadge(
                    isActive = activeCode != null,
                    size = 56.dp,
                    activeColor = cs.primary,
                    inactiveColor = cs.secondaryContainer,
                ) {
                    if (activeCode != null) {
                        ListeningBars(
                            isPlaying = true,
                            modifier = Modifier.size(22.dp),
                            color = cs.onPrimary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Podcasts,
                            contentDescription = null,
                            tint = cs.onSecondaryContainer,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.together_live_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (activeRoom != null) {
                            val listeners = activeRoom.listeners.map { it.name }
                            if (listeners.isEmpty()) {
                                stringResource(R.string.together_code, TogetherWire.displayCode(activeRoom.code))
                            } else {
                                stringResource(R.string.together_listening_now, listeners.joinToString(", "))
                            }
                        } else {
                            stringResource(R.string.together_live_subtitle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            if (activeCode == null) {
                if (currentTrack != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(cs.surfaceContainerHigh)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TogetherArtwork(
                            url = currentTrack.fullResArtwork,
                            isLive = false,
                            size = 52.dp,
                            cornerRadius = 14.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack.title ?: stringResource(R.string.untitled_track),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = currentTrack.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Button(
                    onClick = hapticAction(HapticKind.MEDIUM) {
                        playerViewModel.startLiveListenAlong()
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Icon(Icons.Rounded.Podcasts, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.together_start_live), fontWeight = FontWeight.Bold)
                }

                // Quick join card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.together_join),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = joinInput,
                        onValueChange = { joinInput = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.together_link_or_code_hint),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val joined = Together.joinAndListen(joinInput)
                            if (joined != null) {
                                joinInput = ""
                                onDismissRequest()
                                onOpenRoom(joined.code)
                            } else {
                                Toast.makeText(context, context.getString(R.string.together_bad_code), Toast.LENGTH_SHORT).show()
                            }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilledTonalButton(
                        onClick = hapticAction(HapticKind.UI) {
                            val joined = Together.joinAndListen(joinInput)
                            if (joined != null) {
                                joinInput = ""
                                onDismissRequest()
                                onOpenRoom(joined.code)
                            } else {
                                Toast.makeText(context, context.getString(R.string.together_bad_code), Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = joinInput.isNotBlank(),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.together_listen))
                    }
                }
            } else {
                val code = activeCode!!
                val inviteUrl = remember(code) { TogetherLinks.inviteUrl(code) }

                // Invite Link & Share Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(shape = CircleShape, color = cs.primaryContainer) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = cs.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = TogetherWire.displayCode(code),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = cs.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    Text(
                        text = inviteUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = hapticAction(HapticKind.UI) {
                                TogetherLinks.shareInviteLink(context, code)
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.together_share_link), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        FilledTonalButton(
                            onClick = hapticAction(HapticKind.UI) {
                                TogetherLinks.copyInviteLink(context, code)
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.together_copy_link), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                if (isHost) {
                    TogetherGroupSection {
                        TogetherGroupItem(
                            onClick = hapticAction(HapticKind.UI) { Together.setHostLocked(!isHostLocked) },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TogetherIconBadge(icon = Icons.Rounded.Lock)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.together_lock_controls),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.together_lock_controls_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                SettingsSwitch(
                                    checked = isHostLocked,
                                    onCheckedChange = { Together.setHostLocked(it) },
                                )
                            }
                        }

                        TogetherGroupItem(
                            onClick = hapticAction(HapticKind.UI) {
                                val next = !discordListenAlong
                                discordListenAlong = next
                                prefs.setDiscordListenAlongEnabled(next)
                                context.startService(Intent(context, PlaybackService::class.java).apply {
                                    action = PlaybackService.ACTION_FORCE_UPDATE
                                })
                            },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TogetherIconBadge(icon = Icons.Rounded.Headphones)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.discord_listen_along_title),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = stringResource(R.string.discord_listen_along_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                SettingsSwitch(
                                    checked = discordListenAlong,
                                    onCheckedChange = {
                                        discordListenAlong = it
                                        prefs.setDiscordListenAlongEnabled(it)
                                        context.startService(Intent(context, PlaybackService::class.java).apply {
                                            action = PlaybackService.ACTION_FORCE_UPDATE
                                        })
                                    },
                                )
                            }
                        }

                        if (code == Together.personalLiveCode) {
                            TogetherGroupItem(
                                onClick = hapticAction(HapticKind.UI) {
                                    val fresh = Together.regeneratePersonalLiveCode()
                                    TogetherLinks.copyInviteLink(context, fresh)
                                },
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    TogetherIconBadge(icon = Icons.Rounded.Refresh)
                                    Text(
                                        text = stringResource(R.string.together_reset_link),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                } else if (activeRoom?.hostLocked == true) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cs.secondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = cs.onSecondaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.together_listen_only_badge),
                                style = MaterialTheme.typography.labelLarge,
                                color = cs.onSecondaryContainer,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = hapticAction(HapticKind.UI) {
                            onDismissRequest()
                            onOpenRoom(code)
                        },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.together_open_room), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = hapticAction(HapticKind.UI) {
                            Together.stopListening()
                        },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.error),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (isHost) R.string.together_stop_live else R.string.together_leave),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
