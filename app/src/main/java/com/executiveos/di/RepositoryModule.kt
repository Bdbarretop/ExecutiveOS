package com.executiveos.di

import com.executiveos.data.repository.DataStoreUserPreferencesRepository
import com.executiveos.data.repository.OfflineCategoryRepository
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {
    @Binds
    fun bindCategoryRepository(impl: OfflineCategoryRepository): CategoryRepository

    @Binds
    fun bindUserPreferencesRepository(
        impl: DataStoreUserPreferencesRepository,
    ): UserPreferencesRepository
}
