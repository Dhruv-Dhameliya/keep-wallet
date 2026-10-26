package com.keepwallet.app.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.CurrencyRates
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.SubScreenHeader
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Converts an amount between currencies. Works offline with built-in rates; the one network call in the app is the
 * "Update rates" button on this page.
 */
@Composable
fun ConverterScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val table by viewModel.rates.collectAsStateWithLifecycle()
  val status by viewModel.ratesStatus.collectAsStateWithLifecycle()
  val symbol by viewModel.currencySymbol.collectAsStateWithLifecycle()
  val home = remember(symbol) { CurrencyRates.codeForSymbol(symbol) }

  var amount by rememberSaveable { mutableStateOf("100") }
  var from by rememberSaveable { mutableStateOf(home) }
  var to by rememberSaveable { mutableStateOf(if (home == "USD") "EUR" else "USD") }

  val typed = amount.toDoubleOrNull()
  val result = typed?.let { CurrencyRates.convert(it, from, to, table.perUsd) }
  val unit = CurrencyRates.convert(1.0, from, to, table.perUsd)

  Column(modifier.fillMaxSize()) {
    SubScreenHeader("Currency converter", onBack)
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("converter_list"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item(key = "inputs") {
        AppCard(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = amount,
              onValueChange = { amount = cleanNumber(it) },
              label = { Text("Amount") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
              modifier = Modifier.fillMaxWidth().testTag("converter_amount")
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyPicker("From", from, "converter_from") { from = it }
                CurrencyPicker("To", to, "converter_to") { to = it }
              }
              IconButton(onClick = { from = to.also { to = from } }, modifier = Modifier.size(48.dp).testTag("converter_swap")) {
                Icon(Icons.Rounded.SwapVert, contentDescription = "Swap currencies")
              }
            }
          }
        }
      }

      item(key = "result") {
        AppCard(Modifier.fillMaxWidth(), container = MaterialTheme.colorScheme.primaryContainer) {
          Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              if (typed != null) "${format(typed, from)} =" else "Enter an amount",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.testTag("converter_from_text")
            )
            Text(
              if (result != null) format(result, to) else "–",
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              maxLines = 1,
              modifier = Modifier.testTag("converter_result")
            )
            if (unit != null) {
              Text(
                "1 $from = ${rate(unit)} $to",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("converter_rate")
              )
            }
          }
        }
      }

      item(key = "also") {
        val others = remember(from, to) { listOf("USD", "EUR", "GBP", "INR", "JPY", "AUD").filter { it != from }.take(5) }
        if (typed != null) {
          AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("${format(typed, from)} in other currencies", style = MaterialTheme.typography.titleSmall)
              others.forEach { code ->
                val converted = CurrencyRates.convert(typed, from, code, table.perUsd)
                Row(Modifier.fillMaxWidth().testTag("converter_other_$code"), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(CurrencyRates.info(code).name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text(if (converted != null) format(converted, code) else "–", style = MaterialTheme.typography.titleSmall)
                }
              }
            }
          }
        }
      }

      item(key = "rates") {
        AppCard(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Exchange rates", style = MaterialTheme.typography.titleSmall)
            Text(
              if (table.builtIn) "Built-in rates, approximate. Update them for today's numbers."
              else "Updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(table.updatedAt ?: 0L))}.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("converter_rates_info")
            )
            FilledTonalButton(
              onClick = viewModel::updateRates,
              enabled = !status.loading,
              modifier = Modifier.testTag("converter_update")
            ) {
              Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.size(8.dp))
              Text(if (status.loading) "Updating…" else "Update rates")
            }
            status.message?.let {
              Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("converter_status"))
            }
            Text(
              "This is the only button in Keep Wallet that goes online, and it sends nothing about you.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CurrencyPicker(label: String, code: String, tag: String, onPick: (String) -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag(tag)) {
      Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$code · ${CurrencyRates.info(code).name}", maxLines = 1)
      }
      Icon(Icons.Rounded.ExpandMore, contentDescription = null)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.height(360.dp)) {
      CurrencyRates.currencies.forEach { currency ->
        DropdownMenuItem(
          text = { Text("${currency.code} · ${currency.name}") },
          onClick = {
            onPick(currency.code)
            open = false
          },
          modifier = Modifier.testTag("${tag}_${currency.code}")
        )
      }
    }
  }
}

/** Digits with at most one decimal point. */
private fun cleanNumber(text: String): String {
  val out = StringBuilder()
  var dot = false
  for (ch in text.take(14)) {
    if (ch.isDigit()) out.append(ch) else if ((ch == '.' || ch == ',') && !dot) {
      dot = true
      out.append('.')
    }
  }
  return out.toString()
}

private fun format(value: Double, code: String): String {
  val digits = if (code in setOf("JPY", "KRW", "VND", "IDR")) 0 else 2
  return String.format(Locale.US, "%,.${digits}f", value) + " " + code
}

private fun rate(value: Double): String = when {
  value >= 100 -> String.format(Locale.US, "%,.2f", value)
  // at least two decimals, up to four where they carry information
  value >= 1 -> String.format(Locale.US, "%.4f", value).let { text -> text.dropLast(if (text.endsWith("00")) 2 else if (text.endsWith("0")) 1 else 0) }
  else -> String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
}
