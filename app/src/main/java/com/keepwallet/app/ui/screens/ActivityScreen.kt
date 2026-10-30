package com.keepwallet.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.keepwallet.app.ui.components.CompactPeriodNav
import com.keepwallet.app.ui.components.EmptyState
import com.keepwallet.app.ui.components.MonthPickerDialog
import com.keepwallet.app.ui.components.ResponsiveRow
import com.keepwallet.app.ui.components.TransactionRow
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.Motion
import com.keepwallet.app.ui.theme.extra
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ActivityScreen(
  viewModel: MainViewModel,
  onEdit: (TransactionEntity) -> Unit,
  modifier: Modifier = Modifier,
  listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState()
) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  val month by viewModel.month.collectAsStateWithLifecycle()
  val filters by viewModel.filters.collectAsStateWithLifecycle()
  val today by viewModel.today.collectAsStateWithLifecycle()
  var showMonthPicker by remember { mutableStateOf(false) }
  var categoryMenu by remember { mutableStateOf(false) }
  var sortMenu by remember { mutableStateOf(false) }
  var accountMenu by remember { mutableStateOf(false) }
  var receiptMenu by remember { mutableStateOf(false) }
  var amountMenu by remember { mutableStateOf(false) }
  var showDateRangePicker by remember { mutableStateOf(false) }
  var collapsedDays by remember { mutableStateOf(setOf<LocalDate>()) }

  if (!data.loaded) return

  // A selected day belongs to one month; moving to another month clears it.
  LaunchedEffect(month) {
    val day = filters.day
    if (day != null && YearMonth.from(day) != month) viewModel.updateFilters { it.copy(day = null) }
  }

  val categoriesById = remember(data.categories) { data.categories.associateBy { it.id } }
  val accountsById = remember(data.accounts) { data.accounts.associateBy { it.id } }

  val hasCustomDateRange = filters.fromDate != null || filters.toDate != null
  val allMonth = remember(data.transactions, month, hasCustomDateRange) {
    if (hasCustomDateRange) data.transactions
    else Analytics.inSpan(data.transactions, Periods.monthSpan(month))
  }

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
    val txDate = Periods.millisToDate(tx.timestamp)
    val matchesDay = filters.day == null || txDate == filters.day
    val minAmount = filters.minAmountMinor
    val maxAmount = filters.maxAmountMinor
    val from = filters.fromDate
    val to = filters.toDate
    val matchesReceipt = filters.hasReceipt == null || (if (filters.hasReceipt == true) tx.receiptFile != null else tx.receiptFile == null)
    val matchesMin = minAmount == null || tx.amountMinor >= minAmount
    val matchesMax = maxAmount == null || tx.amountMinor <= maxAmount
    val matchesFrom = from == null || txDate >= from
    val matchesTo = to == null || txDate <= to

    matchesQuery && matchesCategory && matchesAccount && matchesDay && matchesReceipt && matchesMin && matchesMax && matchesFrom && matchesTo
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
        label = if (hasCustomDateRange) "Custom" else Periods.monthLabel(month),
        onPrevious = { if (!hasCustomDateRange) viewModel.shiftMonth(-1) },
        onNext = { if (!hasCustomDateRange) viewModel.shiftMonth(1) },
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
      val focus = remember { FocusRequester() }
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
        colors = OutlinedTextFieldDefaults.colors(
          unfocusedBorderColor = Color.Transparent,
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
            modifier = Modifier.padding(vertical = 4.dp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
          )
          Box(
            Modifier
              .fillMaxWidth()
              .height(3.dp)
              .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
          )
        }
      }
    }

    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Account filter
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

      // Category filter
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

      // Amount filter
      Box {
        val amountLabel = when {
          filters.minAmountMinor == null && filters.maxAmountMinor == 50000L -> "< 500"
          filters.minAmountMinor == 50000L && filters.maxAmountMinor == 200000L -> "500 - 2k"
          filters.minAmountMinor == 200000L && filters.maxAmountMinor == 1000000L -> "2k - 10k"
          filters.minAmountMinor == 1000000L && filters.maxAmountMinor == null -> "> 10k"
          filters.minAmountMinor != null || filters.maxAmountMinor != null -> "Custom amount"
          else -> "All amounts"
        }
        FilterChip(
          selected = filters.minAmountMinor != null || filters.maxAmountMinor != null,
          onClick = { amountMenu = true },
          label = { Text(amountLabel) },
          trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
          modifier = Modifier.testTag("filter_amount")
        )
        DropdownMenu(expanded = amountMenu, onDismissRequest = { amountMenu = false }) {
          DropdownMenuItem(text = { Text("All amounts") }, onClick = {
            viewModel.updateFilters { it.copy(minAmountMinor = null, maxAmountMinor = null) }
            amountMenu = false
          })
          DropdownMenuItem(text = { Text("Under 500") }, onClick = {
            viewModel.updateFilters { it.copy(minAmountMinor = null, maxAmountMinor = 50000L) }
            amountMenu = false
          })
          DropdownMenuItem(text = { Text("500 – 2,000") }, onClick = {
            viewModel.updateFilters { it.copy(minAmountMinor = 50000L, maxAmountMinor = 200000L) }
            amountMenu = false
          })
          DropdownMenuItem(text = { Text("2,000 – 10,000") }, onClick = {
            viewModel.updateFilters { it.copy(minAmountMinor = 200000L, maxAmountMinor = 1000000L) }
            amountMenu = false
          })
          DropdownMenuItem(text = { Text("Over 10,000") }, onClick = {
            viewModel.updateFilters { it.copy(minAmountMinor = 1000000L, maxAmountMinor = null) }
            amountMenu = false
          })
        }
      }

      // Receipt filter
      Box {
        val receiptLabel = when (filters.hasReceipt) {
          true -> "With receipt"
          false -> "No receipt"
          null -> "Receipt"
        }
        FilterChip(
          selected = filters.hasReceipt != null,
          onClick = { receiptMenu = true },
          label = { Text(receiptLabel) },
          trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
          modifier = Modifier.testTag("filter_receipt")
        )
        DropdownMenu(expanded = receiptMenu, onDismissRequest = { receiptMenu = false }) {
          DropdownMenuItem(text = { Text("All entries") }, onClick = {
            viewModel.updateFilters { it.copy(hasReceipt = null) }
            receiptMenu = false
          })
          DropdownMenuItem(text = { Text("With receipt only") }, onClick = {
            viewModel.updateFilters { it.copy(hasReceipt = true) }
            receiptMenu = false
          })
          DropdownMenuItem(text = { Text("Without receipt") }, onClick = {
            viewModel.updateFilters { it.copy(hasReceipt = false) }
            receiptMenu = false
          })
        }
      }

      // Date Range filter
      val dateRangeLabel = if (hasCustomDateRange) {
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        val from = filters.fromDate?.format(fmt).orEmpty()
        val to = filters.toDate?.format(fmt).orEmpty()
        if (from.isNotEmpty() && to.isNotEmpty()) "$from – $to"
        else if (from.isNotEmpty()) "From $from"
        else "Up to $to"
      } else "Date range"

      FilterChip(
        selected = hasCustomDateRange,
        onClick = { showDateRangePicker = true },
        label = { Text(dateRangeLabel) },
        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        modifier = Modifier.testTag("filter_date_range")
      )

      // Sort order
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

  if (showDateRangePicker) {
    DateRangeFilterDialog(
      currentFrom = filters.fromDate,
      currentTo = filters.toDate,
      onApply = { from, to ->
        viewModel.updateFilters { it.copy(fromDate = from, toDate = to) }
      },
      onDismiss = { showDateRangePicker = false }
    )
  }
}

