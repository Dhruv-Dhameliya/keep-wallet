package com.keepwallet.app

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import com.keepwallet.app.data.AppPalette
import com.keepwallet.app.data.CurrencyRates
import com.keepwallet.app.data.RateTable
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.widget.WalletWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf

private fun UiTestBase.viewModel(): MainViewModel = ViewModelProvider(rule.activity)[MainViewModel::class.java]

class ConverterFlowTest : UiTestBase() {
  private fun openConverter() {
    click("tab_insights")
    waitForTag("insights_list")
    click("open_converter")
    waitForTag("converter_amount")
  }

  @Test
  fun convertsWithTheBuiltInRatesAndSwaps() {
    waitForSeed()
    openConverter()
    // the app's currency is "$", so it starts from US dollars
    awaitEquals({ "100.00 USD =" }, { textOf("converter_from_text") })
    click("converter_to")
    click("converter_to_INR")
    awaitEquals({ "8,800.00 INR" }, { textOf("converter_result") })
    awaitEquals({ "1 USD = 88.00 INR" }, { textOf("converter_rate") })
    click("converter_swap")
    awaitEquals({ "100.00 INR =" }, { textOf("converter_from_text") })
    awaitEquals({ "1.14 USD" }, { textOf("converter_result") })
    rule.onNodeWithTag("converter_rates_info").assertTextContains("Built-in rates", substring = true)
  }

  @Test
  fun aTypedAmountIsConvertedAndNonsenseIsNot() {
    waitForSeed()
    openConverter()
    rule.onNodeWithTag("converter_amount").performTextReplacement("250.5")
    awaitEquals({ "250.50 USD =" }, { textOf("converter_from_text") })
    rule.onNodeWithTag("converter_amount").performTextReplacement("")
    awaitEquals({ "Enter an amount" }, { textOf("converter_from_text") })
    awaitEquals({ "–" }, { textOf("converter_result") })
  }

  @Test
  fun updatingRatesUsesTheFetchedTableAndSavesIt() {
    waitForSeed()
    container.rateFetcher = { now -> RateTable(CurrencyRates.builtIn + ("INR" to 100.0), now, builtIn = false) }
    openConverter()
    click("converter_to")
    click("converter_to_INR")
    click("converter_update")
    awaitEquals({ "10,000.00 INR" }, { textOf("converter_result") })
    rule.onNodeWithTag("converter_status").assertTextContains("updated", substring = true, ignoreCase = true)
    assertFalse(container.settings.rates.value.builtIn)
    // a fresh settings object reads the saved rates back
    val again = com.keepwallet.app.data.SettingsStore(rule.activity, firstRunEnabled = false)
    assertEquals(100.0, again.rates.value.perUsd.getValue("INR"), 1e-9)
  }

  @Test
  fun aFailedUpdateKeepsTheOldRatesAndSaysSo() {
    waitForSeed()
    container.rateFetcher = { null }
    openConverter()
    click("converter_update")
    waitForTag("converter_status")
    rule.onNodeWithTag("converter_status").assertTextContains("Could not reach", substring = true)
    assertTrue(container.settings.rates.value.builtIn)
  }

  @Test
  fun theConverterIsAlsoInSettingsAndNeverOnHome() {
    waitForSeed()
    assertFalse(tagExists("open_converter"))
    click("open_settings")
    click("settings_converter")
    waitForTag("converter_amount")
    click("back")
    waitForTag("settings_list")
  }
}

class SeasonalPackTest : UiTestBase() {
  @Test
  fun aSeasonalPackSetsItsOwnColoursAndBackground() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "palette_DIWALI")
    click("palette_DIWALI")
    rule.waitUntil(5_000) { container.settings.palette.value == AppPalette.DIWALI }
    assertEquals(AppPalette.DIWALI.seed!!.toInt(), container.settings.customAccent.value)
    assertEquals(AppPalette.DIWALI.tertiary.toInt(), container.settings.highlight.value)
    // choosing a classic accent afterwards leaves the pack behind
    scrollTo("settings_list", "accent_BLUE")
    click("accent_BLUE")
    rule.waitUntil(5_000) { container.settings.palette.value == null && container.settings.customAccent.value == null }
  }

  @Test
  fun everyMonthHasAPackAndEveryPackIsOffered() {
    (1..12).forEach { assertEquals(com.keepwallet.app.data.PACK_SEASONAL, AppPalette.suggestedFor(it).pack) }
    assertTrue(AppPalette.seasonal.size >= 8)
    assertEquals(AppPalette.entries.size, AppPalette.classic.size + AppPalette.seasonal.size)
  }

  @Test
  fun theColourStyleChipsChangeTheSchemeAndWallpaperColoursCanBeSwitched() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "color_style_BALANCED")
    click("color_style_BALANCED")
    rule.waitUntil(5_000) { container.settings.colorStyle.value == com.keepwallet.app.data.ColorStyle.BALANCED }
    scrollTo("settings_list", "system_colors_switch")
    click("system_colors_switch")
    rule.waitUntil(5_000) { container.settings.systemColors.value }
  }
}

