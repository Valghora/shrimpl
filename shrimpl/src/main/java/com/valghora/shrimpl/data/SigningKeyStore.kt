package com.valghora.shrimpl.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.valghora.shrimpl.core.build.SigningKey
import com.valghora.shrimpl.core.build.SigningKeyException
import com.valghora.shrimpl.core.build.SigningKeys
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The key every web app is signed with (docs/SHRIMPL_SPEC.md R5). It is a PKCS12 file in no-backup
 * storage; the file's random password is encrypted with an AES key that never leaves the Android
 * Keystore. Exported backups are PKCS12 files protected by a password the user chooses.
 */
class SigningKeyStore(context: Context) {
    private val dir = File(context.noBackupFilesDir, "signing")
    private val keyFile = AtomicFile(File(dir, "shrimpl.p12"))
    private val passwordFile = AtomicFile(File(dir, "shrimpl.p12.password"))
    private var cached: SigningKey? = null

    fun exists(): Boolean = keyFile.baseFile.exists() && passwordFile.baseFile.exists()

    /** The key, created on first use (R5.1). Slow the first time: call off the main thread. */
    @Synchronized
    fun getOrCreate(): SigningKey {
        cached?.let { return it }
        val key = if (exists()) SigningKeys.import(keyFile.readFully(), password()) else SigningKeys.generate().also(::store)
        cached = key
        return key
    }

    /** The key if it exists, without creating one. */
    @Synchronized
    fun current(): SigningKey? = cached ?: if (exists()) getOrCreate() else null

    /** A PKCS12 backup protected by [password] (R5.2). */
    fun export(password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD) { "The password must have at least $MIN_PASSWORD characters" }
        return SigningKeys.export(getOrCreate(), password)
    }

    /** Reads a backup without installing it, so the caller can compare certificates first. */
    fun read(pkcs12: ByteArray, password: CharArray): SigningKey = SigningKeys.import(pkcs12, password)

    /** Replaces the current key with [key] (R5.2; the caller confirms when web apps exist). */
    @Synchronized
    fun replace(key: SigningKey) {
        store(key)
        cached = key
    }

    private fun store(key: SigningKey) {
        dir.mkdirs()
        val password = randomPassword()
        write(keyFile, SigningKeys.export(key, password))
        write(passwordFile, encrypt(String(password).toByteArray()))
    }

    private fun password(): CharArray = try {
        String(decrypt(passwordFile.readFully())).toCharArray()
    } catch (e: java.security.GeneralSecurityException) {
        throw SigningKeyException("Signing key's password can't be decrypted", e)
    }

    private fun randomPassword(): CharArray {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().withoutPadding().encodeToString(bytes).toCharArray()
    }

    // ----- Android Keystore AES-GCM -----

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    /** IV length (1 byte), IV, ciphertext. */
    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun decrypt(data: ByteArray): ByteArray {
        val ivLength = data[0].toInt()
        val iv = data.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv)) }
        return cipher.doFinal(data, 1 + ivLength, data.size - 1 - ivLength)
    }

    private fun write(file: AtomicFile, bytes: ByteArray) {
        val out = file.startWrite()
        try {
            out.write(bytes)
            file.finishWrite(out)
        } catch (e: java.io.IOException) {
            file.failWrite(out)
            throw e
        }
    }

    companion object {
        const val MIN_PASSWORD = 8
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "shrimpl-signing-key-password"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
