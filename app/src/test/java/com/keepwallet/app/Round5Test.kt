package com.keepwallet.app

import android.content.Context
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.CategoryGroup
import com.keepwallet.app.data.DefaultData
import com.keepwallet.app.data.KeepWalletDatabase
import com.keepwallet.app.data.KeepWalletRepository
import com.keepwallet.app.data.ReceiptStore
import com.keepwallet.app.data.SettingsStore
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Health category reaches installs made before it existed, exactly once and without touching anything else. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestKeepWalletApp::class, sdk = [35])
class HealthCategoryTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private fun settings(): SettingsStore {
    val app = ApplicationProvider.getApplicationContext<TestKeepWalletApp>()
    val deadline = System.currentTimeMillis() + 10_000
    while (System.currentTimeMillis() < deadline && !(app.container.settings.isSeeded && app.container.settings.defaultsVersion >= DefaultData.VERSION)) Thread.sleep(25)
    Thread.sleep(150)
    context.getSharedPreferences("keep_wallet_settings", Context.MODE_PRIVATE).edit().clear().commit()
    return SettingsStore(context)
  }

  @Test
  fun aNewInstallHasHealthAmongTheEssentials() = runBlocking {
    val db = KeepWalletDatabase.inMemory(context)
    val repo = KeepWalletRepository(db, settings(), ReceiptStore(context))
    repo.ensureSeeded()
    val health = db.categoryDao().getAll().single { it.name == DefaultData.HEALTH }
    assertEquals(TxType.EXPENSE, health.type)
    assertEquals(CategoryGroup.ESSENTIAL, health.group)
    assertEquals("health", health.iconKey)
  }

  @Test
  fun anInstallFromBeforeGetsHealthOnceAndKeepsItsOwnCategories() = runBlocking {
    val db = KeepWalletDatabase.inMemory(context)
    val store = settings()
    val repo = KeepWalletRepository(db, store, ReceiptStore(context))
    repo.ensureSeeded()
    val dao = db.categoryDao()
    // Make it look like the version before: no Health, and a category of the user's own.
    dao.delete(dao.getAll().single { it.name == DefaultData.HEALTH })
    dao.insert(CategoryEntity(name = "Pets", iconKey = "pets", color = 0xFF123456.toInt(), type = TxType.EXPENSE, group = CategoryGroup.FLEXIBLE, sortOrder = 50))
    store.defaultsVersion = 3

    repo.ensureSeeded()
    repo.ensureSeeded()
    val names = dao.getAll().map { it.name }
    assertEquals(1, names.count { it == DefaultData.HEALTH })
    assertTrue("Pets" in names)
    assertEquals(DefaultData.VERSION, store.defaultsVersion)
  }

  @Test
  fun aHealthCategoryTheUserAlreadyMadeIsNotDuplicated() = runBlocking {
    val db = KeepWalletDatabase.inMemory(context)
    val store = settings()
    val repo = KeepWalletRepository(db, store, ReceiptStore(context))
    repo.ensureSeeded()
    store.defaultsVersion = 3
    repo.ensureSeeded()
    assertEquals(1, db.categoryDao().getAll().count { it.name.equals("health", ignoreCase = true) })
  }
}

/** The home card, the balance page that chooses what it adds up, and the bell. */
class HeroSelectionUiTest : UiTestBase() {
  private fun fmt(minor: Long) = Money.format(minor, container.settings.currencySymbol.value)

  private fun balanceOf(kinds: Set<AccountKind>) =
    accounts().filter { it.kind in kinds }.sumOf { Analytics.accountBalance(it, transactions()) }

  @Test
  fun theCardNoLongerRepeatsTheAccountNameOrShowsAnArrow() {
    waitForSeed()
    loadSampleData()
    waitForTag("balance_card")
    assertFalse(textExists("All accounts"))
    assertFalse(textExists("Bank account"))
    assertFalse(tagExists("hero_chevron"))
    // The whole card is still the way into the balance page.
    click("balance_card")
    waitForTag("balance_page")
  }

