package com.commspurx.mobile.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthUser(
    val id: String = "",
    val email: String,
    val name: String = "",
    val role: String,
    val accountId: String = "",
    val preferredLanguage: String = "en",
    val theme: String = "light",
)

@Serializable
data class AuthAccount(
    val id: String,
    val code: String,
    val name: String,
    val hasLogo: Boolean = false,
)

@Serializable
data class LoginRequest(
    val accountCode: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String? = null,
    val user: AuthUser,
    val account: AuthAccount? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUser,
    val account: AuthAccount? = null,
)

@Serializable
data class LogoutRequest(
    val refreshToken: String,
)

@Serializable
data class FcmTokenRequest(
    val token: String,
    val deviceLabel: String? = null,
)

@Serializable
data class ApiErrorResponse(
    val error: String? = null,
    val code: String? = null,
)

@Serializable
data class NotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val entityType: String,
    val entityId: String,
    val priority: String = "normal",
    val readAt: String? = null,
    val createdAt: String,
) {
    val isHighPriority: Boolean get() = priority == "high"
}

@Serializable
data class NotificationsResponse(
    val data: List<NotificationItem> = emptyList(),
    val meta: com.commspurx.mobile.data.model.PaginationMeta? = null,
    val unreadCount: Int = 0,
)

@Serializable
data class ApprovalItem(
    val id: String,
    val entityType: String,
    val label: String,
    val subtitle: String,
    val createdAt: String,
    val createdByUserId: String? = null,
    val createdByName: String? = null,
)

@Serializable
data class ApprovalsResponse(
    val data: List<ApprovalItem> = emptyList(),
    val pendingCount: Int = 0,
)

@Serializable
data class DecideApprovalRequest(
    val action: String,
)

@Serializable
data class RejectedRow(
    val rowNumber: Int,
    val reason: String,
)

@Serializable
data class BulkImportJobResult(
    val id: String,
    val entityType: String,
    val status: String,
    val fileName: String,
    val totalRows: Int,
    val successCount: Int,
    val rejectedCount: Int,
    val rejectedRows: List<RejectedRow> = emptyList(),
    val errorMessage: String? = null,
    val createdAt: String,
    val completedAt: String? = null,
)

@Serializable
data class SessionSnapshot(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUser,
    val account: AuthAccount?,
)

enum class UserRole(val value: String) {
    Admin("admin"),
    Manager("manager"),
    Trader("trader");

    companion object {
        fun from(value: String?): UserRole? =
            entries.find { it.value == value?.lowercase() }
    }
}

fun UserRole.canAccessApprovals(): Boolean =
    this == UserRole.Admin || this == UserRole.Manager

fun UserRole.canReceiveBulkNotifications(): Boolean =
    this == UserRole.Admin || this == UserRole.Manager

val APPROVAL_ENTITY_LABELS = mapOf(
    "location" to "Location",
    "oil_product" to "Oil product",
    "broker" to "Broker",
    "owner" to "Owner",
)
