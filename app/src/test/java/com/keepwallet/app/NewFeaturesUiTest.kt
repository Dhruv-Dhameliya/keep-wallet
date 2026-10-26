package com.keepwallet.app

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.TxType
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.balanceAsOf
import com.keepwallet.app.domain.endOfMonthMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** The month, the hero card, expandable sections, list/grid views, bills, goals, habits and settings search. */
class MonthAndHeroTest : UiTestBase() {
  private fun fmt(minor: Long) = Money.format(minor, container.settings.currencySymbol.value)

  private fun bankBalanceAsOf(month: YearMonth) =
    accounts().filter { it.kind == AccountKind.BANK }.sumOf { Analytics.balanceAsOf(it, transactions(), Analytics.endOfMonthMillis(month)) }

  private fun anotherMonth(): YearMonth {
    val today = LocalDate.now()
    return YearMonth.of(today.year, if (today.monthValue == 1) 2 else today.monthValue - 1)
  }

  @Test
  fun holdingTheCardPicksAMonthAndEverythingFollowsIt() {
    waitForSeed()
    loadSampleData()
    waitForTag("balance_card")
    val target = anotherMonth()

    rule.onNodeWithTag("balance_card").performTouchInput { longClick() }
    waitForTag("month_${target.monthValue}")
    click("month_${target.monthValue}")

    // the card shows the balance as it stood at the end of that month, with the way back
    waitForTag("hero_now")
    waitForText("END OF ${Periods.shortMonthLabel(target).uppercase()}")
    awaitEquals({ fmt(bankBalanceAsOf(target)) }, { heroText() })
    // the other sections load that month's entries
    val activityHeader = "Activity · ${Periods.monthLabel(target)}"
    scrollTo("home_list", "widget_recent")
    waitForText(activityHeader)
    // and the other tabs are on the same month
    click("tab_activity")
    waitForText(Periods.monthLabel(target))
    click("tab_home")

    click("hero_now")
    waitForGone("hero_now")
    awaitEquals({ fmt(accounts().filter { it.kind == AccountKind.BANK }.sumOf { Analytics.accountBalance(it, transactions()) }) }, { heroText() })
  }

  @Test
  fun theMonthPillOpensThePickerToo() {
    waitForSeed()
    click("hero_month")
    waitForTag("month_this")
    click("month_this")
    waitForGone("month_this")
    assertFalse(tagExists("hero_now"))
  }

  @Test
  fun theEyeHidesTheBalanceAndRemembersIt() {
    waitForSeed()
    loadSampleData()
    waitForTag("hero_eye")
    click("hero_eye")
    awaitEquals({ "••••••" }, { heroText() })
    assertTrue(container.settings.hideBalance.value)
    click("hero_eye")
    awaitEquals({ fmt(accounts().filter { it.kind == AccountKind.BANK }.sumOf { Analytics.accountBalance(it, transactions()) }) }, { heroText() })
    assertFalse(container.settings.hideBalance.value)
  }

  @Test
  fun theBalanceCardShowsTheMonthsIncomeAndExpense() {
    waitForSeed()
    loadSampleData()
    waitForTag("hero_income")
    val month = YearMonth.now()
    val tx = Analytics.inSpan(transactions(), Periods.monthSpan(month))
    val income = Analytics.total(tx, TxType.INCOME)
    val expense = Analytics.total(tx, TxType.EXPENSE)
    awaitEquals({ "${Money.formatWhole(income, "$")} in" }, { textOf("hero_income") })
    awaitEquals({ "${Money.formatWhole(expense, "$")} out" }, { textOf("hero_expense") })
  }
}

class ExpandableSectionsTest : UiTestBase() {
  @Test
  fun theGraphOpensItsDetailsWhenTheHeaderIsTapped() {
    waitForSeed()
    loadSampleData()
    val chart = widgets().first { it.kind == WidgetKind.SPENDING_CHART }
    waitForTag("chart_expand_${chart.id}")
    assertFalse(tagExists("chart_details_${chart.id}"))
    click("chart_expand_${chart.id}")
    waitForTag("chart_details_${chart.id}")
    waitForText("DAILY AVERAGE")
    click("chart_expand_${chart.id}")
    waitForGone("chart_details_${chart.id}")
  }

