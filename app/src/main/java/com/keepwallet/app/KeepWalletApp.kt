package com.keepwallet.app

import android.app.Application
import android.content.Context
import com.keepwallet.app.data.BackupManager
import com.keepwallet.app.data.CurrencyRates
import com.keepwallet.app.data.RateTable
import com.keepwallet.app.data.KeepWalletDatabase
import com.keepwallet.app.data.KeepWalletRepository
import com.keepwallet.app.data.ReceiptStore
import com.keepwallet.app.data.MlKitTextReader
import com.keepwallet.app.data.SettingsStore
import com.keepwallet.app.data.TextReader
import com.keepwallet.app.notify.CapturePipeline
import com.keepwallet.app.notify.DetectedNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import com.keepwallet.app.widget.AppShortcuts
import com.keepwallet.app.widget.WalletWidget

/** Hand-wired dependencies; small enough that a DI framework would only add noise. */
class AppContainer(context: Context, database: KeepWalletDatabase, firstRun: Boolean = true, hideOnOpen: Boolean = true) {
  val appContext: Context = context.applicationContext
  val settings = SettingsStore(context, firstRun, hideOnOpen)
  val receipts = ReceiptStore(context)
  val repository = KeepWalletRepository(database, settings, receipts)
  val notifier = DetectedNotifier(context)
  val capture = CapturePipeline(repository, settings, notifier)
  val backup = BackupManager(context, repository, settings, receipts)
  val reminders = com.keepwallet.app.notify.Reminders(context, repository, settings)

  /** Reads receipt photos; replaced by a fake in tests. */
  var textReader: TextReader = MlKitTextReader(context.applicationContext)

  /** Downloads exchange rates for the converter (the only network call in the app); replaced by a fake in tests. */
  var rateFetcher: (Long) -> RateTable? = { now -> CurrencyRates.fetch(now) }
}

open class KeepWalletApp : Application() {
  lateinit var container: AppContainer
    private set

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    container = createContainer()
    container.notifier.ensureChannel()
    container.reminders.ensureChannel()
    container.reminders.sync()
    appScope.launch {
      container.repository.ensureSeeded()
      container.repository.cleanOrphanReceipts()
    }
    // Keep the home-screen widget and the "add again" icon shortcuts in step with the data (and the app lock).
    appScope.launch {
      @OptIn(kotlinx.coroutines.FlowPreview::class)
      combine(container.repository.transactions, container.repository.categories, container.settings.lockEnabled, container.settings.currencySymbol) { t, _, lock, _ -> t to lock }
        .debounce(800)
        .collect { (transactions, lock) ->
          WalletWidget.refresh(this@KeepWalletApp)
          AppShortcuts.refresh(this@KeepWalletApp, transactions, lock)
        }
    }
  }

  /** Overridden by tests to supply an in-memory database. */
  protected open fun createContainer(): AppContainer = AppContainer(this, KeepWalletDatabase.create(this))
}
