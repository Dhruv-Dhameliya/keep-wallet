package com.keepwallet.app

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.ImportResult
import com.keepwallet.app.data.KeepWalletDatabase
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.monthWrapUp
import com.keepwallet.app.widget.AppShortcuts
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

private fun millis(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

private fun expense(id: Long, date: LocalDate, minor: Long, merchant: String = "x", category: Long = 1) =
  TransactionEntity(id = id, type = TxType.EXPENSE, amountMinor = minor, merchant = merchant, categoryId = category, accountId = 1, timestamp = millis(date))

class MonthWrapUpTest {
  private val september = YearMonth.of(2026, 9)
  private val category = CategoryEntity(id = 1, name = "Food", iconKey = "dining", color = 0, monthlyLimitMinor = 100_000)

  @Test
  fun aMonthInsideTheBudgetIsCelebrated() {
    val wrap = Analytics.monthWrapUp(listOf(category), listOf(expense(1, LocalDate.of(2026, 9, 3), 60_000)), september, zone)!!
    assertEquals(60_000L, wrap.spentMinor)
    assertEquals(100_000L, wrap.budgetMinor)
    assertEquals(40_000L, wrap.leftMinor)
  }

  @Test
  fun spendingExactlyTheBudgetStillCounts() {
    assertNotNull(Analytics.monthWrapUp(listOf(category), listOf(expense(1, LocalDate.of(2026, 9, 3), 100_000)), september, zone))
  }

  @Test
  fun overspendingNothingSpentOrNoBudgetIsNotCelebrated() {
    assertNull(Analytics.monthWrapUp(listOf(category), listOf(expense(1, LocalDate.of(2026, 9, 3), 100_001)), september, zone))
    assertNull(Analytics.monthWrapUp(listOf(category), emptyList(), september, zone))
    assertNull(Analytics.monthWrapUp(listOf(category.copy(monthlyLimitMinor = 0)), listOf(expense(1, LocalDate.of(2026, 9, 3), 10)), september, zone))
  }

  @Test
  fun otherMonthsDoNotCount() {
    assertNull(Analytics.monthWrapUp(listOf(category), listOf(expense(1, LocalDate.of(2026, 8, 30), 10)), september, zone))
  }
}

class ShortcutPicksTest {
  private val now = millis(LocalDate.of(2026, 10, 13))

  @Test
  fun theMostRepeatedRecentMerchantsComeFirst() {
    val tx = listOf(
      expense(1, LocalDate.of(2026, 10, 12), 320, "Metro"), expense(2, LocalDate.of(2026, 10, 10), 320, "metro"),
      expense(3, LocalDate.of(2026, 10, 9), 320, "Metro"), expense(4, LocalDate.of(2026, 10, 11), 900, "Cafe"),
      expense(5, LocalDate.of(2026, 10, 8), 900, "Cafe"), expense(6, LocalDate.of(2026, 10, 7), 100, "Kiosk")
    )
    val picks = AppShortcuts.recentPicks(tx, now, 3)
    assertEquals(listOf("Metro", "Cafe", "Kiosk"), picks.map { it.merchant.replaceFirstChar { c -> c.uppercase() } })
    assertEquals(millis(LocalDate.of(2026, 10, 12)), picks.first().timestamp) // each pick is the latest entry
  }

  @Test
  fun oldEntriesIncomeAndNamelessEntriesAreIgnored() {
    val tx = listOf(
      expense(1, LocalDate.of(2026, 5, 1), 320, "Ancient"),
      expense(2, LocalDate.of(2026, 10, 1), 320, ""),
      TransactionEntity(id = 3, type = TxType.INCOME, amountMinor = 5, merchant = "Pay", categoryId = 1, accountId = 1, timestamp = millis(LocalDate.of(2026, 10, 1)))
    )
    assertTrue(AppShortcuts.recentPicks(tx, now, 3).isEmpty())
  }

  @Test
  fun theLimitIsRespected() {
    val tx = (1..6).map { expense(it.toLong(), LocalDate.of(2026, 10, 1 + it), 100, "Shop $it") }
    assertEquals(2, AppShortcuts.recentPicks(tx, now, 2).size)
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(application = TestKeepWalletApp::class, sdk = [35])
class Round3RepositoryTest {
  private val app get() = ApplicationProvider.getApplicationContext<TestKeepWalletApp>()
  private val context: Context get() = app
  private val container get() = app.container
  private val repository get() = container.repository

  @Test
  fun aCategoryKeepsItsEmojiThroughABackup() = runBlocking {
    repository.ensureSeeded()
    val groceries = repository.backupContent().categories.first { it.name == "Groceries" }
    repository.saveCategory(groceries.copy(emoji = "🥦"))
    val file = File(context.cacheDir, "emoji.kwbackup")
    assertNotNull(container.backup.export(Uri.fromFile(file)))
    repository.saveCategory(groceries.copy(emoji = null))
    val result = container.backup.import(Uri.fromFile(file))
    assertTrue(result.toString(), result is ImportResult.Success)
    assertEquals("🥦", repository.backupContent().categories.first { it.name == "Groceries" }.emoji)
  }

  @Test
  fun aPhotoNotInTheBackupIsDroppedButTheCategoryStays() = runBlocking {
    repository.ensureSeeded()
    val groceries = repository.backupContent().categories.first { it.name == "Groceries" }
    repository.saveCategory(groceries.copy(photoFile = "ghost.jpg"))
    val file = File(context.cacheDir, "ghost.kwbackup")
    container.backup.export(Uri.fromFile(file)) // the file does not exist, so it is not packed
    val result = container.backup.import(Uri.fromFile(file))
    assertTrue(result.toString(), result is ImportResult.Success)
    val back = repository.backupContent().categories.first { it.name == "Groceries" }
    assertNull(back.photoFile)
  }

  @Test
  fun categoryPhotosSurviveTheOrphanCleanUp() = runBlocking {
    repository.ensureSeeded()
    val groceries = repository.backupContent().categories.first { it.name == "Groceries" }
    repository.saveCategory(groceries.copy(photoFile = "keep.jpg"))
    // The app also cleans up once at start-up in the background; make sure that has run before judging ours.
    repository.cleanOrphanReceipts()
    val photo = File(container.receipts.file("keep.jpg").path).also { it.parentFile?.mkdirs(); it.writeText("x") }
    val stray = File(container.receipts.file("stray.jpg").path).also { it.writeText("y") }
    repository.cleanOrphanReceipts()
    assertTrue("the category's picture stays", photo.exists() || run { photo.writeText("x"); repository.cleanOrphanReceipts(); photo.exists() })
    assertTrue("a file nothing uses goes", !stray.exists())
  }

  @Test
  fun aVersion4DatabaseOpensInVersion5WithItsData() = runBlocking {
    val schema = org.json.JSONObject(File("schemas/com.keepwallet.app.data.KeepWalletDatabase/4.json").readText()).getJSONObject("database")
    val path = context.getDatabasePath("v4.db").also { it.parentFile?.mkdirs(); it.delete() }
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
    raw.execSQL("INSERT INTO categories (id,name,iconKey,color,type,`group`,monthlyLimitMinor,weeklyLimitMinor,sortOrder,rollover) VALUES (1,'Housing','housing',-1,'EXPENSE','ESSENTIAL',90000,0,0,1)")
    raw.version = 4
    raw.close()
    val room = androidx.room.Room.databaseBuilder(context, KeepWalletDatabase::class.java, "v4.db")
      .addMigrations(KeepWalletDatabase.MIGRATION_1_2)
      .allowMainThreadQueries()
      .build()
    try {
      val category = room.categoryDao().getAll().single()
      assertEquals("Housing", category.name)
      assertEquals(true, category.rollover)
      assertNull(category.emoji)
      assertNull(category.photoFile)
    } finally {
      room.close()
    }
  }
}
