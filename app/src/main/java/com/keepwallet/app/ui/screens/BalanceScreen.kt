package com.keepwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AmountText
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.ChevronToggle
import com.keepwallet.app.ui.components.ExpandArea
import com.keepwallet.app.ui.components.Eyebrow
import com.keepwallet.app.ui.components.FormSheet
import com.keepwallet.app.ui.components.LocalCurrency
import com.keepwallet.app.ui.components.MonthPickerDialog
import com.keepwallet.app.ui.components.PeriodNavigator
import com.keepwallet.app.ui.components.ResponsiveRow
import com.keepwallet.app.ui.components.SegmentedToggle
import com.keepwallet.app.ui.components.SubScreenHeader
import com.keepwallet.app.ui.components.TransactionRow
import com.keepwallet.app.ui.components.ViewModeToggle
import com.keepwallet.app.ui.components.heroFontSize
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra

fun accountIcon(kind: AccountKind): ImageVector = when (kind) {
  AccountKind.BANK -> Icons.Rounded.AccountBalance
  AccountKind.WALLET -> Icons.Rounded.AccountBalanceWallet
  AccountKind.CASH -> Icons.Rounded.Payments
}

fun accountKindLabel(kind: AccountKind): String = when (kind) {
  AccountKind.BANK -> "Bank"
  AccountKind.WALLET -> "Wallet"
  AccountKind.CASH -> "Cash"
}

/**
 * What opens when you tap the balance card: every account with its balance (tap one to see its latest entries or
 * set the amount you have right now), the total, and income, expense and net for the month the app is on.
 */
