package com.commspurx.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.badgeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "commspurx_badges",
)

@Serializable
private data class IdSetSnapshot(val ids: Set<String> = emptySet())

/**
 * Persists seen-state for hub/tab badges. Uses count watermarks for large contract lists
 * (avoids storing hundreds of UUIDs). Never blocks the main thread.
 */
class BadgeStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.applicationContext.badgeDataStore
    private val mutex = Mutex()

    private var purchaseSeenCount = 0
    private var salesSeenCount = 0
    private var hubDeliveries = emptySet<String>()
    private var currentTabDeliveries = emptySet<String>()
    private var completedTabDeliveries = emptySet<String>()
    private var hubNotifications = emptySet<String>()
    private var hubApprovals = emptySet<String>()
    private var approvalsTab = emptySet<String>()
    private var activityTab = emptySet<String>()
    private var loaded = false

    suspend fun hubDeliveryBadge(deliveryIds: Collection<String>): Int = mutex.withLock {
        ensureLoaded()
        countUnseen(deliveryIds, hubDeliveries)
    }

    suspend fun markHubDeliveriesSeen(deliveryIds: Collection<String>) = mutex.withLock {
        ensureLoaded()
        hubDeliveries = hubDeliveries + deliveryIds
        persist()
    }

    suspend fun mainTabPurchaseUnseen(currentCount: Int): Int = mutex.withLock {
        ensureLoaded()
        BadgeCounts.unseen(currentCount, purchaseSeenCount)
    }

    suspend fun mainTabPurchaseBadge(currentCount: Int): Int = mutex.withLock {
        ensureLoaded()
        BadgeCounts.badgeValue(BadgeCounts.unseen(currentCount, purchaseSeenCount))
    }

    suspend fun markMainTabPurchaseSeen(currentCount: Int) = mutex.withLock {
        ensureLoaded()
        purchaseSeenCount = currentCount
        persist()
    }

    suspend fun mainTabSalesUnseen(currentCount: Int): Int = mutex.withLock {
        ensureLoaded()
        BadgeCounts.unseen(currentCount, salesSeenCount)
    }

    suspend fun mainTabSalesBadge(currentCount: Int): Int = mutex.withLock {
        ensureLoaded()
        BadgeCounts.badgeValue(BadgeCounts.unseen(currentCount, salesSeenCount))
    }

    suspend fun markMainTabSalesSeen(currentCount: Int) = mutex.withLock {
        ensureLoaded()
        salesSeenCount = currentCount
        persist()
    }

    suspend fun currentTabBadge(deliveryIds: Collection<String>): Int = mutex.withLock {
        ensureLoaded()
        countUnseen(deliveryIds, currentTabDeliveries)
    }

    suspend fun markCurrentTabSeen(deliveryIds: Collection<String>) = mutex.withLock {
        ensureLoaded()
        currentTabDeliveries = currentTabDeliveries + deliveryIds
        persist()
    }

    suspend fun completedTabBadge(deliveryIds: Collection<String>): Int = mutex.withLock {
        ensureLoaded()
        countUnseen(deliveryIds, completedTabDeliveries)
    }

    suspend fun markCompletedTabSeen(deliveryIds: Collection<String>) = mutex.withLock {
        ensureLoaded()
        completedTabDeliveries = completedTabDeliveries + deliveryIds
        persist()
    }

    suspend fun hubNotificationUnseen(
        notificationIds: Collection<String>,
        approvalKeys: Collection<String>,
    ): Int = mutex.withLock {
        ensureLoaded()
        countUnseen(notificationIds, hubNotifications) +
            countUnseen(approvalKeys, hubApprovals)
    }

    suspend fun hubNotificationBadge(
        notificationIds: Collection<String>,
        approvalKeys: Collection<String>,
    ): Int = mutex.withLock {
        ensureLoaded()
        BadgeCounts.badgeValue(
            countUnseen(notificationIds, hubNotifications) +
                countUnseen(approvalKeys, hubApprovals),
        )
    }

    suspend fun markHubNotificationsSeen(
        notificationIds: Collection<String>,
        approvalKeys: Collection<String>,
    ) = mutex.withLock {
        ensureLoaded()
        hubNotifications = hubNotifications + notificationIds
        hubApprovals = hubApprovals + approvalKeys
        persist()
    }

    suspend fun approvalsTabBadge(approvalKeys: Collection<String>): Int = mutex.withLock {
        ensureLoaded()
        BadgeCounts.badgeValue(countUnseen(approvalKeys, approvalsTab))
    }

    suspend fun markApprovalsTabSeen(approvalKeys: Collection<String>) = mutex.withLock {
        ensureLoaded()
        approvalsTab = approvalsTab + approvalKeys
        persist()
    }

    suspend fun activityTabBadge(notificationIds: Collection<String>): Int = mutex.withLock {
        ensureLoaded()
        BadgeCounts.badgeValue(countUnseen(notificationIds, activityTab))
    }

    suspend fun markActivityTabSeen(notificationIds: Collection<String>) = mutex.withLock {
        ensureLoaded()
        activityTab = activityTab + notificationIds
        persist()
    }

    suspend fun clear() = mutex.withLock {
        dataStore.edit { it.clear() }
        purchaseSeenCount = 0
        salesSeenCount = 0
        hubDeliveries = emptySet()
        currentTabDeliveries = emptySet()
        completedTabDeliveries = emptySet()
        hubNotifications = emptySet()
        hubApprovals = emptySet()
        approvalsTab = emptySet()
        activityTab = emptySet()
        loaded = true
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val prefs = dataStore.data.first()
        purchaseSeenCount = prefs[KEY_PURCHASE_SEEN_COUNT] ?: 0
        salesSeenCount = prefs[KEY_SALES_SEEN_COUNT] ?: 0
        hubDeliveries = decodeSet(prefs[KEY_HUB_DELIVERIES])
        currentTabDeliveries = decodeSet(prefs[KEY_CURRENT_TAB])
        completedTabDeliveries = decodeSet(prefs[KEY_COMPLETED_TAB])
        hubNotifications = decodeSet(prefs[KEY_NOTIFICATIONS])
        hubApprovals = decodeSet(prefs[KEY_APPROVALS])
        approvalsTab = decodeSet(prefs[KEY_APPROVALS_TAB])
        activityTab = decodeSet(prefs[KEY_ACTIVITY_TAB])
        loaded = true
    }

    private fun decodeSet(raw: String?): Set<String> =
        raw?.let { runCatching { json.decodeFromString<IdSetSnapshot>(it).ids }.getOrNull() }
            ?: emptySet()

    private suspend fun persist() {
        dataStore.edit { prefs ->
            prefs[KEY_PURCHASE_SEEN_COUNT] = purchaseSeenCount
            prefs[KEY_SALES_SEEN_COUNT] = salesSeenCount
            prefs[KEY_HUB_DELIVERIES] = json.encodeToString(IdSetSnapshot(hubDeliveries))
            prefs[KEY_CURRENT_TAB] = json.encodeToString(IdSetSnapshot(currentTabDeliveries))
            prefs[KEY_COMPLETED_TAB] = json.encodeToString(IdSetSnapshot(completedTabDeliveries))
            prefs[KEY_NOTIFICATIONS] = json.encodeToString(IdSetSnapshot(hubNotifications))
            prefs[KEY_APPROVALS] = json.encodeToString(IdSetSnapshot(hubApprovals))
            prefs[KEY_APPROVALS_TAB] = json.encodeToString(IdSetSnapshot(approvalsTab))
            prefs[KEY_ACTIVITY_TAB] = json.encodeToString(IdSetSnapshot(activityTab))
        }
    }

    private fun countUnseen(ids: Collection<String>, seen: Set<String>): Int =
        ids.count { it !in seen }

    companion object {
        private val KEY_PURCHASE_SEEN_COUNT = intPreferencesKey("purchase_seen_count")
        private val KEY_SALES_SEEN_COUNT = intPreferencesKey("sales_seen_count")
        private val KEY_HUB_DELIVERIES = stringPreferencesKey("hub_delivery_ids")
        private val KEY_CURRENT_TAB = stringPreferencesKey("current_tab_delivery_ids")
        private val KEY_COMPLETED_TAB = stringPreferencesKey("completed_tab_delivery_ids")
        private val KEY_NOTIFICATIONS = stringPreferencesKey("hub_notification_ids")
        private val KEY_APPROVALS = stringPreferencesKey("hub_approval_keys")
        private val KEY_APPROVALS_TAB = stringPreferencesKey("approvals_tab_keys")
        private val KEY_ACTIVITY_TAB = stringPreferencesKey("activity_tab_notification_ids")

        fun approvalKey(entityType: String, id: String): String = "$entityType:$id"
    }
}
