package com.keepwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.domain.AlertItem
import com.keepwallet.app.domain.AlertKind
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.PlanSection
import com.keepwallet.app.ui.Tab
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.EmptyState
import com.keepwallet.app.ui.components.SubScreenHeader
import com.keepwallet.app.ui.theme.extra

/**
 * What needs attention right now, in one place: payments waiting for review, bills that are due, budgets that are
 * filling up. It is worked out from your data each time, so it is never out of date and never needs clearing.
 */
@Composable
fun NotificationsScreen(viewModel: MainViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
  val alerts by viewModel.alerts.collectAsStateWithLifecycle()
  val seen by viewModel.seenAlerts.collectAsStateWithLifecycle()
  // What was new when the page opened stays marked "New" while it is on screen; leaving counts it as read.
  val newOnOpen = androidx.compose.runtime.remember { alerts.map { it.key }.filter { it !in seen }.toSet() }
  androidx.compose.runtime.LaunchedEffect(alerts) { viewModel.markAlertsSeen() }
  Column(modifier.fillMaxSize()) {
    SubScreenHeader("Notifications", onBack)
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("alerts_list"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (alerts.isEmpty()) {
        item(key = "empty") {
          EmptyState(
            icon = Icons.Rounded.NotificationsNone,
            title = "You're all caught up",
            body = "Bills that are due, budgets that are filling up and payments waiting for review will show up here.",
            modifier = Modifier.padding(top = 32.dp).testTag("alerts_empty")
          )
        }
      } else {
        items(alerts, key = { it.key }) { alert ->
          AlertRow(alert, isNew = alert.key in newOnOpen || alert.key !in seen) {
            when (alert.kind) {
              AlertKind.PAYMENTS_WAITING -> viewModel.requestTab(Tab.HOME)
              AlertKind.BILL_DUE, AlertKind.BILL_OVERDUE -> viewModel.openPlan(PlanSection.BILLS)
              AlertKind.BUDGET_NEAR, AlertKind.BUDGET_OVER -> viewModel.openPlan(PlanSection.BUDGETS)
            }
          }
        }
      }
      item(key = "settings") {
        androidx.compose.material3.OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().testTag("alerts_settings")) { Text("Choose which reminders to get") }
      }
    }
  }
}

@Composable
private fun AlertRow(alert: AlertItem, isNew: Boolean, onClick: () -> Unit) {
  val extra = MaterialTheme.extra
  val (icon, tint) = when (alert.kind) {
    AlertKind.PAYMENTS_WAITING -> Icons.Rounded.AccountBalanceWallet to MaterialTheme.colorScheme.primary
    AlertKind.BILL_OVERDUE -> Icons.Rounded.Warning to MaterialTheme.colorScheme.error
    AlertKind.BILL_DUE -> Icons.Rounded.EventRepeat to extra.warning
    AlertKind.BUDGET_OVER -> Icons.Rounded.Warning to MaterialTheme.colorScheme.error
    AlertKind.BUDGET_NEAR -> Icons.Rounded.PieChart to extra.warning
  }
  AppCard(Modifier.fillMaxWidth().testTag("alert_${alert.kind.name}"), onClick = onClick) {
    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
      }
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(alert.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
          if (isNew) {
            Spacer(Modifier.width(8.dp))
            Text("New", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("alert_new_${alert.kind.name}"))
          }
        }
        Text(alert.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
