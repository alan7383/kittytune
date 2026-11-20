package com.alananasss.kittytune.ui.together

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.ui.common.QrCodeGenerator

@Composable
internal fun TogetherInviteQrButton(code: String) {
    var showQr by remember(code) { mutableStateOf(false) }
    val label = stringResource(R.string.together_invite_qr)
    FilledTonalIconButton(
        onClick = { showQr = true },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(Icons.Rounded.QrCode2, contentDescription = label)
    }

    if (showQr) {
        val inviteUrl = remember(code) { TogetherLinks.inviteUrl(code) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text(label) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) {
                        val bitmap = remember(inviteUrl) { QrCodeGenerator.generateQrBitmap(inviteUrl)?.asImageBitmap() }
                        bitmap?.let {
                            Image(bitmap = it, contentDescription = label, modifier = Modifier.size(212.dp))
                        }
                    }
                    Text(
                        stringResource(R.string.together_invite_qr_instructions),
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text(stringResource(R.string.btn_close)) } },
        )
    }
}
