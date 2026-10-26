package com.keepwallet.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import com.keepwallet.app.data.AppPalette
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.Frequency
import com.keepwallet.app.data.RecurringEntity
import com.keepwallet.app.data.SettingsStore
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.ui.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate

private fun UiTestBase.viewModel(): MainViewModel = ViewModelProvider(rule.activity)[MainViewModel::class.java]

private fun UiTestBase.spendNow(minor: Long, categoryId: Long = categories().first { it.type == TxType.EXPENSE }.id, merchant: String = "Shop") = runBlocking {
  repository.saveTransaction(
    TransactionEntity(type = TxType.EXPENSE, amountMinor = minor, merchant = merchant, categoryId = categoryId, accountId = accounts().first().id, timestamp = System.currentTimeMillis())
  )
}

class DefaultLookTest : UiTestBase() {
  @Test
  fun theAppStartsInBlackAndWhite() {
    waitForSeed()
    assertEquals(AppPalette.MONO, container.settings.palette.value)
    assertEquals(BackgroundMode.SYSTEM, container.settings.backgroundMode.value)
  }
}

class NotificationsPageTest : UiTestBase() {
  @Test
  fun quietAtFirstThenTheBellOpensAnEmptyPage() {
    waitForSeed()
    waitForTag("open_alerts")
    assertFalse(tagExists("alerts_badge"))
    click("open_alerts")
    waitForTag("alerts_empty")
    click("back")
    waitForTag("home_list")
  }

  @Test
  fun aBudgetNearItsLimitShowsABadgeAndARowThatOpensPlan() {
    waitForSeed()
    val category = categories().first { it.type == TxType.EXPENSE }
    runBlocking { repository.saveCategory(category.copy(monthlyLimitMinor = 10_000)) }
    spendNow(8_500, category.id)
    waitForTag("alerts_badge")
    click("open_alerts")
    waitForTag("alert_BUDGET_NEAR")
    click("alert_BUDGET_NEAR")
    waitForTag("plan_list")
  }

  @Test
  fun aBillDueSoonIsListedAndOpensBills() {
    waitForSeed()
    val category = categories().first { it.type == TxType.EXPENSE }
    runBlocking {
      repository.saveRecurring(
        RecurringEntity(name = "Streaming", amountMinor = 1_500, categoryId = category.id, accountId = accounts().first().id, frequency = Frequency.MONTHLY,
          nextDueDay = LocalDate.now().plusDays(1).toEpochDay(), anchorDay = LocalDate.now().plusDays(1).dayOfMonth)
      )
    }
    waitForTag("alerts_badge")
    click("open_alerts")
    waitForTag("alert_BILL_DUE")
    waitForText("Streaming is due tomorrow")
    click("alert_BILL_DUE")
    waitForTag("plan_list")
  }

  @Test
  fun aPaymentWaitingForReviewIsListed() {
    waitForSeed()
    container.settings.setCaptureEnabled(true)
    runBlocking { container.capture.process("com.example.bank", "Example Bank", "UPI Alert", "Rs 90 paid to Corner Cafe") }
    waitForTag("alerts_badge")
    click("open_alerts")
    waitForTag("alert_PAYMENTS_WAITING")
    click("alert_PAYMENTS_WAITING")
    waitForTag("detected_card")
  }

  @Test
  fun anAlertFromANotificationOpensThePage() {
    waitForSeed()
    rule.runOnUiThread { rule.activity.handleIntent(android.content.Intent(rule.activity, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_ALERTS)) }
    waitForTag("alerts_list")
  }

  @Test
  fun theReminderSwitchesAreInSettingsAndRemembered() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "remind_bills_switch")
    click("remind_bills_switch")
    rule.waitUntil(5_000) { container.settings.remindBills.value }
    click("budget_alerts_switch")
    rule.waitUntil(5_000) { container.settings.budgetAlerts.value }
    assertFalse(tagExists("nudge_hour_20"))
    click("daily_nudge_switch")
    rule.waitUntil(5_000) { container.settings.dailyNudge.value }
    waitForTag("nudge_hour_22")
    click("nudge_hour_22")
    rule.waitUntil(5_000) { container.settings.nudgeHour.value == 22 }
  }
}

class ReviewScreenTest : UiTestBase() {
  private fun openReview() {
    click("tab_insights")
    waitForTag("insights_list")
    click("open_review")
    waitForTag("review_list")
  }

