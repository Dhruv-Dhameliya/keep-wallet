package com.keepwallet.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.DashboardWidgetEntity
import com.keepwallet.app.data.DefaultData
import com.keepwallet.app.data.Frequency
import com.keepwallet.app.data.GoalEntity
import com.keepwallet.app.data.ImportResult
import com.keepwallet.app.data.KeepWalletDatabase
import com.keepwallet.app.data.KeepWalletRepository
import com.keepwallet.app.data.RecurringEntity
import com.keepwallet.app.data.ReceiptStore
import com.keepwallet.app.data.SettingsStore
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.Recurrence
import com.keepwallet.app.domain.balanceAsOf
import com.keepwallet.app.domain.endOfMonthMillis
import com.keepwallet.app.domain.frequentEntries
import com.keepwallet.app.domain.highlights
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

private val zone: ZoneId = ZoneId.of("UTC")

private fun millis(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

private fun expense(id: Long, date: LocalDate, minor: Long, merchant: String = "Shop", category: Long = 1, account: Long = 1, hour: Int = 12) =
  TransactionEntity(id = id, type = TxType.EXPENSE, amountMinor = minor, merchant = merchant, categoryId = category, accountId = account, timestamp = millis(date, hour))

// ---- Dates for bills that repeat ----------------------------------------------------------------

class RecurrenceTest {
  @Test
  fun `monthly bills stay on their day even through short months`() {
    var day = LocalDate.of(2026, 1, 31)
    val seen = mutableListOf(day)
    repeat(4) {
      day = Recurrence.next(day, Frequency.MONTHLY, 31)
      seen += day
    }
    assertEquals(
      listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30), LocalDate.of(2026, 5, 31)),
      seen
    )
  }

  @Test
  fun `weekly and yearly bills`() {
    assertEquals(LocalDate.of(2026, 10, 20), Recurrence.next(LocalDate.of(2026, 10, 13), Frequency.WEEKLY, 13))
    assertEquals(LocalDate.of(2027, 2, 28), Recurrence.next(LocalDate.of(2026, 2, 28), Frequency.YEARLY, 28))
    // a leap day comes back on the 29th in leap years and the 28th otherwise
    assertEquals(LocalDate.of(2025, 2, 28), Recurrence.next(LocalDate.of(2024, 2, 29), Frequency.YEARLY, 29))
    assertEquals(LocalDate.of(2028, 2, 29), Recurrence.next(LocalDate.of(2027, 2, 28), Frequency.YEARLY, 29))
  }

  @Test
  fun `occurrences cover every missed date up to today and no more`() {
    val dates = Recurrence.occurrences(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 10, 13), Frequency.MONTHLY, 1)
    assertEquals(listOf(7, 8, 9, 10), dates.map { it.monthValue })
    assertTrue(Recurrence.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 13), Frequency.MONTHLY, 1).isEmpty())
    assertEquals(120, Recurrence.occurrences(LocalDate.of(1990, 1, 1), LocalDate.of(2026, 1, 1), Frequency.WEEKLY, 1).size) // capped
  }

  @Test
  fun `due text reads naturally`() {
    val today = LocalDate.of(2026, 10, 13)
    assertEquals("Due today", Recurrence.dueText(today, today))
    assertEquals("Due tomorrow", Recurrence.dueText(today.plusDays(1), today))
    assertEquals("Due in 5 days", Recurrence.dueText(today.plusDays(5), today))
    assertEquals("1 day overdue", Recurrence.dueText(today.minusDays(1), today))
    assertEquals("3 days overdue", Recurrence.dueText(today.minusDays(3), today))
  }
}

// ---- Figures for Highlights, history and habits ---------------------------------------------------

class HighlightsMathTest {
  private val today = LocalDate.of(2026, 10, 10)
  private val october = YearMonth.of(2026, 10)