@Composable
fun BalanceScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  val month by viewModel.month.collectAsStateWithLifecycle()
  val today by viewModel.today.collectAsStateWithLifecycle()
  val hidden by viewModel.hideBalance.collectAsStateWithLifecycle()
  val showTotal by viewModel.showTotalOnHero.collectAsStateWithLifecycle()
  val heroAccounts by viewModel.heroAccounts.collectAsStateWithLifecycle()
  val gridViews by viewModel.gridViews.collectAsStateWithLifecycle()
  var showMonthPicker by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<AccountEntity?>(null) }
  var adding by remember { mutableStateOf<AccountKind?>(null) }
  var transferring by remember { mutableStateOf(false) }
  val grid = "accounts" in gridViews

  if (!data.loaded) return

  val balances = remember(data.accounts, data.transactions) {
    data.accounts.associateWith { Analytics.accountBalance(it, data.transactions) }
  }
  val banks = data.accounts.filter { it.kind == AccountKind.BANK }
  val others = data.accounts.filter { it.kind != AccountKind.BANK }
  val bankTotal = banks.sumOf { balances[it] ?: 0 }
  val otherTotal = others.sumOf { balances[it] ?: 0 }
  val monthTx = remember(data.transactions, month) { Analytics.inSpan(data.transactions, Periods.monthSpan(month)) }
  val income = Analytics.total(monthTx, TxType.INCOME)
  val expense = Analytics.total(monthTx, TxType.EXPENSE)
  val net = income - expense
  val extra = MaterialTheme.extra
  val mask = "••••••"

  Column(modifier.fillMaxSize()) {
    SubScreenHeader(
      "Balance", onBack,
      trailing = { ViewModeToggle(grid = grid, onChange = { viewModel.setGridView("accounts", it) }, modifier = Modifier.padding(end = 12.dp)) }
    )
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("balance_page"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        AppCard(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(18.dp)) {
            Eyebrow("Total balance")
            val totalText = if (hidden) mask else money(bankTotal + otherTotal)
            AmountText(
              totalText,
              style = MaterialTheme.typography.displaySmall.copy(fontSize = heroFontSize(totalText.dropLast(3), 40)),
              modifier = Modifier.testTag("balance_total")
            )
            Spacer(Modifier.height(6.dp))
            ResponsiveRow {
              Text("Bank ${if (hidden) mask else money(bankTotal)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("Wallets & cash ${if (hidden) mask else money(otherTotal)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }

      item(key = "hero_choice") {
        // What the balance card on Home adds up: tick the accounts you want in it.
        val bankIds = banks.map { it.id }.toSet()
        val allIds = data.accounts.map { it.id }.toSet()
        val counted = heroAccounts?.filter { it in allIds }?.toSet()?.takeIf { it.isNotEmpty() } ?: if (showTotal) allIds else bankIds
        AppCard(Modifier.fillMaxWidth().testTag("hero_choice")) {
          Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Shown on the home card", style = MaterialTheme.typography.titleSmall)
            Text("The balance on the card adds up the accounts you tick.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              data.accounts.forEach { account ->
                androidx.compose.material3.FilterChip(
                  selected = account.id in counted,
                  onClick = {
                    val next = if (account.id in counted) counted - account.id else counted + account.id
                    // The card always adds up something.
                    if (next.isNotEmpty()) viewModel.setHeroAccounts(next)
                  },
                  label = { Text(account.name) },
                  leadingIcon = if (account.id in counted) ({ Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
                  modifier = Modifier.testTag("hero_account_${account.name}")
                )
              }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
              TextButton(onClick = { viewModel.setShowTotalOnHero(false) }, modifier = Modifier.testTag("hero_preset_banks")) { Text("Bank only") }
              TextButton(onClick = { viewModel.setShowTotalOnHero(true) }, modifier = Modifier.testTag("hero_preset_all")) { Text("Everything") }
            }
          }
        }
      }

      item {
        if (data.accounts.size >= 2) {
          androidx.compose.material3.FilledTonalButton(
            onClick = { transferring = true },
            modifier = Modifier.fillMaxWidth().testTag("transfer_button"),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
          ) {
            Icon(androidx.compose.material.icons.Icons.Rounded.SwapHoriz, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Transfer between accounts")
          }
        }
      }
      item { AccountGroup("Bank accounts", banks, balances, data.transactions, data.categories, today, grid, hidden, "Add bank account", AccountKind.BANK, viewModel, { editing = it }) { adding = it } }
      item { AccountGroup("Wallets & cash", others, balances, data.transactions, data.categories, today, grid, hidden, "Add wallet or cash", AccountKind.WALLET, viewModel, { editing = it }) { adding = it } }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("This month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
            PeriodNavigator(
              label = Periods.monthLabel(month),
              onPrevious = { viewModel.shiftMonth(-1) },
              onNext = { viewModel.shiftMonth(1) },
              onLabelClick = { showMonthPicker = true },
              previousDescription = "Previous month",
              nextDescription = "Next month",
              modifier = Modifier.testTag("balance_month")
            )
          }
          AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
              StatLine("Income", if (hidden) mask else money(income, sign = income > 0), extra.income, "balance_income")
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              StatLine("Expense", if (hidden) mask else money(-expense), MaterialTheme.colorScheme.onSurface, "balance_expense")
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              StatLine("Net", if (hidden) mask else money(net, sign = net > 0), if (net >= 0) extra.income else extra.warning, "balance_net", bold = true)
            }
          }
        }
      }
    }
  }

  if (showMonthPicker) {
    MonthPickerDialog(
      current = month,
      onDismiss = { showMonthPicker = false },
      onPick = {
        viewModel.setMonth(it)
        showMonthPicker = false
      }
    )
  }

  if (transferring) {
    TransferSheet(
      accounts = data.accounts,
      balances = balances,
      onSave = { from, to, amount, note ->
        viewModel.saveTransfer(from, to, amount, note)
        transferring = false
      },
      onDismiss = { transferring = false }
    )
  }

  val sheetAccount = editing
  val sheetKind = adding
  if (sheetAccount != null || sheetKind != null) {
    AccountSheet(
      existing = sheetAccount,
      newKind = sheetKind ?: AccountKind.BANK,
      currentBalance = sheetAccount?.let { balances[it] } ?: 0L,
      onSave = { account, balance ->
        viewModel.saveAccount(account, balance)
        editing = null
        adding = null
      },
      onDelete = {
        sheetAccount?.let(viewModel::deleteAccount)
        editing = null
      },
      onDismiss = {
        editing = null
        adding = null
      }
    )
  }
}

