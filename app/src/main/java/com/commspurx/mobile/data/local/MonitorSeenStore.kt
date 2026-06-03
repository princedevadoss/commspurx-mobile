package com.commspurx.mobile.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.monitorSeenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "commspurx_monitor_seen",
)

@Serializable
data class MonitorSeenState(
    val baselineSet: Boolean = false,
    val approvalKeys: Set<String> = emptySet(),
    val notificationIds: Set<String> = emptySet(),
    val deliveryIds: Set<String> = emptySet(),
    val completedDeliveryIds: Set<String> = emptySet(),
    val purchaseContractIds: Set<String> = emptySet(),
    val salesContractIds: Set<String> = emptySet(),
    /** Count watermark when expiring lists are large (avoids storing hundreds of IDs). */
    val purchaseExpiringSeenCount: Int = 0,
    val salesExpiringSeenCount: Int = 0,
)

/**
 * Thread-safe store for items that already triggered an OS notification or were acknowledged in-app.
 */
class MonitorSeenStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = context.applicationContext.monitorSeenDataStore
    private val mutex = Mutex()

    suspend fun load(): MonitorSeenState = mutex.withLock {
        readState()
    }

    suspend fun save(state: MonitorSeenState) = mutex.withLock {
        writeState(state)
    }

    suspend fun clear() = mutex.withLock {
        dataStore.edit { it.clear() }
    }

    /** Atomic read-modify-write — prevents poll vs UI mark races. */
    suspend fun update(transform: (MonitorSeenState) -> MonitorSeenState) = mutex.withLock {
        writeState(transform(readState()))
    }

    suspend fun markNotificationsSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        update { it.copy(notificationIds = it.notificationIds + ids) }
    }

    suspend fun markApprovalsSeen(keys: Collection<String>) {
        if (keys.isEmpty()) return
        update { it.copy(approvalKeys = it.approvalKeys + keys) }
    }

    suspend fun markPendingDeliveriesSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        update { it.copy(deliveryIds = it.deliveryIds + ids) }
    }

    suspend fun markCompletedDeliveriesSeen(ids: Collection<String>) {
        if (ids.isEmpty()) return
        update { it.copy(completedDeliveryIds = it.completedDeliveryIds + ids) }
    }

    private suspend fun readState(): MonitorSeenState {
        val raw = dataStore.data.map { it[KEY_STATE] }.first() ?: return MonitorSeenState()
        return runCatching { json.decodeFromString<MonitorSeenState>(raw) }
            .getOrDefault(MonitorSeenState())
    }

    private suspend fun writeState(state: MonitorSeenState) {
        dataStore.edit { prefs ->
            prefs[KEY_STATE] = json.encodeToString(state)
        }
    }

    companion object {
        private val KEY_STATE = stringPreferencesKey("monitor_seen_state")

        fun approvalKey(entityType: String, id: String): String = "$entityType:$id"
    }
}
