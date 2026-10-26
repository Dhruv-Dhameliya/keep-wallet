package com.keepwallet.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Keeps receipt photos inside the app's private storage. Photos are re-encoded as downscaled JPEGs,
 * which also drops EXIF metadata such as GPS location.
 */
class ReceiptStore(context: Context) {
  private val appContext = context.applicationContext
  private val dir: File get() = File(appContext.filesDir, "receipts").also { it.mkdirs() }

  /** Copies the picked image into private storage and returns its file name, or null if it can't be read. */
  fun import(uri: Uri): String? = try {
    val bitmap = decode(uri) ?: return null
    val name = "${UUID.randomUUID()}.jpg"
    FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    bitmap.recycle()
    name
  } catch (_: Exception) {
    null
  }

  /** A small square picture for a category: centre-cropped and scaled to [size] pixels. */
  fun importSquare(uri: Uri, size: Int = 256): String? = try {
    val source = decode(uri) ?: return null
    val side = minOf(source.width, source.height)
    val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
    val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
    val name = "${UUID.randomUUID()}.jpg"
    FileOutputStream(File(dir, name)).use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    if (cropped !== source) source.recycle()
    if (scaled !== cropped) cropped.recycle()
    scaled.recycle()
    name
  } catch (_: Exception) {
    null
  }

  fun file(name: String): File = File(dir, name)

  /** Decodes a receipt for display, scaled so its longest side is at most [maxSidePx]. */
  fun load(name: String, maxSidePx: Int): Bitmap? {
    val file = file(name)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
  }

  /** Replaces every stored receipt with the files in [stage] (used when restoring a backup). */
  fun installFrom(stage: File) {
    dir.listFiles()?.forEach { it.delete() }
    stage.listFiles()?.forEach { it.copyTo(File(dir, it.name), overwrite = true) }
  }

  fun delete(name: String?) {
    if (name != null) file(name).delete()
  }

  /** Removes receipt files that no transaction refers to any more. */
  fun deleteExcept(keep: Set<String>) {
    dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
  }

  private fun decode(uri: Uri): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) {
      val source = ImageDecoder.createSource(appContext.contentResolver, uri)
      return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = max(info.size.width, info.size.height)
        if (longest > MAX_SIDE) {
          val scale = MAX_SIDE.toFloat() / longest
          decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
      }
    }
    val resolver = appContext.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
    return resolver.openInputStream(uri)?.use {
      BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
  }

  private companion object {
    const val MAX_SIDE = 1600
  }
}
