package com.commspurx.mobile.ui.contracts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commspurx.mobile.data.model.ContractSummary
import com.commspurx.mobile.data.model.daysUntilEnd
import com.commspurx.mobile.data.model.displayRef
import com.commspurx.mobile.network.BackendConnectionStatus
import com.commspurx.mobile.ui.components.CommspurxTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContractsScreen(
    viewModel: ContractsViewModel,
    connectionStatus: BackendConnectionStatus,
    onBack: () -> Unit = {},
    embeddedInMainShell: Boolean = false,
) {
    val state by viewModel.uiState.collectAsState()

    val listContent: @Composable (Modifier) -> Unit = { contentModifier ->
        if (state.contracts.isEmpty()) {
            Column(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (state.isOfflineData) {
                        "No cached contracts. Connect to load pending contracts."
                    } else {
                        "No pending contracts in the next 7 days."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = contentModifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.isOfflineData) {
                    item {
                        Text(
                            text = "Offline — showing data saved on this device",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                items(state.contracts, key = { it.id }) { contract ->
                    ContractListCard(contract)
                }
            }
        }
    }

    if (embeddedInMainShell) {
        listContent(Modifier)
    } else {
        Scaffold(
            topBar = {
                CommspurxTopBar(
                    title = state.title,
                    subtitle = state.subtitle,
                    showBack = true,
                    onBack = onBack,
                    connectionStatus = connectionStatus,
                )
            },
        ) { padding ->
            listContent(Modifier.padding(padding))
        }
    }
}

@Composable
private fun ContractListCard(contract: ContractSummary) {
    val daysLeft = contract.daysUntilEnd()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = contract.displayRef(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${contract.oilType} · ${contract.location}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Available ${contract.availableQty} / ${contract.quantityMt} MT · ends ${contract.periodEnd}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (daysLeft != null) {
                Text(
                    text = when {
                        daysLeft < 0 -> "Ended"
                        daysLeft == 0 -> "Ends today"
                        daysLeft == 1 -> "Ends tomorrow"
                        else -> "$daysLeft days left"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (daysLeft <= 2) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
    }
}
