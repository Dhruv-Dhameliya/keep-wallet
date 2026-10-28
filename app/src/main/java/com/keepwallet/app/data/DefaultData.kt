package com.keepwallet.app.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Random

/** First-run defaults plus the optional demo data set. */
object DefaultData {
  /** Bumped whenever the built-in categories, accounts or home layout change. */
  const val VERSION = 4

  const val OTHERS = "Others"
  const val PERSON = "Person"
  const val MONEY_TRANSFER = "Money transfer"
  const val CASHBACKS = "Cashbacks"

  const val HEALTH = "Health"

  fun categories(): List<CategoryEntity> = listOf(
    expense("Housing", "housing", 0xFFD9532F, CategoryGroup.ESSENTIAL, 0),
    expense("Groceries", "groceries", 0xFF2E9E5B, CategoryGroup.ESSENTIAL, 1),
    expense("Transport", "transport", 0xFF3B82F6, CategoryGroup.ESSENTIAL, 2),
    expense(HEALTH, "health", 0xFFEF4444, CategoryGroup.ESSENTIAL, 3),
    expense("Dining", "dining", 0xFFF59E0B, CategoryGroup.FLEXIBLE, 4),
    expense("Subscriptions", "subscriptions", 0xFF8B5CF6, CategoryGroup.FLEXIBLE, 5),
    expense("Shopping", "shopping", 0xFFEC4899, CategoryGroup.FLEXIBLE, 6),
    expense(PERSON, "person", 0xFF0EA5E9, CategoryGroup.FLEXIBLE, 7),
    expense(MONEY_TRANSFER, "transfer", 0xFF6366F1, CategoryGroup.FLEXIBLE, 8),
    expense(OTHERS, "other", 0xFF64748B, CategoryGroup.FLEXIBLE, 9),
    income("Salary", "salary", 0xFF16A34A, 10),
    income(CASHBACKS, "cashback", 0xFF0D9488, 11),
    income("Other income", "gift", 0xFF84CC16, 12)
  )

  fun accounts(): List<AccountEntity> = listOf(
    AccountEntity(name = "Bank account", kind = AccountKind.BANK, sortOrder = 0),
    AccountEntity(name = "Wallet", kind = AccountKind.WALLET, sortOrder = 1),
    AccountEntity(name = "Cash", kind = AccountKind.CASH, sortOrder = 2)
  )

  /**
   * Home as it starts out: balance card, monthly plan, spending graph, recent activity, pockets. Everything else
   * is available but off, and can be turned on or reordered in "Customize home".
   */
  fun widgets(): List<DashboardWidgetEntity> = listOf(
    DashboardWidgetEntity(kind = WidgetKind.BALANCE_CARD, position = 0),
    DashboardWidgetEntity(kind = WidgetKind.MONTH_OVERVIEW, position = 1),
    DashboardWidgetEntity(kind = WidgetKind.SPENDING_CHART, position = 2, range = ChartRange.WEEK),
    DashboardWidgetEntity(kind = WidgetKind.RECENT_ACTIVITY, position = 3),
    DashboardWidgetEntity(kind = WidgetKind.POCKETS_GRID, position = 4),
    DashboardWidgetEntity(kind = WidgetKind.QUICK_STATS, position = 5, enabled = false),
    DashboardWidgetEntity(kind = WidgetKind.WEEK_OVERVIEW, position = 6, enabled = false),
    DashboardWidgetEntity(kind = WidgetKind.CATEGORY_BREAKDOWN, position = 7, enabled = false, range = ChartRange.WEEK),
    DashboardWidgetEntity(kind = WidgetKind.HIGHLIGHTS, position = 8, enabled = false),
    DashboardWidgetEntity(kind = WidgetKind.UPCOMING_BILLS, position = 9, enabled = false),
    DashboardWidgetEntity(kind = WidgetKind.SAVINGS_GOALS, position = 10, enabled = false)
  )

  fun sampleMonthlyLimit(name: String): Long = when (name) {
    "Housing" -> 120_000
    "Groceries" -> 60_000
    "Transport" -> 30_000
    HEALTH -> 15_000
    "Dining" -> 24_000
    "Subscriptions" -> 10_000
    "Shopping" -> 40_000
    PERSON -> 20_000
    OTHERS -> 36_000
    else -> 0
  }

  fun sampleWeeklyLimit(name: String): Long = when (name) {
    "Housing" -> 30_000
    "Groceries" -> 20_000
    "Transport" -> 10_000
    HEALTH -> 4_000
    "Dining" -> 8_000
    "Subscriptions" -> 2_500
    "Shopping" -> 12_000
    PERSON -> 5_000
    OTHERS -> 9_000
    else -> 0
  }

  /** A few bills that repeat, due on the next occurrence after [today], so the demo has something to show. */
  fun sampleRecurring(today: LocalDate, categoryIds: Map<String, Long>, bankId: Long, walletId: Long): List<RecurringEntity> {
    fun due(day: Int): LocalDate {
      val thisMonth = YearMonth.from(today)
      val candidate = thisMonth.atDay(day.coerceAtMost(thisMonth.lengthOfMonth()))
      return if (candidate.isAfter(today)) candidate else YearMonth.from(today).plusMonths(1).let { it.atDay(day.coerceAtMost(it.lengthOfMonth())) }
    }
    fun item(name: String, category: String, amount: Long, day: Int, account: Long, type: TxType = TxType.EXPENSE, auto: Boolean = false): RecurringEntity? {
      val categoryId = categoryIds[category] ?: return null
      return RecurringEntity(
        name = name, type = type, amountMinor = amount, categoryId = categoryId, accountId = account,
        frequency = Frequency.MONTHLY, nextDueDay = due(day).toEpochDay(), anchorDay = day, autoPost = auto
      )
    }
    return listOfNotNull(
      item("Rent", "Housing", 120_000, 1, bankId),
      item("Streamly", "Subscriptions", 999, 11, bankId),
      item("Cloud storage", "Subscriptions", 499, 14, walletId),
      item("Monthly salary", "Salary", 480_000, 1, bankId, TxType.INCOME)
    )
  }

