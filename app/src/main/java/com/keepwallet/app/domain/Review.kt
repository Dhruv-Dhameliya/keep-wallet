package com.keepwallet.app.domain

import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.RecurringEntity
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Income and spending of one month (transfers between your own accounts are not counted either way). */
data class MonthFigure(val month: YearMonth, val incomeMinor: Long, val spentMinor: Long) {
  val netMinor: Long get() = incomeMinor - spentMinor
  val hasActivity: Boolean get() = incomeMinor != 0L || spentMinor != 0L
}

/** Where the rest of the current month is heading at the current pace. */
data class Forecast(
  val spentSoFarMinor: Long,
  val dailyAverageMinor: Long,
  val daysLeft: Int,
  /** Bills that are still due before the month ends. */
  val billsLeftMinor: Long,
  /** Expected total spending for the month: what is spent plus the larger of the pace and the bills still to come. */
  val projectedSpendMinor: Long,
  val budgetMinor: Long
) {
  /** Projected spending against the budget; null without a budget. Positive is room to spare. */
  val projectedLeftMinor: Long? get() = if (budgetMinor > 0) budgetMinor - projectedSpendMinor else null
}

data class ReviewReport(
  val title: String,
  val incomeMinor: Long,
  val spentMinor: Long,
  val topCategories: List<CategoryTotal>,
  /** The 12 months of a yearly review (empty for a monthly one). */
  val months: List<MonthFigure>,
  val best: MonthFigure?,
  val worst: MonthFigure?,
  /** Total across all accounts at the end of each month, oldest first. */
  val netWorth: List<Pair<YearMonth, Long>>,
  val forecast: Forecast?
) {
  val netMinor: Long get() = incomeMinor - spentMinor

  /** Share of income that was kept, in whole percent; null when nothing came in. Can be negative. */
  val savingsRatePercent: Int? get() = if (incomeMinor > 0) Math.round(netMinor * 100.0 / incomeMinor).toInt() else null
}

/** The monthly and yearly review. Pure functions over the stored data, so they are easy to check. */
object Review {
  fun month(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    bills: List<RecurringEntity>,
    month: YearMonth,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
  ): ReviewReport {
    val inMonth = Analytics.inSpan(transactions, Periods.monthSpan(month), zone)
    return ReviewReport(
      title = Periods.monthLabel(month),
      incomeMinor = Analytics.total(inMonth, TxType.INCOME),
      spentMinor = Analytics.total(inMonth, TxType.EXPENSE),
      topCategories = Analytics.byCategory(inMonth, categories, TxType.EXPENSE).take(5),
      months = emptyList(),
      best = null,
      worst = null,
      netWorth = netWorth(accounts, transactions, month, today, zone),
      forecast = forecast(transactions, categories, bills, month, today, zone)
    )
  }

  fun year(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    year: Int,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
  ): ReviewReport {
    val months = (1..12).map { m ->
      val ym = YearMonth.of(year, m)
      val inMonth = Analytics.inSpan(transactions, Periods.monthSpan(ym), zone)
      MonthFigure(ym, Analytics.total(inMonth, TxType.INCOME), Analytics.total(inMonth, TxType.EXPENSE))
    }
    val active = months.filter { it.hasActivity }
    val inYear = transactions.filter { Periods.millisToDate(it.timestamp, zone).year == year }
    val end = YearMonth.of(year, 12)
    return ReviewReport(
      title = year.toString(),
      incomeMinor = months.sumOf { it.incomeMinor },
      spentMinor = months.sumOf { it.spentMinor },
      topCategories = Analytics.byCategory(inYear, categories, TxType.EXPENSE).take(5),
      months = months,
      best = active.maxByOrNull { it.netMinor },
      worst = active.minByOrNull { it.netMinor },
      netWorth = netWorth(accounts, transactions, end, today, zone),
      forecast = null
    )
  }

  /** The last 12 month-end totals up to [end], never into the future. */
  fun netWorth(
    accounts: List<AccountEntity>,
    transactions: List<TransactionEntity>,
    end: YearMonth,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
  ): List<Pair<YearMonth, Long>> {
    val last = minOf(end, YearMonth.from(today))
    return (11 downTo 0).map { last.minusMonths(it.toLong()) }.map { m ->
      val cutoff = Analytics.endOfMonthMillis(m, zone)
      m to accounts.sumOf { Analytics.balanceAsOf(it, transactions, cutoff) }
    }
  }

  /** Only for the month that is under way: pace so far, bills still to come, and the likely total. */
  fun forecast(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    bills: List<RecurringEntity>,
    month: YearMonth,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
  ): Forecast? {
    if (YearMonth.from(today) != month) return null
    val spent = Analytics.total(Analytics.inSpan(transactions, Periods.monthSpan(month), zone), TxType.EXPENSE)
    val daysPassed = today.dayOfMonth
    val daysLeft = month.lengthOfMonth() - daysPassed
    val average = spent / daysPassed
    val monthEnd = month.atEndOfMonth()
    val billsLeft = bills.filter { it.active && it.type == TxType.EXPENSE }.sumOf { bill ->
      val first = LocalDate.ofEpochDay(bill.nextDueDay)
      if (first.isAfter(monthEnd)) 0L
      else Recurrence.occurrences(first, monthEnd, bill.frequency, bill.anchorDay).count { it.isAfter(today) } * bill.amountMinor
    }
    val projected = spent + maxOf(average * daysLeft, billsLeft)
    return Forecast(spent, average, daysLeft, billsLeft, projected, Analytics.budgetTotalFor(categories, false, transactions, month, zone))
  }
}
