package dev.kartik.accessmanager.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kartik.accessmanager.database.AccessManagerDatabase
import dev.kartik.accessmanager.database.dao.PolicyDao
import javax.inject.Singleton

/**
 * Hilt module providing Room database and DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AccessManagerDatabase =
        Room.databaseBuilder(
            context,
            AccessManagerDatabase::class.java,
            "access_manager.db",
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun providePolicyDao(database: AccessManagerDatabase): PolicyDao =
        database.policyDao()
}
