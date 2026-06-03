package com.commspurx.mobile.ui.contracts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commspurx.mobile.data.local.BadgeCounts
import com.commspurx.mobile.data.model.AuthAccount
import com.commspurx.mobile.data.model.AuthUser
import com.commspurx.mobile.data.model.ContractSummary
import com.commspurx.mobile.data.repository.PurchaseContractsRepository
import com.commspurx.mobile.data.repository.SalesContractsRepository
import com.commspurx.mobile.network.BackendConnectionStatus
import com.commspurx.mobile.network.ConnectivityMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ContractListKind {
    PurchaseExpiring,
    SalesExpiring,
}

data class ContractsUiState(
    val user: AuthUser,
    val account: AuthAccount?,
    val kind: ContractListKind,
    val contracts: List<ContractSummary> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isOfflineData: Boolean = false,
    val totalItems: Int = 0,
    val hasMore: Boolean = false,
    val errorMessage: String? = null,
) {
    val title: String = when (kind) {
        ContractListKind.PurchaseExpiring -> "Pending purchase contracts"
        ContractListKind.SalesExpiring -> "Pending sales contracts"
    }

    val subtitle: String = "Open contracts ending within 7 days"
    val summaryLine: String? =
        if (totalItems > BadgeCounts.DISPLAY_CAP) {
            "$totalItems contracts — showing in pages of ${BadgeCounts.MOBILE_PAGE_SIZE}"
        } else {
            null
        }
}

class ContractsViewModel(
    user: AuthUser,
    account: AuthAccount?,
    private val listKind: ContractListKind,
    private val purchaseContractsRepository: PurchaseContractsRepository,
    private val salesContractsRepository: SalesContractsRepository,
    private val connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {
    private val accountId = user.accountId
    private val _uiState = MutableStateFlow(
        ContractsUiState(user = user, account = account, kind = listKind),
    )
    val uiState: StateFlow<ContractsUiState> = _uiState.asStateFlow()
    private var nextPage = 1

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            nextPage = 1
            val (rows, meta) = when (listKind) {
                ContractListKind.PurchaseExpiring ->
                    purchaseContractsRepository.loadPage(accountId, page = 1)
                ContractListKind.SalesExpiring ->
                    salesContractsRepository.loadPage(accountId, page = 1)
            }
            val total = meta?.totalItems ?: rows.size
            nextPage = 2
            _uiState.update {
                it.copy(
                    contracts = rows,
                    totalItems = total,
                    hasMore = meta?.let { m -> m.page < m.totalPages } ?: false,
                    isRefreshing = false,
                    isOfflineData = connectivityMonitor.backendStatus.value !=
                        BackendConnectionStatus.Connected,
                )
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isRefreshing || !state.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val page = nextPage
            val (rows, meta) = when (listKind) {
                ContractListKind.PurchaseExpiring ->
                    purchaseContractsRepository.loadPage(accountId, page = page)
                ContractListKind.SalesExpiring ->
                    salesContractsRepository.loadPage(accountId, page = page)
            }
            nextPage = page + 1
            _uiState.update {
                it.copy(
                    contracts = it.contracts + rows,
                    hasMore = meta?.let { m -> m.page < m.totalPages } ?: false,
                    isLoadingMore = false,
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
