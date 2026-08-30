package com.alananasss.kittytune.ui.profile

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.vk.VkQrAuthManager
import com.alananasss.kittytune.data.vk.VkQrCodeData
import com.alananasss.kittytune.data.vk.VkQrPollState
import com.alananasss.kittytune.ui.common.QrCodeGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VkQrLoginDialog(
    onDismissRequest: () -> Unit,
    onSuccess: (token: String?, userId: Long, remixsid: String?, remixnsid: String?, remixdsid: String?, firstName: String, lastName: String, photoUrl: String?, screenName: String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authManager = remember { VkQrAuthManager(context) }

    var isLoading by remember { mutableStateOf(true) }
    var qrData by remember { mutableStateOf<VkQrCodeData?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pollState by remember { mutableStateOf<VkQrPollState>(VkQrPollState.WaitingScan) }
    var confirmationCode by remember { mutableStateOf("") }
    var isValidatingCode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadQrCode() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            pollState = VkQrPollState.WaitingScan
            qrBitmap = null

            val data = authManager.requestQrCode()
            if (data != null) {
                qrData = data
                qrBitmap = QrCodeGenerator.generateQrBitmap(
                    content = data.authUrl,
                    sizePx = 512,
                    darkColor = android.graphics.Color.BLACK,
                    lightColor = android.graphics.Color.WHITE
                )
                isLoading = false
            } else {
                isLoading = false
                errorMessage = "Failed to load VK QR code. Please check your network connection."
            }
        }
    }

    LaunchedEffect(Unit) {
        loadQrCode()
    }

    // Polling loop
    LaunchedEffect(qrData) {
        val currentQr = qrData ?: return@LaunchedEffect
        while (isActive) {
            delay(2000)
            val state = authManager.checkQrStatus(currentQr)
            pollState = state

            when (state) {
                is VkQrPollState.Success -> {
                    onSuccess(
                        state.token,
                        state.userId,
                        state.remixsid,
                        state.remixnsid,
                        state.remixdsid,
                        state.firstName,
                        state.lastName,
                        state.photoUrl,
                        state.screenName
                    )
                    onDismissRequest()
                    break
                }
                is VkQrPollState.Expired -> {
                    break
                }
                is VkQrPollState.Error -> {
                    errorMessage = state.message
                    break
                }
                else -> {
                    // Continue polling
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_vk),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = stringResource(R.string.account_vk_qr_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.account_vk_qr_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Box(
                    modifier = Modifier
                        .size(230.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (isLoading || qrBitmap == null) MaterialTheme.colorScheme.surfaceContainerHighest else Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading || qrBitmap == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "VK QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    }
                }

                // Dynamic status box
                AnimatedContent(
                    targetState = pollState,
                    label = "QrStatusContent"
                ) { state ->
                    when (state) {
                        is VkQrPollState.WaitingScan -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.account_vk_qr_waiting_scan),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Scanning needs a second device. The QR simply encodes this URL, so
                                // opening it here hands the same confirmation to the VK app (or to a
                                // logged-in browser) on the very phone running KittyTune.
                                qrData?.authUrl?.takeIf { it.isNotBlank() }?.let { authUrl ->
                                    FilledTonalButton(
                                        onClick = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                                )
                                            }
                                        },
                                        shapes = ButtonDefaults.shapes(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhoneAndroid,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.account_vk_qr_same_device))
                                    }
                                    Text(
                                        text = stringResource(R.string.account_vk_qr_same_device_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        is VkQrPollState.ScannedWaitingConfirm -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhoneAndroid,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.account_vk_qr_scanned_waiting_confirm),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = confirmationCode,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }.take(6)
                                        confirmationCode = digits
                                        if (digits.length == 6 && qrData != null && !isValidatingCode) {
                                            isValidatingCode = true
                                            coroutineScope.launch {
                                                val res = authManager.validateAuthCode(qrData!!, digits)
                                                isValidatingCode = false
                                                if (res is VkQrPollState.Success) {
                                                    onSuccess(
                                                        res.token,
                                                        res.userId,
                                                        res.remixsid,
                                                        res.remixnsid,
                                                        res.remixdsid,
                                                        res.firstName,
                                                        res.lastName,
                                                        res.photoUrl,
                                                        res.screenName
                                                    )
                                                    onDismissRequest()
                                                }
                                            }
                                        }
                                    },
                                    label = { Text(stringResource(R.string.account_vk_qr_code_input_label)) },
                                    placeholder = { Text("123456") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (confirmationCode.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            if (qrData != null && !isValidatingCode) {
                                                isValidatingCode = true
                                                coroutineScope.launch {
                                                    val res = authManager.validateAuthCode(qrData!!, confirmationCode)
                                                    isValidatingCode = false
                                                    if (res is VkQrPollState.Success) {
                                                        onSuccess(
                                                            res.token,
                                                            res.userId,
                                                            res.remixsid,
                                                            res.remixnsid,
                                                            res.remixdsid,
                                                            res.firstName,
                                                            res.lastName,
                                                            res.photoUrl,
                                                            res.screenName
                                                        )
                                                        onDismissRequest()
                                                    }
                                                }
                                            }
                                        },
                                        shapes = ButtonDefaults.shapes(),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.account_vk_qr_code_btn_confirm))
                                    }
                                }
                            }
                        }
                        is VkQrPollState.Expired -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.account_vk_qr_expired),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium
                                )
                                FilledTonalButton(
                                    onClick = { loadQrCode() },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.account_vk_qr_reload))
                                }
                            }
                        }
                        is VkQrPollState.Error -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = errorMessage ?: state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                FilledTonalButton(
                                    onClick = { loadQrCode() },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.account_vk_qr_reload))
                                }
                            }
                        }
                        is VkQrPollState.Success -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.account_vk_login_success),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismissRequest,
                shapes = ButtonDefaults.shapes()
            ) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
