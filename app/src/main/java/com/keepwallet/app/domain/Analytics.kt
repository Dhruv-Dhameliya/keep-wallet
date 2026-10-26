package com.keepwallet.app.domain

import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.ChartRange
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

data class CategoryTotal(
  val category: CategoryEntity,
  val amountMinor: Long,
  /** Share of the grand total, 0..1. */
  val share: Float,
  /** Whole-number share; across a list these add up to exactly 100. */
  val percent: Int = Math.round(share * 100)
) {
  /** "42%", or "<1%" for a real amount that rounds to nothing. */
  val percentLabel: String get() = if (percent == 0 && amountMinor > 0) "<1%" else "$percent%"
}

data class Bucket(
  val label: String,
  val valueMinor: Long,
  val isCurrent: Boolean = false
)

data class BudgetImpact(
  val limitMinor: Long,
  val spentMinor: Long,
  /** Limit minus everything already spent in the month and the amount being entered. Negative means over budget. */
  val leftAfterMinor: Long
)

/** Pure calculations over the transaction list. Everything here is unit-tested. */
object Analytics {

  fun inSpan(
    transactions: List<TransactionEntity>,
    span: DateSpan,
    zone: ZoneId = ZoneId.systemDefault()
  ): List<TransactionEntity> {
    val from = span.startMillis(zone)
    val to = span.endExclusiveMillis(zone)
    return transactions.filter { it.timestamp in from until to }
  }

  fun total(transactions: List<TransactionEntity>, type: TxType): Long =
    transactions.filter { it.type == type }.sumOf { it.amountMinor }

  fun byCategory(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    type: TxType
  ): List<CategoryTotal> {
    val byId = categories.associateBy { it.id }
    val sums = transactions.filter { it.type == type }.groupBy { it.categoryId }
      .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
    val rows = sums.mapNotNull { (id, amount) -> byId[id]?.let { it to amount } }
      .sortedWith(compareByDescending<Pair<CategoryEntity, Long>> { it.second }.thenBy { it.first.sortOrder })
    val grand = rows.sumOf { it.second }
    val percents = percentages(rows.map { it.second })
    return rows.mapIndexed { index, (category, amount) ->
      CategoryTotal(category, amount, if (grand > 0) amount.toFloat() / grand else 0f, percents[index])
    }
  }

  fun spentInCategory(transactions: List<TransactionEntity>, categoryId: Long): Long =
    transactions.filter { it.type == TxType.EXPENSE && it.categoryId == categoryId }.sumOf { it.amountMinor }

  fun budgetTotal(categories: List<CategoryEntity>, weekly: Boolean): Long =
    categories.filter { it.type == TxType.EXPENSE }
      .sumOf { if (weekly) it.weeklyLimitMinor else it.monthlyLimitMinor }

  fun limitOf(category: CategoryEntity, weekly: Boolean): Long =
    if (weekly) category.weeklyLimitMinor else category.monthlyLimitMinor

  /** Date span covered by a dashboard chart range, anchored on the selected month and week. */
  fun rangeSpan(range: ChartRange, month: YearMonth, weekStart: LocalDate): DateSpan = when (range) {
    ChartRange.WEEK -> Periods.weekSpan(weekStart)
    ChartRange.MONTH -> Periods.monthSpan(month)
    ChartRange.QUARTER -> DateSpan(month.minusMonths(2).atDay(1), month.atEndOfMonth())
    ChartRange.YEAR -> DateSpan(month.minusMonths(11).atDay(1), month.atEndOfMonth())
  }

