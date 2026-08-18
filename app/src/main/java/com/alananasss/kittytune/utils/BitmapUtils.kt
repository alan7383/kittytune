package com.alananasss.kittytune.ui.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

object BitmapUtils {

    /**
     * Crops the bitmap based on the visual transformation applied in the UI.
     *
     * @param source The original bitmap.
     * @param cropRect The visible cropping area in screen coordinates (pixels).
     * @param imageState Current transformation state (zoom, pan X, pan Y, rotation).
     * @param viewWidth Width of the image display area.
     * @param viewHeight Height of the image display area.
     * @param targetWidth Final output width in pixels.
     * @param targetHeight Final output height in pixels.
     */
    fun cropBitmap(
        source: Bitmap,
        cropRect: RectF,
        imageState: ImageState,
        viewWidth: Float,
        viewHeight: Float,
        targetWidth: Int = 1400,
        targetHeight: Int = 1400
    ): Bitmap {
        val matrix = Matrix()
        val imageWidth = source.width.toFloat()
        val imageHeight = source.height.toFloat()
        val cropSize = cropRect.width()

        // Base scale fills the crop window completely
        val baseScale = cropSize / min(imageWidth, imageHeight)

        // 1. Center in view
        matrix.postTranslate((viewWidth - imageWidth) / 2f, (viewHeight - imageHeight) / 2f)

        // 2. Base scale around view center
        matrix.postScale(baseScale, baseScale, viewWidth / 2f, viewHeight / 2f)

        // 3. User transformations (Zoom, Rotation, Pan) around view center
        matrix.postScale(imageState.scale, imageState.scale, viewWidth / 2f, viewHeight / 2f)
        if (imageState.rotation != 0f) {
            matrix.postRotate(imageState.rotation, viewWidth / 2f, viewHeight / 2f)
        }
        matrix.postTranslate(imageState.offsetX, imageState.offsetY)

        // 4. Map the cropRect area to the output bitmap [0..targetWidth, 0..targetHeight]
        val outputMatrix = Matrix(matrix)
        outputMatrix.postTranslate(-cropRect.left, -cropRect.top)
        val outScaleX = targetWidth.toFloat() / cropRect.width()
        val outScaleY = targetHeight.toFloat() / cropRect.height()
        outputMatrix.postScale(outScaleX, outScaleY)

        val outputBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, outputMatrix, paint)

        return outputBitmap
    }
}

data class ImageState(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f
)
