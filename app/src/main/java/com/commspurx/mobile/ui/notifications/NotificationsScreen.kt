package com.commspurx.mobile.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commspurx.mobile.data.model.APPROVAL_ENTITY_LABELS
import com.commspurx.mobile.data.model.ApprovalItem
import com.commspurx.mobile.data.model.NotificationItem
import com.commspurx.mobile.network.BackendConnectionStatus
import com.commspurx.mobile.ui.components.BulkImportResultSheet
import com.commspurx.mobile.ui.components.CommspurxTopBar
import com.commspurx.mobile.ui.components.TabBadgeLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    connectionStatus: BackendConnectionStatus,
    onBack: () -> Unit = {},
    embeddedInMainShell: Boolean = false,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val selectedTab = state.selectedTab

    LaunchedEffect(Unit) {
        viewModel.markHubNotificationsSeen()
    }

    LaunchedEffect(state.errorMessage, state.actionMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.actionMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    if (state.showBulkImportSheet) {
        BulkImportResultSheet(
            job = state.selectedJob,
            isLoading = state.isLoadingJob,
            onDismiss = viewModel::dismissBulkImportSheet,
        )
    }

    val body: @Composable (Modifier) -> Unit = { contentModifier ->
        Column(modifier = contentModifier.fillMaxSize()) {
            if (state.showApprovalsTab) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        text = {
                            TabBadgeLabel(
                                label = "Approvals",
                                badgeCount = state.approvalsTabBadge,
                            )
                        },
                        icon = { Icon(Icons.Default.TaskAlt, contentDescription = null) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        text = {
                            TabBadgeLabel(
                                label = "Activity",
                                badgeCount = state.activityTabBadge,
                            )
                        },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    )
                }
            }

            when {
                state.showApprovalsTab && selectedTab == 0 -> {
                    ApprovalsList(
                        items = state.approvals,
                        actingKey = state.actingApprovalKey,
                        onApprove = { viewModel.decideApproval(it, "approve") },
                        onReject = { viewModel.decideApproval(it, "reject") },
                    )
                }
                else -> {
                    ActivityNotificationsSection(
                        items = state.notifications,
                        isMarkingAllSeen = state.isMarkingAllSeen,
                        onMarkAllSeen = viewModel::markAllSeen,
                        onItemClick = viewModel::onNotificationClick,
                    )
                }
            }
        }
    }

    if (embeddedInMainShell) {
        body(Modifier)
    } else {
        Scaffold(
            topBar = {
                CommspurxTopBar(
                    title = "Notifications",
                    subtitle = state.account?.name ?: state.user.email,
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

@Composable
private fun ApprovalsList(
    items: List<ApprovalItem>,
    actingKey: String?,
    onApprove: (ApprovalItem) -> Unit,
    onReject: (ApprovalItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(message = "No items pending approval.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { "${it.entityType}:${it.id}" }) { item ->
            val rowKey = "${item.entityType}:${item.id}"
            ApprovalCard(
                item = item,
                busy = actingKey == rowKey,
                onApprove = { onApprove(item) },
                onReject = { onReject(item) },
            )
        }
    }
}

@Composable
private fun ActivityNotificationsSection(
    items: List<NotificationItem>,
    isMarkingAllSeen: Boolean,
    onMarkAllSeen: () -> Unit,
    onItemClick: (NotificationItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (items.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onMarkAllSeen,
                    enabled = !isMarkingAllSeen,
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isMarkingAllSeen) "Marking…" else "Mark seen")
                }
            }
        }
        NotificationsList(items = items, onItemClick = onItemClick)
    }
}

@Composable
private fun NotificationsList(
    items: List<NotificationItem>,
    onItemClick: (NotificationItem) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(message = "No new notifications.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items.sortedWith(
                compareBy<NotificationItem> { !it.isHighPriority }.thenByDescending { it.createdAt },
            ),
            key = { it.id },
        ) { item ->
            NotificationCard(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApprovalCard(
    item: ApprovalItem,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = APPROVAL_ENTITY_LABELS[item.entityType] ?: item.entityType,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.createdByName?.let { name ->
                Text(
                    text = "Submitted by $name",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                text = formatTimestamp(item.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onReject, enabled = !busy) {
                    Text("Reject")
                }
                Spacer(modifier = Modifier.padding(4.dp))
                Button(onClick = onApprove, enabled = !busy) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Text("Approve")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
) {
    val urgent = item.isHighPriority
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (urgent) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (urgent) {
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (urgent) 6.dp else 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (urgent) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 6.dp),
            )
            if (urgent) {
                Text(
                    text = "HIGH PRIORITY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (item.entityType == "bulk_import") {
                Text(
                    text = "Tap to view import results",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (urgent && item.entityType == "sales_contract") {
                Text(
                    text = "Map purchase stock or create an on-demand request",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (item.entityType == "delivery") {
                Text(
                    text = "Tap to view pending deliveries",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Text(
                text = formatTimestamp(item.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun formatTimestamp(value: String): String {
    return runCatching {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrDefault(value)
}
