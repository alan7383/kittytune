package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Help
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Redo
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Report
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Send
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Translate
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.icon.extended.VolumeOff
import top.yukonga.miuix.kmp.icon.extended.VolumeUp
import androidx.compose.material3.Checkbox as M3Checkbox
import androidx.compose.material3.Icon as M3Icon
import androidx.compose.material3.RadioButton as M3RadioButton
import androidx.compose.material3.TriStateCheckbox as M3TriStateCheckbox

// --- Checkbox & radio ------------------------------------------------------------------------

@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (miuixEnabled(MiuixComponent.CHECKBOXES)) {
        MiuixMaterialCheckbox(ToggleableState(checked), onCheckedChange?.let { { it(!checked) } }, modifier, enabled)
        return
    }
    M3Checkbox(checked, onCheckedChange, modifier, enabled, colors, interactionSource)
}

@Composable
fun TriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = CheckboxDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (miuixEnabled(MiuixComponent.CHECKBOXES)) {
        MiuixMaterialCheckbox(state, onClick, modifier, enabled)
        return
    }
    M3TriStateCheckbox(state, onClick, modifier, enabled, colors, interactionSource)
}

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (miuixEnabled(MiuixComponent.CHECKBOXES)) {
        MiuixMaterialRadio(selected, onClick, modifier, enabled)
        return
    }
    M3RadioButton(selected, onClick, modifier, enabled, colors, interactionSource)
}

// --- Icons -----------------------------------------------------------------------------------

/**
 * Material icon → MIUIX icon, by the icon's base name ("Rounded.Home" and "Filled.Home" are both
 * "Home"). Only shapes with a true HyperOS counterpart are mapped; everything else stays Material.
 */
private val MiuixIconMap: Map<String, () -> ImageVector> = mapOf(
    "Home" to { MiuixIcons.Home },
    "Search" to { MiuixIcons.Search },
    "Settings" to { MiuixIcons.Settings },
    "Share" to { MiuixIcons.Share },
    "Delete" to { MiuixIcons.Delete },
    "DeleteOutline" to { MiuixIcons.Delete },
    "Edit" to { MiuixIcons.Edit },
    "Add" to { MiuixIcons.Add },
    "Close" to { MiuixIcons.Close },
    "Clear" to { MiuixIcons.Clear },
    "MoreVert" to { MiuixIcons.More },
    "MoreHoriz" to { MiuixIcons.More },
    "Favorite" to { MiuixIcons.FavoritesFill },
    "FavoriteBorder" to { MiuixIcons.Favorites },
    "PlayArrow" to { MiuixIcons.Play },
    "Pause" to { MiuixIcons.Pause },
    "ArrowBack" to { MiuixIcons.Back },
    "ArrowBackIos" to { MiuixIcons.ChevronBackward },
    "ArrowBackIosNew" to { MiuixIcons.ChevronBackward },
    "ArrowForwardIos" to { MiuixIcons.ChevronForward },
    "ChevronRight" to { MiuixIcons.ChevronForward },
    "ChevronLeft" to { MiuixIcons.ChevronBackward },
    "KeyboardArrowDown" to { MiuixIcons.ExpandMore },
    "KeyboardArrowUp" to { MiuixIcons.ExpandLess },
    "ExpandMore" to { MiuixIcons.ExpandMore },
    "ExpandLess" to { MiuixIcons.ExpandLess },
    "Download" to { MiuixIcons.Download },
    "FileDownload" to { MiuixIcons.Download },
    "Info" to { MiuixIcons.Info },
    "Refresh" to { MiuixIcons.Refresh },
    "Sort" to { MiuixIcons.Sort },
    "FilterList" to { MiuixIcons.Filter },
    "Tune" to { MiuixIcons.Tune },
    "ContentCopy" to { MiuixIcons.Copy },
    "Link" to { MiuixIcons.Link },
    "Lock" to { MiuixIcons.Lock },
    "Mic" to { MiuixIcons.Mic },
    "QueueMusic" to { MiuixIcons.Playlist },
    "PlaylistPlay" to { MiuixIcons.Playlist },
    "MusicNote" to { MiuixIcons.Music },
    "Album" to { MiuixIcons.Album },
    "Folder" to { MiuixIcons.Folder },
    "Image" to { MiuixIcons.Image },
    "Photo" to { MiuixIcons.Photos },
    "PhotoLibrary" to { MiuixIcons.Photos },
    "Timer" to { MiuixIcons.Timer },
    "VolumeUp" to { MiuixIcons.VolumeUp },
    "VolumeOff" to { MiuixIcons.VolumeOff },
    "Check" to { MiuixIcons.Ok },
    "Done" to { MiuixIcons.Ok },
    "Undo" to { MiuixIcons.Undo },
    "Redo" to { MiuixIcons.Redo },
    "Send" to { MiuixIcons.Send },
    "Visibility" to { MiuixIcons.Show },
    "VisibilityOff" to { MiuixIcons.Hide },
    "CloudUpload" to { MiuixIcons.UploadCloud },
    "Upload" to { MiuixIcons.UploadCloud },
    "Translate" to { MiuixIcons.Translate },
    "History" to { MiuixIcons.Recent },
    "Help" to { MiuixIcons.Help },
    "HelpOutline" to { MiuixIcons.Help },
    "Person" to { MiuixIcons.Contacts },
    "Report" to { MiuixIcons.Report },
    "GridView" to { MiuixIcons.GridView },
    "ViewList" to { MiuixIcons.ListView },
    "Email" to { MiuixIcons.Email },
    "PushPin" to { MiuixIcons.Pin },
    "QrCodeScanner" to { MiuixIcons.Scan },
    "SelectAll" to { MiuixIcons.SelectAll },
    "Remove" to { MiuixIcons.Remove },
    "Backup" to { MiuixIcons.Backup },
)

private val iconCache = HashMap<String, ImageVector?>()

private fun miuixIconFor(vector: ImageVector): ImageVector? {
    val base = vector.name.substringAfterLast('.')
    return iconCache.getOrPut(vector.name) { MiuixIconMap[base]?.invoke() }
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val settings = LocalMiuixSettings.current
    val vector = if (settings.enabled && settings.miuixIcons) miuixIconFor(imageVector) ?: imageVector else imageVector
    M3Icon(vector, contentDescription, modifier, tint)
}

@Composable
fun Icon(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) = M3Icon(bitmap, contentDescription, modifier, tint)

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) = M3Icon(painter, contentDescription, modifier, tint)

/** The MIUIX counterpart of [vector] when MIUIX icons are on, else [vector] itself. */
@Composable
fun miuixIconOrSelf(vector: ImageVector): ImageVector {
    val settings = LocalMiuixSettings.current
    return if (settings.enabled && settings.miuixIcons) miuixIconFor(vector) ?: vector else vector
}
