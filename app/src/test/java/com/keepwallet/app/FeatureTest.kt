package com.keepwallet.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.keepwallet.app.data.AccentColor
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.ChartRange
import com.keepwallet.app.data.ChartType
import com.keepwallet.app.data.DetectedEntryEntity
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.notify.CaptureOutcome
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.YearMonth

private val defaultOrder = listOf(
  WidgetKind.BALANCE_CARD, WidgetKind.MONTH_OVERVIEW, WidgetKind.SPENDING_CHART, WidgetKind.RECENT_ACTIVITY, WidgetKind.POCKETS_GRID,
  WidgetKind.QUICK_STATS, WidgetKind.WEEK_OVERVIEW, WidgetKind.CATEGORY_BREAKDOWN,
  WidgetKind.HIGHLIGHTS, WidgetKind.UPCOMING_BILLS, WidgetKind.SAVINGS_GOALS
)

class DashboardTest : UiTestBase() {
  private fun order() = widgets().sortedBy { it.position }.map { it.kind }

  @Test
  fun homeStartsCleanInTheRequestedOrder() {
    waitForSeed()
    assertEquals(defaultOrder, order())
    assertEquals(
      setOf(WidgetKind.BALANCE_CARD, WidgetKind.MONTH_OVERVIEW, WidgetKind.SPENDING_CHART, WidgetKind.RECENT_ACTIVITY, WidgetKind.POCKETS_GRID),
      widgets().filter { it.enabled }.map { it.kind }.toSet()
    )
    val chart = widgets().first { it.kind == WidgetKind.SPENDING_CHART }
    waitForTag("balance_card"); waitForTag("widget_spending_chart_${chart.id}"); waitForTag("widget_recent"); waitForTag("widget_pockets")
    fun top(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().top
    assertTrue(top("balance_card") < top("widget_spending_chart_${chart.id}"))
    assertTrue(top("widget_spending_chart_${chart.id}") < top("widget_recent"))
    assertTrue(top("widget_recent") < top("widget_pockets"))
    // everything else is off, and so is the old header month selector
    assertTrue(tagExists("widget_month_overview"))
    assertFalse(tagExists("widget_quick_stats"))
    assertFalse(tagExists("widget_week_overview"))
    assertFalse(tagExists("month_navigator"))
  }

  @Test
  fun sectionsAreDraggedIntoANewOrderAndItSticks() {
    waitForSeed()
    click("customize_home")
    waitForTag("customize_list")

    fun drag(kind: WidgetKind, rows: Float) {
      rule.onNodeWithTag("drag_handle_${kind.name}").performTouchInput {
        val row = 76.dp.toPx()
        down(center)
        repeat(10) { moveBy(Offset(0f, row * rows / 10f)) }
        up()
      }
    }
    drag(WidgetKind.RECENT_ACTIVITY, -1f)
    rule.waitUntil(5_000) { order().indexOf(WidgetKind.RECENT_ACTIVITY) == 2 }
    assertEquals(
      listOf(WidgetKind.BALANCE_CARD, WidgetKind.MONTH_OVERVIEW, WidgetKind.RECENT_ACTIVITY, WidgetKind.SPENDING_CHART, WidgetKind.POCKETS_GRID),
      order().take(5)
    )
    drag(WidgetKind.BALANCE_CARD, 3f)
    rule.waitUntil(5_000) { order().indexOf(WidgetKind.BALANCE_CARD) == 3 }
    assertEquals(
      listOf(WidgetKind.MONTH_OVERVIEW, WidgetKind.RECENT_ACTIVITY, WidgetKind.SPENDING_CHART, WidgetKind.BALANCE_CARD, WidgetKind.POCKETS_GRID),
      order().take(5)
    )
    assertEquals((0 until 11).toList(), widgets().map { it.position }.sorted())

    // Home follows the new order
    click("back")
    waitForTag("home_list")
    fun top(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().top
    assertTrue(top("widget_recent") < top("balance_card"))
  }

  @Test
  fun sectionsCanBeToggledAddedAndRemoved() {
    waitForSeed()
    click("customize_home")
    waitForTag("customize_list")

    click("toggle_POCKETS_GRID")
    rule.waitUntil(5_000) { !widgets().first { it.kind == WidgetKind.POCKETS_GRID }.enabled }
    click("toggle_HIGHLIGHTS")
    rule.waitUntil(5_000) { widgets().first { it.kind == WidgetKind.HIGHLIGHTS }.enabled }

    click("add_section")
    click("add_QUICK_STATS")
    rule.waitUntil(5_000) { widgets().count { it.kind == WidgetKind.QUICK_STATS } == 2 }
    click("remove_WEEK_OVERVIEW")
    rule.waitUntil(5_000) { widgets().none { it.kind == WidgetKind.WEEK_OVERVIEW } }
    assertEquals(11, widgets().size)
    assertEquals((0 until 11).toList(), widgets().map { it.position }.sorted())

    click("back")
    waitForTag("home_list")
    scrollTo("home_list", "widget_highlights")
    assertFalse(tagExists("widget_pockets"))
  }

  @Test
  fun graphPeriodComesFromADropdownAndTheStyleFromSettings() {
    waitForSeed()
    loadSampleData()
    val chart = widgets().first { it.kind == WidgetKind.SPENDING_CHART }
    waitForTag("widget_spending_chart_${chart.id}")
    val symbol = container.settings.currencySymbol.value
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val weekStart = Periods.weekStart(today)

    fun expectedTotal(range: ChartRange): String {
      val span = Analytics.rangeSpan(range, month, weekStart)
      return Money.formatWhole(Analytics.total(Analytics.inSpan(transactions(), span), TxType.EXPENSE), symbol)
    }
    fun shown() = textOf("chart_total_${chart.id}")

    // The old chart-type chips are gone; Bars is the default look.
    ChartType.entries.forEach { assertFalse(tagExists("chart_type_${it.name}_${chart.id}")) }
    assertEquals(ChartType.BAR, container.settings.chartType.value)

    assertEquals(ChartRange.WEEK, chart.range)
    awaitEquals({ expectedTotal(ChartRange.WEEK) }, { shown() })

    listOf(ChartRange.MONTH, ChartRange.QUARTER, ChartRange.YEAR, ChartRange.WEEK).forEach { range ->
      click("chart_range_${chart.id}")
      click("chart_range_${range.name}_${chart.id}")
      rule.waitUntil(5_000) { widgets().first { it.id == chart.id }.range == range }
      awaitEquals({ expectedTotal(range) }, { shown() })
    }
    assertTrue(expectedTotal(ChartRange.YEAR) != expectedTotal(ChartRange.WEEK))

    // The look is a setting.
    click("open_settings")
    click("chart_style_LINE")
    rule.waitUntil(5_000) { container.settings.chartType.value == ChartType.LINE }
  }
}

class HeroCardTest : UiTestBase() {
  private fun fmt(minor: Long) = Money.format(minor, container.settings.currencySymbol.value)

  private fun balanceOf(kinds: Set<AccountKind>) =
    accounts().filter { it.kind in kinds }.sumOf { Analytics.accountBalance(it, transactions()) }

  @Test
  fun cardShowsOnlyTheBankBalanceUnlessTotalIsSwitchedOn() {
    waitForSeed()
    loadSampleData()
    val bank = balanceOf(setOf(AccountKind.BANK))
    val all = balanceOf(AccountKind.entries.toSet())
    assertTrue(bank != all)
    waitForText("BANK BALANCE")
    awaitEquals({ fmt(bank) }, { heroText() })

    click("open_settings")
    click("hero_total_switch")
    click("back")
    click("back")
    waitForText("TOTAL BALANCE")
    awaitEquals({ fmt(all) }, { heroText() })
    assertTrue(container.settings.showTotalOnHero.value)
  }

  @Test
  fun tappingTheCardOpensBalancesAndTheMonthNowLivesThere() {
    waitForSeed()
    loadSampleData()
    click("balance_card")
    waitForTag("balance_page")
    val symbol = container.settings.currencySymbol.value
    val today = LocalDate.now()

    fun expected(month: YearMonth): Triple<String, String, String> {
      val tx = Analytics.inSpan(transactions(), Periods.monthSpan(month))
      val income = Analytics.total(tx, TxType.INCOME)
      val expense = Analytics.total(tx, TxType.EXPENSE)
      val net = income - expense
      return Triple(
        Money.format(income, symbol, forceSign = income > 0),
        Money.format(-expense, symbol),
        Money.format(net, symbol, forceSign = net > 0)
      )
    }

    var (income, expense, net) = expected(YearMonth.from(today))
    awaitEquals({ income }, { textOf("balance_income") })
    awaitEquals({ expense }, { textOf("balance_expense") })
    awaitEquals({ net }, { textOf("balance_net") })

    // browse back a month inside the page
    rule.onNodeWithContentDescription("Previous month").performClick()
    val previous = expected(YearMonth.from(today).minusMonths(1))
    awaitEquals({ previous.first }, { textOf("balance_income") })
    awaitEquals({ previous.second }, { textOf("balance_expense") })

    // every account is listed and the total is their sum
    assertTrue(tagExists("account_row_Bank account"))
    assertTrue(tagExists("account_row_Wallet"))
    assertTrue(tagExists("account_row_Cash"))
    val total = balanceOf(AccountKind.entries.toSet())
    awaitEquals({ Money.format(total, symbol) }, { textOf("balance_total") })

    // the back arrow returns to Home
    click("back")
    waitForTag("home_list")
    assertFalse(tagExists("balance_page"))
  }

  @Test
  fun accountsCanBeAddedSetAndDeleted() {
    waitForSeed()
    click("balance_card")
    waitForTag("balance_page")

    // a new wallet with an opening amount
    click("add_account_WALLET")
    waitForTag("account_name")
    rule.onNodeWithTag("account_name").performTextInput("Paytm")
    rule.onNodeWithTag("account_balance_input").performTextInput("250")
    click("account_save")
    rule.waitUntil(5_000) { accounts().any { it.name == "Paytm" } }
    val paytm = accounts().first { it.name == "Paytm" }
    assertEquals(AccountKind.WALLET, paytm.kind)
    awaitEquals({ "$250.00" }, { textOf("account_balance_Paytm") })

    // an account with entries can't be deleted
    runBlocking {
      repository.saveTransaction(
        TransactionEntity(type = TxType.EXPENSE, amountMinor = 1_000, merchant = "Tea", categoryId = categories().first().id,
          accountId = paytm.id, timestamp = System.currentTimeMillis())
      )
    }
    click("account_row_Paytm"); click("account_edit_Paytm")
    waitForTag("account_delete")
    click("account_delete")
    waitForText("still has transactions")
    assertTrue(accounts().any { it.name == "Paytm" })
    awaitEquals({ "$240.00" }, { textOf("account_balance_Paytm") })

    // setting the balance makes the account show exactly that amount, entries included
    click("account_edit_Paytm") // the row is still open from before
    waitForTag("account_balance_input")
    rule.onNodeWithTag("account_balance_input").performTextReplacement("2405")
    click("account_save")
    rule.waitUntil(5_000) {
      Analytics.accountBalance(accounts().first { it.name == "Paytm" }, transactions()) == 240_500L
    }
  }

  @Test
  fun anEmptyAccountCanBeDeleted() {
    waitForSeed()
    click("balance_card")
    waitForTag("balance_page")
    click("account_row_Cash"); click("account_edit_Cash")
    waitForTag("account_delete")
    click("account_delete")
    rule.waitUntil(5_000) { accounts().none { it.name == "Cash" } }
  }
}

class BudgetTest : UiTestBase() {
  private fun category(name: String) = categories().first { it.name == name }

  @Test
  fun limitsCanBeEditedFromThePlanScreen() {
    waitForSeed()
    click("tab_plan")
    click("budget_row_Groceries")
    click("budget_edit_Groceries")
    waitForTag("limit_monthly")
    rule.onNodeWithTag("limit_monthly").performTextInput("600")
    rule.onNodeWithTag("limit_weekly").performTextInput("150")
    click("limit_save")
    rule.waitUntil(5_000) { category("Groceries").monthlyLimitMinor == 60_000L }
    assertEquals(15_000L, category("Groceries").weeklyLimitMinor)
    rule.waitUntil(5_000) { textExists("$600") }
  }

  @Test
  fun moveMoneyShiftsLimitBetweenCategories() {
    waitForSeed()
    runBlocking {
      repository.setLimits(category("Housing").id, 100_000, 25_000)
      repository.setLimits(category("Groceries").id, 60_000, 15_000)
    }
    click("tab_plan")
    click("move_money")
    waitForTag("move_amount")
    rule.onNodeWithTag("move_amount").performTextInput("200")
    click("move_confirm")
    rule.waitUntil(5_000) { category("Housing").monthlyLimitMinor == 80_000L }
    assertEquals(80_000L, category("Groceries").monthlyLimitMinor)
    assertEquals(160_000L, Analytics.budgetTotal(categories(), weekly = false))
  }

  @Test
  fun moveMoneyRefusesMoreThanTheSourceHas() {
    waitForSeed()
    runBlocking {
      repository.setLimits(category("Housing").id, 10_000, 0)
      repository.setLimits(category("Groceries").id, 60_000, 0)
    }
    click("tab_plan")
    click("move_money")
    waitForTag("move_amount")
    rule.onNodeWithTag("move_amount").performTextInput("500")
    click("move_confirm")
    waitForText("doesn't have that much")
    assertEquals(10_000L, category("Housing").monthlyLimitMinor)
    assertEquals(60_000L, category("Groceries").monthlyLimitMinor)
  }

  @Test
  fun entryFormShowsWhatIsLeftAfterSaving() {
    waitForSeed()
    val groceries = category("Groceries")
    val bank = accounts().first { it.kind == AccountKind.BANK }
    runBlocking {
      repository.setLimits(groceries.id, 60_000, 0)
      repository.saveTransaction(
        TransactionEntity(
          type = TxType.EXPENSE, amountMinor = 42_000, merchant = "Green Market", categoryId = groceries.id,
          accountId = bank.id, timestamp = System.currentTimeMillis()
        )
      )
    }
    waitForText("Green Market") // the home screen has caught up with the new entry
    click("tab_add")
    waitForTag("entry_sheet")
    typeAmount("24")
    waitForTag("budget_impact")
    waitForText("$156 left after saving")
    typeAmount("00")
    waitForText("over the Groceries budget")
  }
}

class NotificationCaptureTest : UiTestBase() {
  private val notifications get() =
    shadowOf(rule.activity.getSystemService(NotificationManager::class.java)).allNotifications

  private fun process(title: String, text: String): CaptureOutcome =
    runBlocking { container.capture.process("com.example.bank", "Example Bank", title, text) }

  @Test
  fun detectedPaymentCanBeReviewedAndSaved() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)

    assertTrue(process("HDFC Bank", "Rs 250 debited at Metro transit") is CaptureOutcome.Captured)

    assertEquals(1, notifications.size)
    val posted = notifications.single()
    assertEquals("Payment detected: $250.00", posted.extras.getString(Notification.EXTRA_TITLE))
    assertEquals(2, posted.actions.size)

    waitForTag("detected_card")
    rule.onNodeWithTag("detected_amount", useUnmergedTree = true).assertTextContains("250.00", substring = true)
    assertTrue(transactions().isEmpty())

    click("detected_add")
    waitForTag("entry_sheet")
    amountNode().assertTextEquals("250")
    rule.onNodeWithTag("merchant_input").assertTextContains("Metro transit", substring = true)
    assertTrue(transactions().isEmpty())

    click("save_entry")
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    val saved = transactions().single()
    assertEquals(25_000L, saved.amountMinor)
    assertEquals(TxType.EXPENSE, saved.type)
    assertEquals("Metro transit", saved.merchant)
    rule.waitUntil(5_000) { detected().isEmpty() }
    assertTrue(notifications.isEmpty())
  }

