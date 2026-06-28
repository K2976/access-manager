package dev.kartik.accessmanager.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.kartik.accessmanager.database.dao.PolicyDao
import dev.kartik.accessmanager.database.entity.PolicyEntity

/**
 * Room database for AccessManager.
 *
 * Version 1: policies table only.
 * Structured so future entities and migrations can be added without redesign.
 */
@Database(
    entities = [PolicyEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AccessManagerDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao
}
