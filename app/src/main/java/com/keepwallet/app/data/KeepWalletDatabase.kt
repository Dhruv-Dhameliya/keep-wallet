package com.keepwallet.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    TransactionEntity::class,
    CategoryEntity::class,
    AccountEntity::class,
    DashboardWidgetEntity::class,
    DetectedEntryEntity::class,
    RecurringEntity::class,
    GoalEntity::class,
    PaymentRuleEntity::class
  ],
  version = 6,
  autoMigrations = [AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
  exportSchema = true
)
abstract class KeepWalletDatabase : RoomDatabase() {
  abstract fun transactionDao(): TransactionDao
  abstract fun categoryDao(): CategoryDao
  abstract fun accountDao(): AccountDao
  abstract fun widgetDao(): WidgetDao
  abstract fun detectedEntryDao(): DetectedEntryDao
  abstract fun recurringDao(): RecurringDao
  abstract fun goalDao(): GoalDao
  abstract fun paymentRuleDao(): PaymentRuleDao

  companion object {
    /** Accounts are now Bank, Wallet or Cash; the old "Card" kind becomes Bank. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE accounts SET kind = 'BANK' WHERE kind = 'CARD'")
      }
    }

    fun create(context: Context): KeepWalletDatabase =
      Room.databaseBuilder(context.applicationContext, KeepWalletDatabase::class.java, "keep_wallet.db")
        .addMigrations(MIGRATION_1_2)
        .build()

    fun inMemory(context: Context): KeepWalletDatabase =
      Room.inMemoryDatabaseBuilder(context.applicationContext, KeepWalletDatabase::class.java)
        .allowMainThreadQueries()
        .build()
  }
}
