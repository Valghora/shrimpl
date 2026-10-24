package com.valghora.cloner

import com.android.apksig.ApkVerifier
import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Clones the real template APK (the committed Discord release build) on the host. */
class ApkClonerTest {
    private val template = File(System.getProperty("templateApk") ?: "missing")
    private lateinit var tmp: File

    @Before
    fun setUp() {
        assumeTrue("template APK not found: $template", template.isFile)
        tmp = Files.createTempDirectory("cloner").toFile()
    }

    @After
    fun tearDown() {
        if (::tmp.isInitialized) tmp.deleteRecursively()
    }

    private fun manifestOf(apk: File) = ZipFile(apk).use { zip ->
        zip.getInputStream(zip.getEntry("AndroidManifest.xml")).use { it.readBytes() }
    }

    @Test
    fun readsTemplate() {
        val info = ManifestPatcher.read(manifestOf(template))
        assertEquals("com.valghora.pwa.discord", info.packageName)
        assertNull("the template label is a resource reference", info.literalLabel)
    }

    @Test
    fun unchangedPatchIsByteIdentical() {
        val manifest = manifestOf(template)
        val pkg = ManifestPatcher.read(manifest).packageName
        assertArrayEquals(manifest, ManifestPatcher.patch(manifest, pkg))
    }

    @Test
    fun renamesPackageDerivedStringsAndSetsLabel() {
        val original = ManifestPatcher.read(manifestOf(template))
        val patched = ManifestPatcher.read(
            ManifestPatcher.patch(manifestOf(template), "com.valghora.pwa.discord.clone2", "Discord 2")
        )
        assertEquals("com.valghora.pwa.discord.clone2", patched.packageName)
        assertEquals("Discord 2", patched.literalLabel)
        val old = "com.valghora.pwa.discord"
        assertTrue(patched.strings.none { it == old || it.startsWith("$old.") && !it.startsWith("$old.clone2") })
        assertTrue("com.valghora.pwa.discord.clone2.androidx-startup" in patched.strings)
        assertTrue("com.valghora.pwa.discord.clone2.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" in patched.strings)
        // Strings unrelated to the package are untouched, and the template namespace's classes keep their names.
        assertTrue("android.permission.INTERNET" in patched.strings)
        assertTrue("com.valghora.wrapper.MainActivity" in patched.strings)
        assertEquals(original.strings.size + 1, patched.strings.size)
    }

    @Test
    fun cloneIsSignedAlignedAndRenamed() {
        val (key, certs) = testKey()
        val clone = File(tmp, "clone.apk")
        ApkCloner.clone(template, clone, "com.valghora.pwa.discord.clone2", "Discord 2", key, certs)

        val result = ApkVerifier.Builder(clone).build().verify()
        assertTrue("signature errors: ${result.errors}", result.isVerified)
        // The template's minSdk is 37, so apksig signs with scheme v3 only (v2 is for Android 7 to 8.1).
        assertTrue(result.isVerifiedUsingV3Scheme)
        assertEquals(certs.first(), result.signerCertificates.first())

        assertEquals("com.valghora.pwa.discord.clone2", ManifestPatcher.read(manifestOf(clone)).packageName)
        ZipFile(clone).use { zip ->
            val arsc = zip.getEntry("resources.arsc")
            assertEquals("resources.arsc must stay uncompressed", ZipEntry.STORED, arsc.method)
            assertEquals(
                ZipFile(template).use { it.size() },
                zip.size(),
            )
        }
        System.getenv("CLONER_TEST_OUTPUT")?.let { clone.copyTo(File(it), overwrite = true) }
        aapt2Badging(clone)?.let { badging ->
            assertTrue(badging, badging.contains("package: name='com.valghora.pwa.discord.clone2'"))
            assertTrue(badging, badging.contains("application-label:'Discord 2'"))
        }
    }

    /** A throwaway PKCS12 key made with the JDK's keytool. */
    private fun testKey(): Pair<PrivateKey, List<X509Certificate>> {
        val store = File(tmp, "test.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val process = ProcessBuilder(
            keytool, "-genkeypair", "-keystore", store.path, "-storetype", "PKCS12", "-storepass", "password",
            "-alias", "test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1", "-dname", "CN=Cloner test",
        ).redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().readText()
        assertEquals(log, 0, process.waitFor())
        val ks = KeyStore.getInstance("PKCS12").apply { store.inputStream().use { load(it, "password".toCharArray()) } }
        val key = ks.getKey("test", "password".toCharArray()) as PrivateKey
        return key to listOf(ks.getCertificate("test") as X509Certificate)
    }

    /** `aapt2 dump badging` output when the Android SDK is available, else null. */
    private fun aapt2Badging(apk: File): String? {
        val sdk = System.getenv("ANDROID_HOME") ?: return null
        val aapt2 = File(sdk, "build-tools").listFiles()?.mapNotNull { File(it, "aapt2").takeIf(File::canExecute) }
            ?.maxByOrNull { it.parentFile.name } ?: return null
        val process = ProcessBuilder(aapt2.path, "dump", "badging", apk.path).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        assertEquals(out, 0, process.waitFor())
        return out
    }
}
