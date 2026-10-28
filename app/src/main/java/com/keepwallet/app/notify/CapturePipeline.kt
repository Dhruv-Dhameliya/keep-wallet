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
  data class NotATransaction(val reason: SkipReason = SkipReason.NO_AMOUNT) : CaptureOutcome
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

  /**
   * Looks at one notification. [text] is the main text; [moreTexts] are any other lines the notification carried (an
   * SMS app may put the message only in its conversation style, a bank app in a longer "big text"). The first one
   * that reads as a payment wins.
   */
  suspend fun process(
    sourcePackage: String,
    sourceApp: String,
    title: String?,
    text: String?,
    moreTexts: List<String> = emptyList()
  ): CaptureOutcome {
    if (!settings.isCaptureEnabledNow()) return CaptureOutcome.Disabled
    if (sourcePackage in IGNORED_PACKAGES) return CaptureOutcome.NotATransaction(SkipReason.CHAT)

    val candidates = (listOfNotNull(text) + moreTexts).map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { listOf("") }
    // Nothing without a digit can be a payment: leave quickly, and do not clutter the log with every chat and news alert.
    val hasFigure = (candidates + listOfNotNull(title)).any { line -> line.any { it.isDigit() } }
    if (!hasFigure) return CaptureOutcome.NotATransaction(SkipReason.NO_AMOUNT)

    var firstSkip: SkipReason? = null
    var parsed: ParsedTransaction? = null
    for (candidate in candidates) {
      when (val result = NotificationParser.analyse(title, candidate)) {
        is ParseResult.Found -> {
          parsed = result.transaction
          break
        }
        is ParseResult.Skipped -> if (firstSkip == null || firstSkip == SkipReason.EMPTY) firstSkip = result.reason
      }
    }
    if (parsed == null) {
      val reason = firstSkip ?: SkipReason.NO_AMOUNT
      log(sourceApp, "Skipped: ${reason.label}", captured = false)
      return CaptureOutcome.NotATransaction(reason)
    }
    val draft = DetectedEntryEntity(
      sourcePackage = sourcePackage,
      sourceApp = sourceApp,
      amountMinor = parsed.amountMinor,
      type = parsed.type,
      // A mail app is not who was paid: when the message names nobody, the sender (a bank, say) is the better label.
      merchant = parsed.merchant ?: (if (sourcePackage in MAIL_PACKAGES) title?.trim()?.takeIf { it.isNotEmpty() } else null) ?: sourceApp,
      detectedAt = clock()
    )
    val id = repository.addDetected(draft)
    if (id == null) {
      log(sourceApp, "Already detected a moment ago", captured = false)
      return CaptureOutcome.Duplicate
    }
    val saved = draft.copy(id = id)
    log(sourceApp, "Read ${if (parsed.type == com.keepwallet.app.data.TxType.EXPENSE) "money out" else "money in"}: ${com.keepwallet.app.domain.Money.format(parsed.amountMinor, settings.currencySymbol.value)}", captured = true)
    _events.tryEmit(saved)
    notifier.show(saved, settings.currencySymbol.value)
    return CaptureOutcome.Captured(saved)
  }

  private fun log(app: String, outcome: String, captured: Boolean) {
    settings.addDetectionLog(com.keepwallet.app.data.DetectionLogEntry(clock(), app, outcome, captured))
  }

  companion object {
    /** Chat apps are full of people talking about money; they are never a transaction alert. */
    /** The common mail apps: bank transfers often arrive as an email, and their notifications are read like any other. */
    val MAIL_PACKAGES = setOf(
      "com.google.android.gm", "com.google.android.gm.lite", "com.microsoft.office.outlook", "com.yahoo.mobile.client.android.mail",
      "com.samsung.android.email.provider", "ch.protonmail.android", "com.fsck.k9", "com.fsck.k9.material", "net.thunderbird.android",
      "com.my.mail", "me.bluemail.mail", "com.oneplus.email", "com.android.email"
    )

    val IGNORED_PACKAGES = setOf(
      "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.facebook.orca",
      "com.instagram.android", "com.discord", "com.Slack"
    )
  }
}
