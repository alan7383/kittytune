package com.alananasss.kittytune.ui.musicimport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.musicimport.MusicApi
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.PlaylistSyncStatus
import com.alananasss.kittytune.ui.common.SettingsScaffold

@Composable
fun MusicImportTransferScreen(
    onBackClick: () -> Unit,
    onDone: () -> Unit,
    previewPlatform: MusicApi? = null,
    viewModel: MusicImportTransferViewModel = viewModel()
) {
    LaunchedEffect(previewPlatform) {
        if (previewPlatform != null) {
            viewModel.previewMock(previewPlatform)
        } else {
            viewModel.start()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val isTransferring = viewModel.phase == TransferPhase.STARTING || viewModel.phase == TransferPhase.SYNCING

    val handleBackClick = {
        if (isTransferring) {
            android.widget.Toast.makeText(context, context.getString(R.string.music_import_transfer_in_progress_toast), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            onBackClick()
        }
    }

    androidx.activity.compose.BackHandler(enabled = true, onBack = handleBackClick)

    SettingsScaffold(
        title = stringResource(R.string.music_import_transfer_title),
        onBackClick = handleBackClick
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (viewModel.phase) {
                TransferPhase.IDLE -> Unit
                TransferPhase.ERROR -> ErrorState(error = viewModel.error)
                TransferPhase.DONE -> DoneState(onDone = onDone)
                else -> SyncingState(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SyncingState(viewModel: MusicImportTransferViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        val platform = viewModel.platform
        if (platform != null) {
            val visual = platform.visual()
            Icon(
                painter = androidx.compose.ui.res.painterResource(visual.logoRes),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(platform.labelRes()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.music_import_transfer_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearWavyProgressIndicator(
            progress = { viewModel.overallProgress / 100f },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.music_import_transfer_percent, viewModel.overallProgress),
            style = MaterialTheme.typography.titleMedium
        )

    }
}

@Composable
private fun DoneState(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.music_import_transfer_done),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.music_import_transfer_done_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDone,
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.music_import_done), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ErrorState(error: String?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(
                if (error == "authentication_required") R.string.music_import_auth_required
                else R.string.music_import_transfer_error
            ),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}
