    package com.alananasss.kittytune.ui.profile

    import android.graphics.Bitmap
    import android.graphics.Matrix
    import android.graphics.RectF
    import kotlin.math.max
    import kotlin.math.min

    object BitmapUtils {

        /**
         * Crops the bitmap based on the visual transformation applied in the UI.
         *
         * @param source The original bitmap.
         * @param cropRect The visible cropping area in screen coordinates (pixels).
         * @param imageState Current transformation state (zoom, pan X, pan Y).
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
            targetWidth: Int = 2048,
            targetHeight: Int = 2048
        ): Bitmap {
            val matrix = Matrix()

            val imageWidth = source.width.toFloat()
            val imageHeight = source.height.toFloat()

            // 1. Replicate UI display logic (ContentScale.Fit + Center)
            val scaleX = viewWidth / imageWidth
            val scaleY = viewHeight / imageHeight
            val baseScale = min(scaleX, scaleY) // Initial "Fit" scale

            // Initial centering
            matrix.postTranslate((viewWidth - imageWidth) / 2f, (viewHeight - imageHeight) / 2f)

            // Apply base scale from center
            matrix.postScale(baseScale, baseScale, viewWidth / 2f, viewHeight / 2f)

            // Apply user transformations (Zoom/Pan) from center
            matrix.postScale(imageState.scale, imageState.scale, viewWidth / 2f, viewHeight / 2f)
            matrix.postTranslate(imageState.offsetX, imageState.offsetY)

            // 2. Invert matrix to map screen cropRect back to source image coordinates
            val inverseMatrix = Matrix()
            matrix.invert(inverseMatrix)

            val srcRect = RectF(cropRect)
            inverseMatrix.mapRect(srcRect)

            // 3. Clamp bounds to avoid out-of-bounds exceptions
            val left = max(0f, srcRect.left)
            val top = max(0f, srcRect.top)
            val width = min(srcRect.width(), imageWidth - left)
            val height = min(srcRect.height(), imageHeight - top)

            // If selection is invalid, return a safe fallback (scaled original)
            if (width <= 0 || height <= 0) return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)

            // 4. Create the cropped bitmap
            val cropped = Bitmap.createBitmap(
                source,
                left.toInt(),
                top.toInt(),
                width.toInt(),
                height.toInt()
            )

            // 5. Resize to target output size (2048x2048 avatar, 1240x260 banner)
            return Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
        }
    }

    data class ImageState(
        val scale: Float = 1f,
        val offsetX: Float = 0f,
        val offsetY: Float = 0f
    )