  @Test
  fun decliningDiscardsTheDetectedEntry() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    assertTrue(process("UPI Alert", "Rs 90 paid to Corner Cafe") is CaptureOutcome.Captured)
    waitForTag("detected_card")
    click("detected_dismiss")
    rule.waitUntil(5_000) { detected().isEmpty() }
    waitForGone("detected_card")
    assertTrue(transactions().isEmpty())
    assertTrue(notifications.isEmpty())
  }

  @Test
  fun incomeIsDetectedAsIncome() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    assertTrue(process("Direct Deposit", "$4,800.00 credited as Salary") is CaptureOutcome.Captured)
    click("detected_add")
    waitForTag("entry_sheet")
    amountNode().assertTextEquals("4800")
    waitForText("Add income")
    click("save_entry")
    rule.waitUntil(5_000) { transactions().size == 1 }
    assertEquals(TxType.INCOME, transactions().single().type)
    assertEquals(480_000L, transactions().single().amountMinor)
  }

  @Test
  fun nothingIsProcessedWhileDetectionIsOff() {
    waitForSeed()
    container.settings.setCaptureEnabled(false)
    assertEquals(CaptureOutcome.Disabled, process("HDFC Bank", "Rs 250 debited at Metro transit"))
    assertTrue(detected().isEmpty())
    assertTrue(notifications.isEmpty())
    assertFalse(tagExists("detected_card"))
  }

  @Test
  fun repostedNotificationIsNotDetectedTwice() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    assertTrue(process("HDFC Bank", "Rs 250 debited at Metro transit") is CaptureOutcome.Captured)
    assertEquals(CaptureOutcome.Duplicate, process("HDFC Bank", "Rs 250 debited at Metro transit"))
    assertEquals(1, detected().size)
  }

  @Test
  fun nonPaymentNotificationsAreIgnored() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    assertTrue(process("Messages", "Dinner tonight? Let me know") is CaptureOutcome.NotATransaction)
    assertTrue(process("Bank", "123456 is your OTP for payment of Rs 500") is CaptureOutcome.NotATransaction)
    assertTrue(detected().isEmpty())
  }

  @Test
  fun onlyParsedFieldsAreStored() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    process("HDFC Bank", "Rs 250 debited at Metro transit. Avl bal Rs 9,999 a/c XX4321")
    val entry = detected().single()
    val stored = listOf(entry.merchant, entry.sourceApp, entry.sourcePackage).joinToString(" ")
    assertFalse(stored.contains("4321"))
    assertFalse(stored.contains("9,999"))
    assertEquals(25_000L, entry.amountMinor)
  }

  @Test
  fun settingsExplainsAndReflectsPermissionState() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "settings_detection")
    click("settings_detection")
    waitForTag("capture_switch")
    waitForText("Off – no notifications are read")
    waitForText("Not granted")
    click("capture_switch")
    waitForText("Allow notification access")
    clickText("Not now")
    rule.waitUntil(5_000) { !container.settings.captureEnabled.value }
    waitForText("Off – no notifications are read")
  }

  @Test
  fun testToolPreviewsAndRunsThroughThePipeline() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "settings_detection")
    click("settings_detection")
    scrollTo("detection_list", "test_preview")
    waitForText("Payment of $250.00 · Metro transit")
    scrollTo("detection_list", "test_detect")
    click("test_detect")
    waitForText("Turn on transaction detection first")
    assertTrue(detected().isEmpty())
    rule.waitUntil(10_000) { !textExists("Turn on transaction detection first") } // the snackbar would cover the button
    container.settings.setCaptureEnabled(true)
    scrollTo("detection_list", "test_detect")
    click("test_detect")
    rule.waitUntil(5_000) { detected().size == 1 }
    assertEquals(25_000L, detected().single().amountMinor)
  }
}

