package com.alananasss.kittytune.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.vk.VkUser
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VkAccountSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToWebViewLogin: () -> Unit = {},
    accountViewModel: VkAccountViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val user = accountViewModel.user
    val isLoggedIn = accountViewModel.isLoggedIn
    val isLoading = accountViewModel.isLoading
    val isRefreshing = accountViewModel.isRefreshing

    var showQrLoginDialog by remember { mutableStateOf(false) }
    var showManualLoginDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val copyToClipboard: (String, String) -> Unit = { text, label ->
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(
            context,
            context.getString(R.string.account_action_copied, label),
            Toast.LENGTH_SHORT
        ).show()
    }

    if (showQrLoginDialog) {
        VkQrLoginDialog(
            onDismissRequest = { showQrLoginDialog = false },
            onSuccess = { token, userId, remixsid, remixnsid, remixdsid, firstName, lastName, photoUrl, screenName ->
                if (!token.isNullOrBlank()) {
                    accountViewModel.loginWithToken(
                        token = token,
                        userId = userId,
                        remixsid = remixsid,
                        remixnsid = remixnsid,
                        remixdsid = remixdsid,
                        firstName = firstName,
                        lastName = lastName,
                        photoUrl = photoUrl,
                        screenName = screenName
                    )
                } else if (!remixsid.isNullOrBlank() || !remixnsid.isNullOrBlank()) {
                    accountViewModel.loginWithCookies(
                        remixsid = remixsid ?: "",
                        remixnsid = remixnsid,
                        remixdsid = remixdsid,
                        userId = userId
                    )
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.account_vk_login_success),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showManualLoginDialog) {
        var inputCookieOrToken by remember { mutableStateOf("") }
        var inputUserId by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualLoginDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_vk),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.account_vk_manual_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.account_vk_manual_dialog_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = inputCookieOrToken,
                        onValueChange = { inputCookieOrToken = it },
                        label = { Text(stringResource(R.string.account_vk_label_remixsid)) },
                        placeholder = { Text("remixsid value or vk1.a...") },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inputUserId,
                        onValueChange = { inputUserId = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.account_vk_label_user_id)) },
                        placeholder = { Text("123456789") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = inputCookieOrToken.trim()
                        val uId = inputUserId.toLongOrNull() ?: 0L
                        if (trimmed.isNotBlank() && uId != 0L) {
                            if (trimmed.startsWith("vk1.") || trimmed.length > 64) {
                                accountViewModel.loginWithToken(trimmed, uId)
                            } else {
                                accountViewModel.loginWithCookies(remixsid = trimmed, userId = uId)
                            }
                            showManualLoginDialog = false
                            Toast.makeText(
                                context,
                                context.getString(R.string.account_vk_login_success),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Please enter both token/cookie and numeric User ID",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.account_vk_manual_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualLoginDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    accountViewModel.diagnosticsReport?.let { report ->
        AlertDialog(
            onDismissRequest = { accountViewModel.dismissDiagnostics() },
            title = { Text(stringResource(R.string.account_vk_diagnose)) },
            text = {
                Text(
                    text = report,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(report))
                    Toast.makeText(context, R.string.account_vk_diagnose_copied, Toast.LENGTH_SHORT)
                        .show()
                }) { Text(stringResource(R.string.account_vk_diagnose_copy)) }
            },
            dismissButton = {
                TextButton(onClick = { accountViewModel.dismissDiagnostics() }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.account_vk_logout_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(text = stringResource(R.string.account_vk_logout_confirm_message))
            },
            confirmButton = {
                Button(
                    onClick = {
                        accountViewModel.logout()
                        try {
                            android.webkit.CookieManager.getInstance().apply {
                                removeAllCookies(null)
                                flush()
                            }
                            android.webkit.WebStorage.getInstance().deleteAllData()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        showLogoutDialog = false
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.account_vk_btn_logout))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.account_vk_details_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)
        ) {
            item {
                AnimatedContent(
                    targetState = isLoggedIn,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "VkAccountHeader"
                ) { loggedIn ->
                    if (loggedIn) {
                        VkConnectedHeader(
                            user = user,
                            onOpenProfile = {
                                val url = "https://vk.com/id${user?.id ?: accountViewModel.tokenManager.userId}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            }
                        )
                    } else {
                        VkGuestHeader(
                            onSignInQr = { showQrLoginDialog = true },
                            onSignInWebView = onNavigateToWebViewLogin,
                            onSignInManual = { showManualLoginDialog = true }
                        )
                    }
                }
            }

            if (isLoggedIn) {
                item {
                    VkStatsCard(
                        tracksCount = accountViewModel.tracksCount,
                        playlistsCount = accountViewModel.playlistsCount
                    )
                }

                item {
                    SettingsGroup(
                        title = stringResource(R.string.account_section_identity),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_display_name),
                                    subtitle = user?.fullName ?: "VKontakte User",
                                    icon = Icons.Rounded.Person,
                                    onClick = { copyToClipboard(user?.fullName ?: "", "Name") }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_user_id),
                                    subtitle = (user?.id ?: accountViewModel.tokenManager.userId).toString(),
                                    icon = Icons.Rounded.Badge,
                                    onClick = {
                                        copyToClipboard(
                                            (user?.id ?: accountViewModel.tokenManager.userId).toString(),
                                            "VK User ID"
                                        )
                                    }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_profile_url),
                                    subtitle = "https://vk.com/id${user?.id ?: accountViewModel.tokenManager.userId}",
                                    icon = Icons.Rounded.Link,
                                    onClick = {
                                        copyToClipboard(
                                            "https://vk.com/id${user?.id ?: accountViewModel.tokenManager.userId}",
                                            "Profile URL"
                                        )
                                    }
                                )
                            }
                        )
                    )
                }

                item {
                    SettingsGroup(
                        title = stringResource(R.string.account_vk_section_sync),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_vk_setting_enable_search),
                                    subtitle = if (accountViewModel.includeInSearch) "Active" else "Disabled",
                                    icon = Icons.Rounded.Search,
                                    hasSwitch = true,
                                    switchState = accountViewModel.includeInSearch,
                                    onSwitchChange = {
                                        accountViewModel.setIncludeInSearchSetting(it)
                                    }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_vk_setting_enable_recommendations),
                                    subtitle = if (accountViewModel.includeInRecommendations) "Active" else "Disabled",
                                    icon = Icons.Rounded.Recommend,
                                    hasSwitch = true,
                                    switchState = accountViewModel.includeInRecommendations,
                                    onSwitchChange = {
                                        accountViewModel.setIncludeInRecommendationsSetting(it)
                                    }
                                )
                            }
                        )
                    )
                }

                item {
                    SettingsGroup(
                        title = stringResource(R.string.account_section_actions),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_action_refresh),
                                    subtitle = if (isRefreshing) "Refreshing..." else "Reload library and profile data",
                                    icon = Icons.Rounded.Refresh,
                                    onClick = { accountViewModel.loadAccount(forceRefresh = true) }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_action_open_browser),
                                    subtitle = "vk.com/id${user?.id ?: accountViewModel.tokenManager.userId}",
                                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                    onClick = {
                                        val url = "https://vk.com/id${user?.id ?: accountViewModel.tokenManager.userId}"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        context.startActivity(intent)
                                    }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_vk_diagnose),
                                    subtitle = if (accountViewModel.isDiagnosing) {
                                        stringResource(R.string.account_vk_diagnose_running)
                                    } else {
                                        stringResource(R.string.account_vk_diagnose_subtitle)
                                    },
                                    icon = Icons.Rounded.NetworkCheck,
                                    onClick = { accountViewModel.runDiagnostics() }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_vk_btn_logout),
                                    subtitle = "Disconnect VKontakte from KittyTune",
                                    icon = Icons.AutoMirrored.Rounded.Logout,
                                    iconTint = MaterialTheme.colorScheme.error,
                                    iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    onClick = { showLogoutDialog = true }
                                )
                            }
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VkConnectedHeader(
    user: VkUser?,
    onOpenProfile: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
            ) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_vk),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "VKontakte",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.offset(y = (-36).dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        shadowElevation = 6.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(76.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        ) {
                            if (!user?.photoMax.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(user.photoMax)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = user.fullName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            } else {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = user?.fullName?.take(1)?.uppercase() ?: "V",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 28.dp)
                    ) {
                        Text(
                            text = user?.fullName ?: "VKontakte User",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (!user?.screenName.isNullOrBlank()) "@${user.screenName}" else "ID: ${user?.id ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (!user?.status.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = user.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-16).dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onOpenProfile,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.account_action_view_profile),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VkStatsCard(
    tracksCount: Int,
    playlistsCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.account_vk_section_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCounterBox(
                    modifier = Modifier.weight(1f),
                    count = tracksCount,
                    label = stringResource(R.string.account_vk_stat_tracks),
                    icon = Icons.AutoMirrored.Rounded.QueueMusic
                )
                StatCounterBox(
                    modifier = Modifier.weight(1f),
                    count = playlistsCount,
                    label = stringResource(R.string.account_vk_stat_playlists),
                    icon = Icons.Rounded.LibraryMusic
                )
            }
        }
    }
}

@Composable
private fun StatCounterBox(
    modifier: Modifier = Modifier,
    count: Int,
    label: String,
    icon: ImageVector
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (count > 0) count.toString() else "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VkGuestHeader(
    onSignInQr: () -> Unit,
    onSignInWebView: () -> Unit,
    onSignInManual: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_vk),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.account_vk_header_guest_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.account_vk_header_guest_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(24.dp))

            // Primary: QR Code VK ID
            Button(
                onClick = onSignInQr,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.account_vk_btn_login_qr),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Secondary: Webview
            FilledTonalButton(
                onClick = onSignInWebView,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_vk),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.account_vk_btn_login_webview),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Tertiary: Manual token / cookie
            OutlinedButton(
                onClick = onSignInManual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.account_vk_btn_login_manual),
                    fontWeight = FontWeight.Normal,
                    fontSize = 15.sp
                )
            }
        }
    }
}
