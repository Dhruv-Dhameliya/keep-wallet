package com.keepwallet.app

import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.ChartRange
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.AmountInput
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.Treemap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

class DomainTest {
  private val zone = ZoneId.of("UTC")
  private val today = LocalDate.of(2026, 10, 12) // a Monday

  private val groceries = CategoryEntity(id = 1, name = "Groceries", iconKey = "groceries", color = 0, monthlyLimitMinor = 60_000, weeklyLimitMinor = 20_000)
  private val dining = CategoryEntity(id = 2, name = "Dining", iconKey = "dining", color = 0, monthlyLimitMinor = 24_000, weeklyLimitMinor = 8_000, sortOrder = 1)
  private val salary = CategoryEntity(id = 3, name = "Salary", iconKey = "salary", color = 0, type = TxType.INCOME)
  private val card = AccountEntity(id = 1, name = "Card", openingBalanceMinor = 100_000)

  private fun tx(
    id: Long, date: LocalDate, minor: Long, category: Long = 1, type: TxType = TxType.EXPENSE,
    time: LocalTime = LocalTime.NOON
  ) = TransactionEntity(
    id = id, type = type, amountMinor = minor, merchant = "m$id", categoryId = category, accountId = 1,
    timestamp = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
  )

  // ---- Money ----

  @Test
  fun `money formats with grouping and sign`() {
    assertEquals("$1,234.50", Money.format(123_450, "$"))
    assertEquals("-$12.50", Money.format(-1_250, "$"))
    assertEquals("+$12.50", Money.format(1_250, "$", forceSign = true))
    assertEquals("$2,340", Money.formatWhole(234_000, "$"))
    assertEquals("₹1,23,456", Money.formatWhole(12_345_600, "₹"))
    assertEquals("-$5", Money.formatWhole(-500, "$"))
  }

  @Test
  fun `money parsing is exact`() {
    assertEquals(1_250L, Money.parseMinor("12.5"))
    assertEquals(1_999L, Money.parseMinor("19.99"))
    assertEquals(0L, Money.parseMinor("abc"))
    assertEquals(0L, Money.parseMinor(""))
    assertEquals(1_099L, Money.toMinor(10.99))
    assertEquals("12.5", Money.toEditText(1_250))
    assertEquals("12", Money.toEditText(1_200))
    assertEquals("0", Money.toEditText(0))
  }

  @Test
  fun `compact axis labels`() {
    assertEquals("$0", Money.formatCompact(0, "$"))
    assertEquals("$150", Money.formatCompact(15_000, "$"))
    assertEquals("$1,500", Money.formatCompact(150_000, "$"))
    assertEquals("$12.5k", Money.formatCompact(1_250_000, "$"))
  }

  // ---- Keypad ----

  @Test
  fun `keypad builds amounts like a calculator`() {
    fun type(vararg keys: String) = keys.fold("0") { text, key -> AmountInput.press(text, key) }
    assertEquals("24", type("2", "4"))
    assertEquals("0.5", type(".", "5"))
    assertEquals("12.34", type("1", "2", ".", "3", "4", "5"))
    assertEquals("1.5", type("1", ".", ".", "5"))
    assertEquals("0", type("0", "0", "0"))
    assertEquals("5", type("0", "5"))
    assertEquals("12", type("1", "2", "3", AmountInput.BACKSPACE))
    assertEquals("0", type("7", AmountInput.BACKSPACE, AmountInput.BACKSPACE))
    assertEquals("123456789", type("1", "2", "3", "4", "5", "6", "7", "8", "9", "9"))
  }

  // ---- Periods ----

  @Test
  fun `weeks run monday to sunday`() {
    assertEquals(LocalDate.of(2026, 10, 12), Periods.weekStart(LocalDate.of(2026, 10, 18)))
    assertEquals(LocalDate.of(2026, 10, 5), Periods.weekStart(LocalDate.of(2026, 10, 11)))
    assertEquals("5 – 11 Oct", Periods.weekLabel(LocalDate.of(2026, 10, 5), Locale.ENGLISH))
    assertEquals("28 Sep – 4 Oct", Periods.weekLabel(LocalDate.of(2026, 9, 28), Locale.ENGLISH))
    assertEquals("October 2026", Periods.monthLabel(YearMonth.of(2026, 10), Locale.ENGLISH))
  }