  @Test
  fun aPocketOpensDetailsUnderItAndLinksToActivity() {
    waitForSeed()
    loadSampleData()
    scrollTo("home_list", "widget_pockets")
    val pocket = "pocket_Groceries"
    // the busiest four are shown; open whichever of them comes first
    val name = listOf("Housing", "Groceries", "Transport", "Dining", "Shopping").first { tagExists("pocket_$it") }
    click("pocket_$name")
    waitForTag("pocket_details_$name")
    waitForText("See all in Activity")
    click("pocket_open_$name")
    waitForTag("activity_list")
    assertTrue(pocket.isNotEmpty())
  }

  @Test
  fun recentActivityShowsMoreOnRequest() {
    waitForSeed()
    loadSampleData()
    scrollTo("home_list", "recent_expand")
    click("recent_expand")
    waitForText("Show fewer")
    scrollTo("home_list", "recent_expand") // the button moved down when the list grew
    click("recent_expand")
    waitForText("more")
  }

  @Test
  fun anAccountOpensItsLatestEntriesAndTheEditButton() {
    waitForSeed()
    loadSampleData()
    click("balance_card")
    waitForTag("balance_page")
    click("account_row_Wallet")
    waitForTag("account_details_Wallet")
    waitForTag("account_edit_Wallet")
    click("account_row_Wallet")
    waitForGone("account_details_Wallet")
  }

  @Test
  fun accountsAndCategoriesCanBeShownAsAGrid() {
    waitForSeed()
    loadSampleData()
    click("balance_card")
    waitForTag("balance_page")
    click("view_grid")
    rule.waitUntil(5_000) { "accounts" in container.settings.gridViews.value }
    click("account_row_Cash")
    waitForTag("account_details_Cash")
    click("view_list")
    rule.waitUntil(5_000) { "accounts" !in container.settings.gridViews.value }
  }

  @Test
  fun insightsRowsOpenTheLatestEntriesOfACategory() {
    waitForSeed()
    loadSampleData()
    click("tab_insights")
    waitForTag("insights_breakdown")
    val first = listOf("Housing", "Groceries", "Transport", "Dining", "Shopping", "Subscriptions").first { tagExists("breakdown_row_$it") }
    click("breakdown_row_$first")
    waitForTag("breakdown_details_$first")
    // the grid keeps the opened category open; tapping its tile closes it again
    click("view_grid")
    rule.waitUntil(5_000) { "insights" in container.settings.gridViews.value }
    waitForTag("breakdown_details_$first")
    click("breakdown_row_$first")
    waitForGone("breakdown_details_$first")
  }

  @Test
  fun budgetsOpenDetailsAndCanSwitchToAGrid() {
    waitForSeed()
    loadSampleData()
    click("tab_plan")
    click("budget_row_Groceries")
    waitForTag("budget_details_Groceries")
    waitForTag("budget_edit_Groceries")
    click("view_grid")
    rule.waitUntil(5_000) { "plan" in container.settings.gridViews.value }
    waitForTag("budget_row_Groceries")
    click("budget_row_Groceries")
    waitForTag("budget_details_Groceries")
    click("budget_open_Groceries")
    waitForTag("activity_list")
  }

  @Test
  fun activityDaysCanBeFolded() {
    waitForSeed()
    loadSampleData()
    click("tab_activity")
    waitForTag("activity_list")
    val first = transactions().first()
    waitForTag("tx_${first.id}")
    val date = Periods.millisToDate(first.timestamp)
    click("day_header_$date")
    waitForGone("tx_${first.id}")
    click("day_header_$date")
    waitForTag("tx_${first.id}")
  }
}

class HomeSectionsTest : UiTestBase() {
  private fun enable(kind: WidgetKind) {
    runBlocking { repository.setWidgetEnabled(widgets().first { it.kind == kind }.id, true) }
  }

  @Test
  fun highlightsExplainEachFigureWhenTapped() {
    waitForSeed()
    loadSampleData()
    enable(WidgetKind.HIGHLIGHTS)
    scrollTo("home_list", "widget_highlights")
    waitForTag("highlight_daily")
    assertFalse(tagExists("highlight_note"))
    click("highlight_forecast")
    waitForTag("highlight_note")
    waitForText("At this pace")
    click("highlight_biggest")
    waitForTag("highlight_open_biggest")
    click("highlight_open_biggest")
    waitForTag("entry_details")
  }

