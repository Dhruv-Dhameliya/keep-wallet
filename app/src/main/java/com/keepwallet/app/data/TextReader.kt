package com.keepwallet.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Reads the text in a photo. Behind an interface so the rest of the app (and its tests) never touch the recogniser. */
fun interface TextReader {
  /** The text found in the picture, or null when it could not be read. */
  suspend fun read(uri: Uri): String?
}

/**
 * On-device text recognition (Google ML Kit, bundled model): the picture never leaves the phone and there is no
 * network use.
 */
class MlKitTextReader(private val context: Context) : TextReader {
  override suspend fun read(uri: Uri): String? = suspendCancellableCoroutine { continuation ->
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
      val image = InputImage.fromFilePath(context, uri)
      recognizer.process(image)
        .addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result.text.ifBlank { null }) }
        .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        .addOnCompleteListener { recognizer.close() }
    } catch (_: Exception) {
      recognizer.close()
      if (continuation.isActive) continuation.resume(null)
    }
    continuation.invokeOnCancellation { recognizer.close() }
  }
}
