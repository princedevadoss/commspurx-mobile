package com.commspurx.mobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DeliveryItem(
    val id: String,
    val displayId: String = "",
    val salesContractRef: String = "",
    val purchaseContractRef: String? = null,
    val vehicleNo: String = "",
    val quantityMt: Double = 0.0,
    val actualDeliveredMt: Double? = null,
    val status: String = "ongoing",
    val scheduledDate: String = "",
    val oilType: String? = null,
    val oilName: String? = null,
    val location: String? = null,
)

@Serializable
data class ListMeta(
    val page: Int = 1,
    val pageSize: Int = 20,
    val totalItems: Int = 0,
    val totalPages: Int = 0,
)

@Serializable
data class DeliveriesResponse(
    val data: List<DeliveryItem> = emptyList(),
    val meta: ListMeta? = null,
)

fun DeliveryItem.deliveryRef(): String = displayId.ifBlank { id }

fun DeliveryItem.oilLabel(): String =
    oilName?.takeIf { it.isNotBlank() } ?: oilType?.takeIf { it.isNotBlank() } ?: "—"

fun DeliveryItem.isCompleted(): Boolean = status == "completed"
