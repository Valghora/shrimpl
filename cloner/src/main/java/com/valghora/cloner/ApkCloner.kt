package com.valghora.cloner

import com.android.apksig.ApkSigner
import java.io.File
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Makes an installable copy of an APK under a new package name: rewrites the manifest, drops the
 * old signature, and signs the result (APK signature schemes v2 and v3) with the given key.
 * Plain JVM code; [MainActivity] supplies a key from the Android Keystore.
 */
object ApkCloner {
    private const val MANIFEST = "AndroidManifest.xml"
    private val V1_SIGNATURE = Regex("""META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))""")

    fun clone(
        template: File,
        output: File,
        newPackage: String,
        newLabel: String?,
        key: PrivateKey,
        certificates: List<X509Certificate>,
    ) {
        val unsigned = File(output.parentFile, output.name + ".unsigned")
        try {
            ZipFile(template).use { zip ->
                ZipOutputStream(unsigned.outputStream().buffered()).use { out ->
                    for (entry in zip.entries()) {
                        if (V1_SIGNATURE.matches(entry.name)) continue
                        var data = zip.getInputStream(entry).use { it.readBytes() }
                        if (entry.name == MANIFEST) data = ManifestPatcher.patch(data, newPackage, newLabel)
                        out.putNextEntry(copyOf(entry, data))
                        out.write(data)
                        out.closeEntry()
                    }
                }
            }
            val signer = ApkSigner.SignerConfig.Builder("cloner", key, certificates).build()
            ApkSigner.Builder(listOf(signer))
                .setInputApk(unsigned)
                .setOutputApk(output)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setAlignmentPreserved(false) // re-align stored entries (resources.arsc must be 4-byte aligned)
                .build()
                .sign()
        } finally {
            unsigned.delete()
        }
    }

    /** Keeps each entry's compression: resources.arsc must stay uncompressed for Android to install the APK. */
    private fun copyOf(entry: ZipEntry, data: ByteArray) = ZipEntry(entry.name).apply {
        time = entry.time
        if (entry.method == ZipEntry.STORED) {
            method = ZipEntry.STORED
            size = data.size.toLong()
            compressedSize = data.size.toLong()
            crc = CRC32().apply { update(data) }.value
        } else {
            method = ZipEntry.DEFLATED
        }
    }
}
