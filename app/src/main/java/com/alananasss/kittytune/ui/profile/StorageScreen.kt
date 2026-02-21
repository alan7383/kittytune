    package com.alananasss.kittytune.ui.profile
    
    import android.net.Uri
    import android.text.format.Formatter
    import androidx.activity.compose.rememberLauncherForActivityResult
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.compose.animation.core.FastOutSlowInEasing
    import androidx.compose.animation.core.animateFloatAsState
    import androidx.compose.animation.core.tween
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.outlined.*
    import androidx.compose.material.icons.rounded.Folder
    import androidx.compose.material.icons.rounded.SdStorage
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.Shape
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    
    data class DeleteAction(val message: String, val action: () -> Unit)
    
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
            val deleteAction = showDeleteDialog!!
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                icon = { Icon(Icons.Outlined.DeleteForever, null) },
                title = { Text(stringResource(R.string.dialog_clean_title)) },
                text = { Text(deleteAction.message) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deleteAction.action()
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
    
        SettingsScaffold(
            title = stringResource(R.string.pref_storage_title),
            onBackClick = onBackClick
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // GAUGE (Kept separate as it's not a list item)
                item {
                    Box(modifier = Modifier.padding(16.dp)) {
                        DetailedStorageGauge(
                            audioBytes = viewModel.audioSize,
                            imageBytes = viewModel.imageSize,
                            cacheBytes = viewModel.cacheSize,
                            dbBytes = viewModel.databaseSize,
                            freeSpaceBytes = viewModel.freeSpace,
                            formatSize = viewModel::formatSize
                        )
                    }
                }
    
                // LOCATION
                item {
                    SettingsGroup(
                        title = stringResource(R.string.storage_location),
                        items = listOf(
                            { shape ->
                                LocationSelectorItem(
                                    shape = shape,
                                    currentPath = viewModel.currentPath,
                                    isExternal = viewModel.isExternal,
                                    onChangeClick = { folderPicker.launch(null) },
                                    onResetClick = { viewModel.resetToDefault() }
                                )
                            }
                        )
                    )
                }
    
                // DETAILS & CLEANUP
                item {
                    SettingsGroup(
                        title = stringResource(R.string.menu_details),
                        items = listOf(
                            { shape ->
                                StorageItemRow(
                                    shape = shape,
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
                            },
                            { shape ->
                                StorageItemRow(
                                    shape = shape,
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
                            },
                            { shape ->
                                StorageItemRow(
                                    shape = shape,
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
                            },
                            { shape ->
                                StorageItemRow(
                                    shape = shape,
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
                        )
                    )
                }
            }
        }
    }
    
    @Composable
    fun DetailedStorageGauge(
        audioBytes: Long,
        imageBytes: Long,
        cacheBytes: Long,
        dbBytes: Long,
        freeSpaceBytes: Long,
        formatSize: (Long) -> String
    ) {
        val animAudio by animateFloatAsState(targetValue = audioBytes.toFloat(), animationSpec = tween(800, easing = FastOutSlowInEasing), label = "audio")
        val animImage by animateFloatAsState(targetValue = imageBytes.toFloat(), animationSpec = tween(800, easing = FastOutSlowInEasing), label = "image")
        val animCache by animateFloatAsState(targetValue = cacheBytes.toFloat(), animationSpec = tween(800, easing = FastOutSlowInEasing), label = "cache")
        val animDb by animateFloatAsState(targetValue = dbBytes.toFloat(), animationSpec = tween(800, easing = FastOutSlowInEasing), label = "db")
    
        val totalAnimated = (animAudio + animImage + animCache + animDb).coerceAtLeast(1f)
    
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatSize(totalAnimated.toLong()) + " " + stringResource(R.string.storage_used),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(IntrinsicSize.Min)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = stringResource(R.string.storage_free_formatted, formatSize(freeSpaceBytes)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                maxLines = 2
                            )
                        }
                    }
                }
    
                Spacer(modifier = Modifier.height(24.dp))
    
                Box(modifier = Modifier.clip(RoundedCornerShape(12.dp))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        if (totalAnimated > 1024f) {
                            if (animAudio > 0) {
                                Box(modifier = Modifier.weight(animAudio).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                            if (animImage > 0) {
                                Box(modifier = Modifier.weight(animImage).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                            if (animCache > 0) {
                                Box(modifier = Modifier.weight(animCache).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                            if (animDb > 0) {
                                Box(modifier = Modifier.weight(animDb).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))
                            }
                        }
                    }
                }
    
                Spacer(modifier = Modifier.height(20.dp))
    
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LegendItem(
                            color = MaterialTheme.colorScheme.primary,
                            label = stringResource(R.string.storage_cat_audio),
                            size = formatSize(animAudio.toLong()),
                            modifier = Modifier.weight(1f)
                        )
                        LegendItem(
                            color = MaterialTheme.colorScheme.tertiary,
                            label = stringResource(R.string.storage_cat_images),
                            size = formatSize(animImage.toLong()),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        LegendItem(
                            color = MaterialTheme.colorScheme.secondary,
                            label = stringResource(R.string.storage_cat_cache),
                            size = formatSize(animCache.toLong()),
                            modifier = Modifier.weight(1f)
                        )
                        LegendItem(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            label = stringResource(R.string.storage_cat_db),
                            size = formatSize(animDb.toLong()),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    fun LegendItem(color: Color, label: String, size: String, modifier: Modifier = Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(size, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
    
    @Composable
    fun LocationSelectorItem(
        shape: Shape,
        currentPath: String,
        isExternal: Boolean,
        onChangeClick: () -> Unit,
        onResetClick: () -> Unit
    ) {
        Card(
            onClick = onChangeClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = shape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val icon = if (isExternal) Icons.Rounded.SdStorage else Icons.Rounded.Folder
                val iconColor = if (isExternal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    
                // Icon container to match SettingsItem style
                Icon(icon, null, tint = iconColor)
    
                Spacer(Modifier.width(20.dp))
    
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                    Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
    
    @Composable
    fun StorageItemRow(
        shape: Shape,
        icon: ImageVector,
        title: String,
        subtitle: String? = null,
        size: Long,
        formatSize: (Long) -> String,
        color: Color,
        isDeletable: Boolean,
        onClick: () -> Unit
    ) {
        val animSize by animateFloatAsState(targetValue = size.toFloat(), label = "listSize")
    
        Card(
            onClick = onClick,
            enabled = isDeletable,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = shape,
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = color)
    
                Spacer(Modifier.width(20.dp))
    
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Text(formatSize(animSize.toLong()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
    
                if (isDeletable) {
                    IconButton(onClick = onClick) {
                        Icon(
                            Icons.Outlined.DeleteForever,
                            contentDescription = stringResource(R.string.btn_delete),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                } else if (subtitle == null) {
                    Text(formatSize(animSize.toLong()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }


