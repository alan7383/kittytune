package com.alananasss.kittytune.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.LocalMediaRepository
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMediaSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val scope = rememberCoroutineScope()

    var isEnabled by remember { mutableStateOf(prefs.getLocalMediaEnabled()) }
    var folderUris by remember { mutableStateOf(prefs.getLocalMediaUris().toList()) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pref_local_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                SettingsCategoryHeader(stringResource(R.string.settings_cat_general))

                SwitchSettingItem(
                    icon = Icons.Default.SdStorage,
                    title = stringResource(R.string.pref_local_enable),
                    subtitle = stringResource(R.string.pref_local_enable_sub),
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        prefs.setLocalMediaEnabled(it)
                    }
                )

                if (isEnabled) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))

                    SettingsCategoryHeader(stringResource(R.string.pref_local_folders))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.pref_local_add), color = MaterialTheme.colorScheme.primary) },
                        leadingContent = { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { folderPicker.launch(null) }
                    )
                }
            }

            if (isEnabled) {
                if (folderUris.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.pref_local_no_folder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(folderUris) { uriString ->
                        val path = Uri.parse(uriString).path?.substringAfter("primary:") ?: uriString

                        ListItem(
                            headlineContent = { Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = {
                                IconButton(onClick = {
                                    prefs.removeLocalMediaUri(uriString)
                                    folderUris = prefs.getLocalMediaUris().toList()
                                }) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { scope.launch { LocalMediaRepository.scanLocalMedia(context) } },
                            enabled = !isScanning && folderUris.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.pref_local_scanning))
                            } else {
                                Icon(Icons.Default.Refresh, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.pref_local_scan))
                            }
                        }
                    }

                    if (isScanning) {
                        Text(
                            text = scanProgress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    InfoCard(stringResource(R.string.pref_local_info))
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}