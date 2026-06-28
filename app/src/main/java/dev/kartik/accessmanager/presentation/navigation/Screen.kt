package dev.kartik.accessmanager.presentation.navigation

/**
 * Type-safe navigation destinations.
 *
 * Every screen in the application is represented as an object implementing this interface.
 * This eliminates hardcoded route strings throughout the codebase.
 */
sealed interface Screen {
    val route: String

    data object Home : Screen {
        override val route: String = "home"
    }
}
