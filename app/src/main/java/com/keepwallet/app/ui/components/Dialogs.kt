package com.keepwallet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.keepwallet.app.data.DetectedEntryEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Periods
import java.time.YearMonth

/** Year-and-month grid used wherever the user jumps to another month. */
@Composable
fun MonthPickerDialog(
  current: YearMonth,
  onDismiss: () -> Unit,
  onPick: (YearMonth) -> Unit
) {
  var year by remember { mutableIntStateOf(current.year) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = { year-- }) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous year") }
        Text(year.toString(), style = MaterialTheme.typography.titleLarge)
        IconButton(onClick = { year++ }) { Icon(Icons.Rounded.ChevronRight, contentDescription = "Next year") }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..12).chunked(3).forEach { months ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            months.forEach { m ->
              val option = YearMonth.of(year, m)
              val selected = option == current
              Box(
                modifier = Modifier
                  .weight(1f)
                  .height(44.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                  .clickable(role = Role.Button) { onPick(option) }
                  .testTag("month_$m"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  Periods.shortMonthLabel(option),
                  style = MaterialTheme.typography.labelLarge,
                  color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    dismissButton = { TextButton(onClick = { onPick(YearMonth.now()) }, modifier = Modifier.testTag("month_this")) { Text("This month") } }
  )
}

/** "Payment detected" prompt with one-tap Add and Dismiss, used on Home and as a floating banner. */
@Composable
fun DetectedEntryCard(
  entry: DetectedEntryEntity,
  onAdd: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  moreCount: Int = 0
) {
  val isIncome = entry.type == TxType.INCOME
  AppCard(
    modifier = modifier.fillMaxWidth().testTag("detected_card"),
    container = MaterialTheme.colorScheme.primaryContainer,
    border = null
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
          Text(
            if (isIncome) "Deposit detected" else "Payment detected",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
          Text(
            "${entry.merchant} · from ${entry.sourceApp}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            maxLines = 1
          )
        }
        Text(
          money(if (isIncome) entry.amountMinor else -entry.amountMinor, sign = isIncome),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.testTag("detected_amount")
        )
      }
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).testTag("detected_dismiss")) { Text("Dismiss") }
        Button(onClick = onAdd, modifier = Modifier.weight(1f).testTag("detected_add")) { Text("Add entry") }
      }
      if (moreCount > 0) {
        Text(
          "+$moreCount more waiting for review in Settings",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
      }
    }
  }
}

@Composable
fun ConfirmDialog(
  title: String,
  body: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  destructive: Boolean = false
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(body) },
    confirmButton = {
      TextButton(onClick = onConfirm) {
        Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

/**
 * Bottom-sheet form with the same slots as an AlertDialog. Used for anything with text fields: it has
 * room for the keyboard, scrolls when the form is long, and keeps Cancel / Save within thumb reach.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
  onDismissRequest: () -> Unit,
  title: @Composable () -> Unit,
  text: @Composable () -> Unit,
  confirmButton: @Composable () -> Unit,
  dismissButton: (@Composable () -> Unit)? = null
) {
  androidx.compose.material3.ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier = Modifier.testTag("form_sheet")
  ) {
    val maxHeight = with(androidx.compose.ui.platform.LocalDensity.current) { (androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height * 0.80f).toDp() }
    Column(
      Modifier
        .fillMaxWidth()
        .heightIn(max = maxHeight)
        .androidx_imePadding()
        .padding(horizontal = 20.dp)
    ) {
      ProvideTitleStyle { title() }
      Spacer(Modifier.height(12.dp))
      Column(
        Modifier
          .weight(1f, fill = false)
          .androidx_verticalScroll()
      ) { text() }
      Spacer(Modifier.height(12.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        dismissButton?.invoke()
        Spacer(Modifier.width(8.dp))
        confirmButton()
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun ProvideTitleStyle(content: @Composable () -> Unit) {
  androidx.compose.runtime.CompositionLocalProvider(
    androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.titleLarge
  ) { content() }
}

@Composable
private fun Modifier.androidx_imePadding(): Modifier =
  this.then(Modifier.imePadding()).then(Modifier.navigationBarsPadding())

@Composable
private fun Modifier.androidx_verticalScroll(): Modifier = this.verticalScroll(rememberScrollState())
