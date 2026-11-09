package com.executiveos.data.backup

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal typealias BackupRow = Map<String, JsonPrimitive>

internal data class BackupDocument(
    val schemaVersion: Int,
    val createdAt: Instant,
    val tables: Map<String, List<BackupRow>>,
)

internal object BackupCodec {
    private const val FORMAT = "executive-os-backup"
    private const val FORMAT_VERSION = 1

    /** Sin esta marca, Excel interpreta mal los acentos de un CSV en UTF-8. */
    private val BYTE_ORDER_MARK = Char(0xFEFF)
    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')

    fun encode(document: BackupDocument): ByteArray = buildJsonObject {
        put("format", FORMAT)
        put("formatVersion", FORMAT_VERSION)
        put("schemaVersion", document.schemaVersion)
        put("createdAt", document.createdAt.toString())
        putJsonObject("tables") {
            document.tables.forEach { (table, rows) ->
                put(table, buildJsonArray { rows.forEach { add(JsonObject(it)) } })
            }
        }
    }.toString().encodeToByteArray()

    /** Cualquier estructura inesperada lanza una excepción: el archivo no es una copia válida. */
    fun decode(bytes: ByteArray): BackupDocument {
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        require(root.getValue("format").jsonPrimitive.content == FORMAT) { "Formato desconocido" }
        require(root.getValue("formatVersion").jsonPrimitive.int == FORMAT_VERSION) { "Versión desconocida" }
        return BackupDocument(
            schemaVersion = root.getValue("schemaVersion").jsonPrimitive.int,
            createdAt = Instant.parse(root.getValue("createdAt").jsonPrimitive.content),
            tables = root.getValue("tables").jsonObject.mapValues { (_, rows) ->
                rows.jsonArray.map { row -> row.jsonObject.mapValues { (_, value) -> value.jsonPrimitive } }
            },
        )
    }

    fun csvZip(document: BackupDocument): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            document.tables.forEach { (table, rows) ->
                zip.putNextEntry(ZipEntry("$table.csv"))
                zip.write(csv(rows).encodeToByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun csv(rows: List<BackupRow>): String {
        val columns = rows.flatMap { it.keys }.distinct()
        return buildString {
            append(BYTE_ORDER_MARK)
            appendLine(columns.joinToString(",", transform = ::escape))
            rows.forEach { row ->
                appendLine(columns.joinToString(",") { column -> cell(row[column]) })
            }
        }
    }

    private fun cell(value: JsonPrimitive?): String = when {
        value == null || value is JsonNull -> ""
        value.isString -> escape(neutralizeFormula(value.content))
        else -> value.content
    }

    /** Una hoja de cálculo ejecutaría como fórmula un texto que empezara por «=». */
    private fun neutralizeFormula(text: String): String =
        if (text.firstOrNull() in FORMULA_PREFIXES) "'$text" else text

    private fun escape(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${text.replace("\"", "\"\"")}\""
        } else {
            text
        }
}
