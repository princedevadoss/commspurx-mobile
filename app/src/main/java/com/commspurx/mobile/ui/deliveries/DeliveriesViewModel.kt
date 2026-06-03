package com.commspurx.mobile.ui.deliveries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commspurx.mobile.data.local.BadgeStore
import com.commspurx.mobile.data.local.MonitorSeenStore
import com.commspurx.mobile.data.model.AuthAccount
import com.commspurx.mobile.data.model.AuthUser
import com.commspurx.mobile.data.model.DeliveryItem
import com.commspurx.mobile.data.model.isCompleted
import com.commspurx.mobile.data.repository.DeliveriesRepository
import com.commspurx.mobile.notifications.MonitorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DeliveryTab {
    Current,
    Completed,
}

data class DeliveriesUiState(
    val user: AuthUser,
    val account: AuthAccount?,
    val selectedTab: DeliveryTab = DeliveryTab.Current,
    val currentDeliveries: List<DeliveryItem> = emptyList(),
    val completedDeliveries: List<DeliveryItem> = emptyList(),
    val currentTabBadge: Int = 0,
    val completedTabBadge: Int = 0,
    val errorMessage: String? = null,
) {
    val visibleDeliveries: List<DeliveryItem>
        get() = when (selectedTab) {
            DeliveryTab.Current -> currentDeliveries
            DeliveryTab.Completed -> completedDeliveries
        }
}

class DeliveriesViewModel(
    user: AuthUser,
    account: AuthAccount?,
    private val deliveriesRepository: DeliveriesRepository,
    private val badgeStore: BadgeStore,
    private val monitorSeenStore: MonitorSeenStore,
) : ViewModel() {
    private val accountId = user.accountId
    private val _uiState = MutableStateFlow(DeliveriesUiState(user = user, account = account))
    val uiState: StateFlow<DeliveriesUiState> = _uiState.asStateFlow()

    init {
        observeCache()
        observeMonitorSnapshots()
        syncInBackground()
    }

    fun selectTab(tab: DeliveryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        markSelectedTabSeen()
    }

    fun markHubAndCurrentTabSeen() {
        val state = _uiState.value
        val currentIds = state.currentDeliveries.map { it.id }
        val completedIds = state.completedDeliveries.map { it.id }
        viewModelScope.launch {
            badgeStore.markHubDeliveriesSeen(currentIds)
            badgeStore.markCurrentTabSeen(currentIds)
            monitorSeenStore.markPendingDeliveriesSeen(currentIds)
            if (state.selectedTab == DeliveryTab.Completed) {
                monitorSeenStore.markCompletedDeliveriesSeen(completedIds)
            }
            refreshTabBadges()
        }
    }

    fun refresh() {
        syncInBackground()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun observeCache() {
        viewModelScope.launch {
            deliveriesRepository.observeCurrent(accountId).collect { current ->
                _uiState.update { it.copy(currentDeliveries = current) }
                refreshTabBadges()
            }
        }
        viewModelScope.launch {
            deliveriesRepository.observeCompleted(accountId).collect { completed ->
                _uiState.update { it.copy(completedDeliveries = completed) }
                refreshTabBadges()
            }
        }
    }

    private fun syncInBackground() {
        viewModelScope.launch {
            runCatching {
                val current = deliveriesRepository.listCurrent(accountId)
                val completed = deliveriesRepository.listCompleted(accountId)
                _uiState.update {
                    it.copy(
                        currentDeliveries = current,
                        completedDeliveries = completed,
                        errorMessage = null,
                    )
                }
                refreshTabBadges()
            }
        }
    }

    private var lastCurrentCount = 0

    private fun observeMonitorSnapshots() {
        viewModelScope.launch {
            MonitorState.snapshots.collect { snapshot ->
                val current = snapshot.pendingDeliveries.filter { !it.isCompleted() }
                val currentShrunk = current.size < lastCurrentCount
                lastCurrentCount = current.size
                _uiState.update { it.copy(currentDeliveries = current) }
                refreshTabBadges()
                if (currentShrunk) {
                    viewModelScope.launch {
                        runCatching {
                            deliveriesRepository.listCompleted(accountId)
                        }.onSuccess { completed ->
                            _uiState.update { it.copy(completedDeliveries = completed) }
                            refreshTabBadges()
                        }
                    }
                }
            }
        }
    }

    private fun markSelectedTabSeen() {
        viewModelScope.launch {
            val state = _uiState.value
            when (state.selectedTab) {
                DeliveryTab.Current -> {
                    val ids = state.currentDeliveries.map { it.id }
                    badgeStore.markHubDeliveriesSeen(ids)
                    badgeStore.markCurrentTabSeen(ids)
                    monitorSeenStore.markPendingDeliveriesSeen(ids)
                }
                DeliveryTab.Completed -> {
                    val ids = state.completedDeliveries.map { it.id }
                    badgeStore.markCompletedTabSeen(ids)
                    monitorSeenStore.markCompletedDeliveriesSeen(ids)
                }
            }
            refreshTabBadges()
        }
    }

    private suspend fun refreshTabBadges() {
        val state = _uiState.value
        val currentIds = state.currentDeliveries.map { it.id }
        val completedIds = state.completedDeliveries.map { it.id }
        _uiState.update {
            it.copy(
                currentTabBadge = badgeStore.currentTabBadge(currentIds),
                completedTabBadge = badgeStore.completedTabBadge(completedIds),
            )
        }
    }
}
