package com.commspurx.mobile.ui.deliveries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.commspurx.mobile.data.model.DeliveryItem
import com.commspurx.mobile.data.model.deliveryRef
import com.commspurx.mobile.data.model.isCompleted
import com.commspurx.mobile.data.model.oilLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeliveryListCard(item: DeliveryItem) {
    val completed = item.isCompleted()
    val statusColor = if (completed) {
        Color(0xFF2E7D5A)
    } else {
        MaterialTheme.colorScheme.primary
    }
    val statusBg = if (completed) {
        Color(0xFFE6F4EC)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val statusLabel = if (completed) "Completed" else "In progress"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(statusBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.deliveryRef(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Delivery ID",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatusChip(label = statusLabel, color = statusColor, background = statusBg)
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (item.oilLabel() != "—") {
                    InfoChip(
                        icon = Icons.Default.OilBarrel,
                        label = item.oilLabel(),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
                item.location?.takeIf { it.isNotBlank() }?.let { location ->
                    InfoChip(
                        icon = Icons.Default.LocationOn,
                        label = location,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DetailRow(label = "Sales contract", value = item.salesContractRef)
                    item.purchaseContractRef?.takeIf { it.isNotBlank() }?.let { ref ->
                        DetailRow(label = "Purchase contract", value = ref)
                    }
                    DetailRow(
                        label = if (completed) "Delivered" else "Quantity",
                        value = if (completed && item.actualDeliveredMt != null) {
                            "${formatQuantity(item.actualDeliveredMt)} MT"
                        } else {
                            "${formatQuantity(item.quantityMt)} MT"
                        },
                    )
                    DetailRow(label = "Vehicle", value = item.vehicleNo.ifBlank { "—" })
                    DetailRow(
                        label = if (completed) "Completed on" else "Scheduled",
                        value = formatDeliveryDate(item.scheduledDate),
                        leadingIcon = Icons.Default.Schedule,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    color: Color,
    background: Color,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = background,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun InfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = tint.copy(alpha = 0.1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            leadingIcon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private fun formatDeliveryDate(value: String): String {
    if (value.isBlank()) return "—"
    return runCatching {
        val instant = Instant.parse(value)
        DateTimeFormatter.ofPattern("dd MMM yyyy")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }.getOrElse {
        runCatching {
            val parts = value.split("-")
            if (parts.size == 3) {
                val local = java.time.LocalDate.of(
                    parts[0].toInt(),
                    parts[1].toInt(),
                    parts[2].toInt(),
                )
                DateTimeFormatter.ofPattern("dd MMM yyyy").format(local)
            } else {
                value
            }
        }.getOrDefault(value)
    }
}

@Composable
fun DeliveryListContent(
    deliveries: List<DeliveryItem>,
    emptyMessage: String,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    if (deliveries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(deliveries, key = { it.id }) { item ->
                DeliveryListCard(item = item)
            }
        }
    }
}
