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
import com.commspurx.mobile.notifications.DeepLinkTarget
import com.commspurx.mobile.notifications.MonitorState
import com.commspurx.mobile.ui.contracts.ContractListKind
import com.commspurx.mobile.ui.contracts.ContractsScreen
import com.commspurx.mobile.ui.contracts.ContractsViewModel
import com.commspurx.mobile.ui.deliveries.DeliveriesScreen
import com.commspurx.mobile.ui.deliveries.DeliveriesViewModel
import com.commspurx.mobile.ui.hub.HubScreen
import com.commspurx.mobile.ui.hub.HubViewModel
import com.commspurx.mobile.ui.login.LoginScreen
import com.commspurx.mobile.ui.login.LoginViewModel
import com.commspurx.mobile.ui.notifications.NotificationsScreen
import com.commspurx.mobile.ui.notifications.NotificationsViewModel

private object Routes {
    const val Splash = "splash"
    const val Login = "login"
    const val Hub = "hub"
    const val Notifications = "notifications"
    const val Deliveries = "deliveries"
    const val PurchaseExpiring = "purchase_expiring"
    const val SalesExpiring = "sales_expiring"
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val app = context.applicationContext as CommspurxApplication
    val navController = rememberNavController()
    var currentSession by remember { mutableStateOf<SessionSnapshot?>(null) }
    var bootComplete by remember { mutableStateOf(false) }
    val connectionStatus by app.connectivityMonitor.backendStatus.collectAsState()

    LaunchedEffect(Unit) {
        currentSession = app.authRepository.restoreSession()
        currentSession?.user?.let { app.startNotificationMonitor(it) }
        bootComplete = true
        navController.navigate(if (currentSession != null) Routes.Hub else Routes.Login) {
            popUpTo(Routes.Splash) { inclusive = true }
        }
    }

    LaunchedEffect(bootComplete, currentSession) {
        if (!bootComplete || currentSession == null) return@LaunchedEffect
        routeDeepLink(navController, MonitorState.consumePendingDeepLink())
        MonitorState.pendingDeepLink.collect { target ->
            routeDeepLink(navController, target)
            MonitorState.setPendingDeepLink(null)
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
            LaunchedEffect(Unit) {
                app.stopNotificationMonitor()
            }
            val loginViewModel: LoginViewModel = viewModel(
                factory = LoginViewModelFactory(app.authRepository) { session ->
                    currentSession = session
                    app.startNotificationMonitor(session.user)
                    app.connectivityMonitor.probeBackend()
                    navController.navigate(Routes.Hub) {
                        popUpTo(Routes.Login) { inclusive = true }
                    }
                },
            )
            LoginScreen(viewModel = loginViewModel)
        }

        composable(Routes.Hub) {
            val session = currentSession ?: app.authRepository.getStoredSession()
            if (session == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Hub) { inclusive = true }
                    }
                }
                return@composable
            }

            val hubViewModel: HubViewModel = viewModel(
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
                ) {
                    currentSession = null
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Hub) { inclusive = true }
                    }
                },
            )

            HubScreen(
                viewModel = hubViewModel,
                connectionStatus = connectionStatus,
                onOpenNotifications = { navController.navigate(Routes.Notifications) },
                onOpenDeliveries = {
                    hubViewModel.markDeliveriesSectionOpened()
                    navController.navigate(Routes.Deliveries)
                },
                onOpenPurchaseExpiring = { navController.navigate(Routes.PurchaseExpiring) },
                onOpenSalesExpiring = { navController.navigate(Routes.SalesExpiring) },
            )
        }

        composable(Routes.Notifications) {
            val session = currentSession ?: app.authRepository.getStoredSession()
            if (session == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Notifications) { inclusive = true }
                    }
                }
                return@composable
            }

            val notificationsViewModel: NotificationsViewModel = viewModel(
                key = "notifications-${session.user.id}",
                factory = NotificationsViewModelFactory(
                    session = session,
                    notificationsRepository = app.notificationsRepository,
                    approvalsRepository = app.approvalsRepository,
                    bulkImportRepository = app.bulkImportRepository,
                    badgeStore = app.badgeStore,
                    onNavigateToDeliveries = { navController.navigate(Routes.Deliveries) },
                ),
            )

            NotificationsScreen(
                viewModel = notificationsViewModel,
                connectionStatus = connectionStatus,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.Deliveries) {
            val session = currentSession ?: app.authRepository.getStoredSession()
            if (session == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.Deliveries) { inclusive = true }
                    }
                }
                return@composable
            }

            val deliveriesViewModel: DeliveriesViewModel = viewModel(
                key = "deliveries-${session.user.id}",
                factory = DeliveriesViewModelFactory(
                    session = session,
                    deliveriesRepository = app.deliveriesRepository,
                    badgeStore = app.badgeStore,
                ),
            )

            DeliveriesScreen(
                viewModel = deliveriesViewModel,
                connectionStatus = connectionStatus,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PurchaseExpiring) {
            val session = currentSession ?: app.authRepository.getStoredSession()
            if (session == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.PurchaseExpiring) { inclusive = true }
                    }
                }
                return@composable
            }

            val contractsViewModel: ContractsViewModel = viewModel(
                key = "purchase-expiring-${session.user.id}",
                factory = ContractsViewModelFactory(
                    session = session,
                    kind = ContractListKind.PurchaseExpiring,
                    purchaseContractsRepository = app.purchaseContractsRepository,
                    salesContractsRepository = app.salesContractsRepository,
                    connectivityMonitor = app.connectivityMonitor,
                ),
            )

            ContractsScreen(
                viewModel = contractsViewModel,
                connectionStatus = connectionStatus,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SalesExpiring) {
            val session = currentSession ?: app.authRepository.getStoredSession()
            if (session == null) {
                LaunchedEffect(Unit) {
                    navController.navigate(Routes.Login) {
                        popUpTo(Routes.SalesExpiring) { inclusive = true }
                    }
                }
                return@composable
            }

            val contractsViewModel: ContractsViewModel = viewModel(
                key = "sales-expiring-${session.user.id}",
                factory = ContractsViewModelFactory(
                    session = session,
                    kind = ContractListKind.SalesExpiring,
                    purchaseContractsRepository = app.purchaseContractsRepository,
                    salesContractsRepository = app.salesContractsRepository,
                    connectivityMonitor = app.connectivityMonitor,
                ),
            )

            ContractsScreen(
                viewModel = contractsViewModel,
                connectionStatus = connectionStatus,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun routeDeepLink(
    navController: androidx.navigation.NavHostController,
    target: DeepLinkTarget?,
) {
    when (target) {
        DeepLinkTarget.Deliveries -> navController.navigate(Routes.Deliveries)
        DeepLinkTarget.Approvals,
        DeepLinkTarget.Activity,
        is DeepLinkTarget.ActivityItem,
        is DeepLinkTarget.BulkImport,
        -> navController.navigate(Routes.Notifications)
        null -> Unit
    }
}
