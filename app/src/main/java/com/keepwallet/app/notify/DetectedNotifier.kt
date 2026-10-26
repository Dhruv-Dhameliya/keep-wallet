package com.keepwallet.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.keepwallet.app.MainActivity
import com.keepwallet.app.R
import com.keepwallet.app.data.DetectedEntryEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Money

/** Shows the high-priority "entry detected" notification with one-tap Add and Dismiss actions. */
class DetectedNotifier(context: Context) {
  private val appContext = context.applicationContext

  fun ensureChannel() {
    val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
      CHANNEL_ID,
      appContext.getString(R.string.channel_detected_name),
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = appContext.getString(R.string.channel_detected_description)
      lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
    }
    manager.createNotificationChannel(channel)
  }

  /** Posts the quick action. Silently does nothing when the user has blocked notifications. */
  fun show(entry: DetectedEntryEntity, currencySymbol: String) {
    val manager = NotificationManagerCompat.from(appContext)
    if (!manager.areNotificationsEnabled()) return

    val notificationId = entry.id.toInt()
    val open = PendingIntent.getActivity(
      appContext,
      notificationId * 2,
      Intent(appContext, MainActivity::class.java)
        .setAction(MainActivity.ACTION_REVIEW_DETECTED)
        .putExtra(MainActivity.EXTRA_DETECTED_ID, entry.id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val dismiss = PendingIntent.getBroadcast(
      appContext,
      notificationId * 2 + 1,
      Intent(appContext, DetectedActionReceiver::class.java)
        .setAction(DetectedActionReceiver.ACTION_DISMISS)
        .putExtra(MainActivity.EXTRA_DETECTED_ID, entry.id),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val amount = Money.format(entry.amountMinor, currencySymbol)
    val title = if (entry.type == TxType.INCOME) "Deposit detected: $amount" else "Payment detected: $amount"
    val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_wallet)
      .setContentTitle(title)
      .setContentText("${entry.merchant} · from ${entry.sourceApp}")
      .setSubText("Keep Wallet")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_STATUS)
      .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .setContentIntent(open)
      .setAutoCancel(true)
      .addAction(0, "Add to Keep Wallet", open)
      .addAction(0, "Dismiss", dismiss)
      .build()

    try {
      manager.notify(notificationId, notification)
    } catch (_: SecurityException) {
      // Notification permission was revoked between the check and the post.
    }
  }

  fun cancel(entryId: Long) {
    NotificationManagerCompat.from(appContext).cancel(entryId.toInt())
  }

  companion object {
    const val CHANNEL_ID = "detected_transactions"
  }
}
