package com.keepwallet.app.notify

import android.app.Notification
import android.content.pm.ApplicationInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.keepwallet.app.KeepWalletApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens to posted notifications once the user has granted notification access, and hands the
 * ones that look like payments to [CapturePipeline]. Nothing is saved without the user's review.
 */
class CaptureListenerService : NotificationListenerService() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    sbn ?: return
    val app = application as? KeepWalletApp ?: return
    val container = app.container

    // Cheap checks first: respect the user's switch, skip our own alerts and non-content notifications.
    if (!container.settings.isCaptureEnabledNow()) return
    if (sbn.packageName == packageName) return
    val notification = sbn.notification ?: return
    if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
    if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

    val extras = notification.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
      ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    if (title.isNullOrBlank() && text.isNullOrBlank()) return

    val sourcePackage = sbn.packageName
    val sourceApp = resolveAppName(@Suppress("DEPRECATION") extras.getParcelable<ApplicationInfo>(EXTRA_APP_INFO), sourcePackage)
    scope.launch {
      container.capture.process(sourcePackage, sourceApp, title, text)
    }
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun resolveAppName(info: ApplicationInfo?, packageName: String): String {
    val label = try {
      info?.loadLabel(packageManager)?.toString()
    } catch (_: Exception) {
      null
    }
    if (!label.isNullOrBlank()) return label
    return packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
  }

  private companion object {
    const val EXTRA_APP_INFO = "android.appInfo"
  }
}
