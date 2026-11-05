package com.valghora.shrimpl.core

/**
 * The camera options a page's file input gets (docs/TEMPLATE_SPEC.md T6.4): a photo when it takes
 * images, or names no type, and a video when it takes video. With the input's `capture` attribute the
 * camera opens [direct]ly instead of beside the file picker.
 */
data class CaptureChoice(val photo: Boolean, val video: Boolean, val direct: Boolean) {
    val any: Boolean get() = photo || video

    companion object {
        private val PHOTO_EXTENSIONS = setOf(".jpg", ".jpeg", ".png", ".heic", ".heif", ".webp", ".gif")
        private val VIDEO_EXTENSIONS = setOf(".mp4", ".mov", ".webm", ".3gp", ".mkv")

        /**
         * The choice for an input that accepts [acceptTypes] (as WebView reports them: MIME types,
         * wildcards or extensions, sometimes several in one comma-separated entry), with [capture] set
         * when the page asks for the camera.
         */
        fun of(acceptTypes: List<String>, capture: Boolean): CaptureChoice {
            val types = acceptTypes.flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            val anything = types.isEmpty() || "*/*" in types
            val photo = anything || types.any { it.startsWith("image/") || it in PHOTO_EXTENSIONS }
            val video = types.any { it.startsWith("video/") || it in VIDEO_EXTENSIONS }
            return CaptureChoice(photo, video, direct = capture && (photo || video))
        }
    }
}
