package com.commspurx.mobile.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchase_contracts")
data class PurchaseContractEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val refId: String,
    val oilType: String,
    val location: String,
    val quantityMt: Double,
    val availableQty: Double,
    val periodEnd: String,
    val status: String,
    val rate: Double,
    val seller: String,
    val syncedAt: Long,
)

@Entity(tableName = "sales_contracts")
data class SalesContractEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val refId: String,
    val oilType: String,
    val location: String,
    val quantityMt: Double,
    val availableQty: Double,
    val periodEnd: String,
    val status: String,
    val rate: Double,
    val buyer: String,
    val syncedAt: Long,
)

@Entity(tableName = "deliveries")
data class DeliveryEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val displayId: String,
    val salesContractRef: String,
    val purchaseContractRef: String?,
    val vehicleNo: String,
    val quantityMt: Double,
    val actualDeliveredMt: Double?,
    val status: String,
    val scheduledDate: String,
    val oilName: String?,
    val location: String?,
    val isCompleted: Boolean,
    val syncedAt: Long,
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val title: String,
    val message: String,
    val entityType: String,
    val entityId: String,
    val priority: String = "normal",
    val readAt: String?,
    val createdAt: String,
    val syncedAt: Long,
)
