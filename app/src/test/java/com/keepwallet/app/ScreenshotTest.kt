package com.keepwallet.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.keepwallet.app.data.AccentColor
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.WidgetKind
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the real screens with sample data so the design can be reviewed without a device.
 * Images are written to app/build/screenshots; the test itself only checks that rendering works.
 */
@Config(application = TestKeepWalletApp::class, sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest : UiTestBase() {
  private val dir = File("build/screenshots").also { it.mkdirs() }

  private fun capture(name: String) {
    rule.waitForIdle()
    val image = rule.onAllNodes(isRoot())[0].captureToImage()
    FileOutputStream(File(dir, "$name.png")).use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun scrollList(list: String, target: String) {
    rule.onNodeWithTag(list).performScrollToNode(hasTestTag(target))
    rule.waitForIdle()
  }

  private fun look(accent: AccentColor, background: BackgroundMode) {
    container.settings.setAccent(accent)
    container.settings.setBackgroundMode(background)
    rule.waitForIdle()
  }

  private fun prepare(accent: AccentColor, background: BackgroundMode, sample: Boolean = true) {
    waitForSeed()
    if (sample) loadSampleData()
    look(accent, background)
    waitForTag("balance_card")
  }

  private fun homeAndLists(prefix: String) {
    capture("${prefix}_home_1_top")
    val recent = "widget_recent"
    scrollList("home_list", recent)
    capture("${prefix}_home_2_recent")
    scrollList("home_list", "widget_pockets")
    capture("${prefix}_home_3_pockets")
    click("tab_activity"); waitForTag("activity_list")
    capture("${prefix}_activity")
    click("tab_insights"); waitForTag("insights_list")
    capture("${prefix}_insights")
    click("tab_plan"); waitForTag("plan_list")
    capture("${prefix}_plan")
    click("tab_home"); waitForTag("home_list")
  }

  @Test
  fun tealLightFullTour() {
    prepare(AccentColor.TEAL, BackgroundMode.LIGHT)
    homeAndLists("teal")

    scrollHomeToTop()
    click("balance_card"); waitForTag("balance_page")
    capture("teal_balance_1")
    scrollList("balance_page", "balance_net")
    capture("teal_balance_2")
    click("back")

    val id = transactions().first().id
    click("tab_activity"); click("tx_$id"); waitForTag("entry_details")
    capture("teal_details")
    click("edit_entry_btn"); waitForTag("entry_sheet")
    capture("teal_edit")
    click("category_field"); waitForTag("category_grid")
    capture("teal_edit_grid")
    click("cancel_entry"); waitForGone("entry_sheet")
    click("back"); waitForGone("entry_details")

    click("tab_add"); waitForTag("entry_sheet")
    typeAmount("24")
    capture("teal_add")
    click("category_field"); waitForTag("category_grid")
    capture("teal_add_grid")
    click("cancel_entry"); waitForGone("entry_sheet")

    click("tab_home"); scrollHomeToTop(); click("customize_home"); waitForTag("customize_list")
    capture("teal_customize")
    click("back")
    click("open_settings"); waitForTag("settings_list")
    capture("teal_settings_1")
    scrollList("settings_list", "erase_all")
    capture("teal_settings_2")
    scrollList("settings_list", "settings_detection"); click("settings_detection"); waitForTag("detection_list")
    capture("teal_detection")
  }

  @Test
  fun violetDark() {
    prepare(AccentColor.VIOLET, BackgroundMode.DARK)
    homeAndLists("violet_dark")
    val id = transactions().first().id
    click("tab_activity"); click("tx_$id"); waitForTag("entry_details")
    capture("violet_dark_details")
    click("back")
    click("tab_add"); waitForTag("entry_sheet")
    typeAmount("24")
    capture("violet_dark_add")
    click("cancel_entry"); waitForGone("entry_sheet")
    click("tab_home"); scrollHomeToTop(); click("open_settings"); waitForTag("settings_list")
    capture("violet_dark_settings")
  }

  @Test
  fun graphiteOnPureBlack() {
    prepare(AccentColor.GRAPHITE, BackgroundMode.BLACK)
    capture("graphite_home")
    scrollHomeToTop()
    click("balance_card"); waitForTag("balance_page")
    capture("graphite_balance")
  }

  @Test
  fun roseOnPureWhite() {
    prepare(AccentColor.ROSE, BackgroundMode.WHITE)
    capture("rose_home")
    click("tab_insights"); waitForTag("insights_list")
    capture("rose_insights")
  }

  @Test
  fun amberLight() {
    prepare(AccentColor.AMBER, BackgroundMode.LIGHT)
    capture("amber_light_home")
    click("tab_activity"); waitForTag("activity_list")
    capture("amber_light_activity")
  }

  @Test
  fun everySectionOnHome() {
    prepare(AccentColor.BLUE, BackgroundMode.LIGHT)
    runBlocking { widgets().filter { !it.enabled }.forEach { repository.setWidgetEnabled(it.id, true) } }
    scrollList("home_list", "widget_month_overview")
    capture("blue_all_1")
    scrollList("home_list", "widget_pockets")
    capture("blue_all_2")
    scrollList("home_list", "widget_category_breakdown_${widgets().first { it.kind == WidgetKind.CATEGORY_BREAKDOWN }.id}")
    capture("blue_all_3")
  }

  private fun enable(vararg kinds: WidgetKind) {
    runBlocking { kinds.forEach { kind -> repository.setWidgetEnabled(widgets().first { it.kind == kind }.id, true) } }
  }

  @Test
  fun newHomeSectionsAndExpandedStates() {
    prepare(AccentColor.TEAL, BackgroundMode.LIGHT)
    enable(WidgetKind.HIGHLIGHTS, WidgetKind.UPCOMING_BILLS, WidgetKind.SAVINGS_GOALS, WidgetKind.MONTH_OVERVIEW, WidgetKind.QUICK_STATS)
    val chart = widgets().first { it.kind == WidgetKind.SPENDING_CHART }
    waitForTag("chart_expand_${chart.id}")
    click("chart_expand_${chart.id}"); waitForTag("chart_details_${chart.id}")
    capture("new_home_chart_open")
    scrollList("home_list", "widget_pockets")
    click("pocket_Groceries"); waitForTag("pocket_details_Groceries")
    capture("new_home_pocket_open")
    scrollList("home_list", "widget_highlights")
    click("highlight_forecast"); waitForTag("highlight_note")
    capture("new_home_highlights")
    scrollList("home_list", "widget_upcoming")
    capture("new_home_bills")
    scrollList("home_list", "widget_goals")
    capture("new_home_goals")
    scrollList("home_list", "widget_month_overview")
    click("widget_month_overview"); waitForTag("month_details")
    capture("new_home_month_open")
  }

  @Test
  fun heroInOtherMonthAndHidden() {
    prepare(AccentColor.VIOLET, BackgroundMode.LIGHT)
    click("hero_eye")
    awaitHidden()
    capture("new_hero_hidden")
    click("hero_eye")
    val today = java.time.LocalDate.now()
    click("hero_month")
    waitForTag("month_this")
    capture("new_month_picker")
    click("month_${if (today.monthValue == 1) 2 else today.monthValue - 1}")
    waitForTag("hero_now")
    capture("new_hero_past_month")
  }

  private fun awaitHidden() = rule.waitUntil(5_000) { container.settings.hideBalance.value }

  @Test
  fun planBudgetsGoalsAndBills() {
    prepare(AccentColor.TEAL, BackgroundMode.LIGHT)
    click("tab_plan"); waitForTag("plan_list")
    capture("new_plan_list")
    click("budget_row_Groceries"); waitForTag("budget_details_Groceries")
    capture("new_plan_row_open")
    click("view_grid"); waitForTag("budget_row_Dining")
    click("budget_row_Dining"); waitForTag("budget_details_Dining")
    capture("new_plan_grid")
    click("segment_Goals"); waitForTag("goals_summary")
    click("view_grid")
    click("goal_Summer trip"); waitForTag("goal_details_Summer trip")
    capture("new_goals_grid_open")
    click("view_list")
    capture("new_goals_list")
    click("segment_Bills"); waitForTag("bills_summary")
    click("bill_Streamly"); waitForTag("bill_details_Streamly")
    capture("new_bills")
    click("add_bill"); waitForTag("bill_name")
    capture("new_bill_sheet")
  }

  @Test
  fun darkAndLightScreens() {
    prepare(AccentColor.VIOLET, BackgroundMode.DARK)
    capture("theme_dark_home")
    scrollList("home_list", "widget_pockets")
    click("pocket_Groceries"); waitForTag("pocket_details_Groceries")
    capture("theme_dark_pocket")
    click("tab_plan"); waitForTag("plan_list")
    capture("theme_dark_plan")
    click("segment_Bills"); waitForTag("bills_summary")
    capture("theme_dark_bills")
    click("tab_home"); scrollHomeToTop(); click("open_settings"); waitForTag("settings_list")
    capture("theme_dark_settings")
    look(AccentColor.TEAL, BackgroundMode.LIGHT)
    capture("theme_light_settings")
    click("back"); waitForTag("home_list")
    capture("theme_light_home")
    click("tab_add"); waitForTag("entry_sheet")
    capture("theme_light_add")
  }

  @Test
  fun newSettingsAndSearch() {
    prepare(AccentColor.AMBER, BackgroundMode.LIGHT)
    click("open_settings"); waitForTag("settings_list")
    capture("new_settings_1")
    scrollList("settings_list", "settings_bills")
    capture("new_settings_2")
    scrollList("settings_list", "app_version")
    capture("new_settings_3")
    scrollList("settings_list", "settings_search")
    rule.onNodeWithTag("settings_search").performTextInput("back")
    waitForTag("export_backup")
    capture("new_settings_search")
  }

  @Test
  fun emptyStates() {
    waitForSeed()
    capture("empty_home")
    scrollHomeToTop()
    click("balance_card"); waitForTag("balance_page")
    capture("empty_balance")
    click("back")
    click("tab_activity"); waitForTag("day_strip")
    capture("empty_activity")
    click("tab_insights"); waitForTag("insights_list")
    capture("empty_insights")
    click("tab_plan"); waitForTag("plan_list")
    capture("empty_plan")
    rule.onNodeWithTag("plan_list").assertIsDisplayed()
  }
}

/** The welcome pages of a fresh install. */
class FirstLaunchScreenshotTest : UiTestBase() {
  private val dir = File("build/screenshots").also { it.mkdirs() }

  private fun capture(name: String) {
    rule.waitForIdle()
    val image = rule.onAllNodes(isRoot())[0].captureToImage()
    FileOutputStream(File(dir, "$name.png")).use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
  }

  @Test
  fun welcomePages() {
    waitForSeed()
    container.settings.setAccent(AccentColor.TEAL)
    container.settings.resetOnboarding()
    waitForTag("welcome_screen")
    capture("welcome_1")
    click("palette_MIDNIGHT")
    rule.waitUntil(5_000) { container.settings.palette.value != null }
    capture("welcome_1_midnight")
    click("welcome_next")
    waitForTag("welcome_balance")
    capture("welcome_2")
  }
}

/** Round 2 screens: transfers, split, lock, privacy settings, layouts and rules. */
@Config(application = TestKeepWalletApp::class, sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class Round2ScreenshotTest : UiTestBase() {
  private val dir = File("build/screenshots").also { it.mkdirs() }

  private fun capture(name: String) {
    rule.waitForIdle()
    val image = rule.onAllNodes(isRoot())[0].captureToImage()
    FileOutputStream(File(dir, "$name.png")).use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun ready() {
    waitForSeed()
    loadSampleData()
    container.settings.setAccent(AccentColor.TEAL)
    waitForTag("balance_card")
  }

  @Test
  fun transferAndSplitSheets() {
    ready()
    click("balance_card")
    waitForTag("transfer_button")
    capture("r2_balance")
    click("transfer_button")
    waitForTag("transfer_amount")
    rule.onNodeWithTag("transfer_amount").performTextInput("250")
    capture("r2_transfer_sheet")
  }

  @Test
  fun splitSheetAndActivity() {
    ready()
    click("tab_add")
    waitForTag("entry_sheet")
    click("quick_amount_100")
    click("split_button")
    waitForTag("split_amount_0")
    capture("r2_split_sheet")
  }

  @Test
  fun lockScreenAndPrivacySettings() {
    ready()
    click("open_settings")
    scrollTo("settings_list", "lock_switch")
    capture("r2_privacy_off")
    click("lock_switch")
    waitForTag("pin_new")
    capture("r2_pin_setup")
    rule.onNodeWithTag("pin_new").performTextInput("1234")
    rule.onNodeWithTag("pin_repeat").performTextInput("1234")
    click("pin_save")
    rule.waitUntil(5_000) { container.settings.lockEnabled.value }
    scrollTo("settings_list", "lock_now")
    capture("r2_privacy_on")
    click("lock_now")
    waitForTag("lock_screen")
    click("lock_key_1")
    click("lock_key_2")
    capture("r2_lock_screen")
  }

  @Test
  fun layoutsAndRules() {
    ready()
    click("customize_home")
    waitForTag("layouts_card")
    scrollList("customize_list", "layouts_card")
    capture("r2_layouts")
  }

  private fun scrollList(list: String, target: String) {
    runCatching { rule.onNodeWithTag(list).performScrollToNode(hasTestTag(target)) }
    rule.waitForIdle()
  }
}

/** Round 3 screens: converter, category looks, tags, add form actions. */
@Config(application = TestKeepWalletApp::class, sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class Round3ScreenshotTest : UiTestBase() {
  private val dir = File("build/screenshots").also { it.mkdirs() }

  private fun capture(name: String) {
    rule.waitForIdle()
    val image = rule.onAllNodes(isRoot())[0].captureToImage()
    FileOutputStream(File(dir, "$name.png")).use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun ready() {
    waitForSeed()
    loadSampleData()
    container.settings.setAccent(AccentColor.TEAL)
    waitForTag("balance_card")
  }

  @Test
  fun converter() {
    ready()
    click("tab_insights")
    click("open_converter")
    waitForTag("converter_amount")
    capture("r3_converter")
  }

  @Test
  fun categoryEditorWithEmoji() {
    ready()
    click("open_settings")
    scrollTo("settings_list", "settings_manage")
    click("settings_manage")
    waitForTag("manage_list")
    click("add_category_EXPENSE")
    waitForTag("category_name")
    click("segment_Emoji")
    waitForTag("category_emoji")
    click("emoji_☕")
    capture("r3_category_editor")
  }

  @Test
  fun seasonalPacks() {
    ready()
    click("open_settings")
    scrollTo("settings_list", "palette_row_seasonal")
    capture("r3_seasonal")
    click("palette_DIWALI")
    rule.waitForIdle()
    capture("r3_diwali_settings")
    click("back")
    waitForTag("home_list")
    capture("r3_diwali_home")
  }
}

/** Round 4: the polished look in light and dark, the new settings, the new startup card. */
@Config(application = TestKeepWalletApp::class, sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class PolishScreenshotTest : UiTestBase() {
  private val dir = File("build/screenshots").also { it.mkdirs() }

  private fun capture(name: String) {
    rule.waitForIdle()
    val image = rule.onAllNodes(isRoot())[0].captureToImage()
    FileOutputStream(File(dir, "$name.png")).use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun tour(prefix: String, mode: BackgroundMode) {
    waitForSeed()
    loadSampleData()
    container.settings.setAccent(AccentColor.TEAL)
    container.settings.setBackgroundMode(mode)
    waitForTag("balance_card")
    capture("${prefix}_home")
    rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("widget_recent"))
    rule.waitForIdle()
    capture("${prefix}_home_2")
    click("tab_activity"); waitForTag("activity_list"); capture("${prefix}_activity")
    click("tab_insights"); waitForTag("insights_list"); capture("${prefix}_insights")
    click("tab_plan"); waitForTag("plan_list"); capture("${prefix}_plan")
    click("tab_home"); click("open_settings"); waitForTag("settings_list"); capture("${prefix}_settings_1")
    rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("group_money")); rule.waitForIdle(); capture("${prefix}_settings_2")
    click("back"); waitForTag("home_list")
    click("tab_add"); waitForTag("entry_sheet"); capture("${prefix}_add")
  }

  @Test fun light() = tour("p_light", BackgroundMode.LIGHT)
  @Test fun dark() = tour("p_dark", BackgroundMode.DARK)
}


/** Pictures for the README: the main screens in light and dark with sample data. */
@Config(application = TestKeepWalletApp::class, sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReadmeScreenshotTest : UiTestBase() {
  private val dir = File("build/readme").also { it.mkdirs() }

  private fun capture(name: String) {
    rule.waitForIdle()
    val image = rule.onAllNodes(isRoot())[0].captureToImage()
    FileOutputStream(File(dir, "$name.png")).use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun prepare(mode: BackgroundMode) {
    waitForSeed()
    loadSampleData()
    container.settings.setBackgroundMode(mode)
    waitForTag("balance_card")
  }

  @Test
  fun light() {
    prepare(BackgroundMode.LIGHT)
    capture("home-light")
    click("tab_activity"); waitForTag("activity_list"); capture("activity")
    click("tab_insights"); waitForTag("insights_list"); capture("insights")
    click("tab_plan"); waitForTag("plan_list"); capture("plan")
    click("tab_home"); click("open_alerts"); waitForTag("alerts_list"); capture("notifications")
    click("back"); click("tab_insights"); click("open_review"); waitForTag("review_list"); capture("review")
    click("back"); click("tab_home"); click("tab_add"); waitForTag("entry_sheet"); capture("add-expense")
  }

  @Test
  fun dark() {
    prepare(BackgroundMode.DARK)
    capture("home-dark")
    click("open_settings"); waitForTag("settings_list"); capture("settings")
  }

  @Test
  fun welcome() {
    waitForSeed()
    container.settings.setBackgroundMode(BackgroundMode.LIGHT)
    container.settings.resetOnboarding()
    waitForTag("welcome_screen")
    capture("welcome")
  }
}
