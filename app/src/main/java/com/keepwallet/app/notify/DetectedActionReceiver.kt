package com.keepwallet.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.keepwallet.app.KeepWalletApp
import com.keepwallet.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the "Dismiss" button on the detected-entry notification: the entry is discarded for good. */
class DetectedActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION_DISMISS) return
    val id = intent.getLongExtra(MainActivity.EXTRA_DETECTED_ID, -1L)
    if (id < 0) return
    val container = (context.applicationContext as? KeepWalletApp)?.container ?: return
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        container.repository.discardDetected(id)
        container.notifier.cancel(id)
      } finally {
        pending.finish()
      }
    }
  }

  companion object {
    const val ACTION_DISMISS = "com.keepwallet.app.action.DISMISS_DETECTED"
  }
}
