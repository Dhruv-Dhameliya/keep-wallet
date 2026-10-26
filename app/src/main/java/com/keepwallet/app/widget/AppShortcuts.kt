package com.keepwallet.app.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.keepwallet.app.MainActivity
import com.keepwallet.app.R
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType

/**
 * Long-press shortcuts on the app icon. Four fixed ones live in res/xml/shortcuts.xml; this adds the entries you
 * repeat most as "add it again" shortcuts. They are removed while the app lock is on, since a shop name on the home
 * screen is private too.
 */
object AppShortcuts {
  private const val ID_PREFIX = "again_"
  private const val STATIC_COUNT = 4

  /** The up-to-[limit] expense merchants used most in the last two months, newest data first. */
  fun recentPicks(transactions: List<TransactionEntity>, now: Long, limit: Int): List<TransactionEntity> {
    val since = now - 60L * 24 * 60 * 60 * 1000
    return transactions
      .filter { it.type == TxType.EXPENSE && it.merchant.isNotBlank() && it.timestamp >= since }
      .groupBy { it.merchant.trim().lowercase() }
      .values
      .sortedWith(compareByDescending<List<TransactionEntity>> { it.size }.thenByDescending { list -> list.maxOf { it.timestamp } })
      .map { list -> list.maxBy { it.timestamp } }
      .take(limit)
  }

  fun refresh(context: Context, transactions: List<TransactionEntity>, lockEnabled: Boolean, now: Long = System.currentTimeMillis()) {
    try {
      val room = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - STATIC_COUNT).coerceIn(0, 3)
      val picks = if (lockEnabled || room == 0) emptyList() else recentPicks(transactions, now, room)
      val shortcuts = picks.map { t ->
        ShortcutInfoCompat.Builder(context, ID_PREFIX + t.merchant.trim().lowercase().take(30))
          .setShortLabel(t.merchant.trim().take(20))
          .setLongLabel("Add ${t.merchant.trim().take(30)} again")
          .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_add))
          .setIntent(
            Intent(context, MainActivity::class.java)
              .setAction(MainActivity.ACTION_QUICK_ENTRY)
              .putExtra(MainActivity.EXTRA_MERCHANT, t.merchant.trim())
              .putExtra(MainActivity.EXTRA_AMOUNT_MINOR, t.amountMinor)
              .putExtra(MainActivity.EXTRA_CATEGORY_ID, t.categoryId)
              .putExtra(MainActivity.EXTRA_ACCOUNT_ID, t.accountId)
          )
          .build()
      }
      if (shortcuts.isEmpty()) ShortcutManagerCompat.removeAllDynamicShortcuts(context)
      else ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    } catch (_: Exception) {
      // Launchers differ; a shortcut that cannot be made is not worth a crash.
    }
  }
}
