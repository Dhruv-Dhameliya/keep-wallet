package com.keepwallet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keepwallet.app.data.ReceiptStore
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.ui.AppData
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.SubScreenHeader
import com.keepwallet.app.ui.components.heroFontSize
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A simple bill-style summary of one entry, with Edit and Delete. */
@Composable
fun EntryDetailsScreen(
  viewModel: MainViewModel,
  transaction: TransactionEntity,
  data: AppData,
  receipts: ReceiptStore,
  onBack: () -> Unit
) {
  val category = data.categories.firstOrNull { it.id == transaction.categoryId }
  val account = data.accounts.firstOrNull { it.id == transaction.accountId }
  val isTransfer = transaction.type == TxType.TRANSFER_OUT || transaction.type == TxType.TRANSFER_IN
  val isIncome = transaction.type == TxType.INCOME
  val legs = if (isTransfer) data.transactions.filter { it.transferId == transaction.transferId } else emptyList()
  val fromAccount = legs.firstOrNull { it.type == TxType.TRANSFER_OUT }?.let { leg -> data.accounts.firstOrNull { it.id == leg.accountId } }
  val toAccount = legs.firstOrNull { it.type == TxType.TRANSFER_IN }?.let { leg -> data.accounts.firstOrNull { it.id == leg.accountId } }
  val siblings = if (transaction.splitId != null) data.transactions.filter { it.splitId == transaction.splitId } else emptyList()
  val moment = Instant.ofEpochMilli(transaction.timestamp).atZone(ZoneId.systemDefault())
  var confirmDelete by remember { mutableStateOf(false) }
  var viewingReceipt by remember { mutableStateOf(false) }
  val extra = MaterialTheme.extra
  val amountText = if (isTransfer) money(transaction.amountMinor) else money(if (isIncome) transaction.amountMinor else -transaction.amountMinor, sign = isIncome)

  Surface(Modifier.fillMaxSize().testTag("entry_details"), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
      SubScreenHeader("Details", onBack)
      Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        AppCard(Modifier.fillMaxWidth()) {
          Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isTransfer) {
              Box(Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = "Transfer", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
              }
            } else {
              CategoryBadge(category, size = 60.dp)
            }
            Spacer(Modifier.height(12.dp))
            Text(
              transaction.merchant,
              style = MaterialTheme.typography.titleLarge,
              textAlign = TextAlign.Center,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.testTag("details_name")
            )
            Spacer(Modifier.height(10.dp))
            Text(
              amountText,
              style = MaterialTheme.typography.displaySmall.copy(fontSize = heroFontSize(amountText, 40)),
              color = when {
                isTransfer -> MaterialTheme.colorScheme.secondary
                isIncome -> extra.income
                else -> MaterialTheme.colorScheme.onSurface
              },
              maxLines = 1,
              softWrap = false,
              modifier = Modifier.testTag("details_amount")
            )
            Spacer(Modifier.height(8.dp))
            Box(
              Modifier
                .clip(RoundedCornerShape(50))
                .background(if (isIncome) extra.incomeContainer else MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
              Text(
                if (isTransfer) "Transferred" else if (isIncome) "Received" else "Paid",
                style = MaterialTheme.typography.labelMedium,
                color = if (isIncome) extra.income else MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
          }
          TicketDivider()
          Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DetailRow("Date", DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy").format(moment))
            DetailRow("Time", DateTimeFormatter.ofPattern("HH:mm").format(moment))
            if (isTransfer) {
              DetailRow("From", fromAccount?.name ?: "–")
              DetailRow("To", toAccount?.name ?: "–")
            } else {
              DetailRow("Category", category?.name ?: "–")
              DetailRow(if (isIncome) "Received in" else "Paid from", account?.name ?: "–")
            }
            if (transaction.foreignCode != null && transaction.foreignMinor != null) {
              val info = com.keepwallet.app.data.CurrencyRates.info(transaction.foreignCode)
              DetailRow("Paid in ${info.code}", com.keepwallet.app.domain.Money.format(transaction.foreignMinor, info.symbol))
            }
            transaction.note?.takeIf { it.isNotBlank() }?.let { DetailRow("Note", it) }
            if (siblings.size > 1) {
              DetailRow("Split bill", "${siblings.size} parts · ${money(siblings.sumOf { it.amountMinor })}")
              siblings.filter { it.id != transaction.id }.forEach { part ->
                DetailRow("  also", "${data.categories.firstOrNull { it.id == part.categoryId }?.name ?: "–"} · ${money(part.amountMinor)}")
              }
            }
          }
          if (transaction.receiptFile != null) {
            TicketDivider()
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Text("Receipt", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(Modifier.height(10.dp))
              ReceiptThumbnail(
                name = transaction.receiptFile, receipts = receipts, maxSidePx = 600,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(180.dp)
                  .clip(RoundedCornerShape(14.dp))
                  .clickable(role = Role.Button) { viewingReceipt = true }
                  .semantics { contentDescription = "Receipt photo. Tap to enlarge." }
                  .testTag("details_receipt")
              )
            }
          }
          TicketDivider()
          Text(
            "Keep Wallet",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
          )
        }
      }

      Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
          onClick = { confirmDelete = true },
          modifier = Modifier.weight(1f).height(52.dp).testTag("delete_entry_btn"),
          shape = RoundedCornerShape(16.dp)
        ) {
          Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
          Spacer(Modifier.size(8.dp))
          Text("Delete", color = MaterialTheme.colorScheme.error)
        }
        if (!isTransfer) Button(
          onClick = viewModel::editViewing,
          modifier = Modifier.weight(1f).height(52.dp).testTag("edit_entry_btn"),
          shape = RoundedCornerShape(16.dp)
        ) {
          Icon(Icons.Rounded.Edit, contentDescription = null)
          Spacer(Modifier.size(8.dp))
          Text("Edit")
        }
      }
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(if (isTransfer) "Delete this transfer?" else "Delete this entry?") },
      text = { Text(if (isTransfer) "The ${money(transaction.amountMinor)} transfer will be removed from both accounts. You can undo right after." else "${transaction.merchant} · ${money(transaction.amountMinor)} will be removed. You can undo right after.") },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDelete = false
            viewModel.deleteViewing()
          },
          modifier = Modifier.testTag("confirm_delete")
        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
    )
  }

  if (viewingReceipt && transaction.receiptFile != null) {
    ReceiptViewer(transaction.receiptFile, receipts, onClose = { viewingReceipt = false })
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(16.dp))
    Text(
      value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
      textAlign = TextAlign.End,
      modifier = Modifier.weight(1f)
    )
  }
}

/** Dashed tear-off line with a notch on each side, like the edge of a receipt. */
@Composable
private fun TicketDivider() {
  val line = MaterialTheme.colorScheme.outline
  val page = MaterialTheme.colorScheme.background
  Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(2.dp)) {
      drawLine(
        color = line,
        start = Offset(0f, size.height / 2),
        end = Offset(size.width, size.height / 2),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))
      )
    }
    Box(Modifier.align(Alignment.CenterStart).offset(x = (-10).dp).size(20.dp).clip(CircleShape).background(page))
    Box(Modifier.align(Alignment.CenterEnd).offset(x = 10.dp).size(20.dp).clip(CircleShape).background(page))
  }
}