@Composable
private fun StatLine(label: String, value: String, color: Color, tag: String, bold: Boolean = false) {
  ResponsiveRow(Modifier.padding(vertical = 10.dp)) {
    Text(label, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge)
    Text(
      value,
      style = MaterialTheme.typography.titleMedium,
      color = color,
      maxLines = 1,
      softWrap = false,
      modifier = Modifier.testTag(tag)
    )
  }
}

@Composable
private fun AccountGroup(
  title: String,
  accounts: List<AccountEntity>,
  balances: Map<AccountEntity, Long>,
  transactions: List<TransactionEntity>,
  categories: List<CategoryEntity>,
  today: java.time.LocalDate,
  grid: Boolean,
  hidden: Boolean,
  addLabel: String,
  addKind: AccountKind,
  viewModel: MainViewModel,
  onEdit: (AccountEntity) -> Unit,
  onAdd: (AccountKind) -> Unit
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
    if (accounts.isEmpty()) {
      AppCard(Modifier.fillMaxWidth()) {
        Text("Nothing here yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp))
      }
    } else if (grid) {
      var open by rememberSaveable(title) { mutableLongStateOf(-1L) }
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.chunked(2).forEach { pair ->
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { account ->
              AccountTile(account, balances[account] ?: 0L, hidden, open == account.id, Modifier.weight(1f)) { open = if (open == account.id) -1L else account.id }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
          }
          pair.firstOrNull { it.id == open }?.let { account ->
            AppCard(Modifier.fillMaxWidth().testTag("account_details_${account.name}")) {
              Column(Modifier.padding(14.dp)) { AccountDetails(account, transactions, categories, today, onEdit = { onEdit(account) }, onOpen = viewModel::openDetails) }
            }
          }
        }
      }
    } else {
      AppCard(Modifier.fillMaxWidth()) {
        accounts.forEachIndexed { index, account ->
          if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
          AccountRow(account, balances[account] ?: 0L, hidden, transactions, categories, today, onEdit = { onEdit(account) }, onOpen = viewModel::openDetails)
        }
      }
    }
    TextButton(onClick = { onAdd(addKind) }, modifier = Modifier.testTag("add_account_${addKind.name}")) {
      Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(6.dp))
      Text(addLabel)
    }
  }
}

@Composable
private fun AccountBadge(kind: AccountKind, size: androidx.compose.ui.unit.Dp = 38.dp) {
  Box(
    Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
    contentAlignment = Alignment.Center
  ) {
    Icon(accountIcon(kind), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.55f))
  }
}

@Composable
private fun AccountRow(
  account: AccountEntity,
  balance: Long,
  hidden: Boolean,
  transactions: List<TransactionEntity>,
  categories: List<CategoryEntity>,
  today: java.time.LocalDate,
  onEdit: () -> Unit,
  onOpen: (TransactionEntity) -> Unit
) {
  var open by rememberSaveable(account.id) { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth()) {
    androidx.compose.material3.Surface(
      onClick = { open = !open },
      color = Color.Transparent,
      modifier = Modifier.fillMaxWidth().testTag("account_row_${account.name}")
    ) {
      Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AccountBadge(account.kind)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(account.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
          Text(accountKindLabel(account.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
          if (hidden) "••••" else money(balance),
          style = MaterialTheme.typography.titleSmall,
          color = if (balance < 0) MaterialTheme.extra.warning else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.testTag("account_balance_${account.name}")
        )
        ChevronToggle(open, Modifier.padding(start = 4.dp))
      }
    }
    ExpandArea(open) {
      Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp).testTag("account_details_${account.name}")) {
        AccountDetails(account, transactions, categories, today, onEdit, onOpen)
      }
    }
  }
}

