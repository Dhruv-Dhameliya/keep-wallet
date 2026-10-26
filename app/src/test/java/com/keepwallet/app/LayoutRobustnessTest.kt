package com.keepwallet.app

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.layout.boundsInRoot
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Hostile conditions: a small phone, a 1.5x system font size and eye-wateringly large amounts.
 * The entry form must keep Save on screen and nothing may be pushed out of its container.
 */
@Config(application = TestKeepWalletApp::class, sdk = [35], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.5f)
class LayoutRobustnessTest : UiTestBase() {
  private val dir = File("build/screenshots").also { it.mkdirs() }

  private fun capture(name: String) {
    rule.waitForIdle()
    val image = rule.onAllNodes(isRoot())[0].captureToImage()
    FileOutputStream(File(dir, "$name.png")).use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun bounds(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

  @Test
  fun entryFormKeepsSaveVisibleOnASmallPhoneWithLargeText() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    typeAmount("123456789.99")
    capture("small_entry")
    // The app draws at 95% of the system density, so compare in pixels, not in dp.
    val screenHeight = rule.activity.resources.displayMetrics.heightPixels.toFloat()
    val save = rule.onNodeWithTag("save_entry", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue("Save ($save) must end on screen ($screenHeight px)", save.bottom <= screenHeight)
    assertTrue("keypad must sit above Save", rule.onNodeWithTag("key_1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top < save.top)
    rule.onNodeWithTag("save_entry").assertIsDisplayed()
    click("category_field")
    waitForTag("category_grid")
    capture("small_entry_grid")
  }

  @Test
  fun hugeAmountsStayInsideTheirCards() {
    waitForSeed()
    val bank = accounts().first { it.kind == AccountKind.BANK }
    val groceries = categories().first { it.name == "Groceries" }
    runBlocking {
      repository.saveAccount(bank, 98_765_432_101L)
      repository.saveTransaction(
        TransactionEntity(
          type = TxType.EXPENSE, amountMinor = 12_345_678_901, merchant = "A merchant with a rather long name indeed",
          categoryId = groceries.id, accountId = bank.id, timestamp = System.currentTimeMillis(),
          note = "Note that is also fairly long so that it has to wrap or truncate somewhere sensible"
        )
      )
    }
    waitForText("123,456,789")
    capture("small_home_huge")
    val card = bounds("balance_card")
    val amount = bounds("hero_amount")
    assertTrue("amount ($amount) must stay inside the card ($card)", amount.right <= card.right && amount.left >= card.left)

    click("balance_card")
    waitForTag("balance_page")
    capture("small_balance_huge")
    click("back")

    click("tab_activity")
    waitForTag("activity_list")
    capture("small_activity_huge")
    val id = transactions().first().id
    click("tx_$id")
    waitForTag("entry_details")
    capture("small_details_huge")
  }

  @Test
  fun theNewSectionsAndPlanPartsFitTheSmallestScreen() {
    waitForSeed()
    loadSampleData()
    runBlocking {
      listOf(
        com.keepwallet.app.data.WidgetKind.HIGHLIGHTS, com.keepwallet.app.data.WidgetKind.UPCOMING_BILLS,
        com.keepwallet.app.data.WidgetKind.SAVINGS_GOALS, com.keepwallet.app.data.WidgetKind.QUICK_STATS
      ).forEach { kind -> repository.setWidgetEnabled(widgets().first { it.kind == kind }.id, true) }
    }
    waitForTag("balance_card")
    // everything on the card stays inside it, even with big text
    val card = bounds("balance_card")
    listOf("hero_eye", "hero_amount", "hero_income").forEach { tag ->
      val b = bounds(tag)
      assertTrue("$tag ($b) must stay inside the card ($card)", b.left >= card.left && b.right <= card.right && b.top >= card.top && b.bottom <= card.bottom)
    }
    capture("small_hero")
    scrollTo("home_list", "widget_highlights"); capture("small_highlights")
    scrollTo("home_list", "widget_upcoming"); capture("small_upcoming")
    scrollTo("home_list", "widget_goals"); capture("small_goals_widget")

    click("tab_plan"); waitForTag("plan_list"); capture("small_plan_budgets")
    click("view_grid"); scrollTo("plan_list", "budget_row_Groceries"); click("budget_row_Groceries"); waitForTag("budget_details_Groceries"); capture("small_plan_grid")
    click("segment_Goals"); waitForTag("goals_summary"); scrollTo("plan_list", "goal_Summer trip"); click("goal_Summer trip"); waitForTag("goal_details_Summer trip"); capture("small_goals")
    click("segment_Bills"); waitForTag("bills_summary"); scrollTo("plan_list", "bill_Streamly"); click("bill_Streamly"); waitForTag("bill_details_Streamly"); capture("small_bills")
    scrollTo("plan_list", "add_bill"); click("add_bill"); waitForTag("bill_name"); capture("small_bill_sheet")
  }

  @Test
  fun everyScreenRendersOnTheSmallestScreenWithSampleData() {
    waitForSeed()
    loadSampleData()
    waitForTag("balance_card")
    capture("small_home")
    click("balance_card"); waitForTag("balance_page"); capture("small_balance"); click("back")
    click("tab_activity"); waitForTag("activity_list"); capture("small_activity")
    click("tab_insights"); waitForTag("insights_list"); capture("small_insights")
    click("tab_plan"); waitForTag("plan_list"); capture("small_plan")
    click("tab_home"); scrollHomeToTop(); click("customize_home"); waitForTag("customize_list"); capture("small_customize"); click("back")
    click("open_settings"); waitForTag("settings_list"); capture("small_settings")
  }
}
