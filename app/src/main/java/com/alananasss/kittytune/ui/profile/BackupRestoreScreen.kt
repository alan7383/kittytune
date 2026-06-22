    package com.alananasss.kittytune.ui.profile
    
    import android.net.Uri
    import android.widget.Toast
    import androidx.activity.compose.rememberLauncherForActivityResult
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.CloudUpload
    import androidx.compose.material.icons.rounded.Info
    import androidx.compose.material.icons.rounded.Restore
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.Shape
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.BackupManager
    import com.alananasss.kittytune.ui.common.SettingsGroup
    import com.alananasss.kittytune.ui.common.SettingsItem
    import com.alananasss.kittytune.ui.common.SettingsScaffold
    import kotlinx.coroutines.launch
    
    @Composable
    fun BackupRestoreScreen(
        onBackClick: () -> Unit,
        viewModel: BackupViewModel = viewModel()
    ) {
        val context = LocalContext.current
    
        val createDocumentLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            uri?.let { viewModel.backup(it) }
        }
    
        val openDocumentLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let { viewModel.restore(it) }
        }
    
        SettingsScaffold(
            title = stringResource(R.string.pref_backup_title),
            onBackClick = onBackClick
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 180.dp)
                ) {
                    // ACTIONS GROUP
                    item {
                        SettingsGroup(
                            title = stringResource(R.string.pref_backup_subtitle),
                            items = listOf(
                                { shape ->
                                    SettingsItem(
                                        shape = shape,
                                        title = stringResource(R.string.backup_action),
                                        subtitle = stringResource(R.string.backup_desc),
                                        onClick = { createDocumentLauncher.launch(BackupManager.getBackupFileName()) }
                                    )
                                },
                                { shape ->
                                    SettingsItem(
                                        shape = shape,
                                        title = stringResource(R.string.restore_action),
                                        subtitle = stringResource(R.string.restore_desc),
                                        onClick = { openDocumentLauncher.launch(arrayOf("*/*")) }
                                    )
                                }
                            )
                        )
                    }
    
                    // INFO SECTION
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 24.dp)
                        ) {
                            InfoCard(text = stringResource(R.string.backup_info))
                            Spacer(Modifier.height(12.dp))
                            InfoCard(text = stringResource(R.string.backup_info_guest))
                        }
                    }
    
                    // STATUS MESSAGE
                    if (viewModel.statusMessage != null) {
                        item {
                            Text(
                                text = viewModel.statusMessage!!,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
    
                // LOADING OVERLAY
                if (viewModel.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.backup_in_progress))
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun InfoCard(text: String) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
        }
    }


