package dev.kartik.accessmanager.domain.model

/**
 * Represents the access policy for a single application.
 *
 * This model is intentionally separate from [InstalledApp].
 * An installed app describes what exists on the device.
 * A policy describes what the user has decided about that app's access.
 */
data class AccessPolicy(
    val packageName: String,
    val accessState: AccessState,
)
