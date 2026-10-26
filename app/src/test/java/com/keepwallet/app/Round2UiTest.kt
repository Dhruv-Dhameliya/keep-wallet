package com.keepwallet.app

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.PaymentRuleEntity
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.notify.CaptureOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun UiTestBase.bankAndCash() = accounts().let { list -> list.first { it.kind == AccountKind.BANK } to list.first { it.kind == AccountKind.CASH } }

class TransferFlowTest : UiTestBase() {
  private fun balance(account: com.keepwallet.app.data.AccountEntity) = Analytics.accountBalance(accounts().first { it.id == account.id }, transactions())

  @Test
  fun moneyMovesBetweenAccountsFromTheBalancePage() {
    waitForSeed()
    val (bank, cash) = bankAndCash()
    runBlocking { repository.saveAccount(bank, 100_000) }
    click("balance_card")
    waitForTag("balance_page")
    click("transfer_button")
    waitForTag("transfer_amount")
    click("transfer_to")
    click("transfer_to_option_Cash")
    rule.onNodeWithTag("transfer_amount").performTextInput("250")
    rule.onNodeWithTag("transfer_note").performTextInput("ATM run")
    click("transfer_save")
    rule.waitUntil(5_000) { transactions().size == 2 }
    assertEquals(75_000L, balance(bank))
    assertEquals(25_000L, balance(cash))
    assertEquals(setOf(TxType.TRANSFER_OUT, TxType.TRANSFER_IN), transactions().map { it.type }.toSet())
    awaitEquals({ "$750.00" }, { textOf("account_balance_${bank.name}") })
  }

  @Test
  fun aTransferIsOneLineInActivityWithItsOwnDetailsAndDeletesWholly() {
    waitForSeed()
    val (bank, cash) = bankAndCash()
    runBlocking { repository.saveTransfer(bank.id, cash.id, 4_000, System.currentTimeMillis()) }
    rule.waitUntil(5_000) { transactions().size == 2 }
    val out = transactions().first { it.type == TxType.TRANSFER_OUT }
    val into = transactions().first { it.type == TxType.TRANSFER_IN }
    click("tab_activity")
    waitForTag("tx_${out.id}")
    assertFalse("the incoming half is not listed on its own", tagExists("tx_${into.id}"))
    waitForText("All (1)")
    click("tx_${out.id}")
    waitForTag("entry_details")
    waitForText("Transferred")
    waitForText("From")
    assertFalse("transfers have no Edit", tagExists("edit_entry_btn"))
    click("delete_entry_btn")
    click("confirm_delete")
    rule.waitUntil(5_000) { transactions().isEmpty() }
  }

  @Test
  fun theAccountFilterFindsATransferFromEitherSide() {
    waitForSeed()
    val (bank, cash) = bankAndCash()
    runBlocking { repository.saveTransfer(bank.id, cash.id, 4_000, System.currentTimeMillis()) }
    rule.waitUntil(5_000) { transactions().size == 2 }
    click("tab_activity")
    waitForText("All (1)")
    click("filter_account")
    click("filter_account_${cash.name}")
    waitForText("All (1)")
    click("filter_account")
    click("filter_account_Wallet")
    waitForText("All (0)")
  }
}