class SettingsTest : UiTestBase() {
  @Test
  fun settingsIsOrganisedIntoSections() {
    waitForSeed()
    click("open_settings")
    waitForTag("settings_list")
    waitForText("Appearance")
    listOf(
      "settings_customize_home" to "Home", "settings_manage" to "Money", "settings_detection" to "Smart capture",
      "export_backup" to "Backup & sync", "erase_all" to "Data", "app_version" to "About"
    ).forEach { (row, section) ->
      scrollTo("settings_list", row)
      waitForTag("settings_page_${settingsSectionOf(row)}")
      waitForText(section)
    }
    // import, export (backup and spreadsheet) live here, plus the not-yet-available Google sync
    scrollTo("settings_list", "google_sync")
    assertTrue(tagExists("export_backup"))
    assertTrue(tagExists("import_backup"))
    assertTrue(tagExists("export_csv"))
    click("google_sync")
    waitForText("Google sync isn't available yet")
  }

  @Test
  fun accentBackgroundAndPalettesAreSavedAndEverythingStillRenders() {
    waitForSeed()
    loadSampleData()
    click("open_settings")
    AccentColor.entries.forEach { accent ->
      click("accent_${accent.name}")
      rule.waitUntil(5_000) { container.settings.accent.value == accent }
    }
    BackgroundMode.entries.forEach { mode ->
      click("background_${mode.name}")
      rule.waitUntil(5_000) { container.settings.backgroundMode.value == mode }
    }
    com.keepwallet.app.data.AppPalette.entries.forEach { palette ->
      scrollTo("settings_list", "palette_${palette.name}")
      rule.onNodeWithTag("palette_${palette.name}").performScrollTo().performClick()
      rule.waitUntil(5_000) { container.settings.palette.value == palette }
      assertEquals(palette.accent, container.settings.accent.value)
      assertEquals(palette.background, container.settings.backgroundMode.value)
      assertEquals(palette.tertiary.toInt(), container.settings.highlight.value)
    }
    click("back")
    click("back")
    // after a palette is applied, every tab still renders its content
    waitForTag("home_list")
    click("tab_activity"); waitForTag("activity_list")
    click("tab_insights"); waitForTag("insights_list")
    click("tab_plan"); waitForTag("plan_list")
    click("tab_add"); waitForTag("entry_sheet")
    click("cancel_entry"); waitForGone("entry_sheet")
  }
}

