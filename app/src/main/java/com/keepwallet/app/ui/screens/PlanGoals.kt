package com.keepwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keepwallet.app.data.GoalEntity
import com.keepwallet.app.data.RecurringEntity
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.percentUsed
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.AppProgressBar
import com.keepwallet.app.ui.components.ChevronToggle
import com.keepwallet.app.ui.components.EmptyState
import com.keepwallet.app.ui.components.ExpandArea
import com.keepwallet.app.ui.components.Eyebrow
import com.keepwallet.app.ui.components.FormSheet
import com.keepwallet.app.ui.components.LocalCurrency
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.CategoryIcons
import com.keepwallet.app.ui.theme.CategoryPalette
import com.keepwallet.app.ui.theme.extra

/** Which sheet of the Plan tab is open; kept out of the lists so the sheets survive scrolling. */
@Stable
class PlanDialogs {
  var goalEditing by mutableStateOf<GoalEntity?>(null)
  var goalNew by mutableStateOf(false)
  var goalAmount by mutableStateOf<GoalEntity?>(null)
  var billEditing by mutableStateOf<RecurringEntity?>(null)
  var billNew by mutableStateOf(false)
}

private val GoalIcons = listOf("savings", "travel", "housing", "work", "shopping", "gift", "education", "fitness", "pets", "health", "entertainment")
private val QuickAmounts = listOf(10L, 50L, 100L)

// ---- The list ---------------------------------------------------------------------------------------

