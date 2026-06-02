package com.commspurx.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.badgeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "commspurx_badges",
)

/**
 * Persists which items the user has already seen so badge pills stay cleared
 * across navigation and app restarts until genuinely new items arrive.
 */
class BadgeStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.applicationContext.badgeDataStore

    fun hubDeliveryBadge(deliveryIds: Collection<String>): Int =
        deliveryIds.count { it !in readSet(KEY_HUB_DELIVERIES) }

    fun markHubDeliveriesSeen(deliveryIds: Collection<String>) {
        mergeInto(KEY_HUB_DELIVERIES, deliveryIds)
    }

    fun mainTabPurchaseBadge(contractIds: Collection<String>): Int =
        contractIds.count { it !in readSet(KEY_MAIN_TAB_PURCHASE) }

    fun markMainTabPurchaseSeen(contractIds: Collection<String>) {
        mergeInto(KEY_MAIN_TAB_PURCHASE, contractIds)
    }

    fun mainTabSalesBadge(contractIds: Collection<String>): Int =
        contractIds.count { it !in readSet(KEY_MAIN_TAB_SALES) }

    fun markMainTabSalesSeen(contractIds: Collection<String>) {
        mergeInto(KEY_MAIN_TAB_SALES, contractIds)
    }

    fun currentTabBadge(deliveryIds: Collection<String>): Int =
        deliveryIds.count { it !in readSet(KEY_CURRENT_TAB) }

    fun markCurrentTabSeen(deliveryIds: Collection<String>) {
        mergeInto(KEY_CURRENT_TAB, deliveryIds)
    }

    fun completedTabBadge(deliveryIds: Collection<String>): Int =
        deliveryIds.count { it !in readSet(KEY_COMPLETED_TAB) }

    fun markCompletedTabSeen(deliveryIds: Collection<String>) {
        mergeInto(KEY_COMPLETED_TAB, deliveryIds)
    }

    fun hubNotificationBadge(
        notificationIds: Collection<String>,
        approvalKeys: Collection<String>,
    ): Int =
        notificationIds.count { it !in readSet(KEY_NOTIFICATIONS) } +
            approvalKeys.count { it !in readSet(KEY_APPROVALS) }

    fun markHubNotificationsSeen(
        notificationIds: Collection<String>,
        approvalKeys: Collection<String>,
    ) {
        mergeInto(KEY_NOTIFICATIONS, notificationIds)
        mergeInto(KEY_APPROVALS, approvalKeys)
    }

    fun approvalsTabBadge(approvalKeys: Collection<String>): Int =
        approvalKeys.count { it !in readSet(KEY_NOTIFICATIONS_APPROVALS_TAB) }

    fun markApprovalsTabSeen(approvalKeys: Collection<String>) {
        mergeInto(KEY_NOTIFICATIONS_APPROVALS_TAB, approvalKeys)
    }

    fun activityTabBadge(notificationIds: Collection<String>): Int =
        notificationIds.count { it !in readSet(KEY_NOTIFICATIONS_ACTIVITY_TAB) }

    fun markActivityTabSeen(notificationIds: Collection<String>) {
        mergeInto(KEY_NOTIFICATIONS_ACTIVITY_TAB, notificationIds)
    }

    fun clear() = runBlocking {
        dataStore.edit { it.clear() }
    }

    private fun readSet(key: Preferences.Key<String>): Set<String> = runBlocking {
        val raw = dataStore.data.map { it[key] }.first() ?: return@runBlocking emptySet()
        runCatching { json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
    }

    private fun mergeInto(key: Preferences.Key<String>, ids: Collection<String>) {
        if (ids.isEmpty()) return
        runBlocking {
            val merged = readSet(key).toMutableSet().apply { addAll(ids) }
            dataStore.edit { prefs ->
                prefs[key] = json.encodeToString(merged)
            }
        }
    }

    companion object {
        private val KEY_MAIN_TAB_PURCHASE = stringPreferencesKey("main_tab_purchase_contract_ids")
        private val KEY_MAIN_TAB_SALES = stringPreferencesKey("main_tab_sales_contract_ids")
        private val KEY_HUB_DELIVERIES = stringPreferencesKey("hub_delivery_ids")
        private val KEY_CURRENT_TAB = stringPreferencesKey("current_tab_delivery_ids")
        private val KEY_COMPLETED_TAB = stringPreferencesKey("completed_tab_delivery_ids")
        private val KEY_NOTIFICATIONS = stringPreferencesKey("hub_notification_ids")
        private val KEY_APPROVALS = stringPreferencesKey("hub_approval_keys")
        private val KEY_NOTIFICATIONS_APPROVALS_TAB = stringPreferencesKey("approvals_tab_keys")
        private val KEY_NOTIFICATIONS_ACTIVITY_TAB = stringPreferencesKey("activity_tab_notification_ids")

        fun approvalKey(entityType: String, id: String): String = "$entityType:$id"
    }
}
