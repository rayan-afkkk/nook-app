package com.nook.app.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class CompressedImage(val bytes: ByteArray, val width: Int, val height: Int)

/** Downscales to [maxDim] and re-encodes as JPEG so uploads stay small and fast. */
object ImageCompressor {

    fun decode(context: Context, uri: Uri, maxDim: Int): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width; val h = info.size.height
                val scale = maxDim.toFloat() / max(w, h)
                if (scale < 1f) decoder.setTargetSize((w * scale).roundToInt(), (h * scale).roundToInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: error("Couldn't read that image")
            val scale = maxDim.toFloat() / max(bmp.width, bmp.height)
            if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).roundToInt(), (bmp.height * scale).roundToInt(), true) else bmp
        }
    }

    fun compress(context: Context, uri: Uri, maxDim: Int = 1600, quality: Int = 82): CompressedImage {
        val bmp = decode(context, uri, maxDim)
        return encode(bmp, quality)
    }

    fun encode(bmp: Bitmap, quality: Int = 85): CompressedImage {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return CompressedImage(out.toByteArray(), bmp.width, bmp.height)
    }
}
