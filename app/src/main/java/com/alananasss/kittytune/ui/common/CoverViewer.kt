package com.alananasss.kittytune.ui.common

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

object CoverViewerState {
    var visible by mutableStateOf(false)
    var currentUrl by mutableStateOf<String?>(null)

    fun show(url: String?) {
        if (!url.isNullOrBlank()) {
            currentUrl = url
            visible = true
        }
    }

    fun hide() {
        visible = false
    }
}

fun Modifier.viewableCover(url: String?): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null
) {
    if (!url.isNullOrBlank()) CoverViewerState.show(url)
}

@Composable
fun CoverViewerOverlay() {
    val visible = CoverViewerState.visible
    val url = CoverViewerState.currentUrl
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        if (uri != null && url != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        val u = URL(url)
                        u.openStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(context, context.getString(R.string.cover_saved), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) { 
                        Toast.makeText(context, context.getString(R.string.cover_save_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val transitionState = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { CoverViewerState.hide() },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val view = androidx.compose.ui.platform.LocalView.current
            SideEffect {
                val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                if (window != null) {
                    window.setDimAmount(0f)
                    window.setWindowAnimations(0)
                }
            }

            AnimatedVisibility(
                visibleState = transitionState,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { CoverViewerState.hide() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
            AnimatedVisibility(visible = visible, enter = scaleIn(initialScale = 0.9f), exit = scaleOut(targetScale = 0.9f)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 64.dp, horizontal = 32.dp)
                ) {
                    
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        FilledTonalButton(shapes = ButtonDefaults.shapes(), 
                            onClick = {
                                url?.let {
                                    scope.launch {
                                        clipboardManager.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("url", it)))
                                    }
                                    Toast.makeText(context, context.getString(R.string.cover_copy_link), Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Link, null)
                            Spacer(Modifier.width(8.dp))
                            Text(context.getString(R.string.cover_copy_link))
                        }
                        
                        Button(shapes = ButtonDefaults.shapes(), 
                            onClick = {
                                url?.let {
                                    createDocumentLauncher.launch("cover_${System.currentTimeMillis()}.jpg")
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.Download, null)
                            Spacer(Modifier.width(8.dp))
                            Text(context.getString(R.string.btn_download))
                        }
                    }
                }
            }
            
            IconButton(shapes = IconButtonDefaults.shapes(), 
                onClick = { CoverViewerState.hide() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
            }
        }
    }
}
}