  @Test
  fun theBalancePageChoosesWhichAccountsTheCardAddsUp() {
    waitForSeed()
    loadSampleData()
    val wallet = accounts().first { it.kind == AccountKind.WALLET }
    val bank = balanceOf(setOf(AccountKind.BANK))
    val both = balanceOf(setOf(AccountKind.BANK, AccountKind.WALLET))
    assertTrue(bank != both)

    click("balance_card")
    waitForTag("hero_choice")
    click("hero_account_${wallet.name}")
    rule.waitUntil(5_000) { container.settings.heroAccounts.value?.contains(wallet.id) == true }
    click("back")
    waitForText("SELECTED BALANCE")
    awaitEquals({ fmt(both) }, { heroText() })

    // The presets put it back to the simple choices.
    click("balance_card")
    click("hero_preset_all")
    rule.waitUntil(5_000) { container.settings.heroAccounts.value == null && container.settings.showTotalOnHero.value }
    click("hero_preset_banks")
    rule.waitUntil(5_000) { !container.settings.showTotalOnHero.value }
    assertNull(container.settings.heroAccounts.value)
    click("back")
    waitForText("BANK BALANCE")
    awaitEquals({ fmt(bank) }, { heroText() })
  }

  @Test
  fun theBellCountClearsOnceThePageHasBeenOpened() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    runBlocking { container.capture.process("com.example.bank", "Example Bank", "Debit alert", "Rs 120.00 debited from your account at Corner Cafe") }
    waitForTag("alerts_badge")
    click("open_alerts")
    waitForTag("alert_PAYMENTS_WAITING")
    dismissBanner()
    click("back")
    waitForTag("home_list")
    waitForGone("alerts_badge")
    // Opened again, the row is still there, no longer new.
    click("open_alerts")
    waitForTag("alert_PAYMENTS_WAITING")
    assertFalse(tagExists("alert_new_PAYMENTS_WAITING"))
  }

  @Test
  fun aNewPaymentBringsTheCountBackAfterItWasCleared() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    runBlocking { container.capture.process("com.example.bank", "Example Bank", "Debit alert", "Rs 120.00 debited from your account at Corner Cafe") }
    waitForTag("alerts_badge")
    click("open_alerts"); waitForTag("alert_PAYMENTS_WAITING"); dismissBanner(); click("back")
    waitForGone("alerts_badge")
    runBlocking { container.capture.process("com.example.bank", "Example Bank", "Credit alert", "Rs 900.00 credited to your account by Acme Payroll") }
    waitForTag("alerts_badge")
  }
}

/** The new Add page: details first, then the category, then the amount and keypad, all on one screen. */
class AddScreenFitTest : UiTestBase() {
  @Test
  @Config(qualifiers = "w320dp-h640dp-hdpi")
  fun everythingFitsOnASmallScreenWithoutScrolling() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    val sheet = rule.onNodeWithTag("entry_sheet").fetchSemanticsNode().boundsInRoot
    listOf("merchant_input", "category_field", "key_1", "key_0", "save_entry").forEach { tag ->
      waitForTag(tag)
      val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
      assertTrue("$tag should start inside the page: $bounds vs $sheet", bounds.top >= sheet.top - 1f)
      assertTrue("$tag should end inside the page: $bounds vs $sheet", bounds.bottom <= sheet.bottom + 1f)
    }
  }

  @Test
  fun detailsComeFirstThenTheCategoryThenTheKeypad() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    fun top(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
    assertTrue(top("merchant_input") < top("category_field"))
    assertTrue(top("category_field") < top("key_5"))
    // The steps bar and the foreign-currency part are gone.
    assertFalse(tagExists("step_details"))
    assertFalse(tagExists("details_toggle"))
    assertFalse(tagExists("foreign_toggle"))
    // Receipt is plain text, not a button-like row.
    assertTrue(tagExists("add_receipt"))
  }

  @Test
  fun healthIsOneOfTheCategoriesToChooseFrom() {
    waitForSeed()
    click("tab_add")
    click("category_field")
    waitForTag("category_grid")
    assertTrue(tagExists("category_option_Health"))
  }
}