@Composable
fun DateRangeFilterDialog(
  currentFrom: LocalDate?,
  currentTo: LocalDate?,
  onApply: (from: LocalDate?, to: LocalDate?) -> Unit,
  onDismiss: () -> Unit
) {
  var fromDate by remember { mutableStateOf(currentFrom) }
  var toDate by remember { mutableStateOf(currentTo) }
  var pickingField by remember { mutableStateOf<String?>(null) }

  if (pickingField != null) {
    val initialDate = (if (pickingField == "from") fromDate else toDate) ?: LocalDate.now()
    val datePickerState = rememberDatePickerState(
      initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
      onDismissRequest = { pickingField = null },
      confirmButton = {
        TextButton(onClick = {
          datePickerState.selectedDateMillis?.let { millis ->
            val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            if (pickingField == "from") fromDate = date else toDate = date
          }
          pickingField = null
        }) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { pickingField = null }) { Text("Cancel") } }
    ) {
      DatePicker(state = datePickerState)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Filter by date range") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("From date", style = MaterialTheme.typography.labelMedium)
            Text(fromDate?.format(formatter) ?: "Not set", style = MaterialTheme.typography.bodyMedium)
          }
          Row {
            if (fromDate != null) TextButton(onClick = { fromDate = null }) { Text("Clear") }
            TextButton(onClick = { pickingField = "from" }) { Text("Set") }
          }
        }
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("To date", style = MaterialTheme.typography.labelMedium)
            Text(toDate?.format(formatter) ?: "Not set", style = MaterialTheme.typography.bodyMedium)
          }
          Row {
            if (toDate != null) TextButton(onClick = { toDate = null }) { Text("Clear") }
            TextButton(onClick = { pickingField = "to" }) { Text("Set") }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        onApply(fromDate, toDate)
        onDismiss()
      }) { Text("Apply") }
    },
    dismissButton = {
      Row {
        TextButton(onClick = {
          onApply(null, null)
          onDismiss()
        }) { Text("Reset") }
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    }
  )
}

/** New entries default to today, or to a sensible day when browsing another month. */
private fun defaultEntryDate(month: YearMonth, today: LocalDate): LocalDate =
  if (YearMonth.from(today) == month) today else month.atDay(1)

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
  month: YearMonth,
  today: LocalDate,
  selected: LocalDate?,
  daysWithEntries: Set<LocalDate>,
  onSelect: (LocalDate?) -> Unit
) {
  val days = remember(month) { (1..month.lengthOfMonth()).map { month.atDay(it) } }
  val listState = rememberLazyListState()
  LaunchedEffect(month, selected) {
    val target = selected ?: today.takeIf { YearMonth.from(it) == month }
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