class CategoryManagementTest : UiTestBase() {
  @Test
  fun categoriesCanBeAddedAndDeletedWithEntriesMovedSafely() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "settings_manage")
    click("settings_manage")
    click("add_category_EXPENSE")
    waitForTag("category_name")
    rule.onNodeWithTag("category_name").performTextInput("Gifts")
    rule.onNodeWithTag("category_monthly").performTextInput("80")
    click("category_save")
    rule.waitUntil(5_000) { categories().any { it.name == "Gifts" } }
    assertEquals(8_000L, categories().first { it.name == "Gifts" }.monthlyLimitMinor)

    val gifts = categories().first { it.name == "Gifts" }
    val others = categories().first { it.name == "Others" }
    runBlocking {
      repository.saveTransaction(
        TransactionEntity(type = TxType.EXPENSE, amountMinor = 1_500, merchant = "Card", categoryId = gifts.id,
          accountId = accounts().first().id, timestamp = System.currentTimeMillis())
      )
    }
    click("category_row_Gifts")
    click("category_delete")
    waitForText("1 entry uses this category")
    click("move_entries_to")
    click("move_target_Others")
    click("category_delete_confirm")
    rule.waitUntil(5_000) { categories().none { it.name == "Gifts" } }
    assertEquals(others.id, transactions().single().categoryId)
  }

  @Test
  fun duplicateNamesAreRejected() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "settings_manage")
    click("settings_manage")
    click("add_category_EXPENSE")
    waitForTag("category_name")
    rule.onNodeWithTag("category_name").performTextInput("Groceries")
    waitForText("already have a category with this name")
  }
}

