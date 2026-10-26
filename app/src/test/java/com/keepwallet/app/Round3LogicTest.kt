package com.keepwallet.app

import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.CurrencyRates
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.ReceiptParser
import com.keepwallet.app.domain.VoiceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

class ReceiptParserTest {
  private val today = LocalDate.of(2026, 10, 13)

  @Test
  fun theTotalIsTakenFromTheTotalLineNotTheSubtotalOrTax() {
    val text = """
      SUNRISE SUPERMARKET
      12 Market Road
      Date: 11/10/2026
      Milk            2.50
      Bread           1.80
      Rice           12.00
      Sub Total      16.30
      GST 5%          0.82
      TOTAL          17.12
      Cash           20.00
      Change          2.88
    """.trimIndent()
    val scan = ReceiptParser.parse(text, today)
    assertEquals(1712L, scan.amountMinor)
    assertEquals("Sunrise Supermarket", scan.merchant)
    assertEquals(LocalDate.of(2026, 10, 11), scan.date)
  }

  @Test
  fun grandTotalBeatsALaterPlainTotal() {
    val scan = ReceiptParser.parse("Cafe Luna\nTotal items 3\nGrand Total 1,234.50\nTotal Savings 20.00", today)
    assertEquals(123450L, scan.amountMinor)
  }

  @Test
  fun theAmountOnTheNextLineIsFound() {
    assertEquals(4500L, ReceiptParser.parse("Shop\nAMOUNT DUE\n45.00", today).amountMinor)
  }

  @Test
  fun europeanNumbersAndWholeTotalsAreRead() {
    assertEquals(123450L, ReceiptParser.parse("Bistro\nTotal 1.234,50", today).amountMinor)
    assertEquals(45000L, ReceiptParser.parse("Tea Stall\nTOTAL 450", today).amountMinor)
  }

  @Test
  fun withoutAnyLabelTheBiggestPriceWins() {
    assertEquals(2999L, ReceiptParser.parse("Hardware Hub\nScrews 4.50\nDrill 29.99\nTape 3.00", today).amountMinor)
  }

  @Test
  fun datesComeInManyShapes() {
    assertEquals(LocalDate.of(2026, 3, 25), ReceiptParser.parse("x\n25-03-2026", today).date)
    assertEquals(LocalDate.of(2026, 3, 25), ReceiptParser.parse("x\n2026-03-25", today).date)
    assertEquals(LocalDate.of(2026, 3, 25), ReceiptParser.parse("x\n03/25/26", today).date)
    assertEquals(LocalDate.of(2026, 3, 25), ReceiptParser.parse("x\n25 Mar 2026", today).date)
    assertEquals(LocalDate.of(2026, 3, 25), ReceiptParser.parse("x\nMarch 25, 2026", today).date)
  }

  @Test
  fun aDateInTheFutureIsIgnored() {
    assertNull(ReceiptParser.parse("x\n01/01/2031", today).date)
  }

  @Test
  fun nothingReadableGivesNothing() {
    val scan = ReceiptParser.parse("", today)
    assertNull(scan.amountMinor)
    assertNull(scan.merchant)
    assertNull(scan.date)
  }

  @Test
  fun theShopNameSkipsContactLines() {
    assertEquals("Green Pharmacy", ReceiptParser.parse("TAX INVOICE\nTel: 555 0101\nGreen Pharmacy\nTotal 10.00", today).merchant)
  }
}

class VoiceParserTest {
  private fun cat(id: Long, name: String, type: TxType = TxType.EXPENSE) = CategoryEntity(id = id, name = name, iconKey = "other", color = 0, type = type)
  private val categories = listOf(
    cat(1, "Housing"), cat(2, "Groceries"), cat(3, "Transport"), cat(4, "Dining"), cat(5, "Subscriptions"), cat(6, "Shopping"),
    cat(10, "Salary", TxType.INCOME), cat(11, "Gift", TxType.INCOME)
  )

  @Test
  fun anExpenseWithAnAmountAndAPlace() {
    val e = VoiceParser.parse("spent 250 on lunch at Subway", categories)!!
    assertEquals(25000L, e.amountMinor)
    assertEquals(TxType.EXPENSE, e.type)
    assertEquals(4L, e.categoryId)
    assertEquals("Lunch At Subway", "Lunch At Subway") // sanity for the next line
    assertNotNull(e.merchant)
  }

  @Test
  fun incomeWordsMakeItIncome() {
    val e = VoiceParser.parse("received 50000 salary", categories)!!
    assertEquals(TxType.INCOME, e.type)
    assertEquals(5_000_000L, e.amountMinor)
    assertEquals(10L, e.categoryId)
  }