  @Test
  fun upcomingBillsCanBePaidFromHome() {
    waitForSeed()
    loadSampleData()
    enable(WidgetKind.UPCOMING_BILLS)
    scrollTo("home_list", "widget_upcoming")
    val bill = runBlocking { repository.recurring.first() }.minByOrNull { it.nextDueDay }!!
    val before = transactions().size
    val dueBefore = bill.nextDueDay
    click("pay_${bill.name}")
    rule.waitUntil(5_000) { transactions().size == before + 1 }
    assertEquals(bill.name, transactions().first { it.merchant == bill.name && it.note?.startsWith("Recurring") == true }.merchant)
    rule.waitUntil(5_000) { runBlocking { repository.recurring.first() }.first { it.id == bill.id }.nextDueDay > dueBefore }
  }

  @Test
  fun savingsGoalsShowOnHomeAndOpenThePlanTab() {
    waitForSeed()
    loadSampleData()
    enable(WidgetKind.SAVINGS_GOALS)
    scrollTo("home_list", "widget_goals")
    waitForText("Emergency fund")
    click("widget_goals")
    waitForTag("plan_list")
    waitForTag("goals_summary")
  }

  @Test
  fun anEmptyHomeSectionExplainsItselfAndLeadsWhereToFixIt() {
    waitForSeed()
    enable(WidgetKind.UPCOMING_BILLS)
    enable(WidgetKind.SAVINGS_GOALS)
    scrollTo("home_list", "widget_upcoming")
    waitForText("No bills yet")
    scrollTo("home_list", "widget_goals")
    waitForText("Nothing saved for yet")
  }

  @Test
  fun newSectionsAreOffByDefaultButAvailableInCustomize() {
    waitForSeed()
    listOf(WidgetKind.HIGHLIGHTS, WidgetKind.UPCOMING_BILLS, WidgetKind.SAVINGS_GOALS).forEach { kind ->
      assertFalse(widgets().first { it.kind == kind }.enabled)
    }
    assertFalse(tagExists("widget_highlights"))
    click("customize_home")
    waitForTag("customize_row_HIGHLIGHTS")
    waitForTag("customize_row_UPCOMING_BILLS")
    waitForTag("customize_row_SAVINGS_GOALS")
  }
}

class PlanGoalsAndBillsTest : UiTestBase() {
  @Test
  fun goalsCanBeCreatedFundedEditedAndDeleted() {
    waitForSeed()
    click("tab_plan")
    click("segment_Goals")
    waitForText("No goals yet")
    click("add_goal")
    waitForTag("goal_name")
    rule.onNodeWithTag("goal_name").performTextInput("Trip")
    rule.onNodeWithTag("goal_target").performTextInput("1000")
    rule.onNodeWithTag("goal_saved").performTextInput("100")
    click("goal_save")
    rule.waitUntil(5_000) { runBlocking { repository.goals.first() }.isNotEmpty() }
    val goal = runBlocking { repository.goals.first() }.single()
    assertEquals(100_000L, goal.targetMinor)
    assertEquals(10_000L, goal.savedMinor)
    waitForTag("goals_summary")

    click("goal_Trip")
    waitForTag("goal_details_Trip")
    click("goal_add_Trip_50")
    rule.waitUntil(5_000) { runBlocking { repository.goals.first() }.single().savedMinor == 15_000L }

    click("goal_custom_Trip")
    waitForTag("goal_amount")
    rule.onNodeWithTag("goal_amount").performTextInput("25")
    click("goal_amount_take")
    rule.waitUntil(5_000) { runBlocking { repository.goals.first() }.single().savedMinor == 12_500L }

    click("goal_edit_Trip")
    waitForTag("goal_delete")
    click("goal_delete")
    rule.waitUntil(5_000) { runBlocking { repository.goals.first() }.isEmpty() }
  }

  @Test
  fun goalsCanBeShownAsAGrid() {
    waitForSeed()
    loadSampleData()
    click("tab_plan")
    click("segment_Goals")
    waitForTag("goal_Emergency fund")
    click("view_grid")
    rule.waitUntil(5_000) { "goals" in container.settings.gridViews.value }
    click("goal_Summer trip")
    waitForTag("goal_details_Summer trip")
  }

