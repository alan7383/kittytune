package com.alananasss.kittytune.ui.profile.recap

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.RecapKind
import com.alananasss.kittytune.data.stats.RecapPeriod
import com.alananasss.kittytune.data.stats.RecapPersona
import com.alananasss.kittytune.data.stats.Season
import java.time.format.TextStyle
import java.util.Locale

/**
 * A recap's colours: a container and what sits on it, an accent for figures and shapes, and the
 * colour that sits on the accent. Always Material pairs, so text and icons keep their contrast in
 * every theme and wallpaper.
 */
internal data class RecapPalette(val container: Color, val onContainer: Color, val accent: Color, val onAccent: Color)

/** What goes on the current slide's accent: the rank-1 badge, the intro and persona icons, the active day/night badge. */
internal val LocalRecapOnAccent = androidx.compose.runtime.compositionLocalOf { Color.Unspecified }

/**
 * Each kind of recap has its own colour role, so a row of them reads at a glance: months on the
 * primary container, each season on its own, the year on the full-strength primary as the headline.
 * Roles rather than fixed colours, so it follows the wallpaper and the dark theme like the rest of the app.
 */
internal fun RecapPeriod.palette(scheme: ColorScheme): RecapPalette = when {
    kind == RecapKind.YEAR -> RecapPalette(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer)
    kind == RecapKind.MONTH -> RecapPalette(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary)
    season == Season.SPRING -> RecapPalette(scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary, scheme.onTertiary)
    // Summer on the full-strength secondary: its container sat too close to the dark background to
    // read as a card at all.
    season == Season.SUMMER -> RecapPalette(scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer)
    season == Season.AUTUMN -> RecapPalette(scheme.tertiary, scheme.onTertiary, scheme.tertiaryContainer, scheme.onTertiaryContainer)
    // Winter: the inverse surface — crisp light on a near-black row, like snow on a dark morning.
    // surfaceContainerHighest washed out against the background and left the card hard to read.
    else -> RecapPalette(scheme.inverseSurface, scheme.inverseOnSurface, scheme.inversePrimary, scheme.inverseSurface)
}

/** The expressive shape that stands for each kind: a flower for spring, a sun for summer… */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun RecapPeriod.polygon(): RoundedPolygon = when {
    kind == RecapKind.YEAR -> MaterialShapes.Cookie12Sided
    kind == RecapKind.MONTH -> MaterialShapes.Cookie9Sided
    season == Season.SPRING -> MaterialShapes.Flower
    season == Season.SUMMER -> MaterialShapes.Sunny
    season == Season.AUTUMN -> MaterialShapes.Clover4Leaf
    else -> MaterialShapes.SoftBurst
}

internal fun RecapPeriod.icon(): ImageVector = when {
    kind == RecapKind.YEAR -> Icons.Rounded.Celebration
    kind == RecapKind.MONTH -> Icons.Rounded.CalendarMonth
    season == Season.SPRING -> Icons.Rounded.LocalFlorist
    season == Season.SUMMER -> Icons.Rounded.WbSunny
    season == Season.AUTUMN -> Icons.Rounded.Park
    else -> Icons.Rounded.AcUnit
}

/** "October 2026", "Summer 2026", "Winter 2025–26", "2026". */
@Composable
internal fun RecapPeriod.title(): String {
    val locale = Locale.getDefault()
    return when (kind) {
        RecapKind.MONTH -> start.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) } + " ${start.year}"
        RecapKind.SEASON -> {
            val name = stringResource(season.nameRes())
            if (end.year != start.year) "$name ${start.year}–${end.year % 100}" else "$name ${start.year}"
        }
        RecapKind.YEAR -> start.year.toString()
    }
}

/** "Monthly recap", "Seasonal recap", "Yearly recap". */
@Composable
internal fun RecapPeriod.kindLabel(): String = stringResource(
    when (kind) {
        RecapKind.MONTH -> R.string.recaps_kind_month
        RecapKind.SEASON -> R.string.recaps_kind_season
        RecapKind.YEAR -> R.string.recaps_kind_year
    }
)

internal fun Season?.nameRes(): Int = when (this) {
    Season.SPRING -> R.string.recaps_season_spring
    Season.SUMMER -> R.string.recaps_season_summer
    Season.AUTUMN -> R.string.recaps_season_autumn
    else -> R.string.recaps_season_winter
}

