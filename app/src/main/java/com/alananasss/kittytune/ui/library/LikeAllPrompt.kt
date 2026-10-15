package com.alananasss.kittytune.ui.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.domain.Track

/**
 * "Like all songs" asks first (desktop PR #77): a record often ends with a minute of silence, a thirty-second
 * voice note or a twenty-minute drone, and none of those are songs anybody means to like. The prompt is shared,
 * so both places that offer the action (the album's menu on its page, the library's playlist menu) ask the
 * same question from a single host.
 */
object LikeAllPrompt {
    /** Shortest and longest a track can be and still be kept when the reader skips the odd ones. */
    private const val MIN_MS = 60_000L
    private const val MAX_MS = 20 * 60_000L

    var pending by mutableStateOf<List<Track>?>(null)
        private set

    fun ask(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        pending = tracks
    }

    internal fun dismiss() {
        pending = null
    }

    /** The tracks a reader who skips the odd ones would like: anything with a known length between the two bounds. */
    internal fun sensible(tracks: List<Track>): List<Track> = tracks.filter { track ->
        val ms = track.durationMs ?: return@filter true
        ms in MIN_MS..MAX_MS
    }

    /** Likes [tracks] and toasts how many went in. */
    internal fun like(tracks: List<Track>): Int {
        val liked = LikeRepository.addLikesBulk(tracks)
        return liked
    }
}

/** Put once near the top of the window; shows the prompt whenever [LikeAllPrompt.ask] has been called. */
@Composable
fun LikeAllPromptHost() {
    val tracks = LikeAllPrompt.pending ?: return
    val context = LocalContext.current
    val sensible = LikeAllPrompt.sensible(tracks)

    fun toast(liked: Int) {
        android.widget.Toast.makeText(
            context,
            context.getString(
                if (liked > 0) R.string.toast_like_all_done else R.string.toast_like_all_nothing,
                liked
            ),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    AlertDialog(
        onDismissRequest = { LikeAllPrompt.dismiss() },
        title = { Text(stringResource(R.string.like_all_prompt_title)) },
        text = { Text(stringResource(R.string.like_all_prompt_text, tracks.size, tracks.size - sensible.size)) },
        confirmButton = {
            FilledTonalButton(
                onClick = {
                    LikeAllPrompt.dismiss()
                    toast(LikeAllPrompt.like(sensible))
                },
                shapes = ButtonDefaults.shapes(),
                enabled = sensible.isNotEmpty() && sensible.size < tracks.size,
            ) { Text(stringResource(R.string.like_all_prompt_skip_odd)) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    LikeAllPrompt.dismiss()
                    toast(LikeAllPrompt.like(tracks))
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.like_all_prompt_all)) }
        },
    )
}