  /**
   * Splits [transactions] into display buckets for a chart: days for a week or month,
   * weeks for a quarter and months for a year.
   */
  fun buckets(
    transactions: List<TransactionEntity>,
    span: DateSpan,
    range: ChartRange,
    type: TxType,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
  ): List<Bucket> {
    val relevant = inSpan(transactions, span, zone).filter { it.type == type }
    val dates = relevant.map { Periods.millisToDate(it.timestamp, zone) to it.amountMinor }
    return when (range) {
      ChartRange.WEEK, ChartRange.MONTH -> {
        val sums = dates.groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }
        (0 until span.dayCount).map { offset ->
          val day = span.start.plusDays(offset.toLong())
          val label = if (range == ChartRange.WEEK) Periods.weekdayShort(day, locale) else day.dayOfMonth.toString()
          Bucket(label, sums[day] ?: 0, day == today)
        }
      }
      ChartRange.QUARTER -> {
        val firstWeek = Periods.weekStart(span.start)
        val weekCount = (ChronoUnit.WEEKS.between(firstWeek, Periods.weekStart(span.end)) + 1).toInt()
        val sums = dates.groupBy({ Periods.weekStart(it.first) }, { it.second }).mapValues { it.value.sum() }
        val currentWeek = Periods.weekStart(today)
        (0 until weekCount).map { index ->
          val start = firstWeek.plusWeeks(index.toLong())
          Bucket("${start.dayOfMonth}/${start.monthValue}", sums[start] ?: 0, start == currentWeek)
        }
      }
      ChartRange.YEAR -> {
        val sums = dates.groupBy({ YearMonth.from(it.first) }, { it.second }).mapValues { it.value.sum() }
        val currentMonth = YearMonth.from(today)
        (0 until 12).map { index ->
          val month = YearMonth.from(span.start).plusMonths(index.toLong())
          Bucket(Periods.shortMonthLabel(month, locale).take(3), sums[month] ?: 0, month == currentMonth)
        }
      }
    }
  }

  /** Opening balance plus all income minus all expenses booked to the account. */
  fun accountBalance(account: AccountEntity, transactions: List<TransactionEntity>): Long {
    var balance = account.openingBalanceMinor
    for (tx in transactions) {
      if (tx.accountId != account.id) continue
      balance += if (tx.type == TxType.INCOME || tx.type == TxType.TRANSFER_IN) tx.amountMinor else -tx.amountMinor
    }
    return balance
  }

  /**
   * "How much of this category's monthly limit is left if I save this entry?" – null when the category
   * has no monthly limit. An entry being edited is excluded so its old amount is not counted twice.
   */
  fun budgetImpact(
    category: CategoryEntity?,
    transactions: List<TransactionEntity>,
    entryDate: LocalDate,
    amountMinor: Long,
    editingId: Long?,
    zone: ZoneId = ZoneId.systemDefault()
  ): BudgetImpact? {
    if (category == null || category.type != TxType.EXPENSE || category.monthlyLimitMinor <= 0) return null
    val monthTransactions = inSpan(transactions, Periods.monthSpan(YearMonth.from(entryDate)), zone)
      .filter { it.id != editingId }
    val spent = spentInCategory(monthTransactions, category.id)
    val limit = effectiveMonthlyLimit(category, transactions.filter { it.id != editingId }, YearMonth.from(entryDate), zone)
    return BudgetImpact(limit, spent, limit - spent - amountMinor)
  }
}

/**
 * Whole-number percentages of [values] that always add up to 100 (largest-remainder method), so a
 * breakdown never reads 97% or 101%. An all-zero list gives all zeros.
 */
fun percentages(values: List<Long>): List<Int> {
  val total = values.sum()
  if (total <= 0) return values.map { 0 }
  val exact = values.map { it * 100.0 / total }
  val result = exact.map { it.toInt() }.toMutableList()
  var missing = 100 - result.sum()
  exact.indices.sortedByDescending { exact[it] - result[it] }.forEach { index ->
    if (missing > 0) {
      result[index]++
      missing--
    }
  }
  return result
}

/** Share of [limit] already used as a whole number; shows 99% rather than 100% until the limit is actually reached. */
fun percentUsed(spent: Long, limit: Long): Int {
  if (limit <= 0) return 0
  val rounded = Math.round(spent * 100.0 / limit).toInt()
  return if (spent < limit) rounded.coerceAtMost(99) else rounded
}

/** A merchant the user enters again and again, with what they last used for it, for one-tap entry. */
data class FrequentEntry(
  val merchant: String,
  val categoryId: Long,
  val accountId: Long,
  val lastAmountMinor: Long,
  val count: Int
)

/** Figures about one month's spending that the Highlights section shows. */
data class Highlights(
  val incomeMinor: Long,
  val expenseMinor: Long,
  /** Days of the month that have passed (the whole month once it's over, 0 for a month still to come). */
  val elapsedDays: Int,
  val dailyAverageMinor: Long,
  /** Where the month will end at the current pace; equals the spend once the month is over. */
  val projectedMinor: Long,
  val noSpendDays: Int,
  val biggest: TransactionEntity?,
  /** Spending over the same stretch of the previous month, so a half-finished month isn't compared with a whole one. */
  val previousSameStretchMinor: Long
) {
  /** Percent change against the previous month, or null when there is nothing to compare with. */
  val changePercent: Int? get() = if (previousSameStretchMinor > 0) Math.round((expenseMinor - previousSameStretchMinor) * 100.0 / previousSameStretchMinor).toInt() else null
  val savingsPercent: Int? get() = if (incomeMinor > 0) Math.round((incomeMinor - expenseMinor) * 100.0 / incomeMinor).toInt() else null
}

fun Analytics.highlights(
  transactions: List<TransactionEntity>,
  month: YearMonth,
  today: LocalDate,
  zone: ZoneId = ZoneId.systemDefault()
): Highlights {
  val monthTx = inSpan(transactions, Periods.monthSpan(month), zone)
  val expenses = monthTx.filter { it.type == TxType.EXPENSE }
  val spent = expenses.sumOf { it.amountMinor }
  val income = total(monthTx, TxType.INCOME)
  val todayMonth = YearMonth.from(today)
  val elapsed = when {
    month.isBefore(todayMonth) -> month.lengthOfMonth()
    month == todayMonth -> today.dayOfMonth
    else -> 0
  }
  val daily = if (elapsed > 0) Math.round(spent.toDouble() / elapsed) else 0L
  val projected = if (month == todayMonth && elapsed > 0) Math.round(spent.toDouble() / elapsed * month.lengthOfMonth()) else spent
  val spendDays = expenses.map { Periods.millisToDate(it.timestamp, zone) }.toSet()
  val noSpend = (1..elapsed).count { month.atDay(it) !in spendDays }
  val previous = month.minusMonths(1)
  val previousSpan = DateSpan(previous.atDay(1), previous.atDay(elapsed.coerceIn(1, previous.lengthOfMonth())))
  val previousSpent = if (elapsed == 0) 0 else inSpan(transactions, previousSpan, zone).filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
  return Highlights(income, spent, elapsed, daily, projected, noSpend, expenses.maxByOrNull { it.amountMinor }, previousSpent)
}

