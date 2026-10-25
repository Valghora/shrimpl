package com.valghora.wrapper.core.build

import java.security.interfaces.RSAPublicKey
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningKeysTest {
    private val key = TemplateFixture.key

    @Test
    fun generatesSelfSignedRsa3072Certificate() {
        val cert = key.certificate
        cert.verify(cert.publicKey) // self-signed: throws if the signature is wrong
        cert.checkValidity()
        assertEquals(3, cert.version)
        assertEquals(3072, (cert.publicKey as RSAPublicKey).modulus.bitLength())
        assertEquals("CN=${SigningKeys.COMMON_NAME}", cert.subjectX500Principal.name)
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
        assertEquals("SHA256withRSA", cert.sigAlgName)
        assertTrue(cert.serialNumber.signum() > 0)
    }

    @Test
    fun validityUsesGeneralizedTimeAfter2049() {
        val start = ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, ZoneOffset.UTC)
        val cert = SigningKeys.generate("Test", start).certificate
        assertEquals(start.toInstant(), cert.notBefore.toInstant())
        assertEquals(start.plusYears(30).toInstant(), cert.notAfter.toInstant()) // 2056
    }

    @Test
    fun fingerprintIsSha256Hex() {
        assertTrue(key.fingerprint, Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}").matches(key.fingerprint))
    }

    @Test
    fun exportImportRoundTrip() {
        val password = "correct horse".toCharArray()
        val restored = SigningKeys.import(SigningKeys.export(key, password), password)
        assertEquals(key.certificate, restored.certificate)
        assertArrayEquals(key.privateKey.encoded, restored.privateKey.encoded)
        assertEquals(key.fingerprint, restored.fingerprint)
    }

    @Test
    fun importRejectsWrongPasswordAndGarbage() {
        val file = SigningKeys.export(key, "right".toCharArray())
        assertThrows(SigningKeyException::class.java) { SigningKeys.import(file, "wrong".toCharArray()) }
        assertThrows(SigningKeyException::class.java) { SigningKeys.import(byteArrayOf(1, 2, 3), "x".toCharArray()) }
    }

    @Test
    fun derEncodesLongLengths() {
        assertEquals(listOf(0x30, 0x81, 200), Der.tlv(0x30, ByteArray(200)).take(3).map { it.toInt() and 0xFF })
        assertEquals(listOf(0x30, 0x82, 1, 0), Der.tlv(0x30, ByteArray(256)).take(4).map { it.toInt() and 0xFF })
        assertEquals(listOf(0x30, 0x83, 1, 0, 0), Der.tlv(0x30, ByteArray(65536)).take(5).map { it.toInt() and 0xFF })
    }
}
