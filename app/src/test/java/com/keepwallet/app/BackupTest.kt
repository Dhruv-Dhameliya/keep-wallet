package com.keepwallet.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.keepwallet.app.data.AccentColor
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.ChartType
import com.keepwallet.app.data.ImportResult
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Export and import: a full round trip, plus every way a bad file must be refused without touching the data. */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestKeepWalletApp::class, sdk = [35])
class BackupTest {
  private val app get() = ApplicationProvider.getApplicationContext<TestKeepWalletApp>()
  private val container get() = app.container
  private val repository get() = container.repository

  private fun file(name: String) = File(app.cacheDir, name)
  private fun uri(file: File) = Uri.fromFile(file)

  private fun zipOf(file: File, entries: Map<String, ByteArray>) {
    ZipOutputStream(file.outputStream()).use { zip ->
      entries.forEach { (name, bytes) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
      }
    }
  }

  private fun seed() = runBlocking {
    repository.ensureSeeded()
    repository.replaceWithSampleData(LocalDate.now())
  }

  @Test
  fun everythingSurvivesAnExportAndImport() = runBlocking {
    seed()
    container.settings.setAccent(AccentColor.VIOLET)
    container.settings.setBackgroundMode(BackgroundMode.BLACK)
    container.settings.setCustomAccent(0xFF336699.toInt())
    container.settings.setChartType(ChartType.LINE)
    container.settings.setShowTotalOnHero(true)
    container.settings.setCurrencySymbol("€")

    // one entry with a receipt photo
    val receiptBytes = byteArrayOf(1, 2, 3, 4, 5)
    container.receipts.file("receipt-a.jpg").writeBytes(receiptBytes)
    val content = repository.backupContent()
    repository.saveTransaction(
      TransactionEntity(
        type = TxType.EXPENSE, amountMinor = 4_250, merchant = "With receipt", categoryId = content.categories.first().id,
        accountId = content.accounts.first().id, timestamp = System.currentTimeMillis(),
        note = "Line one – ünïcode ✓", receiptFile = "receipt-a.jpg"
      )
    )
    val before = repository.backupContent()

    val backup = file("round.kwbackup")
    val saved = container.backup.export(uri(backup))
    assertEquals(before.transactions.size, saved)

    // wipe everything, including the settings, then restore
    repository.eraseAll()
    container.receipts.file("receipt-a.jpg").delete()
    container.settings.setAccent(AccentColor.TEAL)
    container.settings.setBackgroundMode(BackgroundMode.SYSTEM)
    container.settings.setChartType(ChartType.BAR)
    container.settings.setShowTotalOnHero(false)
    container.settings.setCurrencySymbol("$")

    val result = container.backup.import(uri(backup))
    assertTrue(result.toString(), result is ImportResult.Success)
    assertEquals(before.transactions.size, (result as ImportResult.Success).transactions)
    assertEquals(before, repository.backupContent())
    assertEquals(AccentColor.VIOLET, container.settings.accent.value)
    assertEquals(BackgroundMode.BLACK, container.settings.backgroundMode.value)
    assertEquals(0xFF336699.toInt(), container.settings.customAccent.value)
    assertEquals(ChartType.LINE, container.settings.chartType.value)
    assertTrue(container.settings.showTotalOnHero.value)
    assertEquals("€", container.settings.currencySymbol.value)
    assertEquals(receiptBytes.toList(), container.receipts.file("receipt-a.jpg").readBytes().toList())
  }

  @Test
  fun anExportIsAZipWithDataAndReceipts() = runBlocking {
    seed()
    container.receipts.file("r1.jpg").writeBytes(byteArrayOf(9))
    val content = repository.backupContent()
    repository.saveTransaction(
      TransactionEntity(
        type = TxType.EXPENSE, amountMinor = 100, merchant = "x", categoryId = content.categories.first().id,
        accountId = content.accounts.first().id, timestamp = 1L, receiptFile = "r1.jpg"
      )
    )
    val backup = file("zip.kwbackup")
    container.backup.export(uri(backup))
    val names = ZipFile(backup).use { zip -> zip.entries().toList().map { it.name } }
    assertTrue(names.toString(), "data.json" in names)
    assertTrue(names.toString(), "receipts/r1.jpg" in names)
  }

