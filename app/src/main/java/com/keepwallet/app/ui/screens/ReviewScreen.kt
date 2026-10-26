package com.keepwallet.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.domain.Forecast
import com.keepwallet.app.domain.MonthFigure
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.Review
import com.keepwallet.app.domain.ReviewReport
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.AppProgressBar
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.CompactPeriodNav
import com.keepwallet.app.ui.components.Eyebrow
import com.keepwallet.app.ui.components.MonthBars
import com.keepwallet.app.ui.components.NetWorthChart
import com.keepwallet.app.ui.components.SegmentedToggle
import com.keepwallet.app.ui.components.SubScreenHeader
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra
import java.time.YearMonth

/**
 * A look back (or ahead): one month or one year in figures, where the money went, how much you kept, what you own
 * over time, and, for the month under way, where it is heading. The whole thing can be saved as a PDF.
 */
@Composable
fun ReviewScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  val today by viewModel.today.collectAsStateWithLifecycle()
  val symbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
  var yearly by rememberSaveable { mutableStateOf(false) }
  var year by rememberSaveable { mutableIntStateOf(today.year) }
  var monthIndex by rememberSaveable { mutableIntStateOf(YearMonth.from(today).year * 12 + YearMonth.from(today).monthValue - 1) }
  val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)

  val report = remember(data.transactions, data.categories, data.accounts, data.recurring, yearly, year, month, today) {
    if (yearly) Review.year(data.transactions, data.categories, data.accounts, year, today)
    else Review.month(data.transactions, data.categories, data.accounts, data.recurring, month, today)
  }

  val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
    if (uri != null) viewModel.exportReviewPdf(uri, report)
  }

  Column(modifier.fillMaxSize()) {
    SubScreenHeader("Review", onBack, trailing = {
      IconButton(onClick = { pdf.launch("keep-wallet-${report.title.replace(' ', '-').lowercase()}.pdf") }, modifier = Modifier.testTag("review_export")) {
        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Save as PDF")
      }
    })
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("review_list"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item(key = "controls") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SegmentedToggle(
            options = listOf(false to "Month", true to "Year"),
            selected = yearly,
            onSelect = { yearly = it },
            modifier = Modifier.weight(1f).testTag("review_mode")
          )
          if (yearly) {
            CompactPeriodNav(year.toString(), { year -= 1 }, { year += 1 }, previousDescription = "Previous year", nextDescription = "Next year", modifier = Modifier.testTag("review_period"))
          } else {
            CompactPeriodNav(
              Periods.shortMonthLabel(month), { monthIndex -= 1 }, { monthIndex += 1 },
              previousDescription = "Previous month", nextDescription = "Next month", modifier = Modifier.testTag("review_period")
            )
          }
        }
      }
      item(key = "summary") { SummaryCard(report) }
      if (report.topCategories.isNotEmpty()) item(key = "categories") { CategoriesCard(report) }
      report.forecast?.let { f -> item(key = "forecast") { ForecastCard(f) } }
      if (report.months.isNotEmpty()) {
        item(key = "months") { MonthsCard(report.months, report.best, report.worst) }
      }
      if (report.netWorth.size >= 2) item(key = "networth") { NetWorthCard(report) }
    }
  }
}

@Composable
private fun SummaryCard(report: ReviewReport) {
  AppCard(Modifier.fillMaxWidth().testTag("review_summary")) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
          Eyebrow("Kept")
          Text(money(report.netMinor, whole = true, sign = true), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("review_net"))
        }
        report.savingsRatePercent?.let { rate ->
          Column(horizontalAlignment = Alignment.End) {
            Eyebrow("Of income")
            Text("$rate%", style = MaterialTheme.typography.headlineMedium, color = if (rate >= 0) MaterialTheme.extra.income else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("review_rate"))
          }
        }
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
          Eyebrow("Income")
          Text(money(report.incomeMinor, whole = true), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("review_income"))
        }
        Column(horizontalAlignment = Alignment.End) {
          Eyebrow("Spent")
          Text(money(report.spentMinor, whole = true), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("review_spent"))
        }
      }
    }
  }
}

@Composable
private fun CategoriesCard(report: ReviewReport) {
  AppCard(Modifier.fillMaxWidth().testTag("review_categories")) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Where it went", style = MaterialTheme.typography.titleSmall)
      report.topCategories.forEach { total ->
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(total.category, size = 28.dp)
            Spacer(Modifier.padding(start = 10.dp))
            Text(total.category.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(money(total.amountMinor, whole = true), style = MaterialTheme.typography.labelLarge)
            Text("  " + total.percentLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          AppProgressBar(total.share, color = androidx.compose.ui.graphics.Color(total.category.color), height = 6.dp)
        }
      }
    }
  }
}

@Composable
private fun ForecastCard(f: Forecast) {
  AppCard(Modifier.fillMaxWidth().testTag("review_forecast")) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Rest of the month", style = MaterialTheme.typography.titleSmall)
      Text(
        "At this pace you will spend about ${money(f.projectedSpendMinor, whole = true)} by month end.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag("review_projected")
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
          Eyebrow("Per day so far")
          Text(money(f.dailyAverageMinor, whole = true), style = MaterialTheme.typography.titleSmall)
        }
        Column(horizontalAlignment = Alignment.End) {
          Eyebrow("Bills still due")
          Text(money(f.billsLeftMinor, whole = true), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("review_bills_left"))
        }
      }
      f.projectedLeftMinor?.let { left ->
        Text(
          if (left >= 0) "That leaves about ${money(left, whole = true)} of your budget." else "That would be ${money(-left, whole = true)} over your budget.",
          style = MaterialTheme.typography.bodySmall,
          color = if (left >= 0) MaterialTheme.extra.income else MaterialTheme.colorScheme.error,
          modifier = Modifier.testTag("review_projected_left")
        )
      }
    }
  }
}

@Composable
private fun MonthsCard(months: List<MonthFigure>, best: MonthFigure?, worst: MonthFigure?) {
  AppCard(Modifier.fillMaxWidth().testTag("review_months")) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text("Month by month", style = MaterialTheme.typography.titleSmall)
      MonthBars(months)
      if (best != null && worst != null && best.month != worst.month) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column {
            Eyebrow("Best month")
            Text(Periods.shortMonthLabel(best.month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("review_best"))
            Text(money(best.netMinor, whole = true, sign = true), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Column(horizontalAlignment = Alignment.End) {
            Eyebrow("Hardest month")
            Text(Periods.shortMonthLabel(worst.month), style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("review_worst"))
            Text(money(worst.netMinor, whole = true, sign = true), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun NetWorthCard(report: ReviewReport) {
  AppCard(Modifier.fillMaxWidth().testTag("review_networth")) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text("What you own", style = MaterialTheme.typography.titleSmall)
        Text(money(report.netWorth.last().second, whole = true), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("review_networth_now"))
      }
      NetWorthChart(report.netWorth)
      Spacer(Modifier.height(0.dp))
    }
  }
}
