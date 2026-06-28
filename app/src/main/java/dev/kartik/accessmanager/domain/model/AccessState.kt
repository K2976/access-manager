package dev.kartik.accessmanager.domain.model

/**
 * Represents whether an application is allowed or blocked from Internet access.
 *
 * Intentionally NOT a Boolean. Expressive types prevent confusion
 * and allow future expansion (e.g., Limited, Scheduled) without refactoring.
 */
enum class AccessState {
    Allowed,
    Blocked,
    ;

    fun toggle(): AccessState = when (this) {
        Allowed -> Blocked
        Blocked -> Allowed
    }
}
