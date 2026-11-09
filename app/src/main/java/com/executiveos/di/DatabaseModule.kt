package com.executiveos.di

import android.content.Context
import androidx.room.Room
import com.executiveos.core.database.AppDatabase
import com.executiveos.core.database.DefaultDataSeedCallback
import com.executiveos.data.local.dao.CategoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /** Sin migración destructiva: si falta una migración la app falla en vez de borrar datos. */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, clock: Clock): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.FILE_NAME)
            .addCallback(DefaultDataSeedCallback(clock))
            .build()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()

    /** Zona del dispositivo: con UTC, por la noche «hoy» sería el día siguiente en América. */
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
