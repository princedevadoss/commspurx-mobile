package com.commspurx.mobile.ui.deliveries

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.commspurx.mobile.network.BackendConnectionStatus
import com.commspurx.mobile.ui.components.CommspurxTopBar
import com.commspurx.mobile.ui.components.TabBadgeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveriesScreen(
    viewModel: DeliveriesViewModel,
    connectionStatus: BackendConnectionStatus,
    onBack: () -> Unit = {},
    embeddedInMainShell: Boolean = false,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTab = state.selectedTab

    LaunchedEffect(Unit) {
        viewModel.markHubAndCurrentTabSeen()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val body: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(modifier = contentModifier.fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == DeliveryTab.Current,
                    onClick = { viewModel.selectTab(DeliveryTab.Current) },
                    text = {
                        TabBadgeLabel(
                            label = "Current",
                            badgeCount = state.currentTabBadge,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == DeliveryTab.Completed,
                    onClick = { viewModel.selectTab(DeliveryTab.Completed) },
                    text = {
                        TabBadgeLabel(
                            label = "Completed",
                            badgeCount = state.completedTabBadge,
                        )
                    },
                )
            }

            DeliveryListContent(
                deliveries = state.visibleDeliveries,
                emptyMessage = when (selectedTab) {
                    DeliveryTab.Current -> "No pending deliveries."
                    DeliveryTab.Completed -> "No completed deliveries yet."
                },
            )
        }
    }

    if (embeddedInMainShell) {
        body(Modifier)
    } else {
        Scaffold(
            topBar = {
                CommspurxTopBar(
                    title = "Deliveries",
                    subtitle = "Track in-progress and completed loads",
                    showBack = true,
                    onBack = onBack,
                    connectionStatus = connectionStatus,
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            body(Modifier.padding(padding))
        }
    }
}
