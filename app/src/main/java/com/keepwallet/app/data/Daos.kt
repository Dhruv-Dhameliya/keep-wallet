package com.keepwallet.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
  @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
  fun observeAll(): Flow<List<TransactionEntity>>

  @Query("SELECT * FROM transactions WHERE id = :id")
  suspend fun get(id: Long): TransactionEntity?

  @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
  suspend fun countForCategory(categoryId: Long): Int

  @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
  suspend fun countForAccount(accountId: Long): Int

  @Query("SELECT receiptFile FROM transactions WHERE receiptFile IS NOT NULL")
  suspend fun allReceiptFiles(): List<String>

  /** Income minus expenses booked to one account. */
  @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('INCOME', 'TRANSFER_IN') THEN amountMinor ELSE -amountMinor END), 0) FROM transactions WHERE accountId = :accountId")
  suspend fun netForAccount(accountId: Long): Long

  @Query("SELECT * FROM transactions ORDER BY id ASC")
  suspend fun getAll(): List<TransactionEntity>

  @Insert
  suspend fun insert(transaction: TransactionEntity): Long

  @Insert
  suspend fun insertAll(transactions: List<TransactionEntity>)

  @Update
  suspend fun update(transaction: TransactionEntity)

  @Delete
  suspend fun delete(transaction: TransactionEntity)

  @Query("UPDATE transactions SET categoryId = :toId WHERE categoryId = :fromId")
  suspend fun moveCategory(fromId: Long, toId: Long)

  @Query("SELECT * FROM transactions WHERE transferId = :transferId ORDER BY id ASC")
  suspend fun legsOfTransfer(transferId: Long): List<TransactionEntity>

  @Query("DELETE FROM transactions WHERE transferId = :transferId")
  suspend fun deleteTransfer(transferId: Long)

  @Query("DELETE FROM transactions")
  suspend fun deleteAll()
}

@Dao
interface CategoryDao {
  @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
  fun observeAll(): Flow<List<CategoryEntity>>

  @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
  suspend fun getAll(): List<CategoryEntity>

  @Query("SELECT COUNT(*) FROM categories")
  suspend fun count(): Int

  @Query("SELECT * FROM categories WHERE id = :id")
  suspend fun get(id: Long): CategoryEntity?

  @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM categories")
  suspend fun nextSortOrder(): Int

  @Insert
  suspend fun insert(category: CategoryEntity): Long

  @Insert
  suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

  @Update
  suspend fun update(category: CategoryEntity)

  @Update
  suspend fun updateAll(categories: List<CategoryEntity>)

  @Delete
  suspend fun delete(category: CategoryEntity)

  @Query("DELETE FROM categories")
  suspend fun deleteAll()
}

@Dao
interface AccountDao {
  @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
  fun observeAll(): Flow<List<AccountEntity>>

  @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
  suspend fun getAll(): List<AccountEntity>

  @Query("SELECT COUNT(*) FROM accounts")
  suspend fun count(): Int

  @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM accounts")
  suspend fun nextSortOrder(): Int

  @Insert
  suspend fun insert(account: AccountEntity): Long

  @Insert
  suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

  @Update
  suspend fun update(account: AccountEntity)

  @Delete
  suspend fun delete(account: AccountEntity)

  @Query("DELETE FROM accounts")
  suspend fun deleteAll()
}

@Dao
interface WidgetDao {
  @Query("SELECT * FROM dashboard_widgets ORDER BY position ASC, id ASC")
  fun observeAll(): Flow<List<DashboardWidgetEntity>>

  @Query("SELECT * FROM dashboard_widgets ORDER BY position ASC, id ASC")
  suspend fun getAll(): List<DashboardWidgetEntity>

  @Query("SELECT * FROM dashboard_widgets WHERE id = :id")
  suspend fun get(id: Long): DashboardWidgetEntity?

  @Query("SELECT COUNT(*) FROM dashboard_widgets")
  suspend fun count(): Int

  @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM dashboard_widgets")
  suspend fun nextPosition(): Int

