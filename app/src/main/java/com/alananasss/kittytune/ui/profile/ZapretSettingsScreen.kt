package com.alananasss.kittytune.ui.profile

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.zapret.Reachability
import com.alananasss.kittytune.data.zapret.ServiceCheck
import com.alananasss.kittytune.data.zapret.ZapretManager
import com.alananasss.kittytune.data.zapret.ZapretService
import com.alananasss.kittytune.data.zapret.ZapretStrategy
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import kotlinx.coroutines.launch

/**
 * Zapret on Android: DPI bypass built into the app — no zapret folder, no VPN, no proxy.
 * Same service catalogue and check logic as KittyTune Desktop; the bypass list drives the
 * in-app ClientHello fragmentation instead of an external `winws`/`nfqws` process.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZapretSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val zapretState by ZapretManager.state.collectAsState()

    var checks by remember { mutableStateOf<List<ServiceCheck>?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val strategyName = stringResource(
        if (zapretState.strategy == ZapretStrategy.MULTI) R.string.zapret_strategy_multi
        else R.string.zapret_strategy_split2
    )

    fun runCheck() {
        if (isChecking) return
        isChecking = true
        message = null
        scope.launch {
            checks = ZapretManager.check()
            isChecking = false
        }
    }

    fun add(services: List<ZapretService>) {
        scope.launch {
            val added = ZapretManager.addDomains(context, services)
            message = if (added.isEmpty()) context.getString(R.string.zapret_nothing_to_add)
            else context.getString(R.string.zapret_added, added.size)
            checks = ZapretManager.check()
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.zapret_title),
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
            // What it does: built-in DPI bypass, KittyTune traffic only.
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.zapret_info_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.zapret_info_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Master switch.
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_general),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.zapret_enable),
                                subtitle = if (zapretState.enabled) {
                                    context.getString(
                                        R.string.zapret_status_enabled,
                                        zapretState.domains.size,
                                        strategyName
                                    )
                                } else {
                                    stringResource(R.string.zapret_enable_desc)
                                },
                                icon = Icons.Rounded.Shield,
                                hasSwitch = true,
                                switchState = zapretState.enabled,
                                onSwitchChange = { enabled ->
                                    ZapretManager.setEnabled(context, enabled)
                                    message = null
                                    if (enabled) {
                                        // First enable: probe and bypass only what is actually blocked.
                                        scope.launch {
                                            val added = ZapretManager.autoConfigureOnce(context)
                                            if (added.isNotEmpty()) {
                                                message = context.getString(R.string.zapret_added, added.size)
                                            }
                                            checks = ZapretManager.check()
                                        }
                                    }
                                }
                            )
                        }
                    )
                )
            }

            // Strategy picker.
            item {
                AnimatedVisibility(
                    visible = zapretState.enabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                text = stringResource(R.string.zapret_strategy),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val options = listOf(ZapretStrategy.SPLIT2, ZapretStrategy.MULTI)
                            ExpressiveConnectedButtonGroup(
                                options = options,
                                selectedOption = zapretState.strategy,
                                onOptionSelected = { selected ->
                                    ZapretManager.setStrategy(context, selected)
                                },
                                labelProvider = { option ->
                                    Text(
                                        text = stringResource(
                                            if (option == ZapretStrategy.MULTI) R.string.zapret_strategy_multi
                                            else R.string.zapret_strategy_split2
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                iconProvider = {
                                    Icon(
                                        imageVector = Icons.Rounded.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            Text(
                                text = stringResource(
                                    if (zapretState.strategy == ZapretStrategy.MULTI) R.string.zapret_strategy_multi_desc
                                    else R.string.zapret_strategy_split2_desc
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Service check + results.
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = ::runCheck,
                                enabled = !isChecking,
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Icon(
                                    Icons.Rounded.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(
                                        if (isChecking) R.string.zapret_checking
                                        else R.string.zapret_check
                                    )
                                )
                            }
                            val blocked = checks.orEmpty()
                                .filter { it.reachability == Reachability.BLOCKED && !it.isCovered }
                            if (blocked.isNotEmpty()) {
                                FilledTonalButton(
                                    onClick = { add(blocked.map { it.service }) },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(stringResource(R.string.zapret_add_blocked, blocked.size))
                                }
                            }
                            if (isChecking) {
                                LoadingIndicator(
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        checks?.let { results ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                results.forEach { check ->
                                    ZapretServiceRow(check) { add(listOf(check.service)) }
                                }
                            }
                        }

                        message?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        // What KittyTune bypasses.
                        if (zapretState.domains.isNotEmpty()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.zapret_own_title),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        zapretState.domains.sorted().joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(
                                            AnnotatedString(ZapretManager.exportAsHostList())
                                        )
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.zapret_copied),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.zapret_copy_list))
                                }
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            ZapretManager.removeOwnDomains(context)
                                            checks?.let { checks = ZapretManager.check() }
                                        }
                                    }
                                ) { Text(stringResource(R.string.zapret_remove_own)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZapretServiceRow(check: ServiceCheck, onAdd: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (label, color) = when {
        check.reachability == Reachability.REACHABLE -> stringResource(R.string.zapret_reachable) to scheme.primary
        check.isCovered -> stringResource(R.string.zapret_covered) to scheme.secondary
        else -> stringResource(R.string.zapret_blocked) to scheme.error
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                check.service.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                check.service.domains.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        if (!check.isCovered && check.reachability == Reachability.BLOCKED) {
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onAdd) { Text(stringResource(R.string.zapret_add)) }
        }
    }
}
