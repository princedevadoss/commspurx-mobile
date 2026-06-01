package com.commspurx.mobile.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.commspurx.mobile.data.local.BadgeStore
import com.commspurx.mobile.data.model.SessionSnapshot
import com.commspurx.mobile.data.repository.ApprovalsRepository
import com.commspurx.mobile.data.repository.AuthRepository
import com.commspurx.mobile.data.repository.BulkImportRepository
import com.commspurx.mobile.data.repository.DeliveriesRepository
import com.commspurx.mobile.data.repository.NotificationsRepository
import com.commspurx.mobile.data.repository.PurchaseContractsRepository
import com.commspurx.mobile.data.repository.SalesContractsRepository
import com.commspurx.mobile.network.ConnectivityMonitor
import com.commspurx.mobile.ui.contracts.ContractListKind
import com.commspurx.mobile.ui.contracts.ContractsViewModel
import com.commspurx.mobile.ui.deliveries.DeliveriesViewModel
import com.commspurx.mobile.ui.hub.HubViewModel
import com.commspurx.mobile.ui.login.LoginViewModel
import com.commspurx.mobile.ui.notifications.NotificationsViewModel

class LoginViewModelFactory(
    private val authRepository: AuthRepository,
    private val onLoggedIn: (SessionSnapshot) -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            return LoginViewModel(authRepository, onLoggedIn) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class HubViewModelFactory(
    private val session: SessionSnapshot,
    private val authRepository: AuthRepository,
    private val notificationsRepository: NotificationsRepository,
    private val approvalsRepository: ApprovalsRepository,
    private val deliveriesRepository: DeliveriesRepository,
    private val purchaseContractsRepository: PurchaseContractsRepository,
    private val salesContractsRepository: SalesContractsRepository,
    private val badgeStore: BadgeStore,
    private val stopNotificationMonitor: () -> Unit,
    private val onLoggedOut: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HubViewModel::class.java)) {
            return HubViewModel(
                user = session.user,
                account = session.account,
                authRepository = authRepository,
                notificationsRepository = notificationsRepository,
                approvalsRepository = approvalsRepository,
                deliveriesRepository = deliveriesRepository,
                purchaseContractsRepository = purchaseContractsRepository,
                salesContractsRepository = salesContractsRepository,
                badgeStore = badgeStore,
                stopNotificationMonitor = stopNotificationMonitor,
                onLoggedOut = onLoggedOut,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ContractsViewModelFactory(
    private val session: SessionSnapshot,
    private val kind: ContractListKind,
    private val purchaseContractsRepository: PurchaseContractsRepository,
    private val salesContractsRepository: SalesContractsRepository,
    private val connectivityMonitor: ConnectivityMonitor,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContractsViewModel::class.java)) {
            return ContractsViewModel(
                user = session.user,
                account = session.account,
                listKind = kind,
                purchaseContractsRepository = purchaseContractsRepository,
                salesContractsRepository = salesContractsRepository,
                connectivityMonitor = connectivityMonitor,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class NotificationsViewModelFactory(
    private val session: SessionSnapshot,
    private val notificationsRepository: NotificationsRepository,
    private val approvalsRepository: ApprovalsRepository,
    private val bulkImportRepository: BulkImportRepository,
    private val badgeStore: BadgeStore,
    private val onNavigateToDeliveries: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationsViewModel::class.java)) {
            return NotificationsViewModel(
                user = session.user,
                account = session.account,
                notificationsRepository = notificationsRepository,
                approvalsRepository = approvalsRepository,
                bulkImportRepository = bulkImportRepository,
                badgeStore = badgeStore,
                onNavigateToDeliveries = onNavigateToDeliveries,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DeliveriesViewModelFactory(
    private val session: SessionSnapshot,
    private val deliveriesRepository: DeliveriesRepository,
    private val badgeStore: BadgeStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeliveriesViewModel::class.java)) {
            return DeliveriesViewModel(
                user = session.user,
                account = session.account,
                deliveriesRepository = deliveriesRepository,
                badgeStore = badgeStore,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
