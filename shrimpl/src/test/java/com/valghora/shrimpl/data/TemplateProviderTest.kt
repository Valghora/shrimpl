package com.valghora.shrimpl.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The bundled template is copied out once per install of Shrimpl (docs/SHRIMPL_SPEC.md R8.1): a copy
 * left by an earlier build with the same template version is never used.
 */
@RunWith(RobolectricTestRunner::class)
class TemplateProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun anEarlierBuildsCopyOfTheSameVersionIsReplaced() {
        val provider = TemplateProvider(context)
        val dir = File(context.noBackupFilesDir, "template").apply { mkdirs() }
        // What an earlier build of the same template version left: the old naming, by version alone.
        val stale = File(dir, "template-${provider.version}.apk").apply { writeText("an older template") }

        val file = provider.file()

        assertFalse("the earlier build's copy is still there", stale.exists())
        assertTrue(file.exists())
        assertEquals(
            "the copy isn't this build's template",
            context.assets.open("template/template.apk").use { it.readBytes().size },
            file.length().toInt(),
        )
        assertEquals("a second call copies again", file, provider.file())
    }
}
