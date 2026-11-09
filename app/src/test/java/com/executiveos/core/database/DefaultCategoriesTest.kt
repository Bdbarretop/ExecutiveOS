package com.executiveos.core.database

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultCategoriesTest {
    @Test
    fun catalogMatchesTheBriefInOrder() {
        assertEquals(
            listOf("Trabajo", "Estudio", "Proyecto", "Personal", "Finanzas", "Salud", "Otro"),
            DefaultCategories.all.map { it.name },
        )
    }

    @Test
    fun identifiersAreValidAndUniqueUuids() {
        val ids = DefaultCategories.all.map { it.id }

        ids.forEach { id -> assertEquals(id, UUID.fromString(id).toString()) }
        assertEquals(ids.size, ids.toSet().size)
    }
}
