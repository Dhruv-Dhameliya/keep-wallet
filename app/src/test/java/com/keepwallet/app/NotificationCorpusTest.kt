package com.keepwallet.app

import com.keepwallet.app.data.TxType
import com.keepwallet.app.notify.NotificationParser
import com.keepwallet.app.notify.ParseResult
import com.keepwallet.app.notify.SkipReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wide set of realistic bank, card, UPI and wallet messages: what is read, which way the money went, how much, and
 * who it was with. Written the way these messages actually arrive (SMS sender ids, shouting capitals, references).
 */
class NotificationCorpusTest {
  private fun found(title: String?, text: String): com.keepwallet.app.notify.ParsedTransaction {
    val result = NotificationParser.analyse(title, text)
    assertTrue("expected a transaction in: $text but got $result", result is ParseResult.Found)
    return (result as ParseResult.Found).transaction
  }

  private fun skipped(title: String?, text: String): SkipReason {
    val result = NotificationParser.analyse(title, text)
    assertTrue("expected a skip for: $text but got $result", result is ParseResult.Skipped)
    return (result as ParseResult.Skipped).reason
  }

  private fun expect(title: String?, text: String, minor: Long, type: TxType, merchant: String? = null) {
    val t = found(title, text)
    assertEquals("amount in: $text", minor, t.amountMinor)
    assertEquals("direction in: $text", type, t.type)
    if (merchant != null) assertEquals("merchant in: $text", merchant, t.merchant)
  }

  // ---- Indian bank SMS ---------------------------------------------------------------------------

  @Test
  fun `debit with a vpa and a trailing balance`() =
    expect("AD-HDFCBK", "Rs 1,234.56 debited from A/c XX1234 on 12-10-26 to VPA swiggy@icici (UPI Ref No 123456789012). Avl Bal Rs 12,345.00", 123_456, TxType.EXPENSE, "Swiggy")

  @Test
  fun `salary by neft is money in`() =
    expect("VM-SBIINB", "Dear Customer, INR 50,000.00 is credited to your A/c XX9876 on 01-10-2026 by NEFT-ACME CORP-SALARY. Avl bal INR 90,000.00", 5_000_000, TxType.INCOME)

  @Test
  fun `sent to a person with a call-us footer`() =
    expect("HDFC Bank", "Sent Rs.250.00 From HDFC Bank A/C *1234 To MR RAVI KUMAR On 05/10/26 Ref 123456789 Not You? Call 18002586161", 25_000, TxType.EXPENSE, "Mr Ravi Kumar")

  @Test
  fun `upi txn at a merchant in capitals`() =
    expect("ICICI Bank", "UPI txn of Rs 499.00 at AMAZON PAY INDIA on 10-Oct. UPI Ref 1234567890", 49_900, TxType.EXPENSE, "Amazon Pay India")

  @Test
  fun `credit card used for a purchase`() =
    expect("SBI Card", "Your SBI Credit Card ending 4321 was used for Rs 2,500.00 at BIG BAZAAR on 11/10/26.", 250_000, TxType.EXPENSE, "Big Bazaar")

  @Test
  fun `thank you for using your card`() =
    expect("HDFC Bank", "Thank you for using your HDFC Bank Credit Card ending 1234 for Rs 1,999.00 at NETFLIX on 03-Oct-26", 199_900, TxType.EXPENSE, "Netflix")

  @Test
  fun `the word credit in a card name is not money in`() =
    expect("Axis Bank", "Spent Rs 500 on your Axis Credit Card ending 8899 at STARBUCKS", 50_000, TxType.EXPENSE, "Starbucks")

  @Test
  fun `a debit card purchase is money out`() =
    expect("Kotak", "Rs 1,200.00 spent on your Kotak Debit Card XX5566 at DECATHLON", 120_000, TxType.EXPENSE, "Decathlon")

