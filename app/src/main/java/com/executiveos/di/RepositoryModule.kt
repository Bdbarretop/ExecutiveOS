package com.executiveos.di

import com.executiveos.data.repository.DataStoreCaptureDraftRepository
import com.executiveos.data.repository.DataStoreUserPreferencesRepository
import com.executiveos.data.repository.OfflineCategoryRepository
import com.executiveos.data.repository.OfflineInboxRepository
import com.executiveos.data.repository.OfflinePrioritySettingsRepository
import com.executiveos.data.repository.OfflineTaskRepository
import com.executiveos.data.repository.OfflineTemplateRepository
import com.executiveos.domain.repository.CaptureDraftRepository
import com.executiveos.domain.repository.CategoryRepository
import com.executiveos.domain.repository.InboxRepository
import com.executiveos.domain.repository.PrioritySettingsRepository
import com.executiveos.domain.repository.TaskRepository
import com.executiveos.domain.repository.TemplateRepository
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

    @Binds
    fun bindInboxRepository(impl: OfflineInboxRepository): InboxRepository

    @Binds
    fun bindTaskRepository(impl: OfflineTaskRepository): TaskRepository

    @Binds
    fun bindTemplateRepository(impl: OfflineTemplateRepository): TemplateRepository

    @Binds
    fun bindCaptureDraftRepository(impl: DataStoreCaptureDraftRepository): CaptureDraftRepository

    @Binds
    fun bindPrioritySettingsRepository(
        impl: OfflinePrioritySettingsRepository,
    ): PrioritySettingsRepository
}
