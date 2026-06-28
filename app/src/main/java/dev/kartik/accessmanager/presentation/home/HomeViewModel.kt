package dev.kartik.accessmanager.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kartik.accessmanager.domain.model.AccessState
import dev.kartik.accessmanager.domain.usecase.GetInstalledAppsUseCase
import dev.kartik.accessmanager.domain.usecase.GetPoliciesUseCase
import dev.kartik.accessmanager.domain.usecase.TogglePolicyUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val getPolicies: GetPoliciesUseCase,
    private val togglePolicy: TogglePolicyUseCase,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeCombinedState()
        loadApps()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onClearSearch() {
        _searchQuery.value = ""
    }

    fun onTogglePolicy(packageName: String) {
        viewModelScope.launch {
            togglePolicy(packageName)
        }
    }

    fun retry() {
        loadApps()
    }

    /**
     * Combines three sources into a single UI state:
     * 1. Installed apps (from PackageManager)
     * 2. Access policies (in-memory)
     * 3. Search query (user input)
     *
     * Any emission from any source triggers a full recomputation.
     * Filtering happens here on the already-loaded in-memory list — no PackageManager rescan.
     */
    private fun observeCombinedState() {
        combine(
            getInstalledApps(),
            getPolicies(),
            _searchQuery,
        ) { apps, policies, query ->
            val trimmedQuery = query.trim()

            val allAppItems = apps.map { app ->
                AppWithPolicyUiState(
                    packageName = app.packageName,
                    appName = app.appName,
                    icon = app.icon,
                    accessState = policies.getOrDefault(app.packageName, AccessState.Allowed),
                )
            }

            val filteredApps = if (trimmedQuery.isBlank()) {
                allAppItems
            } else {
                val lowerQuery = trimmedQuery.lowercase()
                allAppItems.filter { app ->
                    app.appName.lowercase().contains(lowerQuery) ||
                        app.packageName.lowercase().contains(lowerQuery)
                }
            }

            HomeUiState(
                isLoading = false,
                apps = filteredApps,
                searchQuery = query,
                error = null,
            )
        }
            .onEach { state -> _uiState.value = state }
            .catch { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = throwable.message ?: "Failed to load applications",
                )
            }
            .launchIn(viewModelScope)
    }

    private fun loadApps() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                getInstalledApps.refresh()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load applications",
                )
            }
        }
    }
}
