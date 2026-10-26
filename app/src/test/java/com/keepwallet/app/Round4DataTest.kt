package com.keepwallet.app

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.keepwallet.app.data.AccentColor
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.AppPalette
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.Frequency
import com.keepwallet.app.data.ImportResult
import com.keepwallet.app.data.KeepWalletDatabase
import com.keepwallet.app.data.RecurringEntity
import com.keepwallet.app.data.SettingsStore
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.AlertKind
import com.keepwallet.app.domain.Alerts
import com.keepwallet.app.domain.Review
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

private val zone: ZoneId = ZoneId.of("UTC")
private fun at(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
private fun spend(id: Long, date: LocalDate, minor: Long, category: Long = 1, account: Long = 1) =
  TransactionEntity(id = id, type = TxType.EXPENSE, amountMinor = minor, merchant = "x", categoryId = category, accountId = account, timestamp = at(date))
private fun earn(id: Long, date: LocalDate, minor: Long) =
  TransactionEntity(id = id, type = TxType.INCOME, amountMinor = minor, merchant = "Pay", categoryId = 9, accountId = 1, timestamp = at(date))

// ---- What needs attention ------------------------------------------------------------------------

class AlertsTest {
  private val today = LocalDate.of(2026, 10, 14)
  private val food = CategoryEntity(id = 1, name = "Food", iconKey = "dining", color = 0, monthlyLimitMinor = 100_000)

  private fun bill(id: Long, name: String, due: LocalDate, active: Boolean = true, type: TxType = TxType.EXPENSE) = RecurringEntity(
    id = id, name = name, type = type, amountMinor = 50_000, categoryId = 1, accountId = 1, frequency = Frequency.MONTHLY,
    nextDueDay = due.toEpochDay(), anchorDay = due.dayOfMonth, active = active
  )

  private fun compute(detected: Int = 0, bills: List<RecurringEntity> = emptyList(), tx: List<TransactionEntity> = emptyList()) =
    Alerts.compute(detected, bills, listOf(food), tx, today, "$")

  @Test
  fun nothingNeedsAttentionWhenAllIsQuiet() {
    assertTrue(compute().isEmpty())
  }

  @Test
  fun waitingPaymentsAreCountedInOneAlert() {
    val one = compute(detected = 1).single()
    assertEquals(AlertKind.PAYMENTS_WAITING, one.kind)
    assertEquals("1 payment waiting", one.title)
    assertEquals("3 payments waiting", compute(detected = 3).single().title)
  }

  @Test
  fun aBillIsListedFromThreeDaysBeforeItsDueDate() {
    assertTrue(compute(bills = listOf(bill(1, "Rent", today.plusDays(4)))).isEmpty())
    val soon = compute(bills = listOf(bill(1, "Rent", today.plusDays(3)))).single()
    assertEquals(AlertKind.BILL_DUE, soon.kind)
    assertEquals("Rent is due in 3 days", soon.title)
    assertEquals("Rent is due tomorrow", compute(bills = listOf(bill(1, "Rent", today.plusDays(1)))).single().title)
    assertEquals("Rent is due today", compute(bills = listOf(bill(1, "Rent", today))).single().title)
  }

  @Test
  fun anOverdueBillIsSaidToBeLate() {
    val late = compute(bills = listOf(bill(1, "Rent", today.minusDays(1)))).single()
    assertEquals(AlertKind.BILL_OVERDUE, late.kind)
    assertEquals("Rent was due yesterday", late.title)
    assertEquals("Rent was due 5 days ago", compute(bills = listOf(bill(1, "Rent", today.minusDays(5)))).single().title)
  }

  @Test
  fun pausedBillsAndIncomeAreNotAlerts() {
    assertTrue(compute(bills = listOf(bill(1, "Rent", today, active = false))).isEmpty())
    assertTrue(compute(bills = listOf(bill(2, "Salary", today, type = TxType.INCOME))).isEmpty())
  }

  @Test
  fun aBudgetAtEightyPercentIsNearAndAtOneHundredIsOver() {
    assertTrue(compute(tx = listOf(spend(1, today, 79_000))).isEmpty())
    val near = compute(tx = listOf(spend(1, today, 85_000))).single()
    assertEquals(AlertKind.BUDGET_NEAR, near.kind)
    assertEquals("Food is at 85%", near.title)
    val full = compute(tx = listOf(spend(1, today, 100_000))).single()
    assertEquals(AlertKind.BUDGET_OVER, full.kind)
    assertEquals("Food has used its whole budget", full.title)
    assertEquals("Food is over budget", compute(tx = listOf(spend(1, today, 100_001))).single().title)
  }

  @Test
  fun lastMonthsSpendingDoesNotCountAndCategoriesWithoutALimitAreIgnored() {
    assertTrue(compute(tx = listOf(spend(1, today.minusMonths(1), 500_000))).isEmpty())
    val noLimit = Alerts.compute(0, emptyList(), listOf(food.copy(monthlyLimitMinor = 0)), listOf(spend(1, today, 500_000)), today, "$")
    assertTrue(noLimit.isEmpty())
  }

  @Test
  fun eachSituationHasItsOwnKeyAndTheSameSituationTheSameKey() {
    val a = compute(tx = listOf(spend(1, today, 85_000))).single().key
    val b = compute(tx = listOf(spend(1, today, 86_000))).single().key
    val c = compute(tx = listOf(spend(1, today, 100_000))).single().key
    assertEquals(a, b)
    assertFalse(a == c)
  }
}

// ---- The monthly and yearly review ---------------------------------------------------------------

class ReviewTest {
  private val today = LocalDate.of(2026, 10, 14)
  private val account = AccountEntity(id = 1, name = "Bank", kind = AccountKind.BANK, openingBalanceMinor = 100_000)
  private val food = CategoryEntity(id = 1, name = "Food", iconKey = "dining", color = 0, monthlyLimitMinor = 300_000)
  private val fun_ = CategoryEntity(id = 2, name = "Fun", iconKey = "entertainment", color = 0)
  private val pay = CategoryEntity(id = 9, name = "Salary", iconKey = "salary", color = 0, type = TxType.INCOME)

  private val tx = listOf(
    earn(1, LocalDate.of(2026, 10, 1), 500_000),
    spend(2, LocalDate.of(2026, 10, 3), 120_000, category = 1),
    spend(3, LocalDate.of(2026, 10, 9), 30_000, category = 2),
    earn(4, LocalDate.of(2026, 9, 1), 400_000),
    spend(5, LocalDate.of(2026, 9, 5), 450_000, category = 1),
    TransactionEntity(id = 6, type = TxType.TRANSFER_OUT, amountMinor = 99_999, merchant = "t", categoryId = 1, accountId = 1, timestamp = at(LocalDate.of(2026, 10, 2)), transferId = 1)
  )

  private fun month(m: YearMonth, bills: List<RecurringEntity> = emptyList(), now: LocalDate = today) =
    Review.month(tx, listOf(food, fun_, pay), listOf(account), bills, m, now, zone)

  @Test
  fun aMonthAddsUpIncomeAndSpendingButNotTransfers() {
    val r = month(YearMonth.of(2026, 10))
    assertEquals(500_000L, r.incomeMinor)
    assertEquals(150_000L, r.spentMinor)
    assertEquals(350_000L, r.netMinor)
    assertEquals(70, r.savingsRatePercent)
  }

  @Test
  fun theRateIsMissingWithoutIncomeAndNegativeWhenOverspent() {
    assertNull(Review.month(emptyList(), emptyList(), emptyList(), emptyList(), YearMonth.of(2026, 10), today, zone).savingsRatePercent)
    assertEquals(-12, month(YearMonth.of(2026, 9)).savingsRatePercent)
  }

  @Test
  fun topCategoriesAreRankedAndAtMostFive() {
    val r = month(YearMonth.of(2026, 10))
    assertEquals(listOf("Food", "Fun"), r.topCategories.map { it.category.name })
    assertEquals(80, r.topCategories.first().percent)
  }

  @Test
  fun aYearHasTwelveMonthsAndFindsTheBestAndHardest() {
    val r = Review.year(tx, listOf(food, fun_, pay), listOf(account), 2026, today, zone)
    assertEquals(12, r.months.size)
    assertEquals(900_000L, r.incomeMinor)
    assertEquals(600_000L, r.spentMinor)
    assertEquals(YearMonth.of(2026, 10), r.best?.month)
    assertEquals(YearMonth.of(2026, 9), r.worst?.month)
    assertNull(r.forecast)
  }

  @Test
  fun aYearWithNoEntriesHasNoBestOrWorst() {
    val r = Review.year(emptyList(), emptyList(), listOf(account), 2026, today, zone)
    assertNull(r.best)
    assertNull(r.worst)
    assertEquals(0L, r.incomeMinor)
  }

  @Test
  fun netWorthIsTheSumOfAccountsAtEachMonthEndAndNeverLooksAhead() {
    val series = Review.netWorth(listOf(account), tx, YearMonth.of(2026, 12), today, zone)
    assertEquals(12, series.size)
    assertEquals(YearMonth.of(2026, 10), series.last().first) // capped at this month
    // 100,000 opening + income 900,000 - spending 600,000 - transfer out 99,999
    assertEquals(300_001L, series.last().second)
    assertEquals(100_000L + 400_000 - 450_000, series.first { it.first == YearMonth.of(2026, 9) }.second)
  }

  @Test
  fun theForecastExistsOnlyForTheMonthUnderWayAndUsesThePace() {
    assertNull(month(YearMonth.of(2026, 9)).forecast)
    val f = month(YearMonth.of(2026, 10)).forecast!!
    assertEquals(150_000L, f.spentSoFarMinor)
    assertEquals(150_000L / 14, f.dailyAverageMinor)
    assertEquals(17, f.daysLeft)
    assertEquals(150_000L + (150_000L / 14) * 17, f.projectedSpendMinor)
    assertEquals(300_000L, f.budgetMinor)
    assertEquals(300_000L - f.projectedSpendMinor, f.projectedLeftMinor)
  }

  @Test
  fun billsStillDueCountWhenTheyAreMoreThanThePace() {
    val rent = RecurringEntity(
      id = 1, name = "Rent", type = TxType.EXPENSE, amountMinor = 1_000_000, categoryId = 1, accountId = 1,
      frequency = Frequency.MONTHLY, nextDueDay = LocalDate.of(2026, 10, 28).toEpochDay(), anchorDay = 28
    )
    val f = month(YearMonth.of(2026, 10), bills = listOf(rent)).forecast!!
    assertEquals(1_000_000L, f.billsLeftMinor)
    assertEquals(150_000L + 1_000_000L, f.projectedSpendMinor)
  }

  @Test
  fun aBillAlreadyPaidThisMonthDoesNotCountAgain() {
    val paid = RecurringEntity(
      id = 1, name = "Rent", type = TxType.EXPENSE, amountMinor = 1_000_000, categoryId = 1, accountId = 1,
      frequency = Frequency.MONTHLY, nextDueDay = LocalDate.of(2026, 11, 5).toEpochDay(), anchorDay = 5
    )
    assertEquals(0L, month(YearMonth.of(2026, 10), bills = listOf(paid)).forecast!!.billsLeftMinor)
  }
}

// ---- Settings, defaults and storage --------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Round4SettingsTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test
  fun aFreshInstallStartsInBlackAndWhite() {
    val s = SettingsStore(context)
    assertEquals(AccentColor.GRAPHITE, s.accent.value)
    assertEquals(AppPalette.MONO, s.palette.value)
    assertEquals(BackgroundMode.SYSTEM, s.backgroundMode.value)
    assertNull(s.customAccent.value)
  }

  @Test
  fun anOlderInstallIsMovedToTheDefaultOnceAndThenLeftAlone() {
    context.getSharedPreferences("keep_wallet_settings", Context.MODE_PRIVATE).edit().clear()
      .putString("accent_color", "TEAL").putString("background_mode", "DARK").apply()
    val first = SettingsStore(context)
    assertEquals(AccentColor.GRAPHITE, first.accent.value)
    first.setAccent(AccentColor.ROSE)
    first.setBackgroundMode(BackgroundMode.LIGHT)
    val again = SettingsStore(context)
    assertEquals("a later choice is not undone", AccentColor.ROSE, again.accent.value)
    assertEquals(BackgroundMode.LIGHT, again.backgroundMode.value)
  }

  @Test
  fun theBalanceStartsHiddenAndTheEyeOnlyShowsItForNow() {
    val s = SettingsStore(context)
    assertTrue(s.hideOnOpen.value)
    assertTrue(s.hideBalance.value)
    s.toggleBalanceVisibility()
    assertFalse("the eye shows it", s.hideBalance.value)
    assertTrue("but it is not remembered", SettingsStore(context).hideBalance.value)
    s.hideAgainIfNeeded()
    assertTrue("leaving the app hides it again", s.hideBalance.value)
  }

  @Test
  fun withHideOnOpenOffTheEyeIsRemembered() {
    val s = SettingsStore(context)
    s.setHideOnOpen(false)
    s.setHideBalance(false)
    assertFalse(SettingsStore(context).hideBalance.value)
    s.toggleBalanceVisibility()
    assertTrue(SettingsStore(context).hideBalance.value)
    s.hideAgainIfNeeded()
    assertTrue(s.hideBalance.value)
    s.toggleBalanceVisibility()
    s.hideAgainIfNeeded()
    assertFalse("with the setting off, leaving does not hide", s.hideBalance.value)
  }

  @Test
  fun theAlwaysHideSwitchSurvivesAndOverridesTheOpenSetting() {
    val s = SettingsStore(context)
    s.setHideOnOpen(false)
    s.setHideBalance(true)
    assertTrue(SettingsStore(context).hideBalance.value)
  }

  @Test
  fun reminderSwitchesAreOffByDefaultAndRemembered() {
    val s = SettingsStore(context)
    assertFalse(s.remindBills.value)
    assertFalse(s.budgetAlerts.value)
    assertFalse(s.dailyNudge.value)
    assertEquals(20, s.nudgeHour.value)
    s.setRemindBills(true)
    s.setBudgetAlerts(true)
    s.setDailyNudge(true)
    s.setNudgeHour(22)
    val again = SettingsStore(context)
    assertTrue(again.remindBills.value && again.budgetAlerts.value && again.dailyNudge.value)
    assertEquals(22, again.nudgeHour.value)
    again.setNudgeHour(99)
    assertEquals(23, again.nudgeHour.value)
  }
}

