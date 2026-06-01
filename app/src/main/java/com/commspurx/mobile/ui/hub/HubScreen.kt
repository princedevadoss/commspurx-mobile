package com.commspurx.mobile.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.commspurx.mobile.network.BackendConnectionStatus
import com.commspurx.mobile.ui.components.CommspurxTopBar
import com.commspurx.mobile.ui.components.HubGradientBackground
import com.commspurx.mobile.ui.components.HubMenuCard
import com.commspurx.mobile.ui.theme.BrandAccent
import com.commspurx.mobile.ui.theme.BrandPrimary
import com.commspurx.mobile.ui.theme.BrandSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    viewModel: HubViewModel,
    connectionStatus: BackendConnectionStatus,
    onOpenNotifications: () -> Unit,
    onOpenDeliveries: () -> Unit,
    onOpenPurchaseExpiring: () -> Unit,
    onOpenSalesExpiring: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val darkTheme = isSystemInDarkTheme()
    var serverReachable by remember {
        mutableStateOf(connectionStatus == BackendConnectionStatus.Connected)
    }
    LaunchedEffect(connectionStatus) {
        serverReachable = when (connectionStatus) {
            BackendConnectionStatus.Connected -> true
            BackendConnectionStatus.Offline,
            BackendConnectionStatus.Unreachable,
            -> false
            BackendConnectionStatus.Checking -> serverReachable
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBadgeDisplay()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLogoutConfirm,
            title = { Text("Sign out?") },
            text = {
                Text(
                    "While signed out you will not receive approvals or activity notifications. " +
                        "Sign in again to resume notifications on this device.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmLogout) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLogoutConfirm) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CommspurxTopBar(
                title = "Commspurx",
                subtitle = state.account?.name ?: state.user.name.ifBlank { state.user.email },
                connectionStatus = connectionStatus,
                userInitial = state.user.name.ifBlank { state.user.email },
                onLogout = viewModel::requestLogout,
            )
        },
    ) { padding ->
        HubGradientBackground(darkTheme = darkTheme) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                    Text(
                        text = "Operations hub",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (serverReachable) {
                            "Monitor deliveries, contract deadlines, and team activity. " +
                                "Data syncs with your server when online."
                        } else {
                            "Monitor deliveries, contract deadlines, and team activity. " +
                                "Offline — showing saved data on this device."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HubMenuCard(
                        title = "Current deliveries",
                        description = "In-progress and scheduled loads",
                        icon = Icons.Default.LocalShipping,
                        badgeCount = state.deliveryBadgeCount,
                        accentBrush = Brush.linearGradient(
                            listOf(BrandPrimary.copy(alpha = 0.25f), BrandPrimary.copy(alpha = 0.05f)),
                        ),
                        onClick = onOpenDeliveries,
                    )
                    HubMenuCard(
                        title = "Purchase contracts expiring",
                        description = "Open purchase contracts ending within 7 days",
                        icon = Icons.Default.ShoppingCart,
                        badgeCount = state.purchaseExpiringCount,
                        accentBrush = Brush.linearGradient(
                            listOf(BrandSecondary.copy(alpha = 0.22f), BrandSecondary.copy(alpha = 0.05f)),
                        ),
                        onClick = onOpenPurchaseExpiring,
                    )
                    HubMenuCard(
                        title = "Sales contracts expiring",
                        description = "Open sales contracts ending within 7 days",
                        icon = Icons.Default.TrendingUp,
                        badgeCount = state.salesExpiringCount,
                        accentBrush = Brush.linearGradient(
                            listOf(BrandAccent.copy(alpha = 0.28f), BrandAccent.copy(alpha = 0.06f)),
                        ),
                        onClick = onOpenSalesExpiring,
                    )
                    HubMenuCard(
                        title = "Notifications",
                        description = "Approvals, imports, and contract alerts",
                        icon = Icons.Default.Notifications,
                        badgeCount = state.notificationBadgeCount,
                        urgentBadge = state.hasUrgentNotifications,
                        onClick = onOpenNotifications,
                    )
                }
        }
    }
}
