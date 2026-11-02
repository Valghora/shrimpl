package com.valghora.shrimpl.core.icons

import com.valghora.shrimpl.core.discovery.IconChoice

/** What a web app's icon is drawn from (docs/SHRIMPL_SPEC.md R10.1). */
sealed interface IconSource {
    /** A single-color glyph (simple-icons, mask-icon, or an SVG the user picked). */
    data class Glyph(val glyph: SvgGlyph) : IconSource

    /** A bitmap image (site icon, photo, icon pack); [maskable] means it already fills the icon shape. */
    class Raster(val bytes: ByteArray, val maskable: Boolean) : IconSource

    /** A letter or digit on the background color. */
    data class Letter(val letter: Char) : IconSource

    companion object {
        fun from(choice: IconChoice): IconSource = when (choice) {
            is IconChoice.Glyph -> Glyph(choice.glyph)
            is IconChoice.Raster -> Raster(choice.bytes, choice.maskable)
            is IconChoice.Letter -> Letter(choice.letter)
        }
    }
}

/**
 * Geometry of the generated icons (docs/SHRIMPL_SPEC.md R10.3): adaptive layers are
 * 108dp drawn at 432 px; the launcher shows the central 72dp, and the safe zone is 66dp.
 */
object IconLayout {
    const val LAYER_PX = 432
    const val NOTIFICATION_PX = 96

    /** A glyph's viewBox fills 54 of 108 units (scale 2.25 for a 24-unit glyph). */
    const val GLYPH_FRACTION = 54.0 / 108

    /** A letter's outline fills 40 of 108 units. */
    const val LETTER_FRACTION = 40.0 / 108

    /** A maskable image at 90 of 108 units maps its 80 percent safe circle onto the 72-unit visible area. */
    const val RASTER_MASKABLE_FRACTION = 90.0 / 108

    /** Any other image fits the 66-unit safe zone. */
    const val RASTER_ANY_FRACTION = 66.0 / 108

    /** In the 24-unit notification icon, a glyph fills the icon and a letter 20 units. */
    const val NOTIFICATION_GLYPH_FRACTION = 1.0
    const val NOTIFICATION_LETTER_FRACTION = 20.0 / 24

    /** Scale and translation that put a box into a square [canvasPx] wide, centered, its larger side [fraction] of it. */
    data class Fit(val scale: Double, val translateX: Double, val translateY: Double)

    /**
     * Fits the box at ([minX], [minY]), [width] by [height] into a square
     * [canvasPx] wide: centered, its larger side [fraction] of the square.
     */
    fun fit(minX: Double, minY: Double, width: Double, height: Double, canvasPx: Int, fraction: Double): Fit {
        require(width > 0 && height > 0) { "empty box" }
        val scale = canvasPx * fraction / maxOf(width, height)
        val center = canvasPx / 2.0
        return Fit(scale, center - (minX + width / 2) * scale, center - (minY + height / 2) * scale)
    }

    /** Fits [viewBox] into a square [canvasPx] wide, as the other [fit] does. */
    fun fit(viewBox: ViewBox, canvasPx: Int, fraction: Double) =
        fit(viewBox.minX, viewBox.minY, viewBox.width, viewBox.height, canvasPx, fraction)
}
