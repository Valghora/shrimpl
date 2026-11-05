package com.valghora.shrimpl.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The camera options a file input gets (docs/TEMPLATE_SPEC.md T6.4). */
class CaptureChoiceTest {
    @Test
    fun anInputForAnythingOffersAPhoto() {
        assertEquals(CaptureChoice(photo = true, video = false, direct = false), CaptureChoice.of(emptyList(), capture = false))
        assertEquals(CaptureChoice(photo = true, video = false, direct = false), CaptureChoice.of(listOf("", "*/*"), capture = false))
    }

    @Test
    fun imagesAndVideoAreRecognisedByTypeOrExtension() {
        assertEquals(CaptureChoice(photo = true, video = false, direct = false), CaptureChoice.of(listOf("image/*"), capture = false))
        assertEquals(CaptureChoice(photo = true, video = false, direct = false), CaptureChoice.of(listOf(".JPG"), capture = false))
        assertEquals(CaptureChoice(photo = false, video = true, direct = false), CaptureChoice.of(listOf("video/mp4"), capture = false))
        assertEquals(CaptureChoice(photo = true, video = true, direct = false), CaptureChoice.of(listOf("image/png, .mov"), capture = false))
    }

    @Test
    fun otherFilesOfferNoCamera() {
        val choice = CaptureChoice.of(listOf("application/pdf", ".csv"), capture = true)
        assertEquals(CaptureChoice(photo = false, video = false, direct = false), choice)
        assertFalse(choice.any)
    }

    @Test
    fun captureOpensTheCameraDirectly() {
        assertEquals(CaptureChoice(photo = true, video = false, direct = true), CaptureChoice.of(listOf("image/*"), capture = true))
        assertEquals(CaptureChoice(photo = false, video = true, direct = true), CaptureChoice.of(listOf("video/*"), capture = true))
    }
}