  fun sampleGoals(): List<GoalEntity> = listOf(
    GoalEntity(name = "Emergency fund", targetMinor = 500_000, savedMinor = 185_000, iconKey = "savings", color = 0xFF0D9488.toInt(), sortOrder = 0),
    GoalEntity(name = "Summer trip", targetMinor = 200_000, savedMinor = 64_000, iconKey = "travel", color = 0xFF3B82F6.toInt(), sortOrder = 1),
    GoalEntity(name = "New laptop", targetMinor = 150_000, savedMinor = 150_000, iconKey = "work", color = 0xFF8B5CF6.toInt(), sortOrder = 2)
  )

  /** Three full months before this one, plus this month up to [today]. Nothing is dated in the future. */
  fun sampleTransactions(
    today: LocalDate,
    zone: ZoneId,
    categoryIds: Map<String, Long>,
    bankId: Long,
    walletId: Long,
    cashId: Long
  ): List<TransactionEntity> {
    val result = mutableListOf<TransactionEntity>()
    val currentMonth = YearMonth.from(today)

    fun add(
      date: LocalDate, time: LocalTime, category: String, name: String, amountMinor: Long,
      type: TxType = TxType.EXPENSE, accountId: Long = bankId, note: String? = null
    ) {
      if (date.isAfter(today)) return
      val categoryId = categoryIds[category] ?: return
      val millis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
      result += TransactionEntity(
        type = type, amountMinor = amountMinor, merchant = name, categoryId = categoryId,
        accountId = accountId, timestamp = millis, note = note
      )
    }

    for (offset in 3 downTo 0) {
      val month = currentMonth.minusMonths(offset.toLong())
      val random = Random(1000L + month.year * 12L + month.monthValue)
      fun day(d: Int): LocalDate = month.atDay(d.coerceAtMost(month.lengthOfMonth()))
      fun cents(low: Int, high: Int): Long = (low + random.nextInt(high - low + 1)) * 100L + random.nextInt(100)
      fun time(): LocalTime = LocalTime.of(8 + random.nextInt(12), random.nextInt(60))

      add(day(1), LocalTime.of(8, 0), "Salary", "Monthly salary", 480_000, TxType.INCOME, note = "Direct deposit")
      add(day(16), LocalTime.of(11, 20), CASHBACKS, "Card cashback", cents(5, 20), TxType.INCOME, walletId)
      add(day(1), LocalTime.of(9, 0), "Housing", "Rent", 120_000, note = "Apartment lease")
      listOf(3, 9, 15, 21, 27).forEach { d ->
        add(day(d), time(), "Groceries", listOf("Green Market", "Fresh Foods", "Corner Grocer").random(random), cents(25, 95))
      }
      listOf(2, 6, 10, 16, 22, 26).forEach { d ->
        val pass = d % 3 == 0
        add(day(d), time(), "Transport", if (pass) "Metro pass" else "Metro", if (pass) 2_500L else 320L, accountId = walletId)
      }
      listOf(4, 12, 19, 24).forEach { d ->
        add(day(d), LocalTime.of(12 + random.nextInt(8), random.nextInt(60)), "Dining",
          listOf("Corner Café", "Trattoria", "Noodle Bar").random(random), cents(9, 45), accountId = walletId)
      }
      add(day(8), time(), "Shopping", "Bookshop", cents(15, 40), note = "Books")
      add(day(18), time(), "Shopping", "Department store", cents(40, 120))
      add(day(11), LocalTime.of(9, 12), "Subscriptions", "Streamly", 999)
      add(day(14), LocalTime.of(10, 0), "Subscriptions", "Cloud storage", 499)
      add(day(13), time(), PERSON, "Dinner with Asha", cents(12, 40), accountId = walletId)
      add(day(23), time(), MONEY_TRANSFER, "Sent to Ravi", cents(50, 150))
      add(day(20), time(), OTHERS, "Pharmacy", cents(8, 30), accountId = cashId, note = "Health")
    }

    // A few recent entries so "today", "yesterday" and the current week always have something to show.
    if (result.none { it.timestamp >= today.atStartOfDay(zone).toInstant().toEpochMilli() }) {
      add(today, LocalTime.of(9, 5), "Dining", "Corner Café", 1_250, accountId = walletId, note = "Morning coffee")
      add(today, LocalTime.of(10, 36), "Groceries", "Green Market", 5_480, note = "Fresh vegetables")
    }
    add(today.minusDays(1), LocalTime.of(18, 45), "Transport", "Metro", 320, accountId = walletId)
    add(today.minusDays(2), LocalTime.of(16, 20), "Shopping", "Bookshop", 2_800, note = "Paperback novel")
    return result
  }

  private fun expense(name: String, icon: String, color: Long, group: CategoryGroup, order: Int) =
    CategoryEntity(
      name = name, iconKey = icon, color = color.toInt(), type = TxType.EXPENSE, group = group, sortOrder = order
    )

  private fun income(name: String, icon: String, color: Long, order: Int) =
    CategoryEntity(name = name, iconKey = icon, color = color.toInt(), type = TxType.INCOME, sortOrder = order)

  private fun <T> List<T>.random(random: Random): T = this[random.nextInt(size)]
}
