package com.commspurx.mobile

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.commspurx.mobile.data.local.CommspurxDatabase
import com.commspurx.mobile.data.local.BadgeStore
import com.commspurx.mobile.data.local.MonitorSeenStore
import com.commspurx.mobile.data.local.SessionStore
import com.commspurx.mobile.data.model.AuthUser
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
import com.commspurx.mobile.network.TokenProvider
import com.commspurx.mobile.notifications.FcmTokenRegistrar
import com.commspurx.mobile.notifications.NotificationMonitorScheduler
import com.commspurx.mobile.notifications.NotificationPollManager

class CommspurxApplication : Application() {
    lateinit var sessionStore: SessionStore
        private set

    lateinit var badgeStore: BadgeStore
        private set

    lateinit var monitorSeenStore: MonitorSeenStore
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
    private lateinit var tokenProvider: TokenProvider

    lateinit var apiClient: ApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        initFirebaseIfNeeded()
        sessionStore = SessionStore(this)
        badgeStore = BadgeStore(this)
        monitorSeenStore = MonitorSeenStore(this)
        database = CommspurxDatabase.get(this)

        apiClient = ApiClient(sessionStore)
        tokenProvider = apiClient.getTokenProvider()
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
            appContext = this,
            authApi = apiClient.authApi,
            refreshAuthApi = apiClient.refreshAuthApi,
            sessionStore = sessionStore,
            tokenProvider = tokenProvider,
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

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (sessionStore.getRefreshToken() == null) return
                    NotificationMonitorScheduler.ensureRunning(
                        context = this@CommspurxApplication,
                        resetBaseline = false,
                        fromForeground = true,
                    )
                    NotificationPollManager.schedulePoll(
                        context = this@CommspurxApplication,
                        resetBaseline = false,
                        debounce = true,
                    )
                }

                override fun onStop(owner: LifecycleOwner) {
                    if (sessionStore.getRefreshToken() == null) return
                    NotificationMonitorScheduler.armBackgroundPolling(this@CommspurxApplication)
                    NotificationMonitorScheduler.enqueueBackgroundPoll(this@CommspurxApplication)
                }
            },
        )

        if (sessionStore.getRefreshToken() != null) {
            NotificationMonitorScheduler.armBackgroundPolling(this)
            FcmTokenRegistrar.syncTokenIfPossible(this)
        }
    }

    private fun initFirebaseIfNeeded() {
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        val apiKey = BuildConfig.FIREBASE_API_KEY.trim()
        val appId = BuildConfig.FIREBASE_APP_ID.trim()
        val projectId = BuildConfig.FIREBASE_PROJECT_ID.trim()
        if (apiKey.isEmpty() || appId.isEmpty() || projectId.isEmpty()) return
        FirebaseApp.initializeApp(
            this,
            FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setApplicationId(appId)
                .setProjectId(projectId)
                .build(),
        )
    }

    fun syncAuthTokenFromStore() {
        tokenProvider.syncFromStore()
    }

    fun startNotificationMonitor(user: AuthUser, resetBaseline: Boolean = false) {
        if (sessionStore.getRefreshToken() == null) return
        syncAuthTokenFromStore()
        FcmTokenRegistrar.syncTokenIfPossible(this)
        NotificationMonitorScheduler.ensureRunning(
            context = this,
            resetBaseline = resetBaseline,
            fromForeground = true,
        )
        if (resetBaseline) {
            NotificationPollManager.schedulePoll(this, resetBaseline = true, debounce = false)
        }
    }

    /** After hub sync — detect new items without re-alerting acknowledged ones. */
    fun requestNotificationPoll() {
        if (sessionStore.getRefreshToken() == null) return
        syncAuthTokenFromStore()
        NotificationPollManager.schedulePoll(this, resetBaseline = false, debounce = true)
    }

    fun stopNotificationMonitor() {
        FcmTokenRegistrar.unregisterCurrentToken(this)
        NotificationMonitorScheduler.stop(this)
    }
}
