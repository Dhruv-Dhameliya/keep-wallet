package com.keepwallet.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.ui.screens.SplashCard
import com.keepwallet.app.ui.theme.KeepWalletTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Renders the first-launch logo card in light and dark so both can be reviewed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SplashScreenshotTest {
  @get:Rule
  val rule = createComposeRule()
  private val dir = File("build/screenshots").also { it.mkdirs() }

  private fun shoot(name: String, accent: Long, background: BackgroundMode) {
    var finished = false
    rule.setContent {
      KeepWalletTheme(accent = Color(accent), highlight = null, background = background) {
        SplashCard(onFinished = { finished = true })
      }
    }
    rule.mainClock.advanceTimeBy(900)
    rule.waitForIdle()
    val image = rule.onAllNodes(isRoot())[0].captureToImage()
    FileOutputStream(File(dir, "$name.png")).use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    rule.mainClock.advanceTimeBy(2_000)
    org.junit.Assert.assertTrue("the card must hand over to the app by itself", finished)
  }

  @Test
  fun lightSplash() = shoot("splash_light", 0xFF16655E, BackgroundMode.LIGHT)

  @Test
  fun darkSplash() = shoot("splash_dark", 0xFF6D4FD6, BackgroundMode.DARK)
}
