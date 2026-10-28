package com.keepwallet.app

import com.keepwallet.app.data.KeepWalletDatabase

/** Application used by Robolectric tests: same wiring as production but with an in-memory database. */
class TestKeepWalletApp : KeepWalletApp() {
  // The app starts in rupees; the tests were written in dollars, so they keep that unless one asks otherwise.
  override fun createContainer(): AppContainer =
    AppContainer(this, KeepWalletDatabase.inMemory(this), firstRun = false, hideOnOpen = false).also { it.settings.setCurrencySymbol("$") }
}
