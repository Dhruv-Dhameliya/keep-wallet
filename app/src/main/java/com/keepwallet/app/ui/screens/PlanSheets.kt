package com.keepwallet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Money
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.MoveMoneyState
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.FormSheet
import com.keepwallet.app.ui.components.LocalCurrency
import com.keepwallet.app.ui.components.money

/** Keeps a typed amount valid: digits and at most one dot with two decimals. */
fun sanitizeAmount(text: String): String {
  val dot = text.indexOf('.')
  val cleaned = buildString {
    text.forEachIndexed { index, char ->
      if (char.isDigit() || (char == '.' && index == dot)) append(char)
    }
  }
  val point = cleaned.indexOf('.')
  return if (point >= 0 && cleaned.length - point > 3) cleaned.take(point + 3) else cleaned.take(12)
}

@Composable
fun LimitsSheet(
  category: CategoryEntity,
  onDismiss: () -> Unit,
  onSave: (monthlyMinor: Long, weeklyMinor: Long, rollover: Boolean) -> Unit
) {
  val symbol = LocalCurrency.current
  var monthly by remember { mutableStateOf(Money.toEditText(category.monthlyLimitMinor).takeIf { it != "0" }.orEmpty()) }
  var weeklyText by remember { mutableStateOf(Money.toEditText(category.weeklyLimitMinor).takeIf { it != "0" }.orEmpty()) }
  var rollover by remember { mutableStateOf(category.rollover) }
  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text("${category.name} limits") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "Leave a field empty for no limit.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
          value = monthly,
          onValueChange = { monthly = sanitizeAmount(it) },
          label = { Text("Monthly limit ($symbol)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("limit_monthly")
        )
        OutlinedTextField(
          value = weeklyText,
          onValueChange = { weeklyText = sanitizeAmount(it) },
          label = { Text("Weekly limit ($symbol)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("limit_weekly")
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Carry over what is left", style = MaterialTheme.typography.titleSmall)
            Text("Unspent monthly budget is added to next month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          androidx.compose.material3.Switch(checked = rollover, onCheckedChange = { rollover = it }, modifier = Modifier.testTag("limit_rollover"))
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSave(Money.parseMinor(monthly), Money.parseMinor(weeklyText), rollover) },
        modifier = Modifier.testTag("limit_save")
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

@Composable
fun MoveMoneySheet(
  viewModel: MainViewModel,
  state: MoveMoneyState,
  categories: List<CategoryEntity>
) {
  val symbol = LocalCurrency.current
  val expense = categories.filter { it.type == TxType.EXPENSE }
  val from = expense.firstOrNull { it.id == state.fromId }
  val to = expense.firstOrNull { it.id == state.toId }
  var fromMenu by remember { mutableStateOf(false) }
  var toMenu by remember { mutableStateOf(false) }
  val available = from?.let { Analytics.limitOf(it, state.weekly) } ?: 0L

  FormSheet(
    onDismissRequest = viewModel::closeMoveMoney,
    title = { Text("Move money") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "Shift ${if (state.weekly) "weekly" else "monthly"} limit from one category to another.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CategoryPickerField("From", from?.name ?: "Choose", fromMenu, { fromMenu = it }, expense, "move_from") { picked ->
          viewModel.updateMoveMoney { it.copy(fromId = picked.id) }
        }
        Text(
          "${money(available, whole = true)} available to move",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CategoryPickerField("To", to?.name ?: "Choose", toMenu, { toMenu = it }, expense, "move_to") { picked ->
          viewModel.updateMoveMoney { it.copy(toId = picked.id) }
        }
        OutlinedTextField(
          value = state.amountText,
          onValueChange = { text -> viewModel.updateMoveMoney { it.copy(amountText = sanitizeAmount(text)) } },
          label = { Text("Amount ($symbol)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("move_amount")
        )
      }
    },
    confirmButton = {
      TextButton(onClick = viewModel::confirmMoveMoney, modifier = Modifier.testTag("move_confirm")) { Text("Move") }
    },
    dismissButton = { TextButton(onClick = viewModel::closeMoveMoney) { Text("Cancel") } }
  )
}

@Composable
private fun CategoryPickerField(
  label: String,
  value: String,
  expanded: Boolean,
  onExpanded: (Boolean) -> Unit,
  options: List<CategoryEntity>,
  tag: String,
  onPick: (CategoryEntity) -> Unit
) {
  Box {
    OutlinedButton(
      onClick = { onExpanded(true) },
      modifier = Modifier.fillMaxWidth().testTag(tag),
      shape = RoundedCornerShape(14.dp)
    ) {
      Text("$label: $value", modifier = Modifier.weight(1f))
      Icon(Icons.Rounded.ExpandMore, contentDescription = null)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(option.name) },
          leadingIcon = { CategoryBadge(option, size = 28.dp) },
          onClick = {
            onPick(option)
            onExpanded(false)
          }
        )
      }
    }
  }
}
