package com.keepwallet.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** An inclusive range of calendar days. */
data class DateSpan(val start: LocalDate, val end: LocalDate) {
  val dayCount: Int get() = (end.toEpochDay() - start.toEpochDay()).toInt() + 1

  operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

  fun startMillis(zone: ZoneId): Long = start.atStartOfDay(zone).toInstant().toEpochMilli()

  fun endExclusiveMillis(zone: ZoneId): Long = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

  fun containsMillis(millis: Long, zone: ZoneId): Boolean = millis >= startMillis(zone) && millis < endExclusiveMillis(zone)
}

object Periods {
  /** Weeks run Monday to Sunday, matching the reference boards. */
  fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

  fun weekSpan(weekStart: LocalDate): DateSpan = DateSpan(weekStart, weekStart.plusDays(6))

  fun monthSpan(month: YearMonth): DateSpan = DateSpan(month.atDay(1), month.atEndOfMonth())

  fun millisToDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

  /** Moment on [date] using the wall-clock time of [timeSource], used so date edits keep their time of day. */
  fun combine(date: LocalDate, timeSource: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val time = Instant.ofEpochMilli(timeSource).atZone(zone).toLocalTime()
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
  }

  fun monthLabel(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

  fun shortMonthLabel(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.SHORT, locale)

  fun monthName(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.month.getDisplayName(TextStyle.FULL, locale)

  /** "5 – 11 Oct" or "28 Sep – 4 Oct" when the week crosses a month boundary. */
  fun weekLabel(weekStart: LocalDate, locale: Locale = Locale.getDefault()): String {
    val end = weekStart.plusDays(6)
    val endMonth = end.month.getDisplayName(TextStyle.SHORT, locale)
    return if (weekStart.month == end.month) {
      "${weekStart.dayOfMonth} – ${end.dayOfMonth} $endMonth"
    } else {
      val startMonth = weekStart.month.getDisplayName(TextStyle.SHORT, locale)
      "${weekStart.dayOfMonth} $startMonth – ${end.dayOfMonth} $endMonth"
    }
  }

  fun dayHeader(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
    val pattern = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    return when (date) {
      today -> "Today · ${date.format(DateTimeFormatter.ofPattern("d MMM", locale))}"
      today.minusDays(1) -> "Yesterday · ${date.format(DateTimeFormatter.ofPattern("d MMM", locale))}"
      else -> date.format(pattern)
    }
  }

  /** "Today", "Yesterday" or "12 Oct". */
  fun shortDay(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("d MMM", locale))
  }

  fun weekdayShort(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
}
