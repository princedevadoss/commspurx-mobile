package com.commspurx.mobile.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.commspurx.mobile.data.local.entity.DeliveryEntity
import com.commspurx.mobile.data.local.entity.NotificationEntity
import com.commspurx.mobile.data.local.entity.PurchaseContractEntity
import com.commspurx.mobile.data.local.entity.SalesContractEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseContractDao {
    @Query("SELECT * FROM purchase_contracts WHERE accountId = :accountId ORDER BY periodEnd ASC")
    fun observeAll(accountId: String): Flow<List<PurchaseContractEntity>>

    @Query("SELECT * FROM purchase_contracts WHERE accountId = :accountId ORDER BY periodEnd ASC")
    suspend fun getAll(accountId: String): List<PurchaseContractEntity>

    @Transaction
    suspend fun replaceAll(accountId: String, rows: List<PurchaseContractEntity>) {
        deleteForAccount(accountId)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<PurchaseContractEntity>)

    @Query("DELETE FROM purchase_contracts WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

@Dao
interface SalesContractDao {
    @Query("SELECT * FROM sales_contracts WHERE accountId = :accountId ORDER BY periodEnd ASC")
    fun observeAll(accountId: String): Flow<List<SalesContractEntity>>

    @Query("SELECT * FROM sales_contracts WHERE accountId = :accountId ORDER BY periodEnd ASC")
    suspend fun getAll(accountId: String): List<SalesContractEntity>

    @Transaction
    suspend fun replaceAll(accountId: String, rows: List<SalesContractEntity>) {
        deleteForAccount(accountId)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<SalesContractEntity>)

    @Query("DELETE FROM sales_contracts WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

@Dao
interface DeliveryDao {
    @Query(
        "SELECT * FROM deliveries WHERE accountId = :accountId AND isCompleted = :completed ORDER BY scheduledDate DESC",
    )
    fun observeByCompleted(accountId: String, completed: Boolean): Flow<List<DeliveryEntity>>

    @Query(
        "SELECT * FROM deliveries WHERE accountId = :accountId AND isCompleted = :completed ORDER BY scheduledDate DESC",
    )
    suspend fun getByCompleted(accountId: String, completed: Boolean): List<DeliveryEntity>

    @Transaction
    suspend fun replaceAll(accountId: String, rows: List<DeliveryEntity>) {
        deleteForAccount(accountId)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<DeliveryEntity>)

    @Query("DELETE FROM deliveries WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE accountId = :accountId ORDER BY createdAt DESC")
    fun observeAll(accountId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE accountId = :accountId ORDER BY createdAt DESC")
    suspend fun getAll(accountId: String): List<NotificationEntity>

    @Transaction
    suspend fun replaceAll(accountId: String, rows: List<NotificationEntity>) {
        deleteForAccount(accountId)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<NotificationEntity>)

    @Query("DELETE FROM notifications WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: String)

    @Query(
        "UPDATE notifications SET readAt = :readAt WHERE accountId = :accountId AND id = :id",
    )
    suspend fun markReadLocal(accountId: String, id: String, readAt: String)

    @Query(
        "UPDATE notifications SET readAt = :readAt WHERE accountId = :accountId AND readAt IS NULL",
    )
    suspend fun markAllReadLocal(accountId: String, readAt: String)
}
