package com.keepwallet.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.unit.sp
import com.keepwallet.app.data.BackgroundMode
import com.keepwallet.app.ui.theme.Motion
import com.keepwallet.app.ui.components.SegmentedToggle
import androidx.compose.material3.Switch
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.AppPalette
import com.keepwallet.app.domain.Money
import com.keepwallet.app.ui.AppData
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.KeepWalletMark
import com.keepwallet.app.ui.theme.extra
import kotlinx.coroutines.delay

/**
 * The logo card shown on the very first launch only: the Keep Wallet lockup on a clean page, in ink on white or in
 * light on near-black to match the theme. It settles in with a short fade and a slight scale, then hands over;
 * later launches skip it entirely.
 */
@Composable
fun SplashCard(onFinished: () -> Unit) {
  val progress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    progress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
    delay(780)
    onFinished()
  }
  val dark = com.keepwallet.app.ui.theme.isDarkTheme()
  val page = if (dark) Color(0xFF0A0B0D) else Color.White
  val ink = if (dark) Color(0xFFF2F4F5) else Color(0xFF0C0D0F)
  Surface(Modifier.fillMaxSize().testTag("splash_card"), color = page) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      // The artwork is nearly square; it is shown at about two thirds of the width, never taller than 60% of the page.
      androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.keepwallet.app.R.drawable.logo_lockup),
        contentDescription = "Keep Wallet",
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(ink),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        modifier = Modifier
          .fillMaxWidth(0.7f)
          .fillMaxHeight(0.6f)
          .graphicsLayer {
            val t = progress.value
            alpha = t
            scaleX = 0.94f + 0.06f * t
            scaleY = 0.94f + 0.06f * t
          }
      )
    }
  }
}

