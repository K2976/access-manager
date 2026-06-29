package dev.kartik.accessmanager.presentation.home

import android.content.Context
import android.net.VpnService
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kartik.accessmanager.domain.model.AccessState
import dev.kartik.accessmanager.domain.usecase.GetInstalledAppsUseCase
import dev.kartik.accessmanager.domain.usecase.GetPoliciesUseCase
import dev.kartik.accessmanager.domain.usecase.TogglePolicyUseCase
import dev.kartik.accessmanager.vpn.model.VpnState
import dev.kartik.accessmanager.vpn.service.VpnManager
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
    val vpnManager: VpnManager,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _searchQuery = savedStateHandle.getStateFlow(KEY_SEARCH_QUERY, "")
    private val _filter = savedStateHandle.getStateFlow(KEY_FILTER, AppFilter.BLOCKED)
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeCombinedState()
        loadApps()
    }

    fun onSearchQueryChanged(query: String) {
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }

    fun onClearSearch() {
        savedStateHandle[KEY_SEARCH_QUERY] = ""
    }
    
    fun setFilter(filter: AppFilter) {
        savedStateHandle[KEY_FILTER] = filter
    }

    fun onTogglePolicy(packageName: String) {
        viewModelScope.launch {
            togglePolicy(packageName)
        }
    }

    /**
     * Checks whether VPN permission is needed.
     * Returns true if permission must be requested (caller should launch the intent).
     * Returns false if permission is already granted (service can start immediately).
     */
    fun needsVpnPermission(context: Context): Boolean =
        VpnService.prepare(context) != null

    fun startVpn(context: Context) {
        vpnManager.start(context)
    }

    fun stopVpn(context: Context) {
        vpnManager.stop(context)
    }

    fun onVpnPermissionDenied() {
        vpnManager.updateState(VpnState.Error("VPN permission denied"))
    }

    fun retry() {
        loadApps()
    }

    /**
     * Combines four sources into a single UI state:
     * 1. Installed apps (from PackageManager)
     * 2. Access policies (Room)
     * 3. Search query (user input)
     * 4. VPN state (lifecycle)
     */
    private fun observeCombinedState() {
        combine(
            getInstalledApps(),
            getPolicies(),
            _searchQuery,
            _filter,
            vpnManager.state,
        ) { apps, policies, query, currentFilter, vpnState ->
            val trimmedQuery = query.trim()

            var blockedCount = 0
            var allowedCount = 0

            val allAppItems = apps.map { app ->
                val accessState = policies.getOrDefault(app.packageName, AccessState.Allowed)
                if (accessState == AccessState.Blocked) blockedCount++ else allowedCount++
                
                AppWithPolicyUiState(
                    packageName = app.packageName,
                    appName = app.appName,
                    icon = app.icon,
                    accessState = accessState,
                )
            }

            val filterMatchedApps = allAppItems.filter { app ->
                when (currentFilter) {
                    AppFilter.BLOCKED -> app.accessState == AccessState.Blocked
                    AppFilter.ALLOWED -> app.accessState == AccessState.Allowed
                }
            }

            val filteredApps = if (trimmedQuery.isBlank()) {
                filterMatchedApps
            } else {
                val lowerQuery = trimmedQuery.lowercase()
                filterMatchedApps.filter { app ->
                    app.appName.lowercase().contains(lowerQuery) ||
                        app.packageName.lowercase().contains(lowerQuery)
                }
            }

            HomeUiState(
                isLoading = false,
                apps = filteredApps,
                searchQuery = query,
                filter = currentFilter,
                blockedCount = blockedCount,
                allowedCount = allowedCount,
                vpnState = vpnState,
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
    
    companion object {
        private const val KEY_SEARCH_QUERY = "search_query"
        private const val KEY_FILTER = "filter"
    }
}
