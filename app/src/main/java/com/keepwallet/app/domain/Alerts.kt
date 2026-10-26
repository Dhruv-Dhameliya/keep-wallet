package com.keepwallet.app.domain

import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.RecurringEntity
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class AlertKind { PAYMENTS_WAITING, BILL_OVERDUE, BILL_DUE, BUDGET_OVER, BUDGET_NEAR }

/**
 * Something worth a look right now. [key] is stable for the same situation (so it can be remembered once reminded),
 * [refId] is the bill or category it is about.
 */
data class AlertItem(val key: String, val kind: AlertKind, val title: String, val body: String, val refId: Long? = null)

/** What needs attention today. One place decides it, so the bell in the app and the reminders always agree. */
object Alerts {
  /** Bills this many days away (or sooner) are listed. */
  const val BILL_WINDOW_DAYS = 3

  fun compute(
    detectedCount: Int,
    bills: List<RecurringEntity>,
    categories: List<CategoryEntity>,
    transactions: List<TransactionEntity>,
    today: LocalDate,
    symbol: String
  ): List<AlertItem> {
    val out = mutableListOf<AlertItem>()
    if (detectedCount > 0) {
      out += AlertItem(
        "payments", AlertKind.PAYMENTS_WAITING,
        if (detectedCount == 1) "1 payment waiting" else "$detectedCount payments waiting",
        "Detected from your notifications. Review and add them."
      )
    }
    bills.filter { it.active && it.type == TxType.EXPENSE }.forEach { bill ->
      val due = LocalDate.ofEpochDay(bill.nextDueDay)
      val days = ChronoUnit.DAYS.between(today, due).toInt()
      val amount = Money.formatWhole(bill.amountMinor, symbol)
      when {
        days < 0 -> out += AlertItem(
          "bill:${bill.id}:${bill.nextDueDay}:late", AlertKind.BILL_OVERDUE,
          "${bill.name} was due ${if (days == -1) "yesterday" else "${-days} days ago"}", "$amount · mark it paid or skip it", bill.id
        )
        days <= BILL_WINDOW_DAYS -> out += AlertItem(
          "bill:${bill.id}:${bill.nextDueDay}:${if (days <= 1) days else 2}", AlertKind.BILL_DUE,
          "${bill.name} is due ${when (days) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $days days" }}", amount, bill.id
        )
      }
    }
    val month = YearMonth.from(today)
    val spentThisMonth = Analytics.inSpan(transactions, Periods.monthSpan(month)).filter { it.type == TxType.EXPENSE }
    categories.filter { it.type == TxType.EXPENSE && it.monthlyLimitMinor > 0 }.forEach { category ->
      val limit = Analytics.effectiveMonthlyLimit(category, transactions, month)
      if (limit <= 0) return@forEach
      val spent = spentThisMonth.filter { it.categoryId == category.id }.sumOf { it.amountMinor }
      val percent = (spent * 100 / limit).toInt()
      val ym = "${month.year}${month.monthValue}"
      when {
        percent >= 100 -> out += AlertItem(
          "budget:${category.id}:$ym:100", AlertKind.BUDGET_OVER,
          if (spent > limit) "${category.name} is over budget" else "${category.name} has used its whole budget",
          "${Money.formatWhole(spent, symbol)} of ${Money.formatWhole(limit, symbol)} this month", category.id
        )
        percent >= 80 -> out += AlertItem(
          "budget:${category.id}:$ym:80", AlertKind.BUDGET_NEAR,
          "${category.name} is at $percent%",
          "${Money.formatWhole(limit - spent, symbol)} left this month", category.id
        )
      }
    }
    return out
  }
}
