package com.keepwallet.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.keepwallet.app.domain.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed interface ImportResult {
  data class Success(val accounts: Int, val categories: Int, val transactions: Int) : ImportResult
  data class Invalid(val reason: String) : ImportResult
}

/**
 * Exports everything to a single `.kwbackup` file (a zip holding `data.json` and the receipt photos) and
 * restores it on another device. A file is fully checked before anything on the device is replaced.
 */
class BackupManager(
  context: Context,
  private val repository: KeepWalletRepository,
  private val settings: SettingsStore,
  private val receipts: ReceiptStore
) {
  private val appContext = context.applicationContext

  /** Writes a backup to [uri]. Returns the number of transactions saved, or null if the file couldn't be written. */
  suspend fun export(uri: Uri, now: Long = System.currentTimeMillis()): Int? = withContext(Dispatchers.IO) {
    try {
      val content = repository.backupContent()
      val json = toJson(content, now).toString().toByteArray(Charsets.UTF_8)
      val output = appContext.contentResolver.openOutputStream(uri, "wt") ?: return@withContext null
      output.use { stream ->
        ZipOutputStream(stream).use { zip ->
          zip.putNextEntry(ZipEntry(DATA_ENTRY))
          zip.write(json)
          zip.closeEntry()
          (content.transactions.mapNotNull { it.receiptFile } + content.categories.mapNotNull { it.photoFile }).distinct().forEach { name ->
            val file = receipts.file(name)
            if (file.exists()) {
              zip.putNextEntry(ZipEntry("$RECEIPTS_DIR/$name"))
              file.inputStream().use { it.copyTo(zip) }
              zip.closeEntry()
            }
          }
        }
      }
      content.transactions.size
    } catch (_: Exception) {
      null
    }
  }

  /** Writes an automatic backup to a Storage Access Framework directory tree. */
  suspend fun exportToTree(treeUri: Uri, now: Long = System.currentTimeMillis()): String? = withContext(Dispatchers.IO) {
    try {
      val parentId = DocumentsContract.getTreeDocumentId(treeUri)
      val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
      val fileName = suggestedFileName(LocalDate.now())
      val fileUri = DocumentsContract.createDocument(appContext.contentResolver, parentUri, "application/zip", fileName)
        ?: return@withContext null
      val count = export(fileUri, now)
      if (count != null) fileName else null
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Writes every entry as a spreadsheet-friendly CSV (date, time, type, amount, category, account, name, note).
   * Returns the number of rows, or null if the file couldn't be written.
   */
  suspend fun exportCsv(uri: Uri, zone: ZoneId = ZoneId.systemDefault()): Int? = withContext(Dispatchers.IO) {
    try {
      val content = repository.backupContent()
      val text = csv(content, zone)
      val output = appContext.contentResolver.openOutputStream(uri, "wt") ?: return@withContext null
      output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
      content.transactions.size
    } catch (_: Exception) {
      null
    }
  }

  /** Reads entries from a CSV spreadsheet and inserts them without erasing existing records. */
  suspend fun importCsv(uri: Uri, zone: ZoneId = ZoneId.systemDefault()): ImportResult = withContext(Dispatchers.IO) {
    try {
      val text = appContext.contentResolver.openInputStream(uri)?.use { it.bufferedReader(Charsets.UTF_8).readText() }
        ?: return@withContext ImportResult.Invalid("Couldn't open that file.")
      val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
      if (lines.isEmpty()) return@withContext ImportResult.Invalid("The CSV file is empty.")

      val content = repository.backupContent()
      val categoriesByName = content.categories.associateBy { it.name.lowercase() }
      val defaultCategory = content.categories.firstOrNull { it.name == DefaultData.OTHERS } ?: content.categories.firstOrNull()
        ?: return@withContext ImportResult.Invalid("No categories found to file entries under.")
      val accountsByName = content.accounts.associateBy { it.name.lowercase() }
      val defaultAccount = content.accounts.firstOrNull { it.kind == AccountKind.BANK } ?: content.accounts.firstOrNull()
        ?: return@withContext ImportResult.Invalid("No account found to file entries under.")

      val header = parseCsvLine(lines.first()).map { it.lowercase() }
      val hasHeader = header.any { it.contains("date") || it.contains("amount") || it.contains("merchant") }
      val rows = if (hasHeader) lines.drop(1) else lines

      val dateIdx = if (hasHeader) header.indexOfFirst { it.contains("date") } else 0
      val timeIdx = if (hasHeader) header.indexOfFirst { it.contains("time") } else 1
      val typeIdx = if (hasHeader) header.indexOfFirst { it.contains("type") } else 2
      val amountIdx = if (hasHeader) header.indexOfFirst { it.contains("amount") || it.contains("amt") || it.contains("value") } else 3
      val categoryIdx = if (hasHeader) header.indexOfFirst { it.contains("category") } else 4
      val accountIdx = if (hasHeader) header.indexOfFirst { it.contains("account") } else 5
      val nameIdx = if (hasHeader) header.indexOfFirst { it.contains("name") || it.contains("merchant") || it.contains("payee") || it.contains("desc") } else 6
      val noteIdx = if (hasHeader) header.indexOfFirst { it.contains("note") || it.contains("memo") } else 7

      val datePatterns = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy")
      )

      val newTransactions = mutableListOf<TransactionEntity>()
      for (line in rows) {
        val cols = parseCsvLine(line)
        if (cols.isEmpty() || cols.all { it.isEmpty() }) continue

        val rawAmount = if (amountIdx in cols.indices) cols[amountIdx] else ""
        val cleanAmount = rawAmount.replace(",", "").replace("$", "").replace("₹", "").replace("€", "").replace("£", "").trim()
        val num = cleanAmount.toDoubleOrNull() ?: continue
        val isNegative = num < 0
        val amountMinor = Money.toMinor(kotlin.math.abs(num))
        if (amountMinor <= 0) continue

        val rawType = if (typeIdx in cols.indices) cols[typeIdx].lowercase() else ""
        val type = when {
          rawType.contains("income") || rawType.contains("credit") || rawType.contains("cr") -> TxType.INCOME
          rawType.contains("expense") || rawType.contains("debit") || rawType.contains("dr") -> TxType.EXPENSE
          isNegative -> TxType.EXPENSE
          else -> TxType.EXPENSE
        }

        val rawDate = if (dateIdx in cols.indices) cols[dateIdx] else ""
        var parsedDate: LocalDate? = null
        for (pattern in datePatterns) {
          try {
            parsedDate = LocalDate.parse(rawDate.trim(), pattern)
            break
          } catch (_: Exception) {}
        }
        val date = parsedDate ?: LocalDate.now()

        val rawTime = if (timeIdx in cols.indices) cols[timeIdx].trim() else ""
        val time = runCatching { java.time.LocalTime.parse(rawTime, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
          ?: java.time.LocalTime.of(12, 0)

        val timestamp = date.atTime(time).atZone(zone).toInstant().toEpochMilli()

        val rawCat = if (categoryIdx in cols.indices) cols[categoryIdx].trim().lowercase() else ""
        val categoryId = categoriesByName[rawCat]?.id ?: defaultCategory.id

        val rawAcc = if (accountIdx in cols.indices) cols[accountIdx].trim().lowercase() else ""
        val accountId = accountsByName[rawAcc]?.id ?: defaultAccount.id

        val merchant = (if (nameIdx in cols.indices) cols[nameIdx].trim() else "").ifEmpty { "Imported payment" }
        val note = if (noteIdx in cols.indices) cols[noteIdx].trim().ifEmpty { null } else null

        newTransactions += TransactionEntity(
          type = type,
          amountMinor = amountMinor,
          merchant = merchant,
          categoryId = categoryId,
          accountId = accountId,
          timestamp = timestamp,
          note = note
        )
      }

      if (newTransactions.isEmpty()) return@withContext ImportResult.Invalid("No valid transaction rows found in CSV.")

      repository.restoreTransactions(newTransactions)
      ImportResult.Success(content.accounts.size, content.categories.size, newTransactions.size)
    } catch (_: Exception) {
      ImportResult.Invalid("Could not parse that CSV file.")
    }
  }

  fun parseCsvLine(line: String): List<String> {
    val tokens = mutableListOf<String>()
    var inQuotes = false
    val sb = StringBuilder()
    var i = 0
    while (i < line.length) {
      val c = line[i]
      if (c == '"') {
        if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
          sb.append('"')
          i++
        } else {
          inQuotes = !inQuotes
        }
      } else if (c == ',' && !inQuotes) {
        tokens.add(sb.toString().trim())
        sb.setLength(0)
      } else {
        sb.append(c)
      }
      i++
    }
    tokens.add(sb.toString().trim())
    return tokens.map { if (it.startsWith("'")) it.removePrefix("'") else it }
  }

  /** Oldest entry first, so the sheet reads like a statement. Amounts are plain numbers with two decimals. */
  fun csv(content: BackupContent, zone: ZoneId = ZoneId.systemDefault()): String {
    val categories = content.categories.associateBy { it.id }
    val accounts = content.accounts.associateBy { it.id }
    fun cell(value: String): String {
      // Leading = + - @ would be run as a formula by spreadsheets, so they are defused with an apostrophe.
      val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
      return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
    val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE
    val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    return buildString {
      append("Date,Time,Type,Amount,Category,Account,Name,Note\r\n")
      content.transactions.sortedBy { it.timestamp }.forEach { t ->
        val moment = java.time.Instant.ofEpochMilli(t.timestamp).atZone(zone)
        val amount = java.math.BigDecimal(t.amountMinor).movePointLeft(2).setScale(2).toPlainString()
        append(
          listOf(
            moment.format(dateFormat), moment.format(timeFormat), when (t.type) { TxType.INCOME -> "Income"; TxType.EXPENSE -> "Expense"; TxType.TRANSFER_OUT -> "Transfer out"; TxType.TRANSFER_IN -> "Transfer in" }, amount,
            categories[t.categoryId]?.name.orEmpty(), accounts[t.accountId]?.name.orEmpty(), t.merchant, t.note.orEmpty()
          ).joinToString(",") { cell(it) }
        )
        append("\r\n")
      }
    }
  }

  /** Replaces all data with the backup at [uri]. Nothing changes unless the whole file is valid. */
  suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
    val stage = File(appContext.cacheDir, "import-${System.nanoTime()}").also { it.mkdirs() }
    try {
      var jsonText: String? = null
      val input = appContext.contentResolver.openInputStream(uri) ?: return@withContext ImportResult.Invalid("Couldn't open that file.")
      input.use { stream ->
        ZipInputStream(stream).use { zip ->
          var entry = zip.nextEntry
          while (entry != null) {
            when {
              entry.name == DATA_ENTRY -> jsonText = zip.readBytes().toString(Charsets.UTF_8)
              entry.name.startsWith("$RECEIPTS_DIR/") && !entry.isDirectory -> {
                val name = entry.name.removePrefix("$RECEIPTS_DIR/")
                if (SAFE_NAME.matches(name)) File(stage, name).outputStream().use { zip.copyTo(it) }
              }
            }
            entry = zip.nextEntry
          }
        }
      }
      val text = jsonText ?: return@withContext ImportResult.Invalid("That file isn't a Keep Wallet backup.")
      val parsed = try {
        parse(text, stagedReceipts = stage.list()?.toSet().orEmpty())
      } catch (e: ParseProblem) {
        return@withContext ImportResult.Invalid(e.message ?: "That backup is damaged.")
      } catch (_: JSONException) {
        return@withContext ImportResult.Invalid("That file isn't a Keep Wallet backup.")
      }

      repository.restore(parsed.content)
      receipts.installFrom(stage)
      parsed.settings.apply(settings)
      ImportResult.Success(parsed.content.accounts.size, parsed.content.categories.size, parsed.content.transactions.size)
    } catch (_: Exception) {
      ImportResult.Invalid("That file couldn't be read.")
    } finally {
      stage.deleteRecursively()
    }
  }

  // ---- JSON -------------------------------------------------------------------------------------

  private class ParseProblem(message: String) : Exception(message)

  private class SettingsSnapshot(
    val currency: String?,
    val accent: AccentColor?,
    val background: BackgroundMode?,
    val customAccent: Int?,
    val highlight: Int?,
    val chart: ChartType?,
    val heroTotal: Boolean?,
    val hideBalance: Boolean?,
    val colorStyle: ColorStyle? = null,
    val systemColors: Boolean? = null,
    val palette: String? = null,
    val savedLayouts: List<SavedLayout>? = null,
    val remindBills: Boolean? = null,
    val budgetAlerts: Boolean? = null,
    val dailyNudge: Boolean? = null,
    val nudgeHour: Int? = null
  ) {
    fun apply(settings: SettingsStore) {
      currency?.let(settings::setCurrencySymbol)
      accent?.let(settings::setAccent)
      background?.let(settings::setBackgroundMode)
      customAccent?.let(settings::setCustomAccent)
      highlight?.let { settings.setHighlight(it) }
      chart?.let(settings::setChartType)
      heroTotal?.let(settings::setShowTotalOnHero)
      hideBalance?.let(settings::setHideBalance)
      colorStyle?.let(settings::setColorStyle)
      systemColors?.let(settings::setSystemColors)
      palette?.let(settings::applyPaletteByName)
      savedLayouts?.forEach(settings::saveLayout)
      remindBills?.let(settings::setRemindBills)
      budgetAlerts?.let(settings::setBudgetAlerts)
      dailyNudge?.let(settings::setDailyNudge)
      nudgeHour?.let(settings::setNudgeHour)
    }
  }

  private class Parsed(val content: BackupContent, val settings: SettingsSnapshot)

  private fun toJson(content: BackupContent, now: Long): JSONObject = JSONObject().apply {
    put("format", FORMAT)
    put("version", VERSION)
    put("exportedAt", now)
    put("settings", JSONObject().apply {
      put("currency", settings.currencySymbol.value)
      put("accent", settings.accent.value.name)
      put("background", settings.backgroundMode.value.name)
      settings.customAccent.value?.let { put("customAccent", it) }
      settings.highlight.value?.let { put("highlight", it) }
      put("chartType", settings.chartType.value.name)
      put("showTotalOnHero", settings.showTotalOnHero.value)
      put("hideBalance", settings.hideBalance.value)
      put("colorStyle", settings.colorStyle.value.name)
      put("systemColors", settings.systemColors.value)
      settings.palette.value?.let { put("palette", it.name) }
      put("remindBills", settings.remindBills.value)
      put("budgetAlerts", settings.budgetAlerts.value)
      put("dailyNudge", settings.dailyNudge.value)
      put("nudgeHour", settings.nudgeHour.value)
      put("savedLayouts", JSONArray().also { a -> settings.layouts.value.forEach { a.put(it.toJson()) } })
    })
    put("accounts", JSONArray().also { array ->
      content.accounts.forEach { a ->
        array.put(JSONObject().put("id", a.id).put("name", a.name).put("kind", a.kind.name)
          .put("openingBalanceMinor", a.openingBalanceMinor).put("sortOrder", a.sortOrder))
      }
    })
    put("categories", JSONArray().also { array ->
      content.categories.forEach { c ->
        array.put(JSONObject().put("id", c.id).put("name", c.name).put("iconKey", c.iconKey).put("color", c.color)
          .put("type", c.type.name).put("group", c.group.name).put("monthlyLimitMinor", c.monthlyLimitMinor)
          .put("weeklyLimitMinor", c.weeklyLimitMinor).put("sortOrder", c.sortOrder).put("rollover", c.rollover)
          .put("emoji", c.emoji ?: JSONObject.NULL).put("photoFile", c.photoFile ?: JSONObject.NULL))
      }
    })
    put("transactions", JSONArray().also { array ->
      content.transactions.forEach { t ->
        array.put(JSONObject().put("id", t.id).put("type", t.type.name).put("amountMinor", t.amountMinor)
          .put("merchant", t.merchant).put("categoryId", t.categoryId).put("accountId", t.accountId)
          .put("timestamp", t.timestamp).put("note", t.note ?: JSONObject.NULL).put("receiptFile", t.receiptFile ?: JSONObject.NULL)
          .put("transferId", t.transferId ?: JSONObject.NULL).put("splitId", t.splitId ?: JSONObject.NULL)
          .put("foreignCode", t.foreignCode ?: JSONObject.NULL).put("foreignMinor", t.foreignMinor ?: JSONObject.NULL))
      }
    })
    put("widgets", JSONArray().also { array ->
      content.widgets.forEach { w ->
        array.put(JSONObject().put("id", w.id).put("kind", w.kind.name).put("enabled", w.enabled)
          .put("position", w.position).put("chartType", w.chartType.name).put("range", w.range.name))
      }
    })
    put("recurring", JSONArray().also { array ->
      content.recurring.forEach { r ->
        array.put(JSONObject().put("id", r.id).put("name", r.name).put("type", r.type.name).put("amountMinor", r.amountMinor)
          .put("categoryId", r.categoryId).put("accountId", r.accountId).put("frequency", r.frequency.name)
          .put("nextDueDay", r.nextDueDay).put("anchorDay", r.anchorDay).put("autoPost", r.autoPost).put("active", r.active))
      }
    })
    put("rules", JSONArray().also { array ->
      content.rules.forEach { r ->
        array.put(JSONObject().put("id", r.id).put("match", r.match).put("categoryId", r.categoryId)
          .put("accountId", r.accountId ?: JSONObject.NULL).put("displayName", r.displayName ?: JSONObject.NULL))
      }
    })
    put("goals", JSONArray().also { array ->
      content.goals.forEach { g ->
        array.put(JSONObject().put("id", g.id).put("name", g.name).put("targetMinor", g.targetMinor).put("savedMinor", g.savedMinor)
          .put("iconKey", g.iconKey).put("color", g.color).put("sortOrder", g.sortOrder))
      }
    })
    put("ious", JSONArray().also { array ->
      content.ious.forEach { i ->
        array.put(JSONObject().put("id", i.id).put("person", i.person).put("amountMinor", i.amountMinor)
          .put("type", i.type.name).put("note", i.note ?: JSONObject.NULL)
          .put("dueEpochDay", i.dueEpochDay ?: JSONObject.NULL).put("settled", i.settled).put("createdAt", i.createdAt))
      }
    })
  }

  private fun <E : Enum<E>> JSONObject.enumOf(key: String, values: Array<E>): E =
    values.firstOrNull { it.name == getString(key) } ?: throw ParseProblem("The backup contains an unknown $key.")

  private fun parse(text: String, stagedReceipts: Set<String>): Parsed {
    val root = JSONObject(text)
    if (root.optString("format") != FORMAT) throw ParseProblem("That file isn't a Keep Wallet backup.")
    val version = root.optInt("version", -1)
    if (version < 1) throw ParseProblem("That file isn't a Keep Wallet backup.")
    if (version > VERSION) throw ParseProblem("That backup was made by a newer version of Keep Wallet. Update the app and try again.")

    fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> = (0 until length()).map { block(getJSONObject(it)) }
    val accounts = root.getJSONArray("accounts").mapObjects { o ->
      AccountEntity(o.getLong("id"), o.getString("name"), o.enumOf("kind", AccountKind.entries.toTypedArray()), o.getLong("openingBalanceMinor"), o.optInt("sortOrder"))
    }
    val categories = root.getJSONArray("categories").mapObjects { o ->
      CategoryEntity(
        o.getLong("id"), o.getString("name"), o.getString("iconKey"), o.getInt("color"), o.enumOf("type", TxType.entries.toTypedArray()),
        o.enumOf("group", CategoryGroup.entries.toTypedArray()), o.getLong("monthlyLimitMinor"), o.getLong("weeklyLimitMinor"), o.optInt("sortOrder"),
        o.optBoolean("rollover", false),
        if (o.isNull("emoji")) null else o.optString("emoji").take(16).ifEmpty { null },
        if (o.isNull("photoFile")) null else o.optString("photoFile").takeIf { it in stagedReceipts }
      )
    }
    val accountIds = accounts.map { it.id }.toSet()
    val categoryIds = categories.map { it.id }.toSet()
    if (accountIds.size != accounts.size || categoryIds.size != categories.size) throw ParseProblem("The backup contains duplicate entries.")

    val transactions = root.getJSONArray("transactions").mapObjects { o ->
      val receipt = if (o.isNull("receiptFile")) null else o.getString("receiptFile").takeIf { it in stagedReceipts }
      TransactionEntity(
        o.getLong("id"), o.enumOf("type", TxType.entries.toTypedArray()), o.getLong("amountMinor"), o.getString("merchant"),
        o.getLong("categoryId"), o.getLong("accountId"), o.getLong("timestamp"),
        if (o.isNull("note")) null else o.getString("note"), receipt,
        if (o.isNull("transferId") || !o.has("transferId")) null else o.getLong("transferId"),
        if (o.isNull("splitId") || !o.has("splitId")) null else o.getLong("splitId"),
        if (o.isNull("foreignCode") || !o.has("foreignCode")) null else o.getString("foreignCode").take(6),
        if (o.isNull("foreignMinor") || !o.has("foreignMinor")) null else o.getLong("foreignMinor")
      )
    }
    if (transactions.any { it.accountId !in accountIds || it.categoryId !in categoryIds || it.amountMinor <= 0 }) {
      throw ParseProblem("The backup contains a transaction with missing details.")
    }

    val widgets = root.getJSONArray("widgets").mapObjects { o ->
      DashboardWidgetEntity(
        o.getLong("id"), o.enumOf("kind", WidgetKind.entries.toTypedArray()), o.getBoolean("enabled"), o.getInt("position"),
        o.enumOf("chartType", ChartType.entries.toTypedArray()), o.enumOf("range", ChartRange.entries.toTypedArray())
      )
    }
    val recurring = (root.optJSONArray("recurring") ?: JSONArray()).mapObjects { o ->
      RecurringEntity(
        o.getLong("id"), o.getString("name"), o.enumOf("type", TxType.entries.toTypedArray()), o.getLong("amountMinor"),
        o.getLong("categoryId"), o.getLong("accountId"), o.enumOf("frequency", Frequency.entries.toTypedArray()),
        o.getLong("nextDueDay"), o.getInt("anchorDay"), o.getBoolean("autoPost"), o.optBoolean("active", true)
      )
    }
    if (recurring.any { it.accountId !in accountIds || it.categoryId !in categoryIds || it.amountMinor <= 0 }) {
      throw ParseProblem("The backup contains a recurring bill that doesn't fit.")
    }
    val goals = (root.optJSONArray("goals") ?: JSONArray()).mapObjects { o ->
      GoalEntity(o.getLong("id"), o.getString("name"), o.getLong("targetMinor"), o.getLong("savedMinor"), o.getString("iconKey"), o.getInt("color"), o.optInt("sortOrder"))
    }
    if (goals.any { it.targetMinor <= 0 || it.savedMinor < 0 }) throw ParseProblem("The backup contains an invalid goal.")
    val rules = (root.optJSONArray("rules") ?: JSONArray()).mapObjects { o ->
      PaymentRuleEntity(
        o.getLong("id"), o.getString("match"), o.getLong("categoryId"),
        if (o.isNull("accountId")) null else o.getLong("accountId"),
        if (o.isNull("displayName")) null else o.getString("displayName")
      )
    }
    if (rules.any { it.categoryId !in categoryIds || it.match.isBlank() }) throw ParseProblem("The backup contains a rule that doesn't fit.")
    val ious = (root.optJSONArray("ious") ?: JSONArray()).mapObjects { o ->
      IouEntity(
        id = o.getLong("id"),
        person = o.getString("person"),
        amountMinor = o.getLong("amountMinor"),
        type = o.enumOf("type", IouType.entries.toTypedArray()),
        note = if (o.isNull("note")) null else o.optString("note"),
        dueEpochDay = if (o.isNull("dueEpochDay")) null else o.optLong("dueEpochDay"),
        settled = o.optBoolean("settled", false),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
      )
    }
    // Both legs of a transfer must be there, or the balances would be wrong.
    if (transactions.filter { it.transferId != null }.groupBy { it.transferId }.any { (_, legs) -> legs.map { it.type }.sorted() != listOf(TxType.TRANSFER_OUT, TxType.TRANSFER_IN) }) {
      throw ParseProblem("The backup contains a transfer with a missing half.")
    }

    val s = root.optJSONObject("settings")
    fun <E> JSONObject.optEnum(key: String, values: Array<E>): E? where E : Enum<E> =
      values.firstOrNull { it.name == optString(key) }
    val snapshot = SettingsSnapshot(
      currency = s?.optString("currency")?.takeIf { it.isNotBlank() },
      accent = s?.optEnum("accent", AccentColor.entries.toTypedArray()),
      background = s?.optEnum("background", BackgroundMode.entries.toTypedArray()),
      customAccent = s?.takeIf { it.has("customAccent") }?.getInt("customAccent"),
      highlight = s?.takeIf { it.has("highlight") }?.getInt("highlight"),
      chart = s?.optEnum("chartType", ChartType.entries.toTypedArray()),
      heroTotal = s?.takeIf { it.has("showTotalOnHero") }?.getBoolean("showTotalOnHero"),
      hideBalance = s?.takeIf { it.has("hideBalance") }?.getBoolean("hideBalance"),
      colorStyle = s?.optEnum("colorStyle", ColorStyle.entries.toTypedArray()),
      systemColors = s?.takeIf { it.has("systemColors") }?.getBoolean("systemColors"),
      palette = s?.optString("palette")?.takeIf { it.isNotBlank() },
      savedLayouts = s?.optJSONArray("savedLayouts")?.let { arr ->
        (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { SavedLayout.fromJson(it) } }
      },
      remindBills = s?.takeIf { it.has("remindBills") }?.getBoolean("remindBills"),
      budgetAlerts = s?.takeIf { it.has("budgetAlerts") }?.getBoolean("budgetAlerts"),
      dailyNudge = s?.takeIf { it.has("dailyNudge") }?.getBoolean("dailyNudge"),
      nudgeHour = s?.takeIf { it.has("nudgeHour") }?.getInt("nudgeHour")
    )
    return Parsed(BackupContent(accounts, categories, transactions, widgets, recurring, goals, rules, ious), snapshot)
  }

  companion object {
    const val FORMAT = "keepwallet-backup"
    const val VERSION = 3
    private const val DATA_ENTRY = "data.json"
    private const val RECEIPTS_DIR = "receipts"
    private val SAFE_NAME = Regex("[A-Za-z0-9._-]+")

    fun suggestedFileName(date: LocalDate): String = "KeepWallet-$date.kwbackup"

    fun suggestedCsvName(date: LocalDate): String = "KeepWallet-entries-$date.csv"
  }
}
