package com.keepwallet.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import com.keepwallet.app.domain.Recurrence
import java.time.LocalDate
import java.time.ZoneId

/** Everything stored in the database that a backup carries. */
data class BackupContent(
  val accounts: List<AccountEntity>,
  val categories: List<CategoryEntity>,
  val transactions: List<TransactionEntity>,
  val widgets: List<DashboardWidgetEntity>,
  val recurring: List<RecurringEntity> = emptyList(),
  val goals: List<GoalEntity> = emptyList(),
  val rules: List<PaymentRuleEntity> = emptyList(),
  val ious: List<IouEntity> = emptyList()
)

class KeepWalletRepository(
  private val db: KeepWalletDatabase,
  private val settings: SettingsStore,
  val receipts: ReceiptStore
) {
  private val transactionDao = db.transactionDao()
  private val categoryDao = db.categoryDao()
  private val accountDao = db.accountDao()
  private val widgetDao = db.widgetDao()
  private val detectedDao = db.detectedEntryDao()
  private val recurringDao = db.recurringDao()
  private val goalDao = db.goalDao()
  private val ruleDao = db.paymentRuleDao()
  private val iouDao = db.iouDao()

  val transactions: Flow<List<TransactionEntity>> = transactionDao.observeAll()
  val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
  val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()
  val widgets: Flow<List<DashboardWidgetEntity>> = widgetDao.observeAll()
  val detectedEntries: Flow<List<DetectedEntryEntity>> = detectedDao.observeAll()
  val recurring: Flow<List<RecurringEntity>> = recurringDao.observeAll()
  val goals: Flow<List<GoalEntity>> = goalDao.observeAll()
  val rules: Flow<List<PaymentRuleEntity>> = ruleDao.observeAll()
  val ious: Flow<List<IouEntity>> = iouDao.observeAll()

  // ---- First run and upgrades ------------------------------------------------------------------

  /** Creates the defaults on first run, and upgrades the built-in setup of existing installs once. */
  suspend fun ensureSeeded() {
    db.withTransaction {
      if (!settings.isSeeded) {
        if (categoryDao.count() == 0 && accountDao.count() == 0) seedDefaults()
        if (widgetDao.count() == 0) widgetDao.insertAll(DefaultData.widgets())
      } else if (settings.defaultsVersion < DefaultData.VERSION) {
        upgradeDefaults(settings.defaultsVersion)
      }
      Unit
    }
    settings.isSeeded = true
    settings.defaultsVersion = DefaultData.VERSION
  }

  private suspend fun seedDefaults(): Map<String, Long> {
    val defaults = DefaultData.categories()
    val ids = categoryDao.insertAll(defaults)
    accountDao.insertAll(DefaultData.accounts())
    return defaults.map { it.name }.zip(ids).toMap()
  }

  /**
   * Brings an install made by an earlier version in line with the current defaults without touching
   * the user's own data: "Other" becomes "Others", missing categories and a Wallet account are added,
   * and the home screen gets the new default order.
   */
  private suspend fun upgradeDefaults(from: Int) {
    if (from < 2) upgradeToV2()
    if (from < 3) {
      // New sections are added switched off, after whatever the user arranged; nothing they set up is reset.
      val have = widgetDao.getAll().map { it.kind }.toSet()
      var position = widgetDao.nextPosition()
      DefaultData.widgets().filter { it.kind !in have }.forEach { widgetDao.insert(it.copy(enabled = false, position = position++)) }
    }
    if (from < 4) {
      // Health arrives once; a category the user already made (or renamed) with that name is left alone.
      val names = categoryDao.getAll().map { it.name.lowercase() }
      if (DefaultData.HEALTH.lowercase() !in names) {
        DefaultData.categories().first { it.name == DefaultData.HEALTH }.let { categoryDao.insert(it.copy(sortOrder = categoryDao.nextSortOrder())) }
      }
    }
  }

  private suspend fun upgradeToV2() {
    val existing = categoryDao.getAll()
    existing.firstOrNull { it.type == TxType.EXPENSE && it.name.equals("Other", ignoreCase = true) }
      ?.let { categoryDao.update(it.copy(name = DefaultData.OTHERS)) }
    val have = categoryDao.getAll().map { it.name.lowercase() }.toSet()
    var order = categoryDao.nextSortOrder()
    DefaultData.categories()
      .filter { it.name.lowercase() !in have }
      .forEach { categoryDao.insert(it.copy(sortOrder = order++)) }

    val accounts = accountDao.getAll()
    DefaultData.accounts().forEach { default ->
      if (accounts.none { it.kind == default.kind }) accountDao.insert(default.copy(sortOrder = accountDao.nextSortOrder()))
    }

    widgetDao.deleteAll()
    widgetDao.insertAll(DefaultData.widgets())
  }

  // ---- Transactions --------------------------------------------------------------------------

  suspend fun getTransaction(id: Long): TransactionEntity? = transactionDao.get(id)

  /** Inserts a new transaction (id == 0) or updates an existing one. Returns the row id. */
  suspend fun saveTransaction(transaction: TransactionEntity): Long {
    if (transaction.id == 0L) return transactionDao.insert(transaction)
    transactionDao.update(transaction)
    return transaction.id
  }

  suspend fun deleteTransaction(transaction: TransactionEntity) {
    transactionDao.delete(transaction)
  }

  // ---- Transfers and splits ------------------------------------------------------------------

  /**
   * Moves [amountMinor] from one account to another as two linked entries. Returns the shared transfer id, or null
   * when the accounts are the same, the amount isn't positive, or there is no category to file it under.
   */
  suspend fun saveTransfer(fromId: Long, toId: Long, amountMinor: Long, timestamp: Long, note: String? = null): Long? = db.withTransaction {
    if (fromId == toId || amountMinor <= 0) return@withTransaction null
    val from = accountDao.getAll().firstOrNull { it.id == fromId } ?: return@withTransaction null
    val to = accountDao.getAll().firstOrNull { it.id == toId } ?: return@withTransaction null
    val categories = categoryDao.getAll()
    val category = categories.firstOrNull { it.name == DefaultData.MONEY_TRANSFER } ?: categories.firstOrNull() ?: return@withTransaction null
    val id = java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
    transactionDao.insertAll(
      listOf(
        TransactionEntity(type = TxType.TRANSFER_OUT, amountMinor = amountMinor, merchant = "Transfer to ${to.name}", categoryId = category.id, accountId = from.id, timestamp = timestamp, note = note, transferId = id),
        TransactionEntity(type = TxType.TRANSFER_IN, amountMinor = amountMinor, merchant = "Transfer from ${from.name}", categoryId = category.id, accountId = to.id, timestamp = timestamp, note = note, transferId = id)
      )
    )
    id
  }

  suspend fun legsOfTransfer(transferId: Long): List<TransactionEntity> = transactionDao.legsOfTransfer(transferId)

  suspend fun deleteTransfer(transferId: Long) = transactionDao.deleteTransfer(transferId)

  /** Puts back both legs of a deleted transfer (Undo). */
  suspend fun restoreTransactions(transactions: List<TransactionEntity>) = transactionDao.insertAll(transactions)

  /** Saves one bill as several entries that share a split id: same name, date and account, a category and amount each. */
  suspend fun saveSplit(parts: List<TransactionEntity>): Long? {
    if (parts.size < 2 || parts.any { it.amountMinor <= 0 }) return null
    val id = java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)
    transactionDao.insertAll(parts.map { it.copy(id = 0, splitId = id) })
    return id
  }

  // ---- Rules for detected payments --------------------------------------------------------------

  suspend fun saveRule(rule: PaymentRuleEntity): Long = if (rule.id == 0L) ruleDao.insert(rule) else rule.id.also { ruleDao.update(rule) }

  suspend fun deleteRule(id: Long) = ruleDao.delete(id)

  /** Puts back a deleted transaction (used by Undo). Its receipt file is still on disk until cleanup runs. */
  suspend fun restoreTransaction(transaction: TransactionEntity) {
    transactionDao.insert(transaction)
  }

  /** Deletes receipt photos that no transaction refers to, except those in [keep]. */
  suspend fun cleanOrphanReceipts(keep: Set<String> = emptySet()) {
    receipts.deleteExcept(transactionDao.allReceiptFiles().toSet() + categoryDao.getAll().mapNotNull { it.photoFile } + keep)
  }

  // ---- Categories ----------------------------------------------------------------------------

  suspend fun saveCategory(category: CategoryEntity): Long =
    if (category.id == 0L) {
      categoryDao.insert(category.copy(sortOrder = categoryDao.nextSortOrder()))
    } else {
      categoryDao.update(category)
      category.id
    }

  suspend fun setLimits(categoryId: Long, monthlyMinor: Long, weeklyMinor: Long, rollover: Boolean? = null) {
    val category = categoryDao.get(categoryId) ?: return
    categoryDao.update(
      category.copy(monthlyLimitMinor = monthlyMinor.coerceAtLeast(0), weeklyLimitMinor = weeklyMinor.coerceAtLeast(0), rollover = rollover ?: category.rollover)
    )
  }

  /** Number of transactions that would need a new home if [categoryId] were deleted. */
  suspend fun transactionCountForCategory(categoryId: Long): Int = transactionDao.countForCategory(categoryId)

  /** Deletes a category, first moving its transactions to [moveTo] when it has any. */
  suspend fun deleteCategory(category: CategoryEntity, moveTo: Long?): Boolean = db.withTransaction {
    val count = transactionDao.countForCategory(category.id)
    if (count > 0) {
      if (moveTo == null || moveTo == category.id) return@withTransaction false
      transactionDao.moveCategory(category.id, moveTo)
    }
    // Bills that repeat follow the entries to the new category; with nowhere to go they leave with the category.
    if (moveTo != null && moveTo != category.id) recurringDao.moveCategory(category.id, moveTo)
    categoryDao.delete(category)
    true
  }

  /**
   * Shifts budget between two categories ("move money"). The weekly limits move when [weekly] is true,
   * otherwise the monthly limits. Returns false when the source doesn't have enough limit to give.
   */
  suspend fun moveMoney(fromId: Long, toId: Long, amountMinor: Long, weekly: Boolean): Boolean = db.withTransaction {
    if (fromId == toId || amountMinor <= 0) return@withTransaction false
    val from = categoryDao.get(fromId) ?: return@withTransaction false
    val to = categoryDao.get(toId) ?: return@withTransaction false
    val available = if (weekly) from.weeklyLimitMinor else from.monthlyLimitMinor
    if (amountMinor > available) return@withTransaction false
    if (weekly) {
      categoryDao.update(from.copy(weeklyLimitMinor = from.weeklyLimitMinor - amountMinor))
      categoryDao.update(to.copy(weeklyLimitMinor = to.weeklyLimitMinor + amountMinor))
    } else {
      categoryDao.update(from.copy(monthlyLimitMinor = from.monthlyLimitMinor - amountMinor))
      categoryDao.update(to.copy(monthlyLimitMinor = to.monthlyLimitMinor + amountMinor))
    }
    true
  }

  // ---- Accounts ------------------------------------------------------------------------------

  /**
   * Saves an account. [balanceMinor] is what the account holds *right now*: it is stored as the opening
   * balance, adjusted for the entries already booked, so the displayed balance equals what the user typed.
   */
  suspend fun saveAccount(account: AccountEntity, balanceMinor: Long): Long = db.withTransaction {
    if (account.id == 0L) {
      accountDao.insert(account.copy(openingBalanceMinor = balanceMinor, sortOrder = accountDao.nextSortOrder()))
    } else {
      val net = transactionDao.netForAccount(account.id)
      accountDao.update(account.copy(openingBalanceMinor = balanceMinor - net))
      account.id
    }
  }

  /** Accounts that still hold transactions can't be deleted. */
  suspend fun deleteAccount(account: AccountEntity): Boolean = db.withTransaction {
    if (transactionDao.countForAccount(account.id) > 0) return@withTransaction false
    ruleDao.clearAccount(account.id)
    accountDao.delete(account)
    true
  }

  // ---- Dashboard -----------------------------------------------------------------------------

  suspend fun addWidget(kind: WidgetKind) {
    widgetDao.insert(DashboardWidgetEntity(kind = kind, position = widgetDao.nextPosition()))
  }

  suspend fun setWidgetEnabled(id: Long, enabled: Boolean) {
    widgetDao.get(id)?.let { widgetDao.update(it.copy(enabled = enabled)) }
  }

  suspend fun removeWidget(id: Long) = db.withTransaction {
    widgetDao.delete(id)
    reindex(widgetDao.getAll())
  }

  /** Swaps a widget with its neighbour; [direction] is -1 for up and +1 for down. */
  suspend fun moveWidget(id: Long, direction: Int) = db.withTransaction {
    val list = widgetDao.getAll().toMutableList()
    val index = list.indexOfFirst { it.id == id }
    val target = index + direction
    if (index < 0 || target !in list.indices) return@withTransaction
    val moved = list.removeAt(index)
    list.add(target, moved)
    reindex(list)
  }

  /** Applies a whole new order at once (drag and drop). Sections missing from [ids] keep their relative order at the end. */
  suspend fun setWidgetOrder(ids: List<Long>) = db.withTransaction {
    val all = widgetDao.getAll()
    val byId = all.associateBy { it.id }
    val ordered = ids.mapNotNull { byId[it] } + all.filter { it.id !in ids }
    reindex(ordered)
  }

  /**
   * Rearranges Home to a saved layout: sections in the given order and on or off as listed. Sections the layout
   * does not mention (or repeats) are switched off and follow; missing ones are added.
   */
  suspend fun applyLayout(items: List<Pair<WidgetKind, Boolean>>) = db.withTransaction {
    val existing = widgetDao.getAll().toMutableList()
    val ordered = mutableListOf<DashboardWidgetEntity>()
    items.forEach { (kind, on) ->
      if (ordered.any { it.kind == kind }) return@forEach
      val found = existing.firstOrNull { it.kind == kind }
      if (found != null) {
        existing.remove(found)
        ordered += found.copy(enabled = on)
      } else {
        val id = widgetDao.insert(DashboardWidgetEntity(kind = kind, enabled = on, position = ordered.size))
        ordered += (widgetDao.get(id) ?: return@forEach)
      }
    }
    ordered += existing.map { it.copy(enabled = false) }
    reindex(ordered)
  }

  /** Changes only the fields that are given, so two quick taps on different chips never undo each other. */
  suspend fun setWidgetChart(id: Long, type: ChartType? = null, range: ChartRange? = null) {
    widgetDao.get(id)?.let { widgetDao.update(it.copy(chartType = type ?: it.chartType, range = range ?: it.range)) }
  }

  private suspend fun reindex(list: List<DashboardWidgetEntity>) {
    widgetDao.updateAll(list.mapIndexed { index, widget -> widget.copy(position = index) })
  }

  // ---- Bills that repeat -----------------------------------------------------------------------

  /** Inserts a new bill (id == 0) or updates one. The first due date is the one the caller chose. */
  suspend fun saveRecurring(item: RecurringEntity): Long =
    if (item.id == 0L) recurringDao.insert(item) else item.id.also { recurringDao.update(item) }

  suspend fun deleteRecurring(id: Long) = recurringDao.delete(id)

  suspend fun setRecurringAuto(id: Long, auto: Boolean) {
    recurringDao.get(id)?.let { recurringDao.update(it.copy(autoPost = auto)) }
  }

  suspend fun setRecurringActive(id: Long, active: Boolean) {
    recurringDao.get(id)?.let { recurringDao.update(it.copy(active = active)) }
  }

  /**
   * Books the bill as paid: adds an entry dated [today] (never in the future, whatever the due date) and moves the
   * bill on to its next occurrence. Returns the new entry's id, or null if the bill is gone.
   */
  suspend fun payRecurring(id: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long? = db.withTransaction {
    val item = recurringDao.get(id) ?: return@withTransaction null
    val txId = transactionDao.insert(entryFor(item, today, zone, java.time.LocalTime.now(zone).withNano(0)))
    recurringDao.update(item.copy(nextDueDay = advance(item).toEpochDay()))
    txId
  }

  /** Moves the bill to its next occurrence without booking anything. */
  suspend fun skipRecurring(id: Long) {
    recurringDao.get(id)?.let { recurringDao.update(it.copy(nextDueDay = advance(it).toEpochDay())) }
  }

  /**
   * Adds the entries for every automatic bill that has fallen due up to [today], each dated on its due day, and
   * moves the bills on. Bills that are not automatic are left for the user. Returns how many entries were added.
   */
  suspend fun postDueRecurring(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int = db.withTransaction {
    var added = 0
    recurringDao.getAll().filter { it.active && it.autoPost }.forEach { item ->
      val due = Recurrence.occurrences(LocalDate.ofEpochDay(item.nextDueDay), today, item.frequency, item.anchorDay)
      if (due.isEmpty()) return@forEach
      transactionDao.insertAll(due.map { entryFor(item, it, zone) })
      recurringDao.update(item.copy(nextDueDay = Recurrence.next(due.last(), item.frequency, item.anchorDay).toEpochDay()))
      added += due.size
    }
    added
  }

  private fun advance(item: RecurringEntity): LocalDate =
    Recurrence.next(LocalDate.ofEpochDay(item.nextDueDay), item.frequency, item.anchorDay)

  private fun entryFor(item: RecurringEntity, day: LocalDate, zone: ZoneId, time: java.time.LocalTime = java.time.LocalTime.of(9, 0)): TransactionEntity = TransactionEntity(
    type = item.type,
    amountMinor = item.amountMinor,
    merchant = item.name,
    categoryId = item.categoryId,
    accountId = item.accountId,
    timestamp = day.atTime(time).atZone(zone).toInstant().toEpochMilli(),
    note = "Recurring · ${Recurrence.label(item.frequency).lowercase()}"
  )

  // ---- Savings goals ---------------------------------------------------------------------------

  suspend fun saveGoal(goal: GoalEntity): Long =
    if (goal.id == 0L) goalDao.insert(goal.copy(sortOrder = goalDao.nextSortOrder())) else goal.id.also { goalDao.update(goal) }

  suspend fun deleteGoal(id: Long) = goalDao.delete(id)

  // ---- Debts and IOUs --------------------------------------------------------------------------

  suspend fun saveIou(iou: IouEntity): Long =
    if (iou.id == 0L) iouDao.insert(iou) else iou.id.also { iouDao.update(iou) }

  suspend fun deleteIou(id: Long) = iouDao.delete(id)

  suspend fun setIouSettled(id: Long, settled: Boolean) = iouDao.setSettled(id, settled)

  /** Adds [deltaMinor] (negative to take money out) to a goal, never going below zero. Returns the new total. */
  suspend fun addToGoal(id: Long, deltaMinor: Long): Long? = db.withTransaction {
    val goal = goalDao.get(id) ?: return@withTransaction null
    val saved = (goal.savedMinor + deltaMinor).coerceAtLeast(0)
    goalDao.update(goal.copy(savedMinor = saved))
    saved
  }

  // ---- Detected notification entries ----------------------------------------------------------

  suspend fun getDetected(id: Long): DetectedEntryEntity? = detectedDao.get(id)

  /** Stores a detected entry unless the same one arrived moments ago. Returns the new id or null for a duplicate. */
  suspend fun addDetected(entry: DetectedEntryEntity, dedupeWindowMs: Long = DEDUPE_WINDOW_MS): Long? {
    detectedDao.deleteOlderThan(entry.detectedAt - RETENTION_MS)
    val duplicates = detectedDao.countRecentDuplicates(
      entry.sourcePackage, entry.amountMinor, entry.type, entry.detectedAt - dedupeWindowMs
    )
    if (duplicates > 0) return null
    return detectedDao.insert(entry)
  }

  suspend fun discardDetected(id: Long) = detectedDao.delete(id)

  // ---- Backup, data management -----------------------------------------------------------------

  suspend fun backupContent(): BackupContent = db.withTransaction {
    BackupContent(
      accountDao.getAll(), categoryDao.getAll(), transactionDao.getAll(), widgetDao.getAll(),
      recurringDao.getAll(), goalDao.getAll(), ruleDao.getAll(), iouDao.getAll()
    )
  }

  /** Replaces everything with [content] in one transaction, keeping ids so links between rows stay intact. */
  suspend fun restore(content: BackupContent) {
    db.withTransaction {
      clearAllTables()
      categoryDao.insertAll(content.categories)
      accountDao.insertAll(content.accounts)
      transactionDao.insertAll(content.transactions)
      widgetDao.insertAll(content.widgets)
      recurringDao.insertAll(content.recurring)
      goalDao.insertAll(content.goals)
      ruleDao.insertAll(content.rules)
      iouDao.insertAll(content.ious)
    }
    settings.isSeeded = true
    settings.defaultsVersion = DefaultData.VERSION
  }

  /** Wipes everything and restores the first-run defaults (no transactions). */
  suspend fun eraseAll() {
    db.withTransaction {
      clearAllTables()
      seedDefaults()
      widgetDao.insertAll(DefaultData.widgets())
    }
    receipts.deleteExcept(emptySet())
    settings.isSeeded = true
    settings.defaultsVersion = DefaultData.VERSION
  }

  /** Replaces all data with demo budgets and a few months of realistic activity ending today. */
  suspend fun replaceWithSampleData(today: LocalDate, zone: ZoneId = ZoneId.systemDefault()) {
    db.withTransaction {
      clearAllTables()
      val categoryIds = seedDefaults()
      widgetDao.insertAll(DefaultData.widgets())
      categoryDao.updateAll(categoryDao.getAll().map {
        it.copy(
          monthlyLimitMinor = DefaultData.sampleMonthlyLimit(it.name),
          weeklyLimitMinor = DefaultData.sampleWeeklyLimit(it.name)
        )
      })
      val accounts = accountDao.getAll()
      val bank = accounts.first { it.kind == AccountKind.BANK }
      val wallet = accounts.first { it.kind == AccountKind.WALLET }
      val cash = accounts.first { it.kind == AccountKind.CASH }
      accountDao.update(bank.copy(openingBalanceMinor = 100_000))
      accountDao.update(wallet.copy(openingBalanceMinor = 90_000))
      accountDao.update(cash.copy(openingBalanceMinor = 34_000))
      transactionDao.insertAll(DefaultData.sampleTransactions(today, zone, categoryIds, bank.id, wallet.id, cash.id))
      recurringDao.insertAll(DefaultData.sampleRecurring(today, categoryIds, bank.id, wallet.id))
      goalDao.insertAll(DefaultData.sampleGoals())
      // A transfer, a bill split across two categories and a rule, so every new feature has something to show.
      saveTransfer(bank.id, cash.id, 20_000, today.minusDays(3).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), "Cash for the week")
      val splitTime = today.minusDays(4).atTime(17, 30).atZone(zone).toInstant().toEpochMilli()
      saveSplit(
        listOfNotNull(
          categoryIds["Groceries"]?.let { TransactionEntity(type = TxType.EXPENSE, amountMinor = 4_200, merchant = "Supermarket", categoryId = it, accountId = wallet.id, timestamp = splitTime, note = "Weekly shop") },
          categoryIds["Shopping"]?.let { TransactionEntity(type = TxType.EXPENSE, amountMinor = 1_800, merchant = "Supermarket", categoryId = it, accountId = wallet.id, timestamp = splitTime, note = "Weekly shop") }
        )
      )
      categoryIds["Transport"]?.let { ruleDao.insert(PaymentRuleEntity(match = "Metro", categoryId = it, accountId = wallet.id, displayName = "Metro")) }
    }
    receipts.deleteExcept(emptySet())
    settings.isSeeded = true
    settings.defaultsVersion = DefaultData.VERSION
  }

  private suspend fun clearAllTables() {
    ruleDao.deleteAll()
    iouDao.deleteAll()
    recurringDao.deleteAll()
    goalDao.deleteAll()
    transactionDao.deleteAll()
    detectedDao.deleteAll()
    widgetDao.deleteAll()
    categoryDao.deleteAll()
    accountDao.deleteAll()
  }

  companion object {
    const val DEDUPE_WINDOW_MS = 2 * 60 * 1000L
    const val RETENTION_MS = 7 * 24 * 60 * 60 * 1000L
  }
}
