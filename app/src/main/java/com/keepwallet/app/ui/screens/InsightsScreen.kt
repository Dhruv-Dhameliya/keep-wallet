package com.keepwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.graphics.vector.ImageVector
import com.keepwallet.app.data.ChartRange
import com.keepwallet.app.ui.components.BarChart
import com.keepwallet.app.ui.components.LineChart
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.CategoryTotal
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppTopBar
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.AppProgressBar
import com.keepwallet.app.ui.components.ChevronToggle
import com.keepwallet.app.ui.components.ExpandArea
import com.keepwallet.app.ui.components.TransactionRow
import com.keepwallet.app.ui.components.ViewModeToggle
import androidx.compose.runtime.mutableLongStateOf
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.DonutChart
import com.keepwallet.app.ui.components.MonthPickerDialog
import com.keepwallet.app.ui.components.CompactPeriodNav
import com.keepwallet.app.ui.components.ResponsiveRow
import com.keepwallet.app.ui.components.SegmentedToggle
import com.keepwallet.app.ui.components.TreemapChart
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra

@Composable
fun InsightsScreen(viewModel: MainViewModel, onOpenConverter: () -> Unit = {}, onOpenReview: () -> Unit = {}, modifier: Modifier = Modifier, listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  val month by viewModel.month.collectAsStateWithLifecycle()
  val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
  var typeName by rememberSaveable { mutableStateOf(TxType.EXPENSE.name) }
  var visualName by rememberSaveable { mutableStateOf(InsightsVisual.DONUT.name) }
  val visual = InsightsVisual.valueOf(visualName)
  var weekly by rememberSaveable { mutableStateOf(false) }
  var showAll by rememberSaveable { mutableStateOf(false) }
  var showMonthPicker by remember { mutableStateOf(false) }
  var openId by rememberSaveable { mutableLongStateOf(-1L) }
  val today by viewModel.today.collectAsStateWithLifecycle()
  val gridViews by viewModel.gridViews.collectAsStateWithLifecycle()
  val grid = "insights" in gridViews

  if (!data.loaded) return

  val type = TxType.valueOf(typeName)
  val isSpending = type == TxType.EXPENSE
  val span = if (weekly) Periods.weekSpan(weekStart) else Periods.monthSpan(month)
  val inSpan = remember(data.transactions, span) { Analytics.inSpan(data.transactions, span) }
  val slices = remember(inSpan, data.categories, type) { Analytics.byCategory(inSpan, data.categories, type) }
  val total = slices.sumOf { it.amountMinor }
  val periodWord = if (weekly) "week" else "month"

  Column(Modifier.fillMaxSize()) {
    AppTopBar("Insights", actions = {
      // The period sits in the bar, so the page itself starts straight with the toggles and the chart.
      if (weekly) {
        CompactPeriodNav(
          label = Periods.weekLabel(weekStart),
          onPrevious = { viewModel.shiftWeek(-1) },
          onNext = { viewModel.shiftWeek(1) },
          previousDescription = "Previous week",
          nextDescription = "Next week",
          modifier = Modifier.testTag("insights_period")
        )
      } else {
        CompactPeriodNav(
          label = Periods.monthLabel(month),
          onPrevious = { viewModel.shiftMonth(-1) },
          onNext = { viewModel.shiftMonth(1) },
          onLabelClick = { showMonthPicker = true },
          previousDescription = "Previous month",
          nextDescription = "Next month",
          modifier = Modifier.testTag("insights_period")
        )
      }
      IconButton(onClick = onOpenReview, modifier = Modifier.testTag("open_review")) {
        Icon(Icons.Rounded.Assessment, contentDescription = "Monthly and yearly review")
      }
      IconButton(onClick = onOpenConverter, modifier = Modifier.testTag("open_converter")) {
        Icon(Icons.Rounded.CurrencyExchange, contentDescription = "Currency converter")
      }
    })
LazyColumn(
    state = listState,
    modifier = Modifier.weight(1f).fillMaxWidth().testTag("insights_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    item {
      // Side by side normally; stacked when the screen is narrow or the text is large.
      val roomy = androidx.compose.ui.platform.LocalDensity.current.fontScale <= 1.15f
      val typeToggle: @Composable (Modifier) -> Unit = { m ->
        SegmentedToggle(
          options = listOf(TxType.EXPENSE.name to "Spending", TxType.INCOME.name to "Income"),
          selected = typeName,
          onSelect = { typeName = it },
          modifier = m.testTag("insights_type")
        )
      }
      val periodToggle: @Composable (Modifier) -> Unit = { m ->
        SegmentedToggle(
          options = listOf(true to "Week", false to "Month"),
          selected = weekly,
          onSelect = { weekly = it },
          modifier = m.testTag("insights_period_toggle")
        )
      }
      if (roomy) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          typeToggle(Modifier.weight(1.15f))
          periodToggle(Modifier.weight(1f))
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          typeToggle(Modifier.fillMaxWidth())
          periodToggle(Modifier.fillMaxWidth())
        }
      }
    }
    item {
      var visualMenu by remember { mutableStateOf(false) }
      val buckets = remember(data.transactions, span, weekly, type, today) {
        withDateLabels(Analytics.buckets(data.transactions, span, if (weekly) ChartRange.WEEK else ChartRange.MONTH, type, today), span)
      }
      Box(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
          when (visual) {
            InsightsVisual.DONUT -> DonutChart(
              slices = slices,
              totalMinor = total,
              centerLabel = if (isSpending) "Total spent" else "Total income",
              size = 168.dp,
              thickness = 24.dp,
              modifier = Modifier.testTag("insights_donut")
            )
            InsightsVisual.BARS -> BarChart(buckets, Modifier.padding(top = 30.dp).testTag("insights_bars"), height = 168.dp)
            InsightsVisual.LINE -> LineChart(buckets, Modifier.padding(top = 30.dp).testTag("insights_line"), height = 168.dp)
            InsightsVisual.BLOCKS -> TreemapChart(
              slices,
              Modifier.padding(top = 34.dp).testTag("insights_blocks"),
              height = 190.dp,
              onSliceClick = { viewModel.showCategoryActivity(it.category.id, month) }
            )
          }
        }
        // A small dropdown in the corner: the same numbers drawn another way.
        Box(Modifier.align(Alignment.TopEnd)) {
          Row(
            Modifier
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
              .clickable(role = Role.DropdownList, onClickLabel = "Change how it is drawn") { visualMenu = true }
              .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
              .testTag("insights_visual"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(visual.label, style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
          }
          DropdownMenu(expanded = visualMenu, onDismissRequest = { visualMenu = false }) {
            InsightsVisual.entries.forEach { option ->
              DropdownMenuItem(
                text = { Text(option.label) },
                leadingIcon = { Icon(option.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                trailingIcon = { if (option == visual) Icon(Icons.Rounded.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp)) },
                onClick = {
                  visualName = option.name
                  visualMenu = false
                },
                modifier = Modifier.testTag("insights_visual_${option.name}")
              )
            }
          }
        }
      }
    }

    if (slices.isEmpty()) {
      item {
        AppCard(Modifier.fillMaxWidth()) {
          Text(
            if (isSpending) "Nothing spent this $periodWord yet." else "No income recorded this $periodWord yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
          )
        }
      }
    } else {
      item {
        AppCard(Modifier.fillMaxWidth().testTag("insights_breakdown")) {
          Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
              Text(
                if (isSpending) "${if (weekly) "This week's" else "This month's"} spending" else "Income by source",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
              )
              ViewModeToggle(grid = grid, onChange = { viewModel.setGridView("insights", it) }, modifier = Modifier.testTag("insights_view_toggle"))
            }
            Spacer(Modifier.height(6.dp))
            val previousSpan = if (weekly) Periods.weekSpan(weekStart.minusWeeks(1)) else Periods.monthSpan(month.minusMonths(1))
            val previousByCategory = Analytics.inSpan(data.transactions, previousSpan).filter { it.type == type }.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amountMinor } }
            val detailsFor: @Composable (CategoryTotal) -> Unit = { slice ->
              SliceDetails(
                entries = inSpan.filter { it.type == type && it.categoryId == slice.category.id }.sortedByDescending { it.timestamp }.take(3),
                category = slice.category,
                today = today,
                onOpenEntry = viewModel::openDetails,
                onOpenActivity = { viewModel.showCategoryActivity(slice.category.id, month) },
                amountMinor = slice.amountMinor,
                shareLabel = slice.percentLabel,
                count = inSpan.count { it.type == type && it.categoryId == slice.category.id },
                previousMinor = previousByCategory[slice.category.id] ?: 0L,
                isSpending = isSpending,
                periodWord = periodWord
              )
            }
            if (grid) {
              slices.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  pair.forEach { slice ->
                    BreakdownTile(slice, open = openId == slice.category.id, modifier = Modifier.weight(1f)) {
                      openId = if (openId == slice.category.id) -1L else slice.category.id
                    }
                  }
                  if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                pair.firstOrNull { it.category.id == openId }?.let { slice -> Column(Modifier.testTag("breakdown_details_${slice.category.name}")) { detailsFor(slice) } }
              }
            } else {
              slices.forEachIndexed { index, slice ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                val open = openId == slice.category.id
                BreakdownRow(slice, open = open, onClick = { openId = if (open) -1L else slice.category.id })
                ExpandArea(open) { Column(Modifier.padding(bottom = 6.dp).testTag("breakdown_details_${slice.category.name}")) { detailsFor(slice) } }
              }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("Total", style = MaterialTheme.typography.titleSmall)
              Text(money(total, whole = true), style = MaterialTheme.typography.titleSmall)
            }
          }
        }
      }
      item {
        if (visual != InsightsVisual.BLOCKS) AppCard(Modifier.fillMaxWidth().testTag("insights_treemap")) {
          Column(Modifier.padding(16.dp)) {
            Text(if (isSpending) "Spending by pocket" else "Income by source", style = MaterialTheme.typography.titleMedium)
            Text(
              money(total, whole = true) + (if (isSpending) " total spending" else " total income"),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            TreemapChart(slices, onSliceClick = { viewModel.showCategoryActivity(it.category.id, month) })
          }
        }
      }
      item {
        val ranked = if (showAll) slices else slices.take(3)
        AppCard(Modifier.fillMaxWidth().testTag("insights_top")) {
          Column(Modifier.padding(16.dp)) {
            Text("Top categories", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            ranked.forEachIndexed { index, slice ->
              Row(
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .clickable(role = Role.Button) { viewModel.showCategoryActivity(slice.category.id, month) }
                  .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  "${index + 1}",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.width(22.dp)
                )
                CategoryBadge(slice.category, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(slice.category.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(money(slice.amountMinor, whole = true), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(12.dp))
                Text(
                  slice.percentLabel,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.width(36.dp)
                )
              }
            }
            if (slices.size > 3) {
              OutlinedButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("see_all_categories")
              ) {
                Text(if (showAll) "Show fewer" else "See all categories")
                Spacer(Modifier.width(4.dp))
                Icon(if (showAll) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
              }
            }
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
}

/** What sits under an opened category: its latest entries, and a way into Activity filtered to it. */
@Composable
private fun SliceDetails(
  entries: List<TransactionEntity>,
  category: com.keepwallet.app.data.CategoryEntity,
  today: java.time.LocalDate,
  onOpenEntry: (TransactionEntity) -> Unit,
  onOpenActivity: () -> Unit,
  amountMinor: Long,
  shareLabel: String,
  count: Int,
  previousMinor: Long,
  isSpending: Boolean,
  periodWord: String
) {
  val extra = MaterialTheme.extra
  Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // Facts about this category: how it moved since the last period, its share, and how many entries.
    Column(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .testTag("breakdown_facts_${category.name}"),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      val change = if (previousMinor > 0) Math.round((amountMinor - previousMinor) * 100.0 / previousMinor).toInt() else null
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Compared to last $periodWord", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
          when {
            change == null -> "–"
            change == 0 -> "Same"
            else -> "${if (change > 0) "+" else "-"}${kotlin.math.abs(change)}%"
          },
          style = MaterialTheme.typography.labelLarge,
          color = when {
            change == null || change == 0 -> MaterialTheme.colorScheme.onSurface
            (change > 0) == isSpending -> extra.warning
            else -> extra.income
          },
          modifier = Modifier.testTag("breakdown_change_${category.name}")
        )
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (isSpending) "Share of total spending" else "Share of total income", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(shareLabel, style = MaterialTheme.typography.labelLarge)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString(), style = MaterialTheme.typography.labelLarge)
      }
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
    OutlinedButton(onClick = onOpenActivity, modifier = Modifier.fillMaxWidth().testTag("breakdown_open_${category.name}")) { Text("See all in Activity") }
  }
}

@Composable
private fun BreakdownTile(slice: CategoryTotal, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val color = Color(slice.category.color)
  AppCard(modifier.testTag("breakdown_row_${slice.category.name}"), onClick = onClick, container = color.copy(alpha = if (open) 0.24f else 0.13f), border = null) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(slice.category, size = 28.dp)
        Spacer(Modifier.weight(1f))
        ChevronToggle(open, Modifier.size(18.dp))
      }
      Text(slice.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(money(slice.amountMinor, whole = true), style = MaterialTheme.typography.titleMedium, maxLines = 1)
      AppProgressBar(slice.share, color = color, track = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), height = 5.dp)
      Text(slice.percentLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun BreakdownRow(slice: CategoryTotal, open: Boolean, onClick: () -> Unit) {
  val color = Color(slice.category.color)
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 12.dp)
      .testTag("breakdown_row_${slice.category.name}")
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(12.dp).clip(CircleShape).background(color))
      Spacer(Modifier.width(10.dp))
      CategoryBadge(slice.category, size = 32.dp)
      Spacer(Modifier.width(10.dp))
      Text(slice.category.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(money(slice.amountMinor, whole = true), style = MaterialTheme.typography.titleSmall)
      Spacer(Modifier.width(10.dp))
      Text(
        slice.percentLabel,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(36.dp)
      )
      ChevronToggle(open, Modifier.size(20.dp))
    }
    Spacer(Modifier.height(5.dp))
    AppProgressBar(slice.share, color = color, track = color.copy(alpha = 0.14f), height = 5.dp)
  }
}

/** The ways the numbers at the top of Insights can be drawn; picked from the small dropdown in the chart's corner. */
private enum class InsightsVisual(val label: String, val icon: ImageVector) {
  DONUT("Donut", Icons.Rounded.PieChart),
  BARS("Bars", Icons.Rounded.BarChart),
  LINE("Line", Icons.Rounded.ShowChart),
  BLOCKS("Blocks", Icons.Rounded.GridView)
}
