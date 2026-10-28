package com.keepwallet.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.keepwallet.app.data.AccentColor
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.AppPalette
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.SettingsStore
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Periods
import androidx.compose.ui.layout.boundsInRoot
import org.junit.Assert.assertEquals
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

/** Palettes and colours: applying, mixing and the one-time flags for the first launch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeSettingsTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  private fun fresh(firstRun: Boolean = true): SettingsStore {
    context.getSharedPreferences("keep_wallet_settings", Context.MODE_PRIVATE).edit().clear().commit()
    return SettingsStore(context, firstRun)
  }

  @Test
  fun aPaletteSetsAccentHighlightAndBackgroundTogether() {
    val settings = fresh()
    AppPalette.entries.forEach { palette ->
      settings.applyPalette(palette)
      assertEquals(palette.accent, settings.accent.value)
      assertEquals(palette.background, settings.backgroundMode.value)
      assertEquals(palette.tertiary.toInt(), settings.highlight.value)
      assertEquals(palette, settings.palette.value)
      assertEquals(palette.seed?.toInt(), settings.customAccent.value)
    }
  }

  @Test
  fun changingOneThingByHandLeavesThePaletteBehindButKeepsTheRest() {
    val settings = fresh()
    settings.applyPalette(AppPalette.MIDNIGHT)
    settings.setBackgroundMode(BackgroundMode.BLACK)
    assertNull(settings.palette.value)
    assertEquals(AccentColor.VIOLET, settings.accent.value)
    assertEquals(AppPalette.MIDNIGHT.tertiary.toInt(), settings.highlight.value)
    settings.setCustomAccent(0xFF336699.toInt())
    assertEquals(0xFF336699.toInt(), settings.customAccent.value)
    settings.setAccent(AccentColor.ROSE) // a ready-made accent replaces the custom one
    assertNull(settings.customAccent.value)
  }

  @Test
  fun everythingSurvivesReopeningTheSettings() {
    val settings = fresh()
    settings.applyPalette(AppPalette.SUNSET)
    settings.setCustomAccent(0xFF884422.toInt())
    val again = SettingsStore(context)
    assertEquals(0xFF884422.toInt(), again.customAccent.value)
    assertEquals(AppPalette.SUNSET.tertiary.toInt(), again.highlight.value)
    assertEquals(AppPalette.SUNSET.background, again.backgroundMode.value)
    assertEquals(AccentColor.TERRACOTTA, again.accent.value)
  }

  @Test
  fun aFreshInstallPlaysTheLogoCardOnceAndShowsTheWelcome() {
    val settings = fresh(firstRun = true)
    assertTrue(settings.splashPending.value)
    assertFalse(settings.onboarded.value)
    settings.markSplashShown()
    settings.finishOnboarding()
    val reopened = SettingsStore(context, true)
    assertFalse("the logo card must not play again", reopened.splashPending.value)
    assertTrue(reopened.onboarded.value)
  }

  @Test
  fun anInstallThatAlreadyHasDataSkipsBoth() {
    fresh(firstRun = true)
    SettingsStore(context).isSeeded = true
    val upgraded = SettingsStore(context, true)
    assertFalse(upgraded.splashPending.value)
    assertTrue(upgraded.onboarded.value)
  }

  @Test
  fun testsCanTurnTheFirstRunExtrasOff() {
    val settings = fresh(firstRun = false)
    assertFalse(settings.splashPending.value)
    assertTrue(settings.onboarded.value)
  }
}

class PaletteScreenTest : UiTestBase() {
  @Test
  fun theCustomColourSliderSetsAnAccentOfYourOwn() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "custom_hue")
    rule.onNodeWithTag("custom_hue").performSemanticsAction(SemanticsActions.SetProgress) { it(200f) }
    rule.waitUntil(5_000) { container.settings.customAccent.value != null }
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(container.settings.customAccent.value!!, hsv)
    assertTrue("hue should be near 200 but was ${hsv[0]}", kotlin.math.abs(hsv[0] - 200f) < 3f)
    // picking a ready-made accent again clears it
    scrollTo("settings_list", "accent_BLUE")
    click("accent_BLUE")
    rule.waitUntil(5_000) { container.settings.customAccent.value == null }
  }

  @Test
  @org.robolectric.annotation.Config(qualifiers = "w411dp-h640dp-xhdpi")
  fun aSettingsSectionKeepsItsScrollPositionWhenYouComeBackToIt() {
    waitForSeed()
    click("open_settings")
    openSettingsSection("appearance")
    scrollTo("settings_list", "custom_hue")
    val before = settingsScroll()
    assertTrue("the page should be scrolled down, was $before", before > 100f)
    click("back")
    waitForTag("settings_page_index")
    click("settings_section_appearance")
    waitForTag("settings_page_appearance")
    assertEquals("the section must come back where it was left", before, settingsScroll(), 2f)
  }

  private fun settingsScroll(): Float {
    rule.waitForIdle()
    val node = rule.onNodeWithTag("settings_list").fetchSemanticsNode()
    return node.config[SemanticsProperties.VerticalScrollAxisRange].value()
  }

  @Test
  fun sectionsAreRowsOnTheIndexAndTheirSettingsAreAlwaysShownOnTheirOwnPage() {
    waitForSeed()
    click("open_settings")
    waitForTag("settings_page_index")
    // The index holds only the sections; the settings themselves sit one level down, nothing folds.
    assertFalse(tagExists("accent_TEAL"))
    click("settings_section_appearance")
    waitForTag("settings_page_appearance")
    assertTrue(tagExists("accent_TEAL"))
  }

  @Test
  fun everySettingsSectionIsListedInOrder() {
    waitForSeed()
    click("open_settings")
    waitForTag("settings_page_index")
    val ids = listOf("appearance", "home", "money", "capture", "notifications", "privacy", "backup", "data", "about")
    val tops = ids.map { id ->
      scrollTo("settings_list", "settings_section_$id")
      rule.onNodeWithTag("settings_section_$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
    }
    assertEquals("the sections run top to bottom in a fixed order", tops.sorted(), tops)
  }

  @Test
  fun tabsRememberWhereYouWere() {
    waitForSeed()
    loadSampleData()
    click("tab_activity")
    waitForTag("activity_list")
    val oldest = Analytics.inSpan(transactions(), Periods.monthSpan(YearMonth.now())).minByOrNull { it.timestamp }!!
    scrollTo("activity_list", "tx_${oldest.id}")
    click("tab_insights")
    waitForTag("insights_list")
    click("tab_activity")
    waitForTag("activity_list")
    waitForTag("tx_${oldest.id}")
  }
}

class WelcomeFlowTest : UiTestBase() {
  @Test
  fun aFreshInstallIsWelcomedWithAPaletteACurrencyAndAFirstAccount() {
    waitForSeed()
    container.settings.resetOnboarding()
    waitForTag("welcome_screen")
    click("palette_MIDNIGHT")
    rule.waitUntil(5_000) { container.settings.palette.value == AppPalette.MIDNIGHT }
    click("welcome_currency_€")
    rule.waitUntil(5_000) { container.settings.currencySymbol.value == "€" }
    click("welcome_next")
    waitForTag("welcome_balance")
    rule.onNodeWithTag("welcome_account_name").performTextReplacement("Everyday main")
    rule.onNodeWithTag("welcome_balance").performTextInput("2500")
    click("welcome_finish")
    waitForGone("welcome_screen")
    assertTrue(container.settings.onboarded.value)
    // the account is saved a moment after the page closes
    rule.waitUntil(5_000) { Analytics.accountBalance(accounts().first { it.kind == AccountKind.BANK }, transactions()) == 250_000L }
    assertEquals("Everyday main", accounts().first { it.kind == AccountKind.BANK }.name)
  }

  @Test
  fun skippingKeepsTheDefaults() {
    waitForSeed()
    container.settings.resetOnboarding()
    waitForTag("welcome_skip")
    click("welcome_skip")
    waitForGone("welcome_screen")
    assertTrue(container.settings.onboarded.value)
    assertEquals(0L, Analytics.accountBalance(accounts().first { it.kind == AccountKind.BANK }, transactions()))
  }
}

class AddFormAndListsTest : UiTestBase() {
  @Test
  fun quickAmountsAddUpAndTheCategoryIsChosenFromASheet() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    click("quick_amount_50")
    click("quick_amount_50")
    click("quick_amount_20")
    amountNode().assertTextEquals("120")
    typeAmount("5")
    amountNode().assertTextEquals("1205")

    // details, category, amount and keypad are all on the one page; no steps, nothing folds
    assertTrue(tagExists("merchant_input"))
    assertTrue(tagExists("key_1"))
    assertFalse(tagExists("step_category"))
    click("category_field")
    waitForTag("category_grid")
    click("category_option_Dining")
    waitForGone("category_grid")
    rule.onNodeWithTag("category_field").assertTextContains("Dining", substring = true)
    assertTrue(tagExists("merchant_input"))
  }

  @Test
  fun anEntrySavedFromTheNewFormIsCorrect() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    click("quick_amount_100")
    rule.onNodeWithTag("merchant_input").performTextInput("Lunch")
    click("save_entry")
    waitForGone("entry_sheet")
    rule.waitUntil(5_000) { transactions().size == 1 }
    assertEquals(10_000L, transactions().single().amountMinor)
    assertEquals("Lunch", transactions().single().merchant)
  }

  @Test
  fun insightsRowsShowHowTheCategoryMovedSinceLastTime() {
    waitForSeed()
    loadSampleData()
    click("tab_insights")
    waitForTag("insights_breakdown")
    val name = listOf("Housing", "Groceries", "Transport", "Dining", "Shopping").first { tagExists("breakdown_row_$it") }
    click("breakdown_row_$name")
    waitForTag("breakdown_facts_$name")
    waitForTag("breakdown_change_$name")
    waitForText("Share of total spending")
    waitForText("Transactions")
  }

  @Test
  fun activityTabsCarryCountsAndAccountsCanFilter() {
    waitForSeed()
    loadSampleData()
    click("tab_activity")
    waitForTag("filter_ALL")
    val month = Periods.monthSpan(YearMonth.now())
    val everyLeg = Analytics.inSpan(transactions(), month)
    // a transfer is one line in the list: its outgoing half
    val inMonth = everyLeg.filter { it.type != TxType.TRANSFER_IN }
    val expenses = inMonth.count { it.type == TxType.EXPENSE }
    val income = inMonth.count { it.type == TxType.INCOME }
    waitForText("All (${inMonth.size})")
    waitForText("Expenses ($expenses)")
    waitForText("Income ($income)")

    click("filter_account")
    val wallet = accounts().first { it.kind == AccountKind.WALLET }
    click("filter_account_${wallet.name}")
    val inWallet = inMonth.count { tx -> tx.accountId == wallet.id || (tx.transferId != null && everyLeg.any { it.transferId == tx.transferId && it.accountId == wallet.id }) }
    waitForText("All ($inWallet)")
    click("filter_INCOME")
    waitForTag("filter_INCOME")
  }

  @Test
  fun budgetsAreGroupedIntoOneCardPerGroup() {
    waitForSeed()
    click("tab_plan")
    waitForTag("plan_list")
    waitForTag("budget_group_ESSENTIAL")
    waitForTag("budget_group_FLEXIBLE")
  }

  @Test
  fun theMonthlyPlanIsOnByDefaultAndTheMonthSitsInTheTopBar() {
    waitForSeed()
    waitForTag("widget_month_overview")
    waitForTag("hero_month")
    waitForTag("open_settings")
    assertTrue(widgets().first { it.kind == com.keepwallet.app.data.WidgetKind.MONTH_OVERVIEW }.enabled)
    assertNotNull(tagExists("home_title"))
  }
}