/** Persistence across restarts: reopen the same on-disk database and read back what was saved. */
class PersistenceTest : UiTestBase() {
  @Test
  fun dashboardConfigurationAndDataSurviveReopeningTheDatabase() {
    waitForSeed()
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getDatabasePath("persist-check.db").parentFile?.mkdirs()

    fun open() = androidx.room.Room.databaseBuilder(context, com.keepwallet.app.data.KeepWalletDatabase::class.java, "persist-check.db")
      .allowMainThreadQueries().build()

    val first = open()
    val settings = com.keepwallet.app.data.SettingsStore(context)
    val repo1 = com.keepwallet.app.data.KeepWalletRepository(first, settings, com.keepwallet.app.data.ReceiptStore(context))
    runBlocking {
      settings.isSeeded = false
      repo1.ensureSeeded()
      val pockets = repo1.widgets.first().first { it.kind == WidgetKind.POCKETS_GRID }
      repo1.setWidgetEnabled(pockets.id, false)
      val ids = repo1.widgets.first().sortedBy { it.position }.map { it.id }.reversed()
      repo1.setWidgetOrder(ids)
      val chart = repo1.widgets.first().first { it.kind == WidgetKind.SPENDING_CHART }
      repo1.setWidgetChart(chart.id, range = ChartRange.YEAR)
    }
    first.close()

    val second = open()
    val repo2 = com.keepwallet.app.data.KeepWalletRepository(second, settings, com.keepwallet.app.data.ReceiptStore(context))
    val restored = runBlocking { repo2.widgets.first() }
    assertFalse(restored.first { it.kind == WidgetKind.POCKETS_GRID }.enabled)
    assertEquals(defaultOrder.reversed(), restored.sortedBy { it.position }.map { it.kind })
    assertEquals(ChartRange.YEAR, restored.first { it.kind == WidgetKind.SPENDING_CHART }.range)
    second.close()
  }
}

