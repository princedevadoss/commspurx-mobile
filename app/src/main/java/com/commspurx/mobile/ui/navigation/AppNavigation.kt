package com.commspurx.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.commspurx.mobile.CommspurxApplication
import com.commspurx.mobile.data.model.SessionSnapshot
import com.commspurx.mobile.network.BackendConnectionStatus
import com.commspurx.mobile.notifications.MonitorState
import com.commspurx.mobile.ui.contracts.ContractListKind
import com.commspurx.mobile.ui.login.LoginScreen
import com.commspurx.mobile.ui.login.LoginViewModel
import com.commspurx.mobile.ui.main.MainShellScreen
import com.commspurx.mobile.ui.main.MainTab
import com.commspurx.mobile.ui.main.mainTabForDeepLink

private object Routes {
    const val Splash = "splash"
    const val Login = "login"
    const val Main = "main"
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as CommspurxApplication
    val navController = rememberNavController()
    var currentSession by remember { mutableStateOf<SessionSnapshot?>(null) }
    var bootComplete by remember { mutableStateOf(false) }
    var initialMainTab by remember { mutableStateOf(MainTab.Deliveries) }
    val connectionStatus by app.connectivityMonitor.backendStatus.collectAsState()

    LaunchedEffect(Unit) {
        currentSession = app.authRepository.restoreSession()
        bootComplete = true
        currentSession?.user?.let { user ->
            app.startNotificationMonitor(user, resetBaseline = false)
        }
        navController.navigate(if (currentSession != null) Routes.Main else Routes.Login) {
            popUpTo(Routes.Splash) { inclusive = true }
        }
    }

    LaunchedEffect(bootComplete, currentSession) {
        if (!bootComplete || currentSession == null) return@LaunchedEffect
        MonitorState.consumePendingDeepLink()?.let { target ->
            initialMainTab = mainTabForDeepLink(target)
        }
        MonitorState.pendingDeepLink.collect { target ->
            if (target != null) {
                initialMainTab = mainTabForDeepLink(target)
                MonitorState.setPendingDeepLink(null)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
    ) {
        composable(Routes.Splash) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (!bootComplete) {
                    CircularProgressIndicator()
                }
            }
        }

        composable(Routes.Login) {
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModelFactory(app.authRepository) { session ->
                    currentSession = session
                    app.startNotificationMonitor(session.user, resetBaseline = true)
                    app.connectivityMonitor.probeBackend()
                    initialMainTab = MainTab.Deliveries
                    navController.navigate(Routes.Main) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
            )
            LoginScreen(viewModel = loginViewModel)
        }

        composable(Routes.Main) {
            val session = currentSession ?: app.authRepository.getStoredSession()
            if (session == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Main) { inclusive = true }
                    }
                }
                return@composable
            }

            val shellViewModel: com.commspurx.mobile.ui.hub.HubViewModel = viewModel(
                key = session.user.id,
                factory = HubViewModelFactory(
                    session = session,
                    authRepository = app.authRepository,
                    notificationsRepository = app.notificationsRepository,
                    approvalsRepository = app.approvalsRepository,
                    deliveriesRepository = app.deliveriesRepository,
                    purchaseContractsRepository = app.purchaseContractsRepository,
                    salesContractsRepository = app.salesContractsRepository,
                    badgeStore = app.badgeStore,
                    stopNotificationMonitor = { app.stopNotificationMonitor() },
                    onDataSynced = { app.requestNotificationPoll() },
                ) {
                    currentSession = null
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Main) { inclusive = true }
                    }
                },
            )

            val deliveriesViewModel: com.commspurx.mobile.ui.deliveries.DeliveriesViewModel =
                viewModel(
                    key = "main-deliveries-${session.user.id}",
                    factory = DeliveriesViewModelFactory(
                        session = session,
                        deliveriesRepository = app.deliveriesRepository,
                        badgeStore = app.badgeStore,
                        monitorSeenStore = app.monitorSeenStore,
                    ),
                )

            val purchaseViewModel: com.commspurx.mobile.ui.contracts.ContractsViewModel = viewModel(
                key = "main-purchase-${session.user.id}",
                factory = ContractsViewModelFactory(
                    session = session,
                    kind = ContractListKind.PurchaseExpiring,
                    purchaseContractsRepository = app.purchaseContractsRepository,
                    salesContractsRepository = app.salesContractsRepository,
                    connectivityMonitor = app.connectivityMonitor,
                ),
            )

            val salesViewModel: com.commspurx.mobile.ui.contracts.ContractsViewModel = viewModel(
                key = "main-sales-${session.user.id}",
                factory = ContractsViewModelFactory(
                    session = session,
                    kind = ContractListKind.SalesExpiring,
                    purchaseContractsRepository = app.purchaseContractsRepository,
                    salesContractsRepository = app.salesContractsRepository,
                    connectivityMonitor = app.connectivityMonitor,
                ),
            )

            val notificationsViewModel: com.commspurx.mobile.ui.notifications.NotificationsViewModel =
                viewModel(
                    key = "main-notifications-${session.user.id}",
                    factory = NotificationsViewModelFactory(
                        session = session,
                        notificationsRepository = app.notificationsRepository,
                        approvalsRepository = app.approvalsRepository,
                        bulkImportRepository = app.bulkImportRepository,
                        badgeStore = app.badgeStore,
                        monitorSeenStore = app.monitorSeenStore,
                        onNavigateToDeliveries = { initialMainTab = MainTab.Deliveries },
                    ),
                )

            MainShellScreen(
                shellViewModel = shellViewModel,
                deliveriesViewModel = deliveriesViewModel,
                purchaseViewModel = purchaseViewModel,
                salesViewModel = salesViewModel,
                notificationsViewModel = notificationsViewModel,
                connectionStatus = connectionStatus,
                initialTab = initialMainTab,
            )
        }
    }
}
