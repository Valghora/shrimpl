package com.valghora.shrimpl.icons

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.core.graphics.scale
import com.valghora.shrimpl.core.icons.IconException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/** Picked and downloaded icon images, normalized before they are stored (docs/SHRIMPL_SPEC.md R10.1). */
object IconImages {
    /** The largest side stored; icon layers are drawn at 432 px, so more only costs space and time. */
    const val MAX_PX = 512

    /** [bytes], in any format ImageDecoder reads, as a centered square PNG of at most [MAX_PX]. */
    fun squarePng(bytes: ByteArray): ByteArray {
        val bitmap = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: IOException) {
            throw IconException("This image can't be read", e)
        }
        return squarePng(bitmap)
    }

    /** [bitmap] center-cropped to a square PNG of at most [MAX_PX]; smaller images are not enlarged. */
    fun squarePng(bitmap: Bitmap): ByteArray {
        val side = minOf(bitmap.width, bitmap.height)
        val square = if (bitmap.width == bitmap.height) bitmap else Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val sized = if (side > MAX_PX) square.scale(MAX_PX, MAX_PX) else square
        return ByteArrayOutputStream().use { out ->
            sized.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }
}