/**
 * A two-step setup for a fresh install: pick a look and a currency (the screen itself takes on each choice as you
 * make it), then name the first account and say what it holds. Everything can be skipped and changed later.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(viewModel: MainViewModel, data: AppData) {
  val palette by viewModel.palette.collectAsStateWithLifecycle()
  val currency by viewModel.currencySymbol.collectAsStateWithLifecycle()
  val background by viewModel.backgroundMode.collectAsStateWithLifecycle()
  val hideOnOpen by viewModel.hideOnOpen.collectAsStateWithLifecycle()
  var step by rememberSaveable { mutableIntStateOf(0) }
  val bank = data.accounts.firstOrNull { it.kind == AccountKind.BANK }
  var name by rememberSaveable { mutableStateOf(bank?.name ?: "Bank account") }
  var balance by rememberSaveable { mutableStateOf("") }

  fun finish() {
    val amount = Money.parseMinor(balance)
    if (bank != null && (name.isNotBlank() && name.trim() != bank.name || amount > 0)) {
      viewModel.saveAccount(bank.copy(name = name.trim().ifEmpty { bank.name }), amount.takeIf { it > 0 } ?: 0L)
    }
    viewModel.finishOnboarding()
  }

  Surface(Modifier.fillMaxSize().testTag("welcome_screen"), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { index ->
          val active = index == step
          val width by androidx.compose.animation.core.animateDpAsState(if (active) 26.dp else 10.dp, tween(Motion.Medium, easing = Motion.Standard), label = "dot")
          Box(
            Modifier
              .padding(end = 6.dp)
              .height(5.dp)
              .width(width)
              .clip(CircleShape)
              .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
          )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = ::finish, modifier = Modifier.testTag("welcome_skip")) { Text("Skip") }
      }
      AnimatedContent(
        targetState = step,
        transitionSpec = {
          val direction = if (targetState > initialState) 1 else -1
          val move = tween<androidx.compose.ui.unit.IntOffset>(Motion.Page, easing = Motion.Standard)
          (slideInHorizontally(move) { direction * it / 3 } + fadeIn(tween(Motion.Page, easing = Motion.Standard))) togetherWith
            (slideOutHorizontally(move) { -direction * it / 3 } + fadeOut(tween(Motion.Short)))
        },
        modifier = Modifier.weight(1f),
        label = "welcome"
      ) { page ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          if (page == 0) {
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
              KeepWalletMark(size = 40.dp, color = MaterialTheme.colorScheme.onBackground)
              Column {
                Text("Welcome to Keep Wallet", style = MaterialTheme.typography.titleLarge)
                Text("Everything stays on this phone.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            PreviewCard(currency)
            Text("Appearance", style = MaterialTheme.typography.titleSmall)
            SegmentedToggle(
              options = listOf(BackgroundMode.SYSTEM to "System", BackgroundMode.LIGHT to "Light", BackgroundMode.DARK to "Dark"),
              selected = background.takeIf { it == BackgroundMode.LIGHT || it == BackgroundMode.DARK } ?: BackgroundMode.SYSTEM,
              onSelect = viewModel::setBackgroundMode,
              modifier = Modifier.fillMaxWidth().testTag("welcome_appearance")
            )
            Text("Colours", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("welcome_palettes"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              AppPalette.classic.forEach { option ->
                WelcomePalette(option, selected = palette == option) { viewModel.applyPalette(option) }
              }
            }
            Text("Currency", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Money.presetSymbols.forEach { symbol ->
                FilterChip(
                  selected = currency == symbol,
                  onClick = { viewModel.setCurrency(symbol) },
                  label = { Text(symbol, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                  modifier = Modifier.weight(1f).testTag("welcome_currency_$symbol")
                )
              }
            }
          } else {
            Spacer(Modifier.height(6.dp))
            Box(
              Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Text("Your first account", style = MaterialTheme.typography.titleLarge)
            Text(
              "Where most of your money sits. Add what it holds today and Keep Wallet keeps it up to date as you add entries.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
              value = name, onValueChange = { name = it.take(24) }, label = { Text("Account name") }, singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("welcome_account_name")
            )
            OutlinedTextField(
              value = balance, onValueChange = { balance = sanitizeAmount(it) }, label = { Text("Balance today ($currency)") }, singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("welcome_balance")
            )
            Row(
              Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(Modifier.weight(1f)) {
                Text("Hide balance when I open the app", style = MaterialTheme.typography.titleSmall)
                Text("Tap the eye on the card to show it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Switch(checked = hideOnOpen, onCheckedChange = viewModel::setHideOnOpen, modifier = Modifier.testTag("welcome_hide_switch"))
            }
            Text(
              "You can add wallets, cash and more accounts later from the balance card.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
      Button(
        onClick = { if (step == 0) step = 1 else finish() },
        modifier = Modifier.fillMaxWidth().height(50.dp).testTag(if (step == 0) "welcome_next" else "welcome_finish"),
        shape = RoundedCornerShape(16.dp)
      ) { Text(if (step == 0) "Next" else "Start tracking", style = MaterialTheme.typography.titleSmall) }
    }
  }
}

/** A small balance card in the colours chosen so far: the page you are looking at is already the app's look. */
@Composable
private fun PreviewCard(symbol: String) {
  val extra = MaterialTheme.extra
  Box(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(Brush.linearGradient(listOf(extra.cardStart, extra.cardEnd)))
      .padding(horizontal = 16.dp, vertical = 14.dp)
      .testTag("welcome_preview")
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text("BANK BALANCE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f), letterSpacing = 1.1.sp)
      Text("${symbol}12,480.50", style = MaterialTheme.typography.headlineMedium, color = Color.White)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
        listOf("${symbol}3,200 in", "${symbol}1,140 out").forEach { text ->
          Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun WelcomePalette(palette: AppPalette, selected: Boolean, onClick: () -> Unit) {
  val accent = Color(palette.seedArgb)
  val ringWidth by androidx.compose.animation.core.animateDpAsState(if (selected) 2.dp else 0.dp, tween(Motion.Short, easing = Motion.Standard), label = "ring")
  Column(
    Modifier
      .width(88.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(MaterialTheme.colorScheme.surfaceContainerLow)
      .border(if (selected) ringWidth else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
      .clickable(role = Role.RadioButton, onClick = onClick)
      .semantics { contentDescription = palette.label + if (selected) ", selected" else "" }
      .padding(8.dp)
      .testTag("palette_${palette.name}"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(
      Modifier.size(width = 72.dp, height = 48.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.5f)))),
      contentAlignment = Alignment.BottomEnd
    ) {
      Box(Modifier.padding(5.dp).size(14.dp).clip(CircleShape).background(Color(palette.tertiary)))
      if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(16.dp))
    }
    Text(palette.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
  }
}
