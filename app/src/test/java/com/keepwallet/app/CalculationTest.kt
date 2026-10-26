package com.keepwallet.app

import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.ChartRange
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.percentUsed
import com.keepwallet.app.domain.percentages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** Edge cases for the arithmetic users rely on: rounding, period boundaries, time zones, big numbers. */
class CalculationTest {

  private fun category(id: Long, name: String) = CategoryEntity(id = id, name = name, iconKey = "other", color = 0, sortOrder = id.toInt())

  private fun tx(id: Long, minor: Long, categoryId: Long, zone: ZoneId, date: LocalDate, time: LocalTime, type: TxType = TxType.EXPENSE, account: Long = 1) =
    TransactionEntity(
      id = id, type = type, amountMinor = minor, merchant = "m", categoryId = categoryId, accountId = account,
      timestamp = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    )

  // ---- percentages ----

  @Test
  fun `percentages always add up to exactly 100`() {
    val cases = listOf(
      listOf(120_000L, 14_700L, 5_400L, 5_400L, 3_500L, 1_000L), // the sample month: used to read 97%
      listOf(1L, 1L, 1L),
      listOf(1L, 2L),
      listOf(333L, 333L, 334L),
      listOf(1_000_000L, 1L),
      listOf(5L)
    )
    cases.forEach { values -> assertEquals(values.toString(), 100, percentages(values).sum()) }
    assertEquals(listOf(33, 33, 34), percentages(listOf(333, 333, 334)))
    assertEquals(listOf(0, 0), percentages(listOf(0, 0)))
    assertEquals(emptyList<Int>(), percentages(emptyList()))
  }

  @Test
  fun `tiny shares are labelled less than one percent`() {
    val categories = listOf(category(1, "Rent"), category(2, "Streaming"))
    val zone = ZoneId.of("UTC")
    val day = LocalDate.of(2026, 10, 5)
    val rows = Analytics.byCategory(
      listOf(tx(1, 1_000_000, 1, zone, day, LocalTime.NOON), tx(2, 100, 2, zone, day, LocalTime.NOON)),
      categories, TxType.EXPENSE
    )
    assertEquals(listOf("100%", "<1%"), rows.map { it.percentLabel })
    assertEquals(100, rows.sumOf { it.percent })
  }

  @Test
  fun `percent used never claims 100 before the limit is reached`() {
    assertEquals(0, percentUsed(0, 1_000))
    assertEquals(47, percentUsed(1_499, 3_200))
    assertEquals(99, percentUsed(9_996, 10_000))
    assertEquals(100, percentUsed(10_000, 10_000))
    assertEquals(120, percentUsed(12_000, 10_000))
    assertEquals(0, percentUsed(500, 0))
  }

  // ---- periods, time zones, daylight saving ----

  @Test
  fun `entries on the last and first minute of a month land in the right month in any zone`() {
    listOf("Asia/Kolkata", "America/New_York", "Pacific/Auckland", "UTC").map(ZoneId::of).forEach { zone ->
      val oct = YearMonth.of(2026, 10)
      val txs = listOf(
        tx(1, 100, 1, zone, LocalDate.of(2026, 9, 30), LocalTime.of(23, 59, 59)),
        tx(2, 200, 1, zone, LocalDate.of(2026, 10, 1), LocalTime.MIDNIGHT),
        tx(3, 400, 1, zone, LocalDate.of(2026, 10, 31), LocalTime.of(23, 59, 59)),
        tx(4, 800, 1, zone, LocalDate.of(2026, 11, 1), LocalTime.MIDNIGHT)
      )
      val inOct = Analytics.inSpan(txs, Periods.monthSpan(oct), zone)
      assertEquals("zone $zone", setOf(2L, 3L), inOct.map { it.id }.toSet())
    }
  }

  @Test
  fun `a week that crosses the daylight saving change still has seven days of data`() {
    val zone = ZoneId.of("America/New_York")
    val weekStart = LocalDate.of(2026, 11, 2) // clocks go back on Sunday 1 Nov 2026; week after includes no change, check week of change too
    val changeWeek = LocalDate.of(2026, 10, 26)
    listOf(changeWeek, weekStart).forEach { start ->
      val span = Periods.weekSpan(start)
      val txs = (0..6).map { d -> tx(d.toLong(), 100, 1, zone, start.plusDays(d.toLong()), LocalTime.of(0, 30)) } +
        (0..6).map { d -> tx(10L + d, 100, 1, zone, start.plusDays(d.toLong()), LocalTime.of(23, 30)) }
      val buckets = Analytics.buckets(txs, span, ChartRange.WEEK, TxType.EXPENSE, start, zone, Locale.ENGLISH)
      assertEquals(7, buckets.size)
      assertTrue("each day has both entries: $buckets", buckets.all { it.valueMinor == 200L })
    }
  }

