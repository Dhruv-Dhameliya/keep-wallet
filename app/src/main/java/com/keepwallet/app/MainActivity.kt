package com.keepwallet.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.ui.AppRoot
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.LocalCurrency
import com.keepwallet.app.ui.theme.KeepWalletTheme
import com.keepwallet.app.ui.theme.isDarkTheme

class MainActivity : FragmentActivity() {
  private val container: AppContainer get() = (application as KeepWalletApp).container
  private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(container) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      val accent by viewModel.accent.collectAsStateWithLifecycle()
      val background by viewModel.backgroundMode.collectAsStateWithLifecycle()
      val customAccent by viewModel.customAccent.collectAsStateWithLifecycle()
      val highlight by viewModel.highlight.collectAsStateWithLifecycle()
      val colorStyle by viewModel.colorStyle.collectAsStateWithLifecycle()
      val systemColors by viewModel.systemColors.collectAsStateWithLifecycle()
      val currency by viewModel.currencySymbol.collectAsStateWithLifecycle()
      KeepWalletTheme(
        accent = androidx.compose.ui.graphics.Color(customAccent ?: accent.argb.toInt()),
        highlight = highlight?.let { androidx.compose.ui.graphics.Color(it) },
        background = background,
        colorStyle = colorStyle,
        systemColors = systemColors
      ) {
        // Status and navigation bar icons follow the app's own light/dark choice, not just the system's.
        val dark = isDarkTheme()
        DisposableEffect(dark) {
          val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
          enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
          onDispose { }
        }
        // While the lock is on and "hide in recent apps" is chosen, the window is marked secure: the recent-apps
        // thumbnail is blank and screenshots are blocked.
        val lockOn by viewModel.lockEnabled.collectAsStateWithLifecycle()
        val hideRecents by viewModel.hideInRecents.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(lockOn, hideRecents) {
          if (lockOn && hideRecents) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
          else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
        val photoLoader = remember {
          val cache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(64)
          val loader: (String) -> androidx.compose.ui.graphics.ImageBitmap? = { name ->
            cache.get(name) ?: container.receipts.load(name, 256)?.asImageBitmap()?.also { cache.put(name, it) }
          }
          loader
        }
        // The whole interface is drawn at 95% of its natural size: a little more room, a finer, denser look.
        val baseDensity = androidx.compose.ui.platform.LocalDensity.current
        val scaledDensity = remember(baseDensity) { androidx.compose.ui.unit.Density(baseDensity.density * UI_SCALE, baseDensity.fontScale) }
        CompositionLocalProvider(
          androidx.compose.ui.platform.LocalDensity provides scaledDensity,
          com.keepwallet.app.ui.components.LocalPhotoLoader provides photoLoader,
          LocalCurrency provides currency,
          com.keepwallet.app.ui.screens.LocalBiometric provides remember { com.keepwallet.app.ui.screens.BiometricHelper(this@MainActivity) }
        ) {
          AppRoot(viewModel, container.receipts)
        }
      }
    }
    // A rotation re-delivers the original intent; only act on it for a fresh launch.
    if (savedInstanceState == null) handleIntent(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  override fun onStop() {
    super.onStop()
    viewModel.onBackgrounded()
  }

  override fun onResume() {
    super.onResume()
    viewModel.onResume()
    viewModel.checkLock()
  }

  internal fun handleIntent(intent: Intent?) {
    val action = intent?.action ?: return
    when (action) {
      ACTION_REVIEW_DETECTED -> {
        val id = intent.getLongExtra(EXTRA_DETECTED_ID, -1L)
        // Consume the action so it isn't replayed later.
        intent.action = null
        if (id >= 0) viewModel.reviewDetected(id)
      }
      ACTION_OPEN_ALERTS -> {
        intent.action = null
        viewModel.openAlerts()
      }
      ACTION_ADD_EXPENSE, ACTION_ADD_INCOME, ACTION_SCAN_RECEIPT, ACTION_VOICE_ENTRY, ACTION_QUICK_ENTRY -> {
        val merchant = intent.getStringExtra(EXTRA_MERCHANT)
        val amount = intent.getLongExtra(EXTRA_AMOUNT_MINOR, 0L)
        val category = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)
        val account = intent.getLongExtra(EXTRA_ACCOUNT_ID, -1L)
        intent.action = null
        viewModel.launchFromShortcut(action, merchant, amount, category.takeIf { it >= 0 }, account.takeIf { it >= 0 })
      }
    }
  }

  companion object {
    /** Everything is laid out at this fraction of the system size. */
    const val UI_SCALE = 0.95f
    const val ACTION_OPEN_ALERTS = "com.keepwallet.app.action.OPEN_ALERTS"
    const val ACTION_ADD_EXPENSE = "com.keepwallet.app.action.ADD_EXPENSE"
    const val ACTION_ADD_INCOME = "com.keepwallet.app.action.ADD_INCOME"
    const val ACTION_SCAN_RECEIPT = "com.keepwallet.app.action.SCAN_RECEIPT"
    const val ACTION_VOICE_ENTRY = "com.keepwallet.app.action.VOICE_ENTRY"
    const val ACTION_QUICK_ENTRY = "com.keepwallet.app.action.QUICK_ENTRY"
    const val EXTRA_MERCHANT = "merchant"
    const val EXTRA_AMOUNT_MINOR = "amount_minor"
    const val EXTRA_CATEGORY_ID = "category_id"
    const val EXTRA_ACCOUNT_ID = "account_id"
    const val ACTION_REVIEW_DETECTED = "com.keepwallet.app.action.REVIEW_DETECTED"
    const val EXTRA_DETECTED_ID = "detected_id"
  }
}
