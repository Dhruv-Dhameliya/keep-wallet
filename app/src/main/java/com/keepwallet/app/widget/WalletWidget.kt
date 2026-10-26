package com.keepwallet.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.keepwallet.app.KeepWalletApp
import com.keepwallet.app.MainActivity
import com.keepwallet.app.R
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.budgetTotalFor
import com.keepwallet.app.domain.percentUsed
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth

/**
 * The home-screen widget: this month's spending against the budget and a button that opens the add form.
 * With the app lock on it shows no amounts, so nothing private sits on the home screen.
 */
class WalletWidgetProvider : AppWidgetProvider() {
  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        WalletWidget.render(context, appWidgetManager, appWidgetIds)
      } finally {
        pending.finish()
      }
    }
  }
}

object WalletWidget {
  /** What the widget shows; kept apart from the views so it can be tested. */
  data class Content(val title: String, val amount: String, val sub: String, val percent: Int, val accentArgb: Int)

  /** Redraws every widget that is on a home screen; cheap to call when none is. */
  fun refresh(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, WalletWidgetProvider::class.java))
    if (ids.isEmpty()) return
    CoroutineScope(Dispatchers.IO).launch { render(context, manager, ids) }
  }

  suspend fun content(context: Context): Content {
    val container = (context.applicationContext as KeepWalletApp).container
    val settings = container.settings
    val seed = settings.customAccent.value ?: settings.accent.value.argb.toInt()
    val accent = dynamicColorScheme(primary = Color(seed), isDark = false, style = PaletteStyle.Vibrant).primary.toArgb()
    if (settings.lockEnabled.value) return Content(context.getString(R.string.app_name), "Locked", "Open the app to see this month", 0, accent)
    val transactions = container.repository.transactions.first()
    val categories = container.repository.categories.first()
    val month = YearMonth.now()
    val spent = Analytics.total(Analytics.inSpan(transactions, com.keepwallet.app.domain.Periods.monthSpan(month)), TxType.EXPENSE)
    val budget = Analytics.budgetTotalFor(categories, false, transactions, month)
    val symbol = settings.currencySymbol.value
    val monthName = month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    return Content(
      title = "$monthName spending",
      amount = Money.formatWhole(spent, symbol),
      sub = if (budget > 0) {
        val left = budget - spent
        if (left >= 0) "of ${Money.formatWhole(budget, symbol)} · ${Money.formatWhole(left, symbol)} left"
        else "of ${Money.formatWhole(budget, symbol)} · ${Money.formatWhole(-left, symbol)} over"
      } else "No budget set yet",
      percent = if (budget > 0) percentUsed(spent, budget).coerceIn(0, 100) else 0,
      accentArgb = accent
    )
  }

  suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
    val content = try {
      content(context)
    } catch (_: Exception) {
      Content(context.getString(R.string.app_name), "–", "", 0, 0xFF16655E.toInt())
    }
    ids.forEach { id -> manager.updateAppWidget(id, views(context, content)) }
  }

  fun views(context: Context, content: Content): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_wallet)
    views.setInt(R.id.widget_bg, "setColorFilter", content.accentArgb)
    views.setTextViewText(R.id.widget_title, content.title)
    views.setTextViewText(R.id.widget_amount, content.amount)
    views.setTextViewText(R.id.widget_sub, content.sub)
    views.setProgressBar(R.id.widget_progress, 100, content.percent, false)
    views.setOnClickPendingIntent(R.id.widget_root, launch(context, null, 1))
    views.setOnClickPendingIntent(R.id.widget_add, launch(context, MainActivity.ACTION_ADD_EXPENSE, 2))
    return views
  }

  private fun launch(context: Context, action: String?, code: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
      this.action = action ?: Intent.ACTION_MAIN
      if (action == null) addCategory(Intent.CATEGORY_LAUNCHER)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  }
}
