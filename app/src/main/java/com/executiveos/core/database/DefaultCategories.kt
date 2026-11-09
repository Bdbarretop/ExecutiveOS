package com.executiveos.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.Clock

data class DefaultCategory(val id: String, val name: String)

/**
 * Catálogo inicial de categorías. Los identificadores son fijos para que las copias de
 * seguridad restauradas en otra instalación sigan apuntando a la misma categoría.
 */
object DefaultCategories {
    val WORK = DefaultCategory("7f3a1c52-9b1e-4d3a-8f21-0a6b3c1d2e01", "Trabajo")
    val STUDY = DefaultCategory("7f3a1c52-9b1e-4d3a-8f21-0a6b3c1d2e02", "Estudio")
    val PERSONAL = DefaultCategory("7f3a1c52-9b1e-4d3a-8f21-0a6b3c1d2e04", "Personal")

    val all: List<DefaultCategory> = listOf(
        WORK,
        STUDY,
        DefaultCategory("7f3a1c52-9b1e-4d3a-8f21-0a6b3c1d2e03", "Proyecto"),
        PERSONAL,
        DefaultCategory("7f3a1c52-9b1e-4d3a-8f21-0a6b3c1d2e05", "Finanzas"),
        DefaultCategory("7f3a1c52-9b1e-4d3a-8f21-0a6b3c1d2e06", "Salud"),
        DefaultCategory("7f3a1c52-9b1e-4d3a-8f21-0a6b3c1d2e07", "Otro"),
    )
}

/**
 * Siembra el catálogo solo al crear la base. Android ejecuta `onCreate` dentro de una
 * transacción, así que un fallo no puede dejarlo a medias.
 */
class DefaultDataSeedCallback(private val clock: Clock) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        val createdAt = clock.millis()
        DefaultCategories.all.forEachIndexed { index, category ->
            db.execSQL(
                "INSERT INTO categories (id, name, sort_order, is_system, created_at) " +
                    "VALUES (?, ?, ?, 1, ?)",
                arrayOf<Any>(category.id, category.name, index, createdAt),
            )
        }
        seedDefaultTemplates(db, createdAt)
    }
}