  @Test
  fun `keeping the time of day when the date changes survives the spring forward gap`() {
    val zone = ZoneId.of("America/New_York")
    // 02:30 does not exist on 8 Mar 2026; the result must still be on that day.
    val original = LocalDate.of(2026, 3, 1).atTime(2, 30).atZone(zone).toInstant().toEpochMilli()
    val moved = Periods.combine(LocalDate.of(2026, 3, 8), original, zone)
    assertEquals(LocalDate.of(2026, 3, 8), Periods.millisToDate(moved, zone))
  }

  @Test
  fun `weeks that span two months are labelled and bucketed correctly`() {
    assertEquals("28 Sep – 4 Oct", Periods.weekLabel(LocalDate.of(2026, 9, 28), Locale.ENGLISH))
    assertEquals(LocalDate.of(2026, 9, 28), Periods.weekStart(LocalDate.of(2026, 10, 4)))
    // a Sunday belongs to the week that started the Monday before, not the next one
    assertEquals(LocalDate.of(2026, 10, 5), Periods.weekStart(LocalDate.of(2026, 10, 11)))
    assertEquals(LocalDate.of(2026, 10, 12), Periods.weekStart(LocalDate.of(2026, 10, 12)))
  }

  // ---- balances and totals ----

  @Test
  fun `balances stay separate per account and sum to the total`() {
    val zone = ZoneId.of("UTC")
    val day = LocalDate.of(2026, 10, 5)
    val card = AccountEntity(id = 1, name = "Card", openingBalanceMinor = 100_000)
    val cash = AccountEntity(id = 2, name = "Cash", openingBalanceMinor = 5_000)
    val txs = listOf(
      tx(1, 2_500, 1, zone, day, LocalTime.NOON, account = 1),
      tx(2, 1_000, 1, zone, day, LocalTime.NOON, account = 2),
      tx(3, 480_000, 9, zone, day, LocalTime.NOON, TxType.INCOME, account = 1)
    )
    assertEquals(100_000 - 2_500 + 480_000, Analytics.accountBalance(card, txs))
    assertEquals(5_000 - 1_000, Analytics.accountBalance(cash, txs))
    assertEquals(
      card.openingBalanceMinor + cash.openingBalanceMinor + 480_000 - 2_500 - 1_000,
      Analytics.accountBalance(card, txs) + Analytics.accountBalance(cash, txs)
    )
  }

  @Test
  fun `sums use exact integer arithmetic`() {
    val zone = ZoneId.of("UTC")
    val day = LocalDate.of(2026, 10, 5)
    // 0.1 + 0.2 style drift must not exist: 1000 x 10 cents is exactly 100.00
    val txs = (1..1000).map { tx(it.toLong(), 10, 1, zone, day, LocalTime.NOON) }
    assertEquals(10_000L, Analytics.total(txs, TxType.EXPENSE))
    assertEquals(1_999L, Money.toMinor(19.99))
    assertEquals(1_005L, Money.toMinor(10.05))
    assertEquals(1_000_005L, Money.parseMinor("10000.05"))
  }

  @Test
  fun `large amounts format without overflow or precision loss`() {
    assertEquals("$123,456,789.99", Money.format(12_345_678_999, "$"))
    assertEquals("₹12,34,56,789", Money.formatWhole(123_456_789_00, "₹"))
    assertEquals("$999,999,999,999", Money.formatWhole(99_999_999_999_900, "$"))
    assertEquals("$0.05", Money.format(5, "$"))
    assertEquals("-$0.05", Money.format(-5, "$"))
    assertEquals("$1", Money.formatWhole(99, "$"))
    assertEquals("$0", Money.formatWhole(49, "$"))
  }

  @Test
  fun `weekly budget total and category limits are independent of the monthly ones`() {
    val a = category(1, "A").copy(monthlyLimitMinor = 100_000, weeklyLimitMinor = 20_000)
    val b = category(2, "B").copy(monthlyLimitMinor = 50_000, weeklyLimitMinor = 0)
    val income = category(3, "Pay").copy(type = TxType.INCOME, monthlyLimitMinor = 999_999, weeklyLimitMinor = 999_999)
    val all = listOf(a, b, income)
    assertEquals(150_000L, Analytics.budgetTotal(all, weekly = false))
    assertEquals(20_000L, Analytics.budgetTotal(all, weekly = true))
  }
}
