package com.commspurx.mobile

import android.app.Application
import com.commspurx.mobile.data.local.CommspurxDatabase
import com.commspurx.mobile.data.local.BadgeStore
import com.commspurx.mobile.data.local.SessionStore
import com.commspurx.mobile.data.model.AuthUser
import com.commspurx.mobile.data.model.UserRole
import com.commspurx.mobile.data.repository.ApprovalsRepository
import com.commspurx.mobile.data.repository.AuthRepository
import com.commspurx.mobile.data.repository.BulkImportRepository
import com.commspurx.mobile.data.repository.CachedDeliveriesRepository
import com.commspurx.mobile.data.repository.CachedNotificationsRepository
import com.commspurx.mobile.data.repository.DeliveriesRepository
import com.commspurx.mobile.data.repository.NotificationsRepository
import com.commspurx.mobile.data.repository.PurchaseContractsRepository
import com.commspurx.mobile.data.repository.SalesContractsRepository
import com.commspurx.mobile.network.ApiClient
import com.commspurx.mobile.network.ConnectivityMonitor
import com.commspurx.mobile.notifications.NotificationMonitorService

class CommspurxApplication : Application() {
    lateinit var sessionStore: SessionStore
        private set

    lateinit var badgeStore: BadgeStore
        private set

    lateinit var database: CommspurxDatabase
        private set

    lateinit var connectivityMonitor: ConnectivityMonitor
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var notificationsRepository: NotificationsRepository
        private set

    lateinit var approvalsRepository: ApprovalsRepository
        private set

    lateinit var bulkImportRepository: BulkImportRepository
        private set

    lateinit var deliveriesRepository: DeliveriesRepository
        private set

    lateinit var purchaseContractsRepository: PurchaseContractsRepository
        private set

    lateinit var salesContractsRepository: SalesContractsRepository
        private set

    private lateinit var cachedNotificationsRepository: CachedNotificationsRepository
    private lateinit var cachedDeliveriesRepository: CachedDeliveriesRepository

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        badgeStore = BadgeStore(this)
        database = CommspurxDatabase.get(this)

        val apiClient = ApiClient(sessionStore)
        connectivityMonitor = ConnectivityMonitor(this, apiClient.healthApi)

        cachedNotificationsRepository = CachedNotificationsRepository(
            notificationsApi = apiClient.notificationsApi,
            database = database,
            connectivityMonitor = connectivityMonitor,
        )
        cachedDeliveriesRepository = CachedDeliveriesRepository(
            deliveriesApi = apiClient.deliveriesApi,
            database = database,
            connectivityMonitor = connectivityMonitor,
        )

        authRepository = AuthRepository(
            authApi = apiClient.authApi,
            refreshAuthApi = apiClient.refreshAuthApi,
            sessionStore = sessionStore,
            tokenProvider = apiClient.getTokenProvider(),
        )
        notificationsRepository = NotificationsRepository(cachedNotificationsRepository)
        approvalsRepository = ApprovalsRepository(apiClient.approvalsApi)
        bulkImportRepository = BulkImportRepository(apiClient.bulkImportApi)
        deliveriesRepository = DeliveriesRepository(cachedDeliveriesRepository)
        purchaseContractsRepository = PurchaseContractsRepository(
            contractsApi = apiClient.contractsApi,
            database = database,
            connectivityMonitor = connectivityMonitor,
        )
        salesContractsRepository = SalesContractsRepository(
            contractsApi = apiClient.contractsApi,
            database = database,
            connectivityMonitor = connectivityMonitor,
        )
    }

    fun startNotificationMonitor(user: AuthUser) {
        if (sessionStore.getRefreshToken() == null) return
        val role = UserRole.from(user.role) ?: return
        NotificationMonitorService.start(this, role)
    }

    fun stopNotificationMonitor() {
        NotificationMonitorService.stop(this)
    }
}
