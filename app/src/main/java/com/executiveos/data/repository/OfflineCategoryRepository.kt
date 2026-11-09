package com.executiveos.data.repository

import com.executiveos.data.local.dao.CategoryDao
import com.executiveos.data.local.entity.CategoryEntity
import com.executiveos.domain.model.Category
import com.executiveos.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineCategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryRepository {
    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { entities -> entities.map(CategoryEntity::toDomain) }
}

private fun CategoryEntity.toDomain() = Category(id = id, name = name, sortOrder = sortOrder)
