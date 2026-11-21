package com.alananasss.kittytune.ui.player.cover

import android.content.Context
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

@UnstableApi
internal fun videoRenderers(context: Context): DefaultRenderersFactory =
    DefaultRenderersFactory(context).setMediaCodecSelector { mimeType, secure, tunneling ->
        val decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
        if (MimeTypes.isVideo(mimeType) && decoders.any { it.name.startsWith("c2.goldfish.") }) {
            // Goldfish can report a format as supported but fail when it receives the first buffer.
            decoders.sortedBy { it.name.startsWith("c2.goldfish.") }
        } else {
            decoders
        }
    }
