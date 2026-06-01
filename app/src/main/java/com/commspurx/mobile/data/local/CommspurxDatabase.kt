package com.commspurx.mobile.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.commspurx.mobile.data.local.dao.DeliveryDao
import com.commspurx.mobile.data.local.dao.NotificationDao
import com.commspurx.mobile.data.local.dao.PurchaseContractDao
import com.commspurx.mobile.data.local.dao.SalesContractDao
import com.commspurx.mobile.data.local.entity.DeliveryEntity
import com.commspurx.mobile.data.local.entity.NotificationEntity
import com.commspurx.mobile.data.local.entity.PurchaseContractEntity
import com.commspurx.mobile.data.local.entity.SalesContractEntity

@Database(
    entities = [
        PurchaseContractEntity::class,
        SalesContractEntity::class,
        DeliveryEntity::class,
        NotificationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class CommspurxDatabase : RoomDatabase() {
    abstract fun purchaseContractDao(): PurchaseContractDao
    abstract fun salesContractDao(): SalesContractDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var instance: CommspurxDatabase? = null

        fun get(context: Context): CommspurxDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CommspurxDatabase::class.java,
                    "commspurx_cache.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notifications ADD COLUMN priority TEXT NOT NULL DEFAULT 'normal'",
                )
            }
        }
    }
}
