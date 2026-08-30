package com.alananasss.kittytune.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Send
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
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoundCloudAccountSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToProfile: (Long) -> Unit,
    accountViewModel: SoundCloudAccountViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val user = accountViewModel.user
    val isGuest = accountViewModel.isGuest
    val isLoading = accountViewModel.isLoading
    val isRefreshing = accountViewModel.isRefreshing

    var showLogoutDialog by remember { mutableStateOf(false) }
    var selectedEmailForActions by remember { mutableStateOf<com.alananasss.kittytune.domain.MeEmail?>(null) }

    val copyToClipboard: (String, String) -> Unit = { text, label ->
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(
            context,
            context.getString(R.string.account_action_copied, label),
            Toast.LENGTH_SHORT
        ).show()
    }

    if (selectedEmailForActions != null) {
        val emailItem = selectedEmailForActions!!
        AlertDialog(
            onDismissRequest = { selectedEmailForActions = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = emailItem.address,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedEmailForActions = null
                            copyToClipboard(emailItem.address, context.getString(R.string.account_section_emails))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.account_action_copy))
                    }

                    if (!emailItem.isConfirmed) {
                        Button(
                            onClick = {
                                selectedEmailForActions = null
                                accountViewModel.resendConfirmationEmail { success, errorMsg ->
                                    if (success) {
                                        Toast.makeText(context, context.getString(R.string.account_email_confirm_sent), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, errorMsg ?: "Error", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.account_email_resend_confirm))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedEmailForActions = null }) {
                    Text(stringResource(R.string.btn_cancel))
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
                    text = stringResource(R.string.account_logout_confirm_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.account_logout_confirm_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        accountViewModel.logout {
                            onBackClick()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.account_btn_logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.account_details_title),
        onBackClick = onBackClick,
        actions = {
            if (!isGuest) {
                IconButton(
                    onClick = { accountViewModel.loadAccount(forceRefresh = true) },
                    enabled = !isLoading && !isRefreshing
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.account_action_refresh)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp)
        ) {
            if (isGuest) {
                item {
                    SoundCloudGuestCard(onLoginClick = onNavigateToLogin)
                }
            } else if (isLoading && user == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.account_info_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (user != null) {
                val config = accountViewModel.configuration

                item {
                    SoundCloudUserHeroCard(
                        user = user,
                        configuration = config,
                        onViewProfile = { onNavigateToProfile(user.id) }
                    )
                }

                item {
                    val creatorPlan = when {
                        config?.creatorPlan?.isActivePlan == true -> config.creatorPlan.planName ?: "Artist Pro"
                        user.isProUser -> user.creatorPlanTitle ?: "Artist Pro"
                        else -> null
                    }

                    val consumerPlan = when {
                        config?.consumerPlan?.isActivePlan == true -> config.consumerPlan.planName ?: "SoundCloud Go"
                        user.consumerSubscription?.product?.id != null && user.consumerSubscription.product.id != "free" -> user.consumerPlanTitle
                            ?: "SoundCloud Go"

                        else -> null
                    }

                    val hasAnyActivePlan = creatorPlan != null || consumerPlan != null

                    val subscriptionItems = mutableListOf<@Composable (androidx.compose.ui.graphics.Shape) -> Unit>()

                    if (creatorPlan != null) {
                        subscriptionItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = creatorPlan,
                                subtitle = stringResource(R.string.account_label_creator_sub),
                                icon = Icons.Rounded.WorkspacePremium,
                                trailingText = stringResource(R.string.account_status_active)
                            )
                        }
                    }

                    if (consumerPlan != null) {
                        subscriptionItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = consumerPlan,
                                subtitle = stringResource(R.string.account_label_consumer_sub),
                                icon = Icons.Rounded.Headphones,
                                trailingText = stringResource(R.string.account_status_active)
                            )
                        }
                    }

                    if (!hasAnyActivePlan) {
                        subscriptionItems.add { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.account_plan_creator_free),
                                subtitle = stringResource(R.string.account_no_active_subscriptions),
                                icon = Icons.Rounded.MusicNote
                            )
                        }
                    }

                    subscriptionItems.add { shape ->
                        val quota = user.quota
                        val quotaSubtitle = when {
                            quota?.unlimitedUploadQuota == true || quota?.unlimitedUploadDurationQuota == true || user.isProUser -> stringResource(
                                R.string.account_quota_unlimited
                            )

                            quota?.uploadSecondsLeft != null -> {
                                val minutes = quota.uploadSecondsLeft / 60
                                val hours = minutes / 60
                                if (hours > 0) {
                                    stringResource(
                                        R.string.account_quota_remaining,
                                        "${hours}h ${minutes % 60}m"
                                    )
                                } else {
                                    stringResource(R.string.account_quota_remaining, "${minutes}m")
                                }
                            }

                            else -> stringResource(R.string.account_quota_unlimited)
                        }
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.account_stat_quota),
                            subtitle = quotaSubtitle,
                            icon = Icons.Rounded.CloudUpload
                        )
                    }

                    subscriptionItems.add { shape ->
                        val verifiedText = if (user.isVerifiedUser) {
                            stringResource(R.string.user_type_verified_artist)
                        } else {
                            stringResource(R.string.user_type_profile)
                        }
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.account_label_verified),
                            subtitle = verifiedText,
                            icon = if (user.isVerifiedUser) Icons.Rounded.Verified else Icons.Rounded.Shield
                        )
                    }

                    subscriptionItems.add { shape ->
                        SettingsItem(
                            shape = shape,
                            title = stringResource(R.string.account_btn_manage_subscription),
                            subtitle = stringResource(R.string.account_section_subscriptions),
                            icon = Icons.Rounded.CreditCard,
                            onClick = {
                                val manageUrl =
                                    if (config?.consumerPlan?.vendor == "google_play" || config?.creatorPlan?.vendor == "google_play") {
                                        "https://play.google.com/store/account/subscriptions"
                                    } else {
                                        "https://soundcloud.com/you/subscriptions"
                                    }
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(manageUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    SettingsGroup(
                        title = stringResource(R.string.account_active_subscriptions_title),
                        items = subscriptionItems
                    )
                }

                // Activity & Library Statistics Grid
                item {
                    SoundCloudStatsCard(user = user)
                }

                // Email Addresses Section
                val emails = accountViewModel.emails
                val activeEmailList = if (emails.isNotEmpty()) emails else {
                    val primary = user.email
                    if (!primary.isNullOrBlank()) {
                        listOf(
                            com.alananasss.kittytune.domain.MeEmail(
                                address = primary,
                                isPrimary = true,
                                isConfirmed = user.primaryEmailConfirmed ?: false
                            )
                        )
                    } else emptyList()
                }

                if (activeEmailList.isNotEmpty()) {
                    item {
                        SettingsGroup(
                            title = stringResource(R.string.account_section_emails),
                            items = activeEmailList.map { emailItem ->
                                { shape ->
                                    val tag = when {
                                        emailItem.isPrimary -> stringResource(R.string.account_email_primary_tag)
                                        emailItem.isConfirmed -> stringResource(R.string.account_email_confirmed)
                                        else -> stringResource(R.string.account_email_unconfirmed)
                                    }
                                    val subtitleText = if (emailItem.isPrimary) {
                                        stringResource(R.string.account_email_primary_tag)
                                    } else {
                                        stringResource(R.string.account_section_emails)
                                    }

                                    SettingsItem(
                                        shape = shape,
                                        title = emailItem.address,
                                        subtitle = subtitleText,
                                        icon = if (emailItem.isPrimary) Icons.Rounded.MarkEmailRead else Icons.Rounded.Email,
                                        trailingText = tag,
                                        onClick = {
                                            if (!emailItem.isConfirmed) {
                                                selectedEmailForActions = emailItem
                                            } else {
                                                copyToClipboard(
                                                    emailItem.address,
                                                    context.getString(R.string.account_section_emails)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }
                }



                // Identity & Personal Credentials
                item {
                    SettingsGroup(
                        title = stringResource(R.string.account_section_identity),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_username),
                                    subtitle = user.username.orEmpty(),
                                    icon = Icons.Rounded.Person,
                                    onClick = {
                                        user.username?.let {
                                            copyToClipboard(
                                                it,
                                                context.getString(R.string.account_label_username)
                                            )
                                        }
                                    }
                                )
                            },
                            { shape ->
                                val displayName = user.effectiveDisplayName
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_display_name),
                                    subtitle = displayName,
                                    icon = Icons.Rounded.Badge,
                                    onClick = {
                                        copyToClipboard(
                                            displayName,
                                            context.getString(R.string.account_label_display_name)
                                        )
                                    }
                                )
                            },
                            { shape ->
                                val dob = user.dateOfBirth
                                val dobDisplay = if (dob?.year != null) {
                                    "${dob.day ?: 1}/${dob.month ?: 1}/${dob.year}"
                                } else {
                                    stringResource(R.string.account_label_location_empty)
                                }
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_dob),
                                    subtitle = dobDisplay,
                                    icon = Icons.Rounded.Cake,
                                    onClick = null
                                )
                            },
                            { shape ->
                                val genderDisplay = when (user.gender?.lowercase()) {
                                    "male" -> stringResource(R.string.account_gender_male)
                                    "female" -> stringResource(R.string.account_gender_female)
                                    "other", "custom" -> stringResource(R.string.account_gender_other)
                                    else -> stringResource(R.string.account_gender_unspecified)
                                }
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_gender),
                                    subtitle = genderDisplay,
                                    icon = Icons.Rounded.Wc,
                                    onClick = null
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_user_id),
                                    subtitle = user.id.toString(),
                                    icon = Icons.Rounded.Tag,
                                    onClick = {
                                        copyToClipboard(
                                            user.id.toString(),
                                            context.getString(R.string.account_label_user_id)
                                        )
                                    }
                                )
                            },
                            { shape ->
                                val urn = user.urn ?: "soundcloud:users:${user.id}"
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_user_urn),
                                    subtitle = urn,
                                    icon = Icons.Rounded.Fingerprint,
                                    onClick = {
                                        copyToClipboard(
                                            urn,
                                            context.getString(R.string.account_label_user_urn)
                                        )
                                    }
                                )
                            },
                            { shape ->
                                val permalink = user.permalink ?: user.username.orEmpty()
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_permalink),
                                    subtitle = "@$permalink",
                                    icon = Icons.Rounded.AlternateEmail,
                                    onClick = {
                                        copyToClipboard(
                                            permalink,
                                            context.getString(R.string.account_label_permalink)
                                        )
                                    }
                                )
                            },
                            { shape ->
                                val profileUrl =
                                    user.permalinkUrl ?: "https://soundcloud.com/${user.permalink ?: user.id}"
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_profile_url),
                                    subtitle = profileUrl,
                                    icon = Icons.Rounded.Link,
                                    onClick = {
                                        copyToClipboard(
                                            profileUrl,
                                            context.getString(R.string.account_label_profile_url)
                                        )
                                    }
                                )
                            },
                            { shape ->
                                val locationParts = listOfNotNull(
                                    user.city?.takeIf { it.isNotBlank() },
                                    user.country?.takeIf { it.isNotBlank() }
                                        ?: user.countryCode?.takeIf { it.isNotBlank() }
                                )
                                val location =
                                    if (locationParts.isNotEmpty()) locationParts.joinToString(", ") else stringResource(
                                        R.string.account_label_location_empty
                                    )
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_location),
                                    subtitle = location,
                                    icon = Icons.Rounded.LocationOn,
                                    onClick = null
                                )
                            },
                            { shape ->
                                val bio = user.description?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.account_label_bio_empty)
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_bio),
                                    subtitle = bio,
                                    icon = Icons.Rounded.Description,
                                    onClick = if (!user.description.isNullOrBlank()) {
                                        {
                                            copyToClipboard(
                                                user.description,
                                                context.getString(R.string.account_label_bio)
                                            )
                                        }
                                    } else null
                                )
                            }
                        )
                    )
                }

                // Creator & Content Details
                item {
                    SettingsGroup(
                        title = stringResource(R.string.account_section_creator_settings),
                        items = listOf(
                            { shape ->
                                val licenseDisplay = when (user.defaultLicense) {
                                    "all-rights-reserved" -> stringResource(R.string.account_license_all_rights)
                                    "cc-by", "cc-by-nc", "cc-by-nc-nd", "cc-by-nc-sa", "cc-by-nd", "cc-by-sa" -> stringResource(
                                        R.string.account_license_cc
                                    )

                                    else -> user.defaultLicense?.replace("-", " ")?.replaceFirstChar { it.uppercase() }
                                        ?: stringResource(R.string.account_license_all_rights)
                                }
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_default_license),
                                    subtitle = licenseDisplay,
                                    icon = Icons.Rounded.Gavel
                                )
                            },
                            { shape ->
                                val spotlightText = if (user.spotlightLimit > 0) {
                                    stringResource(R.string.account_label_spotlight_desc, user.spotlightLimit)
                                } else {
                                    stringResource(R.string.account_label_location_empty)
                                }
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_spotlight_limit),
                                    subtitle = spotlightText,
                                    icon = Icons.Rounded.Star
                                )
                            },
                            { shape ->
                                val station = user.stationPermalink ?: user.stationUrn ?: "artist-stations:${user.id}"
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_label_station),
                                    subtitle = station,
                                    icon = Icons.Rounded.Radio,
                                    onClick = {
                                        copyToClipboard(
                                            station,
                                            context.getString(R.string.account_label_station)
                                        )
                                    }
                                )
                            }
                        )
                    )
                }

                // Account Actions
                item {
                    SettingsGroup(
                        title = stringResource(R.string.account_section_actions),
                        items = listOf(
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_action_view_profile),
                                    subtitle = stringResource(R.string.pref_about_subtitle),
                                    icon = Icons.Rounded.AccountCircle,
                                    onClick = { onNavigateToProfile(user.id) }
                                )
                            },
                            { shape ->
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.account_btn_logout),
                                    subtitle = stringResource(R.string.pref_account_soundcloud_title),
                                    icon = Icons.AutoMirrored.Rounded.Logout,
                                    iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    iconTint = MaterialTheme.colorScheme.error,
                                    titleColor = MaterialTheme.colorScheme.error,
                                    onClick = { showLogoutDialog = true }
                                )
                            }
                        )
                    )
                }
            } else if (accountViewModel.errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.account_info_error),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = accountViewModel.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { accountViewModel.loadAccount(forceRefresh = true) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.account_action_refresh))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCloudUserHeroCard(
    user: User,
    configuration: com.alananasss.kittytune.domain.SoundCloudConfigurationResponse? = null,
    onViewProfile: () -> Unit
) {
    val highResAvatar = user.avatarUrl?.replace("large", "t500x500")
    val bannerUrl = user.bannerUrl

    val formattedDate = remember(user.createdAt) {
        if (!user.createdAt.isNullOrBlank()) {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                val date = inputFormat.parse(user.createdAt)
                if (date != null) {
                    val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                    outputFormat.format(date)
                } else null
            } catch (e: Exception) {
                user.createdAt.take(10)
            }
        } else null
    }

    val badgeLabel = when {
        configuration?.creatorPlan?.isActivePlan == true -> configuration.creatorPlan.planName ?: "Artist Pro"
        configuration?.consumerPlan?.isActivePlan == true -> configuration.consumerPlan.planName ?: "SoundCloud Go"
        user.creatorPlanTitle != null -> user.creatorPlanTitle
        user.isProUser -> "Artist Pro"
        user.consumerPlanTitle != null -> user.consumerPlanTitle
        else -> "FREE"
    }

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
                    .height(120.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        )
                    )
            ) {
                if (!bannerUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(bannerUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f))
                                )
                            )
                    )
                }

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
                            painter = painterResource(id = R.drawable.ic_soundcloud),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = badgeLabel ?: "FREE",
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
                            if (!highResAvatar.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(highResAvatar)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = user.username,
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
                                            text = user.username?.take(1)?.uppercase() ?: "U",
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.effectiveDisplayName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (user.isVerifiedUser) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Rounded.Verified,
                                    contentDescription = stringResource(R.string.account_label_verified),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = "@${user.permalink ?: user.username.orEmpty()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (formattedDate != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.offset(y = (-20).dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.profile_member_since, formattedDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCloudStatsCard(user: User) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.account_section_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCounterBox(
                    modifier = Modifier.weight(1f),
                    count = user.trackCount,
                    label = stringResource(R.string.account_stat_tracks),
                    icon = Icons.Rounded.MusicNote
                )
                Spacer(Modifier.width(8.dp))
                StatCounterBox(
                    modifier = Modifier.weight(1f),
                    count = user.playlistCount,
                    label = stringResource(R.string.account_stat_playlists),
                    icon = Icons.AutoMirrored.Rounded.QueueMusic
                )
                Spacer(Modifier.width(8.dp))
                StatCounterBox(
                    modifier = Modifier.weight(1f),
                    count = user.likesCount,
                    label = stringResource(R.string.account_stat_likes),
                    icon = Icons.Rounded.Favorite
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatCounterBox(
                    modifier = Modifier.weight(1f),
                    count = user.followersCount,
                    label = stringResource(R.string.account_stat_followers),
                    icon = Icons.Rounded.Group
                )
                Spacer(Modifier.width(8.dp))
                StatCounterBox(
                    modifier = Modifier.weight(1f),
                    count = user.followingsCount,
                    label = stringResource(R.string.account_stat_following),
                    icon = Icons.Rounded.PersonAdd
                )
                Spacer(Modifier.width(8.dp))
                StatCounterBox(
                    modifier = Modifier.weight(1f),
                    count = user.repostsCount,
                    label = stringResource(R.string.account_stat_reposts),
                    icon = Icons.Rounded.Repeat
                )
            }

            if (user.playlistLikesCount > 0 || user.privateTracksCount > 0 || user.privatePlaylistsCount > 0) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (user.playlistLikesCount > 0) {
                        StatCounterBox(
                            modifier = Modifier.weight(1f),
                            count = user.playlistLikesCount,
                            label = stringResource(R.string.account_stat_playlist_likes),
                            icon = Icons.Rounded.LibraryMusic
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (user.privateTracksCount > 0) {
                        StatCounterBox(
                            modifier = Modifier.weight(1f),
                            count = user.privateTracksCount,
                            label = stringResource(R.string.account_stat_private_tracks),
                            icon = Icons.Rounded.Lock
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (user.privatePlaylistsCount > 0) {
                        StatCounterBox(
                            modifier = Modifier.weight(1f),
                            count = user.privatePlaylistsCount,
                            label = stringResource(R.string.account_stat_private_playlists),
                            icon = Icons.Rounded.FolderSpecial
                        )
                    }
                }
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
                text = formatStatNumber(count),
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

@Composable
private fun SoundCloudGuestCard(
    onLoginClick: () -> Unit
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
                        painter = painterResource(id = R.drawable.ic_soundcloud),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.account_header_guest_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.account_header_guest_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_soundcloud),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.account_btn_login),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

private fun formatStatNumber(number: Int): String {
    return when {
        number >= 1_000_000 -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format(Locale.US, "%.1fK", number / 1_000.0)
        else -> number.toString()
    }
}
