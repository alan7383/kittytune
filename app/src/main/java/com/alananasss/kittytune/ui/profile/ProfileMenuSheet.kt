package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.User

@Composable
fun ProfileMenuSheet(
    user: User?,
    isGuest: Boolean,
    onDismiss: () -> Unit,
    onViewProfile: () -> Unit,
    onAchievementsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(stringResource(id = R.string.app_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (user?.avatarUrl != null && !isGuest) {
                        ArtistAvatar(
                            avatarUrl = user.avatarUrl,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if(isGuest) "G" else user?.username?.take(1)?.uppercase() ?: "U",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isGuest) stringResource(R.string.guest_user) else user?.username ?: stringResource(R.string.unknown_user),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isGuest) stringResource(R.string.profile_menu_guest_desc) else "${user?.followersCount ?: 0} ${stringResource(R.string.profile_followers)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.btn_close))
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!isGuest) {
                    OutlinedButton(
                        onClick = { onDismiss(); onViewProfile() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.profile_menu_manage_account))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onDismiss(); onLogoutClick() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.profile_menu_login))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                MenuRowItem(
                    icon = Icons.Rounded.EmojiEvents,
                    label = stringResource(R.string.profile_menu_achievements),
                    onClick = { onDismiss(); onAchievementsClick() },
                    isNew = true
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)

                MenuRowItem(
                    icon = Icons.Rounded.Settings,
                    label = stringResource(R.string.profile_menu_settings),
                    onClick = { onDismiss(); onSettingsClick() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surface, thickness = 1.dp)

                if (!isGuest) {
                    MenuRowItem(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        label = stringResource(R.string.profile_menu_logout),
                        onClick = { onDismiss(); onLogoutClick() }
                    )
                }
            }
        }
    }
}

@Composable
fun MenuRowItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isNew: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium
        )
        if (isNew) {
            Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(stringResource(R.string.profile_menu_badge_new)) }
        }
    }
}