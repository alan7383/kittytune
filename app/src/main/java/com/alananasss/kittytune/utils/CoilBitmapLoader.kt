package com.alananasss.kittytune.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.SettableFuture
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean = true
    
    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch(Dispatchers.IO) {
            val bmp = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bmp != null) future.set(bmp) else future.setException(Exception("Decode failed"))
        }
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context).data(uri).allowHardware(false).build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    future.set((result.drawable as BitmapDrawable).bitmap)
                } else {
                    future.setException(Exception("Failed to load bitmap"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
