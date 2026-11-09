package com.executiveos.data.backup

import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoAndCodecTest {
    private val fastIterations = 1_000
    private val secret = "contraseña".toCharArray()
    private val plain = "datos personales".encodeToByteArray()

    private fun encrypted() = BackupCrypto.encrypt(plain, secret.clone(), fastIterations)

    @Test
    fun encryptedDataIsRecognizedAndDecrypted() {
        val data = encrypted()

        assertTrue(BackupCrypto.isEncrypted(data))
        assertFalse(BackupCrypto.isEncrypted(plain))
        assertArrayEquals(plain, BackupCrypto.decrypt(data, secret.clone()))
    }

    @Test
    fun eachEncryptionUsesFreshRandomness() {
        assertFalse(encrypted().contentEquals(encrypted()))
    }

    @Test
    fun aWrongPasswordFails() {
        assertThrows(WrongPasswordException::class.java) {
            BackupCrypto.decrypt(encrypted(), "otra".toCharArray())
        }
    }

    @Test
    fun alteringTheHeaderOrTheContentFails() {
        val header = encrypted().also { it[8] = (it[8] + 1).toByte() }
        val content = encrypted().also { it[it.size - 3] = (it[it.size - 3] + 1).toByte() }

        assertThrows(WrongPasswordException::class.java) { BackupCrypto.decrypt(header, secret.clone()) }
        assertThrows(WrongPasswordException::class.java) { BackupCrypto.decrypt(content, secret.clone()) }
    }

    @Test
    fun theDocumentSurvivesEncodingWithEveryValueType() {
        val document = BackupDocument(
            schemaVersion = 6,
            createdAt = Instant.parse("2026-11-11T15:00:00Z"),
            tables = mapOf(
                "tasks" to listOf(
                    mapOf(
                        "title" to JsonPrimitive("Comillas \" y ñ"),
                        "minutes" to JsonPrimitive(45),
                        "score" to JsonPrimitive(1.5),
                        "notes" to JsonNull,
                    ),
                ),
            ),
        )

        assertEquals(document, BackupCodec.decode(BackupCodec.encode(document)))
    }

    @Test
    fun csvQuotesSeparatorsAndLeavesNullsEmpty() {
        val csv = BackupCodec.csv(
            listOf(
                mapOf("a" to JsonPrimitive("uno, dos"), "b" to JsonNull),
                mapOf("a" to JsonPrimitive("dice \"hola\""), "b" to JsonPrimitive(3)),
            ),
        ).lines()

        assertEquals("﻿a,b", csv[0])
        assertEquals("\"uno, dos\",", csv[1])
        assertEquals("\"dice \"\"hola\"\"\",3", csv[2])
    }

    @Test
    fun csvNeutralizesTextThatASpreadsheetWouldRunAsAFormula() {
        val csv = BackupCodec.csv(listOf(mapOf("a" to JsonPrimitive("=HYPERLINK(1)")))).lines()

        assertEquals("'=HYPERLINK(1)", csv[1])
    }
}
