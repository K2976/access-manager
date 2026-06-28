package dev.kartik.accessmanager.presentation.home

import android.graphics.drawable.Drawable
import dev.kartik.accessmanager.domain.model.AccessState

/**
 * Immutable UI state for the Home screen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val apps: List<AppWithPolicyUiState> = emptyList(),
    val error: String? = null,
)

/**
 * Immutable UI model representing a single app combined with its access policy.
 *
 * This is a presentation-layer model that merges data from two domain sources:
 * [InstalledApp] and [AccessPolicy]. The composable never touches domain models directly.
 */
data class AppWithPolicyUiState(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val accessState: AccessState,
)