  @Test
  fun aMonthShowsWhatCameInWhatWentAndWhatWasKept() {
    waitForSeed()
    val income = categories().first { it.type == TxType.INCOME }
    runBlocking {
      repository.saveTransaction(TransactionEntity(type = TxType.INCOME, amountMinor = 50_000, merchant = "Pay", categoryId = income.id, accountId = accounts().first().id, timestamp = System.currentTimeMillis()))
    }
    spendNow(15_000)
    openReview()
    waitForTag("review_summary")
    awaitEquals({ "70%" }, { textOf("review_rate") })
    assertTrue(textOf("review_net"), textOf("review_net").contains("350"))
    assertTrue(textOf("review_income"), textOf("review_income").contains("500"))
    assertTrue(textOf("review_spent"), textOf("review_spent").contains("150"))
    waitForTag("review_categories")
    waitForTag("review_forecast")
  }

  @Test
  fun theYearShowsTwelveMonthsAndTheForecastGoes() {
    waitForSeed()
    loadSampleData()
    openReview()
    waitForTag("review_forecast")
    click("segment_Year")
    waitForTag("review_months")
    waitForTag("month_bars")
    waitForTag("review_best")
    assertFalse(tagExists("review_forecast"))
    click("segment_Month")
    waitForTag("review_forecast")
  }

  @Test
  fun anotherMonthHasNoForecast() {
    waitForSeed()
    loadSampleData()
    openReview()
    waitForTag("review_forecast")
    rule.onNodeWithContentDescription("Previous month").performClick()
    rule.waitUntil(5_000) { !tagExists("review_forecast") }
  }

  @Test
  fun savingAPdfAsksWhereToPutIt() {
    waitForSeed()
    openReview()
    click("review_export")
    rule.waitForIdle()
    val started = shadowOf(rule.activity).nextStartedActivityForResult
    assertNotNull(started)
    assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT, started.intent.action)
    assertEquals("application/pdf", started.intent.type)
  }

  @Test
  fun theReviewIsAlsoInSettings() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "settings_review")
    click("settings_review")
    waitForTag("review_list")
    click("back")
    waitForTag("settings_list")
  }
}

class PdfExportTest : UiTestBase() {
  @Test
  fun aReviewBecomesARealPdf() {
    waitForSeed()
    loadSampleData()
    val report = com.keepwallet.app.domain.Review.month(
      transactions(), categories(), accounts(), runBlocking { repository.recurring.first() },
      java.time.YearMonth.now(), LocalDate.now()
    )
    val out = java.io.ByteArrayOutputStream()
    try {
      com.keepwallet.app.data.ReviewPdf.write(out, report, "$")
    } catch (e: IllegalStateException) {
      // The simulated platform used for these tests has no PDF engine; on a phone it does.
      org.junit.Assume.assumeNoException("PdfDocument is not available in this test environment", e)
    }
    val bytes = out.toByteArray()
    assertTrue(bytes.size > 500)
    assertEquals("%PDF", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
  }
}

class ForeignCurrencyUiTest : UiTestBase() {
  @Test
  fun anAmountPaidInEurosIsConvertedAndKeptWithTheEntry() {
    waitForSeed()
    click("tab_add")
    waitForTag("foreign_button")
    click("foreign_button")
    waitForTag("foreign_amount")
    click("foreign_currency")
    click("foreign_currency_EUR")
    rule.onNodeWithTag("foreign_amount").performTextInput("10")
    waitForTag("foreign_result")
    assertTrue(textOf("foreign_result"), textOf("foreign_result").contains("11.63"))
    click("foreign_apply")
    waitForGone("foreign_amount")
    amountNode().assertTextEquals("11.63")
    waitForTag("foreign_note")
    click("save_entry")
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    val saved = transactions().single()
    assertEquals("EUR", saved.foreignCode)
    assertEquals(1_000L, saved.foreignMinor)
    assertEquals(1_163L, saved.amountMinor)
    // and it shows on the entry's page
    click("tab_activity")
    waitForTag("tx_${saved.id}")
    click("tx_${saved.id}")
    waitForText("Paid in EUR")
  }

  @Test
  fun typingADifferentAmountEndsTheLinkToTheOtherCurrency() {
    waitForSeed()
    click("tab_add")
    click("foreign_button")
    waitForTag("foreign_amount")
    rule.onNodeWithTag("foreign_amount").performTextInput("5")
    click("foreign_apply")
    waitForTag("foreign_note")
    click("key_1")
    waitForGone("foreign_note")
  }

  @Test
  fun theApplyButtonNeedsAnAmount() {
    waitForSeed()
    click("tab_add")
    click("foreign_button")
    waitForTag("foreign_apply")
    rule.onNodeWithTag("foreign_apply").assertIsNotEnabled()
  }