/** The floating "payment detected" card sits over a page's back arrow; a person swipes it away first. */
fun UiTestBase.dismissBanner() {
  val viewModel = androidx.lifecycle.ViewModelProvider(rule.activity)[com.keepwallet.app.ui.MainViewModel::class.java]
  rule.runOnUiThread { viewModel.dismissBanner() }
  rule.waitForIdle()
}

/** The capture pipeline end to end, with no network involved anywhere. */
class CapturePipelineBehaviourTest : UiTestBase() {
  private fun run(title: String?, text: String?, more: List<String> = emptyList(), pkg: String = "com.example.bank") =
    runBlocking { container.capture.process(pkg, "Example Bank", title, text, more) }

  @Test
  fun aMessageOnlyHeldInAnotherTextFieldIsStillRead() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    // An SMS app: the main text is a count, the message is in the conversation lines.
    val outcome = run("Example Bank", "1 new message", listOf("Rs 640.00 debited from A/c XX1234 at Fresh Mart on 12-Oct"))
    assertTrue(outcome is com.keepwallet.app.notify.CaptureOutcome.Captured)
    assertEquals(64_000L, detected().single().amountMinor)
    assertEquals(TxType.EXPENSE, detected().single().type)
  }

  @Test
  fun creditedAndDebitedAreRecognisedAsTheirOwnKind() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    run("Bank", "INR 1,500.00 credited to your account XX9876 by Acme Payroll")
    run("Bank", "INR 220.00 debited from your account XX9876 for Corner Cafe")
    val kinds = detected().associate { it.amountMinor to it.type }
    assertEquals(TxType.INCOME, kinds[150_000L])
    assertEquals(TxType.EXPENSE, kinds[22_000L])
  }

  @Test
  fun whatWasDoneWithEachNotificationIsLoggedWithoutTheMessage() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    run("Bank", "Rs 90 debited at Corner Cafe")
    run("Bank", "Your payment of Rs 500 failed")
    val log = container.settings.detectionLog.value
    assertEquals(2, log.size)
    assertTrue(log.any { it.captured && it.outcome.startsWith("Read money out") })
    assertTrue(log.any { !it.captured && it.outcome.startsWith("Skipped") })
    // Only the app, the time and the outcome are kept: nothing of the message text.
    log.forEach { entry ->
      assertFalse(entry.outcome.contains("Corner Cafe"))
      assertFalse(entry.app.contains("500"))
    }
  }

  @Test
  fun chatAppsAreNeverTreatedAsPayments() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    val outcome = run("Priya", "I paid Rs 500 debited to you", pkg = "com.whatsapp")
    assertTrue(outcome is com.keepwallet.app.notify.CaptureOutcome.NotATransaction)
    assertTrue(detected().isEmpty())
  }
}

/** Bank transfers announced by email are read from the mail app's notification, with the sender as the fallback name. */
class EmailCaptureTest : UiTestBase() {
  @Test
  fun aTransferMailInGmailIsCapturedWithTheBankAsTheName() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    // Gmail: sender as the title, subject as the text, the start of the mail as the long text.
    val outcome = runBlocking {
      container.capture.process(
        "com.google.android.gm", "Gmail", "HDFC Bank InstaAlerts", "Transaction alert",
        listOf("Dear Customer, Rs. 15,000.00 has been debited from your account XX1234 via NEFT on 12-10-2026.")
      )
    }
    assertTrue(outcome is com.keepwallet.app.notify.CaptureOutcome.Captured)
    val entry = detected().single()
    assertEquals(1_500_000L, entry.amountMinor)
    assertEquals(TxType.EXPENSE, entry.type)
    assertEquals("HDFC Bank InstaAlerts", entry.merchant)
  }

  @Test
  fun anOrdinaryMailWithoutAPaymentIsLeftAlone() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    val outcome = runBlocking {
      container.capture.process("com.google.android.gm", "Gmail", "Team", "Lunch on Friday?", listOf("We booked a table for 6 at 1pm."))
    }
    assertTrue(outcome is com.keepwallet.app.notify.CaptureOutcome.NotATransaction)
    assertTrue(detected().isEmpty())
  }
}