  private fun assertRefused(backup: File, snippet: String) = runBlocking {
    val before = repository.backupContent()
    val result = container.backup.import(uri(backup))
    assertTrue("expected a refusal for ${backup.name}, got $result", result is ImportResult.Invalid)
    val reason = (result as ImportResult.Invalid).reason
    assertTrue("'$reason' should mention '$snippet'", reason.contains(snippet, ignoreCase = true))
    assertEquals("a refused file must not change anything", before, repository.backupContent())
  }

  private fun validJson(mutate: (String) -> String = { it }): ByteArray = runBlocking {
    val good = file("good.kwbackup")
    container.backup.export(uri(good))
    val text = ZipFile(good).use { zip -> zip.getInputStream(zip.getEntry("data.json")).readBytes().toString(Charsets.UTF_8) }
    mutate(text).toByteArray()
  }

  @Test
  fun notAZipIsRefused() {
    seed()
    val f = file("notzip.kwbackup")
    f.writeText("hello, this is just text")
    assertRefused(f, "Keep Wallet backup")
  }

  @Test
  fun aZipWithoutDataIsRefused() {
    seed()
    val f = file("nodata.kwbackup")
    zipOf(f, mapOf("something.txt" to byteArrayOf(1)))
    assertRefused(f, "Keep Wallet backup")
  }

  @Test
  fun wrongFormatIsRefused() {
    seed()
    val f = file("wrongformat.kwbackup")
    zipOf(f, mapOf("data.json" to validJson { it.replace("keepwallet-backup", "something-else") }))
    assertRefused(f, "Keep Wallet backup")
  }

  @Test
  fun aNewerBackupVersionIsRefusedWithAHelpfulMessage() {
    seed()
    val f = file("future.kwbackup")
    zipOf(f, mapOf("data.json" to validJson { it.replace("\"version\":3", "\"version\":99") }))
    assertRefused(f, "newer version")
  }

  @Test
  fun entriesPointingAtMissingCategoriesAreRefused() {
    seed()
    val f = file("dangling.kwbackup")
    zipOf(f, mapOf("data.json" to validJson { it.replaceFirst("\"categoryId\":", "\"categoryId\":99999") }))
    assertRefused(f, "category or account")
  }

  @Test
  fun unknownEnumValuesAreRefused() {
    seed()
    val f = file("badenum.kwbackup")
    zipOf(f, mapOf("data.json" to validJson { it.replaceFirst("\"kind\":\"BANK\"", "\"kind\":\"CRYPTO\"") }))
    assertRefused(f, "unknown")
  }

  @Test
  fun truncatedJsonIsRefused() {
    seed()
    val f = file("truncated.kwbackup")
    zipOf(f, mapOf("data.json" to validJson { it.take(it.length / 2) }))
    assertRefused(f, "Keep Wallet backup")
  }

  @Test
  fun aReceiptNameThatTriesToEscapeTheFolderIsIgnored() = runBlocking {
    seed()
    val f = file("slip.kwbackup")
    zipOf(f, mapOf("data.json" to validJson(), "receipts/../../evil.txt" to byteArrayOf(1)))
    val result = container.backup.import(uri(f))
    assertTrue(result.toString(), result is ImportResult.Success)
    assertTrue(!File(app.filesDir.parentFile, "evil.txt").exists())
    assertTrue(!File(app.filesDir, "evil.txt").exists())
  }

  @Test
  fun aMissingReceiptFileJustDropsThatAttachment() = runBlocking {
    seed()
    val content = repository.backupContent()
    repository.saveTransaction(
      TransactionEntity(
        type = TxType.EXPENSE, amountMinor = 100, merchant = "x", categoryId = content.categories.first().id,
        accountId = content.accounts.first().id, timestamp = 1L, receiptFile = "ghost.jpg"
      )
    )
    val f = file("ghost.kwbackup")
    container.backup.export(uri(f)) // ghost.jpg is not on disk, so it isn't packed
    val result = container.backup.import(uri(f))
    assertTrue(result.toString(), result is ImportResult.Success)
    assertEquals(null, repository.backupContent().transactions.first { it.merchant == "x" }.receiptFile)
  }
}
