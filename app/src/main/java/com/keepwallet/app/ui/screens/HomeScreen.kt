package com.keepwallet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import com.keepwallet.app.domain.Periods
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.PlanSection
import com.keepwallet.app.ui.Tab
import com.keepwallet.app.ui.TypeFilter
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.DetectedEntryCard
import com.keepwallet.app.ui.components.KeepWalletMark
import com.keepwallet.app.ui.components.MonthPickerDialog

/**
 * The home screen: only the sections you've switched on, in the order you chose. It follows the month chosen on
 * the balance card (hold the card, or tap its month), so every section shows that month.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  viewModel: MainViewModel,
  onOpenSettings: () -> Unit,
  onOpenBalance: () -> Unit,
  onOpenCustomize: () -> Unit,
  onOpenAlerts: () -> Unit = {},
  onOpen: (TransactionEntity) -> Unit,
  modifier: Modifier = Modifier,
  listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  val month by viewModel.month.collectAsStateWithLifecycle()
  val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
  val today by viewModel.today.collectAsStateWithLifecycle()
  val chartType by viewModel.chartType.collectAsStateWithLifecycle()
  val showTotal by viewModel.showTotalOnHero.collectAsStateWithLifecycle()
  val heroAccounts by viewModel.heroAccounts.collectAsStateWithLifecycle()
  val hidden by viewModel.hideBalance.collectAsStateWithLifecycle()
  var showMonthPicker by remember { mutableStateOf(false) }

  if (!data.loaded) return

  val ctx = remember(data, month, weekStart, today) { HomeContext(data, month, weekStart, today) }
  val widgets = remember(data.widgets) { data.widgets.filter { it.enabled }.sortedBy { it.position } }

  // A newly detected payment appears above the first section; a list that was resting at the top would keep its
  // place and leave the card just out of sight, so bring it into view.
  val newestDetected = data.detected.firstOrNull()?.id
  LaunchedEffect(newestDetected) {
    if (newestDetected != null && listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
  }

  Column(Modifier.fillMaxSize()) {
    // Month on the left as plain text (tap to pick), customize and settings on the right.
    TopAppBar(
      title = {
        Row(
          Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = "Choose month") { showMonthPicker = true }
            .padding(vertical = 8.dp)
            .testTag("hero_month"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(Periods.monthLabel(month), maxLines = 1, modifier = Modifier.testTag("home_title"))
          Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(24.dp))
        }
      },
      actions = {
        val alertCount by viewModel.unseenAlerts.collectAsStateWithLifecycle()
        IconButton(onClick = onOpenAlerts, modifier = Modifier.testTag("open_alerts")) {
          BadgedBox(badge = {
            if (alertCount.isNotEmpty()) Badge(modifier = Modifier.testTag("alerts_badge")) { Text(alertCount.size.coerceAtMost(9).toString()) }
          }) {
            Icon(Icons.Rounded.Notifications, contentDescription = if (alertCount.isEmpty()) "Notifications" else "Notifications, ${alertCount.size} new")
          }
        }
        IconButton(onClick = onOpenCustomize, modifier = Modifier.testTag("customize_home")) {
          Icon(Icons.Rounded.Tune, contentDescription = "Customize home")
        }
        IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
          Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
      },
      windowInsets = WindowInsets(0),
      colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
LazyColumn(
    state = listState,
    modifier = Modifier.weight(1f).fillMaxWidth().testTag("home_list"),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    if (data.detected.isNotEmpty()) {
      item(key = "detected") {
        val latest = data.detected.first()
        DetectedEntryCard(
          entry = latest,
          moreCount = data.detected.size - 1,
          onAdd = { viewModel.reviewDetected(latest.id) },
          onDismiss = { viewModel.discardDetected(latest.id) }
        )
      }
    }

    items(widgets, key = { it.id }) { widget ->
      Box {
      when (widget.kind) {
        WidgetKind.BALANCE_CARD -> BalanceCardWidget(
          ctx,
          showTotal = showTotal,
          heroAccounts = heroAccounts,
          hidden = hidden,
          onClick = onOpenBalance,
          onPickMonth = { showMonthPicker = true },
          onToggleHidden = { viewModel.toggleBalanceVisibility() },
          onBackToNow = viewModel::backToThisMonth
        )
        WidgetKind.MONTH_OVERVIEW -> MonthOverviewWidget(ctx, onSetBudget = { viewModel.requestTab(Tab.PLAN) })
        WidgetKind.WEEK_OVERVIEW -> WeekOverviewWidget(
          ctx,
          onPreviousWeek = { viewModel.shiftWeek(-1) },
          onNextWeek = { viewModel.shiftWeek(1) },
          onOpenMonthInsights = { viewModel.requestTab(Tab.INSIGHTS) }
        )
        WidgetKind.QUICK_STATS -> QuickStatsWidget(
          ctx,
          onIncome = {
            viewModel.updateFilters { it.copy(type = TypeFilter.INCOME, categoryId = null, day = null, query = "") }
            viewModel.requestTab(Tab.ACTIVITY)
          },
          onRemaining = { viewModel.openPlan(PlanSection.BUDGETS) }
        )
        WidgetKind.SPENDING_CHART -> SpendingChartWidget(
          ctx, widget, chartType,
          onRangeChange = { range -> viewModel.setWidgetChart(widget.id, range = range) },
          onCategory = { viewModel.showCategoryActivity(it.id, month) }
        )
        WidgetKind.POCKETS_GRID -> PocketsGridWidget(
          ctx,
          onOpenActivity = { viewModel.showCategoryActivity(it.id, month) },
          onOpenEntry = onOpen,
          onMoveMoney = { viewModel.openMoveMoney(weekly = false) },
          onOpenPlan = { viewModel.openPlan(PlanSection.BUDGETS) }
        )
        WidgetKind.CATEGORY_BREAKDOWN -> CategoryBreakdownWidget(
          ctx, widget,
          onOpenInsights = { viewModel.requestTab(Tab.INSIGHTS) },
          onCategoryClick = { viewModel.showCategoryActivity(it.id, month) },
          onToggleRange = { range -> viewModel.setWidgetChart(widget.id, range = range) }
        )
        WidgetKind.RECENT_ACTIVITY -> RecentActivityWidget(
          ctx,
          onSeeAll = { viewModel.requestTab(Tab.ACTIVITY) },
          onAdd = { viewModel.openNewEntry() },
          onOpen = onOpen
        )
        WidgetKind.HIGHLIGHTS -> HighlightsWidget(ctx, onOpenEntry = onOpen)
        WidgetKind.UPCOMING_BILLS -> UpcomingBillsWidget(
          ctx,
          onPay = viewModel::payRecurring,
          onOpenBills = { viewModel.openPlan(PlanSection.BILLS) }
        )
        WidgetKind.SAVINGS_GOALS -> SavingsGoalsWidget(ctx, onOpenGoals = { viewModel.openPlan(PlanSection.GOALS) })
      }
      }
    }

    if (widgets.isEmpty()) {
      item(key = "no_widgets") {
        AppCard(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your home screen is empty", style = MaterialTheme.typography.titleMedium)
            Text(
              "Choose which sections to show and in what order.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onOpenCustomize) { Text("Customize home") }
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
