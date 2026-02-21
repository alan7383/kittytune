    package com.alananasss.kittytune.ui.common
    
    import android.text.format.Formatter
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.verticalScroll
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.SystemUpdate
    import androidx.compose.material.icons.rounded.Download
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.remember
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.StrokeCap
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.compose.ui.window.Dialog
    import androidx.compose.ui.window.DialogProperties
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.network.GithubRelease
    import dev.jeziellago.compose.markdowntext.MarkdownText
    
    @Composable
    fun UpdateAvailableDialog(
        release: GithubRelease,
        onDownload: () -> Unit,
        onDismiss: () -> Unit,
        onDisableAutoUpdate: () -> Unit
    ) {
        val context = LocalContext.current
        val apkAsset = remember(release) {
            release.assets.find { it.name.endsWith(".apk", ignoreCase = true) }
        }
        val fileSizeMb = remember(apkAsset) {
            apkAsset?.let { Formatter.formatFileSize(context, it.size) }
        }
    
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.update_available_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = release.tagName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
    
                    Spacer(Modifier.height(16.dp))
    
                    Text(
                        text = stringResource(R.string.update_available_msg, release.tagName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
    
                    Spacer(Modifier.height(16.dp))
    
                    Text(
                        text = stringResource(R.string.update_changelog),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .heightIn(max = 200.dp)
                    ) {
                        Box(modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())) {
                            MarkdownText(
                                markdown = release.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
    
                    Spacer(Modifier.height(24.dp))
    
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = onDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(50)
                        ) {
                            Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            val btnText = if (fileSizeMb != null) {
                                stringResource(R.string.update_btn_download_with_size, fileSizeMb)
                            } else {
                                stringResource(R.string.update_btn_download)
                            }
                            Text(
                                text = btnText,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
    
                        Spacer(Modifier.height(8.dp))
    
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = stringResource(R.string.update_btn_later),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
    
                        TextButton(
                            onClick = onDisableAutoUpdate,
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.update_btn_disable),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun UpdateProgressDialog(progress: Float, totalSize: Long, status: String) {
        val context = LocalContext.current
        val downloadedSize = (progress * totalSize).toLong()
        val downloadedText = Formatter.formatFileSize(context, downloadedSize)
        val totalText = Formatter.formatFileSize(context, totalSize)
    
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(vertical = 32.dp, horizontal = 24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 6.dp,
                            strokeCap = StrokeCap.Round
                        )
    
                        Spacer(Modifier.height(24.dp))
    
                        Text(
                            text = status,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
    
                        Spacer(Modifier.height(8.dp))
    
                        Text(
                            text = "$downloadedText / $totalText",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }


