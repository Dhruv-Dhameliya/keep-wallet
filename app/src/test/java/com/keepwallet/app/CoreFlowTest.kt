package com.keepwallet.app

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The core offline flow against the real app: set a balance, add an expense and an income, look at them
 * as a bill, edit, delete and undo, and watch every balance follow.
 */
class CoreFlowTest : UiTestBase() {

  private fun bankBalance() = Analytics.accountBalance(accounts().first { it.kind == AccountKind.BANK }, transactions())

  private fun setBankBalance(text: String) {
    click("balance_card")
    waitForTag("balance_page")
    click("account_row_Bank account"); click("account_edit_Bank account")
    waitForTag("account_balance_input")
    rule.onNodeWithTag("account_balance_input").performTextInput(text)
    click("account_save")
    click("back")
    waitForTag("home_list")
  }

  @Test
  fun freshInstallShowsEmptyStatesEverywhere() {
    waitForSeed()
    waitForText("Tap to set your balance")
    awaitEquals({ "$0.00" }, { heroText() })
    waitForText("No transactions yet")
    waitForText("No pockets yet")
    // the month selector no longer lives on Home
    assertFalse(tagExists("month_navigator"))

    click("tab_activity")
    waitForText("No transactions in")
    click("tab_insights")
    waitForText("Nothing spent this month yet.")
    click("tab_plan")
    waitForText("no limits set yet")
    click("tab_home")
    waitForTag("home_list")
  }

  @Test
  fun balanceFollowsEntriesFromAnOpeningAmount() {
    waitForSeed()
    setBankBalance("1000")
    rule.waitUntil(5_000) { bankBalance() == 100_000L }
    awaitEquals({ "$1,000.00" }, { heroText() })

    // expense 24
    click("tab_add")
    waitForTag("entry_sheet")
    typeAmount("24")
    amountNode().assertTextEquals("24")
    click("save_entry")
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    assertEquals(2_400L, transactions().single().amountMinor)
    awaitEquals({ "$976.00" }, { heroText() })

    // income 5000
    click("tab_add")
    waitForTag("entry_sheet")
    click("segment_Income")
    typeAmount("5000")
    click("save_entry")
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 2 }
    assertEquals(597_600L, bankBalance())
    awaitEquals({ "$5,976.00" }, { heroText() })
  }

  @Test
  fun tappingAnEntryShowsABillAndEditUpdatesIt() {
    waitForSeed()
    click("tab_add"); waitForTag("entry_sheet")
    typeAmount("24")
    rule.onNodeWithTag("merchant_input").performTextInput("Green Market")
    click("save_entry"); waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    val id = transactions().single().id

    click("tab_activity")
    click("tx_$id")
    waitForTag("entry_details")
    awaitEquals({ "Green Market" }, { textOf("details_name") })
    awaitEquals({ "-$24.00" }, { textOf("details_amount") })

    // Edit opens the same form, filled in, with an Update button.
    click("edit_entry_btn")
    waitForTag("entry_sheet")
    amountNode().assertTextEquals("24")
    rule.onNodeWithTag("merchant_input").assertTextContains("Green Market", substring = true)
    rule.onNodeWithTag("save_entry").assertTextContains("Update expense", substring = true)
    click("key_DEL"); click("key_DEL")
    typeAmount("30")
    click("save_entry")
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().single().amountMinor == 3_000L }
    // back on the bill, already showing the new amount
    awaitEquals({ "-$30.00" }, { textOf("details_amount") })
  }

  @Test
  fun deleteFromTheBillCanBeUndone() {
    waitForSeed()
    click("tab_add"); waitForTag("entry_sheet")
    typeAmount("40")
    click("save_entry"); waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    val id = transactions().single().id
    val before = bankBalance()

    click("tab_activity")
    click("tx_$id")
    waitForTag("entry_details")
    click("delete_entry_btn")
    click("confirm_delete")
    waitForGone("entry_details")
    rule.waitUntil(5_000) { transactions().isEmpty() }
    assertEquals(before + 4_000, bankBalance())
    clickText("Undo")
    rule.waitUntil(5_000) { transactions().size == 1 }
    assertEquals(before, bankBalance())
  }

  @Test
  fun entriesCanBookToAWalletAndCash() {
    waitForSeed()
    click("tab_add"); waitForTag("entry_sheet")
    typeAmount("15")
    click("account_field")
    click("account_option_Wallet")
    click("save_entry"); waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    val wallet = accounts().first { it.kind == AccountKind.WALLET }
    assertEquals(wallet.id, transactions().single().accountId)
    assertEquals(-1_500L, Analytics.accountBalance(wallet, transactions()))
    // the bank balance is untouched
    assertEquals(0L, bankBalance())
  }

  @Test
  fun categoriesAreChosenFromAGridWithTheRequestedNames() {
    waitForSeed()
    click("tab_add"); waitForTag("entry_sheet")
    click("category_field")
    waitForTag("category_grid")
    val expense = listOf("Housing", "Groceries", "Transport", "Health", "Dining", "Subscriptions", "Shopping", "Person", "Money transfer", "Others")
    expense.forEach { name -> assertTrue("missing $name", tagExists("category_option_$name")) }
    assertEquals(expense, categories().filter { it.type == TxType.EXPENSE }.map { it.name })
    click("category_option_Person")
    waitForGone("category_grid")
    waitForText("Person")

    click("segment_Income")
    click("category_field")
    waitForTag("category_grid")
    listOf("Salary", "Cashbacks", "Other income").forEach { assertTrue("missing $it", tagExists("category_option_$it")) }
    assertEquals(listOf("Salary", "Cashbacks", "Other income"), categories().filter { it.type == TxType.INCOME }.map { it.name })
  }

  @Test
  fun enterOnTheNameFieldMovesToTheNote() {
    waitForSeed()
    click("tab_add"); waitForTag("entry_sheet")
    rule.onNodeWithTag("merchant_input").performTextInput("Lunch")
    rule.onNodeWithTag("merchant_input").performImeAction()
    rule.onNodeWithTag("note_input").assertIsFocused()
  }

  @Test
  fun cannotSaveWithoutAnAmount() {
    waitForSeed()
    click("tab_add"); waitForTag("entry_sheet")
    rule.onNodeWithTag("save_entry").assertIsNotEnabledCompat()
    typeAmount("1")
    rule.onNodeWithTag("save_entry").assertIsEnabledCompat()
    click("key_DEL")
    rule.onNodeWithTag("save_entry").assertIsNotEnabledCompat()
    assertTrue(transactions().isEmpty())
  }

  @Test
  fun closingTheFormDiscardsWhatWasTyped() {
    waitForSeed()
    click("tab_add"); waitForTag("entry_sheet")
    typeAmount("99")
    click("cancel_entry")
    waitForGone("entry_sheet")
    assertTrue(transactions().isEmpty())
  }
}