  @Test
  fun `credited by upi from a person with brackets`() =
    expect("SBI", "Rs.5000.00 credited to your a/c no. XX1234 on 14/10/26 (UPI Ref no 123456789012 from PRIYA SHARMA)", 500_000, TxType.INCOME, "Priya Sharma")

  @Test
  fun `transfer to swiggy with a reference number`() =
    expect("Bank", "A/c XX1234 debited Rs.350.00 on 10-10-2026 trf to Swiggy Refno 123456", 35_000, TxType.EXPENSE, "Swiggy")

  @Test
  fun `atm withdrawal`() =
    expect("BOB", "Withdrawn Rs 2,000 from ATM XX1234 on 12-10-26. Avl Bal Rs 8,000", 200_000, TxType.EXPENSE, "ATM withdrawal")

  @Test
  fun `upi dr reference style`() =
    expect("Bank", "Dr Rs 450.00 UPI/DR/123456789012/Cafe Coffee Day/HDFC", 45_000, TxType.EXPENSE, "Cafe Coffee Day")

  @Test
  fun `upi cr reference style`() =
    expect("Bank", "Cr Rs 300.00 UPI/CR/123456789012/Rohan Mehta/ICIC", 30_000, TxType.INCOME, "Rohan Mehta")

  @Test
  fun `ach debit for insurance`() =
    expect("Bank", "INR 5,000.00 debited. Info: ACH D- LIC OF INDIA-12345678", 500_000, TxType.EXPENSE, "Lic Of India")

  @Test
  fun `hinglish debit`() =
    expect("Bank", "Rs 200 aapke account se debit hua", 20_000, TxType.EXPENSE)

  @Test
  fun `no space between currency and number`() =
    expect("Bank", "Rs500 debited from your account", 50_000, TxType.EXPENSE)

  @Test
  fun `slash dash amounts and indian grouping`() {
    expect("Bank", "Rs.1,00,000/- credited to your account", 10_000_000, TxType.INCOME)
    expect("Bank", "₹1,25,000.50 credited to A/c XX1111", 12_500_050, TxType.INCOME)
  }

  @Test
  fun `amount written as amt`() =
    expect("Bank", "Txn Amt: 750.00 debited from your card ending 1122 at ZOMATO", 75_000, TxType.EXPENSE, "Zomato")

  @Test
  fun `the balance figure is never the amount`() {
    expect("Bank", "Avl Bal Rs 99,999.00. Rs 100 debited from your account", 10_000, TxType.EXPENSE)
    expect("Bank", "Rs 100 debited. Bal Rs 200", 10_000, TxType.EXPENSE)
  }

  // ---- UPI and wallet apps ---------------------------------------------------------------------------

  @Test
  fun `you paid someone`() =
    expect("Google Pay", "You paid ₹250 to Zomato", 25_000, TxType.EXPENSE, "Zomato")

  @Test
  fun `paid with a merchant in capitals`() =
    expect("PhonePe", "₹250 paid to ZOMATO LTD using UPI", 25_000, TxType.EXPENSE, "Zomato Ltd")

  @Test
  fun `someone paid you`() =
    expect("Google Pay", "Rahul Verma paid you ₹500", 50_000, TxType.INCOME, "Rahul Verma")

  @Test
  fun `someone sent you money`() =
    expect("Paytm", "Anita Roy sent you Rs 1,200", 120_000, TxType.INCOME, "Anita Roy")

  @Test
  fun `money received from`() =
    expect("PhonePe", "Money received! ₹500 from Rahul via PhonePe", 50_000, TxType.INCOME, "Rahul")

  @Test
  fun `you sent is money out`() =
    expect("Paytm", "You sent Rs 75 to Chai Point", 7_500, TxType.EXPENSE, "Chai Point")

  // ---- Cards and banks elsewhere ----------------------------------------------------------------------

