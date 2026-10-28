package com.valghora.wrapper.core.icons

import com.valghora.wrapper.core.discovery.Confidence
import com.valghora.wrapper.core.discovery.IconChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IconLayoutTest {
    @Test
    fun glyphFitMatchesTheOldGeneratorSpec() {
        // A 24x24 viewBox in the 108-unit canvas gets scale 2.25 and translate 27.
        val units = IconLayout.fit(0.0, 0.0, 24.0, 24.0, 108, IconLayout.GLYPH_FRACTION)
        assertEquals(IconLayout.Fit(2.25, 27.0, 27.0), units)
        // The same at 432 px: 4x.
        assertEquals(IconLayout.Fit(9.0, 108.0, 108.0), IconLayout.fit(ViewBox(0.0, 0.0, 24.0, 24.0), IconLayout.LAYER_PX, IconLayout.GLYPH_FRACTION))
    }

    @Test
    fun offsetAndWideBoxesAreCentered() {
        val fit = IconLayout.fit(10.0, -5.0, 40.0, 20.0, 100, 0.5)
        assertEquals(1.25, fit.scale, 1e-9)
        // The box's center (30, 5) lands on the canvas center (50, 50).
        assertEquals(50.0, 30 * fit.scale + fit.translateX, 1e-9)
        assertEquals(50.0, 5 * fit.scale + fit.translateY, 1e-9)
        assertThrows(IllegalArgumentException::class.java) { IconLayout.fit(0.0, 0.0, 0.0, 1.0, 10, 1.0) }
    }

    @Test
    fun safeZones() {
        assertEquals(360.0, IconLayout.LAYER_PX * IconLayout.RASTER_MASKABLE_FRACTION, 1e-9)
        assertEquals(264.0, IconLayout.LAYER_PX * IconLayout.RASTER_ANY_FRACTION, 1e-9)
        assertEquals(80.0, IconLayout.NOTIFICATION_PX * IconLayout.NOTIFICATION_LETTER_FRACTION, 1e-9)
    }

    @Test
    fun sourcesFromDiscoveryChoices() {
        val glyph = SvgGlyph(ViewBox(0.0, 0.0, 24.0, 24.0), "M 0 0 h 1 z", evenOdd = false)
        assertEquals(IconSource.Glyph(glyph), IconSource.from(IconChoice.Glyph(ByteArray(0), glyph, null, "", "", Confidence.HIGH)))
        val bytes = byteArrayOf(1, 2)
        val raster = IconSource.from(IconChoice.Raster(bytes, "png", 512, true, "", Confidence.MEDIUM)) as IconSource.Raster
        assertSame(bytes, raster.bytes)
        assertTrue(raster.maskable)
        assertEquals(IconSource.Letter('E'), IconSource.from(IconChoice.Letter('E', "", Confidence.LOW)))
    }
}
