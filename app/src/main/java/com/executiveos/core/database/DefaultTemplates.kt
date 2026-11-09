package com.executiveos.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

private data class TemplateSeed(
    val suffix: String,
    val name: String,
    val titlePrefix: String,
    val categoryId: String?,
    val whatToDo: String?,
    val expectedOutcome: String?,
    val nextAction: String?,
    val estimatedMinutes: Int?,
)

private val WORK = DefaultCategories.WORK.id
private val STUDY = DefaultCategories.STUDY.id
private val PERSONAL = DefaultCategories.PERSONAL.id

private val TEMPLATE_SEEDS = listOf(
    TemplateSeed(
        "01", "Preparar informe", "Preparar informe: ", WORK,
        "Reunir la información y redactar el informe.",
        "Informe revisado y enviado.",
        "Reunir la información necesaria.",
        120,
    ),
    TemplateSeed(
        "02", "Revisar documento", "Revisar documento: ", WORK,
        "Leer el documento y registrar observaciones.",
        "Observaciones enviadas.",
        "Abrir el documento y leer el resumen.",
        45,
    ),
    TemplateSeed(
        "03", "Responder solicitud", "Responder a: ", WORK,
        "Analizar la solicitud y preparar la respuesta.",
        "Respuesta enviada al solicitante.",
        "Confirmar qué se solicita y para cuándo.",
        30,
    ),
    TemplateSeed(
        "04", "Preparar reunión", "Preparar reunión: ", WORK,
        "Definir objetivo, agenda y material.",
        "Agenda y material compartidos.",
        "Definir el objetivo de la reunión.",
        45,
    ),
    TemplateSeed(
        "05", "Realizar análisis", "Analizar: ", WORK,
        "Reunir los datos, analizarlos y documentar las conclusiones.",
        "Análisis documentado.",
        "Solicitar o descargar los datos.",
        120,
    ),
    TemplateSeed(
        "06", "Estudiar tema", "Estudiar: ", STUDY,
        "Estudiar el tema y resumir lo aprendido.",
        "Resumen de lo aprendido.",
        "Seleccionar el material de estudio.",
        60,
    ),
    TemplateSeed(
        "07", "Completar formación", "Completar formación: ", STUDY,
        "Avanzar en el curso hasta terminarlo.",
        "Certificación completada.",
        "Revisar el siguiente módulo.",
        60,
    ),
    TemplateSeed(
        "08", "Hacer seguimiento", "Seguimiento: ", WORK,
        "Verificar el avance con la persona responsable.",
        "Estado confirmado y registrado.",
        "Contactar a la persona responsable.",
        15,
    ),
    TemplateSeed(
        "09", "Resolver incidencia", "Resolver incidencia: ", WORK,
        "Diagnosticar la causa y aplicar la solución.",
        "Incidencia resuelta y documentada.",
        "Reproducir o confirmar el problema.",
        60,
    ),
    TemplateSeed("10", "Actividad personal", "", PERSONAL, null, null, null, null),
)

val DEFAULT_TEMPLATE_COUNT: Int = TEMPLATE_SEEDS.size

/** No sobrescribe plantillas existentes, por si el usuario ya las ha editado. */
fun seedDefaultTemplates(db: SupportSQLiteDatabase, nowMillis: Long) {
    TEMPLATE_SEEDS.forEachIndexed { index, seed ->
        db.execSQL(
            "INSERT OR IGNORE INTO capture_templates (id, name, title_prefix, category_id, " +
                "what_to_do, expected_outcome, next_action, estimated_minutes, sort_order, " +
                "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                "8a1b2c3d-0000-4000-8000-0000000000${seed.suffix}",
                seed.name,
                seed.titlePrefix,
                seed.categoryId,
                seed.whatToDo,
                seed.expectedOutcome,
                seed.nextAction,
                seed.estimatedMinutes,
                index,
                nowMillis,
            ),
        )
    }
}