class SplitFlowTest : UiTestBase() {
  @Test
  fun oneBillBecomesSeveralEntriesInDifferentCategories() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    click("quick_amount_100")
    click("split_button")
    waitForTag("split_amount_0")
    rule.onNodeWithTag("split_amount_0").performTextReplacement("70")
    rule.onNodeWithTag("split_amount_1").performTextReplacement("30")
    waitForText("All of it is assigned.")
    click("split_save")
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 2 }
    val parts = transactions()
    assertEquals(setOf(7_000L, 3_000L), parts.map { it.amountMinor }.toSet())
    assertEquals(2, parts.map { it.categoryId }.toSet().size)
    assertNotNull(parts[0].splitId)
    assertEquals(parts[0].splitId, parts[1].splitId)
    // the details of one part mention the other
    click("tab_activity")
    waitForTag("tx_${parts[0].id}")
    click("tx_${parts[0].id}")
    waitForText("Split bill")
  }

  @Test
  fun theSplitCannotBeSavedUntilThePartsAddUp() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    click("quick_amount_100")
    click("split_button")
    waitForTag("split_amount_0")
    rule.onNodeWithTag("split_amount_0").performTextReplacement("80")
    waitForText("too much")
    rule.onNodeWithTag("split_amount_0").performTextReplacement("10")
    waitForText("left to assign")
    assertTrue(transactions().isEmpty())
  }

  @Test
  fun anotherPartCanBeAdded() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    click("quick_amount_100")
    click("split_button")
    waitForTag("split_add_part")
    click("split_add_part")
    waitForTag("split_amount_2")
    click("split_remove_part")
    waitForGone("split_amount_2")
  }
}

class PaymentRuleFlowTest : UiTestBase() {
  private val notifications get() = Unit

  private fun process(title: String, text: String): CaptureOutcome =
    runBlocking { container.capture.process("com.example.bank", "Example Bank", title, text) }

  @Test
  fun aRuleFilesADetectedPaymentBeforeYouSeeIt() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    val transport = categories().first { it.name == "Transport" }
    runBlocking { repository.saveRule(PaymentRuleEntity(match = "Metro", categoryId = transport.id, displayName = "Metro ride")) }
    rule.waitUntil(5_000) { runBlocking { repository.rules.first() }.isNotEmpty() }
    assertTrue(process("HDFC Bank", "Rs 250 debited at Metro transit") is CaptureOutcome.Captured)
    waitForTag("detected_add")
    click("detected_add")
    waitForTag("entry_sheet")
    waitForTag("rule_note")
    rule.onNodeWithTag("category_field").assertTextContains("Transport", substring = true)
    rule.onNodeWithTag("merchant_input").assertTextContains("Metro ride", substring = true)
  }

  @Test
  fun afterFilingByHandTheAppOffersToRememberIt() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    assertTrue(process("UPI Alert", "Rs 90 paid to Corner Cafe") is CaptureOutcome.Captured)
    waitForTag("detected_add")
    click("detected_add")
    waitForTag("entry_sheet")
    click("category_field")
    click("category_option_Dining")
    click("save_entry")
    waitForGone("entry_sheet")
    waitForTag("rule_yes")
    click("rule_yes")
    rule.waitUntil(5_000) { runBlocking { repository.rules.first() }.isNotEmpty() }
    val saved = runBlocking { repository.rules.first() }.single()
    assertTrue(saved.match.contains("Corner Cafe", ignoreCase = true))
    assertEquals(categories().first { it.name == "Dining" }.id, saved.categoryId)
  }

  @Test
  fun decliningLeavesNoRule() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    process("UPI Alert", "Rs 90 paid to Corner Cafe")
    waitForTag("detected_add")
    click("detected_add")
    waitForTag("entry_sheet")
    click("save_entry")
    waitForGone("entry_sheet")
    waitForTag("rule_no")
    click("rule_no")
    waitForGone("rule_no")
    assertTrue(runBlocking { repository.rules.first() }.isEmpty())
  }

  @Test
  fun rulesCanBeAddedAndRemovedFromTheDetectionPage() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "settings_detection")
    click("settings_detection")
    waitForTag("detection_list")
    scrollTo("detection_list", "add_rule")
    click("add_rule")
    waitForTag("rule_match")
    rule.onNodeWithTag("rule_match").performTextInput("Spotify")
    click("rule_category")
    click("rule_category_option_Subscriptions")
    click("rule_save")
    rule.waitUntil(5_000) { runBlocking { repository.rules.first() }.isNotEmpty() }
    scrollTo("detection_list", "rule_row_Spotify")
    click("rule_row_Spotify")
    waitForTag("rule_delete")
    click("rule_delete")
    rule.waitUntil(5_000) { runBlocking { repository.rules.first() }.isEmpty() }
  }
}

