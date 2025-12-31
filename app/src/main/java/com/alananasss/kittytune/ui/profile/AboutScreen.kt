package com.alananasss.kittytune.ui.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.AchievementNotification
import com.alananasss.kittytune.ui.common.AchievementNotificationManager
import com.alananasss.kittytune.utils.AppUtils
import kotlinx.coroutines.launch

// helper model for credits list
data class Contributor(
    val name: String,
    val roleResId: Int, // using resource id for translation
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appVersion = AppUtils.getAppVersion(context)
    val scope = rememberCoroutineScope()
    var tapCount by remember { mutableIntStateOf(0) }

    var showLicensesDialog by remember { mutableStateOf(false) }
    var showCreditsSheet by remember { mutableStateOf(false) }

    // --- CONTRIBUTORS LIST ---
    // add people here
    val contributors = listOf(
        Contributor("alananasss", R.string.about_role_dev, "https://github.com/alan7383"),
        Contributor("mattdotcat", R.string.about_role_translation, "https://t.me/b37246")
        // Contributor("New", R.string.role, "url"),
    )

    if (showLicensesDialog) {
        LicensesScreen(onDismiss = { showLicensesDialog = false })
    }

    if (showCreditsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCreditsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
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

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.pref_about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_close))
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- HEADER (CAT LOGO) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.tertiaryContainer, // different color for fun
                    modifier = Modifier.size(88.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Pets,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Badge(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable {
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
                                Toast.makeText(context, context.getString(R.string.test_notification_triggered), Toast.LENGTH_SHORT).show()
                            }
                        }
                ) {
                    Text(
                        text = "v$appVersion",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // --- MAIN CARD ---
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column {
                    // credits button
                    SettingsLinkRow(
                        icon = Icons.Rounded.Groups,
                        title = stringResource(R.string.about_credits),
                        onClick = { showCreditsSheet = true }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

                    // github
                    SettingsLinkRow(
                        icon = Icons.Rounded.Code,
                        title = stringResource(R.string.about_github),
                        onClick = { uriHandler.openUri("https://github.com/alananasss/KittyTune") }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

                    // bug report
                    SettingsLinkRow(
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(R.string.about_bug_report),
                        onClick = { uriHandler.openUri("https://github.com/alananasss/KittyTune/issues") }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surface)

                    // licenses
                    SettingsLinkRow(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        title = stringResource(R.string.about_licenses),
                        onClick = { showLicensesDialog = true }
                    )
                }
            }

            // --- TRANSLATION CARD ---
            Card(
                onClick = { uriHandler.openUri("https://github.com/alananasss/KittyTune/pulls") },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth() // ensure card takes full width
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth() // row takes full width available in card
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center // <--- FIX: centers content horizontally
                ) {
                    Text(
                        text = "( ◕▿◕ )",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                    Column(
                        horizontalAlignment = Alignment.Start // text aligns left relative to itself
                    ) {
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

            // --- TECH INFO (Hidden) ---
            ExpandableTechInfo(packageName = context.packageName)

            Spacer(Modifier.height(32.dp))

            // --- FOOTER ---
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

// utility functions

@Composable
fun SettingsLinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack, // using arrow back but flipped for right arrow
            null,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 180f }, // simple hack for right arrow
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun ExpandableTechInfo(packageName: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if(expanded) stringResource(R.string.about_collapse) else stringResource(R.string.about_app_info),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(packageName, style = MaterialTheme.typography.bodySmall)
                Text("Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall)
                Text("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}