  @Test
  fun `a month in progress is averaged over the days so far and forecast to the end`() {
    val tx = listOf(
      expense(1, LocalDate.of(2026, 10, 2), 10_000),
      expense(2, LocalDate.of(2026, 10, 2), 5_000),
      expense(3, LocalDate.of(2026, 10, 7), 35_000),
      TransactionEntity(id = 4, type = TxType.INCOME, amountMinor = 200_000, merchant = "Pay", categoryId = 2, accountId = 1, timestamp = millis(LocalDate.of(2026, 10, 1)))
    )
    val h = Analytics.highlights(tx, october, today, zone)
    assertEquals(50_000L, h.expenseMinor)
    assertEquals(10, h.elapsedDays)
    assertEquals(5_000L, h.dailyAverageMinor)
    assertEquals(155_000L, h.projectedMinor) // 5,000 a day over 31 days
    assertEquals(8, h.noSpendDays) // two days with spending out of ten
    assertEquals(35_000L, h.biggest?.amountMinor)
    assertEquals(75, h.savingsPercent) // saved 150,000 of 200,000
  }

  @Test
  fun `the previous month is compared over the same days, not as a whole`() {
    val tx = listOf(
      expense(1, LocalDate.of(2026, 9, 3), 20_000),
      expense(2, LocalDate.of(2026, 9, 25), 80_000), // after day 10: must not count
      expense(3, LocalDate.of(2026, 10, 4), 30_000)
    )
    val h = Analytics.highlights(tx, october, today, zone)
    assertEquals(20_000L, h.previousSameStretchMinor)
    assertEquals(50, h.changePercent) // 30,000 against 20,000
  }

  @Test
  fun `a finished month uses all of it and forecast equals the total`() {
    val september = YearMonth.of(2026, 9)
    val tx = listOf(expense(1, LocalDate.of(2026, 9, 3), 20_000), expense(2, LocalDate.of(2026, 9, 25), 10_000))
    val h = Analytics.highlights(tx, september, today, zone)
    assertEquals(30, h.elapsedDays)
    assertEquals(1_000L, h.dailyAverageMinor)
    assertEquals(30_000L, h.projectedMinor)
    assertEquals(28, h.noSpendDays)
  }

  @Test
  fun `a month still to come and an empty month do not divide by zero`() {
    val h = Analytics.highlights(emptyList(), YearMonth.of(2026, 12), today, zone)
    assertEquals(0, h.elapsedDays)
    assertEquals(0L, h.dailyAverageMinor)
    assertEquals(0L, h.projectedMinor)
    assertNull(h.changePercent)
    assertNull(h.savingsPercent)
    assertNull(h.biggest)
  }

  @Test
  fun `balance as of a month end ignores later entries`() {
    val account = AccountEntity(id = 1, name = "Bank", kind = AccountKind.BANK, openingBalanceMinor = 100_000)
    val tx = listOf(
      expense(1, LocalDate.of(2026, 8, 15), 10_000),
      expense(2, LocalDate.of(2026, 9, 15), 20_000),
      expense(3, LocalDate.of(2026, 10, 5), 5_000)
    )
    val endOfAugust = Analytics.endOfMonthMillis(YearMonth.of(2026, 8), zone)
    val endOfSeptember = Analytics.endOfMonthMillis(YearMonth.of(2026, 9), zone)
    assertEquals(90_000L, Analytics.balanceAsOf(account, tx, endOfAugust))
    assertEquals(70_000L, Analytics.balanceAsOf(account, tx, endOfSeptember))
    assertEquals(65_000L, Analytics.accountBalance(account, tx))
    // the last moment of the month still counts, the first moment of the next doesn't
    val lateAugust = listOf(expense(9, LocalDate.of(2026, 8, 31), 1_000, hour = 23))
    assertEquals(99_000L, Analytics.balanceAsOf(account, lateAugust, endOfAugust))
    val earlySeptember = listOf(expense(9, LocalDate.of(2026, 9, 1), 1_000, hour = 0))
    assertEquals(100_000L, Analytics.balanceAsOf(account, earlySeptember, endOfAugust))
  }

