package com.commspurx.mobile.ui.main

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.commspurx.mobile.network.BackendConnectionStatus
import com.commspurx.mobile.ui.components.AppShellBackground
import com.commspurx.mobile.ui.components.CommspurxTopBar
import com.commspurx.mobile.ui.contracts.ContractsScreen
import com.commspurx.mobile.ui.contracts.ContractsViewModel
import com.commspurx.mobile.ui.deliveries.DeliveriesScreen
import com.commspurx.mobile.ui.deliveries.DeliveriesViewModel
import com.commspurx.mobile.ui.hub.HubViewModel
import com.commspurx.mobile.ui.notifications.NotificationsScreen
import com.commspurx.mobile.ui.notifications.NotificationsViewModel

enum class MainTab(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    Deliveries(
        title = "Deliveries",
        subtitle = "Pending and completed loads",
        icon = Icons.Default.LocalShipping,
    ),
    Purchase(
        title = "Purchase contracts",
        subtitle = "Open contracts requiring attention",
        icon = Icons.Default.ShoppingCart,
    ),
    Sales(
        title = "Sales contracts",
        subtitle = "Open contracts requiring attention",
        icon = Icons.Default.TrendingUp,
    ),
    Notifications(
        title = "Notifications",
        subtitle = "Approvals and activity",
        icon = Icons.Default.Notifications,
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainShellScreen(
    shellViewModel: HubViewModel,
    deliveriesViewModel: DeliveriesViewModel,
    purchaseViewModel: ContractsViewModel,
    salesViewModel: ContractsViewModel,
    notificationsViewModel: NotificationsViewModel,
    connectionStatus: BackendConnectionStatus,
    initialTab: MainTab = MainTab.Deliveries,
) {
    val shellState by shellViewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
    val darkTheme = isSystemInDarkTheme()

    LaunchedEffect(initialTab) {
        selectedTab = initialTab
    }

    LaunchedEffect(selectedTab) {
        shellViewModel.markMainTabVisited(selectedTab)
    }

    LaunchedEffect(selectedTab, shellState.pendingDeliveries) {
        if (selectedTab == MainTab.Deliveries && shellState.pendingDeliveries > 0) {
            shellViewModel.markDeliveriesSectionOpened()
        }
    }

    LaunchedEffect(selectedTab, shellState.purchaseExpiringCount) {
        if (selectedTab == MainTab.Purchase) {
            shellViewModel.markPurchaseSectionOpened()
        }
    }

    LaunchedEffect(selectedTab, shellState.salesExpiringCount) {
        if (selectedTab == MainTab.Sales) {
            shellViewModel.markSalesSectionOpened()
        }
    }

    LaunchedEffect(
        selectedTab,
        shellState.unreadNotifications,
        shellState.pendingApprovals,
    ) {
        if (selectedTab == MainTab.Notifications &&
            (shellState.unreadNotifications > 0 || shellState.pendingApprovals > 0)
        ) {
            shellViewModel.markNotificationsSectionOpened()
            notificationsViewModel.markHubNotificationsSeen()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                shellViewModel.refreshBadgeDisplay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (shellState.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = shellViewModel::dismissLogoutConfirm,
            title = { Text("Sign out?") },
            text = {
                Text(
                    "Your session stays on this device until you sign out. " +
                        "Background alerts for pending items pause while signed out.",
                )
            },
            confirmButton = {
                TextButton(onClick = shellViewModel::confirmLogout) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = shellViewModel::dismissLogoutConfirm) {
                    Text("Cancel")
                }
            },
        )
    }

    val tabBadges = mapOf(
        MainTab.Deliveries to shellState.deliveryBadgeCount,
        MainTab.Purchase to shellState.purchaseBadgeCount,
        MainTab.Sales to shellState.salesBadgeCount,
        MainTab.Notifications to shellState.notificationBadgeCount,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CommspurxTopBar(
                title = selectedTab.title,
                subtitle = shellState.account?.name
                    ?: shellState.user.name.ifBlank { shellState.user.email },
                connectionStatus = connectionStatus,
                userInitial = shellState.user.name.ifBlank { shellState.user.email },
                onLogout = shellViewModel::requestLogout,
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                MainTab.entries.forEach { tab ->
                    val badgeCount = tabBadges[tab] ?: 0
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (badgeCount > 0) {
                                        Badge {
                                            val label = when (tab) {
                                                MainTab.Purchase -> shellState.purchaseBadgeLabel
                                                MainTab.Sales -> shellState.salesBadgeLabel
                                                MainTab.Notifications -> shellState.notificationBadgeLabel
                                                else -> badgeCount.coerceAtMost(99).toString()
                                            }
                                            Text(label.ifBlank { badgeCount.toString() })
                                        }
                                    }
                                },
                            ) {
                                Icon(tab.icon, contentDescription = tab.title)
                            }
                        },
                        label = {
                            Text(
                                text = when (tab) {
                                    MainTab.Deliveries -> "Deliveries"
                                    MainTab.Purchase -> "Purchase"
                                    MainTab.Sales -> "Sales"
                                    MainTab.Notifications -> "Alerts"
                                },
                                fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        AppShellBackground(darkTheme = darkTheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (selectedTab) {
                    MainTab.Deliveries -> DeliveriesScreen(
                        viewModel = deliveriesViewModel,
                        connectionStatus = connectionStatus,
                        embeddedInMainShell = true,
                    )
                    MainTab.Purchase -> ContractsScreen(
                        viewModel = purchaseViewModel,
                        connectionStatus = connectionStatus,
                        embeddedInMainShell = true,
                    )
                    MainTab.Sales -> ContractsScreen(
                        viewModel = salesViewModel,
                        connectionStatus = connectionStatus,
                        embeddedInMainShell = true,
                    )
                    MainTab.Notifications -> NotificationsScreen(
                        viewModel = notificationsViewModel,
                        connectionStatus = connectionStatus,
                        embeddedInMainShell = true,
                    )
                }
            }
        }
    }
}

fun mainTabForDeepLink(target: com.commspurx.mobile.notifications.DeepLinkTarget?): MainTab =
    when (target) {
        com.commspurx.mobile.notifications.DeepLinkTarget.Deliveries -> MainTab.Deliveries
        com.commspurx.mobile.notifications.DeepLinkTarget.PurchaseContracts -> MainTab.Purchase
        com.commspurx.mobile.notifications.DeepLinkTarget.SalesContracts -> MainTab.Sales
        com.commspurx.mobile.notifications.DeepLinkTarget.Approvals,
        com.commspurx.mobile.notifications.DeepLinkTarget.Activity,
        is com.commspurx.mobile.notifications.DeepLinkTarget.ActivityItem,
        is com.commspurx.mobile.notifications.DeepLinkTarget.BulkImport,
        -> MainTab.Notifications
        null -> MainTab.Deliveries
    }
