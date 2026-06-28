package dev.kartik.accessmanager.data.mapper

import dev.kartik.accessmanager.database.entity.PolicyEntity
import dev.kartik.accessmanager.domain.model.AccessState

/**
 * Maps between Room [PolicyEntity] and domain [AccessState].
 *
 * This ensures Room entities never leak beyond the data layer.
 */
object PolicyMapper {

    fun entityToAccessState(entity: PolicyEntity): AccessState =
        try {
            AccessState.valueOf(entity.accessState)
        } catch (_: IllegalArgumentException) {
            // Graceful fallback if the stored value doesn't match any enum entry.
            // This can happen after an enum refactor with old data.
            AccessState.Allowed
        }

    fun toEntity(packageName: String, state: AccessState): PolicyEntity =
        PolicyEntity(
            packageName = packageName,
            accessState = state.name,
        )
}
