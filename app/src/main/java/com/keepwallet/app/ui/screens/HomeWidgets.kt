package com.keepwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.ChartRange
import com.keepwallet.app.data.ChartType
import com.keepwallet.app.data.DashboardWidgetEntity
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.DateSpan
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.effectiveMonthlyLimit
import com.keepwallet.app.domain.highlights
import com.keepwallet.app.domain.percentUsed
import com.keepwallet.app.ui.AppData
import com.keepwallet.app.ui.components.AmountText
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.AppProgressBar
import com.keepwallet.app.ui.components.BarChart
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.ChevronToggle
import com.keepwallet.app.ui.components.ColorDot
import com.keepwallet.app.ui.components.DonutChart
import com.keepwallet.app.ui.components.EmptyState
import com.keepwallet.app.ui.components.ExpandArea
import com.keepwallet.app.ui.components.Eyebrow
import com.keepwallet.app.ui.components.LineChart
import com.keepwallet.app.ui.components.PeriodNavigator
import com.keepwallet.app.ui.components.ResponsiveRow
import com.keepwallet.app.ui.components.SectionTitle
import com.keepwallet.app.ui.components.TransactionRow
import com.keepwallet.app.ui.components.heroFontSize
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.components.panel
import com.keepwallet.app.ui.theme.extra
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Everything a dashboard section needs, computed once per recomposition of the Home screen. */
class HomeContext(
  val data: AppData,
  val month: YearMonth,
  val weekStart: LocalDate,
  val today: LocalDate
) {
  val isCurrentMonth: Boolean = month == YearMonth.from(today)
  val monthTransactions: List<TransactionEntity> =
    Analytics.inSpan(data.transactions, Periods.monthSpan(month))
  val weekTransactions: List<TransactionEntity> =
    Analytics.inSpan(data.transactions, Periods.weekSpan(weekStart))
  val expenseCategories: List<CategoryEntity> = data.categories.filter { it.type == TxType.EXPENSE }
  private val categoriesById = data.categories.associateBy { it.id }
  private val accountsById = data.accounts.associateBy { it.id }

  private val monthlyLimits: Map<Long, Long> by lazy {
    expenseCategories.associate { it.id to Analytics.effectiveMonthlyLimit(it, data.transactions, month) }
  }

  /** The category's limit for the month on screen, with anything carried over from earlier months. */
  fun monthlyLimit(category: CategoryEntity): Long = monthlyLimits[category.id] ?: category.monthlyLimitMinor

  /** All monthly limits added up, carry-over included. */
  val monthlyBudget: Long by lazy { monthlyLimits.values.sum() }

  fun category(id: Long): CategoryEntity? = categoriesById[id]
  fun accountName(id: Long): String? = accountsById[id]?.name

  /** "October" for the month on screen; "this month" when it is the current one. */
  val monthWord: String get() = if (isCurrentMonth) "this month" else "in ${Periods.monthName(month)}"
}

fun WidgetTitle(kind: WidgetKind): String = when (kind) {
  WidgetKind.BALANCE_CARD -> "Balance card"
  WidgetKind.MONTH_OVERVIEW -> "Monthly budget"
  WidgetKind.WEEK_OVERVIEW -> "Your week"
  WidgetKind.QUICK_STATS -> "Income & remaining"
  WidgetKind.SPENDING_CHART -> "Spending chart"
  WidgetKind.POCKETS_GRID -> "Your pockets"
  WidgetKind.CATEGORY_BREAKDOWN -> "Category spending"
  WidgetKind.RECENT_ACTIVITY -> "Recent activity"
  WidgetKind.HIGHLIGHTS -> "Highlights"
  WidgetKind.UPCOMING_BILLS -> "Upcoming bills"
  WidgetKind.SAVINGS_GOALS -> "Savings goals"
}

