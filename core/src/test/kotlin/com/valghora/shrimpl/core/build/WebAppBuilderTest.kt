package com.valghora.shrimpl.core.build

import com.android.apksig.ApkVerifier
import com.valghora.shrimpl.core.profile.Profile
import com.valghora.shrimpl.core.profile.ProfileCodec
import com.valghora.shrimpl.core.profile.WindowSize
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/SHRIMPL_SPEC.md R8.5: builds real web apps from the real template. */
class WebAppBuilderTest {
    private val tmp: File = Files.createTempDirectory("webapp").toFile()
    private val template = TemplateFixture.apk
    private val key = TemplateFixture.key

    private val profile = Profile(
        name = "Example",
        appId = "example",
        startUrl = "https://example.com/",
        internalDomains = listOf("example.com"),
        iconBackground = "#3367D6",
        headerColor = "#FFFFFF",
        enableMultiWindow = true,
        defaultWindowDp = WindowSize(1000, 700),
        minWindowDp = WindowSize(360, 480),
    )
    private val icons = IconSet(
        background = byteArrayOf(1),
        foreground = byteArrayOf(2, 2),
        monochrome = byteArrayOf(3, 3, 3),
        notification = byteArrayOf(4, 4, 4, 4),
    )

    @After
    fun tearDown() {
        tmp.deleteRecursively()
    }

    private fun build(spec: WebAppSpec = WebAppSpec(profile, buildNumber = 7, icons = icons), name: String = "app.apk") =
        WebAppBuilder.build(template, File(tmp, name), spec, key)

    @Test
    fun templateHasStableIconPaths() {
        ZipFile(template).use { zip ->
            WebAppBuilder.Paths.REQUIRED.forEach { assertTrue("$it missing from the template", zip.getEntry(it) != null) }
        }
        assertEquals(8, WebAppBuilder.templateVersion(template))
    }

    @Test
    fun buildsSignedRenamedWebApp() {
        val stages = mutableListOf<WebAppBuilder.Stage>()
        val built = WebAppBuilder.build(template, File(tmp, "app.apk"), WebAppSpec(profile, 7, icons), key, onStage = { stages += it })
        assertEquals(WebAppBuilder.Stage.entries, stages)
        assertEquals("com.valghora.shrimpl.app.example", built.packageName)
        assertEquals(7, built.versionCode)
        assertEquals("8.7", built.versionName)
        assertEquals(key.fingerprint, built.signerFingerprint)

        val result = ApkVerifier.Builder(built.apk).build().verify()
        assertTrue("${result.errors}", result.isVerified)
        assertEquals(key.certificate, result.signerCertificates.single())

        val info = ManifestPatcher.read(TemplateFixture.entry(built.apk, WebAppBuilder.Paths.MANIFEST))
        assertEquals("Example", info.literalLabel)
        assertEquals("8.7", info.versionName)
        assertEquals(WindowLayout(1000, 700, 360, 480), info.window)
        assertEquals(LaunchMode.SINGLE_INSTANCE_PER_TASK.value, info.launchMode)
        assertEquals(true, info.multiInstance)
    }

    @Test
    fun replacesProfileAndIcons() {
        val apk = build().apk
        assertEquals(profile, ProfileCodec.decode(TemplateFixture.entry(apk, WebAppBuilder.Paths.PROFILE).decodeToString()))
        assertArrayEquals(icons.background, TemplateFixture.entry(apk, WebAppBuilder.Paths.ICON_BACKGROUND))
        assertArrayEquals(icons.foreground, TemplateFixture.entry(apk, WebAppBuilder.Paths.ICON_FOREGROUND))
        assertArrayEquals(icons.monochrome, TemplateFixture.entry(apk, WebAppBuilder.Paths.ICON_MONOCHROME))
        assertArrayEquals(icons.notification, TemplateFixture.entry(apk, WebAppBuilder.Paths.ICON_NOTIFICATION))
    }

    @Test
    fun keepsTemplateIconsWhenNoneGiven() {
        val apk = build(WebAppSpec(profile, 1)).apk
        assertArrayEquals(
            TemplateFixture.entry(template, WebAppBuilder.Paths.ICON_FOREGROUND),
            TemplateFixture.entry(apk, WebAppBuilder.Paths.ICON_FOREGROUND),
        )
    }

    @Test
    fun webAppsHaveNoNativeCode() {
        // R0: generated web apps are dex and resources only, so they run natively on every ARM and
        // x86 device with no translation layer.
        ZipFile(build().apk).use { built ->
            val native = built.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") || it.endsWith(".so") }.toList()
            assertEquals(emptyList<String>(), native)
        }
    }

