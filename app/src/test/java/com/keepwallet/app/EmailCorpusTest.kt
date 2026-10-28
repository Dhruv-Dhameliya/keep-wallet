package com.keepwallet.app

import com.keepwallet.app.data.TxType
import com.keepwallet.app.notify.NotificationParser
import com.keepwallet.app.notify.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bank transfers that come by email rather than text. A mail app's notification carries the sender as the title, the
 * subject as the text and the start of the body as the long text, so each of those is tried on its own.
 */
class EmailCorpusTest {
  private fun read(title: String?, text: String): ParseResult = NotificationParser.analyse(title, text)

  private fun expect(title: String?, text: String, minor: Long, type: TxType) {
    val result = read(title, text)
    assertTrue("expected a transaction in: $text but got $result", result is ParseResult.Found)
    val t = (result as ParseResult.Found).transaction
    assertEquals("amount in: $text", minor, t.amountMinor)
    assertEquals("direction in: $text", type, t.type)
  }

  private fun ignored(title: String?, text: String) {
    val result = read(title, text)
    assertTrue("expected nothing from: $text but got $result", result is ParseResult.Skipped)
  }

  @Test fun `neft debit mail`() = expect("HDFC Bank InstaAlerts", "Rs. 15,000.00 has been debited from your account via NEFT", 1_500_000, TxType.EXPENSE)
  @Test fun `imps transfer mail`() = expect("ICICI Bank", "Your IMPS transfer of INR 25,000.00 to Rahul Sharma was successful", 2_500_000, TxType.EXPENSE)
  @Test fun `credit alert mail`() = expect("Axis Bank", "Credit Alert: INR 1,20,000.00 credited to your account XX1234", 12_000_000, TxType.INCOME)
  @Test fun `rtgs credit mail`() = expect("SBI", "Dear Customer, a sum of Rs. 5,000.00 is credited to your account on 12-10-2026 through RTGS", 500_000, TxType.INCOME)
  @Test fun `neft credit from a company`() = expect("Kotak", "NEFT transfer of Rs 80,000 has been credited to A/c XXXX5678 from ACME LTD", 8_000_000, TxType.INCOME)
  @Test fun `nach mandate debit`() = expect("Bank", "Your account ending 4321 has been debited Rs 5,000 for NACH mandate", 500_000, TxType.EXPENSE)
  @Test fun `wire received`() = expect("Chase", "Wire transfer of $2,450.00 received in your account ending 1234", 245_000, TxType.INCOME)
  @Test fun `card purchase alert`() = expect("Chase", "Transaction alert: A purchase of $54.20 was made at Whole Foods with your card ending 1234", 5_420, TxType.EXPENSE)
  @Test fun `paypal sent`() = expect("PayPal", "You sent a payment of $45.00 USD to Etsy Seller", 4_500, TxType.EXPENSE)
  @Test fun `payment received mail`() = expect("PayPal", "Payment received: $300.00 USD from Client Co", 30_000, TxType.INCOME)
  @Test fun `subscription processed`() = expect("Netflix", "Your payment of $19.99 to Netflix was processed", 1_999, TxType.EXPENSE)
  @Test fun `zelle sent`() = expect("Bank of America", "You sent $120.00 to Alex via Zelle", 12_000, TxType.EXPENSE)

  @Test fun `statement ready is not a payment`() = ignored("HDFC Bank", "Your statement for card ending 1234 is ready. Total amount due Rs 12,000")
  @Test fun `offer mail is not a payment`() = ignored("Amazon", "Get 20% off and up to ₹500 cashback on your next order")
  @Test fun `otp mail is not a payment`() = ignored("Bank", "Your one-time password for the transfer of Rs 5,000 is 482913")
  @Test fun `newsletter is not a payment`() = ignored("Morning Brew", "Markets rally as the dollar slips 2% - your Tuesday briefing")
  @Test fun `failed transfer mail is not a payment`() = ignored("Bank", "Your NEFT transfer of Rs 9,000 could not be processed")
}

class EmailMerchantTest {
  @Test fun `a date after the channel is not a name`() {
    val t = NotificationParser.parse("HDFC Bank InstaAlerts", "Rs. 15,000.00 has been debited from your account XX1234 via NEFT on 12-10-2026.")!!
    assertEquals("HDFC Bank InstaAlerts", t.merchant)
  }
}
