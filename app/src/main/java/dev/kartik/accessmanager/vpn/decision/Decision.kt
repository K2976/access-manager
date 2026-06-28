package dev.kartik.accessmanager.vpn.decision

/**
 * A strongly typed representation of the action to be taken on a network connection.
 *
 * Avoids Boolean returns to allow future extensibility for policy types like:
 * - TemporaryAllow
 * - RateLimit
 * - LogOnly
 */
sealed interface Decision {
    /** The connection is explicitly permitted. */
    data object Allow : Decision

    /** The connection is explicitly denied. */
    data object Block : Decision

    /** The connection could not be evaluated due to missing metadata. */
    data class Unknown(val reason: String) : Decision
}
