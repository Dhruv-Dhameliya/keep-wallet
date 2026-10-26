package com.keepwallet.app

import com.keepwallet.app.data.TxType
import com.keepwallet.app.notify.NotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationParserTest {

  private fun parse(title: String?, text: String?) = NotificationParser.parse(title, text)

  @Test
  fun `plain rupee debit`() {
    val parsed = parse("HDFC Bank", "Rs 250 debited")!!
    assertEquals(25_000L, parsed.amountMinor)
    assertEquals(TxType.EXPENSE, parsed.type)
  }

  @Test
  fun `spent at a merchant in dollars`() {
    val parsed = parse("Chase Card", "Spent \$24.00 at Green Market")!!
    assertEquals(2_400L, parsed.amountMinor)
    assertEquals(TxType.EXPENSE, parsed.type)
    assertEquals("Green Market", parsed.merchant)
  }

  @Test
  fun `upi paid to merchant`() {
    val parsed = parse("Google Pay", "Paid Rs 250 to Metro transit")!!
    assertEquals(25_000L, parsed.amountMinor)
    assertEquals(TxType.EXPENSE, parsed.type)
    assertEquals("Metro transit", parsed.merchant)
  }

  @Test
  fun `credit with salary label`() {
    val parsed = parse("Direct Deposit", "\$4,800.00 credited as Salary")!!
    assertEquals(480_000L, parsed.amountMinor)
    assertEquals(TxType.INCOME, parsed.type)
    assertEquals("Salary", parsed.merchant)
  }

  @Test
  fun `received from a person`() {
    val parsed = parse("PhonePe", "You received ₹1,500.50 from Asha Rao")!!
    assertEquals(150_050L, parsed.amountMinor)
    assertEquals(TxType.INCOME, parsed.type)
    assertEquals("Asha Rao", parsed.merchant)
  }

  @Test
  fun `indian digit grouping`() {
    val parsed = parse("Bank", "INR 1,25,000.00 credited to your account")!!
    assertEquals(12_500_000L, parsed.amountMinor)
    assertEquals(TxType.INCOME, parsed.type)
  }

  @Test
  fun `available balance is not mistaken for the amount`() {
    val parsed = parse("SBI", "A/c XX1234 debited by Rs 500.00 on 12-Oct. Avl bal Rs 12,000.00")!!
    assertEquals(50_000L, parsed.amountMinor)
    assertEquals(TxType.EXPENSE, parsed.type)
  }

  @Test
  fun `balance listed before the amount is skipped`() {
    val parsed = parse("Bank", "Avl bal Rs 12000. Rs 500 debited at Corner Cafe")!!
    assertEquals(50_000L, parsed.amountMinor)
    assertEquals("Corner Cafe", parsed.merchant)
  }

  @Test
  fun `debited for a merchant`() {
    val parsed = parse("UPI Alert", "Rs 12.50 debited for Corner Café")!!
    assertEquals(1_250L, parsed.amountMinor)
    assertEquals("Corner Café", parsed.merchant)
  }

  @Test
  fun `vpa handle becomes the merchant`() {
    val parsed = parse("Bank", "Rs 320.00 debited. Paid to VPA swiggy@okaxis on 12-10-26")!!
    assertEquals(32_000L, parsed.amountMinor)
    assertEquals("swiggy", parsed.merchant)
  }

  @Test
  fun `direction follows whichever keyword comes first`() {
    val parsed = parse("Bank", "Rs 100 debited from your account and credited to Ravi")!!
    assertEquals(TxType.EXPENSE, parsed.type)
  }

  @Test
  fun `otp messages are ignored`() {
    assertNull(parse("Bank", "123456 is your OTP for payment of Rs 500. Do not share."))
  }

  @Test
  fun `failed transactions are ignored`() {
    assertNull(parse("Bank", "Payment of Rs 500 failed. Amount will not be debited."))
  }

  @Test
  fun `payment requests are ignored`() {
    assertNull(parse("Google Pay", "Ravi requested Rs 300 from you"))
  }

  @Test
  fun `promotions are ignored`() {
    assertNull(parse("Shop", "Get Rs 100 cashback offer on your next payment of Rs 999"))
  }

  @Test
  fun `bill reminders are ignored`() {
    assertNull(parse("Card", "Your bill of Rs 4,500 is due on 15 Oct. Minimum amount due Rs 450"))
  }

  @Test
  fun `text without an amount is ignored`() {
    assertNull(parse("Bank", "Your account was debited"))
  }

  @Test
  fun `text without a direction is ignored`() {
    assertNull(parse("Bank", "Rs 500 is your monthly limit"))
  }

  @Test
  fun `blank input is ignored`() {
    assertNull(parse(null, null))
    assertNull(parse("", "  "))
  }

  @Test
  fun `title is used as merchant fallback`() {
    val parsed = parse("Corner Grocer", "Rs 90 paid")!!
    assertNotNull(parsed.merchant)
    assertEquals("Corner Grocer", parsed.merchant)
  }

  @Test
  fun `no merchant when nothing usable`() {
    val parsed = parse(null, "Rs 90 debited")!!
    assertNull(parsed.merchant)
  }
}
