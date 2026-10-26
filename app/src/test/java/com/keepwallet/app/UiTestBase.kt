package com.keepwallet.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.DashboardWidgetEntity
import com.keepwallet.app.data.DetectedEntryEntity
import com.keepwallet.app.data.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Shared plumbing for Robolectric + Compose tests that drive the real MainActivity. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestKeepWalletApp::class, sdk = [35], qualifiers = "w411dp-h1400dp-xhdpi")
abstract class UiTestBase {
  @get:Rule
  val rule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> = createAndroidComposeRule<MainActivity>()

  val container: AppContainer get() = (rule.activity.application as KeepWalletApp).container
  val repository get() = container.repository

  fun waitForTag(tag: String, timeoutMs: Long = 20_000) {
    rule.waitUntil(timeoutMs) { rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
  }

  fun waitForText(text: String, timeoutMs: Long = 20_000) {
    rule.waitUntil(timeoutMs) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
  }

  fun waitForGone(tag: String, timeoutMs: Long = 20_000) {
    rule.waitUntil(timeoutMs) { rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
  }

  fun tagExists(tag: String): Boolean = rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  fun textExists(text: String): Boolean =
    rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

  private val lists = listOf("settings_list", "detection_list", "customize_list", "manage_list", "balance_list", "plan_list", "insights_list", "activity_list", "home_list", "converter_list")

  /** Like a thumb: when the target is not on screen (or not even composed yet), scroll whichever list is showing until it is. */
  private fun reveal(tag: String) {
    lists.forEach { list ->
      if (!tagExists(list)) return@forEach
      if (!tagExists(tag)) {
        runCatching { scrollTo(list, tag) }
        rule.waitForIdle()
      } else {
        // Only a row that really lives in this list is scrolled for; a button on a page laid over it is not.
        val inList = rule.onAllNodes(hasTestTag(tag).and(hasAnyAncestor(hasTestTag(list))), useUnmergedTree = true).fetchSemanticsNodes()
        if (inList.isEmpty()) return@forEach
        val listBounds = rule.onNodeWithTag(list).fetchSemanticsNode().boundsInRoot
        val target = inList.first().boundsInRoot
        val inside = target.left >= listBounds.left - 1f && target.right <= listBounds.right + 1f && target.top > listBounds.top - 1f && target.bottom < listBounds.bottom + 1f
        if (!inside) {
          runCatching { nudge(list, tag) }
          rule.waitForIdle()
        }
      }
    }
  }

  fun click(tag: String) {
    reveal(tag)
    waitForTag(tag)
    rule.onNodeWithTag(tag).performClick()
  }

  fun clickText(text: String) {
    waitForText(text)
    rule.onNodeWithText(text).performClick()
  }

  /** Types an amount on the on-screen keypad, e.g. "12.5". */
  fun typeAmount(text: String) {
    text.forEach { char -> click("key_$char") }
  }

  fun waitForSeed() {
    rule.waitUntil(10_000) { runBlocking { repository.categories.first().isNotEmpty() && repository.widgets.first().isNotEmpty() } }
    waitForTag("home_list")
  }

  fun transactions(): List<TransactionEntity> = runBlocking { repository.transactions.first() }
  fun categories(): List<CategoryEntity> = runBlocking { repository.categories.first() }
  fun accounts(): List<AccountEntity> = runBlocking { repository.accounts.first() }
  fun widgets(): List<DashboardWidgetEntity> = runBlocking { repository.widgets.first() }
  fun detected(): List<DetectedEntryEntity> = runBlocking { repository.detectedEntries.first() }

  protected fun loadSampleData() {
    runBlocking { repository.replaceWithSampleData(LocalDate.now()) }
    rule.waitForIdle()
  }
}

fun androidx.compose.ui.test.SemanticsNodeInteraction.performTextInputCompat(text: String) {
  performTextInput(text)
}

fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsEnabledCompat() = assertIsEnabled()

fun androidx.compose.ui.test.SemanticsNodeInteraction.assertIsNotEnabledCompat() = assertIsNotEnabled()

/** The big amount in the entry sheet; its text lives inside a merged accessibility node. */
fun UiTestBase.amountNode() = rule.onNodeWithTag("amount_display", useUnmergedTree = true)

/**
 * Lists compose lazily, so scroll an off-screen item into existence and then nudge the list until the middle of it
 * is really on screen (a tall card can start at the top and still hold the row below the fold).
 */
fun UiTestBase.scrollTo(listTag: String, targetTag: String) {
  rule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(targetTag))
  rule.waitForIdle()
  nudge(listTag, targetTag)
}

/** Swipes the list a little until the middle of the target is well inside it, clear of the snackbar zone. */
fun UiTestBase.nudge(listTag: String, targetTag: String) {
  repeat(10) {
    val list = rule.onNodeWithTag(listTag).fetchSemanticsNode().boundsInRoot
    val target = rule.onAllNodes(hasTestTag(targetTag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot ?: return
    val middle = (target.top + target.bottom) / 2f
    when {
      middle > list.bottom - list.height * 0.22f -> rule.onNodeWithTag(listTag).performTouchInput { swipeUp(startY = height * 0.8f, endY = height * 0.45f) }
      middle < list.top + list.height * 0.06f -> rule.onNodeWithTag(listTag).performTouchInput { swipeDown(startY = height * 0.3f, endY = height * 0.6f) }
      else -> return
    }
    rule.waitForIdle()
  }
}

/** Polls until [actual] equals [expected], then fails with both values instead of a bare timeout. */
fun UiTestBase.awaitEquals(expected: () -> String, actual: () -> String, timeoutMs: Long = 8_000) {
  val deadline = System.currentTimeMillis() + timeoutMs
  while (System.currentTimeMillis() < deadline) {
    rule.waitForIdle()
    if (expected() == actual()) return
    Thread.sleep(50)
  }
  org.junit.Assert.fail("expected <${expected()}> but was <${actual()}>")
}

/** The visible text of a node, looked up in the unmerged tree (cards that are clickable merge their children). */
fun UiTestBase.textOf(tag: String): String = runCatching {
  rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
    .config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
}.getOrDefault("")

fun UiTestBase.heroText(): String = textOf("hero_amount")

/** Home keeps its scroll position between tabs now, so tests that need the header or the card jump back to the top. */
fun UiTestBase.scrollHomeToTop() {
  rule.onNodeWithTag("home_list").performScrollToIndex(0)
  rule.waitForIdle()
}