// ---- Reminders ----------------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKeepWalletApp::class, sdk = [35])
class RemindersTest {
  private val app get() = ApplicationProvider.getApplicationContext<TestKeepWalletApp>()
  private val container get() = app.container
  private val repository get() = container.repository
  private val manager get() = shadowOf(app.getSystemService(NotificationManager::class.java))

  private val today = LocalDate.now()
  private fun morning() = LocalDateTime.of(today, java.time.LocalTime.of(10, 0))
  private fun evening() = LocalDateTime.of(today, java.time.LocalTime.of(21, 0))

  private fun setup() = runBlocking {
    repository.ensureSeeded()
    container.reminders.ensureChannel()
    val categories = repository.categories.first()
    val account = repository.accounts.first().first()
    categories to account
  }

  private fun titles() = manager.allNotifications.map { it.extras.getString(android.app.Notification.EXTRA_TITLE).orEmpty() }

  @Test
  fun nothingIsSentUnlessSwitchedOn() = runBlocking {
    val (_, account) = setup()
    repository.saveRecurring(RecurringEntity(name = "Rent", amountMinor = 90_000, categoryId = repository.categories.first().first().id, accountId = account.id, nextDueDay = today.toEpochDay(), anchorDay = today.dayOfMonth))
    container.reminders.runDue(morning())
    assertTrue(titles().isEmpty())
  }