@Composable
private fun AccountTile(account: AccountEntity, balance: Long, hidden: Boolean, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
  AppCard(modifier.testTag("account_row_${account.name}"), onClick = onClick) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        AccountBadge(account.kind, 32.dp)
        Spacer(Modifier.weight(1f))
        ChevronToggle(open, Modifier.size(18.dp))
      }
      Text(account.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
      Text(
        if (hidden) "••••" else money(balance),
        style = MaterialTheme.typography.titleMedium,
        color = if (balance < 0) MaterialTheme.extra.warning else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.testTag("account_balance_${account.name}")
      )
      Text(accountKindLabel(account.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** The latest entries booked to one account, with the button that sets the amount it holds. */
@Composable
private fun AccountDetails(
  account: AccountEntity,
  transactions: List<TransactionEntity>,
  categories: List<CategoryEntity>,
  today: java.time.LocalDate,
  onEdit: () -> Unit,
  onOpen: (TransactionEntity) -> Unit
) {
  val recent = remember(transactions, account.id) { transactions.filter { it.accountId == account.id }.take(3) }
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    if (recent.isEmpty()) {
      Text("No entries in this account yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    recent.forEach { tx ->
      TransactionRow(
        transaction = tx,
        category = categories.firstOrNull { it.id == tx.categoryId },
        accountName = null,
        subtitle = Periods.shortDay(Periods.millisToDate(tx.timestamp), today),
        framed = false,
        onClick = { onOpen(tx) }
      )
    }
    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().testTag("account_edit_${account.name}")) { Text("Edit account or balance") }
  }
}

@Composable
private fun AccountSheet(
  existing: AccountEntity?,
  newKind: AccountKind,
  currentBalance: Long,
  onSave: (AccountEntity, Long) -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit
) {
  val symbol = LocalCurrency.current
  var name by remember { mutableStateOf(existing?.name.orEmpty()) }
  var kind by remember { mutableStateOf(existing?.kind ?: newKind) }
  // Untouched means unchanged, even for an overdrawn (negative) account the keypad can't type.
  val initialBalance = if (currentBalance > 0) Money.toEditText(currentBalance) else ""
  var balance by remember { mutableStateOf(initialBalance) }
  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "New account" else "Edit account") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name, onValueChange = { name = it.take(24) },
          label = { Text("Name") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("account_name")
        )
        SegmentedToggle(
          options = listOf(AccountKind.BANK to "Bank", AccountKind.WALLET to "Wallet", AccountKind.CASH to "Cash"),
          selected = kind, onSelect = { kind = it }, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = balance, onValueChange = { balance = sanitizeAmount(it) },
          label = { Text("Balance right now ($symbol)") }, singleLine = true,
          supportingText = { Text("What this account holds today. New entries add to or take from it.") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("account_balance_input")
        )
        if (existing != null) {
          TextButton(onClick = onDelete, modifier = Modifier.testTag("account_delete")) {
            Text("Delete account", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = name.isNotBlank(),
        onClick = { onSave((existing ?: AccountEntity(name = "")).copy(name = name.trim(), kind = kind), if (balance == initialBalance) currentBalance else Money.parseMinor(balance)) },
        modifier = Modifier.testTag("account_save")
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

/** Moves money from one account to another: both balances change, income and expense do not. */
@Composable
private fun TransferSheet(accounts: List<AccountEntity>, balances: Map<AccountEntity, Long>, onSave: (Long, Long, Long, String?) -> Unit, onDismiss: () -> Unit) {
  val symbol = LocalCurrency.current
  var fromId by remember { mutableStateOf(accounts.firstOrNull { (balances[it] ?: 0) > 0 }?.id ?: accounts.first().id) }
  var toId by remember { mutableStateOf(accounts.firstOrNull { it.id != fromId }?.id ?: accounts.first().id) }
  var amount by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  val minor = Money.parseMinor(amount)
  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text("Transfer") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceField("From", accounts.firstOrNull { it.id == fromId }?.name ?: "Choose", accounts, { it.name }, { fromId = it.id }, "transfer_from")
        ChoiceField("To", accounts.firstOrNull { it.id == toId }?.name ?: "Choose", accounts, { it.name }, { toId = it.id }, "transfer_to")
        OutlinedTextField(
          value = amount, onValueChange = { amount = sanitizeAmount(it) }, label = { Text("Amount ($symbol)") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("transfer_amount")
        )
        OutlinedTextField(
          value = note, onValueChange = { note = it.take(120) }, label = { Text("Note (optional)") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("transfer_note")
        )
        if (fromId == toId) Text("Pick two different accounts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }
    },
    confirmButton = {
      TextButton(
        enabled = minor > 0 && fromId != toId,
        onClick = { onSave(fromId, toId, minor, note) },
        modifier = Modifier.testTag("transfer_save")
      ) { Text("Transfer") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}