  @Test
  fun `us card purchase`() =
    expect("Chase", "You made a \$45.23 purchase at TRADER JOE'S on card ending 1234", 4_523, TxType.EXPENSE, "Trader Joe's")

  @Test
  fun `a charge was made`() =
    expect("Amex", "A charge of \$12.99 was made at SPOTIFY", 1_299, TxType.EXPENSE, "Spotify")

  @Test
  fun `venmo style payment received`() =
    expect("Venmo", "Alex Chen paid you \$20.00 - Dinner", 2_000, TxType.INCOME, "Alex Chen")

  @Test
  fun `zelle received`() =
    expect("Zelle", "You received \$200.00 from John Smith", 20_000, TxType.INCOME, "John Smith")

  @Test
  fun `other currencies`() {
    expect("Revolut", "USD 12.50 spent at Uber", 1_250, TxType.EXPENSE, "Uber")
    expect("Bank", "€45,50 debited", 4_550, TxType.EXPENSE)
  }

  @Test
  fun `refund processed is money in`() =
    expect("Amazon", "Refund of ₹499 has been processed to your account", 49_900, TxType.INCOME)

  // ---- What must be skipped, and why -----------------------------------------------------------------

  @Test
  fun `codes are skipped`() {
    assertEquals(SkipReason.SECURITY, skipped("Bank", "123456 is your OTP for transaction of Rs 500 at Amazon. Do not share."))
    assertEquals(SkipReason.SECURITY, skipped("Bank", "Your verification code is 482913"))
  }

  @Test
  fun `failures are skipped`() {
    assertEquals(SkipReason.FAILED, skipped("PhonePe", "Your payment of Rs 500 to Zomato failed"))
    assertEquals(SkipReason.FAILED, skipped("Bank", "Transaction of Rs 1,000 declined due to insufficient funds"))
  }

  @Test
  fun `reminders and requests are skipped`() {
    assertEquals(SkipReason.UPCOMING, skipped("Card", "Your credit card bill of Rs 12,000 is due on 20 Oct"))
    assertEquals(SkipReason.UPCOMING, skipped("GPay", "Rahul requested ₹500 from you"))
    assertEquals(SkipReason.UPCOMING, skipped("Bank", "Rs 799 will be debited on 15-Oct for your Netflix subscription"))
    assertEquals(SkipReason.UPCOMING, skipped("Amazon", "Refund of ₹499 initiated for your order"))
  }

  @Test
  fun `offers are skipped`() {
    assertEquals(SkipReason.PROMO, skipped("Bank", "Get flat 10% cashback up to Rs 100 on your next payment. Apply now"))
    assertEquals(SkipReason.PROMO, skipped("Shop", "Big sale! Use coupon SAVE50 to get Rs 50 off"))
  }

  @Test
  fun `a balance alone is not a transaction`() =
    assertEquals(SkipReason.BALANCE_ONLY, skipped("Bank", "Your A/c XX1234 balance is Rs 12,345.00"))

  @Test
  fun `no money and no direction`() {
    assertEquals(SkipReason.NO_AMOUNT, skipped("Messages", "Dinner tonight? Let me know"))
    assertEquals(SkipReason.NO_DIRECTION, skipped("Bank", "Rs 500 is your new daily limit"))
    assertEquals(SkipReason.EMPTY, skipped(null, ""))
  }

  // ---- Merchant fallbacks ------------------------------------------------------------------------------

  @Test
  fun `a sender id is not used as the merchant but a real title is`() {
    assertNull(found("AD-HDFCBK", "Rs 250 debited").merchant)
    assertNull(found("UPI Alert", "Rs 250 debited").merchant)
    assertEquals("Metro Card", found("Metro Card", "Rs 40 debited").merchant)
  }

  @Test
  fun `a plain debit keeps working`() {
    val t = found("HDFC Bank", "Rs 250 debited")
    assertEquals(25_000L, t.amountMinor)
    assertNotNull(t)
  }
}
