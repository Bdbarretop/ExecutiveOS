package com.executiveos.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.executiveos.data.local.dao.CategoryDao
import com.executiveos.data.local.entity.CategoryEntity

@Database(
    entities = [CategoryEntity::class],
    version = AppDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "executive_os.db"
    }
}
