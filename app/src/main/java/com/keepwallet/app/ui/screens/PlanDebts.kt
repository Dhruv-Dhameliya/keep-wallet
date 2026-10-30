package com.keepwallet.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.IouEntity
import com.keepwallet.app.data.IouType
import com.keepwallet.app.domain.Money
import com.keepwallet.app.ui.AppData
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.EmptyState
import com.keepwallet.app.ui.components.Eyebrow
import com.keepwallet.app.ui.components.FormSheet
import com.keepwallet.app.ui.components.LocalCurrency
import com.keepwallet.app.ui.components.SegmentedToggle
import com.keepwallet.app.ui.components.heroFontSize
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dueFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

fun LazyListScope.debtItems(
  viewModel: MainViewModel,
  data: AppData,
  today: LocalDate,
  dialogs: PlanDialogs
) {
  val ious = data.ious
  val active = ious.filter { !it.settled }
  val settled = ious.filter { it.settled }

  val owedToMe = active.filter { it.type == IouType.OWED_TO_ME }.sumOf { it.amountMinor }
  val iOwe = active.filter { it.type == IouType.I_OWE }.sumOf { it.amountMinor }
  val net = owedToMe - iOwe

  if (ious.isNotEmpty()) {
    item(key = "debts_summary") {
      AppCard(Modifier.fillMaxWidth().testTag("debts_summary")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Eyebrow("Net debt position")
          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              (if (net >= 0) "+" else "") + money(net, whole = true),
              style = MaterialTheme.typography.displaySmall.copy(
                fontSize = heroFontSize(money(net, whole = true), 34),
                fontWeight = FontWeight.Bold
              ),
              color = if (net >= 0) MaterialTheme.extra.income else MaterialTheme.colorScheme.error,
              modifier = Modifier.testTag("debts_net")
            )

            OutlinedButton(
              onClick = { dialogs.debtNew = true },
              modifier = Modifier.testTag("add_debt_button")
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null)
              Spacer(Modifier.width(6.dp))
              Text("Add")
            }
          }

          Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text("Owed to you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                money(owedToMe, whole = true),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.extra.income
              )
            }
            Column(horizontalAlignment = Alignment.End) {
              Text("You owe", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                money(iOwe, whole = true),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      }
    }
  }

  if (ious.isEmpty()) {
    item(key = "debts_empty") {
      AppCard(Modifier.fillMaxWidth()) {
        EmptyState(
          icon = Icons.Rounded.Handshake,
          title = "No debts or loans tracked",
          body = "Keep track of money borrowed or lent with friends, family, and coworkers.",
          actionLabel = "Add a debt",
          onAction = { dialogs.debtNew = true }
        )
      }
    }
  } else {
    if (active.isNotEmpty()) {
      item(key = "header_active_debts") {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Eyebrow("Active (${active.size})")
        }
      }

      items(active, key = { "debt_${it.id}" }) { iou ->
        DebtCard(
          iou = iou,
          today = today,
          onSettle = { dialogs.debtSettle = iou },
          onEdit = { dialogs.debtEditing = iou },
          onDelete = { viewModel.deleteIou(iou.id) }
        )
      }
    }

    if (settled.isNotEmpty()) {
      item(key = "header_settled_debts") {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Eyebrow("Settled (${settled.size})")
        }
      }

      items(settled, key = { "debt_settled_${it.id}" }) { iou ->
        DebtCard(
          iou = iou,
          today = today,
          onSettle = { viewModel.setIouSettled(iou.id, false) },
          onEdit = { dialogs.debtEditing = iou },
          onDelete = { viewModel.deleteIou(iou.id) }
        )
      }
    }
  }
}

