package com.executiveos.data.backup

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

internal class WrongPasswordException(cause: Throwable) : Exception(cause)

/**
 * Cifrado autenticado con los algoritmos estándar de la plataforma: AES-256-GCM con una clave
 * derivada de la contraseña mediante PBKDF2-HMAC-SHA256. La cabecera queda autenticada.
 */
internal object BackupCrypto {
    private val MAGIC = byteArrayOf('E'.code.toByte(), 'O'.code.toByte(), 'S'.code.toByte(), 'B'.code.toByte(), 1)
    private const val DEFAULT_ITERATIONS = 600_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private val HEADER_BYTES = MAGIC.size + Int.SIZE_BYTES + SALT_BYTES + IV_BYTES

    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= MAGIC.size && MAGIC.indices.all { data[it] == MAGIC[it] }

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = DEFAULT_ITERATIONS,
    ): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .putInt(iterations)
            .put(salt)
            .put(iv)
            .array()
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, salt, iv, iterations)
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        require(isEncrypted(data) && data.size > HEADER_BYTES) { "No es una copia cifrada" }
        val buffer = ByteBuffer.wrap(data, MAGIC.size, data.size - MAGIC.size)
        val iterations = buffer.int
        require(iterations in 1..MAX_ITERATIONS) { "Parámetros de cifrado no válidos" }
        val salt = ByteArray(SALT_BYTES).also { buffer.get(it) }
        val iv = ByteArray(IV_BYTES).also { buffer.get(it) }
        return try {
            val cipher = cipher(Cipher.DECRYPT_MODE, password, salt, iv, iterations)
            cipher.updateAAD(data, 0, HEADER_BYTES)
            cipher.doFinal(data, HEADER_BYTES, data.size - HEADER_BYTES)
        } catch (failure: GeneralSecurityException) {
            throw WrongPasswordException(failure)
        }
    }

    private fun cipher(
        mode: Int,
        password: CharArray,
        salt: ByteArray,
        iv: ByteArray,
        iterations: Int,
    ): Cipher {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        }
    }
}
