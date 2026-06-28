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

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeCombinedState()
        loadApps()
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
     * Combines the installed apps Flow with the policies Flow into a single UI state.
     *
     * Every time either source emits, the UI state is recomputed.
     * This is the reactive state combination pattern using Kotlin Flow's combine.
     */
    private fun observeCombinedState() {
        combine(
            getInstalledApps(),
            getPolicies(),
        ) { apps, policies ->
            val appItems = apps.map { app ->
                AppWithPolicyUiState(
                    packageName = app.packageName,
                    appName = app.appName,
                    icon = app.icon,
                    accessState = policies.getOrDefault(app.packageName, AccessState.Allowed),
                )
            }
            HomeUiState(
                isLoading = false,
                apps = appItems,
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