class RolloverFlowTest : UiTestBase() {
  @Test
  fun theSwitchInTheLimitsSheetIsSavedAndTheCarryShowsInPlan() {
    waitForSeed()
    val groceries = categories().first { it.name == "Groceries" }
    runBlocking { repository.setLimits(groceries.id, 60_000, 0) }
    // last month's unspent 600 (one tiny entry makes that month exist)
    val lastMonth = java.time.YearMonth.now().minusMonths(1).atDay(5).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    runBlocking {
      repository.saveTransaction(TransactionEntity(type = TxType.EXPENSE, amountMinor = 10_000, merchant = "Shop", categoryId = groceries.id, accountId = accounts().first().id, timestamp = lastMonth))
    }
    click("tab_plan")
    click("budget_row_Groceries")
    click("budget_edit_Groceries")
    waitForTag("limit_rollover")
    click("limit_rollover")
    click("limit_save")
    rule.waitUntil(5_000) { categories().first { it.id == groceries.id }.rollover }
    waitForTag("rollover_note_Groceries")
    waitForText("carried over")
    // $600 limit, $100 spent last month: $500 carried
    waitForText("$500")
  }
}

class DuplicateWarningTest : UiTestBase() {
  private fun addFive() {
    click("tab_add")
    waitForTag("entry_sheet")
    typeAmount("5")
    click("save_entry")
  }

  @Test
  fun theSameEntryTwiceInAMomentAsksFirst() {
    waitForSeed()
    addFive()
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    addFive()
    waitForTag("duplicate_add")
    waitForText("You added")
    click("duplicate_cancel")
    waitForGone("duplicate_add")
    assertEquals(1, transactions().size)
    click("save_entry")
    waitForTag("duplicate_add")
    click("duplicate_add")
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 2 }
  }

  @Test
  fun aDifferentAmountIsNotAWarning() {
    waitForSeed()
    addFive()
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    click("tab_add")
    waitForTag("entry_sheet")
    typeAmount("6")
    click("save_entry")
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 2 }
  }

  @Test
  fun editingNeverWarns() {
    waitForSeed()
    addFive()
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    val id = transactions().single().id
    click("tab_activity")
    waitForTag("tx_$id")
    click("tx_$id")
    click("edit_entry_btn")
    waitForTag("entry_sheet")
    click("save_entry")
    waitForGone("entry_sheet")
    assertFalse(tagExists("duplicate_add"))
    assertEquals(1, transactions().size)
  }
}

class LayoutFlowTest : UiTestBase() {
  private fun enabled() = widgets().filter { it.enabled }.map { it.kind }.toSet()

  @Test
  fun aReadyMadeLayoutRearrangesHome() {
    waitForSeed()
    click("customize_home")
    waitForTag("layout_minimal")
    click("layout_minimal")
    rule.waitUntil(5_000) { enabled() == setOf(WidgetKind.BALANCE_CARD, WidgetKind.SPENDING_CHART, WidgetKind.RECENT_ACTIVITY) }
    click("layout_bills_focus")
    rule.waitUntil(5_000) { WidgetKind.UPCOMING_BILLS in enabled() }
    assertFalse(WidgetKind.SPENDING_CHART in enabled())
    click("back")
    waitForTag("home_list")
    scrollHomeToTop()
    waitForTag("widget_upcoming")
  }

  @Test
  fun yourOwnLayoutCanBeSavedAppliedAndDeleted() {
    waitForSeed()
    click("customize_home")
    waitForTag("save_layout")
    click("layout_minimal")
    rule.waitUntil(5_000) { enabled().size == 3 }
    click("save_layout")
    waitForTag("layout_name")
    rule.onNodeWithTag("layout_name").performTextInput("Quiet")
    click("layout_save_confirm")
    waitForTag("saved_layout_Quiet")
    click("layout_detailed")
    rule.waitUntil(5_000) { enabled().size > 3 }
    click("apply_layout_Quiet")
    rule.waitUntil(5_000) { enabled().size == 3 }
    click("delete_layout_Quiet")
    waitForGone("saved_layout_Quiet")
  }
}

