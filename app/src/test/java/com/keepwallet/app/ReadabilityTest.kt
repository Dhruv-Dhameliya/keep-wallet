package com.keepwallet.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.keepwallet.app.data.AccentColor
import com.keepwallet.app.data.AppPalette
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.WidgetKind
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Guards against unreadable text: for every background mode, accent, palette and custom colour, real rendered text
 * must contrast with what's behind it (WCAG AA is 4.5:1; large text only needs 3:1).
 */
@Config(application = TestKeepWalletApp::class, sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ReadabilityTest : UiTestBase() {

  private fun luminance(c: Color): Double {
    fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
  }

  private fun contrast(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
  }

  /** Strongest contrast found between the node's corner (its background) and any pixel inside its bounds. */
  private fun textContrast(tag: String): Double {
    rule.waitForIdle()
    val pixels = rule.onAllNodes(isRoot())[0].captureToImage().toPixelMap()
    val b = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()
    val density = rule.density.density
    val left = (b.left.value * density).toInt().coerceIn(0, pixels.width - 1)
    val top = (b.top.value * density).toInt().coerceIn(0, pixels.height - 1)
    val right = (b.right.value * density).toInt().coerceIn(left + 1, pixels.width)
    val bottom = (b.bottom.value * density).toInt().coerceIn(top + 1, pixels.height)
    val background = pixels[left, top]
    var best = 1.0
    for (x in left until right) for (y in top until bottom) best = max(best, contrast(background, pixels[x, y]))
    return best
  }
  private val checks = listOf("home_title" to 4.5, "chart_total_%d" to 4.5, "hero_amount" to 3.0, "month_spent" to 4.5)

  private fun check(label: String, chartId: Long, failures: MutableList<String>) {
    rule.waitForIdle()
    for ((pattern, minimum) in checks) {
      val tag = pattern.replace("%d", chartId.toString())
      if (!tagExists(tag)) continue
      // the page is taller than the screen: bring the text into view first, as a thumb would
      runCatching { rule.onNodeWithTag("home_list").performScrollToNode(hasTestTag(tag)) }
      rule.waitForIdle()
      val ratio = textContrast(tag)
      if (ratio < minimum) failures += "$tag on $label: %.1f:1 (needs $minimum)".format(ratio)
    }
  }

  @Test
  fun headlineTextIsReadableOnEveryBackgroundAccentPaletteAndCustomColour() {
    waitForSeed()
    loadSampleData()
    val chartId = widgets().first { it.kind == WidgetKind.SPENDING_CHART }.id
    waitForTag("home_title")
    val failures = mutableListOf<String>()
    val modes = BackgroundMode.entries.filter { it != BackgroundMode.SYSTEM }
    // every background with a spread of accents
    for (mode in modes) for (accent in listOf(AccentColor.TEAL, AccentColor.VIOLET, AccentColor.GRAPHITE, AccentColor.AMBER, AccentColor.ROSE)) {
      container.settings.setBackgroundMode(mode)
      container.settings.setAccent(accent)
      check("$mode $accent", chartId, failures)
    }
    // every ready-made palette
    for (palette in AppPalette.entries) {
      container.settings.applyPalette(palette)
      check("palette ${palette.name}", chartId, failures)
    }
    // custom colours across the whole hue wheel, on a light and a dark background
    for (mode in listOf(BackgroundMode.LIGHT, BackgroundMode.DARK)) for (hue in 0 until 360 step 40) {
      container.settings.setBackgroundMode(mode)
      container.settings.setCustomAccent(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.72f, 0.62f)))
      check("custom hue $hue on $mode", chartId, failures)
    }
    assertTrue("unreadable text:\n" + failures.joinToString("\n"), failures.isEmpty())
  }
}
