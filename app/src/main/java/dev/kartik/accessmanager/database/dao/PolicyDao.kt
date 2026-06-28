package dev.kartik.accessmanager.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.kartik.accessmanager.database.entity.PolicyEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for access policies.
 */
@Dao
interface PolicyDao {

    /** Observe all stored policies reactively. */
    @Query("SELECT * FROM policies")
    fun observeAll(): Flow<List<PolicyEntity>>

    /** Insert or update a policy. */
    @Upsert
    suspend fun upsert(entity: PolicyEntity)

    /** Delete a policy by package name. */
    @Query("DELETE FROM policies WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    /** Retrieve a single policy by package name, or null if not found. */
    @Query("SELECT * FROM policies WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): PolicyEntity?

    /** Delete all policies. */
    @Query("DELETE FROM policies")
    suspend fun deleteAll()
}