  @Test
  fun aBillDueTomorrowIsRemindedOnceAndNotAgain() = runBlocking {
    val (categories, account) = setup()
    container.settings.setRemindBills(true)
    repository.saveRecurring(RecurringEntity(name = "Rent", amountMinor = 90_000, categoryId = categories.first().id, accountId = account.id, nextDueDay = today.plusDays(1).toEpochDay(), anchorDay = today.plusDays(1).dayOfMonth))
    container.reminders.runDue(morning())
    assertEquals(listOf("Rent is due tomorrow"), titles())
    container.reminders.runDue(morning())
    assertEquals("not twice for the same bill and day", 1, titles().size)
  }

  @Test
  fun billsWaitUntilMorning() = runBlocking {
    val (categories, account) = setup()
    container.settings.setRemindBills(true)
    repository.saveRecurring(RecurringEntity(name = "Rent", amountMinor = 90_000, categoryId = categories.first().id, accountId = account.id, nextDueDay = today.toEpochDay(), anchorDay = today.dayOfMonth))
    container.reminders.runDue(LocalDateTime.of(today, java.time.LocalTime.of(6, 0)))
    assertTrue(titles().isEmpty())
    container.reminders.runDue(morning())
    assertEquals(1, titles().size)
  }

  @Test
  fun withTheAppLockOnNothingPrivateIsShown() = runBlocking {
    val (categories, account) = setup()
    container.settings.setRemindBills(true)
    container.settings.enableLock("1234", false)
    repository.saveRecurring(RecurringEntity(name = "Rent", amountMinor = 90_000, categoryId = categories.first().id, accountId = account.id, nextDueDay = today.toEpochDay(), anchorDay = today.dayOfMonth))
    container.reminders.runDue(morning())
    assertEquals(listOf("Keep Wallet"), titles())
  }

