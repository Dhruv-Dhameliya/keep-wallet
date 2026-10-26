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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.keepwallet.app.data.GoalEntity
import com.keepwallet.app.data.RecurringEntity
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.Recurrence
import com.keepwallet.app.domain.highlights
import com.keepwallet.app.domain.percentUsed
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.AppProgressBar
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.ChevronToggle
import com.keepwallet.app.ui.components.EmptyState
import com.keepwallet.app.ui.components.ExpandArea
import com.keepwallet.app.ui.components.Eyebrow
import com.keepwallet.app.ui.components.SectionTitle
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.components.panel
import com.keepwallet.app.ui.theme.CategoryIcons
import com.keepwallet.app.ui.theme.extra
import java.time.LocalDate

// ---- Highlights -----------------------------------------------------------------------------------

/**
 * Four quick figures about the month on screen. Tap a figure and a line under the grid explains it in plain words;
 * the biggest expense also opens that entry.
 */
@Composable
fun HighlightsWidget(ctx: HomeContext, onOpenEntry: (TransactionEntity) -> Unit) {
  val figures = remember(ctx.data.transactions, ctx.month, ctx.today) { Analytics.highlights(ctx.data.transactions, ctx.month, ctx.today) }
  val budget = ctx.monthlyBudget
  var selected by rememberSaveable { mutableStateOf("") }
  val extra = MaterialTheme.extra
  val month = Periods.monthName(ctx.month)

  AppCard(Modifier.fillMaxWidth().testTag("widget_highlights")) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Highlights", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Eyebrow("${Periods.shortMonthLabel(ctx.month)} ${ctx.month.year}")
      }
      val tiles = listOf(
        Triple("daily", "Daily average", money(figures.dailyAverageMinor, whole = true)),
        Triple("forecast", if (ctx.isCurrentMonth) "Forecast" else "Month total", money(figures.projectedMinor, whole = true)),
        Triple("nospend", "No-spend days", figures.noSpendDays.toString()),
        Triple("biggest", "Biggest expense", figures.biggest?.let { money(it.amountMinor, whole = true) } ?: "–")
      )
      // One tile per row when text is large, so the labels never break mid-word.
      val perRow = if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.25f) 1 else 2
      tiles.chunked(perRow).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          pair.forEach { (key, label, value) ->
            val on = selected == key
            Column(
              Modifier
                .weight(1f)
                .panel(RoundedCornerShape(16.dp), if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), 0.5f)
                .clickable(role = Role.Button) { selected = if (on) "" else key }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("highlight_$key")
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(label, modifier = Modifier.weight(1f), maxLines = 2)
                ChevronToggle(on, Modifier.size(16.dp))
              }
              Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
            }
          }
        }
      }
      ExpandArea(selected.isNotEmpty()) {
        Column(Modifier.testTag("highlight_note"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          val note = when (selected) {
            "daily" -> "You've spent ${money(figures.dailyAverageMinor, whole = true)} a day over ${figures.elapsedDays} ${if (figures.elapsedDays == 1) "day" else "days"} of $month."
            "forecast" -> when {
              !ctx.isCurrentMonth -> "$month closed at ${money(figures.projectedMinor, whole = true)} of spending."
              budget > 0 && figures.projectedMinor > budget -> "At this pace $month ends ${money(figures.projectedMinor - budget, whole = true)} over your ${money(budget, whole = true)} budget."
              budget > 0 -> "At this pace $month ends ${money(budget - figures.projectedMinor, whole = true)} under your ${money(budget, whole = true)} budget."
              else -> "At this pace you'll spend ${money(figures.projectedMinor, whole = true)} by the end of $month. Set limits in Plan to compare."
            }
            "nospend" -> "${figures.noSpendDays} of ${figures.elapsedDays} days without any spending so far in $month."
            "biggest" -> figures.biggest?.let { "${it.merchant} · ${ctx.category(it.categoryId)?.name.orEmpty()} · ${Periods.shortDay(Periods.millisToDate(it.timestamp), ctx.today)}" }
              ?: "No spending yet in $month."
            else -> ""
          }
          Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (selected == "biggest") {
            figures.biggest?.let { tx -> TextButton(onClick = { onOpenEntry(tx) }, modifier = Modifier.testTag("highlight_open_biggest")) { Text("Open entry") } }
          }
        }
      }
      val change = figures.changePercent
      val saved = figures.savingsPercent
      if (change != null || saved != null) {
        com.keepwallet.app.ui.components.ResponsiveRow {
          if (change != null) {
            Text(
              if (change == 0) "Same as last month" else "${if (change > 0) "↑" else "↓"} ${kotlin.math.abs(change)}% vs last month",
              style = MaterialTheme.typography.labelLarge,
              color = if (change > 0) extra.warning else extra.income,
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.testTag("highlight_change")
            )
          }
          if (saved != null) {
            Text(
              if (saved >= 0) "Saved $saved% of income" else "Spent ${-saved}% over income",
              style = MaterialTheme.typography.labelLarge,
              color = if (saved >= 0) extra.income else extra.warning,
              maxLines = 1
            )
          }
        }
      }
    }
  }
}

