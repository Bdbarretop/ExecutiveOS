package com.executiveos.domain.backup

import java.time.LocalDate

enum class ExportFormat { PROTECTED_JSON, PLAIN_JSON, CSV_ZIP }

data class BackupSummary(
    val createdOn: LocalDate,
    val tasks: Int,
    val projects: Int,
    val timeBlocks: Int,
    val reviews: Int,
    val learningGoals: Int,
)

sealed interface BackupCheck {
    data class Valid(val summary: BackupSummary) : BackupCheck
    data object NeedsPassword : BackupCheck

    /** El cifrado autenticado no distingue una contraseña errónea de un archivo manipulado. */
    data object WrongPasswordOrDamaged : BackupCheck
    data object NewerVersion : BackupCheck
    data object Invalid : BackupCheck
}

/** Las rutas son URI en texto: el dominio no conoce Android. */
interface BackupRepository {
    suspend fun export(format: ExportFormat, destination: String, password: String?)

    /** Valida la copia por completo sin modificar ningún dato. */
    suspend fun check(source: String, password: String?): BackupCheck

    /** Sustituye todos los datos o, si algo falla, no cambia ninguno. */
    suspend fun restore(source: String, password: String?)
}
