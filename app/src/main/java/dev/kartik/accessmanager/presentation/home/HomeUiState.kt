package dev.kartik.accessmanager.presentation.home

import dev.kartik.accessmanager.domain.model.InstalledApp

/**
 * Immutable UI state for the Home screen.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val error: String? = null,
)