  @Test
  fun `day headers say today and yesterday`() {
    assertEquals("Today · 12 Oct", Periods.dayHeader(today, today, Locale.ENGLISH))
    assertEquals("Yesterday · 11 Oct", Periods.dayHeader(today.minusDays(1), today, Locale.ENGLISH))
    assertEquals("8 Oct 2026", Periods.dayHeader(today.minusDays(4), today, Locale.ENGLISH))
  }

  // ---- Analytics ----

  @Test
  fun `span filtering is inclusive of both end days`() {
    val txs = listOf(
      tx(1, LocalDate.of(2026, 10, 1), 100, time = LocalTime.MIDNIGHT),
      tx(2, LocalDate.of(2026, 10, 31), 200, time = LocalTime.of(23, 59)),
      tx(3, LocalDate.of(2026, 11, 1), 400, time = LocalTime.MIDNIGHT),
      tx(4, LocalDate.of(2026, 9, 30), 800, time = LocalTime.of(23, 59))
    )
    val october = Analytics.inSpan(txs, Periods.monthSpan(YearMonth.of(2026, 10)), zone)
    assertEquals(setOf(1L, 2L), october.map { it.id }.toSet())
  }

  @Test
  fun `category totals are sorted and sum to one`() {
    val txs = listOf(
      tx(1, today, 5_000, 1), tx(2, today, 1_000, 1), tx(3, today, 3_000, 2),
      tx(4, today, 480_000, 3, TxType.INCOME)
    )
    val totals = Analytics.byCategory(txs, listOf(groceries, dining, salary), TxType.EXPENSE)
    assertEquals(listOf("Groceries", "Dining"), totals.map { it.category.name })
    assertEquals(6_000L, totals[0].amountMinor)
    assertEquals(1f, totals.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    val income = Analytics.byCategory(txs, listOf(groceries, dining, salary), TxType.INCOME)
    assertEquals(1, income.size)
    assertEquals(1f, income[0].share, 0.0001f)
  }

  @Test
  fun `week buckets cover seven days and flag today`() {
    val weekStart = Periods.weekStart(today)
    val txs = listOf(tx(1, weekStart, 1_000), tx(2, weekStart, 500), tx(3, weekStart.plusDays(2), 2_000))
    val buckets = Analytics.buckets(
      txs, Periods.weekSpan(weekStart), ChartRange.WEEK, TxType.EXPENSE, today, zone, Locale.ENGLISH
    )
    assertEquals(7, buckets.size)
    assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), buckets.map { it.label })
    assertEquals(listOf(1_500L, 0L, 2_000L, 0L, 0L, 0L, 0L), buckets.map { it.valueMinor })
    assertTrue(buckets[0].isCurrent)
    assertEquals(1, buckets.count { it.isCurrent })
  }

  @Test
  fun `month buckets have one entry per day`() {
    val month = YearMonth.of(2026, 10)
    val buckets = Analytics.buckets(emptyList(), Periods.monthSpan(month), ChartRange.MONTH, TxType.EXPENSE, today, zone)
    assertEquals(31, buckets.size)
    assertEquals("12", buckets.first { it.isCurrent }.label)
  }

  @Test
  fun `quarter and year buckets group by week and month`() {
    val month = YearMonth.of(2026, 10)
    val txs = listOf(
      tx(1, LocalDate.of(2026, 10, 1), 1_000),
      tx(2, LocalDate.of(2026, 8, 20), 2_000),
      tx(3, LocalDate.of(2025, 12, 5), 4_000)
    )
    val quarter = Analytics.buckets(txs, Analytics.rangeSpan(ChartRange.QUARTER, month, today), ChartRange.QUARTER, TxType.EXPENSE, today, zone)
    assertEquals(3_000L, quarter.sumOf { it.valueMinor })
    assertTrue(quarter.size in 13..14)
    val year = Analytics.buckets(txs, Analytics.rangeSpan(ChartRange.YEAR, month, today), ChartRange.YEAR, TxType.EXPENSE, today, zone, Locale.ENGLISH)
    assertEquals(12, year.size)
    assertEquals(7_000L, year.sumOf { it.valueMinor })
    assertEquals("Oct", year.last().label)
    assertTrue(year.last().isCurrent)
  }

  @Test
  fun `account balance is opening balance plus income minus expenses`() {
    val txs = listOf(
      tx(1, today, 2_500), tx(2, today, 480_000, 3, TxType.INCOME),
      TransactionEntity(id = 9, type = TxType.EXPENSE, amountMinor = 999, merchant = "x", categoryId = 1, accountId = 2, timestamp = 0)
    )
    assertEquals(100_000L - 2_500 + 480_000, Analytics.accountBalance(card, txs))
  }

  @Test
  fun `balance follows edits and deletes because it is derived`() {
    val before = listOf(tx(1, today, 2_500))
    val edited = listOf(tx(1, today, 4_000))
    assertEquals(97_500L, Analytics.accountBalance(card, before))
    assertEquals(96_000L, Analytics.accountBalance(card, edited))
    assertEquals(100_000L, Analytics.accountBalance(card, emptyList()))
  }

  @Test
  fun `budget impact matches the daily reference board`() {
    // Groceries budget 600, 420 already spent, entering 24 -> 156 left.
    val txs = listOf(tx(1, LocalDate.of(2026, 10, 5), 42_000))
    val impact = Analytics.budgetImpact(groceries, txs, today, 2_400, null, zone)!!
    assertEquals(60_000L, impact.limitMinor)
    assertEquals(42_000L, impact.spentMinor)
    assertEquals(15_600L, impact.leftAfterMinor)
  }

  @Test
  fun `budget impact ignores other months and the entry being edited`() {
    val txs = listOf(
      tx(1, LocalDate.of(2026, 10, 5), 10_000),
      tx(2, LocalDate.of(2026, 9, 5), 50_000),
      tx(3, today, 2_400)
    )
    val impact = Analytics.budgetImpact(groceries, txs, today, 2_400, editingId = 3, zone = zone)!!
    assertEquals(10_000L, impact.spentMinor)
    assertEquals(60_000L - 10_000 - 2_400, impact.leftAfterMinor)
  }

  @Test
  fun `budget impact can go negative and is null without a limit`() {
    val txs = listOf(tx(1, today, 59_000))
    assertEquals(-1_000L, Analytics.budgetImpact(groceries, txs, today, 2_000, null, zone)!!.leftAfterMinor)
    assertNull(Analytics.budgetImpact(groceries.copy(monthlyLimitMinor = 0), txs, today, 100, null, zone))
    assertNull(Analytics.budgetImpact(salary, txs, today, 100, null, zone))
    assertNull(Analytics.budgetImpact(null, txs, today, 100, null, zone))
  }

  @Test
  fun `budget totals use the right period`() {
    val all = listOf(groceries, dining, salary)
    assertEquals(84_000L, Analytics.budgetTotal(all, weekly = false))
    assertEquals(28_000L, Analytics.budgetTotal(all, weekly = true))
  }

  // ---- Treemap ----

  @Test
  fun `treemap cells tile the area without overlap`() {
    val cells = Treemap.layout(
      listOf("Housing" to 1200.0, "Groceries" to 420.0, "Dining" to 260.0, "Shopping" to 200.0, "Transport" to 180.0, "Subs" to 80.0),
      width = 300f, height = 200f
    )
    assertEquals(6, cells.size)
    assertEquals(300f * 200f, cells.sumOf { (it.width * it.height).toDouble() }.toFloat(), 1f)
    cells.forEach { c ->
      assertTrue(c.x >= -0.01f && c.y >= -0.01f && c.x + c.width <= 300.01f && c.y + c.height <= 200.01f)
    }
    for (i in cells.indices) for (j in i + 1 until cells.size) {
      val a = cells[i]; val b = cells[j]
      val overlapX = minOf(a.x + a.width, b.x + b.width) - maxOf(a.x, b.x)
      val overlapY = minOf(a.y + a.height, b.y + b.height) - maxOf(a.y, b.y)
      assertTrue("cells $i and $j overlap", overlapX <= 0.05f || overlapY <= 0.05f)
    }
    val housing = cells.first { it.item == "Housing" }
    assertEquals(0.51f, housing.width * housing.height / (300f * 200f), 0.02f)
  }

  @Test
  fun `treemap handles empty and zero input`() {
    assertTrue(Treemap.layout(emptyList<Pair<String, Double>>(), 100f, 100f).isEmpty())
    assertTrue(Treemap.layout(listOf("a" to 0.0), 100f, 100f).isEmpty())
    assertEquals(1, Treemap.layout(listOf("a" to 5.0, "b" to 0.0), 100f, 100f).size)
    assertNotNull(Treemap.layout(listOf("a" to 5.0), 100f, 100f).first())
  }
}
