package com.commspurx.mobile.data.local

import com.commspurx.mobile.data.local.entity.DeliveryEntity
import com.commspurx.mobile.data.local.entity.NotificationEntity
import com.commspurx.mobile.data.local.entity.PurchaseContractEntity
import com.commspurx.mobile.data.local.entity.SalesContractEntity
import com.commspurx.mobile.data.model.ContractSummary
import com.commspurx.mobile.data.model.DeliveryItem
import com.commspurx.mobile.data.model.NotificationItem
import com.commspurx.mobile.data.model.isCompleted

fun PurchaseContractEntity.toSummary(): ContractSummary = ContractSummary(
    id = id,
    refId = refId,
    oilType = oilType,
    location = location,
    quantityMt = quantityMt,
    availableQty = availableQty,
    periodEnd = periodEnd,
    status = status,
    rate = rate,
    counterparty = seller,
)

fun SalesContractEntity.toSummary(): ContractSummary = ContractSummary(
    id = id,
    refId = refId,
    oilType = oilType,
    location = location,
    quantityMt = quantityMt,
    availableQty = availableQty,
    periodEnd = periodEnd,
    status = status,
    rate = rate,
    counterparty = buyer,
)

fun DeliveryEntity.toItem(): DeliveryItem = DeliveryItem(
    id = id,
    displayId = displayId,
    salesContractRef = salesContractRef,
    purchaseContractRef = purchaseContractRef,
    vehicleNo = vehicleNo,
    quantityMt = quantityMt,
    actualDeliveredMt = actualDeliveredMt,
    status = status,
    scheduledDate = scheduledDate,
    oilType = null,
    oilName = oilName,
    location = location,
)

fun DeliveryItem.toEntity(accountId: String, syncedAt: Long): DeliveryEntity = DeliveryEntity(
    id = id,
    accountId = accountId,
    displayId = displayId,
    salesContractRef = salesContractRef,
    purchaseContractRef = purchaseContractRef,
    vehicleNo = vehicleNo,
    quantityMt = quantityMt,
    actualDeliveredMt = actualDeliveredMt,
    status = status,
    scheduledDate = scheduledDate,
    oilName = oilName,
    location = location,
    isCompleted = isCompleted(),
    syncedAt = syncedAt,
)

fun NotificationEntity.toItem(): NotificationItem = NotificationItem(
    id = id,
    title = title,
    message = message,
    entityType = entityType,
    entityId = entityId,
    priority = priority,
    readAt = readAt,
    createdAt = createdAt,
)

fun NotificationItem.toEntity(accountId: String, syncedAt: Long): NotificationEntity = NotificationEntity(
    id = id,
    accountId = accountId,
    title = title,
    message = message,
    entityType = entityType,
    entityId = entityId,
    priority = priority,
    readAt = readAt,
    createdAt = createdAt,
    syncedAt = syncedAt,
)

fun ContractSummary.toPurchaseEntity(accountId: String, syncedAt: Long, seller: String): PurchaseContractEntity =
    PurchaseContractEntity(
        id = id,
        accountId = accountId,
        refId = refId,
        oilType = oilType,
        location = location,
        quantityMt = quantityMt,
        availableQty = availableQty,
        periodEnd = periodEnd,
        status = status,
        rate = rate,
        seller = seller.ifBlank { counterparty },
        syncedAt = syncedAt,
    )

fun ContractSummary.toSalesEntity(accountId: String, syncedAt: Long, buyer: String): SalesContractEntity =
    SalesContractEntity(
        id = id,
        accountId = accountId,
        refId = refId,
        oilType = oilType,
        location = location,
        quantityMt = quantityMt,
        availableQty = availableQty,
        periodEnd = periodEnd,
        status = status,
        rate = rate,
        buyer = buyer.ifBlank { counterparty },
        syncedAt = syncedAt,
    )