  @Test
  fun aBudgetAlertComesWhenEntriesTakeItToEightyAndThenToOneHundred() = runBlocking {
    val (categories, account) = setup()
    container.settings.setBudgetAlerts(true)
    val food = categories.first { it.type == TxType.EXPENSE }
    repository.saveCategory(food.copy(monthlyLimitMinor = 100_000))
    repository.saveTransaction(TransactionEntity(type = TxType.EXPENSE, amountMinor = 50_000, merchant = "a", categoryId = food.id, accountId = account.id, timestamp = System.currentTimeMillis()))
    container.reminders.afterEntrySaved()
    assertTrue("50% is quiet", titles().isEmpty())
    repository.saveTransaction(TransactionEntity(type = TxType.EXPENSE, amountMinor = 35_000, merchant = "b", categoryId = food.id, accountId = account.id, timestamp = System.currentTimeMillis()))
    container.reminders.afterEntrySaved()
    assertEquals(1, titles().size)
    assertTrue(titles().single().contains("85%"))
    container.reminders.afterEntrySaved()
    assertEquals("the same level is not repeated", 1, titles().size)
    repository.saveTransaction(TransactionEntity(type = TxType.EXPENSE, amountMinor = 20_000, merchant = "c", categoryId = food.id, accountId = account.id, timestamp = System.currentTimeMillis()))
    container.reminders.afterEntrySaved()
    assertEquals(2, titles().size)
    assertTrue(titles().any { it.contains("over budget") })
  }