/** Tapping the system notification opens the app straight into the prefilled form. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.Config(application = TestKeepWalletApp::class, sdk = [35], qualifiers = "w411dp-h1400dp-xhdpi")
class NotificationDeepLinkTest {
  @get:org.junit.Rule
  val compose = androidx.compose.ui.test.junit4.v2.createEmptyComposeRule()

  @Test
  fun tappingTheNotificationOpensThePrefilledEntry() {
    val app = ApplicationProvider.getApplicationContext<TestKeepWalletApp>()
    val repository = app.container.repository
    val id = runBlocking {
      repository.ensureSeeded()
      repository.addDetected(
        DetectedEntryEntity(
          sourcePackage = "com.example.bank", sourceApp = "Example Bank", amountMinor = 25_000,
          type = TxType.EXPENSE, merchant = "Metro transit", detectedAt = System.currentTimeMillis()
        )
      )!!
    }
    val intent = Intent(app, MainActivity::class.java)
      .setAction(MainActivity.ACTION_REVIEW_DETECTED)
      .putExtra(MainActivity.EXTRA_DETECTED_ID, id)
    androidx.test.core.app.ActivityScenario.launch<MainActivity>(intent).use {
      compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("entry_sheet")).fetchSemanticsNodes().isNotEmpty() }
      compose.onNodeWithTag("amount_display", useUnmergedTree = true).assertTextEquals("250")
      compose.onNodeWithTag("merchant_input").assertTextContains("Metro transit", substring = true)
    }
  }
}
