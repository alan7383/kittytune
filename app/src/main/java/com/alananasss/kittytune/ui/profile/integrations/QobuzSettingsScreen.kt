package com.alananasss.kittytune.ui.profile.integrations

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import kotlinx.coroutines.launch
import java.util.Locale
import com.alananasss.kittytune.ui.design.AlertDialog
import com.alananasss.kittytune.ui.design.Button
import com.alananasss.kittytune.ui.design.OutlinedButton
import com.alananasss.kittytune.ui.design.RadioButton
import com.alananasss.kittytune.ui.design.TextButton
import com.alananasss.kittytune.ui.design.TextField

@Composable
fun QobuzSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    var qobuzCountry by remember { mutableStateOf(prefs.getQobuzCountry()) }
    var qobuzCustomInstances by remember { mutableStateOf(prefs.getQobuzCustomInstances()) }
    var qobuzQuality by remember { mutableStateOf(prefs.getQobuzQuality()) }
    var qobuzToken by remember { mutableStateOf(prefs.getQobuzUserAuthToken()) }

    var showCountryDialog by remember { mutableStateOf(false) }
    var showInstancesDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showGoogleHelpDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var socialProvider by remember { mutableStateOf<QobuzSocialProvider?>(null) }
    var showGoogleConfig by remember { mutableStateOf(false) }
    var showFacebookConfig by remember { mutableStateOf(false) }

    // Kept out of the dialog block on purpose: a state declared inside a
    // conditional is forgotten the moment the condition flips, and the login
    // error would vanish with it.
    var accountEmail by remember { mutableStateOf("") }
    var accountPassword by remember { mutableStateOf("") }
    var accountLoading by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf<String?>(null) }
    val accountScope = rememberCoroutineScope()

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = { Text(stringResource(R.string.qobuz_account_login)) },
            text = {
                Column {
                    TextField(
                        value = accountEmail,
                        onValueChange = { accountEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = accountPassword,
                        onValueChange = { accountPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        TextButton(
                            onClick = hapticAction(HapticKind.UI) {
                                showAccountDialog = false
                                showGoogleHelpDialog = true
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.qobuz_forgot_or_google_account),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = hapticAction(HapticKind.UI) {
                                showAccountDialog = false
                                showTokenDialog = true
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.qobuz_enter_token),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (accountError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = accountError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = hapticAction(HapticKind.UI) {
                                accountError = null
                                showAccountDialog = false
                                onNavigateToLogin()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.qobuz_sign_in_google))
                        }
                        OutlinedButton(
                            onClick = hapticAction(HapticKind.UI) {
                                accountError = null
                                showAccountDialog = false
                                onNavigateToLogin()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.qobuz_web_login))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = hapticAction(HapticKind.UI) {
                        accountLoading = true
                        accountError = null
                        accountScope.launch {
                            val result = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
                                .login(context, accountEmail, accountPassword)
                            accountLoading = false
                            result.onSuccess { token ->
                                prefs.setQobuzUserAuthToken(token)
                                qobuzToken = token
                                showAccountDialog = false
                            }.onFailure { ex ->
                                accountError = ex.message?.takeIf { it.isNotBlank() }
                                    ?: context.getString(R.string.qobuz_account_login_failed)
                            }
                        }
                    },
                    enabled = !accountLoading && accountEmail.isNotBlank() && accountPassword.isNotBlank()
                ) {
                    if (accountLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) { showAccountDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showGoogleHelpDialog) {
        var helpEmail by remember(accountEmail) { mutableStateOf(accountEmail) }
        var resetStatus by remember { mutableStateOf<String?>(null) }
        var isResetting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showGoogleHelpDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.qobuz_google_help_title),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.qobuz_google_help_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextField(
                        value = helpEmail,
                        onValueChange = { helpEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (resetStatus != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = resetStatus!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = hapticAction(HapticKind.UI) {
                            if (helpEmail.isNotBlank()) {
                                isResetting = true
                                resetStatus = null
                                accountScope.launch {
                                    val success = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
                                        .resetPassword(helpEmail)
                                    isResetting = false
                                    if (success) {
                                        accountEmail = helpEmail
                                        resetStatus = context.getString(R.string.qobuz_reset_password_sent)
                                    } else {
                                        resetStatus = context.getString(R.string.qobuz_reset_password_failed)
                                    }
                                }
                            }
                        },
                        enabled = !isResetting && helpEmail.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isResetting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.qobuz_send_reset_password))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = hapticAction(HapticKind.UI) {
                            runCatching {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.qobuz.com/fr-fr/user/login")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.qobuz_open_website))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = hapticAction(HapticKind.UI) {
                        showGoogleHelpDialog = false
                        showAccountDialog = true
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) { showGoogleHelpDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showTokenDialog) {
        var tempToken by remember { mutableStateOf(qobuzToken.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.qobuz_token_login_title),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.qobuz_token_login_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = tempToken,
                        onValueChange = { tempToken = it },
                        label = { Text("user_auth_token") },
                        singleLine = false,
                        maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = hapticAction(HapticKind.UI) {
                        val cleaned = tempToken.trim()
                        if (cleaned.isNotBlank()) {
                            prefs.setQobuzUserAuthToken(cleaned)
                            qobuzToken = cleaned
                            Toast.makeText(context, R.string.qobuz_token_saved, Toast.LENGTH_SHORT).show()
                            showTokenDialog = false
                        }
                    },
                    enabled = tempToken.isNotBlank()
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) { showTokenDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showGoogleConfig) {
        var clientId by remember {
            mutableStateOf(
                prefs.getQobuzGoogleClientId().ifBlank { QobuzGoogleSignIn.DEFAULT_SERVER_CLIENT_ID }
            )
        }
        AlertDialog(
            onDismissRequest = { showGoogleConfig = false },
            title = { Text(stringResource(R.string.qobuz_google_config_title)) },
            text = {
                Column {
                    TextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text(stringResource(R.string.qobuz_google_client_id)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.qobuz_google_config_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) {
                    prefs.setQobuzGoogleClientId(clientId)
                    showGoogleConfig = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) { showGoogleConfig = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showFacebookConfig) {
        var clientId by remember { mutableStateOf(prefs.getQobuzFacebookClientId()) }
        var redirect by remember { mutableStateOf(prefs.getQobuzFacebookRedirect()) }
        AlertDialog(
            onDismissRequest = { showFacebookConfig = false },
            title = { Text(stringResource(R.string.qobuz_facebook_config_title)) },
            text = {
                Column {
                    TextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        label = { Text(stringResource(R.string.qobuz_facebook_client_id)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextField(
                        value = redirect,
                        onValueChange = { redirect = it },
                        label = { Text(stringResource(R.string.qobuz_facebook_redirect)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.qobuz_facebook_config_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) {
                    prefs.setQobuzFacebookClientId(clientId)
                    prefs.setQobuzFacebookRedirect(redirect)
                    showFacebookConfig = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) { showFacebookConfig = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text(stringResource(R.string.qobuz_account)) },
            text = { Text(stringResource(R.string.qobuz_account_desc_connected)) },
            confirmButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) {
                    prefs.setQobuzUserAuthToken(null)
                    qobuzToken = null
                    showDisconnectDialog = false
                }) {
                    Text(stringResource(R.string.qobuz_account_disconnect))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) { showDisconnectDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showCountryDialog) {
        var tempCountry by remember { mutableStateOf(qobuzCountry) }
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.qobuz_country),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = tempCountry,
                        onValueChange = { tempCountry = it.uppercase(Locale.US).take(2) },
                        placeholder = { Text("US") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.qobuz_country_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = hapticAction(HapticKind.UI) {
                        val finalCountry = tempCountry.trim().uppercase(Locale.US).ifBlank { "US" }
                        qobuzCountry = finalCountry
                        prefs.setQobuzCountry(finalCountry)
                        showCountryDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) { showCountryDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showInstancesDialog) {
        var tempInstances by remember { mutableStateOf(qobuzCustomInstances) }
        AlertDialog(
            onDismissRequest = { showInstancesDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.qobuz_custom_instances),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = tempInstances,
                        onValueChange = { tempInstances = it },
                        placeholder = { Text(stringResource(R.string.qobuz_custom_instances_placeholder)) },
                        singleLine = false,
                        maxLines = 8,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.qobuz_custom_instances_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = hapticAction(HapticKind.UI) {
                        val cleaned = tempInstances.trim()
                        qobuzCustomInstances = cleaned
                        prefs.setQobuzCustomInstances(cleaned)
                        showInstancesDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) { showInstancesDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.qobuz_audio_quality),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column {
                    listOf(
                        27 to R.string.qobuz_quality_hires_192,
                        7 to R.string.qobuz_quality_hires_96,
                        6 to R.string.qobuz_quality_cd,
                        5 to R.string.qobuz_quality_mp3_320
                    ).forEach { (code, labelRes) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .hapticClickable {
                                    qobuzQuality = code
                                    prefs.setQobuzQuality(code)
                                    showQualityDialog = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = qobuzQuality == code,
                                onClick = hapticAction(HapticKind.UI) {
                                    qobuzQuality = code
                                    prefs.setQobuzQuality(code)
                                    showQualityDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) { showQualityDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (socialProvider != null) {
        QobuzSocialLoginScreen(
            provider = socialProvider!!,
            onDismiss = { socialProvider = null },
            onError = { message ->
                accountError = message
                showAccountDialog = true
            },
            onToken = { social ->
                val provider = socialProvider
                accountScope.launch {
                    val token = when (provider) {
                        QobuzSocialProvider.FACEBOOK -> {
                            val userId =
                                com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
                                    .fetchFacebookUserId(social.token)
                            if (userId == null) {
                                null
                            } else {
                                com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
                                    .loginWithFacebook(
                                        context,
                                        accessToken = social.token,
                                        userId = userId,
                                        expiresAt = social.expiresAt,
                                    )
                            }
                        }
                        null -> null
                    }
                    if (token != null) {
                        prefs.setQobuzUserAuthToken(token)
                        qobuzToken = token
                        socialProvider = null
                    } else {
                        accountError = context.getString(R.string.qobuz_account_login_failed)
                        showAccountDialog = true
                        socialProvider = null
                    }
                }
            },
        )
        return
    }

    SettingsScaffold(
        title = stringResource(R.string.qobuz_integration),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)
        ) {
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_general),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_country),
                                subtitle = stringResource(R.string.qobuz_country_desc),
                                icon = Icons.Rounded.Language,
                                onClick = hapticAction(HapticKind.UI) { showCountryDialog = true }
                            )
                        },
                        { shape ->
                            val instances = qobuzCustomInstances.split("\n").filter { it.isNotBlank() }
                            val desc = if (instances.isEmpty() || qobuzCustomInstances == QobuzAudioProvider.DEFAULT_INSTANCE) {
                                stringResource(R.string.qobuz_custom_instances_desc_default)
                            } else {
                                stringResource(R.string.qobuz_custom_instances_desc_custom, instances.size)
                            }
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_custom_instances),
                                subtitle = desc,
                                icon = Icons.Rounded.Link,
                                onClick = hapticAction(HapticKind.UI) { showInstancesDialog = true }
                            )
                        },
                        { shape ->
                            val qualityLabel = when (qobuzQuality) {
                                27 -> stringResource(R.string.qobuz_quality_hires_192)
                                7 -> stringResource(R.string.qobuz_quality_hires_96)
                                6 -> stringResource(R.string.qobuz_quality_cd)
                                else -> stringResource(R.string.qobuz_quality_mp3_320)
                            }
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_audio_quality),
                                subtitle = qualityLabel,
                                icon = Icons.Rounded.GraphicEq,
                                onClick = hapticAction(HapticKind.UI) { showQualityDialog = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_account),
                                subtitle = if (qobuzToken != null)
                                    stringResource(R.string.qobuz_account_desc_connected)
                                else
                                    stringResource(R.string.qobuz_account_desc_disconnected),
                                icon = Icons.Rounded.Person,
                                onClick = hapticAction(HapticKind.UI) {
                                    if (qobuzToken != null) showDisconnectDialog = true else showAccountDialog = true
                                }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_web_login),
                                subtitle = stringResource(R.string.qobuz_web_login_desc),
                                icon = Icons.AutoMirrored.Rounded.Login,
                                onClick = onNavigateToLogin
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_token_login_title),
                                subtitle = stringResource(R.string.qobuz_token_login_desc),
                                icon = Icons.Rounded.Key,
                                onClick = hapticAction(HapticKind.UI) { showTokenDialog = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_google_config_title),
                                subtitle = stringResource(R.string.qobuz_google_config_desc),
                                icon = Icons.Rounded.Link,
                                onClick = hapticAction(HapticKind.UI) { showGoogleConfig = true }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.qobuz_facebook_config_title),
                                subtitle = stringResource(R.string.qobuz_facebook_config_desc),
                                icon = Icons.Rounded.Link,
                                onClick = hapticAction(HapticKind.UI) { showFacebookConfig = true }
                            )
                        }
                    )
                )
            }
        }
    }
}