  @Test
  fun billsSummariseWhatIsDueAndCanBePaidSkippedAndAutomated() {
    waitForSeed()
    loadSampleData()
    click("tab_plan")
    click("segment_Bills")
    waitForTag("bills_summary")
    val bills = runBlocking { repository.recurring.first() }
    val perMonth = bills.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor }
    awaitEquals({ Money.formatWhole(perMonth, "$") }, { textOf("bills_per_month") })

    click("bill_Streamly")
    waitForTag("bill_details_Streamly")
    click("bill_auto_Streamly")
    rule.waitUntil(5_000) { runBlocking { repository.recurring.first() }.first { it.name == "Streamly" }.autoPost }

    val dueBefore = bills.first { it.name == "Streamly" }.nextDueDay
    click("bill_skip_Streamly")
    rule.waitUntil(5_000) { runBlocking { repository.recurring.first() }.first { it.name == "Streamly" }.nextDueDay > dueBefore }

    val entries = transactions().size
    click("bill_pay_Streamly")
    rule.waitUntil(5_000) { transactions().size == entries + 1 }

    click("bill_pause_Streamly")
    rule.waitUntil(5_000) { !runBlocking { repository.recurring.first() }.first { it.name == "Streamly" }.active }
    waitForText("Paused")
  }

  @Test
  fun aBillCanBeAddedFromTheSheetAndDeleted() {
    waitForSeed()
    click("tab_plan")
    click("segment_Bills")
    waitForText("No bills yet")
    click("add_bill")
    waitForTag("bill_name")
    rule.onNodeWithTag("bill_name").performTextInput("Gym")
    rule.onNodeWithTag("bill_amount").performTextInput("30")
    click("segment_Weekly")
    click("bill_save")
    rule.waitUntil(5_000) { runBlocking { repository.recurring.first() }.isNotEmpty() }
    val bill = runBlocking { repository.recurring.first() }.single()
    assertEquals("Gym", bill.name)
    assertEquals(3_000L, bill.amountMinor)
    assertEquals(com.keepwallet.app.data.Frequency.WEEKLY, bill.frequency)
    assertEquals(LocalDate.now().toEpochDay(), bill.nextDueDay)
    assertFalse(bill.autoPost)

    click("bill_Gym")
    click("bill_edit_Gym")
    waitForTag("bill_delete")
    click("bill_delete")
    rule.waitUntil(5_000) { runBlocking { repository.recurring.first() }.isEmpty() }
  }
}

class HabitsAndSettingsTest : UiTestBase() {
  @Test
  fun frequentEntriesOfferQuickFillInTheForm() {
    waitForSeed()
    loadSampleData()
    click("tab_add")
    waitForTag("suggestions")
    waitForTag("suggestion_Metro")
    click("suggestion_Metro")
    rule.onNodeWithTag("merchant_input").assertTextContains("Metro", substring = true)
    amountNode().assertTextEquals("3.2")
    // once a name is filled in, the chips step aside
    waitForGone("suggestions")
  }

  @Test
  fun noSuggestionsOnAFreshInstall() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    assertFalse(tagExists("suggestions"))
  }

  @Test
  fun settingsSearchNarrowsTheListAndSectionsFold() {
    waitForSeed()
    click("open_settings")
    waitForTag("settings_search")
    rule.onNodeWithTag("settings_search").performTextInput("spreadsheet")
    waitForTag("export_csv")
    assertFalse(tagExists("erase_all"))
    assertFalse(tagExists("accent_TEAL"))
    rule.onNodeWithTag("settings_search").performTextInput(" zzzz")
    waitForTag("settings_no_match")
  }

  @Test
  fun hideBalancesAndBillsAndGoalsRowsWork() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "hide_balance_switch")
    click("hide_balance_switch")
    rule.waitUntil(5_000) { container.settings.hideBalance.value }
    scrollTo("settings_list", "settings_bills")
    click("settings_bills")
    waitForTag("plan_list")
    waitForText("No bills yet")
  }

  @Test
  fun theSummaryCountsWhatIsStored() {
    waitForSeed()
    loadSampleData()
    click("open_settings")
    waitForTag("settings_summary")
    waitForText("${transactions().size} entries")
    scrollTo("settings_list", "app_version")
    waitForText("Version ")
  }
}