  @Insert
  suspend fun insert(widget: DashboardWidgetEntity): Long

  @Insert
  suspend fun insertAll(widgets: List<DashboardWidgetEntity>)

  @Update
  suspend fun update(widget: DashboardWidgetEntity)

  @Update
  suspend fun updateAll(widgets: List<DashboardWidgetEntity>)

  @Query("DELETE FROM dashboard_widgets WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM dashboard_widgets")
  suspend fun deleteAll()
}

@Dao
interface DetectedEntryDao {
  @Query("SELECT * FROM detected_entries ORDER BY detectedAt DESC, id DESC")
  fun observeAll(): Flow<List<DetectedEntryEntity>>

  @Query("SELECT * FROM detected_entries WHERE id = :id")
  suspend fun get(id: Long): DetectedEntryEntity?

  /** Used to ignore the same notification being re-posted by an app within a short window. */
  @Query(
    "SELECT COUNT(*) FROM detected_entries WHERE sourcePackage = :pkg AND amountMinor = :amountMinor " +
      "AND type = :type AND detectedAt >= :since"
  )
  suspend fun countRecentDuplicates(pkg: String, amountMinor: Long, type: TxType, since: Long): Int

  @Insert
  suspend fun insert(entry: DetectedEntryEntity): Long

  @Query("DELETE FROM detected_entries WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM detected_entries WHERE detectedAt < :before")
  suspend fun deleteOlderThan(before: Long)

  @Query("DELETE FROM detected_entries")
  suspend fun deleteAll()
}

@Dao
interface RecurringDao {
  @Query("SELECT * FROM recurring ORDER BY nextDueDay ASC, id ASC")
  fun observeAll(): Flow<List<RecurringEntity>>

  @Query("SELECT * FROM recurring ORDER BY nextDueDay ASC, id ASC")
  suspend fun getAll(): List<RecurringEntity>

  @Query("SELECT * FROM recurring WHERE id = :id")
  suspend fun get(id: Long): RecurringEntity?

  @Insert
  suspend fun insert(item: RecurringEntity): Long

  @Insert
  suspend fun insertAll(items: List<RecurringEntity>)

  @Update
  suspend fun update(item: RecurringEntity)

  @Query("DELETE FROM recurring WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("UPDATE recurring SET categoryId = :toId WHERE categoryId = :fromId")
  suspend fun moveCategory(fromId: Long, toId: Long)

  @Query("DELETE FROM recurring")
  suspend fun deleteAll()
}

@Dao
interface GoalDao {
  @Query("SELECT * FROM goals ORDER BY sortOrder ASC, id ASC")
  fun observeAll(): Flow<List<GoalEntity>>

  @Query("SELECT * FROM goals ORDER BY sortOrder ASC, id ASC")
  suspend fun getAll(): List<GoalEntity>

  @Query("SELECT * FROM goals WHERE id = :id")
  suspend fun get(id: Long): GoalEntity?

  @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM goals")
  suspend fun nextSortOrder(): Int

  @Insert
  suspend fun insert(goal: GoalEntity): Long

  @Insert
  suspend fun insertAll(goals: List<GoalEntity>)

  @Update
  suspend fun update(goal: GoalEntity)

  @Query("DELETE FROM goals WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM goals")
  suspend fun deleteAll()
}

@Dao
interface PaymentRuleDao {
  @Query("SELECT * FROM payment_rules ORDER BY id ASC")
  fun observeAll(): Flow<List<PaymentRuleEntity>>

  @Query("SELECT * FROM payment_rules ORDER BY id ASC")
  suspend fun getAll(): List<PaymentRuleEntity>

  @Insert
  suspend fun insert(rule: PaymentRuleEntity): Long

  @Insert
  suspend fun insertAll(rules: List<PaymentRuleEntity>)

  @Update
  suspend fun update(rule: PaymentRuleEntity)

  @Query("DELETE FROM payment_rules WHERE id = :id")
  suspend fun delete(id: Long)

  @Query("DELETE FROM payment_rules")
  suspend fun deleteAll()
}
