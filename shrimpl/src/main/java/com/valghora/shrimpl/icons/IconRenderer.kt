package com.valghora.shrimpl.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import com.valghora.shrimpl.core.build.IconSet
import com.valghora.shrimpl.core.icons.IconException
import com.valghora.shrimpl.core.icons.IconLayout
import com.valghora.shrimpl.core.icons.IconSource
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Draws a web app's icon layers as PNGs (docs/SHRIMPL_SPEC.md R10.3): adaptive background,
 * foreground and monochrome layers, and the white notification icon. Geometry comes from
 * [IconLayout]; glyph path data is already normalized for Android's PathParser.
 */
object IconRenderer {
    private const val WHITE = 0xFFFFFFFF.toInt()

    /**
     * @param background and [foreground] are ARGB colors; [letter] is used for monochrome and
     * notification icons when [source] is an image.
     */
    fun render(source: IconSource, background: Int, foreground: Int, letter: Char): IconSet {
        val size = IconLayout.LAYER_PX
        val backgroundLayer = createBitmap(size, size).apply { eraseColor(background) }
        val monochromeLayer = createBitmap(size, size)
        val notification = createBitmap(IconLayout.NOTIFICATION_PX, IconLayout.NOTIFICATION_PX)

        when (source) {
            is IconSource.Glyph -> {
                val path = glyphPath(source)
                val box = viewBoxRect(source)
                drawPath(monochromeLayer, path, box, IconLayout.GLYPH_FRACTION, WHITE)
                drawPath(notification, path, box, IconLayout.NOTIFICATION_GLYPH_FRACTION, WHITE)
            }
            is IconSource.Raster -> {
                drawLetter(monochromeLayer, letter, IconLayout.LETTER_FRACTION, WHITE)
                drawLetter(notification, letter, IconLayout.NOTIFICATION_LETTER_FRACTION, WHITE)
            }
            is IconSource.Letter -> {
                drawLetter(monochromeLayer, source.letter, IconLayout.LETTER_FRACTION, WHITE)
                drawLetter(notification, source.letter, IconLayout.NOTIFICATION_LETTER_FRACTION, WHITE)
            }
        }
        return IconSet(
            background = png(backgroundLayer),
            foreground = png(foreground(source, foreground)),
            monochrome = png(monochromeLayer),
            notification = png(notification),
        )
    }

    /**
     * Just the adaptive foreground layer ([IconLayout.LAYER_PX] square, transparent around the icon),
     * as previews draw it over the background color; no PNGs are made.
     */
    fun foreground(source: IconSource, foreground: Int): Bitmap {
        val size = IconLayout.LAYER_PX
        val layer = createBitmap(size, size)
        when (source) {
            is IconSource.Glyph -> drawPath(layer, glyphPath(source), viewBoxRect(source), IconLayout.GLYPH_FRACTION, foreground)
            is IconSource.Raster ->
                drawImage(layer, source.bytes, if (source.maskable) IconLayout.RASTER_MASKABLE_FRACTION else IconLayout.RASTER_ANY_FRACTION)
            is IconSource.Letter -> drawLetter(layer, source.letter, IconLayout.LETTER_FRACTION, foreground)
        }
        return layer
    }

    private val Double.f get() = toFloat()

    private fun viewBoxRect(source: IconSource.Glyph) =
        source.glyph.viewBox.let { RectF(it.minX.f, it.minY.f, (it.minX + it.width).f, (it.minY + it.height).f) }

    private fun glyphPath(source: IconSource.Glyph): Path = try {
        PathParser.createPathFromPathData(source.glyph.pathData).apply {
            fillType = if (source.glyph.evenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
        }
    } catch (e: RuntimeException) {
        throw IconException("The glyph's path data could not be read", e)
    }

    /** Fills [path] in [color], fitting [box] (in path units) into [fraction] of the bitmap, centered. */
    private fun drawPath(bitmap: Bitmap, path: Path, box: RectF, fraction: Double, color: Int) {
        val fit = IconLayout.fit(box.left.toDouble(), box.top.toDouble(), box.width().toDouble(), box.height().toDouble(), bitmap.width, fraction)
        val matrix = Matrix().apply {
            setScale(fit.scale.f, fit.scale.f)
            postTranslate(fit.translateX.f, fit.translateY.f)
        }
        val placed = Path(path).apply { transform(matrix) }
        Canvas(bitmap).drawPath(placed, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    /** The letter's outline in the system bold font, fitted by its own bounds. */
    private fun drawLetter(bitmap: Bitmap, letter: Char, fraction: Double, color: Int) {
        val text = letter.uppercaseChar().toString()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 200f
        }
        val path = Path()
        paint.getTextPath(text, 0, text.length, 0f, 0f, path)
        val bounds = RectF()
        path.computeBounds(bounds)
        if (bounds.isEmpty) throw IconException("No glyph for '$text' in the system font")
        drawPath(bitmap, path, bounds, fraction, color)
    }

    /** Draws an image into the central square that is [fraction] of the bitmap, keeping its aspect ratio. */
    private fun drawImage(bitmap: Bitmap, bytes: ByteArray, fraction: Double) {
        val image = try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: IOException) {
            throw IconException("The image could not be decoded", e)
        }
        val side = bitmap.width * fraction
        val scale = side / maxOf(image.width, image.height)
        val w = image.width * scale
        val h = image.height * scale
        val left = (bitmap.width - w) / 2
        val top = (bitmap.height - h) / 2
        Canvas(bitmap).drawBitmap(image, null, RectF(left.f, top.f, (left + w).f, (top + h).f), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        image.recycle()
    }

    private fun png(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        out.toByteArray()
    }
}