fun WidgetDescription(kind: WidgetKind): String = when (kind) {
  WidgetKind.BALANCE_CARD -> "Your bank balance; tap for every account, hold to change month"
  WidgetKind.MONTH_OVERVIEW -> "Spent this month against your total budget"
  WidgetKind.WEEK_OVERVIEW -> "This week's spending with a daily bar chart"
  WidgetKind.QUICK_STATS -> "Income and what is left to spend"
  WidgetKind.SPENDING_CHART -> "Spending graph for the period you pick"
  WidgetKind.POCKETS_GRID -> "Category pockets with what is left in each"
  WidgetKind.CATEGORY_BREAKDOWN -> "Top categories with relative bars"
  WidgetKind.RECENT_ACTIVITY -> "Your latest transactions"
  WidgetKind.HIGHLIGHTS -> "Daily average, month-end forecast, no-spend days, biggest expense"
  WidgetKind.UPCOMING_BILLS -> "Bills and subscriptions due soon, one tap to mark paid"
  WidgetKind.SAVINGS_GOALS -> "Progress towards the things you're saving for"
}

// ---- Monthly plan -----------------------------------------------------------------------------------

/**
 * The month's plan at a glance: what is left to spend, how much of the budget is used, and the amounts. Tap it for
 * the daily average, the forecast and what you can spend per day. Turns warm when the month is over budget.
 */
