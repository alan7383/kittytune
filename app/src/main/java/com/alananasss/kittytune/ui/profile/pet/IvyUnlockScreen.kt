package com.alananasss.kittytune.ui.profile.pet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.theme.GoogleSansRounded
import com.alananasss.kittytune.ui.widget.pet.PetActivity
import com.alananasss.kittytune.ui.widget.pet.PetIvy
import com.alananasss.kittytune.ui.widget.pet.PetSettings
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private val SPACE_TOP = Color(0xFF060A18)
private val SPACE_BOTTOM = Color(0xFF0E2A3F)
private val GLOW = Color(0xFF8FE3FF)

/**
 * Easter egg reveal: Ivy, the cat of KittyTune's creator. His real (space-themed) photo appears,
 * pixelates step by step, and turns into his pixel twin floating among the stars.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun IvyUnlockScreen(settings: PetSettings, onAdopt: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)

    val photo = remember { BitmapFactory.decodeResource(context.resources, R.drawable.pet_ivy_photo) }
    // the photo seen through bigger and bigger pixels
    val pixelSteps = remember(photo) {
        listOf(48, 24, 14, 8).map { w ->
            Bitmap.createScaledBitmap(photo, w, (w * photo.height / photo.width.toFloat()).toInt().coerceAtLeast(1), true).asImageBitmap()
        }
    }
    val photoImage = remember(photo) { photo.asImageBitmap() }
    val ivy = remember(settings) { PetIvy.apply(settings) }

    // 0 intro, 1 photo, 2..5 pixelating, 6 pixel Ivy
    var phase by remember { mutableIntStateOf(0) }
    val photoScale = remember { Animatable(0.6f) }
    val photoAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(500)
        phase = 1
        photoAlpha.animateTo(1f, tween(500))
        photoScale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 200f))
        delay(1600)
        for (i in 2..5) {
            phase = i
            delay(260)
        }
        phase = 6
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SPACE_TOP, SPACE_BOTTOM)))
    ) {
        Starfield()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(50), color = GLOW.copy(alpha = 0.18f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = GLOW)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pet_ivy_badge), color = GLOW, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.pet_ivy_title),
                color = Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(fontFamily = GoogleSansRounded),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            // photo -> pixels -> pixel Ivy
            Box(
                Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                contentAlignment = Alignment.Center
            ) {
                if (phase in 1..5) {
                    val img = if (phase == 1) photoImage else pixelSteps[phase - 2]
                    Image(
                        bitmap = img,
                        contentDescription = stringResource(R.string.pet_ivy_photo_desc),
                        contentScale = ContentScale.Crop,
                        filterQuality = if (phase == 1) FilterQuality.Medium else FilterQuality.None,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(photoScale.value)
                            .alpha(photoAlpha.value)
                            .clip(RoundedCornerShape(28.dp))
                            .border(2.dp, GLOW.copy(alpha = 0.6f), RoundedCornerShape(28.dp))
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = phase >= 6,
                    enter = fadeIn(tween(400)) + androidx.compose.animation.scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.7f)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        PetSprite(
                            activity = PetActivity.IVY_SPACE,
                            settings = ivy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, GLOW.copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
                            boopable = true,
                            label = PetIvy.NAME,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(
                visible = phase >= 6,
                enter = fadeIn(tween(500, delayMillis = 200)) + slideInVertically(tween(500, delayMillis = 200, easing = FastOutSlowInEasing)) { it / 3 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 420.dp)) {
                    Text(
                        stringResource(R.string.pet_ivy_body),
                        color = Color.White.copy(alpha = 0.85f),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onAdopt,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = GLOW, contentColor = SPACE_TOP),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Rounded.Pets, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.pet_ivy_adopt), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onClose, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.pet_ivy_later), color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

/** Slowly twinkling stars behind everything. */
@Composable
private fun Starfield() {
    val stars = remember {
        val r = Random(7)
        List(70) { Triple(Offset(r.nextFloat(), r.nextFloat()), r.nextFloat() * 2.2f + 0.8f, r.nextFloat() * 6.28f) }
    }
    val t by rememberInfiniteTransition(label = "stars").animateFloat(
        initialValue = 0f, targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle"
    )
    Canvas(Modifier.fillMaxSize()) {
        stars.forEach { (pos, radius, phase) ->
            val a = 0.35f + 0.65f * ((sin(t + phase) + 1f) / 2f)
            drawCircle(Color.White.copy(alpha = a), radius = radius, center = Offset(pos.x * size.width, pos.y * size.height))
        }
    }
}
