package com.valghora.wrapper.core.build

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The key every web app is signed with, and its self-signed certificate. */
class SigningKey(val privateKey: PrivateKey, val certificate: X509Certificate) {
    /** SHA-256 of the certificate, as colon-separated uppercase hex (how Android tools show it). */
    val fingerprint: String
        get() = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it) }
}

class SigningKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Creates, exports and imports UniWrap's signing key (docs/UNIWRAP_SPEC.md R5): RSA 3072 with a
 * self-signed certificate, stored as PKCS12. Plain JVM APIs only (the certificate is DER-encoded
 * here because the JDK has no public certificate builder), so it behaves the same on Android.
 */
object SigningKeys {
    const val ALIAS = "uniwrap"
    const val COMMON_NAME = "UniWrap apps"
    private const val KEY_SIZE = 3072
    private const val VALIDITY_YEARS = 30L
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    private const val OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11"
    private const val OID_COMMON_NAME = "2.5.4.3"

    fun generate(
        commonName: String = COMMON_NAME,
        now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
        random: SecureRandom = SecureRandom(),
    ): SigningKey {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE, random) }.generateKeyPair()
        val algorithm = Der.sequence(Der.oid(OID_SHA256_WITH_RSA), Der.NULL)
        val name = Der.sequence(Der.set(Der.sequence(Der.oid(OID_COMMON_NAME), Der.utf8(commonName))))
        val start = now.withNano(0)
        val tbs = Der.sequence(
            Der.explicit(0, Der.integer(BigInteger.valueOf(2))), // X.509 v3
            Der.integer(BigInteger(63, random).add(BigInteger.ONE)),
            algorithm,
            name,
            Der.sequence(Der.time(start), Der.time(start.plusYears(VALIDITY_YEARS))),
            name,
            pair.public.encoded, // already a DER SubjectPublicKeyInfo
        )
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(pair.private)
            update(tbs)
            sign()
        }
        val der = Der.sequence(tbs, algorithm, Der.bitString(signature))
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        return SigningKey(pair.private, certificate)
    }

    /** A password-protected PKCS12 file holding the key (the backup format, R5.2). */
    fun export(key: SigningKey, password: CharArray): ByteArray {
        val store = KeyStore.getInstance("PKCS12").apply { load(null, null) }
        store.setKeyEntry(ALIAS, key.privateKey, password, arrayOf(key.certificate))
        return ByteArrayOutputStream().also { store.store(it, password) }.toByteArray()
    }

    /** Reads a PKCS12 file written by [export] (or any PKCS12 with one key entry). */
    fun import(pkcs12: ByteArray, password: CharArray): SigningKey {
        val store = try {
            KeyStore.getInstance("PKCS12").apply { load(ByteArrayInputStream(pkcs12), password) }
        } catch (e: java.io.IOException) {
            throw SigningKeyException("Wrong password, or not a PKCS12 key file", e)
        } catch (e: GeneralSecurityException) {
            throw SigningKeyException("Not a readable PKCS12 key file", e)
        }
        val alias = store.aliases().toList().firstOrNull { store.isKeyEntry(it) }
            ?: throw SigningKeyException("The file contains no key")
        val privateKey = store.getKey(alias, password) as? PrivateKey
            ?: throw SigningKeyException("The key entry has no private key")
        val certificate = store.getCertificate(alias) as? X509Certificate
            ?: throw SigningKeyException("The key entry has no X.509 certificate")
        return SigningKey(privateKey, certificate)
    }
}

/** The handful of DER encodings an X.509 certificate needs. */
internal object Der {
    private const val INTEGER = 0x02
    private const val BIT_STRING = 0x03
    private const val OBJECT_IDENTIFIER = 0x06
    private const val UTF8_STRING = 0x0C
    private const val UTC_TIME = 0x17
    private const val GENERALIZED_TIME = 0x18
    private const val SEQUENCE = 0x30
    private const val SET = 0x31

    val NULL = byteArrayOf(0x05, 0x00)

    fun sequence(vararg parts: ByteArray) = tlv(SEQUENCE, parts.fold(ByteArray(0)) { acc, b -> acc + b })

    fun set(vararg parts: ByteArray) = tlv(SET, parts.fold(ByteArray(0)) { acc, b -> acc + b })

    fun explicit(tag: Int, content: ByteArray) = tlv(0xA0 or tag, content)

    fun integer(value: BigInteger) = tlv(INTEGER, value.toByteArray())

    fun utf8(value: String) = tlv(UTF8_STRING, value.toByteArray(Charsets.UTF_8))

    fun bitString(bytes: ByteArray) = tlv(BIT_STRING, byteArrayOf(0) + bytes)

    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * 40 + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            val groups = mutableListOf<Int>()
            var v = arc
            do {
                groups.add(0, (v and 0x7F).toInt())
                v = v shr 7
            } while (v > 0)
            groups.forEachIndexed { i, g -> out.write(if (i < groups.lastIndex) g or 0x80 else g) }
        }
        return tlv(OBJECT_IDENTIFIER, out.toByteArray())
    }

    /** UTCTime before 2050, GeneralizedTime from 2050 on (RFC 5280). */
    fun time(at: ZonedDateTime): ByteArray {
        val utc = at.withZoneSameInstant(ZoneOffset.UTC)
        return if (utc.year < 2050) {
            tlv(UTC_TIME, utc.format(DateTimeFormatter.ofPattern("yyMMddHHmmss'Z'")).toByteArray())
        } else {
            tlv(GENERALIZED_TIME, utc.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")).toByteArray())
        }
    }

    fun tlv(tag: Int, content: ByteArray): ByteArray {
        val length = content.size
        val header = when {
            length < 0x80 -> byteArrayOf(tag.toByte(), length.toByte())
            length < 0x100 -> byteArrayOf(tag.toByte(), 0x81.toByte(), length.toByte())
            length < 0x10000 -> byteArrayOf(tag.toByte(), 0x82.toByte(), (length shr 8).toByte(), length.toByte())
            else -> byteArrayOf(
                tag.toByte(), 0x83.toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte(),
            )
        }
        return header + content
    }
}