/** Balance of [account] at the end of [endExclusiveMillis]: the opening amount plus everything booked before then. */
fun Analytics.balanceAsOf(account: AccountEntity, transactions: List<TransactionEntity>, endExclusiveMillis: Long): Long =
  accountBalance(account, transactions.filter { it.timestamp < endExclusiveMillis })

/** The end of [month] as an exclusive millisecond bound. */
fun Analytics.endOfMonthMillis(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Long =
  month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Merchants entered at least twice for [type], most frequent first (ties: most recent first), each carrying the
 * category, account and amount of its latest entry. [transactions] is expected newest first, as the database returns it.
 */
fun Analytics.frequentEntries(transactions: List<TransactionEntity>, type: TxType, limit: Int = 6): List<FrequentEntry> =
  transactions.filter { it.type == type && it.merchant.isNotBlank() }
    .groupBy { it.merchant.trim().lowercase() }
    .values
    .map { list -> list.first().let { latest -> FrequentEntry(latest.merchant.trim(), latest.categoryId, latest.accountId, latest.amountMinor, list.size) } }
    .filter { it.count >= 2 }
    .sortedByDescending { it.count }
    .take(limit)

/**
 * A category's monthly limit in [month]. With rollover on, whatever was left unspent in earlier months (up to a
 * year back, never below zero) is added; overspending in a month does not reduce the next one.
 */
fun Analytics.effectiveMonthlyLimit(
  category: CategoryEntity,
  transactions: List<TransactionEntity>,
  month: YearMonth,
  zone: ZoneId = ZoneId.systemDefault()
): Long {
  val limit = category.monthlyLimitMinor
  if (!category.rollover || limit <= 0) return limit
  val earliest = transactions.minOfOrNull { it.timestamp }?.let { YearMonth.from(Periods.millisToDate(it, zone)) } ?: return limit
  var carry = 0L
  var m = if (earliest.isAfter(month.minusMonths(12))) earliest else month.minusMonths(12)
  while (m.isBefore(month)) {
    val spent = spentInCategory(inSpan(transactions, Periods.monthSpan(m), zone), category.id)
    carry = (limit + carry - spent).coerceAtLeast(0)
    m = m.plusMonths(1)
  }
  return limit + carry
}

/** What is carried into [month] for a category (zero without rollover). */
fun Analytics.rolloverCarry(category: CategoryEntity, transactions: List<TransactionEntity>, month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Long =
  effectiveMonthlyLimit(category, transactions, month, zone) - category.monthlyLimitMinor

/** Total planned for the period: weekly limits as set, monthly limits with any rollover added. */
fun Analytics.budgetTotalFor(
  categories: List<CategoryEntity>,
  weekly: Boolean,
  transactions: List<TransactionEntity>,
  month: YearMonth,
  zone: ZoneId = ZoneId.systemDefault()
): Long =
  if (weekly) budgetTotal(categories, true)
  else categories.filter { it.type == TxType.EXPENSE }.sumOf { effectiveMonthlyLimit(it, transactions, month, zone) }

/** The limit that applies to [category] in the period on screen. */
fun Analytics.limitFor(
  category: CategoryEntity,
  weekly: Boolean,
  transactions: List<TransactionEntity>,
  month: YearMonth,
  zone: ZoneId = ZoneId.systemDefault()
): Long = if (weekly) category.weeklyLimitMinor else effectiveMonthlyLimit(category, transactions, month, zone)

/** A finished month that ended with spending inside the total budget. */
data class MonthWrapUp(val month: YearMonth, val spentMinor: Long, val budgetMinor: Long) {
  val leftMinor: Long get() = budgetMinor - spentMinor
}

/**
 * The summary for [month] when it can be celebrated: it had a budget, had entries, and spending stayed within the
 * budget. Null otherwise (no budget, nothing spent, or over budget).
 */
fun Analytics.monthWrapUp(
  categories: List<CategoryEntity>,
  transactions: List<TransactionEntity>,
  month: YearMonth,
  zone: ZoneId = ZoneId.systemDefault()
): MonthWrapUp? {
  val inMonth = inSpan(transactions, Periods.monthSpan(month), zone)
  if (inMonth.none { it.type == TxType.EXPENSE }) return null
  val budget = budgetTotalFor(categories, false, transactions, month, zone)
  if (budget <= 0) return null
  val spent = total(inMonth, TxType.EXPENSE)
  return if (spent <= budget) MonthWrapUp(month, spent, budget) else null
}
