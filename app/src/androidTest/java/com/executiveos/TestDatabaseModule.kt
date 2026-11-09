package com.executiveos

import android.content.Context
import androidx.room.Room
import com.executiveos.core.database.AppDatabase
import com.executiveos.core.database.DefaultDataSeedCallback
import com.executiveos.data.local.dao.CategoryDao
import com.executiveos.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Clock
import javax.inject.Singleton

/** Base en memoria: las pruebas en un teléfono real nunca dejan datos en la app del usuario. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context, clock: Clock): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DefaultDataSeedCallback(clock))
            .build()

    @Provides
    fun provideCategoryDao(database: AppDatabase): CategoryDao = database.categoryDao()
}