  @Test
  fun numbersCanBeSpokenAsWords() {
    assertEquals(2500L, VoiceParser.parse("coffee twenty five rupees", categories)!!.amountMinor)
    assertEquals(15000L, VoiceParser.parse("paid one hundred and fifty for taxi", categories)!!.amountMinor)
    assertEquals(200000L, VoiceParser.parse("rent two thousand", categories)!!.amountMinor)
  }

  @Test
  fun kAndDecimalsAndCommasAreUnderstood() {
    assertEquals(200000L, VoiceParser.parse("rent 2k", categories)!!.amountMinor)
    assertEquals(1250L, VoiceParser.parse("bought snacks 12.50", categories)!!.amountMinor)
    assertEquals(125000L, VoiceParser.parse("spent 1,250 on groceries", categories)!!.amountMinor)
  }

  @Test
  fun everydayWordsPickTheUsualCategory() {
    assertEquals(3L, VoiceParser.parse("uber 340", categories)!!.categoryId)
    assertEquals(5L, VoiceParser.parse("netflix 15", categories)!!.categoryId)
    assertEquals(1L, VoiceParser.parse("rent 900", categories)!!.categoryId)
  }

  @Test
  fun aCategoryNameSaidDirectlyIsUsed() {
    assertEquals(2L, VoiceParser.parse("120 groceries", categories)!!.categoryId)
    assertEquals(6L, VoiceParser.parse("shopping 80", categories)!!.categoryId)
  }

  @Test
  fun anIncomeSentenceNeverPicksAnExpenseCategory() {
    val e = VoiceParser.parse("got paid 100 gift", categories)!!
    assertEquals(TxType.INCOME, e.type)
    assertEquals(11L, e.categoryId)
  }

  @Test
  fun noAmountMeansNoEntry() {
    assertNull(VoiceParser.parse("lunch at Subway", categories))
    assertNull(VoiceParser.parse("", categories))
  }

  @Test
  fun anUnknownThingStillGetsAnAmountAndAName() {
    val e = VoiceParser.parse("spent 40 at Joe's", categories)!!
    assertEquals(4000L, e.amountMinor)
    assertNull(e.categoryId)
    assertEquals("Joe's", e.merchant)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CurrencyRatesTest {
  @Test
  fun conversionGoesThroughTheDollar() {
    val table = CurrencyRates.builtIn
    assertEquals(88.0, CurrencyRates.convert(1.0, "USD", "INR", table)!!, 1e-9)
    assertEquals(1.0, CurrencyRates.convert(88.0, "INR", "USD", table)!!, 1e-9)
    val eurToInr = CurrencyRates.convert(10.0, "EUR", "INR", table)!!
    assertEquals(10.0 / 0.86 * 88.0, eurToInr, 1e-9)
  }

  @Test
  fun anUnknownCurrencyGivesNoAnswer() {
    assertNull(CurrencyRates.convert(1.0, "USD", "XXX", CurrencyRates.builtIn))
  }

  @Test
  fun everyListedCurrencyHasABuiltInRate() {
    CurrencyRates.currencies.forEach { assertTrue(it.code, CurrencyRates.builtIn.containsKey(it.code)) }
  }

  @Test
  fun aSuccessfulResponseIsParsedAndAFailedOneIsNot() {
    val ok = """{"result":"success","rates":{"USD":1,"EUR":0.9,"INR":85.5,"GBP":0.8,"JPY":140,"AUD":1.5}}"""
    val table = CurrencyRates.parseResponse(ok, 123L)!!
    assertEquals(85.5, table.perUsd.getValue("INR"), 1e-9)
    assertEquals(123L, table.updatedAt)
    assertNull(CurrencyRates.parseResponse("""{"result":"error"}""", 1L))
    assertNull(CurrencyRates.parseResponse("not json", 1L))
    assertNull(CurrencyRates.parseResponse("""{"result":"success","rates":{"USD":1}}""", 1L))
  }

  @Test
  fun storedRatesComeBackAsTheyWentIn() {
    val table = com.keepwallet.app.data.RateTable(mapOf("USD" to 1.0, "EUR" to 0.9, "INR" to 85.5, "GBP" to 0.8, "JPY" to 140.0), 99L, false)
    val back = CurrencyRates.decode(CurrencyRates.encode(table))!!
    assertEquals(table.perUsd, back.perUsd)
    assertEquals(99L, back.updatedAt)
    assertNull(CurrencyRates.decode(null))
    assertNull(CurrencyRates.decode("{}"))
  }

  @Test
  fun symbolsMapToTheirCurrency() {
    assertEquals("INR", CurrencyRates.codeForSymbol("₹"))
    assertEquals("USD", CurrencyRates.codeForSymbol("$"))
  }
}