  @Test
  fun `frequent entries are the ones used twice or more, most used first, with their latest details`() {
    // newest first, as the database returns them
    val tx = listOf(
      expense(1, LocalDate.of(2026, 10, 9), 450, "Corner Café", category = 4, account = 2),
      expense(2, LocalDate.of(2026, 10, 8), 3_200, "Green Market", category = 2, account = 1),
      expense(3, LocalDate.of(2026, 10, 7), 400, "corner café ", category = 4, account = 2),
      expense(4, LocalDate.of(2026, 10, 6), 3_000, "Green Market", category = 2, account = 1),
      expense(5, LocalDate.of(2026, 10, 5), 2_900, "Green Market", category = 2, account = 1),
      expense(6, LocalDate.of(2026, 10, 4), 99_900, "One-off laptop", category = 6, account = 1),
      TransactionEntity(id = 7, type = TxType.INCOME, amountMinor = 1, merchant = "Green Market", categoryId = 9, accountId = 1, timestamp = 0)
    )
    val result = Analytics.frequentEntries(tx, TxType.EXPENSE)
    assertEquals(listOf("Green Market", "Corner Café"), result.map { it.merchant })
    assertEquals(3, result[0].count)
    assertEquals(3_200L, result[0].lastAmountMinor) // the latest, not an average
    assertEquals(2L, result[1].accountId)
    assertTrue(Analytics.frequentEntries(tx, TxType.INCOME).isEmpty())
    assertEquals(1, Analytics.frequentEntries(tx, TxType.EXPENSE, limit = 1).size)
  }
}

// ---- Bills, goals, upgrade, backup and CSV on a real (in-memory) database ----------------------------

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKeepWalletApp::class, sdk = [35])
class RecurringAndGoalsTest {
  private val app get() = ApplicationProvider.getApplicationContext<TestKeepWalletApp>()
  private val context: Context get() = app
  private val container get() = app.container
  private val repository get() = container.repository

  private fun seeded() = runBlocking {
    repository.ensureSeeded()
    repository.backupContent().let { it.categories to it.accounts }
  }

  private fun bill(categoryId: Long, accountId: Long, due: LocalDate, auto: Boolean = false, frequency: Frequency = Frequency.MONTHLY, name: String = "Rent", amount: Long = 120_000) =
    RecurringEntity(
      name = name, amountMinor = amount, categoryId = categoryId, accountId = accountId, frequency = frequency,
      nextDueDay = due.toEpochDay(), anchorDay = due.dayOfMonth, autoPost = auto
    )

  @Test
  fun markingABillPaidBooksTodayAndMovesItToTheNextDate() = runBlocking {
    val (categories, accounts) = seeded()
    val housing = categories.first { it.name == "Housing" }
    val id = repository.saveRecurring(bill(housing.id, accounts.first().id, LocalDate.of(2026, 10, 1)))
    val today = LocalDate.of(2026, 10, 13)

    val txId = repository.payRecurring(id, today, zone)
    assertNotNull(txId)
    val booked = repository.getTransaction(txId!!)!!
    assertEquals(120_000L, booked.amountMinor)
    assertEquals("Rent", booked.merchant)
    assertEquals(housing.id, booked.categoryId)
    assertEquals(today, Periods.millisToDate(booked.timestamp, zone))
    assertEquals(LocalDate.of(2026, 11, 1).toEpochDay(), repository.recurring.first().single().nextDueDay)
    assertNull(repository.payRecurring(9_999, today, zone)) // a bill that no longer exists
  }

  @Test
  fun skippingMovesOnWithoutBookingAnything() = runBlocking {
    val (categories, accounts) = seeded()
    val id = repository.saveRecurring(bill(categories.first().id, accounts.first().id, LocalDate.of(2026, 10, 31), name = "Gym"))
    repository.skipRecurring(id)
    assertEquals(LocalDate.of(2026, 11, 30).toEpochDay(), repository.recurring.first().single().nextDueDay)
    assertTrue(repository.transactions.first().isEmpty())
  }

