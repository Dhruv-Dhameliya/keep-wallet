package com.keepwallet.app.notify

import com.keepwallet.app.data.DetectedEntryEntity
import com.keepwallet.app.data.KeepWalletRepository
import com.keepwallet.app.data.SettingsStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface CaptureOutcome {
  /** The user switched detection off, so the notification was not even parsed. */
  data object Disabled : CaptureOutcome
  data object NotATransaction : CaptureOutcome
  data object Duplicate : CaptureOutcome
  data class Captured(val entry: DetectedEntryEntity) : CaptureOutcome
}

/**
 * The one path every notification goes through, whether it came from the system listener or from the
 * in-app test tool. Only parsed fields are stored; the raw notification text is dropped here.
 */
class CapturePipeline(
  private val repository: KeepWalletRepository,
  private val settings: SettingsStore,
  private val notifier: DetectedNotifier,
  private val clock: () -> Long = System::currentTimeMillis
) {
  private val _events = MutableSharedFlow<DetectedEntryEntity>(extraBufferCapacity = 8)

  /** Emits each newly captured entry so the open app can show an in-app prompt. */
  val events: SharedFlow<DetectedEntryEntity> = _events.asSharedFlow()

  suspend fun process(
    sourcePackage: String,
    sourceApp: String,
    title: String?,
    text: String?
  ): CaptureOutcome {
    if (!settings.isCaptureEnabledNow()) return CaptureOutcome.Disabled
    if (sourcePackage in IGNORED_PACKAGES) return CaptureOutcome.NotATransaction

    val parsed = NotificationParser.parse(title, text) ?: return CaptureOutcome.NotATransaction
    val draft = DetectedEntryEntity(
      sourcePackage = sourcePackage,
      sourceApp = sourceApp,
      amountMinor = parsed.amountMinor,
      type = parsed.type,
      merchant = parsed.merchant ?: sourceApp,
      detectedAt = clock()
    )
    val id = repository.addDetected(draft) ?: return CaptureOutcome.Duplicate
    val saved = draft.copy(id = id)
    _events.tryEmit(saved)
    notifier.show(saved, settings.currencySymbol.value)
    return CaptureOutcome.Captured(saved)
  }

  companion object {
    /** Chat apps are full of people talking about money; they are never a transaction alert. */
    val IGNORED_PACKAGES = setOf(
      "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.facebook.orca",
      "com.instagram.android", "com.discord", "com.Slack"
    )
  }
}
