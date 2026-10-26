package com.keepwallet.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.keepwallet.app.AppContainer
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.ChartRange
import com.keepwallet.app.data.ChartType
import com.keepwallet.app.data.AccentColor
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.data.ImportResult
import com.keepwallet.app.data.DashboardWidgetEntity
import com.keepwallet.app.data.DetectedEntryEntity
import com.keepwallet.app.data.Frequency
import com.keepwallet.app.data.GoalEntity
import com.keepwallet.app.data.PaymentRuleEntity
import com.keepwallet.app.data.RecurringEntity
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.domain.AmountInput
import com.keepwallet.app.domain.FrequentEntry
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.monthWrapUp
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.notify.CaptureOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

enum class Tab { HOME, ACTIVITY, INSIGHTS, PLAN }

enum class TypeFilter { ALL, EXPENSES, INCOME }

/** The three parts of the Plan tab. */
enum class PlanSection(val label: String) { BUDGETS("Budgets"), GOALS("Goals"), BILLS("Bills") }

enum class SortOrder(val label: String) {
  NEWEST("Newest first"), OLDEST("Oldest first"), HIGHEST("Highest amount"), LOWEST("Lowest amount")
}

data class ActivityFilters(
  val query: String = "",
  val type: TypeFilter = TypeFilter.ALL,
  val categoryId: Long? = null,
  val accountId: Long? = null,
  val day: LocalDate? = null,
  val sort: SortOrder = SortOrder.NEWEST
) {
  val isActive: Boolean get() = query.isNotBlank() || type != TypeFilter.ALL || categoryId != null || accountId != null || day != null
}

/** One consistent snapshot of everything stored, so screens never mix old and new rows. */
data class AppData(
  val transactions: List<TransactionEntity> = emptyList(),
  val categories: List<CategoryEntity> = emptyList(),
  val accounts: List<AccountEntity> = emptyList(),
  val widgets: List<DashboardWidgetEntity> = emptyList(),
  val detected: List<DetectedEntryEntity> = emptyList(),
  val recurring: List<RecurringEntity> = emptyList(),
  val goals: List<GoalEntity> = emptyList(),
  val rules: List<com.keepwallet.app.data.PaymentRuleEntity> = emptyList(),
  val loaded: Boolean = false
)

data class EditorState(
  val editingId: Long? = null,
  val type: TxType = TxType.EXPENSE,
  val amountText: String = "0",
  val merchant: String = "",
  val categoryId: Long? = null,
  val accountId: Long? = null,
  val date: LocalDate = LocalDate.now(),
  /** Supplies the time of day; kept from the original entry when editing. */
  val timeSource: Long = System.currentTimeMillis(),
  val note: String = "",
  val receiptFile: String? = null,
  /** Set when the amount was worked out from another currency. */
  val foreignCode: String? = null,
  val foreignMinor: Long? = null,
  val detectedId: Long? = null,
  /** Set when a rule for detected payments chose the category, so the form can say so. */
  val ruleNote: String? = null
) {
  val amountMinor: Long get() = Money.parseMinor(amountText)
}

data class MoveMoneyState(
  val fromId: Long? = null,
  val toId: Long? = null,
  val amountText: String = "",
  val weekly: Boolean = false
)

/** Shown when the entry being saved matches one added moments ago. */
data class DuplicateWarning(val existing: TransactionEntity, val whenText: String)

/** A moment worth marking: confetti and a tap of haptic feedback, with [text] shown as the message. */
data class Celebration(val text: String)

data class UiMessage(val text: String, val actionLabel: String? = null, val onAction: (() -> Unit)? = null)

/** What the converter's update button is doing, and the last thing it had to say. */
data class RatesStatus(val loading: Boolean = false, val message: String? = null)

class MainViewModel(private val container: AppContainer) : ViewModel() {
  private val repository = container.repository
  private val settings = container.settings

  // ---- Stored data -------------------------------------------------------------------------------