@Composable
private fun DebtCard(
  iou: IouEntity,
  today: LocalDate,
  onSettle: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  var menuOpen by remember { mutableStateOf(false) }
  val isOwedToMe = iou.type == IouType.OWED_TO_ME
  val dueDate: LocalDate? = iou.dueEpochDay?.let { LocalDate.ofEpochDay(it) }
  val isOverdue = dueDate != null && dueDate.isBefore(today) && !iou.settled

  AppCard(
    modifier = Modifier.fillMaxWidth().testTag("debt_card_${iou.id}"),
    onClick = onEdit
  ) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(
        onClick = onSettle,
        modifier = Modifier.testTag("toggle_debt_${iou.id}")
      ) {
        Icon(
          if (iou.settled) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
          contentDescription = if (iou.settled) "Mark as active" else "Settle debt",
          tint = if (iou.settled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
      }

      Spacer(Modifier.width(8.dp))

      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            iou.person,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )

          Spacer(Modifier.width(8.dp))

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isOwedToMe) MaterialTheme.extra.income.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
          ) {
            Text(
              if (isOwedToMe) "Owes you" else "You owe",
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
              style = MaterialTheme.typography.labelSmall,
              color = if (isOwedToMe) MaterialTheme.extra.income else MaterialTheme.colorScheme.error,
              fontWeight = FontWeight.Medium
            )
          }
        }

        if (!iou.note.isNullOrBlank()) {
          Text(
            iou.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (dueDate != null) {
          Text(
            (if (isOverdue) "Overdue · " else "Due ") + dueFormat.format(dueDate),
            style = MaterialTheme.typography.labelSmall,
            color = if (isOverdue) MaterialTheme.extra.warning else MaterialTheme.colorScheme.outline
          )
        }
      }

      Spacer(Modifier.width(8.dp))

      Column(horizontalAlignment = Alignment.End) {
        Text(
          money(iou.amountMinor, whole = false),
          style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
          color = if (iou.settled) MaterialTheme.colorScheme.outline
                 else if (isOwedToMe) MaterialTheme.extra.income
                 else MaterialTheme.colorScheme.error
        )

        Box {
          IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
              text = { Text("Edit") },
              leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
              onClick = { menuOpen = false; onEdit() }
            )
            DropdownMenuItem(
              text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
              leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
              onClick = { menuOpen = false; onDelete() }
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebtSheet(
  existing: IouEntity?,
  onSave: (IouEntity) -> Unit,
  onDelete: (() -> Unit)?,
  onDismiss: () -> Unit
) {
  var person by rememberSaveable { mutableStateOf(existing?.person.orEmpty()) }
  var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.toEditText(it.amountMinor) }.orEmpty()) }
  var type by rememberSaveable { mutableStateOf(existing?.type ?: IouType.OWED_TO_ME) }
  var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
  var dueDay by rememberSaveable { mutableStateOf<Long?>(existing?.dueEpochDay) }
  var showDatePicker by remember { mutableStateOf(false) }

  val currency = LocalCurrency.current
  val amountMinor = Money.parseMinor(amountText)
  val canSave = person.isNotBlank() && amountMinor > 0

  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "New debt or loan" else "Edit debt") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentedToggle(
          options = listOf(
            IouType.OWED_TO_ME to "Owed to me",
            IouType.I_OWE to "I owe"
          ),
          selected = type,
          onSelect = { type = it },
          modifier = Modifier.fillMaxWidth().testTag("debt_type_toggle")
        )

        OutlinedTextField(
          value = person,
          onValueChange = { person = it },
          label = { Text(if (type == IouType.OWED_TO_ME) "Who owes you?" else "Who do you owe?") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("debt_person_field")
        )

        OutlinedTextField(
          value = amountText,
          onValueChange = { amountText = it },
          label = { Text("Amount ($currency)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("debt_amount_field")
        )

        OutlinedTextField(
          value = note,
          onValueChange = { note = it },
          label = { Text("Note (optional)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("debt_note_field")
        )

        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
              if (dueDay != null) "Due: ${dueFormat.format(LocalDate.ofEpochDay(dueDay!!))}" else "No due date set",
              style = MaterialTheme.typography.bodyMedium
            )
          }

          Row {
            if (dueDay != null) {
              TextButton(onClick = { dueDay = null }) { Text("Clear") }
            }
            TextButton(onClick = { showDatePicker = true }) {
              Text(if (dueDay != null) "Change" else "Set date")
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = canSave,
        onClick = {
          onSave(
            IouEntity(
              id = existing?.id ?: 0L,
              person = person.trim(),
              amountMinor = amountMinor,
              type = type,
              dueEpochDay = dueDay,
              note = note.trim().takeIf { it.isNotEmpty() },
              settled = existing?.settled ?: false
            )
          )
        },
        modifier = Modifier.testTag("debt_save")
      ) { Text("Save") }
    },
    dismissButton = {
      Row {
        if (onDelete != null) {
          TextButton(onClick = onDelete) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
          }
        }
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    }
  )

  if (showDatePicker) {
    val initialDate = dueDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
    val datePickerState = rememberDatePickerState(
      initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          datePickerState.selectedDateMillis?.let { millis ->
            dueDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
          }
          showDatePicker = false
        }) { Text("OK") }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }
}

@Composable
fun SettleDebtDialog(
  iou: IouEntity,
  accounts: List<AccountEntity>,
  onSettleOnly: () -> Unit,
  onSettleWithAccount: (Long) -> Unit,
  onDismiss: () -> Unit
) {
  var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Settle debt") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val directionText = if (iou.type == IouType.OWED_TO_ME) "received from ${iou.person}" else "paid to ${iou.person}"
        Text("Record transaction of ${money(iou.amountMinor)} $directionText?")

        if (accounts.isNotEmpty()) {
          Text("Account:", style = MaterialTheme.typography.labelMedium)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            accounts.forEach { acc ->
              Row(
                Modifier
                  .fillMaxWidth()
                  .clickable { selectedAccountId = acc.id }
                  .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  if (selectedAccountId == acc.id) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                  contentDescription = null,
                  tint = if (selectedAccountId == acc.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(8.dp))
                Text(acc.name, style = MaterialTheme.typography.bodyMedium)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          selectedAccountId?.let { onSettleWithAccount(it) } ?: onSettleOnly()
        }
      ) {
        Text("Settle & record")
      }
    },
    dismissButton = {
      Row {
        TextButton(onClick = onSettleOnly) {
          Text("Settle only")
        }
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    }
  )
}