fun LazyListScope.goalItems(viewModel: MainViewModel, goals: List<GoalEntity>, grid: Boolean, dialogs: PlanDialogs) {
  val saved = goals.sumOf { it.savedMinor.coerceAtMost(it.targetMinor) }
  val target = goals.sumOf { it.targetMinor }
  val finished = goals.count { it.savedMinor >= it.targetMinor }

  if (goals.isNotEmpty()) {
    item(key = "goals_summary") {
      AppCard(Modifier.fillMaxWidth().testTag("goals_summary")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Eyebrow("Set aside")
          Row(verticalAlignment = Alignment.Bottom) {
            Text(money(saved, whole = true), style = MaterialTheme.typography.displaySmall.copy(fontSize = com.keepwallet.app.ui.components.heroFontSize(money(saved, whole = true), 36)), maxLines = 1, modifier = Modifier.testTag("goals_saved"))
            Text("  of ${money(target, whole = true)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
          }
          AppProgressBar(saved.toFloat() / target.coerceAtLeast(1), height = 9.dp)
          Text(
            "${goals.size} ${if (goals.size == 1) "goal" else "goals"} · $finished complete",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }

  if (goals.isEmpty()) {
    item(key = "goals_empty") {
      AppCard(Modifier.fillMaxWidth()) {
        EmptyState(
          icon = Icons.Rounded.Savings,
          title = "No goals yet",
          body = "Saving for a trip, a laptop or a rainy day? Create a goal and put money towards it.",
          actionLabel = "Create a goal",
          onAction = { dialogs.goalNew = true }
        )
      }
    }
  } else if (grid) {
    val rows = goals.chunked(2)
    items(rows.size, key = { "goal_row_${rows[it].first().id}" }) { index ->
      GoalGridRow(rows[index], viewModel, dialogs)
    }
  } else {
    items(goals.size, key = { "goal_${goals[it].id}" }) { index ->
      GoalCard(goals[index], viewModel, dialogs)
    }
  }

  item(key = "goals_add") {
    OutlinedButton(
      onClick = { dialogs.goalNew = true },
      modifier = Modifier.fillMaxWidth().testTag("add_goal"),
      shape = RoundedCornerShape(14.dp)
    ) {
      Icon(Icons.Rounded.Add, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text("Add a goal")
    }
  }
}

@Composable
private fun GoalCard(goal: GoalEntity, viewModel: MainViewModel, dialogs: PlanDialogs) {
  var open by rememberSaveable(goal.id) { mutableStateOf(false) }
  AppCard(Modifier.fillMaxWidth().testTag("goal_${goal.name}"), onClick = { open = !open }) {
    Column(Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { com.keepwallet.app.ui.screens.GoalProgressRow(goal) }
        ChevronToggle(open, Modifier.padding(start = 6.dp))
      }
      ExpandArea(open) {
        Column(Modifier.padding(top = 10.dp).testTag("goal_details_${goal.name}")) { GoalDetails(goal, viewModel, dialogs) }
      }
    }
  }
}

@Composable
private fun GoalGridRow(row: List<GoalEntity>, viewModel: MainViewModel, dialogs: PlanDialogs) {
  var open by rememberSaveable { mutableLongStateOf(-1L) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      row.forEach { goal ->
        GoalTile(goal, open == goal.id, Modifier.weight(1f)) { open = if (open == goal.id) -1L else goal.id }
      }
      if (row.size == 1) Spacer(Modifier.weight(1f))
    }
    row.firstOrNull { it.id == open }?.let { goal ->
      AppCard(Modifier.fillMaxWidth().testTag("goal_details_${goal.name}")) {
        Column(Modifier.padding(14.dp)) { GoalDetails(goal, viewModel, dialogs) }
      }
    }
  }
}

@Composable
private fun GoalTile(goal: GoalEntity, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val tint = Color(goal.color)
  val done = goal.savedMinor >= goal.targetMinor
  AppCard(modifier.testTag("goal_${goal.name}"), onClick = onClick, container = tint.copy(alpha = if (open) 0.24f else 0.14f), border = null) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
          Icon(CategoryIcons.get(goal.iconKey), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.weight(1f))
        ChevronToggle(open, Modifier.size(18.dp))
      }
      Text(goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        if (done) "Fully funded" else "${percentUsed(goal.savedMinor, goal.targetMinor)}%",
        style = MaterialTheme.typography.titleMedium,
        color = if (done) MaterialTheme.extra.income else MaterialTheme.colorScheme.onSurface
      )
      AppProgressBar(goal.savedMinor.toFloat() / goal.targetMinor.coerceAtLeast(1), color = tint, track = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), height = 5.dp)
      Text("${money(goal.savedMinor, whole = true)} of ${money(goal.targetMinor, whole = true)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalDetails(goal: GoalEntity, viewModel: MainViewModel, dialogs: PlanDialogs) {
  val toGo = (goal.targetMinor - goal.savedMinor).coerceAtLeast(0)
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      if (toGo == 0L) "Fully funded. Nicely done." else "${money(toGo, whole = true)} to go.",
      style = MaterialTheme.typography.bodyMedium,
      color = if (toGo == 0L) MaterialTheme.extra.income else MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      QuickAmounts.forEach { whole ->
        OutlinedButton(
          onClick = { viewModel.addToGoal(goal, whole * 100) },
          modifier = Modifier.testTag("goal_add_${goal.name}_$whole"),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
        ) { Text("+ ${money(whole * 100, whole = true)}") }
      }
      OutlinedButton(
        onClick = { dialogs.goalAmount = goal },
        modifier = Modifier.testTag("goal_custom_${goal.name}"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
      ) { Text("Other amount") }
    }
    TextButton(onClick = { dialogs.goalEditing = goal }, modifier = Modifier.testTag("goal_edit_${goal.name}")) { Text("Edit goal") }
  }
}

// ---- Sheets -----------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalSheet(existing: GoalEntity?, onSave: (GoalEntity) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
  val symbol = LocalCurrency.current
  var name by remember { mutableStateOf(existing?.name.orEmpty()) }
  var target by remember { mutableStateOf(existing?.targetMinor?.let(Money::toEditText).orEmpty()) }
  var saved by remember { mutableStateOf(existing?.savedMinor?.takeIf { it > 0 }?.let(Money::toEditText).orEmpty()) }
  var icon by remember { mutableStateOf(existing?.iconKey ?: "savings") }
  var color by remember { mutableStateOf(existing?.color ?: CategoryPalette[3].toArgb()) }
  val targetMinor = Money.parseMinor(target)
  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "New goal" else "Edit goal") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name, onValueChange = { name = it.take(28) }, label = { Text("What are you saving for?") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("goal_name")
        )
        OutlinedTextField(
          value = target, onValueChange = { target = sanitizeAmount(it) }, label = { Text("Target ($symbol)") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("goal_target")
        )
        OutlinedTextField(
          value = saved, onValueChange = { saved = sanitizeAmount(it) }, label = { Text("Saved so far ($symbol)") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("goal_saved")
        )
        Text("Icon", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          GoalIcons.forEach { key ->
            val selected = key == icon
            Box(
              Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (selected) Color(color).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.RadioButton) { icon = key }
                .semantics { contentDescription = "Icon $key" },
              contentAlignment = Alignment.Center
            ) { Icon(CategoryIcons.get(key), contentDescription = null, tint = if (selected) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant) }
          }
        }
        Text("Colour", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          CategoryPalette.forEach { swatch ->
            val selected = swatch.toArgb() == color
            Box(
              Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(swatch)
                .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                .clickable(role = Role.RadioButton) { color = swatch.toArgb() }
                .semantics { contentDescription = if (selected) "Selected colour" else "Colour" },
              contentAlignment = Alignment.Center
            ) { if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
          }
        }
        if (onDelete != null) {
          TextButton(onClick = onDelete, modifier = Modifier.testTag("goal_delete")) { Text("Delete goal", color = MaterialTheme.colorScheme.error) }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = name.isNotBlank() && targetMinor > 0,
        onClick = {
          onSave(
            (existing ?: GoalEntity(name = "", targetMinor = 0, color = color)).copy(
              name = name.trim(), targetMinor = targetMinor, savedMinor = Money.parseMinor(saved), iconKey = icon, color = color
            )
          )
        },
        modifier = Modifier.testTag("goal_save")
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

/** Put any amount towards a goal, or take some back out. */
@Composable
fun GoalAmountSheet(goal: GoalEntity, onAdd: (Long) -> Unit, onTake: (Long) -> Unit, onDismiss: () -> Unit) {
  val symbol = LocalCurrency.current
  var amount by remember { mutableStateOf("") }
  val minor = Money.parseMinor(amount)
  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text(goal.name) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "${money(goal.savedMinor, whole = true)} saved of ${money(goal.targetMinor, whole = true)}.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
          value = amount, onValueChange = { amount = sanitizeAmount(it) }, label = { Text("Amount ($symbol)") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("goal_amount")
        )
        TextButton(
          enabled = minor > 0 && minor <= goal.savedMinor,
          onClick = { onTake(minor) },
          modifier = Modifier.testTag("goal_amount_take")
        ) { Text("Take out instead") }
      }
    },
    confirmButton = {
      TextButton(enabled = minor > 0, onClick = { onAdd(minor) }, modifier = Modifier.testTag("goal_amount_add")) { Text("Add") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}
