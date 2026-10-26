package com.keepwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.SortOrder
import com.keepwallet.app.ui.TypeFilter
import com.keepwallet.app.ui.components.AppTopBar
import com.keepwallet.app.ui.components.ChevronToggle
import com.keepwallet.app.ui.components.EmptyState
import com.keepwallet.app.ui.components.MonthPickerDialog
import com.keepwallet.app.ui.components.CompactPeriodNav
import com.keepwallet.app.ui.theme.Motion
import com.keepwallet.app.ui.components.ResponsiveRow
import com.keepwallet.app.ui.components.TransactionRow
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra
import java.time.LocalDate

@Composable
fun ActivityScreen(
  viewModel: MainViewModel,
  onEdit: (TransactionEntity) -> Unit,
  modifier: Modifier = Modifier,
  listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  val month by viewModel.month.collectAsStateWithLifecycle()
  val filters by viewModel.filters.collectAsStateWithLifecycle()
  val today by viewModel.today.collectAsStateWithLifecycle()
  var showMonthPicker by remember { mutableStateOf(false) }
  var categoryMenu by remember { mutableStateOf(false) }
  var sortMenu by remember { mutableStateOf(false) }
  var accountMenu by remember { mutableStateOf(false) }
  var collapsedDays by remember { mutableStateOf(setOf<LocalDate>()) }

  if (!data.loaded) return

  // A selected day belongs to one month; moving to another month clears it.
  LaunchedEffect(month) {
    val day = filters.day
    if (day != null && java.time.YearMonth.from(day) != month) viewModel.updateFilters { it.copy(day = null) }
  }

  val categoriesById = remember(data.categories) { data.categories.associateBy { it.id } }
  val accountsById = remember(data.accounts) { data.accounts.associateBy { it.id } }
  val allMonth = remember(data.transactions, month) { Analytics.inSpan(data.transactions, Periods.monthSpan(month)) }
  // A transfer is one line in the list (its outgoing half); either account it touches finds it.
  val transferAccounts = remember(allMonth) {
    allMonth.filter { it.transferId != null }.groupBy({ it.transferId!! }, { it.accountId }).mapValues { it.value.toSet() }
  }
  val monthTransactions = remember(allMonth) { allMonth.filter { it.type != TxType.TRANSFER_IN } }
  val daysWithEntries = remember(monthTransactions) { monthTransactions.map { Periods.millisToDate(it.timestamp) }.toSet() }

  // Everything except the All / Expenses / Income choice, so the tabs can show their own counts.
  val matchesOthers: (TransactionEntity) -> Boolean = { tx ->
    val category = categoriesById[tx.categoryId]
    val query = filters.query.trim()
    val matchesQuery = query.isEmpty() ||
      tx.merchant.contains(query, ignoreCase = true) ||
      (tx.note?.contains(query, ignoreCase = true) == true) ||
      (category?.name?.contains(query, ignoreCase = true) == true)
    val matchesCategory = filters.categoryId == null || tx.categoryId == filters.categoryId
    val matchesAccount = filters.accountId == null || tx.accountId == filters.accountId ||
      (tx.transferId != null && filters.accountId in transferAccounts[tx.transferId].orEmpty())
    val matchesDay = filters.day == null || Periods.millisToDate(tx.timestamp) == filters.day
    matchesQuery && matchesCategory && matchesAccount && matchesDay
  }

  val filtered = remember(monthTransactions, filters, transferAccounts) {
    monthTransactions.filter { tx ->
      val matchesType = when (filters.type) {
        TypeFilter.ALL -> true
        TypeFilter.EXPENSES -> tx.type == TxType.EXPENSE
        TypeFilter.INCOME -> tx.type == TxType.INCOME
      }
      matchesType && matchesOthers(tx)
    }.let { list ->
      when (filters.sort) {
        SortOrder.NEWEST -> list.sortedByDescending { it.timestamp }
        SortOrder.OLDEST -> list.sortedBy { it.timestamp }
        SortOrder.HIGHEST -> list.sortedByDescending { it.amountMinor }
        SortOrder.LOWEST -> list.sortedBy { it.amountMinor }
      }
    }
  }
  val groupByDay = filters.sort == SortOrder.NEWEST || filters.sort == SortOrder.OLDEST
  val groups = remember(filtered, groupByDay) {
    if (groupByDay) filtered.groupBy { Periods.millisToDate(it.timestamp) }.toList() else emptyList()
  }

  Column(modifier.fillMaxSize()) {
    var searchOpen by rememberSaveable { mutableStateOf(filters.query.isNotEmpty()) }
    AppTopBar("Activity", actions = {
      CompactPeriodNav(
        label = Periods.monthLabel(month),
        onPrevious = { viewModel.shiftMonth(-1) },
        onNext = { viewModel.shiftMonth(1) },
        onLabelClick = { showMonthPicker = true },
        previousDescription = "Previous month",
        nextDescription = "Next month",
        modifier = Modifier.testTag("activity_month")
      )
      IconButton(
        onClick = {
          searchOpen = !searchOpen
          if (!searchOpen) viewModel.updateFilters { it.copy(query = "") }
        },
        modifier = Modifier.testTag("activity_search")
      ) {
        Icon(if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search, contentDescription = if (searchOpen) "Close search" else "Search transactions")
      }
    })
    AnimatedVisibility(
      visible = searchOpen,
      enter = expandVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeIn(tween(Motion.Short)),
      exit = shrinkVertically(tween(Motion.Medium, easing = Motion.Standard)) + fadeOut(tween(Motion.Short))
    ) {
      val focus = remember { androidx.compose.ui.focus.FocusRequester() }
      LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
      OutlinedTextField(
        value = filters.query,
        onValueChange = { text -> viewModel.updateFilters { it.copy(query = text) } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).focusRequester(focus).testTag("search_input"),
        placeholder = { Text("Search transactions") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (filters.query.isNotEmpty()) {
            IconButton(onClick = { viewModel.updateFilters { it.copy(query = "") } }) {
              Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
          unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
          unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
          focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
      )
    }

    DayStrip(
      month = month,
      today = today,
      selected = filters.day,
      daysWithEntries = daysWithEntries,
      onSelect = { day -> viewModel.updateFilters { it.copy(day = day) } }
    )

    // All / Expenses / Income as tabs, each with how many entries it holds under the other filters.
    val counts = remember(monthTransactions, filters, transferAccounts) {
      val others = monthTransactions.filter(matchesOthers)
      Triple(others.size, others.count { it.type == TxType.EXPENSE }, others.count { it.type == TxType.INCOME })
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
      listOf(
        Triple(TypeFilter.ALL, "All", counts.first),
        Triple(TypeFilter.EXPENSES, "Expenses", counts.second),
        Triple(TypeFilter.INCOME, "Income", counts.third)
      ).forEach { (type, label, count) ->
        val selected = filters.type == type
        Column(
          Modifier
            .weight(1f)
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .clickable(role = Role.Tab) { viewModel.updateFilters { it.copy(type = type) } }
            .testTag("filter_${type.name}"),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            "$label ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 7.dp)
          )
          Box(
            Modifier
              .fillMaxWidth()
              .height(if (selected) 3.dp else 1.dp)
              .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
          )
        }
      }
    }

    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box {
        FilterChip(
          selected = filters.accountId != null,
          onClick = { accountMenu = true },
          label = { Text(data.accounts.firstOrNull { it.id == filters.accountId }?.name ?: "All accounts") },
          trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
          modifier = Modifier.testTag("filter_account")
        )
        DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
          DropdownMenuItem(text = { Text("All accounts") }, onClick = {
            viewModel.updateFilters { it.copy(accountId = null) }
            accountMenu = false
          })
          data.accounts.forEach { account ->
            DropdownMenuItem(
              text = { Text(account.name) },
              onClick = {
                viewModel.updateFilters { it.copy(accountId = account.id) }
                accountMenu = false
              },
              modifier = Modifier.testTag("filter_account_${account.name}")
            )
          }
        }
      }
      Box {
        FilterChip(
          selected = filters.categoryId != null,
          onClick = { categoryMenu = true },
          label = { Text(categoriesById[filters.categoryId]?.name ?: "All categories") },
          trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
          modifier = Modifier.testTag("filter_category")
        )
        DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
          DropdownMenuItem(text = { Text("All categories") }, onClick = {
            viewModel.updateFilters { it.copy(categoryId = null) }
            categoryMenu = false
          })
          data.categories.forEach { category ->
            DropdownMenuItem(text = { Text(category.name) }, onClick = {
              viewModel.updateFilters { it.copy(categoryId = category.id) }
              categoryMenu = false
            })
          }
        }
      }
      Box {
        FilterChip(
          selected = false,
          onClick = { sortMenu = true },
          label = { Text(if (filters.sort == SortOrder.NEWEST) "Date" else filters.sort.label) },
          trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
          modifier = Modifier.testTag("sort_menu")
        )
        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
          SortOrder.entries.forEach { order ->
            DropdownMenuItem(text = { Text(order.label) }, onClick = {
              viewModel.updateFilters { it.copy(sort = order) }
              sortMenu = false
            })
          }
        }
      }
    }

    if (monthTransactions.isEmpty()) {
      EmptyState(
        icon = Icons.AutoMirrored.Rounded.ReceiptLong,
        title = "No transactions in ${Periods.monthLabel(month)}",
        body = "Entries you add for this month will show up here.",
        actionLabel = "Add transaction",
        onAction = { viewModel.openNewEntry(date = defaultEntryDate(month, today)) },
        modifier = Modifier.padding(top = 24.dp)
      )
    } else if (filtered.isEmpty()) {
      EmptyState(
        icon = Icons.Rounded.SearchOff,
        title = "No matches",
        body = "Nothing fits the current filters.",
        actionLabel = "Clear filters",
        onAction = viewModel::clearFilters,
        modifier = Modifier.padding(top = 24.dp)
      )
    } else {
      val spent = Analytics.total(filtered, TxType.EXPENSE)
      val income = Analytics.total(filtered, TxType.INCOME)
      ResponsiveRow(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        Text(
          "${filtered.size} ${if (filtered.size == 1) "transaction" else "transactions"}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          "Spent ${money(spent, whole = true)} · Income ${money(income, whole = true)}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.testTag("activity_summary")
        )
      }
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().testTag("activity_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)
      ) {
        if (groupByDay) {
          groups.forEach { (date, items) ->
            val collapsed = date in collapsedDays
            item(key = "header_$date") {
              DayHeader(date, today, items, collapsed) { collapsedDays = if (collapsed) collapsedDays - date else collapsedDays + date }
            }
            if (!collapsed) itemsIndexed(items, key = { _, tx -> tx.id }) { _, tx ->
              TransactionRow(
                transaction = tx,
                category = categoriesById[tx.categoryId],
                accountName = accountsById[tx.accountId]?.name,
                showTime = true,
                onClick = { onEdit(tx) },
                modifier = Modifier.animateItem().testTag("tx_${tx.id}")
              )
            }
          }
        } else {
          itemsIndexed(filtered, key = { _, tx -> tx.id }) { _, tx ->
            TransactionRow(
              transaction = tx,
              category = categoriesById[tx.categoryId],
              accountName = accountsById[tx.accountId]?.name,
              subtitle = "${Periods.shortDay(Periods.millisToDate(tx.timestamp), today)} · ${categoriesById[tx.categoryId]?.name.orEmpty()}",
              showTime = true,
              onClick = { onEdit(tx) },
              modifier = Modifier.animateItem().testTag("tx_${tx.id}")
            )
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

/** New entries default to today, or to a sensible day when browsing another month. */
private fun defaultEntryDate(month: java.time.YearMonth, today: LocalDate): LocalDate =
  if (java.time.YearMonth.from(today) == month) today else month.atDay(1)

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, items: List<TransactionEntity>, collapsed: Boolean, onToggle: () -> Unit) {
  val net = items.filter { it.type == TxType.INCOME || it.type == TxType.EXPENSE }.sumOf { if (it.type == TxType.INCOME) it.amountMinor else -it.amountMinor }
  ResponsiveRow(
    Modifier
      .padding(top = 6.dp, bottom = 0.dp)
      .clip(RoundedCornerShape(10.dp))
      .clickable(role = Role.Button, onClickLabel = if (collapsed) "Show day" else "Hide day", onClick = onToggle)
      .padding(vertical = 2.dp)
      .testTag("day_header_$date")
  ) {
    Text(
      Periods.dayHeader(date, today),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        money(net, sign = true),
        style = MaterialTheme.typography.labelLarge,
        color = if (net > 0) MaterialTheme.extra.income else MaterialTheme.colorScheme.onSurfaceVariant
      )
      ChevronToggle(!collapsed, Modifier.size(18.dp))
    }
  }
}

@Composable
private fun DayStrip(
  month: java.time.YearMonth,
  today: LocalDate,
  selected: LocalDate?,
  daysWithEntries: Set<LocalDate>,
  onSelect: (LocalDate?) -> Unit
) {
  val days = remember(month) { (1..month.lengthOfMonth()).map { month.atDay(it) } }
  val listState = rememberLazyListState()
  LaunchedEffect(month, selected) {
    val target = selected ?: today.takeIf { java.time.YearMonth.from(it) == month }
    if (target != null) listState.animateScrollToItem((target.dayOfMonth - 1 + 1 - 2).coerceAtLeast(0))
  }
  LazyRow(
    state = listState,
    modifier = Modifier.fillMaxWidth().testTag("day_strip"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    item(key = "all") {
      StripChip(
        top = "All", bottom = "days", isSelected = selected == null, hasDot = false,
        modifier = Modifier.testTag("day_all"), onClick = { onSelect(null) }
      )
    }
    itemsIndexed(days, key = { _, d -> d.toEpochDay() }) { _, day ->
      StripChip(
        top = Periods.weekdayShort(day).take(3),
        bottom = day.dayOfMonth.toString(),
        isSelected = selected == day,
        isToday = day == today,
        hasDot = day in daysWithEntries,
        modifier = Modifier.testTag("day_${day.dayOfMonth}"),
        onClick = { onSelect(if (selected == day) null else day) }
      )
    }
  }
}

@Composable
private fun StripChip(
  top: String,
  bottom: String,
  isSelected: Boolean,
  hasDot: Boolean,
  modifier: Modifier = Modifier,
  isToday: Boolean = false,
  onClick: () -> Unit
) {
  val container = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
  val content = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
  Column(
    modifier = modifier
      .width(44.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(container)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(vertical = 5.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(top, style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.75f))
    Text(
      bottom,
      style = MaterialTheme.typography.titleSmall,
      fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
      color = content,
      textAlign = TextAlign.Center,
      maxLines = 1
    )
    Spacer(Modifier.height(2.dp))
    Box(
      Modifier
        .size(4.dp)
        .clip(CircleShape)
        .background(if (hasDot) (if (isSelected) content else MaterialTheme.colorScheme.primary) else Color.Transparent)
    )
  }
}