  @Test
  fun automaticBillsCatchUpOnEveryMissedDateAndManualOnesAreLeftAlone() = runBlocking {
    val (categories, accounts) = seeded()
    val cat = categories.first().id
    val acc = accounts.first().id
    repository.saveRecurring(bill(cat, acc, LocalDate.of(2026, 7, 5), auto = true, name = "Streamly", amount = 999))
    repository.saveRecurring(bill(cat, acc, LocalDate.of(2026, 7, 5), auto = false, name = "Manual", amount = 5_000))
    repository.saveRecurring(bill(cat, acc, LocalDate.of(2026, 7, 5), auto = true, name = "Paused", amount = 777).copy(active = false))

    val added = repository.postDueRecurring(LocalDate.of(2026, 10, 13), zone)

    assertEquals(4, added) // Jul, Aug, Sep, Oct
    val booked = repository.transactions.first()
    assertEquals(setOf("Streamly"), booked.map { it.merchant }.toSet())
    assertEquals(listOf(7, 8, 9, 10), booked.map { Periods.millisToDate(it.timestamp, zone).monthValue }.sorted())
    assertEquals(LocalDate.of(2026, 11, 5).toEpochDay(), repository.recurring.first().first { it.name == "Streamly" }.nextDueDay)
    assertEquals(LocalDate.of(2026, 7, 5).toEpochDay(), repository.recurring.first().first { it.name == "Manual" }.nextDueDay)
    // running again the same day adds nothing
    assertEquals(0, repository.postDueRecurring(LocalDate.of(2026, 10, 13), zone))
    assertEquals(4, repository.transactions.first().size)
  }

  @Test
  fun deletingACategoryMovesItsBillsAlongOrRemovesThemWithIt() = runBlocking {
    val (categories, accounts) = seeded()
    val expenseCats = categories.filter { it.type == TxType.EXPENSE }
    val from = expenseCats[0]
    val to = expenseCats[1]
    repository.saveRecurring(bill(from.id, accounts.first().id, LocalDate.of(2026, 11, 1), name = "A"))
    repository.saveRecurring(bill(expenseCats[2].id, accounts.first().id, LocalDate.of(2026, 11, 1), name = "B"))
    assertTrue(repository.deleteCategory(from, moveTo = to.id))
    assertEquals(to.id, repository.recurring.first().first { it.name == "A" }.categoryId)
    // no transactions use it, so it can go without a destination; its bill leaves with it
    assertTrue(repository.deleteCategory(expenseCats[2], moveTo = null))
    assertEquals(listOf("A"), repository.recurring.first().map { it.name })
  }

  @Test
  fun goalsNeverGoBelowZeroAndKeepTheirTotals() = runBlocking {
    seeded()
    val id = repository.saveGoal(GoalEntity(name = "Trip", targetMinor = 200_000, savedMinor = 10_000, color = 0))
    assertEquals(60_000L, repository.addToGoal(id, 50_000))
    assertEquals(0L, repository.addToGoal(id, -500_000)) // taking out more than is there stops at zero
    assertEquals(0L, repository.goals.first().single().savedMinor)
    assertNull(repository.addToGoal(9_999, 100))
    repository.deleteGoal(id)
    assertTrue(repository.goals.first().isEmpty())
  }

  @Test
  fun sampleDataShowsBillsAndGoalsAndBackupCarriesThem() = runBlocking {
    seeded()
    repository.replaceWithSampleData(LocalDate.now(), zone)
    val before = repository.backupContent()
    assertTrue(before.recurring.size >= 3)
    assertTrue(before.goals.size >= 3)
    assertTrue(before.recurring.all { LocalDate.ofEpochDay(it.nextDueDay).isAfter(LocalDate.now()) })

    val file = File(context.cacheDir, "bills.kwbackup")
    assertEquals(before.transactions.size, container.backup.export(Uri.fromFile(file)))
    repository.eraseAll()
    assertTrue(repository.backupContent().recurring.isEmpty() && repository.backupContent().goals.isEmpty())
    val result = container.backup.import(Uri.fromFile(file))
    assertTrue(result.toString(), result is ImportResult.Success)
    assertEquals(before, repository.backupContent())
  }

