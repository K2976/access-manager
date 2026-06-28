package dev.kartik.accessmanager.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a persisted access policy.
 *
 * Stores only what is required: the package name and its access state.
 * The access state is stored as a String (enum name) for readability
 * and forward compatibility.
 */
@Entity(tableName = "policies")
data class PolicyEntity(
    @PrimaryKey
    val packageName: String,
    val accessState: String,
)