  val data: StateFlow<AppData> = combine(
    combine(repository.transactions, repository.categories, repository.accounts, repository.widgets, repository.detectedEntries) { transactions, categories, accounts, widgets, detected ->
      AppData(transactions, categories, accounts, widgets, detected)
    },
    repository.recurring,
    repository.goals,
    repository.rules
  ) { base, recurring, goals, rules ->
    base.copy(recurring = recurring, goals = goals, rules = rules, loaded = true)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

  val accent: StateFlow<AccentColor> = settings.accent
  val backgroundMode: StateFlow<BackgroundMode> = settings.backgroundMode
  val customAccent: StateFlow<Int?> = settings.customAccent
  val highlight: StateFlow<Int?> = settings.highlight
  val colorStyle: StateFlow<com.keepwallet.app.data.ColorStyle> = settings.colorStyle
  val systemColors: StateFlow<Boolean> = settings.systemColors
  val rates: StateFlow<com.keepwallet.app.data.RateTable> = settings.rates
  private val _ratesStatus = MutableStateFlow(RatesStatus())
  val ratesStatus: StateFlow<RatesStatus> = _ratesStatus.asStateFlow()
  val palette: StateFlow<com.keepwallet.app.data.AppPalette?> = settings.palette
  val onboarded: StateFlow<Boolean> = settings.onboarded
  val splashPending: StateFlow<Boolean> = settings.splashPending
  val chartType: StateFlow<ChartType> = settings.chartType
  val showTotalOnHero: StateFlow<Boolean> = settings.showTotalOnHero
  val hideBalance: StateFlow<Boolean> = settings.hideBalance
  val hideOnOpen: StateFlow<Boolean> = settings.hideOnOpen
  val remindBills: StateFlow<Boolean> = settings.remindBills
  val budgetAlerts: StateFlow<Boolean> = settings.budgetAlerts
  val dailyNudge: StateFlow<Boolean> = settings.dailyNudge
  val nudgeHour: StateFlow<Int> = settings.nudgeHour


  private val _alertsRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
  /** Emits when something (a notification) asks the app to show its alerts page. */
  val alertsRequests: SharedFlow<Unit> = _alertsRequests.asSharedFlow()
  fun openAlerts() { _alertsRequests.tryEmit(Unit) }

  fun setRemindBills(value: Boolean) { settings.setRemindBills(value); container.reminders.sync() }
  fun setBudgetAlerts(value: Boolean) { settings.setBudgetAlerts(value); container.reminders.sync() }
  fun setDailyNudge(value: Boolean) { settings.setDailyNudge(value); container.reminders.sync() }
  fun setNudgeHour(hour: Int) = settings.setNudgeHour(hour)

  /** Saves a review as a PDF at the place the user picked. */
  fun exportReviewPdf(uri: Uri, report: com.keepwallet.app.domain.ReviewReport) {
    viewModelScope.launch {
      val ok = withContext(Dispatchers.IO) {
        try {
          container.appContext.contentResolver.openOutputStream(uri, "wt")?.use { com.keepwallet.app.data.ReviewPdf.write(it, report, settings.currencySymbol.value) } != null
        } catch (_: Exception) {
          false
        }
      }
      say(if (ok) "Saved ${report.title} as a PDF" else "Could not save the PDF")
    }
  }
  val gridViews: StateFlow<Set<String>> = settings.gridViews
  val currencySymbol: StateFlow<String> = settings.currencySymbol
  val captureEnabled: StateFlow<Boolean> = settings.captureEnabled

  // ---- Period selection --------------------------------------------------------------------------

  private val _today = MutableStateFlow(LocalDate.now())
  val today: StateFlow<LocalDate> = _today.asStateFlow()

  /** What needs attention now: waiting payments, bills coming up, budgets filling. Drives the bell and its page. */
  val alerts: StateFlow<List<com.keepwallet.app.domain.AlertItem>> = combine(data, settings.currencySymbol, _today) { d, symbol, today ->
    com.keepwallet.app.domain.Alerts.compute(d.detected.size, d.recurring, d.categories, d.transactions, today, symbol)
  }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  /** The month the whole app is looking at: Home, Balance, Activity, Insights and Plan all follow it. */
  private val _month = MutableStateFlow(YearMonth.from(_today.value))
  val month: StateFlow<YearMonth> = _month.asStateFlow()

  private val _weekStart = MutableStateFlow(Periods.weekStart(_today.value))
  val weekStart: StateFlow<LocalDate> = _weekStart.asStateFlow()

  fun refreshToday() {
    val now = LocalDate.now()
    if (now != _today.value) {
      val followingNow = _month.value == YearMonth.from(_today.value)
      _today.value = now
      if (followingNow) setMonth(YearMonth.from(now))
    }
  }

  fun shiftMonth(delta: Int) = setMonth(_month.value.plusMonths(delta.toLong()))

  /** Moves the whole app to [month]; the week shown on Home goes with it (this week for the current month, else the month's last week). */
  fun setMonth(month: YearMonth) {
    _month.value = month
    val anchor = if (month == YearMonth.from(_today.value)) _today.value else month.atEndOfMonth()
    _weekStart.value = Periods.weekStart(anchor)
  }

  fun backToThisMonth() = setMonth(YearMonth.from(_today.value))

  fun shiftWeek(delta: Int) = _weekStart.update { it.plusWeeks(delta.toLong()) }
  fun setWeekContaining(date: LocalDate) { _weekStart.value = Periods.weekStart(date) }

  /** Called when the app comes to the front: picks up a new day and books automatic bills that have fallen due. */
  private val _celebrations = MutableSharedFlow<Celebration>(extraBufferCapacity = 4)
  val celebrations: SharedFlow<Celebration> = _celebrations.asSharedFlow()

  private fun celebrate(text: String) {
    say(text)
    _celebrations.tryEmit(Celebration(text))
  }

  /** Once per finished month: if it ended inside the budget, mark it. */
  private suspend fun checkMonthWrapUp() {
    val snapshot = data.first { it.loaded }
    val previous = java.time.YearMonth.from(_today.value).minusMonths(1)
    if (settings.celebratedMonth == previous.toString()) return
    settings.celebratedMonth = previous.toString()
    val wrap = Analytics.monthWrapUp(snapshot.categories, snapshot.transactions, previous) ?: return
    val symbol = settings.currencySymbol.value
    val name = previous.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    celebrate("$name finished under budget: ${Money.formatWhole(wrap.leftMinor, symbol)} left of ${Money.formatWhole(wrap.budgetMinor, symbol)}")
  }

  fun onResume() {
    refreshToday()
    viewModelScope.launch { checkMonthWrapUp() }
    viewModelScope.launch {
      val added = repository.postDueRecurring(_today.value)
      if (added > 0) say(if (added == 1) "Added 1 recurring entry" else "Added $added recurring entries")
    }
  }

  // ---- Activity filters (kept across tab switches) -----------------------------------------------

  private val _filters = MutableStateFlow(ActivityFilters())
  val filters: StateFlow<ActivityFilters> = _filters.asStateFlow()

  fun updateFilters(change: (ActivityFilters) -> ActivityFilters) = _filters.update(change)
  fun clearFilters() { _filters.update { ActivityFilters(sort = it.sort) } }

  // ---- Navigation requests from deep inside screens ------------------------------------------------

  private val _tabRequests = MutableSharedFlow<Tab>(extraBufferCapacity = 1)
  val tabRequests: SharedFlow<Tab> = _tabRequests.asSharedFlow()

  fun requestTab(tab: Tab) { _tabRequests.tryEmit(tab) }

  private val _planSection = MutableStateFlow(PlanSection.BUDGETS)
  val planSection: StateFlow<PlanSection> = _planSection.asStateFlow()

  fun setPlanSection(section: PlanSection) { _planSection.value = section }

  /** Opens the Plan tab on a particular part (used by Home sections and Settings). */
  fun openPlan(section: PlanSection) {
    _planSection.value = section
    requestTab(Tab.PLAN)
  }

  /** Jumps to Activity showing one category (the "pocket filter" from the Pockets board). */
  fun showCategoryActivity(categoryId: Long, month: YearMonth) {
    _month.value = month
    _filters.value = ActivityFilters(categoryId = categoryId)
    requestTab(Tab.ACTIVITY)
  }

  // ---- Messages --------------------------------------------------------------------------------

  private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
  val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

  fun showMessage(text: String) = say(text)

  private fun say(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    _messages.tryEmit(UiMessage(text, actionLabel, onAction))
  }

  // ---- Entry editor ----------------------------------------------------------------------------

  private val _editor = MutableStateFlow<EditorState?>(null)
  val editor: StateFlow<EditorState?> = _editor.asStateFlow()

  /** Receipt files imported during this editing session; removed again if the user backs out. */
  private val sessionReceipts = mutableSetOf<String>()
  private var saving = false

  fun openNewEntry(type: TxType = TxType.EXPENSE, date: LocalDate? = null) {
    openEditor(newEditor(type, null, date))
  }

  fun openEdit(transaction: TransactionEntity) {
    sessionReceipts.clear()
    _editor.value = EditorState(
      editingId = transaction.id,
      type = transaction.type,
      amountText = Money.toEditText(transaction.amountMinor),
      merchant = transaction.merchant,
      categoryId = transaction.categoryId,
      accountId = transaction.accountId,
      date = Periods.millisToDate(transaction.timestamp),
      timeSource = transaction.timestamp,
      note = transaction.note.orEmpty(),
      receiptFile = transaction.receiptFile,
      foreignCode = transaction.foreignCode,
      foreignMinor = transaction.foreignMinor
    )
  }

  fun reviewDetected(id: Long) {
    viewModelScope.launch {
      val entry = repository.getDetected(id)
      if (entry == null) {
        say("That detected entry was already handled")
      } else {
        _banner.value = null
        detectedNames[entry.id] = entry.merchant
        openEditor(newEditor(entry.type, entry, null))
      }
    }
  }

  private fun openEditor(state: EditorState) {
    sessionReceipts.clear()
    _editor.value = state
  }

  private fun newEditor(type: TxType, detected: DetectedEntryEntity?, date: LocalDate?): EditorState {
    val snapshot = data.value
    val effectiveType = detected?.type ?: type
    val lastOfType = snapshot.transactions.firstOrNull { it.type == effectiveType }
    val categoriesOfType = snapshot.categories.filter { it.type == effectiveType }
    val category = categoriesOfType.firstOrNull { it.id == lastOfType?.categoryId } ?: categoriesOfType.firstOrNull()
    val account = snapshot.accounts.firstOrNull { it.id == lastOfType?.accountId } ?: snapshot.accounts.firstOrNull()
    val now = System.currentTimeMillis()
    // A rule such as "Metro is Transport" decides the category, account and a tidier name for a detected payment.
    val rule = detected?.let { d -> snapshot.rules.firstOrNull { d.merchant.contains(it.match, ignoreCase = true) } }
    val ruleCategory = rule?.let { r -> categoriesOfType.firstOrNull { it.id == r.categoryId } }
    val ruleAccount = rule?.accountId?.let { id -> snapshot.accounts.firstOrNull { it.id == id } }
    return EditorState(
      type = effectiveType,
      amountText = detected?.let { Money.toEditText(it.amountMinor) } ?: "0",
      merchant = rule?.displayName?.takeIf { it.isNotBlank() } ?: detected?.merchant.orEmpty(),
      categoryId = ruleCategory?.id ?: category?.id,
      accountId = ruleAccount?.id ?: account?.id,
      date = date ?: _today.value,
      timeSource = now,
      detectedId = detected?.id,
      ruleNote = if (ruleCategory != null) "Rule: ${rule.match} → ${ruleCategory.name}" else null
    )
  }

  fun closeEditor() {
    sessionReceipts.forEach { container.receipts.delete(it) }
    sessionReceipts.clear()
    _editor.value = null
  }

  // Typing a different amount ends the link to a foreign-currency amount.
  fun pressKey(key: String) = editEditor { it.copy(amountText = AmountInput.press(it.amountText, key), foreignCode = null, foreignMinor = null) }

  fun clearAmount() = editEditor { it.copy(amountText = "0", foreignCode = null, foreignMinor = null) }

  /** Adds a fixed amount to what is typed (the +20 / +50 / +100 / +500 chips). */
  fun addToAmount(minor: Long) = editEditor { it.copy(amountText = Money.toEditText(it.amountMinor + minor), foreignCode = null, foreignMinor = null) }

  /** The amount was paid in another currency: [homeMinor] is its worth in the app's currency. */
  fun applyForeignAmount(code: String, foreignMinor: Long, homeMinor: Long) =
    editEditor { it.copy(amountText = Money.toEditText(homeMinor), foreignCode = code, foreignMinor = foreignMinor) }

  fun clearForeignAmount() = editEditor { it.copy(foreignCode = null, foreignMinor = null) }

  fun setEntryType(type: TxType) {
    editEditor { state ->
      if (state.type == type) return@editEditor state
      val category = data.value.categories.firstOrNull { it.type == type }
      state.copy(type = type, categoryId = category?.id)
    }
  }

  fun setEntryCategory(id: Long) = editEditor { it.copy(categoryId = id) }
  fun setEntryAccount(id: Long) = editEditor { it.copy(accountId = id) }
  fun setEntryDate(date: LocalDate) = editEditor { it.copy(date = date) }
  fun setEntryMerchant(text: String) = editEditor { it.copy(merchant = text.take(60)) }
  fun setEntryNote(text: String) = editEditor { it.copy(note = text.take(280)) }

  private inline fun editEditor(change: (EditorState) -> EditorState) {
    _editor.update { current -> current?.let(change) }
  }

  private val _pendingLaunch = MutableStateFlow<String?>(null)
  /** Set when a shortcut asked the form to start scanning ("SCAN") or listening ("VOICE") as soon as it opens. */
  val pendingLaunch: StateFlow<String?> = _pendingLaunch.asStateFlow()

  fun consumeLaunch() {
    _pendingLaunch.value = null
  }

  /** Opens the form the way a home-screen widget or app-icon shortcut asked for. */
  fun launchFromShortcut(action: String, merchant: String?, amountMinor: Long, categoryId: Long?, accountId: Long?) {
    viewModelScope.launch {
      data.first { it.loaded }
      when (action) {
        com.keepwallet.app.MainActivity.ACTION_ADD_INCOME -> openNewEntry(TxType.INCOME)
        com.keepwallet.app.MainActivity.ACTION_SCAN_RECEIPT -> {
          openNewEntry()
          _pendingLaunch.value = "SCAN"
        }
        com.keepwallet.app.MainActivity.ACTION_VOICE_ENTRY -> {
          openNewEntry()
          _pendingLaunch.value = "VOICE"
        }
        com.keepwallet.app.MainActivity.ACTION_QUICK_ENTRY -> {
          openNewEntry()
          val snapshot = data.value
          editEditor { state ->
            state.copy(
              merchant = merchant.orEmpty().take(60),
              amountText = if (amountMinor > 0) Money.toEditText(amountMinor) else state.amountText,
              categoryId = categoryId?.takeIf { id -> snapshot.categories.any { it.id == id && it.type == state.type } } ?: state.categoryId,
              accountId = accountId?.takeIf { id -> snapshot.accounts.any { it.id == id } } ?: state.accountId
            )
          }
        }
        else -> openNewEntry()
      }
    }
  }

  private val _scanning = MutableStateFlow(false)
  /** True while a receipt photo is being read. */
  val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

  /** Attaches the photo to the entry and fills in what can be read from it: total, shop and date. */
  fun scanReceipt(uri: Uri) {
    attachReceipt(uri)
    viewModelScope.launch {
      _scanning.value = true
      val text = try {
        withContext(Dispatchers.Default) { container.textReader.read(uri) }
      } finally {
        _scanning.value = false
      }
      if (text == null) say("Couldn't read any text on that picture") else applyReceiptText(text)
    }
  }

  /** Puts the readable parts of receipt [text] into the form being edited. */
  fun applyReceiptText(text: String) {
    val scan = com.keepwallet.app.domain.ReceiptParser.parse(text, LocalDate.now())
    if (scan.amountMinor == null && scan.merchant == null && scan.date == null) {
      say("Nothing that looks like a receipt total was found")
      return
    }
    val known = scan.merchant?.let { name -> data.value.transactions.firstOrNull { it.type == TxType.EXPENSE && it.merchant.equals(name, ignoreCase = true) } }
    editEditor { state ->
      state.copy(
        amountText = scan.amountMinor?.let(Money::toEditText) ?: state.amountText,
        merchant = if (state.merchant.isBlank()) scan.merchant.orEmpty().take(60) else state.merchant,
        date = scan.date ?: state.date,
        categoryId = if (state.type == TxType.EXPENSE && known != null) known.categoryId else state.categoryId
      )
    }
    say(
      listOfNotNull(scan.amountMinor?.let { "total ${Money.toEditText(it)}" }, scan.merchant, scan.date?.toString()).joinToString(" · ")
        .let { "Read from the receipt: $it. Please check it." }
    )
  }

  /** Fills the form from a sentence such as "spent 250 on lunch at Subway". Returns false when no amount was heard. */
  fun applyVoiceText(text: String): Boolean {
    val entry = com.keepwallet.app.domain.VoiceParser.parse(text, data.value.categories)
    if (entry == null) {
      say("Didn't catch an amount. Try: \"spent 250 on lunch\"")
      return false
    }
    editEditor { state ->
      val keepCategory = state.type == entry.type
      state.copy(
        type = entry.type,
        amountText = Money.toEditText(entry.amountMinor),
        categoryId = entry.categoryId ?: if (keepCategory) state.categoryId else data.value.categories.firstOrNull { it.type == entry.type }?.id,
        merchant = entry.merchant ?: state.merchant
      )
    }
    return true
  }

  fun attachReceipt(uri: Uri) {
    viewModelScope.launch {
      val name = withContext(Dispatchers.IO) { container.receipts.import(uri) }
      if (name == null) {
        say("Couldn't read that image")
        return@launch
      }
      val previous = _editor.value?.receiptFile
      if (_editor.value == null) {
        container.receipts.delete(name)
        return@launch
      }
      if (previous != null && previous in sessionReceipts) {
        container.receipts.delete(previous)
        sessionReceipts.remove(previous)
      }
      sessionReceipts += name
      editEditor { it.copy(receiptFile = name) }
    }
  }

  fun removeReceipt() {
    val current = _editor.value?.receiptFile ?: return
    if (current in sessionReceipts) {
      container.receipts.delete(current)
      sessionReceipts.remove(current)
    }
    editEditor { it.copy(receiptFile = null) }
  }

  fun saveEntry() {
    val state = _editor.value ?: return
    if (saving) return
    val amount = state.amountMinor
    if (amount <= 0) {
      say("Enter an amount greater than zero")
      return
    }
    val snapshot = data.value
    val category = snapshot.categories.firstOrNull { it.id == state.categoryId }
    if (category == null) {
      say("Choose a category first")
      return
    }
    val account = snapshot.accounts.firstOrNull { it.id == state.accountId }
    if (account == null) {
      say("Add an account in Settings first")
      return
    }
    val transaction = TransactionEntity(
      id = state.editingId ?: 0,
      type = state.type,
      amountMinor = amount,
      merchant = state.merchant.trim().ifEmpty { category.name },
      categoryId = category.id,
      accountId = account.id,
      timestamp = Periods.combine(state.date, state.timeSource),
      note = state.note.trim().ifEmpty { null },
      receiptFile = state.receiptFile,
      foreignCode = state.foreignCode,
      foreignMinor = state.foreignMinor
    )
    // The same entry again within moments: ask first, once.
    if (state.editingId == null && !confirmedDuplicate) {
      val twin = snapshot.transactions.firstOrNull { other ->
        other.type == transaction.type && other.amountMinor == transaction.amountMinor && other.categoryId == transaction.categoryId &&
          other.accountId == transaction.accountId && other.merchant.equals(transaction.merchant, ignoreCase = true) &&
          kotlin.math.abs(System.currentTimeMillis() - other.timestamp) < DUPLICATE_WINDOW_MS
      }
      if (twin != null) {
        val minutes = ((System.currentTimeMillis() - twin.timestamp) / 60_000).coerceAtLeast(0)
        _duplicate.value = DuplicateWarning(twin, if (minutes < 1) "less than a minute ago" else if (minutes == 1L) "a minute ago" else "$minutes minutes ago")
        return
      }
    }
    confirmedDuplicate = false
    saving = true
    viewModelScope.launch {
      try {
        repository.saveTransaction(transaction)
        state.detectedId?.let { discardDetectedInternal(it) }
        sessionReceipts.clear()
        _editor.value = null
        say(
          when {
            state.editingId != null -> "Entry updated"
            state.type == TxType.INCOME -> "Income saved"
            else -> "Expense saved"
          }
        )
        scheduleReceiptCleanup()
        // A payment the app detected, filed by hand and not covered by a rule yet: offer to remember it.
        if (state.detectedId != null && state.ruleNote == null) {
          val raw = detectedMerchantOf(state)
          if (raw.length >= 3 && snapshot.rules.none { raw.contains(it.match, ignoreCase = true) }) {
            _ruleSuggestion.value = PaymentRuleEntity(match = raw, categoryId = category.id, accountId = account.id, displayName = transaction.merchant.takeIf { !it.equals(raw, ignoreCase = true) })
          }
        }
        // An entry may have just taken a budget to 80% or 100%.
        launch(Dispatchers.IO) { container.reminders.afterEntrySaved() }
      } finally {
        saving = false
      }
    }
  }

  private var confirmedDuplicate = false
  private val _duplicate = MutableStateFlow<DuplicateWarning?>(null)
  /** Set when the entry being saved looks like one added moments ago; the form asks before saving it again. */
  val duplicate: StateFlow<DuplicateWarning?> = _duplicate.asStateFlow()

  fun saveAnyway() {
    confirmedDuplicate = true
    _duplicate.value = null
    saveEntry()
  }

  fun dismissDuplicate() { _duplicate.value = null }

  private val detectedNames = mutableMapOf<Long, String>()
  private fun detectedMerchantOf(state: EditorState): String = state.detectedId?.let { detectedNames[it] }.orEmpty()

  private val _ruleSuggestion = MutableStateFlow<PaymentRuleEntity?>(null)
  /** A rule the app proposes after a detected payment was filed by hand; shown as a yes/no question. */
  val ruleSuggestion: StateFlow<PaymentRuleEntity?> = _ruleSuggestion.asStateFlow()

  fun acceptRuleSuggestion() {
    val rule = _ruleSuggestion.value ?: return
    _ruleSuggestion.value = null
    viewModelScope.launch {
      repository.saveRule(rule)
      say("Rule saved: ${rule.match}")
    }
  }

  fun dismissRuleSuggestion() { _ruleSuggestion.value = null }

  fun saveRule(rule: PaymentRuleEntity) {
    viewModelScope.launch {
      repository.saveRule(rule)
      say(if (rule.id == 0L) "Rule added" else "Rule updated")
    }
  }

  fun deleteRule(rule: PaymentRuleEntity) {
    viewModelScope.launch {
      repository.deleteRule(rule.id)
      say("Deleted rule for ${rule.match}", "Undo") { viewModelScope.launch { repository.saveRule(rule) } }
    }
  }

  // ---- Transfers between accounts ---------------------------------------------------------------

  /** Moves money from one account to another as one transfer: it changes both balances but not income or expense. */
  fun saveTransfer(fromId: Long, toId: Long, amountMinor: Long, note: String?) {
    viewModelScope.launch {
      val names = data.value.accounts.associateBy({ it.id }, { it.name })
      val id = repository.saveTransfer(fromId, toId, amountMinor, System.currentTimeMillis(), note?.trim()?.ifEmpty { null })
      if (id == null) {
        say("Pick two different accounts and an amount")
      } else {
        say("Moved ${Money.format(amountMinor, settings.currencySymbol.value)} from ${names[fromId]} to ${names[toId]}", "Undo") {
          viewModelScope.launch { repository.deleteTransfer(id) }
        }
      }
    }
  }

  // ---- Splitting one bill across categories --------------------------------------------------------

  /**
   * Saves the open form as several entries, one per (category, amount) in [parts]. They share the name, date,
   * account, note and receipt of the form; the amounts must add up to what was typed.
   */
  fun saveSplit(parts: List<Pair<Long, Long>>) {
    val state = _editor.value ?: return
    if (saving || state.editingId != null) return
    val snapshot = data.value
    val account = snapshot.accounts.firstOrNull { it.id == state.accountId }
    if (account == null) {
      say("Add an account in Settings first")
      return
    }
    if (parts.size < 2 || parts.any { it.second <= 0 } || parts.sumOf { it.second } != state.amountMinor) {
      say("The parts must add up to ${Money.format(state.amountMinor, settings.currencySymbol.value)}")
      return
    }
    val timestamp = Periods.combine(state.date, state.timeSource)
    val entries = parts.mapNotNull { (categoryId, amount) ->
      val category = snapshot.categories.firstOrNull { it.id == categoryId } ?: return@mapNotNull null
      TransactionEntity(
        type = state.type, amountMinor = amount, merchant = state.merchant.trim().ifEmpty { category.name },
        categoryId = category.id, accountId = account.id, timestamp = timestamp,
        note = state.note.trim().ifEmpty { null }, receiptFile = if (parts.first().first == categoryId) state.receiptFile else null
      )
    }
    if (entries.size != parts.size) {
      say("Choose a category for every part")
      return
    }
    saving = true
    viewModelScope.launch {
      try {
        repository.saveSplit(entries)
        state.detectedId?.let { discardDetectedInternal(it) }
        sessionReceipts.clear()
        _editor.value = null
        say("Saved as ${entries.size} entries")
        scheduleReceiptCleanup()
      } finally {
        saving = false
      }
    }
  }

  // ---- Entry details (the "bill" view) ---------------------------------------------------------

  private val _viewingId = MutableStateFlow<Long?>(null)
  /** Id of the entry whose details page is open, if any. */
  val viewingId: StateFlow<Long?> = _viewingId.asStateFlow()

  fun openDetails(transaction: TransactionEntity) { _viewingId.value = transaction.id }

  fun closeDetails() { _viewingId.value = null }

  /** Opens the entry form filled with the entry being viewed; the button there says "Update". */
  fun editViewing() {
    val id = _viewingId.value ?: return
    data.value.transactions.firstOrNull { it.id == id }?.let(::openEdit)
  }

  fun deleteViewing() {
    val id = _viewingId.value ?: return
    viewModelScope.launch {
      val transaction = repository.getTransaction(id) ?: return@launch
      val transferId = transaction.transferId
      if (transferId != null) {
        // Both halves go together, and both come back with Undo.
        val legs = repository.legsOfTransfer(transferId)
        repository.deleteTransfer(transferId)
        _viewingId.value = null
        say("Deleted the transfer", "Undo") { viewModelScope.launch { repository.restoreTransactions(legs) } }
        return@launch
      }
      repository.deleteTransaction(transaction)
      _viewingId.value = null
      say("Deleted ${transaction.merchant}", "Undo") {
        viewModelScope.launch { repository.restoreTransaction(transaction) }
      }
      scheduleReceiptCleanup()
    }
  }

  /** Receipt photos of deleted entries stay around for a while so Undo can bring them back. */
  private fun scheduleReceiptCleanup() {
    viewModelScope.launch {
      delay(RECEIPT_CLEANUP_DELAY_MS)
      repository.cleanOrphanReceipts(keep = setOfNotNull(_editor.value?.receiptFile))
    }
  }

  // ---- Detected notification entries -----------------------------------------------------------

  private val _banner = MutableStateFlow<DetectedEntryEntity?>(null)

  /** The most recently detected entry, shown as a prompt over whatever screen is open. */
  val banner: StateFlow<DetectedEntryEntity?> = _banner.asStateFlow()

  init {
    viewModelScope.launch {
      container.capture.events.collect { entry ->
        _banner.value = entry
        delay(BANNER_DURATION_MS)
        if (_banner.value?.id == entry.id) _banner.value = null
      }
    }
  }

  fun dismissBanner() { _banner.value = null }

  fun discardDetected(id: Long) {
    viewModelScope.launch { discardDetectedInternal(id) }
  }

  private suspend fun discardDetectedInternal(id: Long) {
    repository.discardDetected(id)
    container.notifier.cancel(id)
    if (_banner.value?.id == id) _banner.value = null
  }

  fun setCaptureEnabled(enabled: Boolean) = settings.setCaptureEnabled(enabled)

  fun simulateNotification(title: String, text: String) {
    viewModelScope.launch {
      val outcome = container.capture.process("com.keepwallet.app.test", "Test notification", title, text)
      val symbol = settings.currencySymbol.value
      say(
        when (outcome) {
          CaptureOutcome.Disabled -> "Turn on transaction detection first"
          CaptureOutcome.NotATransaction -> "No payment or deposit found in that text"
          CaptureOutcome.Duplicate -> "That entry was just detected"
          is CaptureOutcome.Captured -> "Detected ${Money.format(outcome.entry.amountMinor, symbol)} – review it below"
        }
      )
    }
  }

  // ---- Budgets ---------------------------------------------------------------------------------

  fun setLimits(categoryId: Long, monthlyMinor: Long, weeklyMinor: Long, rollover: Boolean? = null) {
    viewModelScope.launch {
      repository.setLimits(categoryId, monthlyMinor, weeklyMinor, rollover)
      say("Limits updated")
    }
  }

  private val _moveMoney = MutableStateFlow<MoveMoneyState?>(null)
  val moveMoney: StateFlow<MoveMoneyState?> = _moveMoney.asStateFlow()

  fun openMoveMoney(weekly: Boolean) {
    val expense = data.value.categories.filter { it.type == TxType.EXPENSE }
    if (expense.size < 2) {
      say("You need at least two categories to move money")
      return
    }
    _moveMoney.value = MoveMoneyState(expense[0].id, expense[1].id, "", weekly)
  }

  fun updateMoveMoney(change: (MoveMoneyState) -> MoveMoneyState) = _moveMoney.update { it?.let(change) }
  fun closeMoveMoney() { _moveMoney.value = null }

  fun confirmMoveMoney() {
    val state = _moveMoney.value ?: return
    val from = state.fromId
    val to = state.toId
    val amount = Money.parseMinor(state.amountText)
    if (from == null || to == null || from == to) {
      say("Pick two different categories")
      return
    }
    if (amount <= 0) {
      say("Enter an amount to move")
      return
    }
    viewModelScope.launch {
      val moved = repository.moveMoney(from, to, amount, state.weekly)
      if (moved) {
        val names = data.value.categories.associateBy({ it.id }, { it.name })
        say("Moved ${Money.format(amount, settings.currencySymbol.value)} from ${names[from]} to ${names[to]}")
        _moveMoney.value = null
      } else {
        say("${data.value.categories.firstOrNull { it.id == from }?.name ?: "That category"} doesn't have that much limit to move")
      }
    }
  }

  // ---- Categories and accounts -------------------------------------------------------------------

  /** Stores a picked picture as a category's photo; returns its file name, or null if it could not be read. */
  suspend fun importCategoryPhoto(uri: Uri): String? = withContext(Dispatchers.IO) { container.receipts.importSquare(uri) }

  fun discardCategoryPhoto(name: String) {
    viewModelScope.launch(Dispatchers.IO) { container.receipts.delete(name) }
  }

  fun saveCategory(category: CategoryEntity) {
    viewModelScope.launch {
      val previous = data.value.categories.firstOrNull { it.id == category.id }?.photoFile
      repository.saveCategory(category)
      if (previous != null && previous != category.photoFile) withContext(Dispatchers.IO) { container.receipts.delete(previous) }
      say(if (category.id == 0L) "Category added" else "Category updated")
    }
  }

  suspend fun transactionCountForCategory(id: Long): Int = repository.transactionCountForCategory(id)

  fun deleteCategory(category: CategoryEntity, moveTo: Long?) {
    viewModelScope.launch {
      val deleted = repository.deleteCategory(category, moveTo)
      if (deleted && category.photoFile != null) withContext(Dispatchers.IO) { container.receipts.delete(category.photoFile) }
      say(if (deleted) "Deleted ${category.name}" else "Pick a category to move its entries to")
    }
  }

  /** [balanceMinor] is what the account holds right now; entries already booked are accounted for. */
  fun saveAccount(account: AccountEntity, balanceMinor: Long) {
    viewModelScope.launch {
      repository.saveAccount(account, balanceMinor)
      say(if (account.id == 0L) "Account added" else "Account updated")
    }
  }

  fun deleteAccount(account: AccountEntity) {
    viewModelScope.launch {
      val deleted = repository.deleteAccount(account)
      say(if (deleted) "Deleted ${account.name}" else "${account.name} still has transactions, so it can't be deleted")
    }
  }

  // ---- Dashboard -------------------------------------------------------------------------------

  fun addWidget(kind: WidgetKind) = viewModelScope.launch { repository.addWidget(kind) }

  fun setWidgetEnabled(id: Long, enabled: Boolean) {
    viewModelScope.launch { repository.setWidgetEnabled(id, enabled) }
  }

  fun moveWidget(id: Long, direction: Int) {
    viewModelScope.launch { repository.moveWidget(id, direction) }
  }

  /** Saves the order the user dragged the sections into. */
  fun reorderWidgets(ids: List<Long>) {
    viewModelScope.launch { repository.setWidgetOrder(ids) }
  }

  fun removeWidget(id: Long) {
    viewModelScope.launch { repository.removeWidget(id) }
  }

  fun setWidgetChart(id: Long, type: ChartType? = null, range: ChartRange? = null) {
    viewModelScope.launch { repository.setWidgetChart(id, type, range) }
  }

  // ---- Quick entry from habits ---------------------------------------------------------------------

  /** Fills the open form from a merchant the user enters often: name, category, account and (if still empty) amount. */
  fun applySuggestion(entry: FrequentEntry) {
    editEditor { state ->
      val snapshot = data.value
      state.copy(
        merchant = entry.merchant,
        categoryId = snapshot.categories.firstOrNull { it.id == entry.categoryId && it.type == state.type }?.id ?: state.categoryId,
        accountId = snapshot.accounts.firstOrNull { it.id == entry.accountId }?.id ?: state.accountId,
        amountText = if (state.amountMinor == 0L) Money.toEditText(entry.lastAmountMinor) else state.amountText
      )
    }
  }

  // ---- Bills that repeat ---------------------------------------------------------------------------

  fun saveRecurring(item: RecurringEntity) {
    viewModelScope.launch {
      repository.saveRecurring(item)
      say(if (item.id == 0L) "Bill added" else "Bill updated")
    }
  }

  fun deleteRecurring(item: RecurringEntity) {
    viewModelScope.launch {
      repository.deleteRecurring(item.id)
      say("Deleted ${item.name}", "Undo") { viewModelScope.launch { repository.saveRecurring(item) } }
    }
  }

  fun setRecurringAuto(id: Long, auto: Boolean) {
    viewModelScope.launch { repository.setRecurringAuto(id, auto) }
  }

  fun setRecurringActive(id: Long, active: Boolean) {
    viewModelScope.launch { repository.setRecurringActive(id, active) }
  }

  /** Books a bill as paid today and moves it to its next date; Undo takes the entry back out and restores the date. */
  fun payRecurring(item: RecurringEntity) {
    viewModelScope.launch {
      val txId = repository.payRecurring(item.id, _today.value) ?: return@launch
      say("Paid ${item.name}", "Undo") {
        viewModelScope.launch {
          repository.getTransaction(txId)?.let { repository.deleteTransaction(it) }
          repository.saveRecurring(item)
        }
      }
    }
  }

  fun skipRecurring(item: RecurringEntity) {
    viewModelScope.launch {
      repository.skipRecurring(item.id)
      say("Skipped ${item.name}", "Undo") { viewModelScope.launch { repository.saveRecurring(item) } }
    }
  }

  // ---- Savings goals -------------------------------------------------------------------------------

  fun saveGoal(goal: GoalEntity) {
    viewModelScope.launch {
      repository.saveGoal(goal)
      say(if (goal.id == 0L) "Goal added" else "Goal updated")
    }
  }

  fun deleteGoal(goal: GoalEntity) {
    viewModelScope.launch {
      repository.deleteGoal(goal.id)
      say("Deleted ${goal.name}", "Undo") { viewModelScope.launch { repository.saveGoal(goal) } }
    }
  }

  /** Puts money towards a goal, or takes it back with a negative amount. */
  fun addToGoal(goal: GoalEntity, deltaMinor: Long) {
    viewModelScope.launch {
      val saved = repository.addToGoal(goal.id, deltaMinor) ?: return@launch
      val symbol = settings.currencySymbol.value
      when {
        deltaMinor < 0 -> say("Took ${Money.format(-deltaMinor, symbol)} out of ${goal.name}")
        saved >= goal.targetMinor && goal.savedMinor < goal.targetMinor -> celebrate("Goal reached: ${goal.name} is fully funded")
        saved >= goal.targetMinor -> say("${goal.name} is fully funded")
        else -> say("Added ${Money.format(deltaMinor, symbol)} to ${goal.name}")
      }
    }
  }

  // ---- Appearance and data -----------------------------------------------------------------------

  val layouts: StateFlow<List<com.keepwallet.app.data.SavedLayout>> = settings.layouts

  fun applyLayout(layout: com.keepwallet.app.data.SavedLayout) {
    viewModelScope.launch {
      repository.applyLayout(layout.items)
      say("Home set to ${layout.name}")
    }
  }

  /** Remembers the sections as they are on Home now under [name]. */
  fun saveCurrentLayout(name: String) {
    val clean = name.trim().take(24)
    if (clean.isEmpty()) return
    val items = data.value.widgets.sortedBy { it.position }.map { it.kind to it.enabled }
    settings.saveLayout(com.keepwallet.app.data.SavedLayout(clean, items))
    say("Saved layout $clean")
  }

  fun deleteLayout(name: String) = settings.deleteLayout(name)

  // ---- App lock ----

  val lockEnabled: StateFlow<Boolean> = settings.lockEnabled
  val pinLength: StateFlow<Int> = settings.pinLength
  val biometricEnabled: StateFlow<Boolean> = settings.biometric
  val lockTimeout: StateFlow<Int> = settings.lockTimeout
  val hideInRecents: StateFlow<Boolean> = settings.hideInRecents

  /** True while the PIN screen covers the app. A cold start with the lock on begins locked. */
  private val _locked = MutableStateFlow(settings.lockEnabled.value)
  val locked: StateFlow<Boolean> = _locked.asStateFlow()

  private val _lockError = MutableStateFlow<String?>(null)
  val lockError: StateFlow<String?> = _lockError.asStateFlow()

  private val attempts = com.keepwallet.app.domain.AttemptLimiter()
  private var leftAt = 0L
  private fun clock(): Long = android.os.SystemClock.elapsedRealtime()

  /** The app went out of sight: remember when, so coming back can decide whether to ask for the PIN. */
  fun onBackgrounded() {
    settings.hideAgainIfNeeded()
    if (settings.lockEnabled.value) leftAt = clock()
  }

  /** The app came back: lock it if it was away longer than the chosen time. */
  fun checkLock() {
    if (!settings.lockEnabled.value || _locked.value || leftAt == 0L) return
    if (com.keepwallet.app.domain.LockPolicy.shouldLock(settings.lockTimeout.value, clock() - leftAt)) _locked.value = true
    leftAt = 0L
  }

  fun lockNow() {
    if (settings.lockEnabled.value) _locked.value = true
  }

  /** Returns true and opens the app when [pin] is right; otherwise sets a message (and slows guessing down). */
  fun unlockWithPin(pin: String): Boolean {
    val wait = attempts.remainingMs(clock())
    if (wait > 0) {
      _lockError.value = "Too many wrong tries. Wait ${(wait + 999) / 1000} seconds."
      return false
    }
    if (settings.verifyPin(pin)) {
      attempts.reset()
      _lockError.value = null
      _locked.value = false
      return true
    }
    attempts.failed(clock())
    val blocked = attempts.remainingMs(clock())
    _lockError.value = if (blocked > 0) "Too many wrong tries. Wait ${(blocked + 999) / 1000} seconds." else "Wrong PIN. ${attempts.triesLeft} tries left."
    return false
  }

  fun unlockWithBiometric() {
    attempts.reset()
    _lockError.value = null
    _locked.value = false
  }

  fun clearLockError() {
    _lockError.value = null
  }

  fun enableLock(pin: String, useBiometric: Boolean) {
    if (!com.keepwallet.app.domain.PinHasher.isValidPin(pin)) return
    settings.enableLock(pin, useBiometric)
    say("App lock is on")
  }

  /** Turns the lock off, but only for someone who knows the current PIN. */
  fun disableLock(currentPin: String): Boolean {
    if (!settings.verifyPin(currentPin)) return false
    settings.disableLock()
    _locked.value = false
    say("App lock is off")
    return true
  }

  fun changePin(currentPin: String, newPin: String): Boolean {
    if (!settings.verifyPin(currentPin) || !com.keepwallet.app.domain.PinHasher.isValidPin(newPin)) return false
    settings.enableLock(newPin, settings.biometric.value)
    say("PIN changed")
    return true
  }

  fun setBiometric(enabled: Boolean) = settings.setBiometric(enabled)
  fun setLockTimeout(seconds: Int) = settings.setLockTimeout(seconds)
  fun setHideInRecents(hide: Boolean) = settings.setHideInRecents(hide)

  fun setHideBalance(hidden: Boolean) = settings.setHideBalance(hidden)
  fun setHideOnOpen(value: Boolean) = settings.setHideOnOpen(value)
  fun toggleBalanceVisibility() = settings.toggleBalanceVisibility()
  fun setGridView(screen: String, grid: Boolean) = settings.setGridView(screen, grid)

  fun setAccent(value: AccentColor) = settings.setAccent(value)
  fun setBackgroundMode(value: BackgroundMode) = settings.setBackgroundMode(value)
  fun setColorStyle(value: com.keepwallet.app.data.ColorStyle) = settings.setColorStyle(value)
  fun setSystemColors(value: Boolean) = settings.setSystemColors(value)
  /** The converter's "Update rates" button: the only network call in the app. */
  fun updateRates() {
    if (_ratesStatus.value.loading) return
    _ratesStatus.value = RatesStatus(loading = true)
    viewModelScope.launch {
      val table = withContext(Dispatchers.IO) { container.rateFetcher(System.currentTimeMillis()) }
      if (table != null) {
        settings.setRates(table)
        _ratesStatus.value = RatesStatus(message = "Rates updated.")
      } else {
        _ratesStatus.value = RatesStatus(message = "Could not reach the rate service. Check your connection; the rates you had are kept.")
      }
    }
  }

  fun applyPalette(palette: com.keepwallet.app.data.AppPalette) = settings.applyPalette(palette)
  fun setCustomAccent(argb: Int) = settings.setCustomAccent(argb)
  fun finishOnboarding() = settings.finishOnboarding()
  fun markSplashShown() = settings.markSplashShown()
  fun setChartType(type: ChartType) = settings.setChartType(type)
  fun setShowTotalOnHero(enabled: Boolean) = settings.setShowTotalOnHero(enabled)
  fun setCurrency(symbol: String) = settings.setCurrencySymbol(symbol)

  fun exportBackup(uri: android.net.Uri) {
    viewModelScope.launch {
      val count = container.backup.export(uri)
      say(if (count != null) "Backup saved · $count transactions" else "Couldn't save the backup")
    }
  }

  fun exportCsv(uri: android.net.Uri) {
    viewModelScope.launch {
      val count = container.backup.exportCsv(uri)
      say(if (count != null) "Exported $count entries" else "Couldn't save the file")
    }
  }

  fun importBackup(uri: android.net.Uri) {
    viewModelScope.launch {
      when (val result = container.backup.import(uri)) {
        is ImportResult.Success -> {
          _filters.value = ActivityFilters()
          _viewingId.value = null
          say("Restored ${result.transactions} transactions")
        }
        is ImportResult.Invalid -> say(result.reason)
      }
    }
  }

  fun googleSyncInfo() = say("Google sync isn't available yet. Use Export backup to move your data.")

  fun loadSampleData() {
    viewModelScope.launch {
      repository.replaceWithSampleData(_today.value)
      _filters.value = ActivityFilters()
      say("Sample data loaded")
    }
  }

  fun eraseAllData() {
    viewModelScope.launch {
      repository.eraseAll()
      _filters.value = ActivityFilters()
      say("All data erased")
    }
  }

  class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
  }

  private companion object {
    const val BANNER_DURATION_MS = 12_000L
    const val DUPLICATE_WINDOW_MS = 10 * 60 * 1000L
    const val RECEIPT_CLEANUP_DELAY_MS = 20_000L
  }
}