class CategoryLookTest : UiTestBase() {
  @Test
  fun anEmojiCanReplaceTheIcon() {
    waitForSeed()
    click("open_settings")
    scrollTo("settings_list", "settings_manage")
    click("settings_manage")
    waitForTag("manage_list")
    click("add_category_EXPENSE")
    waitForTag("category_name")
    rule.onNodeWithTag("category_name").performTextInput("Coffee")
    click("segment_Emoji")
    waitForTag("category_emoji")
    click("emoji_☕")
    click("category_save")
    rule.waitUntil(5_000) { categories().any { it.name == "Coffee" } }
    val saved = categories().first { it.name == "Coffee" }
    assertEquals("☕", saved.emoji)
    assertEquals(null, saved.photoFile)
  }

  @Test
  fun aPastedEmojiKeepsOnlyTheFirstSymbol() {
    assertEquals("🍔", com.keepwallet.app.ui.screens.firstEmoji("🍔🍕"))
    assertEquals("❤️", com.keepwallet.app.ui.screens.firstEmoji(" ❤️!"))
    assertEquals(null, com.keepwallet.app.ui.screens.firstEmoji("   "))
  }

  @Test
  fun backToIconRemovesTheEmoji() {
    waitForSeed()
    val groceries = categories().first { it.name == "Groceries" }
    runBlocking { repository.saveCategory(groceries.copy(emoji = "🥦")) }
    click("open_settings")
    scrollTo("settings_list", "settings_manage")
    click("settings_manage")
    waitForTag("category_row_Groceries")
    click("category_row_Groceries")
    waitForTag("category_look")
    click("segment_Icon")
    click("category_save")
    rule.waitUntil(5_000) { categories().first { it.id == groceries.id }.emoji == null }
  }
}

class ScanAndVoiceTest : UiTestBase() {
  private val receipt = "SUNRISE SUPERMARKET\nDate: 11/10/2026\nMilk 2.50\nSub Total 16.30\nGST 0.82\nTOTAL 17.12\nCash 20.00"

  @Test
  fun aReadReceiptFillsTheAmountShopAndDate() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    rule.runOnUiThread { viewModel().applyReceiptText(receipt) }
    rule.waitUntil(5_000) { viewModel().editor.value?.amountText == "17.12" }
    val editor = viewModel().editor.value!!
    assertEquals("Sunrise Supermarket", editor.merchant)
    assertEquals(java.time.LocalDate.of(2026, 10, 11), editor.date)
    amountNode().assertTextEquals("17.12")
  }

  @Test
  fun anUnreadableReceiptChangesNothing() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    rule.runOnUiThread { viewModel().applyReceiptText("???") }
    rule.waitForIdle()
    assertEquals("0", viewModel().editor.value!!.amountText)
  }

  @Test
  fun scanningAPhotoReadsItThroughTheTextReader() {
    waitForSeed()
    container.textReader = com.keepwallet.app.data.TextReader { receipt }
    click("tab_add")
    waitForTag("entry_sheet")
    val file = java.io.File(rule.activity.cacheDir, "scan.jpg")
    val bitmap = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
    rule.runOnUiThread { viewModel().scanReceipt(android.net.Uri.fromFile(file)) }
    rule.waitUntil(20_000) { viewModel().editor.value?.amountText == "17.12" }
    assertFalse(viewModel().scanning.value)
  }

  @Test
  fun aSpokenSentenceFillsTheForm() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    var ok = false
    rule.runOnUiThread { ok = viewModel().applyVoiceText("spent 250 on lunch at Subway") }
    assertTrue(ok)
    val editor = viewModel().editor.value!!
    assertEquals("250", editor.amountText)
    assertEquals(categories().first { it.name == "Dining" }.id, editor.categoryId)
    assertEquals(TxType.EXPENSE, editor.type)
  }

  @Test
  fun spokenIncomeSwitchesTheFormToIncome() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    rule.runOnUiThread { viewModel().applyVoiceText("received 50000 salary") }
    val editor = viewModel().editor.value!!
    assertEquals(TxType.INCOME, editor.type)
    assertEquals("50000", editor.amountText)
    assertEquals(categories().first { it.name == "Salary" }.id, editor.categoryId)
  }

  @Test
  fun aSentenceWithoutAnAmountSaysSoAndLeavesTheForm() {
    waitForSeed()
    click("tab_add")
    waitForTag("entry_sheet")
    var ok = true
    rule.runOnUiThread { ok = viewModel().applyVoiceText("lunch at Subway") }
    assertFalse(ok)
    assertEquals("0", viewModel().editor.value!!.amountText)
  }

  @Test
  fun theMicrophoneButtonAsksTheSystemRecogniser() {
    waitForSeed()
    click("tab_add")
    waitForTag("voice_entry")
    click("voice_entry")
    rule.waitForIdle()
    val started = shadowOf(rule.activity).nextStartedActivityForResult
    assertNotNull("the recogniser should have been asked", started)
    assertEquals(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH, started.intent.action)
    assertTrue(started.intent.getBooleanExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
  }

  @Test
  fun theScanAndMicButtonsAreOnlyOnANewEntry() {
    waitForSeed()
    runBlocking { repository.saveTransaction(TransactionEntity(type = TxType.EXPENSE, amountMinor = 500, merchant = "X", categoryId = categories().first().id, accountId = accounts().first().id, timestamp = System.currentTimeMillis())) }
    click("tab_add")
    waitForTag("scan_receipt")
    assertTrue(tagExists("voice_entry"))
    click("cancel_entry")
    waitForGone("entry_sheet")
    click("tab_activity")
    val id = transactions().first().id
    waitForTag("tx_$id")
    click("tx_$id")
    click("edit_entry_btn")
    waitForTag("entry_sheet")
    assertFalse(tagExists("scan_receipt"))
    assertFalse(tagExists("voice_entry"))
  }
}