/** Round 6: a tighter Insights top with a chart-style dropdown, a tighter hero card, dates on the home graph, rupee first. */
class CompactLookUiTest : UiTestBase() {
  @Test
  fun aNewInstallStartsInRupees() {
    ApplicationProvider.getApplicationContext<android.content.Context>()
      .getSharedPreferences("keep_wallet_settings", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    val fresh = SettingsStore(ApplicationProvider.getApplicationContext())
    assertEquals("₹", fresh.currencySymbol.value)
    assertEquals("₹", Money.defaultSymbol())
  }

  @Test
  fun insightsPutsThePeriodInTheBarAndTheChartRightUnderTheToggles() {
    waitForSeed()
    loadSampleData()
    click("tab_insights")
    waitForTag("insights_list")
    waitForTag("insights_donut")
    fun top(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
    val listTop = rule.onNodeWithTag("insights_list").fetchSemanticsNode().boundsInRoot.top
    // The month control lives in the top bar, above the list, not as a row of its own inside it.
    assertTrue("the period control should be above the list", top("insights_period") < listTop)
    // The toggles are the first thing in the list and the chart follows straight after.
    val toggles = rule.onNodeWithTag("insights_type", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val donut = rule.onNodeWithTag("insights_donut", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue("toggles should start near the top of the list", toggles.top - listTop < 40f)
    assertTrue("the chart should follow the toggles closely", donut.top - toggles.bottom < 40f)
  }

  @Test
  fun theChartDropdownDrawsTheSameNumbersInOtherWays() {
    waitForSeed()
    loadSampleData()
    click("tab_insights")
    waitForTag("insights_donut")
    click("insights_visual"); click("insights_visual_BARS")
    waitForTag("insights_bars"); assertFalse(tagExists("insights_donut"))
    click("insights_visual"); click("insights_visual_LINE")
    waitForTag("insights_line")
    click("insights_visual"); click("insights_visual_BLOCKS")
    waitForTag("insights_blocks")
    // The blocks are the chart now, so the card of blocks lower down is not repeated.
    assertFalse(tagExists("insights_treemap"))
    click("insights_visual"); click("insights_visual_DONUT")
    waitForTag("insights_donut")
  }

  @Test
  fun theBalanceCardIsAsTallAsItsContentWithLittleAirInTheMiddle() {
    waitForSeed()
    loadSampleData()
    waitForTag("balance_card")
    val card = rule.onNodeWithTag("balance_card", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val density = rule.activity.resources.displayMetrics.density * 0.95f
    assertTrue("the card should be well under its old 244 dp content height plus padding: ${card.height / density} dp", card.height / density < 232f)
    // The gap between the brand row and the balance label is small.
    val eye = rule.onNodeWithTag("hero_eye", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val amount = rule.onNodeWithTag("hero_amount", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue("brand row to amount: ${(amount.top - eye.bottom) / density} dp", (amount.top - eye.bottom) / density < 70f)
  }

  @Test
  fun theHomeGraphShowsDatesNotWeekdaysAndTheRangeBesideTheTitle() {
    waitForSeed()
    loadSampleData()
    val chart = widgets().first { it.kind == com.keepwallet.app.data.WidgetKind.SPENDING_CHART }
    scrollTo("home_list", "widget_spending_chart_${chart.id}")
    waitForTag("chart_dates_${chart.id}")
    val dates = textOf("chart_dates_${chart.id}")
    assertTrue("the range of dates should sit beside the title: $dates", dates.contains("–") || dates.contains("-"))
    // The week's days are numbered, not named.
    val descriptions = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("chart", substring = true, ignoreCase = true), useUnmergedTree = true)
      .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.firstOrNull() }
    val weekChart = descriptions.firstOrNull { it.startsWith("Bar chart") }
    assertNotNull("a bar chart should be on Home", weekChart)
    listOf("Mon ", "Tue ", "Wed ", "Thu ", "Fri ", "Sat ", "Sun ").forEach { name -> assertFalse("$name in $weekChart", weekChart!!.contains(name)) }
  }
}
