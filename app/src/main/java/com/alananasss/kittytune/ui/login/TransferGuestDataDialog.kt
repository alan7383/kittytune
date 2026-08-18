package com.alananasss.kittytune.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.GuestDataSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferGuestDataDialog(
    summary: GuestDataSummary,
    isTransferring: Boolean,
    progress: Float,
    onTransfer: (transferLikes: Boolean, transferUserPlaylists: Boolean, transferLikedPlaylists: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var transferLikes by remember { mutableStateOf(summary.likes.isNotEmpty()) }
    var transferPlaylists by remember { mutableStateOf(summary.userPlaylists.isNotEmpty()) }
    var transferLikedPlaylists by remember { mutableStateOf(summary.likedPlaylists.isNotEmpty()) }

    var showWarningDialog1 by remember { mutableStateOf(false) }
    var showWarningDialog2 by remember { mutableStateOf(false) }

    if (showWarningDialog1) {
        AlertDialog(
            onDismissRequest = { showWarningDialog1 = false },
            icon = {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.transfer_guest_warn_title_1),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.transfer_guest_warn_desc_1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningDialog1 = false
                        showWarningDialog2 = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.transfer_guest_warn_btn_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog1 = false }) {
                    Text(stringResource(R.string.transfer_guest_warn_btn_back))
                }
            }
        )
    }

    if (showWarningDialog2) {
        AlertDialog(
            onDismissRequest = { showWarningDialog2 = false },
            icon = {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.transfer_guest_warn_title_2),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.transfer_guest_warn_desc_2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWarningDialog2 = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.transfer_guest_warn_btn_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog2 = false }) {
                    Text(stringResource(R.string.transfer_guest_warn_btn_cancel))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (!isTransferring) showWarningDialog1 = true
        },
        icon = {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.transfer_guest_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.transfer_guest_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (summary.likes.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isTransferring) {
                                transferLikes = !transferLikes
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.transfer_guest_likes, summary.likes.size),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = transferLikes,
                                onCheckedChange = { transferLikes = it },
                                enabled = !isTransferring
                            )
                        }
                    }
                }

                if (summary.userPlaylists.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isTransferring) {
                                transferPlaylists = !transferPlaylists
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.transfer_guest_playlists, summary.userPlaylists.size),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = transferPlaylists,
                                onCheckedChange = { transferPlaylists = it },
                                enabled = !isTransferring
                            )
                        }
                    }
                }

                if (summary.likedPlaylists.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isTransferring) {
                                transferLikedPlaylists = !transferLikedPlaylists
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LibraryMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.transfer_guest_liked_playlists, summary.likedPlaylists.size),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = transferLikedPlaylists,
                                onCheckedChange = { transferLikedPlaylists = it },
                                enabled = !isTransferring
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isTransferring) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                        Text(
                            text = stringResource(R.string.transfer_guest_in_progress),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onTransfer(transferLikes, transferPlaylists, transferLikedPlaylists)
                },
                enabled = !isTransferring && (transferLikes || transferPlaylists || transferLikedPlaylists)
            ) {
                Text(stringResource(R.string.transfer_guest_btn_transfer))
            }
        },
        dismissButton = {
            if (!isTransferring) {
                TextButton(onClick = { showWarningDialog1 = true }) {
                    Text(stringResource(R.string.transfer_guest_btn_skip))
                }
            }
        }
    )
}
