package com.valghora.cloner

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Calendar
import javax.security.auth.x500.X500Principal

/**
 * The one key every copy is signed with, kept in the Android Keystore. Updates to a copy must be
 * signed with the same key; the key cannot be exported, so uninstalling this app loses it.
 */
object SigningKey {
    private const val ALIAS = "cloner-signing-key"
    private const val PROVIDER = "AndroidKeyStore"

    fun getOrCreate(): Pair<PrivateKey, List<X509Certificate>> {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            val notAfter = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }.time
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setKeySize(3072) // apksig signs RSA keys up to 3072 bits with SHA-256
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(X500Principal("CN=Universal Android Wrapper cloner PoC"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotAfter(notAfter)
                .build()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER).run {
                initialize(spec)
                generateKeyPair()
            }
        }
        val entry = store.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.privateKey to listOf(entry.certificate as X509Certificate)
    }
}
