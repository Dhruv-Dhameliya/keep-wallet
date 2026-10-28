package com.keepwallet.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.BuiltInLayouts
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.DefaultData
import com.keepwallet.app.data.ImportResult
import com.keepwallet.app.data.KeepWalletDatabase
import com.keepwallet.app.data.PaymentRuleEntity
import com.keepwallet.app.data.SavedLayout
import com.keepwallet.app.data.SettingsStore
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.AttemptLimiter
import com.keepwallet.app.domain.LockPolicy
import com.keepwallet.app.domain.PinHasher
import com.keepwallet.app.domain.effectiveMonthlyLimit
import com.keepwallet.app.domain.rolloverCarry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private val utc: ZoneId = ZoneId.of("UTC")

private fun at(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(utc).toInstant().toEpochMilli()

private fun spend(id: Long, date: LocalDate, minor: Long, category: Long = 1, account: Long = 1, note: String? = null) =
  TransactionEntity(id = id, type = TxType.EXPENSE, amountMinor = minor, merchant = "x", categoryId = category, accountId = account, timestamp = at(date), note = note)

// ---- App lock logic ------------------------------------------------------------------------------

class LockSecurityTest {
  @Test
  fun `a pin hashes to something that is not the pin, differs per salt, and verifies`() {
    val salt = PinHasher.newSalt()
    val hash = PinHasher.hash("4821", salt)
    assertNotEquals("4821", hash)
    assertTrue(PinHasher.matches("4821", salt, hash))
    assertFalse(PinHasher.matches("4822", salt, hash))
    assertNotEquals(hash, PinHasher.hash("4821", PinHasher.newSalt()))
    assertNotEquals(PinHasher.newSalt(), PinHasher.newSalt())
  }

  @Test
  fun `only four to six digits count as a pin`() {
    listOf("1234", "123456", "0000").forEach { assertTrue(it, PinHasher.isValidPin(it)) }
    listOf("", "123", "1234567", "12a4", "12 34", "١٢٣٤").forEach { assertFalse(it, PinHasher.isValidPin(it)) }
  }

  @Test
  fun `five wrong tries start a wait that grows, and a right pin clears it`() {
    val limiter = AttemptLimiter(freeTries = 5, cooldownMs = 30_000)
    repeat(4) { limiter.failed(now = 1_000) }
    assertEquals(0L, limiter.remainingMs(1_000))
    assertEquals(1, limiter.triesLeft)
    limiter.failed(now = 1_000)
    assertEquals(30_000L, limiter.remainingMs(1_000))
    assertEquals(0L, limiter.remainingMs(31_000))
    repeat(5) { limiter.failed(now = 40_000) }
    assertEquals(60_000L, limiter.remainingMs(40_000)) // the second round waits twice as long
    limiter.reset()
    assertEquals(0L, limiter.remainingMs(40_000))
    assertEquals(5, limiter.triesLeft)
  }

  @Test
  fun `the app locks again only after being away for the chosen time`() {
    assertTrue(LockPolicy.shouldLock(0, 0))
    assertFalse(LockPolicy.shouldLock(60, 59_999))
    assertTrue(LockPolicy.shouldLock(60, 60_000))
    assertFalse(LockPolicy.shouldLock(300, 120_000))
    assertTrue(LockPolicy.shouldLock(300, 301_000))
  }
}

// ---- Rollover ---------------------------------------------------------------------------

class RolloverTest {
  private val groceries = CategoryEntity(id = 1, name = "Groceries", iconKey = "groceries", color = 0, monthlyLimitMinor = 100_000, rollover = true)
  private val october = YearMonth.of(2026, 10)

  @Test
  fun `unspent budget carries forward month after month`() {
    val tx = listOf(
      spend(1, LocalDate.of(2026, 8, 5), 0 + 1, category = 2), // makes August the first month on record
      spend(2, LocalDate.of(2026, 9, 10), 30_000)
    )
    // August: nothing spent in the category -> 100,000 carried; September: 100,000 + 100,000 - 30,000 -> 170,000
    assertEquals(270_000L, Analytics.effectiveMonthlyLimit(groceries, tx, october, utc))
    assertEquals(170_000L, Analytics.rolloverCarry(groceries, tx, october, utc))
  }

  @Test
  fun `overspending does not make the next month smaller`() {
    val tx = listOf(spend(1, LocalDate.of(2026, 9, 10), 150_000))
    assertEquals(100_000L, Analytics.effectiveMonthlyLimit(groceries, tx, october, utc))
  }

  @Test
  fun `without the switch, or without a limit, nothing is carried`() {
    val tx = listOf(spend(1, LocalDate.of(2026, 9, 10), 10_000))
    assertEquals(100_000L, Analytics.effectiveMonthlyLimit(groceries.copy(rollover = false), tx, october, utc))
    assertEquals(0L, Analytics.effectiveMonthlyLimit(groceries.copy(monthlyLimitMinor = 0), tx, october, utc))
    assertEquals(100_000L, Analytics.effectiveMonthlyLimit(groceries, emptyList(), october, utc))
  }

  @Test
  fun `carry never reaches back more than a year`() {
    val tx = listOf(spend(1, LocalDate.of(2020, 1, 5), 1, category = 2))
    // twelve untouched months of 100,000, then this one
    assertEquals(100_000L * 13, Analytics.effectiveMonthlyLimit(groceries, tx, october, utc))
  }

  @Test
  fun `the budget impact in the form counts the carry`() {
    val tx = listOf(spend(1, LocalDate.of(2026, 9, 10), 40_000))
    val impact = Analytics.budgetImpact(groceries, tx, LocalDate.of(2026, 10, 14), 20_000, null, utc)!!
    assertEquals(160_000L, impact.limitMinor)
    assertEquals(140_000L, impact.leftAfterMinor)
  }
}

// ---- Transfers, splits, rules, layouts and the 3 -> 4 upgrade on a real in-memory database -------------

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKeepWalletApp::class, sdk = [35])
class Round2RepositoryTest {
  private val app get() = ApplicationProvider.getApplicationContext<TestKeepWalletApp>()
  private val context: Context get() = app
  private val container get() = app.container
  private val repository get() = container.repository