  @Test
  fun theEveningNudgeIsSentOnlyIfNothingWasAddedToday() = runBlocking {
    val (categories, account) = setup()
    container.settings.setDailyNudge(true)
    container.reminders.runDue(LocalDateTime.of(today, java.time.LocalTime.of(19, 0)))
    assertTrue("before the chosen hour", titles().isEmpty())
    container.reminders.runDue(evening())
    assertEquals(1, titles().size)
    container.reminders.runDue(evening())
    assertEquals("once a day", 1, titles().size)
  }

  @Test
  fun noNudgeWhenSomethingWasAlreadyAddedToday() = runBlocking {
    val (categories, account) = setup()
    container.settings.setDailyNudge(true)
    repository.saveTransaction(TransactionEntity(type = TxType.EXPENSE, amountMinor = 100, merchant = "a", categoryId = categories.first().id, accountId = account.id, timestamp = System.currentTimeMillis()))
    container.reminders.runDue(evening())
    assertTrue(titles().isEmpty())
  }
}

// ---- Entries in another currency -------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKeepWalletApp::class, sdk = [35])
class ForeignCurrencyDataTest {
  private val app get() = ApplicationProvider.getApplicationContext<TestKeepWalletApp>()
  private val context: Context get() = app
  private val container get() = app.container
  private val repository get() = container.repository

