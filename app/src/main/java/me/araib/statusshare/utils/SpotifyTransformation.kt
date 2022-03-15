package me.araib.statusshare.utils

import android.animation.ArgbEvaluator
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import androidx.core.graphics.drawable.toBitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.charset.Charset
import java.security.MessageDigest


class SpotifyTransformation(
    private val backgroundTint: Int,
    private val foregroundTint: Int
) : BitmapTransformation() {
    private val ID = "com.bumptech.glide.transformations.SpotifyTransformation"
    private val ID_BYTES: ByteArray = ID.toByteArray(Charset.forName("UTF-8"))

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        val toTransform = replaceColor(toTransform, Color.BLACK, Color.TRANSPARENT)

        val backgroundDrawable = ColorDrawable(backgroundTint).toBitmap(
            toTransform.width,
            toTransform.height,
            Bitmap.Config.ARGB_8888
        )
        val foregroundDrawable = ColorDrawable(foregroundTint).toBitmap(
            toTransform.width,
            toTransform.height,
            Bitmap.Config.ARGB_8888
        )
        val result =
            Bitmap.createBitmap(toTransform.width, toTransform.height, Bitmap.Config.ARGB_8888)

        val mCanvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        mCanvas.drawBitmap(foregroundDrawable, 0F, 0F, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        mCanvas.drawBitmap(toTransform, 0F, 0F, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_ATOP)
        mCanvas.drawBitmap(backgroundDrawable, 0F, 0F, paint)

        return result
    }

    private fun replaceColor(src: Bitmap, fromColor: Int, targetColor: Int): Bitmap {
        // Source image size
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        //get pixels
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        for (x in pixels.indices) {
            pixels[x] = when {
                pixels[x] == fromColor -> targetColor
                pixels[x] similarTo fromColor -> {
                    pixels[x] tintWith targetColor
                }
                else -> pixels[x]
            }
        }
        // create result bitmap output
        val result = Bitmap.createBitmap(width, height, src.config)
        //set pixels
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    private infix fun Int.tintWith(other: Int): Int =
        ArgbEvaluator().evaluate(1F, this, other) as Int

    private infix fun Int.similarTo(other: Int): Boolean {
        val percentage = 0.8
        val currentColor = Color.valueOf(this)
        val otherColor = Color.valueOf(other)
        return currentColor.blue() difference otherColor.blue() < percentage
                || currentColor.red() difference otherColor.red() < percentage
                || currentColor.green() difference otherColor.green() < percentage
    }

    private infix fun Float.difference(other: Float): Float =
        if (this > other) this - other else other - this

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is SpotifyTransformation -> {
                true
            }
            else -> super.equals(other)
        }
    }

    override fun hashCode(): Int {
        return ID.hashCode()
    }
}