  private fun seeded(): Pair<List<CategoryEntity>, List<AccountEntity>> = runBlocking {
    repository.ensureSeeded()
    repository.backupContent().let { it.categories to it.accounts }
  }

  private fun balances(): Map<String, Long> = runBlocking {
    val tx = repository.transactions.first()
    repository.accounts.first().associate { it.name to Analytics.accountBalance(it, tx) }
  }

  @Test
  fun aTransferMovesMoneyBetweenAccountsWithoutTouchingIncomeOrExpense() = runBlocking {
    val (_, accounts) = seeded()
    val bank = accounts.first { it.kind == AccountKind.BANK }
    val cash = accounts.first { it.kind == AccountKind.CASH }
    repository.saveAccount(bank, 100_000)
    val before = balances()

    val id = repository.saveTransfer(bank.id, cash.id, 25_000, at(LocalDate.of(2026, 10, 5)), "ATM")
    assertNotNull(id)
    val tx = repository.transactions.first()
    assertEquals(2, tx.size)
    assertEquals(setOf(TxType.TRANSFER_OUT, TxType.TRANSFER_IN), tx.map { it.type }.toSet())
    assertTrue(tx.all { it.transferId == id && it.amountMinor == 25_000L && it.note == "ATM" })
    assertEquals(before.getValue(bank.name) - 25_000, balances().getValue(bank.name))
    assertEquals(before.getValue(cash.name) + 25_000, balances().getValue(cash.name))
    assertEquals(before.values.sum(), balances().values.sum()) // money only moved
    assertEquals(0L, Analytics.total(tx, TxType.EXPENSE))
    assertEquals(0L, Analytics.total(tx, TxType.INCOME))
    assertEquals(25_000L, repository.legsOfTransfer(id!!).first { it.type == TxType.TRANSFER_OUT }.amountMinor)
  }

  @Test
  fun aTransferNeedsTwoDifferentAccountsAndAnAmount() = runBlocking {
    val (_, accounts) = seeded()
    val a = accounts[0].id
    val b = accounts[1].id
    assertNull(repository.saveTransfer(a, a, 100, 0))
    assertNull(repository.saveTransfer(a, b, 0, 0))
    assertNull(repository.saveTransfer(a, 9_999, 100, 0))
    assertTrue(repository.transactions.first().isEmpty())
  }

