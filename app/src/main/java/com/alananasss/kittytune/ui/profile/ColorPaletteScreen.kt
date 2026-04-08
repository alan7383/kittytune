package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppThemeMode
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

val keyColorOptions = listOf(
    Color(0xFFF44336).toArgb(), Color(0xFFE91E63).toArgb(),
    Color(0xFF9C27B0).toArgb(), Color(0xFF673AB7).toArgb(),
    Color(0xFF3F51B5).toArgb(), Color(0xFF2196F3).toArgb(),
    Color(0xFF00BCD4).toArgb(), Color(0xFF009688).toArgb(),
    Color(0xFF4FAF50).toArgb(), Color(0xFFFFEB3B).toArgb(),
    Color(0xFFFFC107).toArgb(), Color(0xFFFF9800).toArgb(),
    Color(0xFF795548).toArgb(), Color(0xFF607D8F).toArgb(),
    Color(0xFFFF9CA8).toArgb(),
)

@Composable
fun ColorPaletteScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val haptic = LocalHapticFeedback.current

    var currentKeyColor by remember { mutableIntStateOf(prefs.getKeyColor()) }
    var colorStyle by remember { mutableStateOf(prefs.getColorStyle()) }
    val themeMode = prefs.getThemeMode()
    val pureBlack = prefs.getPureBlack()

    val isDark = themeMode == AppThemeMode.DARK || (themeMode == AppThemeMode.SYSTEM && isSystemInDarkTheme())

    SettingsScaffold(
        title = stringResource(R.string.color_palette_screen_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            ThemePreviewCard(
                keyColor = currentKeyColor,
                isDark = isDark,
                pureBlack = pureBlack,
                paletteStyle = try { PaletteStyle.valueOf(colorStyle) } catch (e: Exception) { PaletteStyle.TonalSpot }
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ColorButtonMaterial(
                        color = Color.Unspecified,
                        isSelected = currentKeyColor == 0,
                        isDark = isDark,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            currentKeyColor = 0; prefs.setKeyColor(0)
                        }
                    )
                }
                items(keyColorOptions) { colorArgb ->
                    ColorButtonMaterial(
                        color = Color(colorArgb),
                        isSelected = currentKeyColor == colorArgb,
                        isDark = isDark,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            currentKeyColor = colorArgb; prefs.setKeyColor(colorArgb)
                        }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColorAtElevation(1.dp))
            ) {
                Column {
                    val styles = PaletteStyle.entries.map { it.name }
                    SettingsDropdownRow(
                        title = stringResource(R.string.pref_color_style_title),
                        items = styles,
                        selectedItem = colorStyle,
                        onItemSelected = { colorStyle = it; prefs.setColorStyle(it) }
                    )
                }
            }

            Spacer(Modifier.height(140.dp))
        }
    }
}

@Composable
fun SettingsDropdownRow(
    title: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Box {
            Text(
                text = selectedItem,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items.forEach { label ->
                    DropdownMenuItem(
                        text = { Text(label, color = if (label == selectedItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                        onClick = { onItemSelected(label); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(
    keyColor: Int,
    isDark: Boolean,
    pureBlack: Boolean,
    paletteStyle: PaletteStyle
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight

    val colorScheme = if (keyColor == 0) {
        val baseScheme = if (isDark && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) androidx.compose.material3.dynamicDarkColorScheme(context)
        else if (!isDark && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) androidx.compose.material3.dynamicLightColorScheme(context)
        else if (isDark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
        rememberDynamicColorScheme(
            seedColor = baseScheme.primary,
            isDark = isDark,
            isAmoled = pureBlack,
            style = paletteStyle
        )
    } else {
        rememberDynamicColorScheme(
            seedColor = Color(keyColor),
            isDark = isDark,
            isAmoled = pureBlack,
            style = paletteStyle
        )
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.35f).aspectRatio(screenRatio),
            color = colorScheme.background,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                Box(modifier = Modifier.height(40.dp).fillMaxWidth(), contentAlignment = Alignment.TopStart) {
                    Row(modifier = Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(colors = CardDefaults.cardColors(containerColor = colorScheme.primary), modifier = Modifier.fillMaxWidth().height(32.dp), shape = RoundedCornerShape(12.dp)) {}
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Card(colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh), modifier = Modifier.weight(1f).height(24.dp), shape = RoundedCornerShape(12.dp)) {}
                            Card(colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerHigh), modifier = Modifier.weight(1f).height(24.dp), shape = RoundedCornerShape(12.dp)) {}
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().height(70.dp), shape = RoundedCornerShape(12.dp)) {}
                    }
                }
                Surface(color = colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.height(32.dp).fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Home, null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorButtonMaterial(color: Color, isSelected: Boolean, isDark: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val colorScheme = if (color == Color.Unspecified) {
        val baseScheme = if (isDark && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) androidx.compose.material3.dynamicDarkColorScheme(context)
        else if (!isDark && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) androidx.compose.material3.dynamicLightColorScheme(context)
        else if (isDark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
        rememberDynamicColorScheme(
            seedColor = baseScheme.primary,
            isDark = isDark
        )
    } else {
        rememberDynamicColorScheme(seedColor = color, isDark = isDark)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = surfaceColorAtElevation(1.dp),
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(40.dp)) {
                drawArc(color = colorScheme.primaryContainer, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(color = colorScheme.tertiaryContainer, startAngle = 0f, sweepAngle = 180f, useCenter = true)
            }

            val scale by animateFloatAsState(targetValue = if (isSelected) 1.1f else 1.0f, label = "scale")
            Box(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(modifier = Modifier.size(48.dp).border(3.dp, colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(colorScheme.primary, CircleShape)) {
                            Icon(Icons.Rounded.Check, null, tint = colorScheme.onPrimary, modifier = Modifier.align(Alignment.Center).size(14.dp))
                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSelected,
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.8f)
                ) {
                    Box(modifier = Modifier.size(16.dp).background(colorScheme.primary, CircleShape))
                }
            }
        }
    }
}

@Composable
fun surfaceColorAtElevation(elevation: androidx.compose.ui.unit.Dp): Color {
    if (elevation == 0.dp) return MaterialTheme.colorScheme.surface
    val alpha = ((4.5f * kotlin.math.ln(elevation.value + 1)) + 2f) / 100f
    return androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primary, alpha)
}