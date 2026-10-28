package com.keepwallet.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.balanceAsOf
import com.keepwallet.app.domain.endOfMonthMillis
import com.keepwallet.app.ui.components.AmountText
import com.keepwallet.app.ui.components.KeepWalletMark
import com.keepwallet.app.ui.components.StatPill
import com.keepwallet.app.ui.components.heroFontSize
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra

private const val HIDDEN = "••••••"

/**
 * The home screen's hero: one large card with the bank balance (or the total of every account, if that's switched
 * on in Settings). Tap it for every account; hold it to pick another month, and the whole app follows that month.
 * A past month shows the balance as it stood at the end of that month.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun BalanceCardWidget(
  ctx: HomeContext,
  showTotal: Boolean,
  heroAccounts: Set<Long>? = null,
  hidden: Boolean,
  onClick: () -> Unit,
  onPickMonth: () -> Unit,
  onToggleHidden: () -> Unit,
  onBackToNow: () -> Unit
) {
  val data = ctx.data
  val endMillis = remember(ctx.month) { Analytics.endOfMonthMillis(ctx.month) }
  fun balanceOf(list: List<AccountEntity>): Long = list.sumOf {
    if (ctx.isCurrentMonth) Analytics.accountBalance(it, data.transactions) else Analytics.balanceAsOf(it, data.transactions, endMillis)
  }
  val banks = data.accounts.filter { it.kind == AccountKind.BANK }
  // Which accounts add up on the card: a hand-picked set (chosen on the Balance page), or all of them, or the banks.
  val picked = heroAccounts?.let { ids -> data.accounts.filter { it.id in ids } }?.takeIf { it.isNotEmpty() }
  val counted = picked ?: if (showTotal) data.accounts else banks
  val shownBalance = balanceOf(counted)
  val everything = counted.size == data.accounts.size
  val bankOnly = counted.map { it.id }.toSet() == banks.map { it.id }.toSet()
  // The balance counts up to its value, and from the old value to a new one when the month or an entry changes it.
  var displayed by remember { mutableLongStateOf(0L) }
  LaunchedEffect(shownBalance) {
    val start = displayed
    Animatable(0f).animateTo(1f, tween(550, easing = FastOutSlowInEasing)) {
      displayed = start + ((shownBalance - start) * value.toDouble()).toLong()
    }
    displayed = shownBalance
  }
  val income = remember(ctx.monthTransactions) { Analytics.total(ctx.monthTransactions, TxType.INCOME) }
  val expense = remember(ctx.monthTransactions) { Analytics.total(ctx.monthTransactions, TxType.EXPENSE) }
  val untouched = data.transactions.isEmpty() && data.accounts.all { it.openingBalanceMinor == 0L }
  val label = (if (everything) "Total balance" else if (bankOnly) "Bank balance" else if (counted.size == 1) counted.first().name else "Selected balance") +
    if (ctx.isCurrentMonth) "" else " · end of ${Periods.shortMonthLabel(ctx.month)} ${ctx.month.year}"
  val extra = MaterialTheme.extra
  val shape = RoundedCornerShape(30.dp)
  val haptics = LocalHapticFeedback.current
  val roomy = LocalDensity.current.fontScale <= 1.2f

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("balance_card")
      .clip(shape)
      .background(Brush.linearGradient(listOf(extra.cardStart, extra.cardEnd)))
      .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
      .combinedClickable(
        role = Role.Button,
        onClickLabel = "Open balance details",
        onClick = onClick,
        onLongClickLabel = "Choose month",
        onLongClick = {
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          onPickMonth()
        }
      )
  ) {
    // Quiet decoration: two soft discs and a diagonal sheen, nothing that competes with the figures.
    Canvas(Modifier.matchParentSize()) {
      drawCircle(Color.White.copy(alpha = 0.07f), radius = size.width * 0.46f, center = Offset(size.width * 1.0f, size.height * 0.0f))
      drawCircle(Color.White.copy(alpha = 0.05f), radius = size.width * 0.34f, center = Offset(size.width * 0.78f, size.height * 1.08f))
      drawRect(
        Brush.linearGradient(
          listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent),
          start = Offset(0f, 0f),
          end = Offset(size.width, size.height)
        )
      )
    }
    Column(
      // No fixed height: the card is as tall as what it holds, with only a little air between the name and the balance.
      Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        KeepWalletMark(size = 26.dp, knockout = extra.cardStart)
        if (roomy) {
          Spacer(Modifier.width(8.dp))
          Text("KEEP WALLET", style = MaterialTheme.typography.labelMedium, letterSpacing = 2.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        Box(
          Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(role = Role.Button, onClick = onToggleHidden)
            .testTag("hero_eye")
            .semantics { contentDescription = if (hidden) "Show balance" else "Hide balance" },
          contentAlignment = Alignment.Center
        ) {
          Icon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
      }

      Column(Modifier.padding(top = 10.dp)) {
        Text(
          label.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          letterSpacing = 1.4.sp,
          color = Color.White.copy(alpha = 0.78f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        // Only this text reads the counting value, so the frames of the count-up redraw one line, not the whole card.
        HeroAmount(hidden = hidden, value = { displayed })
        Spacer(Modifier.width(1.dp))
        androidx.compose.foundation.layout.FlowRow(
          Modifier.padding(top = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          StatPill(
            if (hidden) "$HIDDEN in" else "${money(income, whole = true)} in",
            leading = { Icon(Icons.Rounded.SouthWest, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp)) },
            textTag = "hero_income"
          )
          StatPill(
            if (hidden) "$HIDDEN out" else "${money(expense, whole = true)} out",
            leading = { Icon(Icons.Rounded.NorthEast, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp)) },
            textTag = "hero_expense"
          )
        }
      }

      // The whole card opens the balance page, so it needs no label saying so. Only a hint when there is nothing
      // yet, and a way back when another month is on show.
      if (untouched || !ctx.isCurrentMonth) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(
            if (untouched) "Tap to set your balance" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          if (!ctx.isCurrentMonth) {
            StatPill(
              "Back to now",
              container = Color.White.copy(alpha = 0.24f),
              modifier = Modifier.clickable(role = Role.Button, onClick = onBackToNow).testTag("hero_now")
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HeroAmount(hidden: Boolean, value: () -> Long) {
  val amountText = if (hidden) HIDDEN else money(value())
  AmountText(
    amountText,
    style = MaterialTheme.typography.displaySmall.copy(fontSize = heroFontSize(amountText.dropLast(3), 46)),
    color = Color.White,
    modifier = Modifier.testTag("hero_amount")
  )
}