class AppLockFlowTest : UiTestBase() {
  private fun enterPin(pin: String) = pin.forEach { click("lock_key_$it") }

  private fun turnOnLock(openSettings: Boolean = true) {
    if (openSettings) click("open_settings")
    scrollTo("settings_list", "lock_switch")
    click("lock_switch")
    waitForTag("pin_new")
    rule.onNodeWithTag("pin_new").performTextInput("1234")
    rule.onNodeWithTag("pin_repeat").performTextInput("1234")
    click("pin_save")
    rule.waitUntil(5_000) { container.settings.lockEnabled.value }
  }

  @Test
  fun aPinTurnsTheLockOnAndTheLockScreenNeedsIt() {
    waitForSeed()
    turnOnLock()
    // the PIN is stored hashed, never as typed
    val prefs = rule.activity.getSharedPreferences("keep_wallet_settings", android.content.Context.MODE_PRIVATE)
    assertTrue(prefs.all.values.none { it == "1234" || it == 1234 })
    assertTrue(prefs.contains("pin_hash") && prefs.contains("pin_salt"))
    assertFalse(tagExists("lock_screen"))

    scrollTo("settings_list", "lock_now")
    click("lock_now")
    waitForTag("lock_screen")
    enterPin("9999")
    waitForTag("lock_error")
    waitForText("Wrong PIN")
    assertTrue(tagExists("lock_screen"))
    enterPin("1234")
    waitForGone("lock_screen")
  }

  @Test
  fun mismatchedPinsCannotBeSaved() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "lock_switch")
    click("lock_switch")
    waitForTag("pin_new")
    rule.onNodeWithTag("pin_new").performTextInput("1234")
    rule.onNodeWithTag("pin_repeat").performTextInput("1235")
    waitForTag("pin_mismatch")
    assertFalse(container.settings.lockEnabled.value)
  }

  @Test
  fun turningTheLockOffAsksForThePin() {
    waitForSeed()
    turnOnLock()
    scrollTo("settings_list", "lock_switch")
    click("lock_switch")
    waitForTag("pin_current")
    rule.onNodeWithTag("pin_current").performTextInput("0000")
    click("pin_confirm")
    waitForTag("pin_wrong")
    assertTrue(container.settings.lockEnabled.value)
    rule.onNodeWithTag("pin_current").performTextReplacement("1234")
    click("pin_confirm")
    rule.waitUntil(5_000) { !container.settings.lockEnabled.value }
  }

  @Test
  fun thePinCanBeChangedAndTheTimeoutChosen() {
    waitForSeed()
    turnOnLock()
    scrollTo("settings_list", "change_pin")
    click("change_pin")
    waitForTag("pin_current")
    rule.onNodeWithTag("pin_current").performTextInput("1234")
    rule.onNodeWithTag("pin_new").performTextInput("5678")
    rule.onNodeWithTag("pin_repeat").performTextInput("5678")
    click("pin_save")
    rule.waitUntil(5_000) { container.settings.verifyPin("5678") }
    assertFalse(container.settings.verifyPin("1234"))
    scrollTo("settings_list", "lock_timeout_300")
    click("lock_timeout_300")
    rule.waitUntil(5_000) { container.settings.lockTimeout.value == 300 }
    click("lock_timeout_0")
    rule.waitUntil(5_000) { container.settings.lockTimeout.value == 0 }
  }

  @Test
  fun settingsHidesTheLockOptionsUntilTheLockIsOn() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "lock_switch")
    assertFalse(tagExists("lock_now"))
    assertFalse(tagExists("change_pin"))
    assertFalse(tagExists("hide_recents_switch"))
    turnOnLock(openSettings = false)
    scrollTo("settings_list", "hide_recents_switch")
    assertTrue(tagExists("lock_now"))
    assertTrue(tagExists("change_pin"))
  }
}