  @Test
  fun freshRatesChangeTheConversion() {
    waitForSeed()
    container.settings.setRates(com.keepwallet.app.data.RateTable(com.keepwallet.app.data.CurrencyRates.builtIn + ("EUR" to 0.5), 1L, builtIn = false))
    click("tab_add")
    click("foreign_button")
    waitForTag("foreign_amount")
    rule.onNodeWithTag("foreign_amount").performTextInput("10")
    waitForTag("foreign_result")
    assertTrue(textOf("foreign_result"), textOf("foreign_result").contains("20.00"))
  }
}

class ActivityHeaderTest : UiTestBase() {
  @Test
  fun searchLivesInTheHeaderAndThePlusIsGone() {
    waitForSeed()
    loadSampleData()
    click("tab_activity")
    waitForTag("activity_search")
    assertFalse("no plus in the header", tagExists("activity_add"))
    assertFalse("the field is closed until the icon is tapped", tagExists("search_input"))
    click("activity_search")
    waitForTag("search_input")
    rule.onNodeWithTag("search_input").performTextInput("Metro")
    rule.waitUntil(5_000) { viewModel().filters.value.query == "Metro" }
    click("activity_search")
    waitForGone("search_input")
    assertEquals("closing the search clears it", "", viewModel().filters.value.query)
  }

  @Test
  fun theMonthSitsInTheHeaderAndMovesBothWays() {
    waitForSeed()
    click("tab_activity")
    waitForTag("activity_month")
    val before = viewModel().month.value
    rule.onNodeWithContentDescription("Previous month").performClick()
    rule.waitUntil(5_000) { viewModel().month.value == before.minusMonths(1) }
    rule.onNodeWithContentDescription("Next month").performClick()
    rule.waitUntil(5_000) { viewModel().month.value == before }
  }

  @Test
  fun thereIsNoTagRowAnymore() {
    waitForSeed()
    loadSampleData()
    click("tab_activity")
    waitForTag("activity_list")
    assertFalse(tagExists("tag_row"))
  }
}

class HideOnOpenUiTest : UiTestBase() {
  @Test
  fun theCardStartsHiddenTheEyeShowsItForNowAndLeavingHidesItAgain() {
    waitForSeed()
    loadSampleData()
    container.settings.setHideOnOpen(true)
    container.settings.hideAgainIfNeeded()
    waitForTag("hero_eye")
    awaitEquals({ "••••••" }, { heroText() })
    click("hero_eye")
    rule.waitUntil(5_000) { heroText() != "••••••" }
    assertFalse(SettingsStore(rule.activity).let { it.hideOnOpen.value && !it.hideBalance.value })
    rule.runOnUiThread { viewModel().onBackgrounded() }
    awaitEquals({ "••••••" }, { heroText() })
  }

  @Test
  fun theSettingCanBeSwitchedOff() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "hide_on_open_switch")
    click("hide_on_open_switch")
    rule.waitUntil(5_000) { container.settings.hideOnOpen.value }
    click("hide_on_open_switch")
    rule.waitUntil(5_000) { !container.settings.hideOnOpen.value }
  }
}

class WelcomeLookTest : UiTestBase() {
  @Test
  fun theWelcomePreviewsTheLookAndOffersAppearanceAndHiding() {
    waitForSeed()
    container.settings.resetOnboarding()
    waitForTag("welcome_screen")
    waitForTag("welcome_preview")
    click("segment_Dark")
    rule.waitUntil(5_000) { container.settings.backgroundMode.value == BackgroundMode.DARK }
    click("segment_Light")
    rule.waitUntil(5_000) { container.settings.backgroundMode.value == BackgroundMode.LIGHT }
    click("palette_OCEAN")
    rule.waitUntil(5_000) { container.settings.palette.value == AppPalette.OCEAN }
    click("welcome_next")
    waitForTag("welcome_hide_switch")
    click("welcome_hide_switch")
    rule.waitUntil(5_000) { container.settings.hideOnOpen.value }
    click("welcome_finish")
    waitForGone("welcome_screen")
  }
}

class PageMotionTest : UiTestBase() {
  @Test
  fun tabsAndPagesSettleOnTheRightScreenInEitherDirection() {
    waitForSeed()
    click("tab_plan"); waitForTag("plan_list")
    click("tab_activity"); waitForTag("activity_month")
    click("tab_home"); waitForTag("home_list")
    click("tab_insights"); waitForTag("insights_list")
    click("tab_home"); waitForTag("home_list")
    click("open_settings"); waitForTag("settings_list")
    click("back"); waitForTag("home_list")
    click("open_alerts"); waitForTag("alerts_list")
    click("back"); waitForTag("home_list")
    waitForTag("balance_card")
  }

  @Test
  fun aPageOpenedFromAPageGoesBackOneStepAtATime() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "settings_manage")
    click("settings_manage")
    waitForTag("manage_list")
    click("back")
    waitForTag("settings_list")
    click("back")
    waitForTag("home_list")
  }
}