// ---- Upcoming bills -------------------------------------------------------------------------------

/** The next few bills with a one-tap "Paid" that books the entry and moves the bill to its next date. */
@Composable
fun UpcomingBillsWidget(
  ctx: HomeContext,
  onPay: (RecurringEntity) -> Unit,
  onOpenBills: () -> Unit
) {
  val upcoming = remember(ctx.data.recurring) { ctx.data.recurring.filter { it.active }.sortedBy { it.nextDueDay }.take(3) }
  AppCard(Modifier.fillMaxWidth().testTag("widget_upcoming")) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
      SectionTitle("Upcoming bills", actionLabel = if (ctx.data.recurring.isNotEmpty()) "See all" else null, onAction = onOpenBills)
      if (upcoming.isEmpty()) {
        EmptyState(
          icon = Icons.Rounded.Event,
          title = "No bills yet",
          body = "Add rent, subscriptions or a salary once and Keep Wallet reminds you when they're due.",
          actionLabel = "Add a bill",
          onAction = onOpenBills
        )
      }
      upcoming.forEach { bill ->
        val due = LocalDate.ofEpochDay(bill.nextDueDay)
        val late = due.isBefore(ctx.today)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
          CategoryBadge(ctx.category(bill.categoryId), size = 38.dp)
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(bill.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              Recurrence.dueText(due, ctx.today),
              style = MaterialTheme.typography.bodySmall,
              color = if (late) MaterialTheme.extra.warning else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Text(money(bill.amountMinor, whole = false), style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false)
          Spacer(Modifier.width(8.dp))
          OutlinedButton(
            onClick = { onPay(bill) },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp).testTag("pay_${bill.name}")
          ) { Text(if (bill.type == TxType.INCOME) "Received" else "Paid", style = MaterialTheme.typography.labelMedium) }
        }
      }
    }
  }
}

// ---- Savings goals --------------------------------------------------------------------------------

@Composable
fun SavingsGoalsWidget(ctx: HomeContext, onOpenGoals: () -> Unit) {
  val goals = ctx.data.goals.take(3)
  AppCard(Modifier.fillMaxWidth().testTag("widget_goals"), onClick = onOpenGoals) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Savings goals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (ctx.data.goals.isNotEmpty()) Text("See all", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      }
      if (goals.isEmpty()) {
        EmptyState(
          icon = Icons.Rounded.Savings,
          title = "Nothing saved for yet",
          body = "Set a goal, like a trip or an emergency fund, and watch it fill up.",
          actionLabel = "Create a goal",
          onAction = onOpenGoals
        )
      }
      goals.forEach { goal -> GoalProgressRow(goal) }
    }
  }
}

@Composable
fun GoalProgressRow(goal: GoalEntity, modifier: Modifier = Modifier) {
  val percent = percentUsed(goal.savedMinor, goal.targetMinor)
  val done = goal.savedMinor >= goal.targetMinor
  val tint = Color(goal.color)
  Column(modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
      ) { Icon(CategoryIcons.get(goal.iconKey), contentDescription = null, tint = tint, modifier = Modifier.size(19.dp)) }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(goal.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          "${money(goal.savedMinor, whole = true)} of ${money(goal.targetMinor, whole = true)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
      }
      Text(
        if (done) "Done" else "$percent%",
        style = MaterialTheme.typography.titleSmall,
        color = if (done) MaterialTheme.extra.income else MaterialTheme.colorScheme.onSurface
      )
    }
    Spacer(Modifier.height(6.dp))
    AppProgressBar(goal.savedMinor.toFloat() / goal.targetMinor.coerceAtLeast(1), color = tint, track = tint.copy(alpha = 0.16f), height = 6.dp)
  }
}
