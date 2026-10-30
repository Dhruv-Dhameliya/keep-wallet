package com.keepwallet.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.keepwallet.app.KeepWalletApp
import com.keepwallet.app.MainActivity
import com.keepwallet.app.R
import com.keepwallet.app.data.KeepWalletRepository
import com.keepwallet.app.data.SettingsStore
import com.keepwallet.app.domain.AlertItem
import com.keepwallet.app.domain.AlertKind
import com.keepwallet.app.domain.Alerts
import com.keepwallet.app.domain.Periods
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Bill reminders, budget alerts and the evening nudge, all optional and all off until switched on in Settings.
 * Nothing is sent anywhere: these are ordinary local notifications. Each situation is reminded once (it is
 * remembered in [marks]), and with the app lock on the notifications carry no names or amounts.
 */
class Reminders(
  context: Context,
  private val repository: KeepWalletRepository,
  private val settings: SettingsStore
) {
  private val appContext = context.applicationContext
  private val marks: SharedPreferences = appContext.getSharedPreferences("keep_wallet_reminders", Context.MODE_PRIVATE)

  fun ensureChannel() {
    val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "Reminders and alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Bills that are due, budgets that are filling up, and the evening nudge"
        lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
      }
    )
  }

  /** Starts or stops the background check to match the switches in Settings. Safe to call at any time. */
  fun sync() {
    try {
      val work = WorkManager.getInstance(appContext)
      if (settings.remindBills.value || settings.budgetAlerts.value || settings.dailyNudge.value || settings.autoBackupEnabled.value) {
        work.enqueueUniquePeriodicWork(
          WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
          PeriodicWorkRequestBuilder<ReminderWorker>(3, TimeUnit.HOURS).build()
        )
      } else {
        work.cancelUniqueWork(WORK_NAME)
      }
    } catch (_: Exception) {
      // Without the scheduler the in-app bell still works.
    }
  }

  /** Runs on the schedule: bills and budgets in the morning, the nudge after the chosen hour. */
  suspend fun runDue(now: LocalDateTime = LocalDateTime.now()) {
    val today = now.toLocalDate()
    if (now.hour >= MORNING_HOUR) postAlerts(today, bills = settings.remindBills.value, budgets = settings.budgetAlerts.value)
    if (settings.dailyNudge.value && now.hour >= settings.nudgeHour.value) postNudge(today)
    if (settings.autoBackupEnabled.value) {
      val uriStr = settings.autoBackupUri.value
      if (uriStr != null) {
        val freqDays = if (settings.autoBackupFrequency.value == "DAILY") 1 else 7
        val elapsed = System.currentTimeMillis() - settings.lastAutoBackupTime.value
        if (elapsed >= freqDays * 24L * 60 * 60 * 1000) {
          val backup = (appContext as? KeepWalletApp)?.container?.backup
          if (backup != null) {
            val res = backup.exportToTree(android.net.Uri.parse(uriStr))
            if (res != null) settings.recordAutoBackupTime(System.currentTimeMillis())
          }
        }
      }
    }
  }

  /** Right after an entry is saved: tell the user at once if a budget just reached 80% or 100%. */
  suspend fun afterEntrySaved(today: LocalDate = LocalDate.now()) {
    if (settings.budgetAlerts.value) postAlerts(today, bills = false, budgets = true)
  }

  private suspend fun postAlerts(today: LocalDate, bills: Boolean, budgets: Boolean) {
    val alerts = Alerts.compute(
      detectedCount = 0,
      bills = repository.recurring.first(),
      categories = repository.categories.first(),
      transactions = repository.transactions.first(),
      today = today,
      symbol = settings.currencySymbol.value
    )
    val masked = settings.lockEnabled.value
    alerts.filter { alert ->
      when (alert.kind) {
        AlertKind.BILL_DUE, AlertKind.BILL_OVERDUE -> bills
        AlertKind.BUDGET_NEAR, AlertKind.BUDGET_OVER -> budgets
        else -> false
      }
    }.forEach { alert ->
      // A budget crossing 100% supersedes its 80% reminder: skip the 80% one if the 100% one was already sent.
      if (!alreadySent(alert.key)) {
        post(alert, masked)
        remember(alert.key)
      }
    }
  }

  private suspend fun postNudge(today: LocalDate) {
    val key = "nudge:${today.toEpochDay()}"
    if (alreadySent(key)) return
    val loggedToday = repository.transactions.first().any { Periods.millisToDate(it.timestamp) == today }
    remember(key)
    if (loggedToday) return
    post(AlertItem(key, AlertKind.PAYMENTS_WAITING, "Anything to note down today?", "A quick moment now keeps the month accurate."), masked = false, id = NUDGE_ID)
  }

  private fun post(alert: AlertItem, masked: Boolean, id: Int = alert.key.hashCode()) {
    val manager = NotificationManagerCompat.from(appContext)
    if (!manager.areNotificationsEnabled()) return
    val open = PendingIntent.getActivity(
      appContext, id,
      Intent(appContext, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_ALERTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val title = if (masked) "Keep Wallet" else alert.title
    val text = if (masked) "Open the app to see what needs attention." else alert.body
    val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_wallet)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(open)
      .setAutoCancel(true)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .build()
    try {
      manager.notify(id, notification)
    } catch (_: SecurityException) {
      // Notification permission was revoked in the meantime.
    }
  }

  // ---- what has already been sent ------------------------------------------------------------

  private fun alreadySent(key: String): Boolean = marks.getBoolean(key, false)

  private fun remember(key: String) {
    val editor = marks.edit().putBoolean(key, true)
    // Old marks are of no use: keep only a few hundred.
    val all = marks.all.keys
    if (all.size > MAX_MARKS) all.take(all.size - MAX_MARKS).forEach { editor.remove(it) }
    editor.apply()
  }

  companion object {
    const val CHANNEL_ID = "reminders"
    const val WORK_NAME = "keepwallet_reminders"
    const val MORNING_HOUR = 9
    private const val NUDGE_ID = 9001
    private const val MAX_MARKS = 300
  }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val container = (applicationContext as? KeepWalletApp)?.container ?: return Result.success()
    container.reminders.runDue()
    return Result.success()
  }
}
