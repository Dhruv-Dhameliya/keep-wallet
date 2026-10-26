package com.keepwallet.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.keepwallet.app.data.CurrencyRates
import com.keepwallet.app.domain.Money
import com.keepwallet.app.ui.components.FormSheet
import java.util.Locale

/**
 * "I paid this in euros": pick the currency, type what was paid, and the amount in your own currency is worked out
 * from the converter's rates. The original amount is kept with the entry.
 */
@Composable
fun ForeignSheet(
  homeCode: String,
  homeSymbol: String,
  rates: Map<String, Double>,
  initialCode: String?,
  initialMinor: Long?,
  onApply: (code: String, foreignMinor: Long, homeMinor: Long) -> Unit,
  onDismiss: () -> Unit
) {
  var code by remember { mutableStateOf(initialCode ?: if (homeCode == "USD") "EUR" else "USD") }
  var text by remember { mutableStateOf(initialMinor?.let(Money::toEditText).orEmpty()) }
  var menu by remember { mutableStateOf(false) }
  val foreignMinor = Money.parseMinor(text)
  val factor = CurrencyRates.convert(1.0, code, homeCode, rates)
  val homeMinor = factor?.let { Money.toMinor(foreignMinor / 100.0 * it) }
  val info = CurrencyRates.info(code)
  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text("Paid in another currency") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
          OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth().testTag("foreign_currency")) {
            Text("${info.code} · ${info.name}", modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Rounded.ExpandMore, contentDescription = null)
          }
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.height(320.dp)) {
            CurrencyRates.currencies.filter { it.code != homeCode }.forEach { currency ->
              DropdownMenuItem(
                text = { Text("${currency.code} · ${currency.name}") },
                onClick = {
                  code = currency.code
                  menu = false
                },
                modifier = Modifier.testTag("foreign_currency_${currency.code}")
              )
            }
          }
        }
        OutlinedTextField(
          value = text,
          onValueChange = { text = sanitizeAmount(it) },
          label = { Text("Amount in ${info.code}") },
          prefix = { Text(info.symbol + " ") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          modifier = Modifier.fillMaxWidth().testTag("foreign_amount")
        )
        if (homeMinor != null && factor != null) {
          Text(
            "About ${Money.format(homeMinor, homeSymbol)} at 1 ${info.code} = ${homeSymbol}${String.format(Locale.US, "%,.2f", factor)}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag("foreign_result")
          )
        }
        Text(
          "Uses the rates from the currency converter. Update them there for today's numbers.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = { if (homeMinor != null && homeMinor > 0 && foreignMinor > 0) onApply(code, foreignMinor, homeMinor) },
        enabled = homeMinor != null && homeMinor > 0 && foreignMinor > 0,
        modifier = Modifier.testTag("foreign_apply")
      ) { Text("Use this amount") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}