    @Test
    fun keepsEveryOtherEntryAndItsCompression() {
        val apk = build().apk
        ZipFile(template).use { original ->
            ZipFile(apk).use { built ->
                assertEquals(original.size(), built.size())
                assertEquals(ZipEntry.STORED, built.getEntry("resources.arsc").method)
                assertArrayEquals(
                    original.getInputStream(original.getEntry("classes.dex")).readBytes(),
                    built.getInputStream(built.getEntry("classes.dex")).readBytes(),
                )
            }
        }
    }

    @Test
    fun keepsLocationWhenAllowed() {
        val apk = build(WebAppSpec(profile.copy(allowLocation = true), 1)).apk
        val info = ManifestPatcher.read(TemplateFixture.entry(apk, WebAppBuilder.Paths.MANIFEST))
        assertTrue(info.permissions.containsAll(listOf("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION")))
        assertTrue("android.hardware.location.gps" in info.features)
    }

    @Test
    fun removesDisabledCapabilities() {
        val apk = build(WebAppSpec(profile.copy(allowNotifications = false, enableMultiWindow = false), 1)).apk
        val info = ManifestPatcher.read(TemplateFixture.entry(apk, WebAppBuilder.Paths.MANIFEST))
        assertEquals(
            listOf("android.permission.INTERNET", "com.valghora.shrimpl.app.example.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"),
            info.permissions,
        )
        assertEquals(listOf("android.hardware.type.pc", "android.hardware.touchscreen"), info.features)
        assertEquals(LaunchMode.SINGLE_TASK.value, info.launchMode)
        assertEquals(false, info.multiInstance)
    }

    @Test
    fun keepsEnabledCapabilities() {
        val apk = build(WebAppSpec(profile.copy(allowMicrophone = true, allowCamera = true), 1)).apk
        val info = ManifestPatcher.read(TemplateFixture.entry(apk, WebAppBuilder.Paths.MANIFEST))
        assertTrue(info.permissions.containsAll(listOf("android.permission.RECORD_AUDIO", "android.permission.CAMERA", "android.permission.POST_NOTIFICATIONS")))
        assertTrue(info.features.containsAll(listOf("android.hardware.microphone", "android.hardware.camera")))
    }

    @Test
    fun aapt2BadgingShowsTheWebApp() {
        val badging = TemplateFixture.aapt2("dump", "badging", build().apk.path) ?: return
        assertTrue(badging, badging.contains("package: name='com.valghora.shrimpl.app.example' versionCode='7' versionName='8.7'"))
        assertTrue(badging, badging.contains("application-label:'Example'"))
        assertFalse(badging, badging.contains("android.permission.CAMERA"))
        assertTrue(badging, badging.contains("icon='res/mipmap-anydpi-v21/ic_launcher.xml'"))
    }

    @Test
    fun rejectsInvalidSpecs() {
        assertThrows(BuildException::class.java) { build(WebAppSpec(profile.copy(startUrl = "http://example.com"), 1)) }
        assertThrows(BuildException::class.java) { build(WebAppSpec(profile, 0)) }
    }

    @Test
    fun rejectsSomethingOtherThanTheTemplate() {
        val other = File(tmp, "other.apk")
        WebAppBuilder.build(template, other, WebAppSpec(profile, 1), key)
        val e = assertThrows(BuildException::class.java) { WebAppBuilder.build(other, File(tmp, "x.apk"), WebAppSpec(profile, 2), key) }
        assertTrue(e.message!!, e.message!!.contains("Not the Shrimpl template"))
    }

    @Test
    fun cancellationStopsAndCleansUp() {
        val out = File(tmp, "cancelled.apk")
        assertThrows(BuildCancelledException::class.java) {
            WebAppBuilder.build(template, out, WebAppSpec(profile, 1), key, isCancelled = { true })
        }
        assertFalse(out.exists())
        assertTrue("no leftover files", tmp.listFiles()!!.isEmpty())
    }

    @Test
    fun removalsFollowProfile() {
        val (permissions, features) = WebAppBuilder.removals(profile.copy(allowMicrophone = true))
        assertFalse("android.permission.RECORD_AUDIO" in permissions)
        assertTrue("android.permission.CAMERA" in permissions)
        assertFalse("android.permission.POST_NOTIFICATIONS" in permissions)
        assertTrue("android.hardware.camera" in features)
    }
}
