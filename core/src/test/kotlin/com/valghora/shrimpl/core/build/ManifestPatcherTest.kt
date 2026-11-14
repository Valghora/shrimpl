package com.valghora.shrimpl.core.build

import com.valghora.shrimpl.core.Versions
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs against the real template manifest. */
class ManifestPatcherTest {
    private val template = TemplateFixture.manifest
    private val original = ManifestPatcher.read(template)
    private val pkg = "com.valghora.shrimpl.app.example"

    @Test
    fun readsTemplate() {
        assertEquals(WebAppBuilder.TEMPLATE_PACKAGE, original.packageName)
        assertNull("the template label is a resource reference", original.literalLabel)
        assertEquals(TemplateFixture.version, original.versionCode)
        assertEquals(Versions.name(TemplateFixture.version), original.versionName)
        assertTrue(original.permissions.containsAll(listOf("android.permission.CAMERA", "android.permission.RECORD_AUDIO")))
        assertTrue("android.hardware.microphone" in original.features)
        assertEquals(LaunchMode.SINGLE_INSTANCE_PER_TASK.value, original.launchMode)
        assertEquals(WindowLayout(1280, 800, 400, 500), original.window)
        assertEquals(true, original.multiInstance)
    }

    @Test
    fun packageOnlyEditKeepsEverythingElse() {
        assertArrayEquals(template, ManifestPatcher.patch(template, ManifestEdits(original.packageName)))
    }

    @Test
    fun renamesPackageAndDerivedStrings() {
        val patched = ManifestPatcher.read(ManifestPatcher.patch(template, ManifestEdits(pkg)))
        assertEquals(pkg, patched.packageName)
        val old = WebAppBuilder.TEMPLATE_PACKAGE
        assertTrue(patched.strings.none { it == old || it.startsWith("$old.") })
        assertTrue("$pkg.androidx-startup" in patched.strings)
        assertTrue("$pkg.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" in patched.permissions)
        assertTrue("com.valghora.shrimpl.webapp.MainActivity" in patched.strings)
    }

    @Test
    fun appliesEveryEdit() {
        val edits = ManifestEdits(
            packageName = pkg,
            label = "Example – Ünïcode",
            versionCode = 12,
            versionName = "1.12",
            removePermissions = setOf("android.permission.CAMERA", "android.permission.POST_NOTIFICATIONS"),
            removeFeatures = setOf("android.hardware.camera", "android.hardware.camera.autofocus"),
            launchMode = LaunchMode.SINGLE_TASK,
            window = WindowLayout(1000, 700, 360, 480),
            multiInstance = false,
        )
        val patched = ManifestPatcher.read(ManifestPatcher.patch(template, edits))
        assertEquals("Example – Ünïcode", patched.literalLabel)
        assertEquals(12, patched.versionCode)
        assertEquals("1.12", patched.versionName)
        assertFalse("android.permission.CAMERA" in patched.permissions)
        assertFalse("android.permission.POST_NOTIFICATIONS" in patched.permissions)
        assertTrue("android.permission.RECORD_AUDIO" in patched.permissions)
        assertEquals(original.permissions.size - 2, patched.permissions.size)
        assertEquals(original.features - setOf("android.hardware.camera", "android.hardware.camera.autofocus"), patched.features)
        assertEquals(LaunchMode.SINGLE_TASK.value, patched.launchMode)
        assertEquals(WindowLayout(1000, 700, 360, 480), patched.window)
        assertEquals(false, patched.multiInstance)
    }

    @Test
    fun aapt2ReadsThePatchedManifest() {
        val edits = ManifestEdits(
            packageName = pkg, label = "Example", versionCode = 3, versionName = "1.3",
            removePermissions = setOf("android.permission.CAMERA"), window = WindowLayout(1000, 700, 360, 480),
            launchMode = LaunchMode.SINGLE_TASK, multiInstance = false,
        )
        // aapt2 reads manifests from inside an APK, so wrap the patched one in a minimal zip.
        val file = kotlin.io.path.createTempFile(suffix = ".apk").toFile()
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(WebAppBuilder.Paths.MANIFEST))
                zip.write(ManifestPatcher.patch(template, edits))
                zip.closeEntry()
            }
            val tree = TemplateFixture.aapt2("dump", "xmltree", "--file", WebAppBuilder.Paths.MANIFEST, file.path) ?: return
            assertTrue(tree, tree.contains("package=\"$pkg\""))
            assertTrue(tree, tree.contains("versionCode(0x0101021b)=3"))
            assertTrue(tree, tree.contains("versionName(0x0101021c)=\"1.3\""))
            assertTrue(tree, tree.contains("label(0x01010001)=\"Example\""))
            assertTrue(tree, tree.contains("launchMode(0x0101001d)=2"))
            assertTrue(tree, tree.contains("defaultWidth(0x010104f4)=1000.000000dp"))
            assertTrue(tree, tree.contains("minHeight(0x01010140)=480.000000dp"))
            assertTrue(tree, tree.contains("value(0x01010024)=false"))
            assertFalse(tree, tree.contains("android.permission.CAMERA"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsInvalidInput() {
        assertThrows(ManifestFormatException::class.java) { ManifestPatcher.read(byteArrayOf(1, 2, 3)) }
        assertThrows(IllegalArgumentException::class.java) {
            ManifestPatcher.patch(template, ManifestEdits(pkg, window = WindowLayout(0, 1, 1, 1)))
        }
        assertThrows(IllegalArgumentException::class.java) { ManifestPatcher.patch(template, ManifestEdits(pkg, versionCode = 0)) }
    }
}
