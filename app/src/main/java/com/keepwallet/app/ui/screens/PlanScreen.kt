package com.keepwallet.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.CategoryGroup
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.budgetTotalFor
import com.keepwallet.app.domain.limitFor
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.percentUsed
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.PlanSection
import com.keepwallet.app.ui.components.AppTopBar
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.AppProgressBar
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.ChevronToggle
import com.keepwallet.app.ui.components.EmptyState
import com.keepwallet.app.ui.components.ExpandArea
import com.keepwallet.app.ui.components.MonthPickerDialog
import com.keepwallet.app.ui.components.PeriodNavigator
import com.keepwallet.app.ui.components.ResponsiveRow
import com.keepwallet.app.ui.components.SegmentedToggle
import com.keepwallet.app.ui.components.TransactionRow
import com.keepwallet.app.ui.components.ViewModeToggle
import com.keepwallet.app.ui.components.heroFontSize
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun PlanScreen(
  viewModel: MainViewModel,
  onManageCategories: () -> Unit,
  modifier: Modifier = Modifier,
  listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  val month by viewModel.month.collectAsStateWithLifecycle()
  val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
  val today by viewModel.today.collectAsStateWithLifecycle()
  val section by viewModel.planSection.collectAsStateWithLifecycle()
  val gridViews by viewModel.gridViews.collectAsStateWithLifecycle()
  var weekly by rememberSaveable { mutableStateOf(false) }
  var editing by remember { mutableStateOf<CategoryEntity?>(null) }
  var showMonthPicker by remember { mutableStateOf(false) }
  val dialogs = remember { PlanDialogs() }

  if (!data.loaded) return

  val screenKey = if (section == PlanSection.GOALS) "goals" else "plan"
  val grid = screenKey in gridViews

  Column(Modifier.fillMaxSize()) {
    AppTopBar("Plan", actions = {
      if (section != PlanSection.BILLS) {
        ViewModeToggle(grid = grid, onChange = { viewModel.setGridView(screenKey, it) }, modifier = Modifier.padding(end = 8.dp).testTag("plan_view_toggle"))
      }
    })
LazyColumn(
    state = listState,
    modifier = Modifier.weight(1f).fillMaxWidth().testTag("plan_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    item(key = "sections") {
      SegmentedToggle(
        options = PlanSection.entries.map { it to it.label },
        selected = section,
        onSelect = viewModel::setPlanSection,
        modifier = Modifier.fillMaxWidth().testTag("plan_section_toggle")
      )
    }
    when (section) {
      PlanSection.BUDGETS -> budgetItems(
        viewModel, data.categories, data.transactions, month, weekStart, today, weekly, grid,
        onWeekly = { weekly = it },
        onEdit = { editing = it },
        onPickMonth = { showMonthPicker = true },
        onManageCategories = onManageCategories,
        onOpenEntry = viewModel::openDetails
      )
      PlanSection.GOALS -> goalItems(viewModel, data.goals, grid, dialogs)
      PlanSection.BILLS -> billItems(viewModel, data, today, dialogs)
    }
  }
}

  editing?.let { category ->
    LimitsSheet(
      category = category,
      onDismiss = { editing = null },
      onSave = { monthly, weeklyLimit, rollover ->
        viewModel.setLimits(category.id, monthly, weeklyLimit, rollover)
        editing = null
      }
    )
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

  if (dialogs.goalNew || dialogs.goalEditing != null) {
    val goal = dialogs.goalEditing
    GoalSheet(
      existing = goal,
      onSave = {
        viewModel.saveGoal(it)
        dialogs.goalNew = false
        dialogs.goalEditing = null
      },
      onDelete = goal?.let { g ->
        {
          viewModel.deleteGoal(g)
          dialogs.goalEditing = null
        }
      },
      onDismiss = {
        dialogs.goalNew = false
        dialogs.goalEditing = null
      }
    )
  }
  dialogs.goalAmount?.let { goal ->
    GoalAmountSheet(
      goal = goal,
      onAdd = {
        viewModel.addToGoal(goal, it)
        dialogs.goalAmount = null
      },
      onTake = {
        viewModel.addToGoal(goal, -it)
        dialogs.goalAmount = null
      },
      onDismiss = { dialogs.goalAmount = null }
    )
  }
  if (dialogs.billNew || dialogs.billEditing != null) {
    val bill = dialogs.billEditing
    BillSheet(
      existing = bill,
      data = data,
      today = today,
      onSave = {
        viewModel.saveRecurring(it)
        dialogs.billNew = false
        dialogs.billEditing = null
      },
      onDelete = bill?.let { b ->
        {
          viewModel.deleteRecurring(b)
          dialogs.billEditing = null
        }
      },
      onDismiss = {
        dialogs.billNew = false
        dialogs.billEditing = null
      }
    )
  }
}

// ---- Budgets --------------------------------------------------------------------------------------

private fun LazyListScope.budgetItems(
  viewModel: MainViewModel,
  categories: List<CategoryEntity>,
  transactions: List<TransactionEntity>,
  month: YearMonth,
  weekStart: LocalDate,
  today: LocalDate,
  weekly: Boolean,
  grid: Boolean,
  onWeekly: (Boolean) -> Unit,
  onEdit: (CategoryEntity) -> Unit,
  onPickMonth: () -> Unit,
  onManageCategories: () -> Unit,
  onOpenEntry: (TransactionEntity) -> Unit
) {
  val expense = categories.filter { it.type == TxType.EXPENSE }
  val span = if (weekly) Periods.weekSpan(weekStart) else Periods.monthSpan(month)
  val inSpan = Analytics.inSpan(transactions, span)
  val limitOf: (CategoryEntity) -> Long = { Analytics.limitFor(it, weekly, transactions, month) }
  val planned = Analytics.budgetTotalFor(expense, weekly, transactions, month)
  val spent = Analytics.total(inSpan, TxType.EXPENSE)
  val left = planned - spent
  val daysLeft = if (span.start <= today && today <= span.end) (span.end.toEpochDay() - today.toEpochDay()).toInt() + 1 else 0

  item(key = "period") {
    // Period and Week/Month switch share a line (and wrap on narrow screens or big text).
    ResponsiveRow {
      if (weekly) {
        PeriodNavigator(
          label = Periods.weekLabel(weekStart),
          onPrevious = { viewModel.shiftWeek(-1) },
          onNext = { viewModel.shiftWeek(1) },
          previousDescription = "Previous week",
          nextDescription = "Next week"
        )
      } else {
        PeriodNavigator(
          label = Periods.monthLabel(month),
          onPrevious = { viewModel.shiftMonth(-1) },
          onNext = { viewModel.shiftMonth(1) },
          onLabelClick = onPickMonth,
          previousDescription = "Previous month",
          nextDescription = "Next month"
        )
      }
      SegmentedToggle(
        options = listOf(true to "Week", false to "Month"),
        selected = weekly,
        onSelect = onWeekly,
        modifier = Modifier.width(170.dp).testTag("plan_period_toggle")
      )
    }
  }

  if (expense.isEmpty()) {
    item {
      AppCard(Modifier.fillMaxWidth()) {
        EmptyState(
          icon = Icons.Rounded.Tune,
          title = "No spending categories",
          body = "Add a category to start planning your budget.",
          actionLabel = "Manage categories",
          onAction = onManageCategories
        )
      }
    }
    return
  }

  item(key = "hero") {
    val extra = MaterialTheme.extra
    AppCard(Modifier.fillMaxWidth().testTag("plan_hero")) {
      Column(Modifier.padding(16.dp)) {
        ResponsiveRow(verticalAlignment = Alignment.Top) {
          Column {
            Text(
              if (planned > 0) money(left.coerceAtLeast(0), whole = true) else "–",
              style = MaterialTheme.typography.displaySmall.copy(fontSize = heroFontSize(money(left.coerceAtLeast(0), whole = true), 38)),
              maxLines = 1,
              color = if (left < 0) extra.warning else MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.testTag("plan_left")
            )
            Text(
              when {
                planned <= 0 -> "no limits set yet"
                left < 0 -> "over by ${money(-left, whole = true)}"
                else -> "left to spend"
              },
              style = MaterialTheme.typography.bodyMedium,
              color = if (left < 0) extra.warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Column(horizontalAlignment = Alignment.End) {
            Text(money(planned, whole = true), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false, modifier = Modifier.testTag("plan_planned"))
            Text("planned", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        if (planned > 0) {
          Spacer(Modifier.height(12.dp))
          AppProgressBar(
            fraction = spent.toFloat() / planned,
            color = if (left < 0) extra.warning else MaterialTheme.colorScheme.primary,
            height = 9.dp
          )
          Spacer(Modifier.height(5.dp))
          val spentPercent = percentUsed(spent, planned)
          ResponsiveRow {
            Text("$spentPercent% spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${money(spent, whole = true)} of ${money(planned, whole = true)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
          onClick = { viewModel.openMoveMoney(weekly) },
          modifier = Modifier.fillMaxWidth().testTag("move_money"),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Rounded.SwapHoriz, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Move money")
        }
      }
    }
  }

  if (weekly) {
    item(key = "month_summary") {
      // Rhythm board: the monthly budget stays visible under the weekly plan.
      val monthSpent = Analytics.total(Analytics.inSpan(transactions, Periods.monthSpan(month)), TxType.EXPENSE)
      val monthPlanned = Analytics.budgetTotalFor(expense, false, transactions, month)
      AppCard(Modifier.fillMaxWidth().testTag("plan_month_summary")) {
        Column(Modifier.padding(14.dp)) {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Monthly budget", style = MaterialTheme.typography.titleSmall)
            Text(money(monthPlanned, whole = true), style = MaterialTheme.typography.titleSmall)
          }
          if (monthPlanned > 0) {
            Spacer(Modifier.height(8.dp))
            AppProgressBar(monthSpent.toFloat() / monthPlanned, height = 8.dp)
            Spacer(Modifier.height(6.dp))
            Text(
              "${money(monthSpent, whole = true)} spent · ${money((monthPlanned - monthSpent).coerceAtLeast(0), whole = true)} remaining",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }

  CategoryGroup.entries.forEach { group ->
    val inGroup = expense.filter { it.group == group }
    if (inGroup.isEmpty()) return@forEach
    item(key = "group_${group.name}") {
      Text(
        if (group == CategoryGroup.ESSENTIAL) "Essentials" else "Flexible",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
      )
    }
    if (grid) {
      val rows = inGroup.chunked(2)
      items(rows.size, key = { "grid_${group.name}_${rows[it].first().id}" }) { index ->
        BudgetGridRow(rows[index], inSpan, weekly, daysLeft, today, onEdit, onOpenEntry, viewModel, limitOf)
      }
    } else {
      // One card per group with a row per budget, like a table: icon, name, spent of limit, what is left.
      item(key = "group_card_${group.name}") {
        AppCard(Modifier.fillMaxWidth().testTag("budget_group_${group.name}")) {
          inGroup.forEachIndexed { index, category ->
            if (index > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            BudgetRow(
              category = category,
              spent = Analytics.spentInCategory(inSpan, category.id),
              weekly = weekly,
              entries = latestEntries(inSpan, category),
              daysLeft = daysLeft,
              today = today,
              onEdit = { onEdit(category) },
              onOpenActivity = { viewModel.showCategoryActivity(category.id, month) },
              onOpenEntry = onOpenEntry,
              limitOf = limitOf
            )
          }
        }
      }
    }
  }

  item(key = "edit_limits") {
    AppCard(Modifier.fillMaxWidth(), onClick = onManageCategories) {
      Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text("Edit limits", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

private fun latestEntries(inSpan: List<TransactionEntity>, category: CategoryEntity): List<TransactionEntity> =
  inSpan.filter { it.type == TxType.EXPENSE && it.categoryId == category.id }.sortedByDescending { it.timestamp }.take(3)

@Composable
private fun BudgetGridRow(
  row: List<CategoryEntity>,
  inSpan: List<TransactionEntity>,
  weekly: Boolean,
  daysLeft: Int,
  today: LocalDate,
  onEdit: (CategoryEntity) -> Unit,
  onOpenEntry: (TransactionEntity) -> Unit,
  viewModel: MainViewModel,
  limitOf: (CategoryEntity) -> Long
) {
  var open by rememberSaveable { mutableLongStateOf(-1L) }
  val month by viewModel.month.collectAsStateWithLifecycle()
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      row.forEach { category ->
        PocketCard(
          category = category,
          spent = Analytics.spentInCategory(inSpan, category.id),
          open = open == category.id,
          modifier = Modifier.weight(1f),
          limit = limitOf(category),
          tag = "budget_row_${category.name}",
          onClick = { open = if (open == category.id) -1L else category.id }
        )
      }
      if (row.size == 1) Spacer(Modifier.weight(1f))
    }
    row.firstOrNull { it.id == open }?.let { category ->
      AppCard(Modifier.fillMaxWidth().testTag("budget_details_${category.name}")) {
        Column(Modifier.padding(14.dp)) {
          BudgetDetails(
            category, Analytics.spentInCategory(inSpan, category.id), weekly, latestEntries(inSpan, category), daysLeft, today,
            onEdit = { onEdit(category) },
            onOpenActivity = { viewModel.showCategoryActivity(category.id, month) },
            onOpenEntry = onOpenEntry,
            limitOf = limitOf
          )
        }
      }
    }
  }
}

/** One budget as a card; tap it to open its details (what's left per day, the latest entries, edit and activity links). */
@Composable
private fun BudgetRow(
  category: CategoryEntity,
  spent: Long,
  weekly: Boolean,
  entries: List<TransactionEntity>,
  daysLeft: Int,
  today: LocalDate,
  onEdit: () -> Unit,
  onOpenActivity: () -> Unit,
  onOpenEntry: (TransactionEntity) -> Unit,
  limitOf: (CategoryEntity) -> Long
) {
  val limit = limitOf(category)
  val over = limit > 0 && spent > limit
  val extra = MaterialTheme.extra
  var open by rememberSaveable(category.id) { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.testTag("budget_row_${category.name}")) {
    Column(Modifier.padding(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(category, size = 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(category.name, style = MaterialTheme.typography.titleSmall)
          Text(
            if (limit > 0) "${money(spent, whole = true)} of ${money(limit, whole = true)}" else "${money(spent, whole = true)} spent · no limit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        if (limit > 0) {
          Text(
            if (over) "${money(spent - limit, whole = true)} over" else "${money(limit - spent, whole = true)} left",
            style = MaterialTheme.typography.titleSmall,
            color = when {
              over -> extra.warning
              spent >= limit -> MaterialTheme.colorScheme.onSurfaceVariant
              else -> extra.income
            }
          )
        }
        ChevronToggle(open, Modifier.padding(start = 4.dp))
      }
      if (limit > 0) {
        Spacer(Modifier.height(8.dp))
        AppProgressBar(
          spent.toFloat() / limit,
          color = if (over) extra.warning else Color(category.color),
          height = 5.dp
        )
      }
      ExpandArea(open) {
        Column(Modifier.padding(top = 10.dp).testTag("budget_details_${category.name}")) {
          BudgetDetails(category, spent, weekly, entries, daysLeft, today, onEdit, onOpenActivity, onOpenEntry, limitOf)
        }
      }
    }
  }
}

@Composable
private fun BudgetDetails(
  category: CategoryEntity,
  spent: Long,
  weekly: Boolean,
  entries: List<TransactionEntity>,
  daysLeft: Int,
  today: LocalDate,
  onEdit: () -> Unit,
  onOpenActivity: () -> Unit,
  onOpenEntry: (TransactionEntity) -> Unit,
  limitOf: (CategoryEntity) -> Long
) {
  val limit = limitOf(category)
  val extra = MaterialTheme.extra
  val carry = if (weekly) 0L else limit - category.monthlyLimitMinor
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (carry > 0) {
      Text(
        "Includes ${money(carry, whole = true)} carried over from earlier months.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag("rollover_note_${category.name}")
      )
    }
    if (limit > 0 && daysLeft > 0) {
      Text(
        if (limit > spent) "About ${money((limit - spent) / daysLeft, whole = true)} a day for the next $daysLeft ${if (daysLeft == 1) "day" else "days"}."
        else "Over the limit with $daysLeft ${if (daysLeft == 1) "day" else "days"} to go.",
        style = MaterialTheme.typography.bodySmall,
        color = if (limit > spent) MaterialTheme.colorScheme.onSurfaceVariant else extra.warning
      )
    } else if (limit <= 0) {
      Text(
        "No ${if (weekly) "weekly" else "monthly"} limit yet. Set one to see what's left.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    if (entries.isEmpty()) {
      Text("No entries in this ${if (weekly) "week" else "month"}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    entries.forEach { tx ->
      TransactionRow(
        transaction = tx,
        category = category,
        accountName = null,
        subtitle = Periods.shortDay(Periods.millisToDate(tx.timestamp), today),
        framed = false,
        onClick = { onOpenEntry(tx) }
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).testTag("budget_edit_${category.name}")) { Text("Edit limits") }
      OutlinedButton(onClick = onOpenActivity, modifier = Modifier.weight(1f).testTag("budget_open_${category.name}")) { Text("Activity") }
    }
  }
}
