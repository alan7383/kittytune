package com.alananasss.kittytune.ui.login

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.alananasss.kittytune.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WelcomeScreen(
    onLoginClick: () -> Unit,
    onGuestClick: () -> Unit,
    isGuestLoading: Boolean
) {
    val context = LocalContext.current

    // État de la permission des notifications (Requis pour Android 13+)
    var isNotificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // Gestion de l'action en attente si l'utilisateur essaie de passer sans la permission
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPermissionWarningDialog by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isNotificationsGranted = isGranted
        // S'il a cliqué sur "Autoriser" depuis le dialogue de warning et qu'il accepte, on exécute l'action de connexion direct
        if (isGranted && pendingAction != null) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }

    // Fonction d'interception
    fun handleLoginAttempt(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationsGranted) {
            pendingAction = action
            showPermissionWarningDialog = true
        } else {
            action()
        }
    }

    // Boîte de dialogue d'avertissement (façon ReVanced Safeguard)
    if (showPermissionWarningDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionWarningDialog = false },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = {
                Text(
                    text = "Highly Recommended", // Tu peux mettre ça dans ton strings.xml
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "KittyTune needs notifications to show the music player in the background. Without it, you won't be able to pause or skip tracks outside the app.\n\nAre you sure you want to skip?",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionWarningDialog = false
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text("Allow Notifications")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPermissionWarningDialog = false
                        pendingAction?.invoke() // Exécute la connexion quand même
                        pendingAction = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel)) // Ou "Skip anyway"
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .systemBarsPadding()
        ) {
            val useSplitLayout = maxWidth >= maxHeight

            if (useSplitLayout) {
                // --- MODE PAYSAGE / TABLETTE ---
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = 24.dp, end = 16.dp, top = 48.dp, bottom = 32.dp),
                    ) {
                        WelcomeHeader()
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 24.dp, top = 32.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            NotificationPermissionCard(
                                isGranted = isNotificationsGranted,
                                onRequest = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        WelcomeButtons(
                            onLoginClick = { handleLoginAttempt(onLoginClick) },
                            onGuestClick = { handleLoginAttempt(onGuestClick) },
                            isGuestLoading = isGuestLoading
                        )
                    }
                }
            } else {
                // --- MODE PORTRAIT ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                ) {
                    Spacer(Modifier.height(32.dp))
                    WelcomeHeader()
                    Spacer(Modifier.weight(1f))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        NotificationPermissionCard(
                            isGranted = isNotificationsGranted,
                            onRequest = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                        Spacer(Modifier.height(24.dp))
                    }

                    WelcomeButtons(
                        onLoginClick = { handleLoginAttempt(onLoginClick) },
                        onGuestClick = { handleLoginAttempt(onGuestClick) },
                        isGuestLoading = isGuestLoading
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeHeader() {
    val context = LocalContext.current
    val appIcon = rememberDrawablePainter(
        drawable = remember(context) {
            ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                ?: ContextCompat.getDrawable(context, R.drawable.ic_launcher_monochrome)
        }
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = appIcon,
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotificationPermissionCard(
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Surface(
        onClick = if (isGranted) { {} } else onRequest,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGranted) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (isGranted) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permission_notifications),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.permission_notifications_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }

            if (isGranted) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onRequest,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(
                        text = stringResource(R.string.permission_grant),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeButtons(
    onLoginClick: () -> Unit,
    onGuestClick: () -> Unit,
    isGuestLoading: Boolean
) {
    AnimatedContent(
        targetState = isGuestLoading,
        transitionSpec = { fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut() },
        label = "LoadingState"
    ) { loading ->
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ContainedLoadingIndicator()
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.guest_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_soundcloud),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.login_soundcloud),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }

                FilledTonalButton(
                    onClick = onGuestClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shapes = ButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.login_guest),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}