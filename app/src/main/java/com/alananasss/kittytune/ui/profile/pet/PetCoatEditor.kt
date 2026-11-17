package com.alananasss.kittytune.ui.profile.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.theme.GoogleSansRounded
import com.alananasss.kittytune.ui.widget.pet.PetActivity
import com.alananasss.kittytune.ui.widget.pet.PetCoat
import com.alananasss.kittytune.ui.widget.pet.PetCoatPresets
import com.alananasss.kittytune.ui.widget.pet.PetCoatRenderer
import com.alananasss.kittytune.ui.widget.pet.PetHat
import com.alananasss.kittytune.ui.widget.pet.PetNeck
import com.alananasss.kittytune.ui.widget.pet.PetPattern
import com.alananasss.kittytune.ui.widget.pet.PetSettings
import kotlin.math.floor

/** Visible window of the 46×32 scene in the editor: the cat, zoomed. */
private const val VIEW_X = 11
private const val VIEW_Y = 3
private const val VIEW_SIZE = 28

/**
 * Pixel editor for the cat's coat: tap or drag on the sitting cat to paint coat, markings or white,
 * with an optional mirror for symmetric faces, undo and reset.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PetCoatEditor(
    settings: PetSettings,
    initial: PetCoat,
    onSave: (PetCoat) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var coat by remember { mutableStateOf(initial) }
    val history = remember { mutableStateListOf<PetCoat>() }
    var brush by remember { mutableStateOf('1') }
    var mirror by remember { mutableStateOf(false) }
    // sprite pixel -> (part, r, c) for the frame we paint on
    val cells = remember {
        val bytes = PetCoatRenderer.partmap(context)["idle_open"] ?: ByteArray(0)
        val map = HashMap<Int, Triple<Int, Int, Int>>()
        var i = 0
        while (i < bytes.size) {
            val x = bytes[i].toInt() and 0xFF
            val y = bytes[i + 1].toInt() and 0xFF
            map[y * 64 + x] = Triple(bytes[i + 2].toInt(), bytes[i + 3].toInt(), bytes[i + 4].toInt())
            i += 5
        }
        map
    }

    fun paint(px: Int, py: Int) {
        val cell = cells[py * 64 + px] ?: return
        val (part, r, c) = cell
        var next = coat
        fun apply(p: Int, rr: Int, cc: Int) {
            next = when (p) {
                PetCoatRenderer.PART_HEAD -> next.withHead(rr, cc, brush)
                PetCoatRenderer.PART_BODY -> next.withBody(rr, cc, brush)
                PetCoatRenderer.PART_TAIL -> next.withTail(rr, brush)
                else -> next
            }
        }
        apply(part, r, c)
        if (mirror) when (part) {
            PetCoatRenderer.PART_HEAD -> apply(part, r, PetCoatPresets.HEAD_W - 1 - c)
            PetCoatRenderer.PART_BODY -> apply(part, r, PetCoatPresets.BODY_W - 1 - c)
        }
        if (next != coat) coat = next
    }

    BackHandler(onBack = onClose)

    // accessories off so every coat pixel can be reached
    val look = settings.copy(pattern = PetPattern.CUSTOM, coat = coat, roomDecor = false, hat = PetHat.NONE, neck = PetNeck.NONE, seasonal = false, holidays = false)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                }
                Text(
                    stringResource(R.string.pet_editor_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansRounded),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { if (history.isNotEmpty()) coat = history.removeAt(history.lastIndex) },
                    enabled = history.isNotEmpty(),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.pet_editor_undo))
                }
                IconButton(onClick = { history.add(coat); coat = initial }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.pet_editor_reset))
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.pet_editor_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                BoxWithConstraints(
                    Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFFE8F5EC))
                ) {
                    val sidePx = constraints.maxWidth.toFloat()
                    fun toPixel(o: Offset): Pair<Int, Int> =
                        floor(VIEW_X + o.x / sidePx * VIEW_SIZE).toInt() to floor(VIEW_Y + o.y / sidePx * VIEW_SIZE).toInt()

                    // the cat, zoomed on the painting window
                    Layout(content = {
                        PetSprite(PetActivity.IDLE, look, corner = 0, animate = false)
                    }) { measurables, constraints ->
                        val side = constraints.maxWidth
                        val w = side * 46 / VIEW_SIZE
                        val h = w * 32 / 46
                        val p = measurables.first().measure(Constraints.fixed(w, h))
                        layout(side, side) {
                            p.place(-side * VIEW_X / VIEW_SIZE, -side * VIEW_Y / VIEW_SIZE)
                        }
                    }
                    // pixel grid + touch painting
                    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(brush, mirror) {
                                detectTapGestures { o ->
                                    history.add(coat)
                                    val (x, y) = toPixel(o); paint(x, y)
                                }
                            }
                            .pointerInput(brush, mirror) {
                                detectDragGestures(
                                    onDragStart = { o -> history.add(coat); val (x, y) = toPixel(o); paint(x, y) },
                                    onDrag = { change, _ -> val (x, y) = toPixel(change.position); paint(x, y) }
                                )
                            }
                    ) {
                        val cell = size.width / VIEW_SIZE
                        for (i in 0..VIEW_SIZE) {
                            drawLine(gridColor, Offset(i * cell, 0f), Offset(i * cell, size.height), 1f)
                            drawLine(gridColor, Offset(0f, i * cell), Offset(size.width, i * cell), 1f)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrushChip(stringResource(R.string.pet_brush_coat), Color(settings.palette.fur), brush == '0') { brush = '0' }
                    BrushChip(stringResource(R.string.pet_brush_mark), Color(settings.palette.mark), brush == '1') { brush = '1' }
                    BrushChip(stringResource(R.string.pet_brush_white), Color(settings.palette.white), brush == '2') { brush = '2' }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconToggleButton(checked = mirror, onCheckedChange = { mirror = it }, shapes = IconButtonDefaults.toggleableShapes()) {
                        Icon(Icons.Rounded.Flip, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pet_editor_mirror), style = MaterialTheme.typography.bodyLarge)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                OutlinedButton(onClick = onClose, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.pet_cancel))
                }
                Button(onClick = { onSave(coat) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Check, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pet_editor_done))
                }
            }
        }
    }
}

@Composable
private fun BrushChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
