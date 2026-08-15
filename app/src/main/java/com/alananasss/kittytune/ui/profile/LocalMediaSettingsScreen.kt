    package com.alananasss.kittytune.ui.profile

    import android.net.Uri
    import androidx.activity.compose.rememberLauncherForActivityResult
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.compose.animation.*
    import androidx.compose.animation.core.Spring
    import androidx.compose.animation.core.spring
    import androidx.compose.animation.core.tween
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.Shape
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.unit.dp
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.LocalMediaRepository
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsItem
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.launch

    @Composable
    fun LocalMediaSettingsScreen(
        onBackClick: () -> Unit
    ) {
        val context = LocalContext.current
        val prefs = remember { PlayerPreferences(context) }
        val scope = rememberCoroutineScope()

        var isEnabled by remember { mutableStateOf(prefs.getLocalMediaEnabled()) }
        var folderUris by remember { mutableStateOf(prefs.getLocalMediaUris().toList()) }

        val deletingUris = remember { mutableStateListOf<String>() }

        val isScanning by LocalMediaRepository.isScanning.collectAsState()
        val scanProgress by LocalMediaRepository.scanProgress.collectAsState()

        val folderPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            if (uri != null) {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) { e.printStackTrace() }

                val uriString = uri.toString()
                prefs.addLocalMediaUri(uriString)
                folderUris = prefs.getLocalMediaUris().toList()
            }
        }

        fun deleteFolderWithAnimation(uriString: String) {
            scope.launch {
                deletingUris.add(uriString)
                delay(400)
                prefs.removeLocalMediaUri(uriString)
                folderUris = prefs.getLocalMediaUris().toList()
                deletingUris.remove(uriString)
            }
        }

        SettingsScaffold(
            title = stringResource(R.string.pref_local_title),
            onBackClick = onBackClick
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(bottom = 180.dp)
            ) {
                item {
                    SettingsGroup(
                        title = stringResource(R.string.settings_cat_general),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_local_enable),
                                    subtitle = stringResource(R.string.pref_local_enable_sub),
                                    hasSwitch = true,
                                    switchState = isEnabled,
                                    onSwitchChange = {
                                        isEnabled = it
                                        prefs.setLocalMediaEnabled(it)
                                    }
                                )
                            }
                        )
                    )
                }

                item {
                    AnimatedVisibility(
                        visible = isEnabled,
                        enter = expandVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            expandFrom = Alignment.Top
                        ) + fadeIn(),
                        exit = shrinkVertically(
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            shrinkTowards = Alignment.Top
                        ) + fadeOut()
                    ) {
                        Column {

                            val dynamicContentItem: @Composable (Shape) -> Unit = { shape ->
                                AnimatedContent(
                                    targetState = folderUris.isEmpty(),
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                                                expandVertically(expandFrom = Alignment.Top) +
                                                scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                                            .togetherWith(
                                                fadeOut(animationSpec = tween(90)) + shrinkVertically()
                                            ).using(
                                                SizeTransform(clip = false, sizeAnimationSpec = { _, _ ->
                                                    spring(stiffness = Spring.StiffnessMediumLow)
                                                })
                                            )
                                    },
                                    label = "FolderListTransition"
                                ) { isEmpty ->
                                    if (isEmpty) {
                                        Surface(
                                            color = Color.Transparent,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            SettingsItem(
                                                shape = shape,
                                                title = stringResource(R.string.pref_local_no_folder),
                                                titleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            folderUris.forEachIndexed { index, uriString ->
                                                key(uriString) {
                                                    val isVisible = !deletingUris.contains(uriString)

                                                    AnimatedVisibility(
                                                        visible = isVisible,
                                                        enter = expandVertically() + fadeIn(),
                                                        exit = shrinkVertically(
                                                            animationSpec = spring(
                                                                stiffness = Spring.StiffnessMediumLow,
                                                                dampingRatio = Spring.DampingRatioNoBouncy
                                                            )
                                                        ) + fadeOut(animationSpec = tween(200))
                                                    ) {
                                                        val path = Uri.parse(uriString).path?.substringAfter("primary:") ?: uriString

                                                        val itemShape = if (index == folderUris.lastIndex) shape else androidx.compose.ui.graphics.RectangleShape

                                                        Surface(
                                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                                            shape = itemShape,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(bottom = 1.dp)
                                                                .height(72.dp)
                                                        ) {
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(horizontal = 20.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    Icons.Rounded.Folder,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary
                                                                )
                                                                Spacer(Modifier.width(20.dp))
                                                                Text(
                                                                    text = path,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    modifier = Modifier.weight(1f),
                                                                    maxLines = 1,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                IconButton(
                                                                    onClick = { deleteFolderWithAnimation(uriString) }
                                                                ) {
                                                                    Icon(
                                                                        Icons.Rounded.Delete,
                                                                        stringResource(R.string.btn_delete),
                                                                        tint = MaterialTheme.colorScheme.error
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val itemsList = mutableListOf<@Composable (Shape) -> Unit>()

                            itemsList.add { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.pref_local_add),
                                    onClick = { folderPicker.launch(null) },
                                    titleColor = MaterialTheme.colorScheme.primary
                                )
                            }

                            itemsList.add(dynamicContentItem)

                            SettingsGroup(
                                title = stringResource(R.string.pref_local_folders),
                                items = itemsList
                            )

                            Spacer(Modifier.height(32.dp))

                            AnimatedVisibility(
                                visible = folderUris.isNotEmpty(),
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch { LocalMediaRepository.scanLocalMedia(context) }
                                        },
                                        enabled = !isScanning,
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                        ),
                                        elevation = ButtonDefaults.buttonElevation(
                                            defaultElevation = 0.dp,
                                            pressedElevation = 2.dp
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .height(56.dp)
                                    ) {
                                        if (isScanning) {
                                            LoadingIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = stringResource(R.string.pref_local_scanning),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = stringResource(R.string.pref_local_scan),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    AnimatedVisibility(visible = isScanning) {
                                        Text(
                                            text = scanProgress,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .padding(top = 12.dp)
                                                .fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.pref_local_info),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }

