package com.keepwallet.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Money is always stored as integer minor units (cents/paise) to avoid floating point drift. */
/** Transfers are stored as two linked legs (out of one account, into another) so balances stay right and income and expense totals never see them. */
enum class TxType { EXPENSE, INCOME, TRANSFER_OUT, TRANSFER_IN }

enum class CategoryGroup { ESSENTIAL, FLEXIBLE }

enum class AccountKind { BANK, WALLET, CASH }

enum class WidgetKind {
  BALANCE_CARD,
  MONTH_OVERVIEW,
  WEEK_OVERVIEW,
  QUICK_STATS,
  SPENDING_CHART,
  POCKETS_GRID,
  CATEGORY_BREAKDOWN,
  RECENT_ACTIVITY,
  UPCOMING_BILLS,
  SAVINGS_GOALS,
  HIGHLIGHTS
}

enum class Frequency { WEEKLY, MONTHLY, YEARLY }

enum class ChartType { BAR, LINE, DONUT }

enum class ChartRange { WEEK, MONTH, QUARTER, YEAR }

@Entity(tableName = "categories")
data class CategoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val iconKey: String,
  /** ARGB color of the category. */
  val color: Int,
  val type: TxType = TxType.EXPENSE,
  val group: CategoryGroup = CategoryGroup.FLEXIBLE,
  val monthlyLimitMinor: Long = 0,
  val weeklyLimitMinor: Long = 0,
  val sortOrder: Int = 0,
  /** When on, what is left of the monthly limit carries into the next month. */
  @ColumnInfo(defaultValue = "0") val rollover: Boolean = false,
  /** An emoji shown in place of the icon, when the user picked one. */
  val emoji: String? = null,
  /** A picture (file name in the private photo folder) shown in place of the icon, when the user chose one. */
  val photoFile: String? = null
)

@Entity(tableName = "accounts")
data class AccountEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val kind: AccountKind = AccountKind.BANK,
  val openingBalanceMinor: Long = 0,
  val sortOrder: Int = 0
)

@Entity(
  tableName = "transactions",
  foreignKeys = [
    ForeignKey(
      entity = CategoryEntity::class,
      parentColumns = ["id"],
      childColumns = ["categoryId"],
      onDelete = ForeignKey.RESTRICT
    ),
    ForeignKey(
      entity = AccountEntity::class,
      parentColumns = ["id"],
      childColumns = ["accountId"],
      onDelete = ForeignKey.RESTRICT
    )
  ],
  indices = [Index("categoryId"), Index("accountId"), Index("timestamp")]
)
data class TransactionEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val type: TxType,
  val amountMinor: Long,
  val merchant: String,
  val categoryId: Long,
  val accountId: Long,
  val timestamp: Long,
  val note: String? = null,
  /** File name of a receipt image stored in the app's private receipts folder. */
  val receiptFile: String? = null,
  /** Shared by the two legs of a transfer between accounts. */
  val transferId: Long? = null,
  /** Shared by the parts of one bill that was split across categories. */
  val splitId: Long? = null,
  /** When the entry was paid in another currency: its code (such as "EUR") and the amount in that currency. */
  val foreignCode: String? = null,
  val foreignMinor: Long? = null
)

@Entity(tableName = "dashboard_widgets")
data class DashboardWidgetEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val kind: WidgetKind,
  val enabled: Boolean = true,
  val position: Int = 0,
  val chartType: ChartType = ChartType.BAR,
  val range: ChartRange = ChartRange.WEEK
)

/**
 * A transaction spotted in a notification that the user has not confirmed yet.
 * Only the parsed fields are kept – never the original notification text.
 */
@Entity(tableName = "detected_entries")
data class DetectedEntryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sourcePackage: String,
  val sourceApp: String,
  val amountMinor: Long,
  val type: TxType,
  val merchant: String,
  val detectedAt: Long
)

/**
 * A bill or income that repeats (rent, a subscription, a salary). [nextDueDay] is the epoch day of the next
 * occurrence; [anchorDay] is the day of the month it belongs to, so a bill on the 31st doesn't drift to the 28th
 * after a short month. With [autoPost] on, due occurrences are added as entries when the app opens; otherwise
 * they wait on Home and in Plan for a tap.
 */
@Entity(
  tableName = "recurring",
  foreignKeys = [
    ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)
  ],
  indices = [Index("categoryId"), Index("accountId")]
)
data class RecurringEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val type: TxType = TxType.EXPENSE,
  val amountMinor: Long,
  val categoryId: Long,
  val accountId: Long,
  val frequency: Frequency = Frequency.MONTHLY,
  val nextDueDay: Long,
  val anchorDay: Int,
  val autoPost: Boolean = false,
  val active: Boolean = true
)

/** Money set aside for something. Kept apart from the accounts: it is a target and a running total, not a balance. */
@Entity(tableName = "goals")
data class GoalEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val targetMinor: Long,
  val savedMinor: Long = 0,
  val iconKey: String = "savings",
  val color: Int,
  val sortOrder: Int = 0
)

/**
 * "Anything from Metro is Transport": when a detected payment's name contains [match], the entry form opens with
 * this category (and account, and a tidier name) already chosen.
 */
@Entity(
  tableName = "payment_rules",
  foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)],
  indices = [Index("categoryId")]
)
data class PaymentRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val match: String,
  val categoryId: Long,
  val accountId: Long? = null,
  /** A cleaner name to use instead of the raw text from the notification. */
  val displayName: String? = null
)
