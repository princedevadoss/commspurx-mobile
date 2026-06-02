package com.commspurx.mobile.ui.contracts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commspurx.mobile.data.model.AuthAccount
import com.commspurx.mobile.data.model.AuthUser
import com.commspurx.mobile.data.model.ContractSummary
import com.commspurx.mobile.data.repository.PurchaseContractsRepository
import com.commspurx.mobile.data.repository.SalesContractsRepository
import com.commspurx.mobile.network.ConnectivityMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ContractListKind {
    PurchaseExpiring,
    SalesExpiring,
    ;

    fun observeExpiring(
        accountId: String,
        purchaseRepo: PurchaseContractsRepository,
        salesRepo: SalesContractsRepository,
    ): Flow<List<ContractSummary>> = when (this) {
        PurchaseExpiring -> purchaseRepo.observeExpiring(accountId)
        SalesExpiring -> salesRepo.observeExpiring(accountId)
    }

    suspend fun refresh(
        accountId: String,
        purchaseRepo: PurchaseContractsRepository,
        salesRepo: SalesContractsRepository,
    ): Boolean = when (this) {
        PurchaseExpiring -> purchaseRepo.refresh(accountId)
        SalesExpiring -> salesRepo.refresh(accountId)
    }

    suspend fun load(
        accountId: String,
        purchaseRepo: PurchaseContractsRepository,
        salesRepo: SalesContractsRepository,
    ) {
        when (this) {
            PurchaseExpiring -> purchaseRepo.load(accountId)
            SalesExpiring -> salesRepo.load(accountId)
        }
    }
}

data class ContractsUiState(
    val user: AuthUser,
    val account: AuthAccount?,
    val kind: ContractListKind,
    val contracts: List<ContractSummary> = emptyList(),
    val isRefreshing: Boolean = false,
    val isOfflineData: Boolean = false,
    val errorMessage: String? = null,
) {
    val title: String = when (kind) {
        ContractListKind.PurchaseExpiring -> "Pending purchase contracts"
        ContractListKind.SalesExpiring -> "Pending sales contracts"
    }

    val subtitle: String = "Open contracts ending within 7 days"
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

    init {
        observeCache()
        refresh()
    }

    private fun observeCache() {
        viewModelScope.launch {
            val flow = listKind.observeExpiring(
                accountId,
                purchaseContractsRepository,
                salesContractsRepository,
            )
            flow.collect { rows ->
                _uiState.update {
                    it.copy(
                        contracts = rows,
                        isOfflineData = connectivityMonitor.backendStatus.value !=
                            com.commspurx.mobile.network.BackendConnectionStatus.Connected,
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val synced = listKind.refresh(
                accountId,
                purchaseContractsRepository,
                salesContractsRepository,
            )
            if (!synced && _uiState.value.contracts.isEmpty()) {
                listKind.load(
                    accountId,
                    purchaseContractsRepository,
                    salesContractsRepository,
                )
            }
            _uiState.update {
                it.copy(
                    isOfflineData = !synced,
                    errorMessage = null,
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
