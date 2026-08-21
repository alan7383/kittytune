package com.alananasss.kittytune.ui.profile

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.ProxyConfig
import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.data.network.ProxyProfile
import com.alananasss.kittytune.data.network.ProxyProtocol
import com.alananasss.kittytune.data.network.ProxyTestResult
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProxySettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PlayerPreferences(context) }

    var isEnabled by remember { mutableStateOf(prefs.getProxyEnabled()) }
    var proxyType by remember { mutableStateOf(prefs.getProxyType()) }
    var host by remember { mutableStateOf(prefs.getProxyHost()) }
    var portText by remember { mutableStateOf(prefs.getProxyPort().toString()) }
    var authEnabled by remember { mutableStateOf(prefs.getProxyAuthEnabled()) }
    var username by remember { mutableStateOf(prefs.getProxyUsername()) }
    var password by remember { mutableStateOf(prefs.getProxyPassword()) }
    var showPassword by remember { mutableStateOf(false) }

    var savedProfiles by remember { mutableStateOf(prefs.getSavedProxyProfiles()) }
    var selectedProfileId by remember { mutableStateOf(prefs.getSelectedProxyProfileId()) }
    val deletingProfileIds = remember { mutableStateListOf<String>() }

    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var profileNameInput by remember { mutableStateOf("") }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<ProxyTestResult?>(null) }

    fun saveAndApply(
        newEnabled: Boolean = isEnabled,
        newType: String = proxyType,
        newHost: String = host,
        newPortText: String = portText,
        newAuthEnabled: Boolean = authEnabled,
        newUsername: String = username,
        newPassword: String = password,
        newProfileId: String? = selectedProfileId
    ) {
        val parsedPort = newPortText.toIntOrNull()?.coerceIn(1, 65535) ?: 8080
        prefs.setProxyEnabled(newEnabled)
        prefs.setProxyType(newType)
        prefs.setProxyHost(newHost)
        prefs.setProxyPort(parsedPort)
        prefs.setProxyAuthEnabled(newAuthEnabled)
        prefs.setProxyUsername(newUsername)
        prefs.setProxyPassword(newPassword)
        prefs.setSelectedProxyProfileId(newProfileId)

        ProxyManager.applyConfiguration(context)
    }

    fun applyConfig(config: ProxyConfig, profileId: String? = null) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        isEnabled = config.enabled
        proxyType = if (config.protocol == ProxyProtocol.SOCKS) "SOCKS" else "HTTP"
        host = config.host
        portText = config.port.toString()
        authEnabled = config.authEnabled
        username = config.username
        password = config.password
        selectedProfileId = profileId
        testResult = null

        saveAndApply(
            newEnabled = config.enabled,
            newType = proxyType,
            newHost = config.host,
            newPortText = config.port.toString(),
            newAuthEnabled = config.authEnabled,
            newUsername = config.username,
            newPassword = config.password,
            newProfileId = profileId
        )
    }

    if (showSaveProfileDialog) {
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.proxy_profile_save_as)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.proxy_profile_name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = profileNameInput,
                        onValueChange = { profileNameInput = it },
                        placeholder = { Text(stringResource(R.string.proxy_profile_name_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = profileNameInput.trim().ifBlank {
                            "${if (proxyType == "SOCKS") "SOCKS5" else "HTTP"} ${host.ifBlank { "Proxy" }}:${portText}"
                        }
                        val parsedPort = portText.toIntOrNull()?.coerceIn(1, 65535) ?: 8080
                        val newProfile = ProxyProfile(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            config = ProxyConfig(
                                enabled = true,
                                protocol = if (proxyType.equals("SOCKS", ignoreCase = true)) ProxyProtocol.SOCKS else ProxyProtocol.HTTP,
                                host = host.trim(),
                                port = parsedPort,
                                authEnabled = authEnabled,
                                username = username.trim(),
                                password = password
                            )
                        )
                        prefs.addOrUpdateProxyProfile(newProfile)
                        savedProfiles = prefs.getSavedProxyProfiles()
                        selectedProfileId = newProfile.id
                        prefs.setSelectedProxyProfileId(newProfile.id)
                        showSaveProfileDialog = false
                        Toast.makeText(context, context.getString(R.string.proxy_profile_saved_toast), Toast.LENGTH_SHORT).show()
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSaveProfileDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.proxy_settings_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(top = innerPadding.calculateTopPadding())
                .fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = innerPadding.calculateBottomPadding() + 48.dp,
                top = 16.dp,
                start = 0.dp,
                end = 0.dp
            )
        ) {
            // Info Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnLock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.proxy_info_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.proxy_info_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Master Switch
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_general),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.proxy_enable),
                                subtitle = if (isEnabled) {
                                    val port = portText.toIntOrNull() ?: 8080
                                    stringResource(R.string.proxy_status_enabled, proxyType, host.ifBlank { "127.0.0.1" }, port)
                                } else {
                                    stringResource(R.string.proxy_enable_desc)
                                },
                                icon = Icons.Rounded.Dns,
                                hasSwitch = true,
                                switchState = isEnabled,
                                onSwitchChange = { enabled ->
                                    isEnabled = enabled
                                    saveAndApply(newEnabled = enabled)
                                }
                            )
                        }
                    )
                )
            }

            // Detailed Proxy Options
            item {
                AnimatedVisibility(
                    visible = isEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Clipboard Quick Import Action
                        FilledTonalButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text?.trim()
                                if (!clipText.isNullOrBlank()) {
                                    val parsed = ProxyManager.parseProxyUri(clipText)
                                    if (parsed != null) {
                                        applyConfig(parsed)
                                        Toast.makeText(context, context.getString(R.string.proxy_import_success), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.proxy_import_error), Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, context.getString(R.string.proxy_import_error), Toast.LENGTH_SHORT).show()
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Rounded.ContentPasteGo, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.proxy_import_clipboard),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Saved Profiles List (Material You Pixel Design)
                        val visibleProfiles = savedProfiles.filter { it.id !in deletingProfileIds }
                        AnimatedVisibility(
                            visible = savedProfiles.isNotEmpty() && visibleProfiles.isNotEmpty(),
                            enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(250)),
                            exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.proxy_profiles_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${visibleProfiles.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                savedProfiles.forEachIndexed { index, profile ->
                                    val isSelected = selectedProfileId == profile.id ||
                                            (host == profile.config.host && portText == profile.config.port.toString())

                                    val itemShape = getSettingsShape(savedProfiles.size, index)
                                    val containerColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        animationSpec = tween(250),
                                        label = "profile_bg"
                                    )
                                    val contentColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                        animationSpec = tween(250),
                                        label = "profile_content"
                                    )

                                    AnimatedVisibility(
                                        visible = profile.id !in deletingProfileIds,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically(animationSpec = tween(280)) + fadeOut(animationSpec = tween(200))
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(itemShape)
                                                .clickable {
                                                    applyConfig(profile.config, profile.id)
                                                },
                                            shape = itemShape,
                                            color = containerColor
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Dns,
                                                            contentDescription = null,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = profile.name,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        color = contentColor,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${if (profile.config.protocol == ProxyProtocol.SOCKS) "SOCKS5" else "HTTP"} • ${profile.config.host}:${profile.config.port}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (isSelected) contentColor.copy(alpha = 0.8f)
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                if (isSelected) {
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.proxy_profile_active),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = {
                                                        scope.launch {
                                                            deletingProfileIds.add(profile.id)
                                                            kotlinx.coroutines.delay(280)
                                                            prefs.deleteProxyProfile(profile.id)
                                                            savedProfiles = prefs.getSavedProxyProfiles()
                                                            deletingProfileIds.remove(profile.id)
                                                            if (selectedProfileId == profile.id) {
                                                                selectedProfileId = null
                                                            }
                                                        }
                                                    },
                                                    shapes = IconButtonDefaults.shapes(),
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.DeleteOutline,
                                                        contentDescription = stringResource(R.string.proxy_profile_delete),
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        SettingsGroupTitle(stringResource(R.string.proxy_settings_title))

                        // Proxy Type Selector with Expressive Connected ButtonGroup
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.proxy_type),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                val proxyOptions = listOf("HTTP", "SOCKS")
                                ExpressiveConnectedButtonGroup(
                                    options = proxyOptions,
                                    selectedOption = if (proxyType.equals("SOCKS", ignoreCase = true)) "SOCKS" else "HTTP",
                                    onOptionSelected = { selected ->
                                        proxyType = selected
                                        selectedProfileId = null
                                        saveAndApply(newType = selected, newProfileId = null)
                                    },
                                    labelProvider = { option ->
                                        Text(
                                            text = if (option == "HTTP") stringResource(R.string.proxy_type_http)
                                            else stringResource(R.string.proxy_type_socks),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    iconProvider = { option ->
                                        Icon(
                                            imageVector = if (option == "HTTP") Icons.Rounded.Language else Icons.Rounded.Security,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }

                        // Host and Port Configuration Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = {
                                        host = it
                                        testResult = null
                                        selectedProfileId = null
                                        saveAndApply(newHost = it, newProfileId = null)
                                    },
                                    label = { Text(stringResource(R.string.proxy_host)) },
                                    placeholder = { Text(stringResource(R.string.proxy_host_hint)) },
                                    singleLine = true,
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Language, contentDescription = null)
                                    },
                                    trailingIcon = {
                                        if (host.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    host = ""
                                                    selectedProfileId = null
                                                    saveAndApply(newHost = "", newProfileId = null)
                                                },
                                                shapes = IconButtonDefaults.shapes()
                                            ) {
                                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = portText,
                                    onValueChange = {
                                        if (it.length <= 5 && it.all { char -> char.isDigit() }) {
                                            portText = it
                                            testResult = null
                                            selectedProfileId = null
                                            saveAndApply(newPortText = it, newProfileId = null)
                                        }
                                    },
                                    label = { Text(stringResource(R.string.proxy_port)) },
                                    placeholder = { Text(stringResource(R.string.proxy_port_hint)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Numbers, contentDescription = null)
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Quick Presets
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.proxy_presets_header),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PresetChip(
                                            label = stringResource(R.string.proxy_preset_v2ray),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                host = "127.0.0.1"
                                                portText = "10808"
                                                proxyType = "SOCKS"
                                                testResult = null
                                                selectedProfileId = null
                                                saveAndApply(newHost = "127.0.0.1", newPortText = "10808", newType = "SOCKS", newProfileId = null)
                                            }
                                        )
                                        PresetChip(
                                            label = stringResource(R.string.proxy_preset_tor),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                host = "127.0.0.1"
                                                portText = "9050"
                                                proxyType = "SOCKS"
                                                testResult = null
                                                selectedProfileId = null
                                                saveAndApply(newHost = "127.0.0.1", newPortText = "9050", newType = "SOCKS", newProfileId = null)
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PresetChip(
                                            label = stringResource(R.string.proxy_preset_http),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                host = "127.0.0.1"
                                                portText = "8080"
                                                proxyType = "HTTP"
                                                testResult = null
                                                selectedProfileId = null
                                                saveAndApply(newHost = "127.0.0.1", newPortText = "8080", newType = "HTTP", newProfileId = null)
                                            }
                                        )
                                        PresetChip(
                                            label = stringResource(R.string.proxy_preset_socks),
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                host = "127.0.0.1"
                                                portText = "1080"
                                                proxyType = "SOCKS"
                                                testResult = null
                                                selectedProfileId = null
                                                saveAndApply(newHost = "127.0.0.1", newPortText = "1080", newType = "SOCKS", newProfileId = null)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Authentication Section
                        SettingsItem(
                            shape = RoundedCornerShape(24.dp),
                            title = stringResource(R.string.proxy_auth_enable),
                            subtitle = stringResource(R.string.proxy_auth_enable_desc),
                            icon = Icons.Rounded.Key,
                            hasSwitch = true,
                            switchState = authEnabled,
                            onSwitchChange = { enabled ->
                                authEnabled = enabled
                                saveAndApply(newAuthEnabled = enabled)
                            }
                        )

                        AnimatedVisibility(
                            visible = authEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = {
                                            username = it
                                            saveAndApply(newUsername = it)
                                        },
                                        label = { Text(stringResource(R.string.proxy_username)) },
                                        singleLine = true,
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Person, contentDescription = null)
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = {
                                            password = it
                                            saveAndApply(newPassword = it)
                                        },
                                        label = { Text(stringResource(R.string.proxy_password)) },
                                        singleLine = true,
                                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                        leadingIcon = {
                                            Icon(Icons.Rounded.Lock, contentDescription = null)
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { showPassword = !showPassword },
                                                shapes = IconButtonDefaults.shapes()
                                            ) {
                                                Icon(
                                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                                    contentDescription = "Toggle password"
                                                )
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Save as Profile and Test Action Buttons (Using expressive shapes)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (host.isBlank()) {
                                        Toast.makeText(context, context.getString(R.string.proxy_host_empty), Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    profileNameInput = "${if (proxyType == "SOCKS") "SOCKS5" else "HTTP"} ${host}:${portText}"
                                    showSaveProfileDialog = true
                                },
                                enabled = host.isNotBlank(),
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.proxy_profile_save_as),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }

                            Button(
                                onClick = {
                                    if (host.isBlank()) {
                                        Toast.makeText(context, context.getString(R.string.proxy_host_empty), Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val port = portText.toIntOrNull()
                                    if (port == null || port !in 1..65535) {
                                        Toast.makeText(context, context.getString(R.string.proxy_port_invalid), Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    isTesting = true
                                    testResult = null
                                    val testConfig = ProxyConfig(
                                        enabled = true,
                                        protocol = if (proxyType.equals("SOCKS", ignoreCase = true)) ProxyProtocol.SOCKS else ProxyProtocol.HTTP,
                                        host = host.trim(),
                                        port = port,
                                        authEnabled = authEnabled,
                                        username = username.trim(),
                                        password = password
                                    )

                                    scope.launch {
                                        testResult = ProxyManager.testProxyConnection(testConfig)
                                        isTesting = false
                                    }
                                },
                                enabled = !isTesting && host.isNotBlank(),
                                shapes = ButtonDefaults.shapes(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.proxy_testing),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                } else {
                                    Icon(
                                        Icons.Rounded.NetworkCheck,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.proxy_test_connection),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Test Result Display (Green Banner with Checkmark)
                        AnimatedVisibility(
                            visible = testResult != null,
                            enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            testResult?.let { result ->
                                when (result) {
                                    is ProxyTestResult.Success -> {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color(0xFF2E7D32).copy(alpha = 0.22f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF4CAF50),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.proxy_test_success, result.pingMs),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                    is ProxyTestResult.Error -> {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = stringResource(R.string.proxy_test_failed, result.message),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
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
    }
}

@Composable
private fun PresetChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = modifier.height(38.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