@Composable
fun MonthOverviewWidget(ctx: HomeContext, onSetBudget: () -> Unit) {
  val spent = Analytics.total(ctx.monthTransactions, TxType.EXPENSE)
  val budget = ctx.monthlyBudget
  val left = budget - spent
  val over = budget > 0 && left < 0
  val extra = MaterialTheme.extra
  val scheme = MaterialTheme.colorScheme
  var expanded by rememberSaveable { mutableStateOf(false) }
  // A soft wash of the accent rather than its full tint: calm, and still clearly the plan card.
  val container = if (over) extra.warningContainer else androidx.compose.ui.graphics.lerp(scheme.surfaceContainerLow, scheme.primaryContainer, 0.5f)
  val onContainer = if (over) extra.warning else scheme.onPrimaryContainer
  val figures = remember(ctx.data.transactions, ctx.month, ctx.today) { Analytics.highlights(ctx.data.transactions, ctx.month, ctx.today) }
  Surface(
    onClick = { expanded = !expanded },
    modifier = Modifier.fillMaxWidth().testTag("widget_month_overview"),
    shape = RoundedCornerShape(24.dp),
    color = container,
    contentColor = onContainer
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(if (ctx.isCurrentMonth) "Monthly plan" else "Plan · ${Periods.monthName(ctx.month)}", color = onContainer.copy(alpha = 0.75f), modifier = Modifier.weight(1f))
        ChevronToggle(expanded, tint = onContainer.copy(alpha = 0.8f))
      }
      if (budget > 0) {
        val leftText = money(left.coerceAtLeast(0), whole = true)
        ResponsiveRow(verticalAlignment = Alignment.Bottom) {
          Column {
            Text(
              leftText,
              style = MaterialTheme.typography.displaySmall.copy(fontSize = heroFontSize(leftText, 36)),
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.testTag("month_left")
            )
            Text(if (over) "over by ${money(-left, whole = true)}" else "left to spend", style = MaterialTheme.typography.bodyMedium, color = onContainer.copy(alpha = 0.8f))
          }
          Column(horizontalAlignment = Alignment.End) {
            Text("${percentUsed(spent, budget)}%", style = MaterialTheme.typography.titleLarge)
            Text("spent", style = MaterialTheme.typography.bodySmall, color = onContainer.copy(alpha = 0.8f))
          }
        }
        Spacer(Modifier.height(10.dp))
        AppProgressBar(fraction = spent.toFloat() / budget, color = if (over) extra.warning else scheme.primary, track = onContainer.copy(alpha = 0.18f), height = 9.dp)
        Spacer(Modifier.height(6.dp))
        ResponsiveRow {
          Text("${money(spent, whole = true)} spent", style = MaterialTheme.typography.labelMedium, color = onContainer.copy(alpha = 0.85f), modifier = Modifier.testTag("month_spent"))
          Text("${money(budget, whole = true)} planned", style = MaterialTheme.typography.labelMedium, color = onContainer.copy(alpha = 0.85f))
        }
      } else {
        val spentText = money(spent, whole = true)
        Text(
          spentText,
          style = MaterialTheme.typography.displaySmall.copy(fontSize = heroFontSize(spentText, 36)),
          maxLines = 1,
          softWrap = false,
          modifier = Modifier.testTag("month_spent")
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            if (ctx.isCurrentMonth) "spent this month. Set category limits to see what's left." else "spent in ${Periods.monthName(ctx.month)}.",
            style = MaterialTheme.typography.bodySmall,
            color = onContainer.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
          )
          TextButton(onClick = onSetBudget, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = scheme.primary)) { Text("Set budget") }
        }
      }
      ExpandArea(expanded) {
        Column(Modifier.padding(top = 12.dp).testTag("month_details"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          HorizontalDivider(color = onContainer.copy(alpha = 0.2f))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat("Daily average", money(figures.dailyAverageMinor, whole = true), onContainer, Modifier.weight(1f))
            HeroStat(if (ctx.isCurrentMonth) "Forecast" else "Total", money(figures.projectedMinor, whole = true), onContainer, Modifier.weight(1f))
            val daysLeft = ctx.month.lengthOfMonth() - figures.elapsedDays
            HeroStat(
              if (budget > 0 && ctx.isCurrentMonth && daysLeft > 0) "Left per day" else "No-spend days",
              if (budget > 0 && ctx.isCurrentMonth && daysLeft > 0) money((left / daysLeft).coerceAtLeast(0), whole = true) else figures.noSpendDays.toString(),
              onContainer,
              Modifier.weight(1f)
            )
          }
          if (budget > 0 && ctx.isCurrentMonth && figures.projectedMinor > budget) {
            Text(
              "At this pace you'll finish ${money(figures.projectedMinor - budget, whole = true)} over budget.",
              style = MaterialTheme.typography.bodySmall,
              color = onContainer
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HeroStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
  Column(modifier) {
    Eyebrow(label, color = color.copy(alpha = 0.75f))
    Text(value, style = MaterialTheme.typography.titleSmall, color = color, maxLines = 1, softWrap = false)
  }
}

// ---- Your week ------------------------------------------------------------------------------------

@Composable
fun WeekOverviewWidget(
  ctx: HomeContext,
  onPreviousWeek: () -> Unit,
  onNextWeek: () -> Unit,
  onOpenMonthInsights: () -> Unit
) {
  val spent = Analytics.total(ctx.weekTransactions, TxType.EXPENSE)
  val weeklyBudget = Analytics.budgetTotal(ctx.data.categories, weekly = true)
  val left = weeklyBudget - spent
  val extra = MaterialTheme.extra
  val buckets = remember(ctx.data.transactions, ctx.weekStart, ctx.today) {
    Analytics.buckets(ctx.data.transactions, Periods.weekSpan(ctx.weekStart), ChartRange.WEEK, TxType.EXPENSE, ctx.today)
  }
  val monthSpent = Analytics.total(ctx.monthTransactions, TxType.EXPENSE)
  AppCard(modifier = Modifier.fillMaxWidth().testTag("widget_week_overview")) {
    Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 12.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Your week", style = MaterialTheme.typography.titleMedium)
        PeriodNavigator(
          label = Periods.weekLabel(ctx.weekStart),
          onPrevious = onPreviousWeek,
          onNext = onNextWeek,
          previousDescription = "Previous week",
          nextDescription = "Next week"
        )
      }
      Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(money(spent, whole = true), style = MaterialTheme.typography.displaySmall.copy(fontSize = heroFontSize(money(spent, whole = true), 36)), maxLines = 1)
          Text("Spent this week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (weeklyBudget > 0) {
          Box(Modifier.width(1.dp).height(40.dp).background(MaterialTheme.colorScheme.outlineVariant))
          Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
              money(left.coerceAtLeast(0), whole = true),
              style = MaterialTheme.typography.headlineSmall,
              color = if (left >= 0) extra.income else extra.warning
            )
            Text(
              if (left >= 0) "left this week" else "${money(-left, whole = true)} over this week",
              style = MaterialTheme.typography.bodySmall,
              color = if (left >= 0) extra.income else extra.warning
            )
          }
        }
      }
      Spacer(Modifier.height(10.dp))
      BarChart(buckets, modifier = Modifier.padding(end = 10.dp))
      Spacer(Modifier.height(6.dp))
      HorizontalDivider(Modifier.padding(end = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
      Row(
        Modifier
          .fillMaxWidth()
          .padding(end = 4.dp)
          .clip(RoundedCornerShape(12.dp))
          .clickable(role = Role.Button, onClick = onOpenMonthInsights)
          .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          "${Periods.monthName(ctx.month)} · ${money(monthSpent, whole = true)} spent",
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

// ---- Income & remaining ---------------------------------------------------------------------------

@Composable
fun QuickStatsWidget(ctx: HomeContext, onIncome: () -> Unit, onRemaining: () -> Unit) {
  val income = Analytics.total(ctx.monthTransactions, TxType.INCOME)
  val spent = Analytics.total(ctx.monthTransactions, TxType.EXPENSE)
  val budget = ctx.monthlyBudget
  val hasBudget = budget > 0
  val remaining = if (hasBudget) budget - spent else income - spent
  val extra = MaterialTheme.extra
  Row(Modifier.fillMaxWidth().testTag("widget_quick_stats"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    StatTile(
      modifier = Modifier.weight(1f).testTag("stat_income"),
      label = "Income",
      value = money(income, whole = true),
      icon = Icons.Rounded.NorthEast,
      tint = extra.income,
      container = extra.incomeContainer,
      onClick = onIncome
    )
    StatTile(
      modifier = Modifier.weight(1f).testTag("stat_remaining"),
      label = if (hasBudget) "Remaining" else "Net ${ctx.monthWord}",
      value = money(remaining, whole = true),
      icon = Icons.Rounded.AccountBalanceWallet,
      tint = MaterialTheme.colorScheme.secondary,
      container = MaterialTheme.colorScheme.secondaryContainer,
      onClick = onRemaining
    )
  }
}

@Composable
private fun StatTile(
  label: String,
  value: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  tint: Color,
  container: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  AppCard(modifier = modifier, container = container, border = null, onClick = onClick) {
    // Icon beside the text normally; above it when the tile is narrow (small phone or large font).
    val narrow = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.25f
    val badge: @Composable () -> Unit = {
      Box(
        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
      ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp)) }
    }
    val text: @Composable () -> Unit = {
      Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
          value,
          style = MaterialTheme.typography.titleLarge.copy(fontSize = heroFontSize(value, 21)),
          maxLines = 1,
          softWrap = false
        )
      }
    }
    if (narrow) {
      Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { badge(); text() }
    } else {
      Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        badge()
        Spacer(Modifier.width(10.dp))
        text()
      }
    }
  }
}