  @Test
  fun anEntryKeepsItsOriginalAmountThroughABackup() = runBlocking {
    repository.ensureSeeded()
    val category = repository.categories.first().first { it.type == TxType.EXPENSE }
    val account = repository.accounts.first().first()
    repository.saveTransaction(
      TransactionEntity(type = TxType.EXPENSE, amountMinor = 91_200, merchant = "Cafe", categoryId = category.id, accountId = account.id, timestamp = System.currentTimeMillis(), foreignCode = "EUR", foreignMinor = 1_000)
    )
    val file = File(context.cacheDir, "foreign.kwbackup")
    assertNotNull(container.backup.export(Uri.fromFile(file)))
    repository.eraseAll()
    val result = container.backup.import(Uri.fromFile(file))
    assertTrue(result.toString(), result is ImportResult.Success)
    val back = repository.transactions.first().single { it.merchant == "Cafe" }
    assertEquals("EUR", back.foreignCode)
    assertEquals(1_000L, back.foreignMinor)
    assertEquals(91_200L, back.amountMinor)
  }

  @Test
  fun aVersion5DatabaseOpensInVersion6WithItsData() = runBlocking {
    val schema = org.json.JSONObject(File("schemas/com.keepwallet.app.data.KeepWalletDatabase/5.json").readText()).getJSONObject("database")
    val path = context.getDatabasePath("v5.db").also { it.parentFile?.mkdirs(); it.delete() }
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
    raw.execSQL("INSERT INTO categories (id,name,iconKey,color,type,`group`,monthlyLimitMinor,weeklyLimitMinor,sortOrder,rollover) VALUES (1,'Housing','housing',-1,'EXPENSE','ESSENTIAL',90000,0,0,0)")
    raw.execSQL("INSERT INTO transactions (id,type,amountMinor,merchant,categoryId,accountId,timestamp,note,receiptFile) VALUES (1,'EXPENSE',2400,'Rent',1,1,1700000000000,NULL,NULL)")
    raw.version = 5
    raw.close()
    val room = androidx.room.Room.databaseBuilder(context, KeepWalletDatabase::class.java, "v5.db")
      .addMigrations(KeepWalletDatabase.MIGRATION_1_2)
      .allowMainThreadQueries()
      .build()
    try {
      val tx = room.transactionDao().getAll().single()
      assertEquals(2_400L, tx.amountMinor)
      assertNull(tx.foreignCode)
      assertNull(tx.foreignMinor)
    } finally {
      room.close()
    }
  }
}
