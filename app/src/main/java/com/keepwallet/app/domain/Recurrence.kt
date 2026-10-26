package com.keepwallet.app.domain

import com.keepwallet.app.data.Frequency
import java.time.LocalDate
import java.time.YearMonth

/** Dates for bills that repeat. */
object Recurrence {
  /**
   * The occurrence after [current]. Monthly and yearly bills stay on [anchorDay] (clamped to short months),
   * so a bill on the 31st goes 31 Jan, 28 Feb, 31 Mar rather than drifting to the 28th for good.
   */
  fun next(current: LocalDate, frequency: Frequency, anchorDay: Int): LocalDate = when (frequency) {
    Frequency.WEEKLY -> current.plusDays(7)
    Frequency.MONTHLY -> onAnchor(YearMonth.from(current).plusMonths(1), anchorDay)
    Frequency.YEARLY -> onAnchor(YearMonth.from(current).plusYears(1), anchorDay)
  }

  /** Every occurrence from [first] up to and including [until], oldest first (at most [limit] of them). */
  fun occurrences(first: LocalDate, until: LocalDate, frequency: Frequency, anchorDay: Int, limit: Int = 120): List<LocalDate> {
    val result = mutableListOf<LocalDate>()
    var day = first
    while (!day.isAfter(until) && result.size < limit) {
      result += day
      day = next(day, frequency, anchorDay)
    }
    return result
  }

  private fun onAnchor(month: YearMonth, anchorDay: Int): LocalDate = month.atDay(anchorDay.coerceIn(1, month.lengthOfMonth()))

  fun label(frequency: Frequency): String = when (frequency) {
    Frequency.WEEKLY -> "Weekly"
    Frequency.MONTHLY -> "Monthly"
    Frequency.YEARLY -> "Yearly"
  }

  /** "Due today", "Due tomorrow", "Due in 5 days" or "3 days overdue". */
  fun dueText(due: LocalDate, today: LocalDate): String {
    val days = (due.toEpochDay() - today.toEpochDay()).toInt()
    return when {
      days == 0 -> "Due today"
      days == 1 -> "Due tomorrow"
      days > 1 -> "Due in $days days"
      days == -1 -> "1 day overdue"
      else -> "${-days} days overdue"
    }
  }
}
