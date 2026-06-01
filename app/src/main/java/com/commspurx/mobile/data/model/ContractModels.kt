package com.commspurx.mobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiOilProduct(
    val name: String = "",
    val code: String = "",
)

@Serializable
data class ApiParty(
    val name: String = "",
)

@Serializable
data class ApiContractRow(
    val id: String = "",
    val refId: String = "",
    @SerialName("reference") val reference: String = "",
    val oilProduct: ApiOilProduct? = null,
    val oilType: String? = null,
    val location: String = "",
    val quantityMt: Double = 0.0,
    val orderedQty: Double = 0.0,
    val availableQty: Double = 0.0,
    val availableQuantityMt: Double = 0.0,
    val periodEnd: String = "",
    val status: String = "",
    val rate: Double = 0.0,
    val pricePerMt: Double = 0.0,
    val seller: ApiParty? = null,
    val buyer: ApiParty? = null,
) {
    fun toSummary(): ContractSummary = ContractSummary(
        id = id,
        refId = refId.ifBlank { reference },
        oilType = oilProduct?.name?.takeIf { it.isNotBlank() } ?: oilType.orEmpty(),
        location = location,
        quantityMt = quantityMt.takeIf { it > 0 } ?: orderedQty,
        availableQty = availableQty.takeIf { it > 0 } ?: availableQuantityMt,
        periodEnd = periodEnd,
        status = status,
        rate = rate.takeIf { it > 0 } ?: pricePerMt,
        counterparty = seller?.name?.takeIf { it.isNotBlank() }
            ?: buyer?.name.orEmpty(),
    )
}

@Serializable
data class ContractSummary(
    val id: String = "",
    val refId: String = "",
    val oilType: String = "",
    val location: String = "",
    val quantityMt: Double = 0.0,
    val availableQty: Double = 0.0,
    val periodEnd: String = "",
    val status: String = "pending",
    val rate: Double = 0.0,
    val counterparty: String = "",
)

@Serializable
data class ContractsListResponse(
    val data: List<ApiContractRow> = emptyList(),
    val days: Int = 7,
)

@Serializable
data class HealthResponse(
    val status: String = "",
    val database: String? = null,
)

fun ContractSummary.displayRef(): String = refId.ifBlank { id }

fun ContractSummary.daysUntilEnd(): Int? {
    if (periodEnd.isBlank()) return null
    return try {
        val end = java.time.LocalDate.parse(periodEnd)
        val today = java.time.LocalDate.now()
        java.time.temporal.ChronoUnit.DAYS.between(today, end).toInt()
    } catch (_: Exception) {
        null
    }
}
