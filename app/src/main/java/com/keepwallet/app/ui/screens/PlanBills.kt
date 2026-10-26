package com.keepwallet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keepwallet.app.data.Frequency
import com.keepwallet.app.data.RecurringEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.Recurrence
import com.keepwallet.app.ui.AppData
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.ChevronToggle
import com.keepwallet.app.ui.components.EmptyState
import com.keepwallet.app.ui.components.ExpandArea
import com.keepwallet.app.ui.components.Eyebrow
import com.keepwallet.app.ui.components.FormSheet
import com.keepwallet.app.ui.components.LocalCurrency
import com.keepwallet.app.ui.components.SegmentedToggle
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What a bill costs per month, whatever its rhythm: weekly bills count 52/12 times, yearly ones a twelfth. */
fun monthlyEquivalent(item: RecurringEntity): Long = when (item.frequency) {
  Frequency.WEEKLY -> item.amountMinor * 52 / 12
  Frequency.MONTHLY -> item.amountMinor
  Frequency.YEARLY -> item.amountMinor / 12
}

private val dueFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

fun LazyListScope.billItems(viewModel: MainViewModel, data: AppData, today: LocalDate, dialogs: PlanDialogs) {
  val bills = data.recurring.sortedWith(compareBy({ !it.active }, { it.nextDueDay }))
  val active = bills.filter { it.active }
  val perMonth = active.filter { it.type == TxType.EXPENSE }.sumOf(::monthlyEquivalent)
  val soon = active.filter { it.type == TxType.EXPENSE && LocalDate.ofEpochDay(it.nextDueDay) <= today.plusDays(30) }
  val overdue = active.count { LocalDate.ofEpochDay(it.nextDueDay).isBefore(today) }

  if (bills.isNotEmpty()) {
    item(key = "bills_summary") {
      AppCard(Modifier.fillMaxWidth().testTag("bills_summary")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Eyebrow("Bills per month")
          Text(
            money(perMonth, whole = true),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = com.keepwallet.app.ui.components.heroFontSize(money(perMonth, whole = true), 36)),
            maxLines = 1,
            modifier = Modifier.testTag("bills_per_month")
          )
          Text(
            "${money(soon.sumOf { it.amountMinor }, whole = true)} due in the next 30 days across ${soon.size} ${if (soon.size == 1) "bill" else "bills"}" +
              if (overdue > 0) " · $overdue overdue" else "",
            style = MaterialTheme.typography.bodySmall,
            color = if (overdue > 0) MaterialTheme.extra.warning else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }

  if (bills.isEmpty()) {
    item(key = "bills_empty") {
      AppCard(Modifier.fillMaxWidth()) {
        EmptyState(
          icon = Icons.Rounded.Event,
          title = "No bills yet",
          body = "Add rent, subscriptions or a salary once. Keep Wallet reminds you when they're due, or adds them for you.",
          actionLabel = "Add a bill",
          onAction = { dialogs.billNew = true }
        )
      }
    }
  } else {
    items(bills.size, key = { "bill_${bills[it].id}" }) { index ->
      BillRow(bills[index], data, today, viewModel, dialogs)
    }
  }

  item(key = "bills_add") {
    OutlinedButton(onClick = { dialogs.billNew = true }, modifier = Modifier.fillMaxWidth().testTag("add_bill"), shape = RoundedCornerShape(14.dp)) {
      Icon(Icons.Rounded.Add, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text("Add a bill")
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillRow(bill: RecurringEntity, data: AppData, today: LocalDate, viewModel: MainViewModel, dialogs: PlanDialogs) {
  var open by rememberSaveable(bill.id) { mutableStateOf(false) }
  val category = data.categories.firstOrNull { it.id == bill.categoryId }
  val account = data.accounts.firstOrNull { it.id == bill.accountId }
  val due = LocalDate.ofEpochDay(bill.nextDueDay)
  val late = bill.active && due.isBefore(today)
  val income = bill.type == TxType.INCOME
  AppCard(Modifier.fillMaxWidth().testTag("bill_${bill.name}"), onClick = { open = !open }) {
    Column(Modifier.padding(12.dp)) {
      // With big text the amount moves under the name instead of squeezing it.
      val narrow = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.25f
      val amountText: @Composable () -> Unit = {
        Text(
          money(if (income) bill.amountMinor else -bill.amountMinor, sign = income),
          style = MaterialTheme.typography.titleSmall,
          color = if (income) MaterialTheme.extra.income else MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          softWrap = false
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(category, size = 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(bill.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            if (bill.active) "${Recurrence.label(bill.frequency)} · ${Recurrence.dueText(due, today)}" else "Paused · ${Recurrence.label(bill.frequency)}",
            style = MaterialTheme.typography.bodySmall,
            color = if (late) MaterialTheme.extra.warning else MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (narrow) amountText()
        }
        if (!narrow) amountText()
        ChevronToggle(open, Modifier.padding(start = 4.dp))
      }
      ExpandArea(open) {
        Column(Modifier.padding(top = 10.dp).testTag("bill_details_${bill.name}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "${category?.name ?: "No category"} · ${account?.name ?: "No account"} · next ${due.format(dueFormat)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Add automatically when due", style = MaterialTheme.typography.titleSmall)
              Text("Otherwise it waits for you to tap ${if (income) "Received" else "Paid"}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = bill.autoPost, onCheckedChange = { viewModel.setRecurringAuto(bill.id, it) }, modifier = Modifier.testTag("bill_auto_${bill.name}"))
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (bill.active) {
              OutlinedButton(onClick = { viewModel.payRecurring(bill) }, modifier = Modifier.testTag("bill_pay_${bill.name}")) { Text(if (income) "Received" else "Mark paid") }
              OutlinedButton(onClick = { viewModel.skipRecurring(bill) }, modifier = Modifier.testTag("bill_skip_${bill.name}")) { Text("Skip once") }
            }
            TextButton(onClick = { viewModel.setRecurringActive(bill.id, !bill.active) }, modifier = Modifier.testTag("bill_pause_${bill.name}")) { Text(if (bill.active) "Pause" else "Resume") }
            TextButton(onClick = { dialogs.billEditing = bill }, modifier = Modifier.testTag("bill_edit_${bill.name}")) { Text("Edit") }
          }
        }
      }
    }
  }
}

// ---- Sheet ------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillSheet(
  existing: RecurringEntity?,
  data: AppData,
  today: LocalDate,
  onSave: (RecurringEntity) -> Unit,
  onDelete: (() -> Unit)?,
  onDismiss: () -> Unit
) {
  val symbol = LocalCurrency.current
  var name by remember { mutableStateOf(existing?.name.orEmpty()) }
  var amount by remember { mutableStateOf(existing?.amountMinor?.let(Money::toEditText).orEmpty()) }
  var type by remember { mutableStateOf(existing?.type ?: TxType.EXPENSE) }
  var categoryId by remember { mutableStateOf(existing?.categoryId ?: data.categories.firstOrNull { it.type == TxType.EXPENSE }?.id) }
  var accountId by remember { mutableStateOf(existing?.accountId ?: data.accounts.firstOrNull()?.id) }
  var frequency by remember { mutableStateOf(existing?.frequency ?: Frequency.MONTHLY) }
  var due by remember { mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.nextDueDay) } ?: today) }
  var auto by remember { mutableStateOf(existing?.autoPost ?: false) }
  var pickDate by remember { mutableStateOf(false) }
  val categories = data.categories.filter { it.type == type }
  val amountMinor = Money.parseMinor(amount)
  val canSave = name.isNotBlank() && amountMinor > 0 && categories.any { it.id == categoryId } && accountId != null

  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "New bill" else "Edit bill") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedToggle(
          options = listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income"),
          selected = type,
          onSelect = {
            type = it
            categoryId = data.categories.firstOrNull { c -> c.type == it }?.id
          },
          modifier = Modifier.fillMaxWidth().testTag("bill_type")
        )
        OutlinedTextField(
          value = name, onValueChange = { name = it.take(30) }, label = { Text("Name") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("bill_name")
        )
        OutlinedTextField(
          value = amount, onValueChange = { amount = sanitizeAmount(it) }, label = { Text("Amount ($symbol)") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("bill_amount")
        )
        ChoiceField(
          label = "Category",
          value = categories.firstOrNull { it.id == categoryId }?.name ?: "Choose",
          options = categories,
          optionLabel = { it.name },
          onPick = { categoryId = it.id },
          tag = "bill_category"
        )
        ChoiceField(
          label = "Account",
          value = data.accounts.firstOrNull { it.id == accountId }?.name ?: "Choose",
          options = data.accounts,
          optionLabel = { it.name },
          onPick = { accountId = it.id },
          tag = "bill_account"
        )
        SegmentedToggle(
          options = Frequency.entries.map { it to Recurrence.label(it) },
          selected = frequency,
          onSelect = { frequency = it },
          modifier = Modifier.fillMaxWidth().testTag("bill_frequency")
        )
        OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth().testTag("bill_due"), shape = RoundedCornerShape(14.dp)) {
          Icon(Icons.Rounded.CalendarToday, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Next due ${due.format(dueFormat)}", modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Add automatically when due", style = MaterialTheme.typography.titleSmall)
            Text("Adds the entry for you when you open the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(checked = auto, onCheckedChange = { auto = it }, modifier = Modifier.testTag("bill_auto"))
        }
        if (onDelete != null) {
          TextButton(onClick = onDelete, modifier = Modifier.testTag("bill_delete")) { Text("Delete bill", color = MaterialTheme.colorScheme.error) }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = canSave,
        onClick = {
          onSave(
            RecurringEntity(
              id = existing?.id ?: 0,
              name = name.trim(),
              type = type,
              amountMinor = amountMinor,
              categoryId = categoryId!!,
              accountId = accountId!!,
              frequency = frequency,
              nextDueDay = due.toEpochDay(),
              anchorDay = due.dayOfMonth,
              autoPost = auto,
              active = existing?.active ?: true
            )
          )
        },
        modifier = Modifier.testTag("bill_save")
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )

  if (pickDate) {
    val state = rememberDatePickerState(initialSelectedDateMillis = due.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
      onDismissRequest = { pickDate = false },
      confirmButton = {
        TextButton(onClick = {
          state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
          pickDate = false
        }) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } }
    ) { DatePicker(state = state) }
  }
}

/** A labelled drop-down: shows the current choice and opens a menu of [options]. */
@Composable
fun <T> ChoiceField(
  label: String,
  value: String,
  options: List<T>,
  optionLabel: (T) -> String,
  onPick: (T) -> Unit,
  tag: String,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier) {
    OutlinedButton(
      onClick = { expanded = true },
      modifier = Modifier.fillMaxWidth().testTag(tag),
      shape = RoundedCornerShape(14.dp)
    ) {
      Text("$label: $value", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Icon(Icons.Rounded.ExpandMore, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(optionLabel(option)) },
          onClick = {
            onPick(option)
            expanded = false
          },
          modifier = Modifier.testTag("${tag}_option_${optionLabel(option)}")
        )
      }
    }
  }
}
