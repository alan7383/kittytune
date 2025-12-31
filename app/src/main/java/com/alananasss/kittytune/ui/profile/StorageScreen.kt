package com.alananasss.kittytune.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(
    onBackClick: () -> Unit,
    viewModel: StorageViewModel = viewModel()
) {
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFolderSelected(uri)
        }
    }

    var showDeleteDialog by remember { mutableStateOf<DeleteAction?>(null) }

    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Outlined.DeleteForever, null) },
            title = { Text(stringResource(R.string.dialog_clean_title)) },
            text = { Text(showDeleteDialog!!.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog!!.action()
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pref_storage_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            val totalAppUsage = viewModel.audioSize + viewModel.imageSize + viewModel.cacheSize + viewModel.databaseSize

            StorageGaugeCard(
                usedBytes = viewModel.usedSpace,
                freeBytes = viewModel.freeSpace,
                appBytes = totalAppUsage,
                totalBytes = viewModel.totalSpace,
                formatSize = viewModel::formatSize
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.storage_location),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            LocationSelectorCard(
                currentPath = viewModel.currentPath,
                isExternal = viewModel.isExternal,
                onChangeClick = { folderPicker.launch(null) },
                onResetClick = { viewModel.resetToDefault() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                stringResource(R.string.detail_tags), // Note: Reuse of "Tags" string, could be more specific
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column {
                    StorageItemRow(
                        icon = Icons.Outlined.MusicNote,
                        title = stringResource(R.string.storage_cat_audio),
                        size = viewModel.audioSize,
                        formatSize = viewModel::formatSize,
                        color = MaterialTheme.colorScheme.primary,
                        isDeletable = true,
                        onClick = {
                            showDeleteDialog = DeleteAction(
                                context.getString(R.string.dialog_clean_audio_msg),
                                { viewModel.cleanAudio() }
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    StorageItemRow(
                        icon = Icons.Outlined.Image,
                        title = stringResource(R.string.storage_cat_images),
                        size = viewModel.imageSize,
                        formatSize = viewModel::formatSize,
                        color = MaterialTheme.colorScheme.tertiary,
                        isDeletable = true,
                        onClick = {
                            showDeleteDialog = DeleteAction(
                                context.getString(R.string.dialog_clean_images_msg),
                                { viewModel.cleanImages() }
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    StorageItemRow(
                        icon = Icons.Outlined.Cached,
                        title = stringResource(R.string.storage_cat_cache),
                        size = viewModel.cacheSize,
                        formatSize = viewModel::formatSize,
                        color = MaterialTheme.colorScheme.secondary,
                        isDeletable = true,
                        onClick = {
                            showDeleteDialog = DeleteAction(
                                context.getString(R.string.dialog_clean_cache_msg),
                                { viewModel.cleanCache() }
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    StorageItemRow(
                        icon = Icons.Outlined.Storage,
                        title = stringResource(R.string.storage_cat_db),
                        subtitle = stringResource(R.string.storage_db_subtitle),
                        size = viewModel.databaseSize,
                        formatSize = viewModel::formatSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        isDeletable = false,
                        onClick = {}
                    )
                }
            }
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

data class DeleteAction(val message: String, val action: () -> Unit)

@Composable
fun StorageGaugeCard(
    usedBytes: Long,
    freeBytes: Long,
    appBytes: Long,
    totalBytes: Long,
    formatSize: (Long) -> String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(stringResource(R.string.storage_usage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = stringResource(R.string.storage_used_formatted, formatSize(appBytes)),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = stringResource(R.string.storage_free_formatted, formatSize(freeBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val appRatio = (appBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            val usedRatio = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

            val animatedAppRatio by animateFloatAsState(targetValue = appRatio, label = "app")
            val animatedUsedRatio by animateFloatAsState(targetValue = usedRatio, label = "used")

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedUsedRatio)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedAppRatio)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(8.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelSmall)

                Spacer(Modifier.width(16.dp))

                Badge(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f), modifier = Modifier.size(8.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.storage_legend_system), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun LocationSelectorCard(
    currentPath: String,
    isExternal: Boolean,
    onChangeClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
        onClick = onChangeClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = if (isExternal) Icons.Default.SdStorage else Icons.Rounded.Folder
            val iconColor = if (isExternal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor)
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.storage_change_cta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExternal) {
                IconButton(onClick = onResetClick) {
                    Icon(Icons.Outlined.Restore, stringResource(R.string.storage_reset), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StorageItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    size: Long,
    formatSize: (Long) -> String,
    color: Color,
    isDeletable: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(enabled = isDeletable, onClick = onClick),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color)
            }
        },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            if (subtitle != null) Text(subtitle)
            else Text(formatSize(size), color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            if (isDeletable) {
                IconButton(onClick = onClick) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.btn_delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            } else if (subtitle == null) {
                Text(formatSize(size), style = MaterialTheme.typography.bodyMedium)
            }
        }
    )
}