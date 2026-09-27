package com.alananasss.kittytune.ui.profile

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.utils.AppLogManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppLogsSheet(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoadingLogcat by remember { mutableStateOf(true) }
    var rawLogcat by remember { mutableStateOf("") }
    var lastCrash by remember { mutableStateOf<String?>(null) }
    var diagnosticsMap by remember { mutableStateOf<Map<String, List<Pair<String, String>>>>(emptyMap()) }

    fun refreshData() {
        isLoadingLogcat = true
        diagnosticsMap = AppLogManager.getDiagnosticsMap(context)
        lastCrash = AppLogManager.getLastCrash(context)
        scope.launch {
            rawLogcat = AppLogManager.getLogcat(maxLines = 1500)
            isLoadingLogcat = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val filteredLogcat = remember(rawLogcat, searchQuery) {
        if (searchQuery.isBlank()) {
            rawLogcat
        } else {
            rawLogcat.lineSequence()
                .filter { it.contains(searchQuery, ignoreCase = true) }
                .joinToString("\n")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.logs_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.logs_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        refreshData()
                    },
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.logs_refresh)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Primary Actions Bar with ButtonDefaults.shapes()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        scope.launch {
                            val fullReport = AppLogManager.getFullReport(context)
                            AppLogManager.copyToClipboard(context, fullReport)
                            Toast.makeText(
                                context,
                                context.getString(R.string.logs_copied_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.logs_copy_all),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                FilledTonalButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        scope.launch {
                            val fullReport = AppLogManager.getFullReport(context)
                            AppLogManager.shareText(
                                context,
                                fullReport,
                                context.getString(R.string.logs_title)
                            )
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.logs_share),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Navigation Tabs
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedTab = 0
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.logs_tab_diagnostics),
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedTab = 1
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.logs_tab_logcat),
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        selectedTab = 2
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.logs_tab_crash),
                                fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium
                            )
                            if (!lastCrash.isNullOrBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.error) {
                                    Text("!", color = MaterialTheme.colorScheme.onError)
                                }
                            }
                        }
                    }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> DiagnosticsTabContent(
                    diagnosticsMap = diagnosticsMap,
                    onCopySummary = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        val summary = AppLogManager.getDiagnosticsSummary(context)
                        AppLogManager.copyToClipboard(context, summary, "KittyTune Diagnostics")
                        Toast.makeText(
                            context,
                            context.getString(R.string.logs_copied_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                1 -> LogcatTabContent(
                    logcatText = filteredLogcat,
                    searchQuery = searchQuery,
                    isLoading = isLoadingLogcat,
                    onSearchQueryChange = { searchQuery = it },
                    onCopyLogcat = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        AppLogManager.copyToClipboard(context, filteredLogcat, "KittyTune Logcat")
                        Toast.makeText(
                            context,
                            context.getString(R.string.logs_copied_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                2 -> CrashTabContent(
                    crashReport = lastCrash,
                    onClearCrash = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        AppLogManager.clearLastCrash(context)
                        lastCrash = null
                    },
                    onCopyCrash = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        lastCrash?.let {
                            AppLogManager.copyToClipboard(context, it, "KittyTune Crash")
                            Toast.makeText(
                                context,
                                context.getString(R.string.logs_copied_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsTabContent(
    diagnosticsMap: Map<String, List<Pair<String, String>>>,
    onCopySummary: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onCopySummary,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.logs_copy_summary),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        items(diagnosticsMap.entries.toList(), key = { it.key }) { (sectionTitle, items) ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(10.dp))

                    items.forEachIndexed { index, (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1.5f),
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (index < items.size - 1) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogcatTabContent(
    logcatText: String,
    searchQuery: String,
    isLoading: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onCopyLogcat: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.logs_search_hint), style = MaterialTheme.typography.bodySmall) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.weight(1f).height(50.dp)
            )

            OutlinedButton(
                onClick = onCopyLogcat,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.height(50.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.logs_copy_logcat), style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                val horizontalScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScroll)
                    ) {
                        item {
                            Text(
                                text = logcatText.ifBlank { "No logs matched your filter." },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashTabContent(
    crashReport: String?,
    onClearCrash: () -> Unit,
    onCopyCrash: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (crashReport.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.logs_no_crash),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCopyCrash,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.logs_copy_summary), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onClearCrash,
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.logs_clear_crash))
                }
            }

            Spacer(Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                val horizontalScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScroll)
                    ) {
                        item {
                            Text(
                                text = crashReport,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}