  @Test
  fun anOldBackupWithoutBillsOrGoalsStillImports() = runBlocking {
    seeded()
    val file = File(context.cacheDir, "v1.kwbackup")
    container.backup.export(Uri.fromFile(file))
    val text = java.util.zip.ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("data.json")).readBytes().toString(Charsets.UTF_8) }
    val old = org.json.JSONObject(text).apply { remove("recurring"); remove("goals"); put("version", 1) }.toString()
    val oldFile = File(context.cacheDir, "old.kwbackup")
    java.util.zip.ZipOutputStream(oldFile.outputStream()).use { zip ->
      zip.putNextEntry(java.util.zip.ZipEntry("data.json"))
      zip.write(old.toByteArray())
      zip.closeEntry()
    }
    val result = container.backup.import(Uri.fromFile(oldFile))
    assertTrue(result.toString(), result is ImportResult.Success)
    assertTrue(repository.backupContent().recurring.isEmpty())
  }

  @Test
  fun aBackupWithABrokenBillIsRefusedAndChangesNothing() = runBlocking {
    seeded()
    val (categories, accounts) = repository.backupContent().let { it.categories to it.accounts }
    repository.saveRecurring(bill(categories.first().id, accounts.first().id, LocalDate.of(2026, 11, 1)))
    val before = repository.backupContent()
    val file = File(context.cacheDir, "bad.kwbackup")
    container.backup.export(Uri.fromFile(file))
    val text = java.util.zip.ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry("data.json")).readBytes().toString(Charsets.UTF_8) }
    val broken = org.json.JSONObject(text)
    broken.getJSONArray("recurring").getJSONObject(0).put("categoryId", 987_654)
    val badFile = File(context.cacheDir, "bad2.kwbackup")
    java.util.zip.ZipOutputStream(badFile.outputStream()).use { zip ->
      zip.putNextEntry(java.util.zip.ZipEntry("data.json"))
      zip.write(broken.toString().toByteArray())
      zip.closeEntry()
    }
    val result = container.backup.import(Uri.fromFile(badFile))
    assertTrue(result.toString(), result is ImportResult.Invalid)
    assertEquals(before, repository.backupContent())
  }

  @Test
  fun csvListsEveryEntryOldestFirstAndDefusesFormulas() {
    val category = CategoryEntity(id = 1, name = "Dining, out", iconKey = "dining", color = 0)
    val account = AccountEntity(id = 1, name = "Bank \"main\"", kind = AccountKind.BANK)
    val content = com.keepwallet.app.data.BackupContent(
      listOf(account), listOf(category),
      listOf(
        TransactionEntity(id = 2, type = TxType.EXPENSE, amountMinor = 1_250, merchant = "=SUM(A1)", categoryId = 1, accountId = 1, timestamp = millis(LocalDate.of(2026, 10, 2)), note = "line\nbreak"),
        TransactionEntity(id = 1, type = TxType.INCOME, amountMinor = 480_000, merchant = "Pay", categoryId = 1, accountId = 1, timestamp = millis(LocalDate.of(2026, 10, 1)))
      ),
      emptyList()
    )
    val lines = container.backup.csv(content, zone).split("\r\n")
    assertEquals("Date,Time,Type,Amount,Category,Account,Name,Note", lines[0])
    assertEquals("2026-10-01,12:00,Income,4800.00,\"Dining, out\",\"Bank \"\"main\"\"\",Pay,", lines[1])
    assertTrue(lines[2].startsWith("2026-10-02,12:00,Expense,12.50,"))
    assertTrue("formula must be defused: ${lines[2]}", lines[2].contains(",'=SUM(A1),"))
    assertTrue(lines[2].contains("\"line\nbreak\""))
  }

  @Test
  fun anInstallFromTheLastVersionKeepsItsHomeLayoutAndGetsTheNewSectionsSwitchedOff() = runBlocking {
    awaitSeeded()
    context.getSharedPreferences("keep_wallet_settings", Context.MODE_PRIVATE).edit().clear().commit()
    val settings = SettingsStore(context)
    settings.isSeeded = true
    settings.defaultsVersion = 2
    val db = KeepWalletDatabase.inMemory(context)
    val repo = KeepWalletRepository(db, settings, ReceiptStore(context))
    db.categoryDao().insertAll(DefaultData.categories())
    db.accountDao().insertAll(DefaultData.accounts())
    // the user's own arrangement: pockets first, the graph hidden, an extra week section
    db.widgetDao().insertAll(
      listOf(
        DashboardWidgetEntity(kind = WidgetKind.POCKETS_GRID, position = 0),
        DashboardWidgetEntity(kind = WidgetKind.BALANCE_CARD, position = 1),
        DashboardWidgetEntity(kind = WidgetKind.SPENDING_CHART, position = 2, enabled = false),
        DashboardWidgetEntity(kind = WidgetKind.WEEK_OVERVIEW, position = 3)
      )
    )

    repo.ensureSeeded()

    val widgets = db.widgetDao().getAll()
    assertEquals(
      listOf(WidgetKind.POCKETS_GRID, WidgetKind.BALANCE_CARD, WidgetKind.SPENDING_CHART, WidgetKind.WEEK_OVERVIEW),
      widgets.take(4).map { it.kind }
    )
    assertFalse(widgets.first { it.kind == WidgetKind.SPENDING_CHART }.enabled)
    assertTrue(widgets.first { it.kind == WidgetKind.WEEK_OVERVIEW }.enabled)
    val added = widgets.drop(4)
    assertEquals(
      setOf(WidgetKind.RECENT_ACTIVITY, WidgetKind.MONTH_OVERVIEW, WidgetKind.QUICK_STATS, WidgetKind.CATEGORY_BREAKDOWN, WidgetKind.HIGHLIGHTS, WidgetKind.UPCOMING_BILLS, WidgetKind.SAVINGS_GOALS),
      added.map { it.kind }.toSet()
    )
    assertTrue("sections added by an upgrade start switched off", added.none { it.enabled })
    assertEquals((0 until widgets.size).toList(), widgets.map { it.position })
    assertEquals(DefaultData.VERSION, settings.defaultsVersion)
    // and doing it again changes nothing
    val snapshot = repo.backupContent()
    repo.ensureSeeded()
    assertEquals(snapshot, repo.backupContent())
    db.close()
  }

  private fun awaitSeeded() {
    val deadline = System.currentTimeMillis() + 10_000
    while (System.currentTimeMillis() < deadline && !(app.container.settings.isSeeded && app.container.settings.defaultsVersion >= DefaultData.VERSION)) Thread.sleep(25)
    Thread.sleep(150)
  }

  @Test
  fun aVersion2DatabaseOpensInVersion3WithItsDataAndTheNewTables() = runBlocking {
    awaitSeeded()
    val schema = org.json.JSONObject(File("schemas/com.keepwallet.app.data.KeepWalletDatabase/2.json").readText()).getJSONObject("database")
    val path = context.getDatabasePath("v2.db").also { it.parentFile?.mkdirs(); it.delete() }
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
    raw.execSQL("INSERT INTO categories (id,name,iconKey,color,type,`group`,monthlyLimitMinor,weeklyLimitMinor,sortOrder) VALUES (1,'Housing','housing',-1,'EXPENSE','ESSENTIAL',0,0,0)")
    raw.execSQL("INSERT INTO transactions (id,type,amountMinor,merchant,categoryId,accountId,timestamp,note,receiptFile) VALUES (1,'EXPENSE',2400,'Rent',1,1,1700000000000,NULL,NULL)")
    raw.execSQL("INSERT INTO dashboard_widgets (id,kind,enabled,position,chartType,`range`) VALUES (1,'BALANCE_CARD',1,0,'BAR','WEEK')")
    raw.version = 2
    raw.close()

    val room = androidx.room.Room.databaseBuilder(context, KeepWalletDatabase::class.java, "v2.db")
      .addMigrations(KeepWalletDatabase.MIGRATION_1_2)
      .allowMainThreadQueries()
      .build()
    try {
      assertEquals(2_400L, room.transactionDao().getAll().single().amountMinor)
      assertEquals(150_000L, room.accountDao().getAll().single().openingBalanceMinor)
      assertEquals(WidgetKind.BALANCE_CARD, room.widgetDao().getAll().single().kind)
      // the new tables exist and work
      val billId = room.recurringDao().insert(bill(1, 1, LocalDate.of(2026, 11, 1)))
      val goalId = room.goalDao().insert(GoalEntity(name = "Trip", targetMinor = 1_000, color = 0))
      assertEquals(billId, room.recurringDao().getAll().single().id)
      assertEquals(goalId, room.goalDao().getAll().single().id)
    } finally {
      room.close()
    }
  }
}
