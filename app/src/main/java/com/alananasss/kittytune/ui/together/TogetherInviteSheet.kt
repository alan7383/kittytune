@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.alananasss.kittytune.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Podcasts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.common.hapticAction

/**
 * Previews an invitation without joining and asks which name to share with the session.
 */
@Composable
fun TogetherInviteSheet(
    code: String,
    onJoinAndListen: (String, String) -> Unit,
    onOpenRoomOnly: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val normalized = TogetherWire.normalizeCode(code)
    val rooms by Together.rooms.collectAsState()
    val selfAvatarUrl by Together.myAvatarUrlFlow.collectAsState()
    val room = rooms[normalized] ?: Together.Room(normalized, "")
    val cs = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val defaultNickname = stringResource(R.string.together_invite_default_nickname)
    var nickname by rememberSaveable(normalized) { mutableStateOf(Together.displayName(defaultNickname)) }

    DisposableEffect(normalized) {
        Together.previewRoom(normalized)
        onDispose {
            Together.closePreview(normalized)
        }
    }

    val hostName = room.hostDisplayName
    val isLive = room.hasLiveHost

    KittyModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = cs.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExpressiveCookieBadge(
                isActive = isLive,
                size = 68.dp,
                activeColor = cs.primary,
                inactiveColor = cs.secondaryContainer,
            ) {
                if (isLive && room.isPlaying) {
                    ListeningBars(
                        isPlaying = true,
                        modifier = Modifier.size(28.dp),
                        color = cs.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Podcasts,
                        contentDescription = null,
                        tint = if (isLive) cs.onPrimary else cs.onSecondaryContainer,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = if (isLive) cs.primaryContainer else cs.surfaceContainerHighest,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (isLive) {
                            "${stringResource(R.string.together_live_badge)} · ${TogetherWire.displayCode(normalized)}"
                        } else {
                            TogetherWire.displayCode(normalized)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isLive) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    )
                    if (room.hostLocked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (isLive) cs.onPrimaryContainer else cs.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Text(
                text = if (!hostName.isNullOrBlank()) {
                    stringResource(R.string.together_invite_prompt_title, hostName)
                } else {
                    stringResource(R.string.together_invite_prompt_generic)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = cs.onSurface,
            )

            Text(
                text = stringResource(R.string.together_invite_prompt_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = cs.onSurfaceVariant,
            )

            // Live track preview card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(cs.surfaceContainerHigh)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val now = room.nowPlaying
                if (now != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        TogetherArtwork(
                            url = now.artworkUrl,
                            isLive = room.isPlaying,
                            size = 64.dp,
                            cornerRadius = 16.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = now.title ?: stringResource(R.string.untitled_track),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = now.artist.orEmpty().ifBlank { stringResource(R.string.unknown_artist) },
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TogetherLiveProgressBar(room = room)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TogetherIconBadge(icon = Icons.Rounded.Headphones)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = room.name.ifBlank { stringResource(R.string.together_title) },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(
                                    if (isLive) R.string.together_invite_waiting_track
                                    else R.string.together_invite_connecting
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (room.listeners.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        room.listeners.forEach { member ->
                            val isSelf = member.id == Together.memberId
                            TogetherMemberPill(
                                name = member.name,
                                isHost = member.id == room.hostId,
                                isMe = isSelf,
                                avatarUrl = if (isSelf) (selfAvatarUrl ?: Together.myAvatarUrl) else member.avatarUrl,
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                modifier = Modifier.fillMaxWidth().testTag("together-invite-nickname"),
                label = { Text(stringResource(R.string.together_invite_nickname)) },
                supportingText = { Text(stringResource(R.string.together_invite_nickname_hint)) },
                leadingIcon = { Icon(Icons.Rounded.PersonOutline, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                }),
            )

            Button(
                onClick = hapticAction(HapticKind.MEDIUM) {
                    onJoinAndListen(normalized, nickname.trim())
                },
                enabled = nickname.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
            ) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.together_invite_confirm),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = hapticAction(HapticKind.UI, onDismissRequest),
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
                FilledTonalButton(
                    onClick = hapticAction(HapticKind.UI) { onOpenRoomOnly(normalized) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.together_invite_view_only),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
