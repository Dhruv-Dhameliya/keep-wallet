package com.keepwallet.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Draws the launcher icon the way a launcher would (background + foreground inside a mask) so it can be reviewed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class LogoRenderTest {
  private val dir = File("build/screenshots").also { it.mkdirs() }

  private fun render(size: Int, mask: (Path, Float) -> Unit, name: String, monochrome: Boolean = false) {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(0xFFE9EEED.toInt())
    val path = Path().also { mask(it, size.toFloat()) }
    canvas.save()
    canvas.clipPath(path)
    canvas.drawColor(if (monochrome) 0xFFD9E3E1.toInt() else 0xFFFFFFFF.toInt())
    // adaptive icons are drawn on a 108 grid and the visible window is the central 72
    val layer = ResourcesCompat.getDrawable(context.resources, if (monochrome) R.drawable.ic_launcher_mono else R.drawable.ic_launcher_fg, null)!!
    val inset = (size * (108f - 72f) / 2f / 72f).toInt()
    layer.setBounds(-inset, -inset, size + inset, size + inset)
    layer.draw(canvas)
    canvas.restore()
    FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  @Test
  fun launcherIconInDifferentMasks() {
    render(432, { p, s -> p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW) }, "logo_circle")
    render(432, { p, s -> p.addRoundRect(0f, 0f, s, s, s * 0.28f, s * 0.28f, Path.Direction.CW) }, "logo_squircle")
    render(432, { p, s -> p.addRoundRect(0f, 0f, s, s, s * 0.28f, s * 0.28f, Path.Direction.CW) }, "logo_mono", monochrome = true)
    render(96, { p, s -> p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW) }, "logo_small")
  }
}