// ---- Spending chart -------------------------------------------------------------------------------

private val chartRangeLabels = listOf(
  ChartRange.WEEK to "Week", ChartRange.MONTH to "Month", ChartRange.QUARTER to "3 months", ChartRange.YEAR to "Year"
)

/** Days of [span] that have already happened (at least 1), for averages that shouldn't count the future. */
private fun elapsedDays(span: DateSpan, today: LocalDate): Int {
  val last = if (today.isBefore(span.end)) today else span.end
  return (ChronoUnit.DAYS.between(span.start, last) + 1).toInt().coerceIn(1, span.dayCount)
}

/**
 * The home graph. Its look (bars, line or donut) comes from Settings; here you pick the period from a small
 * dropdown. Tap the header to open the details under it: averages, the busiest day and the top categories.
 */
@Composable
fun SpendingChartWidget(
  ctx: HomeContext,
  widget: DashboardWidgetEntity,
  chartType: ChartType,
  onRangeChange: (ChartRange) -> Unit,
  onCategory: (CategoryEntity) -> Unit
) {
  val span = Analytics.rangeSpan(widget.range, ctx.month, ctx.weekStart)
  val inRange = remember(ctx.data.transactions, span) { Analytics.inSpan(ctx.data.transactions, span) }
  val total = Analytics.total(inRange, TxType.EXPENSE)
  val buckets = remember(ctx.data.transactions, span, widget.range, ctx.today) {
    Analytics.buckets(ctx.data.transactions, span, widget.range, TxType.EXPENSE, ctx.today)
  }
  val slices = remember(inRange, ctx.data.categories) { Analytics.byCategory(inRange, ctx.data.categories, TxType.EXPENSE) }
  var menuOpen by remember { mutableStateOf(false) }
  var expanded by rememberSaveable(widget.id) { mutableStateOf(false) }

  AppCard(modifier = Modifier.fillMaxWidth().testTag("widget_spending_chart_${widget.id}")) {
    Column(Modifier.padding(14.dp)) {
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide details" else "Show details") { expanded = !expanded }
          .testTag("chart_expand_${widget.id}"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
      ) {
        Column(Modifier.weight(1f)) {
          Eyebrow("Spending")
          Text(
            money(total, whole = true),
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag("chart_total_${widget.id}")
          )
        }
        Box {
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(14.dp))
              .panel(RoundedCornerShape(14.dp), MaterialTheme.colorScheme.surfaceVariant, 0.5f)
              .clickable(role = Role.DropdownList, onClickLabel = "Change period") { menuOpen = true }
              .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
              .testTag("chart_range_${widget.id}"),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(rangeText(widget.range), style = MaterialTheme.typography.labelLarge)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
          }
          DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            chartRangeLabels.forEach { (range, label) ->
              DropdownMenuItem(
                text = { Text(label) },
                trailingIcon = { if (range == widget.range) Icon(Icons.Rounded.Check, contentDescription = "Selected", modifier = Modifier.size(18.dp)) },
                onClick = {
                  menuOpen = false
                  onRangeChange(range)
                },
                modifier = Modifier.testTag("chart_range_${range.name}_${widget.id}")
              )
            }
          }
        }
        ChevronToggle(expanded, Modifier.padding(start = 4.dp, top = 4.dp))
      }
      Text(
        rangeLabel(widget.range, span, ctx),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(6.dp))
      when (chartType) {
        ChartType.BAR -> BarChart(buckets)
        ChartType.LINE -> LineChart(buckets)
        ChartType.DONUT -> Row(
          Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          DonutChart(slices, total, "Total spent", size = 160.dp, thickness = 22.dp)
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (slices.isEmpty()) {
              Text("No spending in this period", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            slices.take(5).forEach { slice ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(Color(slice.category.color))
                Spacer(Modifier.width(8.dp))
                Text(slice.category.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(slice.percentLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
      ExpandArea(expanded) {
        Column(Modifier.padding(top = 10.dp).testTag("chart_details_${widget.id}"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          val days = elapsedDays(span, ctx.today)
          val busiest = buckets.maxByOrNull { it.valueMinor }?.takeIf { it.valueMinor > 0 }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DetailStat("Daily average", money(total / days, whole = true), Modifier.weight(1f))
            DetailStat("Busiest", busiest?.let { "${it.label} · ${money(it.valueMinor, whole = true)}" } ?: "–", Modifier.weight(1.3f))
            DetailStat("Entries", inRange.count { it.type == TxType.EXPENSE }.toString(), Modifier.weight(0.7f))
          }
          if (slices.isEmpty()) {
            Text("Nothing spent in this period yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          slices.take(3).forEach { slice ->
            Column(
              Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button) { onCategory(slice.category) }
                .padding(vertical = 4.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(slice.category, size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(slice.category.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(money(slice.amountMinor, whole = true), style = MaterialTheme.typography.titleSmall)
                Text("  ${slice.percentLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Spacer(Modifier.height(5.dp))
              AppProgressBar(slice.share, color = Color(slice.category.color), track = Color(slice.category.color).copy(alpha = 0.14f), height = 5.dp)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DetailStat(label: String, value: String, modifier: Modifier = Modifier) {
  Column(modifier) {
    Eyebrow(label)
    Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
  }
}

private fun rangeText(range: ChartRange): String = chartRangeLabels.first { it.first == range }.second

@Composable
private fun chartChipColors() = FilterChipDefaults.filterChipColors(
  selectedContainerColor = MaterialTheme.colorScheme.primary,
  selectedLabelColor = MaterialTheme.colorScheme.onPrimary
)

private fun rangeLabel(range: ChartRange, span: DateSpan, ctx: HomeContext): String = when (range) {
  ChartRange.WEEK -> Periods.weekLabel(span.start)
  ChartRange.MONTH -> Periods.monthLabel(ctx.month)
  ChartRange.QUARTER, ChartRange.YEAR ->
    "${Periods.shortMonthLabel(YearMonth.from(span.start))} ${span.start.year} – ${Periods.shortMonthLabel(ctx.month)} ${ctx.month.year}"
}

// ---- Pockets --------------------------------------------------------------------------------------

/**
 * Category pockets in a two-column grid. Tap one and a details card opens right under its row: what's spent and
 * left, what you can spend per day, and its latest entries.
 */
@Composable
fun PocketsGridWidget(
  ctx: HomeContext,
  onOpenActivity: (CategoryEntity) -> Unit,
  onOpenEntry: (TransactionEntity) -> Unit,
  onMoveMoney: () -> Unit,
  onOpenPlan: () -> Unit
) {
  val pockets = remember(ctx.expenseCategories, ctx.monthTransactions) {
    ctx.expenseCategories
      .filter { ctx.monthlyLimit(it) > 0 || Analytics.spentInCategory(ctx.monthTransactions, it.id) > 0 }
      .sortedWith(
        compareByDescending<CategoryEntity> { Analytics.spentInCategory(ctx.monthTransactions, it.id) }
          .thenBy { it.sortOrder }
      )
  }
  val shown = pockets.take(4)
  val others = pockets.drop(4)
  val extra = MaterialTheme.extra
  var openPocket by rememberSaveable { mutableLongStateOf(-1L) }

  Column(Modifier.fillMaxWidth().testTag("widget_pockets")) {
    SectionTitle(
      "Your pockets",
      actionLabel = if (pockets.size >= 2) "Move money" else null,
      onAction = onMoveMoney
    )
    if (pockets.isEmpty()) {
      AppCard(Modifier.fillMaxWidth()) {
        EmptyState(
          icon = Icons.Rounded.AccountBalanceWallet,
          title = "No pockets yet",
          body = "Give a category a monthly limit and it becomes a pocket that shows what's left.",
          actionLabel = "Set limits",
          onAction = onOpenPlan
        )
      }
      return@Column
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      shown.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          pair.forEach { category ->
            PocketCard(
              category = category,
              spent = Analytics.spentInCategory(ctx.monthTransactions, category.id),
              open = openPocket == category.id,
              limit = ctx.monthlyLimit(category),
              modifier = Modifier.weight(1f),
              onClick = { openPocket = if (openPocket == category.id) -1L else category.id }
            )
          }
          if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
        pair.firstOrNull { it.id == openPocket }?.let { category ->
          PocketDetails(ctx, category, onOpenActivity = { onOpenActivity(category) }, onOpenEntry = onOpenEntry)
        }
      }
      if (others.isNotEmpty()) {
        val leftover = others.sumOf { ctx.monthlyLimit(it) - Analytics.spentInCategory(ctx.monthTransactions, it.id) }
        AppCard(Modifier.fillMaxWidth(), onClick = onOpenPlan) {
          Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Other pockets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
              if (leftover >= 0) "${money(leftover, whole = true)} left" else "${money(-leftover, whole = true)} over",
              style = MaterialTheme.typography.titleSmall,
              color = if (leftover >= 0) MaterialTheme.colorScheme.onSurface else extra.warning
            )
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
internal fun PocketCard(
  category: CategoryEntity,
  spent: Long,
  open: Boolean,
  modifier: Modifier,
  limit: Long = category.monthlyLimitMinor,
  tag: String = "pocket_${category.name}",
  onClick: () -> Unit
) {
  val tint = Color(category.color)
  val over = limit in 1 until spent
  val extra = MaterialTheme.extra
  AppCard(
    modifier = modifier.testTag(tag),
    onClick = onClick,
    container = tint.copy(alpha = if (open) 0.24f else 0.14f),
    border = null
  ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        // The name gets all the room the chevron leaves, so longer names ("Subscriptions") aren't cut short.
        Box(Modifier.weight(1f)) {
          Box(
            Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).padding(horizontal = 8.dp, vertical = 2.dp)
          ) { Text(category.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        ChevronToggle(open, Modifier.size(18.dp))
      }
      CategoryBadge(category, size = 26.dp)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          when {
            limit <= 0 -> money(spent, whole = true)
            over -> "${money(spent - limit, whole = true)} over"
            else -> "${money(limit - spent, whole = true)} left"
          },
          style = MaterialTheme.typography.titleMedium,
          color = if (over) extra.warning else MaterialTheme.colorScheme.onSurface,
          maxLines = 1
        )
        if (over) {
          Spacer(Modifier.width(4.dp))
          Icon(Icons.Rounded.Warning, contentDescription = "Over budget", tint = extra.warning, modifier = Modifier.size(16.dp))
        }
      }
      if (limit > 0) {
        AppProgressBar(
          fraction = spent.toFloat() / limit,
          color = if (over) extra.warning else tint,
          track = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
          height = 5.dp
        )
        Text("${money(spent, whole = true)} of ${money(limit, whole = true)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
      } else {
        Text("spent · no limit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun PocketDetails(
  ctx: HomeContext,
  category: CategoryEntity,
  onOpenActivity: () -> Unit,
  onOpenEntry: (TransactionEntity) -> Unit
) {
  val limit = ctx.monthlyLimit(category)
  val spent = Analytics.spentInCategory(ctx.monthTransactions, category.id)
  val entries = ctx.monthTransactions.filter { it.type == TxType.EXPENSE && it.categoryId == category.id }.take(3)
  val daysLeft = if (ctx.isCurrentMonth) ctx.month.lengthOfMonth() - ctx.today.dayOfMonth + 1 else 0
  val extra = MaterialTheme.extra
  AppCard(Modifier.fillMaxWidth().testTag("pocket_details_${category.name}")) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailStat("Spent", money(spent, whole = true), Modifier.weight(1f))
        DetailStat("Limit", if (limit > 0) money(limit, whole = true) else "None", Modifier.weight(1f))
        DetailStat(
          if (limit > 0 && spent > limit) "Over by" else "Left",
          if (limit > 0) money(kotlin.math.abs(limit - spent), whole = true) else "–",
          Modifier.weight(1f)
        )
      }
      if (limit > 0 && daysLeft > 0) {
        val perDay = (limit - spent) / daysLeft
        Text(
          if (limit > spent) "About ${money(perDay, whole = true)} a day for the next $daysLeft ${if (daysLeft == 1) "day" else "days"}."
          else "Already over the limit with $daysLeft ${if (daysLeft == 1) "day" else "days"} to go.",
          style = MaterialTheme.typography.bodySmall,
          color = if (limit > spent) MaterialTheme.colorScheme.onSurfaceVariant else extra.warning
        )
      }
      if (entries.isEmpty()) {
        Text("No entries in this pocket ${ctx.monthWord}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      entries.forEach { tx ->
        TransactionRow(
          transaction = tx,
          category = category,
          accountName = null,
          subtitle = Periods.shortDay(Periods.millisToDate(tx.timestamp), ctx.today),
          framed = false,
          onClick = { onOpenEntry(tx) }
        )
      }
      OutlinedButton(onClick = onOpenActivity, modifier = Modifier.fillMaxWidth().testTag("pocket_open_${category.name}")) {
        Text("See all in Activity")
      }
    }
  }
}

// ---- Category spending ----------------------------------------------------------------------------

@Composable
fun CategoryBreakdownWidget(
  ctx: HomeContext,
  widget: DashboardWidgetEntity,
  onOpenInsights: () -> Unit,
  onCategoryClick: (CategoryEntity) -> Unit,
  onToggleRange: (ChartRange) -> Unit
) {
  val weekly = widget.range == ChartRange.WEEK
  val transactions = if (weekly) ctx.weekTransactions else ctx.monthTransactions
  val slices = remember(transactions, ctx.data.categories) { Analytics.byCategory(transactions, ctx.data.categories, TxType.EXPENSE) }
  val top = slices.take(5)
  val max = (top.firstOrNull()?.amountMinor ?: 1L).coerceAtLeast(1L)
  val total = Analytics.total(transactions, TxType.EXPENSE)
  AppCard(Modifier.fillMaxWidth().testTag("widget_category_breakdown_${widget.id}")) {
    Column(Modifier.padding(14.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(if (weekly) "This week's spending" else "This month's spending", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(ChartRange.WEEK to "Week", ChartRange.MONTH to "Month").forEach { (range, label) ->
            FilterChip(
              selected = (range == ChartRange.WEEK) == weekly,
              onClick = { onToggleRange(range) },
              label = { Text(label) },
              colors = chartChipColors()
            )
          }
        }
      }
      Spacer(Modifier.height(6.dp))
      if (top.isEmpty()) {
        Text(
          "Nothing spent yet in this ${if (weekly) "week" else "month"}.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 10.dp)
        )
      }
      top.forEach { slice ->
        Column(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onCategoryClick(slice.category) }
            .padding(vertical = 6.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(slice.category, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Text(slice.category.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(money(slice.amountMinor, whole = true), style = MaterialTheme.typography.titleSmall)
          }
          Spacer(Modifier.height(5.dp))
          AppProgressBar(slice.amountMinor.toFloat() / max, color = Color(slice.category.color), height = 5.dp)
        }
      }
      HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
      Row(
        Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .clickable(role = Role.Button, onClick = onOpenInsights)
          .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("${money(total, whole = true)} total", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.ChevronRight, contentDescription = "Open insights", tint = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

// ---- Recent activity ------------------------------------------------------------------------------

@Composable
fun RecentActivityWidget(
  ctx: HomeContext,
  onSeeAll: () -> Unit,
  onAdd: () -> Unit,
  onOpen: (TransactionEntity) -> Unit
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val all = ctx.monthTransactions
  val recent = all.take(if (expanded) 12 else 5)
  AppCard(Modifier.fillMaxWidth().testTag("widget_recent")) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
      SectionTitle(
        if (ctx.isCurrentMonth) "Recent activity" else "Activity · ${Periods.monthLabel(ctx.month)}",
        actionLabel = if (recent.isNotEmpty()) "See all" else null,
        onAction = onSeeAll
      )
      if (recent.isEmpty()) {
        EmptyState(
          icon = Icons.AutoMirrored.Rounded.ReceiptLong,
          title = if (ctx.isCurrentMonth) "No transactions yet" else "Nothing in ${Periods.monthName(ctx.month)}",
          body = if (ctx.isCurrentMonth) "Add your first expense or income to start tracking." else "Entries dated in this month will show up here.",
          actionLabel = "Add transaction",
          onAction = onAdd
        )
      } else {
        recent.forEach { tx ->
          TransactionRow(
            transaction = tx,
            category = ctx.category(tx.categoryId),
            accountName = null,
            subtitle = "${Periods.shortDay(Periods.millisToDate(tx.timestamp), ctx.today)} · ${ctx.category(tx.categoryId)?.name.orEmpty()}",
            framed = false,
            onClick = { onOpen(tx) }
          )
        }
        if (all.size > 5) {
          Row(
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable(role = Role.Button) { expanded = !expanded }
              .padding(vertical = 8.dp)
              .testTag("recent_expand"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(if (expanded) "Show fewer" else "Show ${minOf(12, all.size) - 5} more", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            ChevronToggle(expanded, tint = MaterialTheme.colorScheme.primary)
          }
        }
      }
    }
  }
}