  @Test
  fun deletingATransferRemovesBothHalvesAndUndoBringsThemBack() = runBlocking {
    val (_, accounts) = seeded()
    val id = repository.saveTransfer(accounts[0].id, accounts[1].id, 5_000, 1_000)!!
    val legs = repository.legsOfTransfer(id)
    assertEquals(2, legs.size)
    repository.deleteTransfer(id)
    assertTrue(repository.transactions.first().isEmpty())
    repository.restoreTransactions(legs)
    assertEquals(2, repository.transactions.first().size)
  }

  @Test
  fun aSplitBillIsSeveralLinkedEntries() = runBlocking {
    val (categories, accounts) = seeded()
    val groceries = categories.first { it.name == "Groceries" }
    val shopping = categories.first { it.name == "Shopping" }
    fun part(category: CategoryEntity, amount: Long) =
      TransactionEntity(type = TxType.EXPENSE, amountMinor = amount, merchant = "Supermarket", categoryId = category.id, accountId = accounts.first().id, timestamp = 1_000)
    val id = repository.saveSplit(listOf(part(groceries, 4_200), part(shopping, 1_800)))
    assertNotNull(id)
    val saved = repository.transactions.first()
    assertEquals(2, saved.size)
    assertTrue(saved.all { it.splitId == id })
    assertEquals(setOf(groceries.id, shopping.id), saved.map { it.categoryId }.toSet())
    assertEquals(6_000L, Analytics.total(saved, TxType.EXPENSE))
    assertNull(repository.saveSplit(listOf(part(groceries, 100)))) // one part isn't a split
    assertNull(repository.saveSplit(listOf(part(groceries, 100), part(shopping, 0))))
  }

  @Test
  fun rulesAreSavedEditedAndRemovedWithTheirCategory() = runBlocking {
    val (categories, _) = seeded()
    val transport = categories.first { it.name == "Transport" }
    val dining = categories.first { it.name == "Dining" }
    val id = repository.saveRule(PaymentRuleEntity(match = "Metro", categoryId = transport.id))
    assertEquals(listOf("Metro"), repository.rules.first().map { it.match })
    repository.saveRule(PaymentRuleEntity(id = id, match = "Metro", categoryId = transport.id, displayName = "Metro ride"))
    assertEquals("Metro ride", repository.rules.first().single().displayName)
    repository.saveRule(PaymentRuleEntity(match = "Cafe", categoryId = dining.id))
    repository.deleteRule(id)
    assertEquals(listOf("Cafe"), repository.rules.first().map { it.match })
    // a rule can't outlive its category
    assertTrue(repository.deleteCategory(dining, moveTo = null))
    assertTrue(repository.rules.first().isEmpty())
  }

  @Test
  fun rolloverIsSavedWithTheLimits() = runBlocking {
    val (categories, _) = seeded()
    val groceries = categories.first { it.name == "Groceries" }
    repository.setLimits(groceries.id, 60_000, 15_000, rollover = true)
    assertTrue(repository.categories.first().first { it.id == groceries.id }.rollover)
    repository.setLimits(groceries.id, 70_000, 15_000) // not mentioned: unchanged
    assertTrue(repository.categories.first().first { it.id == groceries.id }.rollover)
    repository.setLimits(groceries.id, 70_000, 15_000, rollover = false)
    assertFalse(repository.categories.first().first { it.id == groceries.id }.rollover)
  }

  @Test
  fun sampleDataShowsATransferASplitAndARule() = runBlocking {
    seeded()
    repository.replaceWithSampleData(LocalDate.now(), utc)
    val content = repository.backupContent()
    assertEquals(2, content.transactions.count { it.transferId != null })
    assertEquals(2, content.transactions.count { it.splitId != null })
    assertEquals(listOf("Metro"), content.rules.map { it.match })
  }

  @Test
  fun everythingNewSurvivesABackupAndRestore() = runBlocking {
    seeded()
    repository.replaceWithSampleData(LocalDate.now(), utc)
    val groceries = repository.categories.first().first { it.name == "Groceries" }
    repository.setLimits(groceries.id, 60_000, 15_000, rollover = true)
    val before = repository.backupContent()
    val file = File(context.cacheDir, "round2.kwbackup")
    container.backup.export(Uri.fromFile(file))
    repository.eraseAll()
    val result = container.backup.import(Uri.fromFile(file))
    assertTrue(result.toString(), result is ImportResult.Success)
    assertEquals(before, repository.backupContent())
    assertTrue(repository.categories.first().first { it.name == "Groceries" }.rollover)
  }

