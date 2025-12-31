// CHEMIN : app/src/main/java/com/alananasss.kittytune.ui/profile/LicensesScreen.kt

package com.alananasss.kittytune.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.R

// just keeping it simple
data class OpenSourceLibrary(
    val name: String,
    val author: String,
    val license: String = "Apache License 2.0",
    val url: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // list of awesome stuff we use
    val libraries = listOf(
        // --- jetpack compose & ui ---
        OpenSourceLibrary("Jetpack Compose", "Google", url = "https://developer.android.com/jetpack/compose"),
        OpenSourceLibrary("Material Components for Android", "Google", url = "https://github.com/material-components/material-components-android"),
        OpenSourceLibrary("AndroidX Activity & Core", "Google", url = "https://developer.android.com/jetpack/androidx"),
        OpenSourceLibrary("AndroidX Lifecycle", "Google", url = "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
        OpenSourceLibrary("AndroidX Navigation", "Google", url = "https://developer.android.com/jetpack/androidx/releases/navigation"),
        OpenSourceLibrary("Palette API", "Google", url = "https://developer.android.com/training/material/palette-colors"),

        // --- core & language ---
        OpenSourceLibrary("Kotlin", "JetBrains", url = "https://kotlinlang.org/"),
        OpenSourceLibrary("Kotlin Coroutines", "JetBrains", url = "https://github.com/Kotlin/kotlinx.coroutines"),

        // --- networking ---
        OpenSourceLibrary("Retrofit", "Square, Inc.", url = "https://square.github.io/retrofit/"),
        OpenSourceLibrary("OkHttp", "Square, Inc.", url = "https://square.github.io/okhttp/"),
        OpenSourceLibrary("Gson", "Google", url = "https://github.com/google/gson"),

        // --- database ---
        OpenSourceLibrary("Room", "Google", url = "https://developer.android.com/jetpack/androidx/releases/room"),

        // --- media & images ---
        OpenSourceLibrary("Media3 (ExoPlayer)", "Google", url = "https://developer.android.com/jetpack/androidx/releases/media3"),
        OpenSourceLibrary("Coil (Image Loading)", "Coil Contributors", url = "https://coil-kt.github.io/coil/"),

        // --- utils ---
        OpenSourceLibrary("OSS Licenses Plugin", "Google", license="The 3-Clause BSD License", url = "https://github.com/google/play-services-plugins/tree/master/oss-licenses-plugin")
    ).sortedBy { it.name }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.about_licenses_title), fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    scrollBehavior = scrollBehavior
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(libraries) { lib ->
                    LicenseItem(library = lib) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lib.url))
                        context.startActivity(intent)
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun LicenseItem(library: OpenSourceLibrary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = library.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = stringResource(R.string.license_author_prefix, library.author), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(text = library.license, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                imageVector = Icons.Rounded.OpenInNew,
                contentDescription = stringResource(R.string.open_in_new),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}