class ShortcutAndWidgetTest : UiTestBase() {
  private fun deliver(action: String, configure: Intent.() -> Unit = {}) {
    val intent = Intent(rule.activity, MainActivity::class.java).setAction(action).apply(configure)
    rule.runOnUiThread { rule.activity.handleIntent(intent) }
  }

  @Test
  fun theAddIncomeShortcutOpensAnIncomeForm() {
    waitForSeed()
    deliver(MainActivity.ACTION_ADD_INCOME)
    waitForTag("entry_sheet")
    rule.waitUntil(5_000) { viewModel().editor.value?.type == TxType.INCOME }
  }

  @Test
  fun theAddExpenseShortcutAndWidgetButtonOpenAnExpenseForm() {
    waitForSeed()
    deliver(MainActivity.ACTION_ADD_EXPENSE)
    waitForTag("entry_sheet")
    assertEquals(TxType.EXPENSE, viewModel().editor.value?.type)
  }

  @Test
  fun theScanShortcutOpensTheFormAndTheCamera() {
    waitForSeed()
    deliver(MainActivity.ACTION_SCAN_RECEIPT)
    waitForTag("entry_sheet")
    // The form asks for the camera as soon as it is up and the request is used up. (Robolectric on Windows cannot
    // resolve the FileProvider path, so the camera app itself is not started here; the shutter is out of reach of a test.)
    rule.waitUntil(10_000) { viewModel().pendingLaunch.value == null }
    assertNull(viewModel().pendingLaunch.value)
  }

  @Test
  fun theVoiceShortcutOpensTheFormAndTheRecogniser() {
    waitForSeed()
    deliver(MainActivity.ACTION_VOICE_ENTRY)
    waitForTag("entry_sheet")
    rule.waitUntil(10_000) { shadowOf(rule.activity).peekNextStartedActivityForResult() != null }
    assertEquals(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH, shadowOf(rule.activity).peekNextStartedActivityForResult().intent.action)
  }

  @Test
  fun anAddAgainShortcutPrefillsTheEntry() {
    waitForSeed()
    val category = categories().first { it.type == TxType.EXPENSE }
    val account = accounts().last()
    deliver(MainActivity.ACTION_QUICK_ENTRY) {
      putExtra(MainActivity.EXTRA_MERCHANT, "Metro")
      putExtra(MainActivity.EXTRA_AMOUNT_MINOR, 320L)
      putExtra(MainActivity.EXTRA_CATEGORY_ID, category.id)
      putExtra(MainActivity.EXTRA_ACCOUNT_ID, account.id)
    }
    waitForTag("entry_sheet")
    rule.waitUntil(5_000) { viewModel().editor.value?.merchant == "Metro" }
    val editor = viewModel().editor.value!!
    assertEquals("3.2", editor.amountText)
    assertEquals(category.id, editor.categoryId)
    assertEquals(account.id, editor.accountId)
  }