  @Test
  fun aBackupWithHalfATransferIsRefused() = runBlocking {
    seeded()
    repository.replaceWithSampleData(LocalDate.now(), utc)
    val before = repository.backupContent()
    val file = File(context.cacheDir, "half.kwbackup")
    container.backup.export(Uri.fromFile(file))
    val text = java.util.zip.ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("data.json")).readBytes().toString(Charsets.UTF_8) }
    val json = org.json.JSONObject(text)
    val array = json.getJSONArray("transactions")
    val keep = org.json.JSONArray()
    var dropped = false
    for (i in 0 until array.length()) {
      val o = array.getJSONObject(i)
      if (!dropped && o.optString("type") == "TRANSFER_IN") { dropped = true; continue }
      keep.put(o)
    }
    json.put("transactions", keep)
    val bad = File(context.cacheDir, "half2.kwbackup")
    java.util.zip.ZipOutputStream(bad.outputStream()).use { zip ->
      zip.putNextEntry(java.util.zip.ZipEntry("data.json"))
      zip.write(json.toString().toByteArray())
      zip.closeEntry()
    }
    val result = container.backup.import(Uri.fromFile(bad))
    assertTrue(result.toString(), result is ImportResult.Invalid)
    assertEquals(before, repository.backupContent())
  }

  @Test
  fun theSpreadsheetNamesTransfersAndTheirTags() {
    val category = CategoryEntity(id = 1, name = "Money transfer", iconKey = "transfer", color = 0)
    val a = AccountEntity(id = 1, name = "Bank", kind = AccountKind.BANK)
    val b = AccountEntity(id = 2, name = "Cash", kind = AccountKind.CASH)
    val tx = listOf(
      TransactionEntity(id = 1, type = TxType.TRANSFER_OUT, amountMinor = 5_000, merchant = "Transfer to Cash", categoryId = 1, accountId = 1, timestamp = at(LocalDate.of(2026, 10, 2)), note = "#cash", transferId = 7),
      TransactionEntity(id = 2, type = TxType.TRANSFER_IN, amountMinor = 5_000, merchant = "Transfer from Bank", categoryId = 1, accountId = 2, timestamp = at(LocalDate.of(2026, 10, 2)), note = "#cash", transferId = 7)
    )
    val lines = container.backup.csv(com.keepwallet.app.data.BackupContent(listOf(a, b), listOf(category), tx, emptyList()), utc).split("\r\n")
    assertTrue(lines[1], lines[1].contains(",Transfer out,50.00,"))
    assertTrue(lines[2], lines[2].contains(",Transfer in,50.00,"))
  }

  @Test
  fun layoutsRearrangeHomeAndKeepTheRest() = runBlocking {
    seeded()
    repository.applyLayout(BuiltInLayouts.minimal.items)
    val widgets = repository.widgets.first().sortedBy { it.position }
    assertEquals(listOf(WidgetKind.BALANCE_CARD, WidgetKind.SPENDING_CHART, WidgetKind.RECENT_ACTIVITY), widgets.take(3).map { it.kind })
    assertEquals(
      setOf(WidgetKind.BALANCE_CARD, WidgetKind.SPENDING_CHART, WidgetKind.RECENT_ACTIVITY),
      widgets.filter { it.enabled }.map { it.kind }.toSet()
    )
    assertEquals(WidgetKind.entries.toSet(), widgets.map { it.kind }.toSet()) // nothing lost, nothing doubled
    assertEquals(widgets.indices.toList(), widgets.map { it.position })

    repository.applyLayout(BuiltInLayouts.billsFocus.items)
    val bills = repository.widgets.first().filter { it.enabled }.map { it.kind }.toSet()
    assertTrue(WidgetKind.UPCOMING_BILLS in bills && WidgetKind.SAVINGS_GOALS in bills && WidgetKind.SPENDING_CHART !in bills)
  }

  @Test
  fun aLayoutAppliedWithADuplicateOrMissingSectionStaysConsistent() = runBlocking {
    seeded()
    // a section that was removed from Home, and another listed twice
    val removed = repository.widgets.first().first { it.kind == WidgetKind.HIGHLIGHTS }
    repository.removeWidget(removed.id)
    repository.applyLayout(listOf(WidgetKind.HIGHLIGHTS to true, WidgetKind.BALANCE_CARD to true, WidgetKind.BALANCE_CARD to false))
    val widgets = repository.widgets.first().sortedBy { it.position }
    assertEquals(WidgetKind.HIGHLIGHTS, widgets[0].kind)
    assertTrue(widgets[0].enabled)
    assertEquals(1, widgets.count { it.kind == WidgetKind.BALANCE_CARD })
    assertTrue(widgets.first { it.kind == WidgetKind.BALANCE_CARD }.enabled)
  }

  @Test
  fun savedLayoutsComeBackAfterReopeningTheSettings() {
    context.getSharedPreferences("keep_wallet_settings", Context.MODE_PRIVATE).edit().clear().commit()
    val settings = SettingsStore(context)
    settings.saveLayout(SavedLayout("Mine", listOf(WidgetKind.BALANCE_CARD to true, WidgetKind.RECENT_ACTIVITY to false)))
    settings.saveLayout(SavedLayout("Other", listOf(WidgetKind.BALANCE_CARD to true)))
    settings.saveLayout(SavedLayout("mine", listOf(WidgetKind.POCKETS_GRID to true))) // the same name replaces
    val again = SettingsStore(context)
    assertEquals(listOf("Other", "mine"), again.layouts.value.map { it.name })
    assertEquals(listOf(WidgetKind.POCKETS_GRID to true), again.layouts.value.last().items)
    again.deleteLayout("OTHER")
    assertEquals(listOf("mine"), SettingsStore(context).layouts.value.map { it.name })
  }

  @Test
  fun aVersion3DatabaseOpensInVersion4WithItsData() = runBlocking {
    val schema = org.json.JSONObject(File("schemas/com.keepwallet.app.data.KeepWalletDatabase/3.json").readText()).getJSONObject("database")
    val path = context.getDatabasePath("v3.db").also { it.parentFile?.mkdirs(); it.delete() }
    val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(path, null)
    val entities = schema.getJSONArray("entities")
    for (i in 0 until entities.length()) {
      val entity = entities.getJSONObject(i)
      val table = entity.getString("tableName")
      raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
      entity.optJSONArray("indices")?.let { indices ->
        for (j in 0 until indices.length()) raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
      }
    }
    raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
    raw.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '${schema.getString("identityHash")}')")
    raw.execSQL("INSERT INTO accounts (id,name,kind,openingBalanceMinor,sortOrder) VALUES (1,'Bank account','BANK',150000,0)")
    raw.execSQL("INSERT INTO categories (id,name,iconKey,color,type,`group`,monthlyLimitMinor,weeklyLimitMinor,sortOrder) VALUES (1,'Housing','housing',-1,'EXPENSE','ESSENTIAL',90000,0,0)")
    raw.execSQL("INSERT INTO transactions (id,type,amountMinor,merchant,categoryId,accountId,timestamp,note,receiptFile) VALUES (1,'EXPENSE',2400,'Rent',1,1,1700000000000,'#home',NULL)")
    raw.version = 3
    raw.close()

    val room = androidx.room.Room.databaseBuilder(context, KeepWalletDatabase::class.java, "v3.db")
      .addMigrations(KeepWalletDatabase.MIGRATION_1_2)
      .allowMainThreadQueries()
      .build()
    try {
      val tx = room.transactionDao().getAll().single()
      assertEquals(2_400L, tx.amountMinor)
      assertNull(tx.transferId)
      assertNull(tx.splitId)
      assertFalse("existing categories start without rollover", room.categoryDao().getAll().single().rollover)
      assertEquals(90_000L, room.categoryDao().getAll().single().monthlyLimitMinor)
      room.paymentRuleDao().insert(PaymentRuleEntity(match = "Metro", categoryId = 1))
      assertEquals(1, room.paymentRuleDao().getAll().size)
    } finally {
      room.close()
    }
  }

  @Test
  fun theDefaultsAreStillIntactAfterEverythingHere() {
    assertEquals(4, DefaultData.VERSION)
  }
}
