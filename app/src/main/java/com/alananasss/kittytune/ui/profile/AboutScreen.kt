package com.alananasss.kittytune.ui.profile

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.UpdateManager
import com.alananasss.kittytune.data.UpdateStatus
import com.alananasss.kittytune.ui.common.AchievementNotification
import com.alananasss.kittytune.ui.common.AchievementNotificationManager
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.utils.AppUtils
import kotlinx.coroutines.launch

data class Contributor(
    val name: String,
    val roleResId: Int,
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit,
    onLicensesClick: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val view = androidx.compose.ui.platform.LocalView.current
    val appVersion = AppUtils.getAppVersion(context)
    val scope = rememberCoroutineScope()
    var tapCount by remember { mutableIntStateOf(0) }

    var showCreditsSheet by remember { mutableStateOf(false) }
    val updateStatus by UpdateManager.status.collectAsState()

    LaunchedEffect(updateStatus) {
        if (updateStatus == UpdateStatus.NO_UPDATE) {
            Toast.makeText(context, context.getString(R.string.update_no_update), Toast.LENGTH_SHORT).show()
            UpdateManager.dismiss()
        } else if (updateStatus == UpdateStatus.ERROR) {
            Toast.makeText(context, context.getString(R.string.update_error), Toast.LENGTH_SHORT).show()
            UpdateManager.dismiss()
        }
    }

    val contributors = listOf(
        Contributor("alananasss", R.string.about_role_dev, "https://github.com/alan7383"),
        Contributor("mattdotcat", R.string.about_role_translation, "https://t.me/b37246"),
        Contributor("Егор Белоусов (kivoyoso)", R.string.about_role_translation, "https://crowdin.com/profile/kivoyoso")
    )

    if (showCreditsSheet) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showCreditsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    text = stringResource(R.string.about_credits),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, bottom = 16.dp)
                )
                LazyColumn {
                    items(contributors) { person ->
                        ListItem(
                            headlineContent = { Text(person.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { Text(stringResource(person.roleResId)) },
                            leadingContent = {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = person.name.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.clickable { uriHandler.openUri(person.url) }
                        )
                    }
                }
            }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_about_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {

            // --- COOL HERO SECTION ---
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // App Logo
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) {
                                    tapCount++
                                    if (tapCount >= 7) {
                                        tapCount = 0
                                        scope.launch {
                                            AchievementNotificationManager.showNotification(
                                                AchievementNotification(
                                                    title = context.getString(R.string.achievement_unlocked),
                                                    subtitle = "Meow Mode Activated 🐱",
                                                    iconEmoji = "🧶",
                                                    xpReward = 1337
                                                )
                                            )
                                        }
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.test_notification_triggered),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_launcher_monochrome),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize().scale(1.8f)
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.height(8.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "Version $appVersion",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        Button(
                            onClick = {
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                scope.launch {
                                    UpdateManager.checkForUpdate(context, isManual = true)
                                }
                            },
                            enabled = updateStatus != UpdateStatus.CHECKING && updateStatus != UpdateStatus.DOWNLOADING,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        ) {
                            if (updateStatus == UpdateStatus.CHECKING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                            } else {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                stringResource(R.string.update_check_manual),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.about_kofi_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_kofi_symbol),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.about_kofi_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { uriHandler.openUri("https://ko-fi.com/alan7383") },
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_kofi_logo),
                                    contentDescription = stringResource(R.string.about_kofi_btn),
                                    modifier = Modifier.width(62.dp).height(17.dp),
                                    tint = androidx.compose.ui.graphics.Color.Unspecified
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.about_website_group),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                icon = Icons.Rounded.Language,
                                title = stringResource(R.string.about_website_title),
                                onClick = { uriHandler.openUri("https://alan7383.github.io/kittytune-website/") }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                icon = Icons.Rounded.Download,
                                title = stringResource(R.string.about_downloads_title),
                                subtitle = stringResource(R.string.about_downloads_desc),
                                onClick = { uriHandler.openUri("https://alan7383.github.io/kittytune-website/download") }
                            )
                        }
                    )
                )
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.about_help),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                icon = Icons.Rounded.Groups,
                                title = stringResource(R.string.about_credits),
                                onClick = { showCreditsSheet = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                icon = Icons.Rounded.Code,
                                title = stringResource(R.string.about_github),
                                onClick = { uriHandler.openUri("https://github.com/alan7383/kittytune") }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                icon = Icons.Filled.BugReport,
                                title = stringResource(R.string.about_bug_report),
                                onClick = { uriHandler.openUri("https://github.com/alan7383/kittytune/issues") }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                icon = Icons.Rounded.OpenInNew,
                                title = stringResource(R.string.about_licenses),
                                onClick = onLicensesClick
                            )
                        }
                    )
                )
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                    Card(
                        onClick = { uriHandler.openUri("https://crowdin.com/project/kittytune") },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "( ◕▿◕ )",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = stringResource(R.string.about_translate_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.about_translate_desc).substringAfter("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsGroup(
                    items = listOf(
                        {
                            ExpandableTechInfo(
                                packageName = context.packageName,
                                shape = RoundedCornerShape(24.dp)
                            )
                        }
                    )
                )
            }

            item {
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.about_made_with),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ExpandableTechInfo(packageName: String, shape: androidx.compose.ui.graphics.Shape) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = shape,
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (expanded) stringResource(R.string.about_collapse)
                    else stringResource(R.string.about_app_info),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}