  @Test
  fun anAddAgainShortcutForADeletedCategoryFallsBackToADefault() {
    waitForSeed()
    deliver(MainActivity.ACTION_QUICK_ENTRY) {
      putExtra(MainActivity.EXTRA_MERCHANT, "Old")
      putExtra(MainActivity.EXTRA_AMOUNT_MINOR, 100L)
      putExtra(MainActivity.EXTRA_CATEGORY_ID, 99_999L)
    }
    waitForTag("entry_sheet")
    rule.waitUntil(5_000) { viewModel().editor.value?.merchant == "Old" }
    assertNotNull(viewModel().editor.value!!.categoryId)
  }

  @Test
  fun theWidgetShowsThisMonthsSpendingAndTheBudget() {
    waitForSeed()
    loadSampleData()
    waitForTag("balance_card")
    val content = runBlocking { WalletWidget.content(rule.activity) }
    assertTrue(content.title, content.title.endsWith("spending"))
    assertTrue(content.amount, content.amount.startsWith("$"))
    assertTrue(content.sub, content.sub.contains("left") || content.sub.contains("over"))
    assertTrue(content.percent in 1..100)
    // the layout inflates and carries the text
    val view = WalletWidget.views(rule.activity, content).apply(rule.activity, android.widget.FrameLayout(rule.activity))
    assertEquals(content.amount, view.findViewById<android.widget.TextView>(R.id.widget_amount).text.toString())
  }

  @Test
  fun theWidgetShowsNoAmountsWhileTheAppLockIsOn() {
    waitForSeed()
    loadSampleData()
    container.settings.enableLock("1234", false)
    val content = runBlocking { WalletWidget.content(rule.activity) }
    assertEquals("Locked", content.amount)
    assertEquals(0, content.percent)
  }

  @Test
  fun theManifestDeclaresTheWidgetAndTheShortcuts() {
    val pm = rule.activity.packageManager
    val receivers = pm.getPackageInfo(rule.activity.packageName, PackageManager.GET_RECEIVERS).receivers.orEmpty().map { it.name }
    assertTrue(receivers.toString(), receivers.any { it.endsWith("WalletWidgetProvider") })
    val shortcutsMeta = pm.getActivityInfo(android.content.ComponentName(rule.activity, MainActivity::class.java), PackageManager.GET_META_DATA).metaData
    assertNotNull(shortcutsMeta?.get("android.app.shortcuts"))
  }
}

class CelebrationTest : UiTestBase() {
  private fun goal(name: String, target: Long, saved: Long) = runBlocking {
    val id = repository.saveGoal(com.keepwallet.app.data.GoalEntity(name = name, targetMinor = target, savedMinor = saved, iconKey = "travel", color = 0xFF0D9488.toInt()))
    repository.goals.first().first { it.id == id }
  }

  @Test
  fun reachingAGoalShowsTheMessageAndConfetti() {
    waitForSeed()
    val trip = goal("Trip", 10_000, 9_000)
    assertFalse(tagExists("confetti"))
    rule.runOnUiThread { viewModel().addToGoal(trip, 1_000) }
    waitForText("Goal reached: Trip")
    waitForTag("confetti")
  }

  @Test
  fun aPartlyFundedGoalOnlyGetsTheUsualMessage() {
    waitForSeed()
    val bike = goal("Bike", 100_000, 0)
    rule.runOnUiThread { viewModel().addToGoal(bike, 1_000) }
    waitForText("Added")
    assertFalse(tagExists("confetti"))
  }

  @Test
  fun topUpOfAnAlreadyFundedGoalDoesNotCelebrateAgain() {
    waitForSeed()
    val done = goal("Done", 10_000, 10_000)
    rule.runOnUiThread { viewModel().addToGoal(done, 500) }
    waitForText("fully funded")
    assertFalse(tagExists("confetti"))
  }
}

class NewLayoutStructureTest : UiTestBase() {
  @Test
  fun homeHasTheMonthOnTheLeftAndCustomizeThenSettingsOnTheRight() {
    waitForSeed()
    fun left(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.left
    waitForTag("hero_month")
    assertTrue(left("hero_month") < left("customize_home"))
    assertTrue(left("customize_home") < left("open_settings"))
    // the month is plain text that still opens the picker
    click("hero_month")
    waitForTag("month_this")
  }

  @Test
  fun theAddButtonAndNavigationBarBelongToTheTabsOnly() {
    waitForSeed()
    assertTrue(tagExists("bottom_bar"))
    assertTrue(tagExists("tab_add"))
    click("open_settings")
    waitForTag("settings_list")
    assertFalse(tagExists("bottom_bar"))
    assertFalse(tagExists("tab_add"))
  }

  @Test
  fun everyTabHasATopAppBarTitle() {
    waitForSeed()
    listOf("tab_activity" to "Activity", "tab_insights" to "Insights", "tab_plan" to "Plan").forEach { (tab, title) ->
      click(tab)
      waitForText(title)
    }
  }
}
