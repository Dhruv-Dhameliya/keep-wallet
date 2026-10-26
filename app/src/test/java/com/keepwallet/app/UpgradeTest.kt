package com.keepwallet.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.CategoryGroup
import com.keepwallet.app.data.DefaultData
import com.keepwallet.app.data.DashboardWidgetEntity
import com.keepwallet.app.data.KeepWalletDatabase
import com.keepwallet.app.data.KeepWalletRepository
import com.keepwallet.app.data.ReceiptStore
import com.keepwallet.app.data.SettingsStore
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.domain.Analytics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Moving an existing install from the previous version to this one must not lose or scramble anything. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestKeepWalletApp::class, sdk = [35])
class UpgradeTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  /** The app container seeds itself on a background thread and shares the preferences file; let it finish first. */
  private fun awaitAppSeeding() {
    val app = ApplicationProvider.getApplicationContext<TestKeepWalletApp>()
    val deadline = System.currentTimeMillis() + 10_000
    while (System.currentTimeMillis() < deadline && !(app.container.settings.isSeeded && app.container.settings.defaultsVersion >= DefaultData.VERSION)) Thread.sleep(25)
    Thread.sleep(150)
  }

  private fun freshSettings(): SettingsStore {
    awaitAppSeeding()
    context.getSharedPreferences("keep_wallet_settings", Context.MODE_PRIVATE).edit().clear().commit()
    return SettingsStore(context)
  }

  /** Builds a version 1 database file exactly as the shipped schema describes it. */
  private fun createVersion1Database(name: String, version: Int = 1, fill: (SQLiteDatabase) -> Unit) {
    val schema = JSONObject(File("schemas/com.keepwallet.app.data.KeepWalletDatabase/$version.json").readText()).getJSONObject("database")
    val path = context.getDatabasePath(name).also { it.parentFile?.mkdirs(); it.delete() }
    val db = SQLiteDatabase.openOrCreateDatabase(path, null)
    val entities = schema.getJSONArray("entities")
    for (i in 0 until entities.length()) {
      val entity = entities.getJSONObject(i)
      val table = entity.getString("tableName")
      db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
      val indices = entity.optJSONArray("indices")
      if (indices != null) for (j in 0 until indices.length()) {
        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
      }
    }
    db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
    db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.getString("identityHash")}')")
    fill(db)
    db.version = version
    db.close()
  }

  @Test
  fun cardAccountsBecomeBankAccountsAndNothingElseChanges() {
    createVersion1Database("migrate.db") { db ->
      db.execSQL("INSERT INTO accounts (id,name,kind,openingBalanceMinor,sortOrder) VALUES (1,'Everyday card','CARD',150000,0)")
      db.execSQL("INSERT INTO accounts (id,name,kind,openingBalanceMinor,sortOrder) VALUES (2,'Cash','CASH',34000,1)")
      db.execSQL("INSERT INTO accounts (id,name,kind,openingBalanceMinor,sortOrder) VALUES (3,'Savings account','BANK',1250000,2)")
      db.execSQL("INSERT INTO categories (id,name,iconKey,color,type,`group`,monthlyLimitMinor,weeklyLimitMinor,sortOrder) VALUES (1,'Groceries','groceries',-1,'EXPENSE','ESSENTIAL',60000,20000,0)")
      db.execSQL("INSERT INTO transactions (id,type,amountMinor,merchant,categoryId,accountId,timestamp,note,receiptFile) VALUES (1,'EXPENSE',2400,'Green Market',1,1,1700000000000,'note','r.jpg')")
    }
    val room = Room.databaseBuilder(context, KeepWalletDatabase::class.java, "migrate.db")
      .addMigrations(KeepWalletDatabase.MIGRATION_1_2)
      .allowMainThreadQueries()
      .build()
    try {
      val accounts = runBlocking { room.accountDao().getAll() }
      assertEquals(listOf(AccountKind.BANK, AccountKind.CASH, AccountKind.BANK), accounts.map { it.kind })
      assertEquals(listOf(150_000L, 34_000L, 1_250_000L), accounts.map { it.openingBalanceMinor })
      assertEquals(listOf("Everyday card", "Cash", "Savings account"), accounts.map { it.name })
      val tx = runBlocking { room.transactionDao().getAll() }.single()
      assertEquals(2_400L, tx.amountMinor)
      assertEquals("Green Market", tx.merchant)
      assertEquals("r.jpg", tx.receiptFile)
      assertEquals(1L, tx.accountId)
    } finally {
      room.close()
    }
  }

  @Test
  fun anExistingInstallGetsTheNewDefaultsWithoutLosingData() = runBlocking {
    val settings = freshSettings()
    settings.isSeeded = true
    settings.defaultsVersion = 1
    val db = KeepWalletDatabase.inMemory(context)
    val repo = KeepWalletRepository(db, settings, ReceiptStore(context))

    // what the previous version created
    val old = listOf(
      "Housing", "Groceries", "Transport", "Dining", "Subscriptions", "Shopping", "Other"
    ).mapIndexed { i, name -> CategoryEntity(name = name, iconKey = "other", color = 0, group = CategoryGroup.FLEXIBLE, sortOrder = i, monthlyLimitMinor = 1_000L * (i + 1)) } +
      listOf(CategoryEntity(name = "Salary", iconKey = "salary", color = 0, type = TxType.INCOME, sortOrder = 7),
        CategoryEntity(name = "Other income", iconKey = "gift", color = 0, type = TxType.INCOME, sortOrder = 8))
    val categoryIds = db.categoryDao().insertAll(old)
    val accountIds = db.accountDao().insertAll(
      listOf(AccountEntity(name = "Everyday card", kind = AccountKind.BANK, openingBalanceMinor = 5_000), AccountEntity(name = "Cash", kind = AccountKind.CASH))
    )
    db.widgetDao().insertAll(
      listOf(
        DashboardWidgetEntity(kind = WidgetKind.MONTH_OVERVIEW, position = 0),
        DashboardWidgetEntity(kind = WidgetKind.QUICK_STATS, position = 1),
        DashboardWidgetEntity(kind = WidgetKind.SPENDING_CHART, position = 2),
        DashboardWidgetEntity(kind = WidgetKind.POCKETS_GRID, position = 3),
        DashboardWidgetEntity(kind = WidgetKind.RECENT_ACTIVITY, position = 4)
      )
    )
    val otherId = categoryIds[6]
    db.transactionDao().insert(
      TransactionEntity(type = TxType.EXPENSE, amountMinor = 700, merchant = "Pharmacy", categoryId = otherId, accountId = accountIds[0], timestamp = 1L)
    )

    repo.ensureSeeded()

    val categories = db.categoryDao().getAll()
    val names = categories.map { it.name }
    assertFalse("Other" in names)
    listOf("Others", "Person", "Money transfer", "Cashbacks", "Salary", "Other income").forEach { assertTrue("missing $it in $names", it in names) }
    // the renamed category is the same row, so its entries and limits are intact
    assertEquals(otherId, categories.first { it.name == "Others" }.id)
    assertEquals(7_000L, categories.first { it.name == "Others" }.monthlyLimitMinor)
    assertEquals(700L, db.transactionDao().getAll().single().amountMinor)
    assertEquals(otherId, db.transactionDao().getAll().single().categoryId)

    val accounts = db.accountDao().getAll()
    assertEquals(setOf(AccountKind.BANK, AccountKind.WALLET, AccountKind.CASH), accounts.map { it.kind }.toSet())
    assertEquals(5_000L, accounts.first { it.name == "Everyday card" }.openingBalanceMinor)

    val widgets = db.widgetDao().getAll()
    assertEquals(
      listOf(WidgetKind.BALANCE_CARD, WidgetKind.MONTH_OVERVIEW, WidgetKind.SPENDING_CHART, WidgetKind.RECENT_ACTIVITY, WidgetKind.POCKETS_GRID,
        WidgetKind.QUICK_STATS, WidgetKind.WEEK_OVERVIEW, WidgetKind.CATEGORY_BREAKDOWN,
        WidgetKind.HIGHLIGHTS, WidgetKind.UPCOMING_BILLS, WidgetKind.SAVINGS_GOALS),
      widgets.sortedBy { it.position }.map { it.kind }
    )
    assertEquals(5, widgets.count { it.enabled })
    assertEquals(DefaultData.VERSION, settings.defaultsVersion)

    // running it again changes nothing
    val snapshot = repo.backupContent()
    repo.ensureSeeded()
    assertEquals(snapshot, repo.backupContent())
    db.close()
  }

  @Test
  fun aFreshInstallStartsWithTheRequestedCategoriesAndAccounts() = runBlocking {
    val settings = freshSettings()
    val db = KeepWalletDatabase.inMemory(context)
    val repo = KeepWalletRepository(db, settings, ReceiptStore(context))
    repo.ensureSeeded()
    val categories = db.categoryDao().getAll()
    assertEquals(
      listOf("Housing", "Groceries", "Transport", "Dining", "Subscriptions", "Shopping", "Person", "Money transfer", "Others"),
      categories.filter { it.type == TxType.EXPENSE }.map { it.name }
    )
    assertEquals(listOf("Salary", "Cashbacks", "Other income"), categories.filter { it.type == TxType.INCOME }.map { it.name })
    assertEquals(
      listOf(AccountKind.BANK, AccountKind.WALLET, AccountKind.CASH),
      db.accountDao().getAll().map { it.kind }
    )
    assertTrue(db.accountDao().getAll().all { it.openingBalanceMinor == 0L })
    assertTrue(db.transactionDao().getAll().isEmpty())
    assertEquals(
      listOf(WidgetKind.BALANCE_CARD, WidgetKind.MONTH_OVERVIEW, WidgetKind.SPENDING_CHART, WidgetKind.RECENT_ACTIVITY, WidgetKind.POCKETS_GRID),
      db.widgetDao().getAll().filter { it.enabled }.sortedBy { it.position }.map { it.kind }
    )
    db.close()
  }

  @Test
  fun settingABalanceMakesTheAccountShowExactlyThatAmount() = runBlocking {
    val settings = freshSettings()
    val db = KeepWalletDatabase.inMemory(context)
    val repo = KeepWalletRepository(db, settings, ReceiptStore(context))
    repo.ensureSeeded()
    val bank = db.accountDao().getAll().first { it.kind == AccountKind.BANK }
    val category = db.categoryDao().getAll().first()

    repo.saveAccount(bank, 100_000)
    assertEquals(100_000L, Analytics.accountBalance(db.accountDao().getAll().first { it.id == bank.id }, db.transactionDao().getAll()))

    db.transactionDao().insert(TransactionEntity(type = TxType.EXPENSE, amountMinor = 2_500, merchant = "x", categoryId = category.id, accountId = bank.id, timestamp = 1L))
    db.transactionDao().insert(TransactionEntity(type = TxType.INCOME, amountMinor = 40_000, merchant = "y", categoryId = db.categoryDao().getAll().last().id, accountId = bank.id, timestamp = 2L))
    // 1000 - 25 + 400 = 1375 so far; now say "I actually have 2000"
    assertEquals(137_500L, Analytics.accountBalance(db.accountDao().getAll().first { it.id == bank.id }, db.transactionDao().getAll()))
    repo.saveAccount(db.accountDao().getAll().first { it.id == bank.id }, 200_000)
    assertEquals(200_000L, Analytics.accountBalance(db.accountDao().getAll().first { it.id == bank.id }, db.transactionDao().getAll()))

    // later entries move it from there
    db.transactionDao().insert(TransactionEntity(type = TxType.EXPENSE, amountMinor = 1_000, merchant = "z", categoryId = category.id, accountId = bank.id, timestamp = 3L))
    assertEquals(199_000L, Analytics.accountBalance(db.accountDao().getAll().first { it.id == bank.id }, db.transactionDao().getAll()))

    // an overdrawn account keeps working
    repo.saveAccount(db.accountDao().getAll().first { it.id == bank.id }, -5_000)
    assertEquals(-5_000L, Analytics.accountBalance(db.accountDao().getAll().first { it.id == bank.id }, db.transactionDao().getAll()))

    // a new account starts at what you typed
    val id = repo.saveAccount(AccountEntity(name = "Paytm", kind = AccountKind.WALLET), 25_000)
    assertEquals(25_000L, Analytics.accountBalance(db.accountDao().getAll().first { it.id == id }, db.transactionDao().getAll()))
    assertEquals(setOf("Bank account", "Wallet", "Cash", "Paytm"), db.accountDao().getAll().map { it.name }.toSet())
    db.close()
  }

  @Test
  fun deletingAnAccountWithEntriesIsRefused() = runBlocking {
    val settings = freshSettings()
    val db = KeepWalletDatabase.inMemory(context)
    val repo = KeepWalletRepository(db, settings, ReceiptStore(context))
    repo.ensureSeeded()
    val wallet = db.accountDao().getAll().first { it.kind == AccountKind.WALLET }
    assertTrue(repo.deleteAccount(wallet))
    val cash = db.accountDao().getAll().first { it.kind == AccountKind.CASH }
    db.transactionDao().insert(TransactionEntity(type = TxType.EXPENSE, amountMinor = 100, merchant = "x", categoryId = db.categoryDao().getAll().first().id, accountId = cash.id, timestamp = 1L))
    assertFalse(repo.deleteAccount(cash))
    assertTrue(db.accountDao().getAll().any { it.id == cash.id })
    assertEquals(1, repo.transactions.first().size)
    db.close()
  }
}
