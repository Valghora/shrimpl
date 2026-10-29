package com.valghora.shrimple.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import com.valghora.shrimple.core.build.IconSet
import com.valghora.shrimple.core.icons.IconException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/** Decoded icon layers for previews. */
class IconLayers(val background: ImageBitmap, val foreground: ImageBitmap, val monochrome: ImageBitmap)

object IconImages {
    fun layers(set: IconSet): IconLayers = IconLayers(decode(set.background), decode(set.foreground), decode(set.monochrome))

    private fun decode(png: ByteArray): ImageBitmap =
        requireNotNull(BitmapFactory.decodeByteArray(png, 0, png.size)) { "not an image" }.asImageBitmap()

    /**
     * A picked photo as a square PNG of [sizePx]: center-cropped to a square (R10.1), any format
     * ImageDecoder reads.
     */
    fun squarePng(bytes: ByteArray, sizePx: Int = 512): ByteArray {
        val source = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: IOException) {
            throw IconException("This image can't be read", e)
        }
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = square.scale(sizePx, sizePx)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }
}