internal fun RecapPersona.titleRes(): Int = when (this) {
    RecapPersona.NIGHT_OWL -> R.string.recap_persona_night_owl
    RecapPersona.EARLY_BIRD -> R.string.recap_persona_early_bird
    RecapPersona.LOYAL_FAN -> R.string.recap_persona_loyal_fan
    RecapPersona.REPLAYER -> R.string.recap_persona_replayer
    RecapPersona.EXPLORER -> R.string.recap_persona_explorer
    RecapPersona.MARATHONER -> R.string.recap_persona_marathoner
    RecapPersona.DEVOTED -> R.string.recap_persona_devoted
    RecapPersona.ECLECTIC -> R.string.recap_persona_eclectic
}

internal fun RecapPersona.descriptionRes(): Int = when (this) {
    RecapPersona.NIGHT_OWL -> R.string.recap_persona_night_owl_desc
    RecapPersona.EARLY_BIRD -> R.string.recap_persona_early_bird_desc
    RecapPersona.LOYAL_FAN -> R.string.recap_persona_loyal_fan_desc
    RecapPersona.REPLAYER -> R.string.recap_persona_replayer_desc
    RecapPersona.EXPLORER -> R.string.recap_persona_explorer_desc
    RecapPersona.MARATHONER -> R.string.recap_persona_marathoner_desc
    RecapPersona.DEVOTED -> R.string.recap_persona_devoted_desc
    RecapPersona.ECLECTIC -> R.string.recap_persona_eclectic_desc
}

internal fun RecapPersona.icon(): ImageVector = when (this) {
    RecapPersona.NIGHT_OWL -> Icons.Rounded.Bedtime
    RecapPersona.EARLY_BIRD -> Icons.Rounded.WbTwilight
    RecapPersona.LOYAL_FAN -> Icons.Rounded.Favorite
    RecapPersona.REPLAYER -> Icons.Rounded.Repeat
    RecapPersona.EXPLORER -> Icons.Rounded.Explore
    RecapPersona.MARATHONER -> Icons.Rounded.DirectionsRun
    RecapPersona.DEVOTED -> Icons.Rounded.AllInclusive
    RecapPersona.ECLECTIC -> Icons.Rounded.Shuffle
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun RecapPersona.polygon(): RoundedPolygon = when (this) {
    RecapPersona.NIGHT_OWL -> MaterialShapes.Ghostish
    RecapPersona.EARLY_BIRD -> MaterialShapes.Sunny
    RecapPersona.LOYAL_FAN -> MaterialShapes.Heart
    RecapPersona.REPLAYER -> MaterialShapes.Cookie7Sided
    RecapPersona.EXPLORER -> MaterialShapes.Gem
    RecapPersona.MARATHONER -> MaterialShapes.Burst
    RecapPersona.DEVOTED -> MaterialShapes.Clover8Leaf
    RecapPersona.ECLECTIC -> MaterialShapes.PixelCircle
}

/**
 * Somewhere between two expressive shapes, for the story's morphing backdrop. The shapes are
 * normalised to the unit square, so scaling to the bounds is all it takes.
 */
internal class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morph.asCubics(progress).toPath(size))
}

private fun List<androidx.graphics.shapes.Cubic>.toPath(size: Size): Path {
    val path = Path()
    if (isEmpty()) return path
    path.moveTo(first().anchor0X, first().anchor0Y)
    for (cubic in this) {
        path.cubicTo(cubic.control0X, cubic.control0Y, cubic.control1X, cubic.control1Y, cubic.anchor1X, cubic.anchor1Y)
    }
    path.close()
    path.transform(Matrix().apply { scale(size.width, size.height) })
    return path
}

/** An expressive shape turning slowly, as a decoration behind a card or a slide. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpinningShape(
    polygon: RoundedPolygon,
    color: Color,
    modifier: Modifier = Modifier,
    periodMs: Int = 24_000,
    clockwise: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (clockwise) 360f else -360f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    val shape = polygon.toShape()
    Box(
        modifier
            .graphicsLayer { rotationZ = angle }
            .clip(shape)
            .background(color)
    )
}
