package com.alananasss.kittytune.ui.profile

import android.view.HapticFeedbackConstants
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsScaffold

enum class CreditFilter {
    ALL,
    DEV,
    TRANSLATION,
    COMMUNITY
}

enum class ContributorCategory {
    DEV,
    TRANSLATION,
    COMMUNITY
}

data class Contributor(
    val name: String,
    @StringRes val roleResId: Int,
    @StringRes val descriptionResId: Int,
    val badge: String,
    val url: String,
    val avatarUrl: String?,
    val category: ContributorCategory
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current

    val contributors = remember {
        listOf(
            Contributor(
                name = "alananasss",
                roleResId = R.string.about_role_dev,
                descriptionResId = R.string.about_role_dev_desc,
                badge = "Lead Dev",
                url = "https://github.com/alan7383",
                avatarUrl = "https://github.com/alan7383.png",
                category = ContributorCategory.DEV
            ),
            Contributor(
                name = "gashjfkkjhsdafjnk",
                roleResId = R.string.about_role_translation_de,
                descriptionResId = R.string.about_role_translation_de_desc,
                badge = "🇩🇪 Deutsch",
                url = "https://github.com/gashjfkkjhsdafjnk",
                avatarUrl = "https://github.com/gashjfkkjhsdafjnk.png",
                category = ContributorCategory.TRANSLATION
            ),
            Contributor(
                name = "wynriu",
                roleResId = R.string.about_role_translation_vi,
                descriptionResId = R.string.about_role_translation_vi_desc,
                badge = "🇻🇳 Tiếng Việt",
                url = "https://github.com/wynriu",
                avatarUrl = "https://github.com/wynriu.png",
                category = ContributorCategory.TRANSLATION
            ),
            Contributor(
                name = "Егор Белоусов (kivoyoso)",
                roleResId = R.string.about_role_translation_ru,
                descriptionResId = R.string.about_role_translation_ru_desc,
                badge = "🇷🇺 Русский",
                url = "https://crowdin.com/profile/kivoyoso",
                avatarUrl = "https://github.com/kivoyoso.png",
                category = ContributorCategory.TRANSLATION
            ),
            Contributor(
                name = "meowsite",
                roleResId = R.string.about_role_translation_qa,
                descriptionResId = R.string.about_role_translation_qa_desc,
                badge = "🌐 QA & Feedback",
                url = "https://github.com/meowsite",
                avatarUrl = "https://github.com/meowsite.png",
                category = ContributorCategory.COMMUNITY
            ),
            Contributor(
                name = "sneoww98",
                roleResId = R.string.about_role_community_contrib,
                descriptionResId = R.string.about_role_community_contrib_desc,
                badge = "Contributor",
                url = "https://github.com/sneoww98",
                avatarUrl = "https://github.com/sneoww98.png",
                category = ContributorCategory.COMMUNITY
            ),
            Contributor(
                name = "quntqunt",
                roleResId = R.string.about_role_community_contrib,
                descriptionResId = R.string.about_role_community_contrib_desc,
                badge = "Contributor",
                url = "https://github.com/quntqunt",
                avatarUrl = "https://github.com/quntqunt.png",
                category = ContributorCategory.COMMUNITY
            ),
            Contributor(
                name = "tankist939-afk",
                roleResId = R.string.about_role_community_contrib,
                descriptionResId = R.string.about_role_community_contrib_desc,
                badge = "Contributor",
                url = "https://github.com/tankist939-afk",
                avatarUrl = "https://github.com/tankist939-afk.png",
                category = ContributorCategory.COMMUNITY
            )
        )
    }

    var selectedFilter by remember { mutableStateOf(CreditFilter.ALL) }

    val filteredContributors = remember(selectedFilter, contributors) {
        when (selectedFilter) {
            CreditFilter.ALL -> contributors
            CreditFilter.DEV -> contributors.filter { it.category == ContributorCategory.DEV }
            CreditFilter.TRANSLATION -> contributors.filter { it.category == ContributorCategory.TRANSLATION }
            CreditFilter.COMMUNITY -> contributors.filter { it.category == ContributorCategory.COMMUNITY }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.about_credits_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Discreet Subtitle Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(14.dp))

                        Text(
                            text = stringResource(R.string.about_credits_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Category Filter: Expressive Connected Button Group
            item {
                ExpressiveConnectedButtonGroup(
                    options = CreditFilter.entries,
                    selectedOption = selectedFilter,
                    onOptionSelected = { filter ->
                        selectedFilter = filter
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                    iconProvider = { filter ->
                        val icon = when (filter) {
                            CreditFilter.ALL -> Icons.Rounded.Groups
                            CreditFilter.DEV -> Icons.Rounded.Code
                            CreditFilter.TRANSLATION -> Icons.Rounded.Language
                            CreditFilter.COMMUNITY -> Icons.Rounded.Forum
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    labelProvider = { filter ->
                        val count = when (filter) {
                            CreditFilter.ALL -> contributors.size
                            CreditFilter.DEV -> contributors.count { it.category == ContributorCategory.DEV }
                            CreditFilter.TRANSLATION -> contributors.count { it.category == ContributorCategory.TRANSLATION }
                            CreditFilter.COMMUNITY -> contributors.count { it.category == ContributorCategory.COMMUNITY }
                        }
                        val label = when (filter) {
                            CreditFilter.ALL -> stringResource(R.string.about_credits_filter_all)
                            CreditFilter.DEV -> stringResource(R.string.about_credits_filter_dev)
                            CreditFilter.TRANSLATION -> stringResource(R.string.about_credits_filter_translation)
                            CreditFilter.COMMUNITY -> stringResource(R.string.about_credits_filter_community)
                        }
                        Text(
                            text = "$label ($count)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            // Contributors display
            if (selectedFilter == CreditFilter.ALL) {
                // Section: Development
                val devList = contributors.filter { it.category == ContributorCategory.DEV }
                if (devList.isNotEmpty()) {
                    item {
                        CreditsSectionHeader(
                            icon = Icons.Rounded.Code,
                            title = stringResource(R.string.about_credits_dev_section),
                            count = devList.size
                        )
                    }
                    items(devList, key = { it.name }) { person ->
                        ContributorCard(
                            person = person,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                uriHandler.openUri(person.url)
                            }
                        )
                    }
                }

                // Section: Translations
                val transList = contributors.filter { it.category == ContributorCategory.TRANSLATION }
                if (transList.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        CreditsSectionHeader(
                            icon = Icons.Rounded.Language,
                            title = stringResource(R.string.about_credits_translation_section),
                            count = transList.size
                        )
                    }
                    items(transList, key = { it.name }) { person ->
                        ContributorCard(
                            person = person,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                uriHandler.openUri(person.url)
                            }
                        )
                    }
                }

                // Section: Community & QA
                val commList = contributors.filter { it.category == ContributorCategory.COMMUNITY }
                if (commList.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        CreditsSectionHeader(
                            icon = Icons.Rounded.Forum,
                            title = stringResource(R.string.about_credits_community_section),
                            count = commList.size
                        )
                    }
                    items(commList, key = { it.name }) { person ->
                        ContributorCard(
                            person = person,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                uriHandler.openUri(person.url)
                            }
                        )
                    }
                }
            } else {
                // Flat filtered list
                items(filteredContributors, key = { it.name }) { person ->
                    ContributorCard(
                        person = person,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            uriHandler.openUri(person.url)
                        }
                    )
                }
            }

            // CTA Section: "Want to contribute?"
            item {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.about_credits_contribute_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // CTA Crowdin
            item {
                ContributeActionCard(
                    icon = Icons.Rounded.Language,
                    title = stringResource(R.string.about_credits_crowdin_title),
                    subtitle = stringResource(R.string.about_credits_crowdin_desc),
                    badge = "Crowdin",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        uriHandler.openUri("https://crowdin.com/project/kittytune")
                    }
                )
            }

            // CTA GitHub
            item {
                ContributeActionCard(
                    icon = Icons.Rounded.Code,
                    title = stringResource(R.string.about_credits_github_title),
                    subtitle = stringResource(R.string.about_credits_github_desc),
                    badge = "GitHub",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        uriHandler.openUri("https://github.com/alan7383/kittytune")
                    }
                )
            }

            // CTA Ko-fi
            item {
                ContributeActionCard(
                    icon = Icons.Rounded.VolunteerActivism,
                    title = stringResource(R.string.about_credits_kofi_title),
                    subtitle = stringResource(R.string.about_credits_kofi_desc),
                    badge = "Ko-fi",
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        uriHandler.openUri("https://ko-fi.com/alan7383")
                    }
                )
            }
        }
    }
}


@Composable
private fun CreditsSectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ContributorCard(
    person: Contributor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Picture / Avatar
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(
                        BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = person.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (!person.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(person.avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = person.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // Information
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = person.badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(person.roleResId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = stringResource(person.descriptionResId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ContributeActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
