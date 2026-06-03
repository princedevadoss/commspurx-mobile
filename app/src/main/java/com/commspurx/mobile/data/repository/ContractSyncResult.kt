package com.commspurx.mobile.data.repository

data class ContractSyncResult(
    val success: Boolean,
    val truncated: Boolean = false,
    val totalItems: Int = 0,
)
