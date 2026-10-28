package com.keepwallet.app.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.abs

/** Helpers for amounts stored as integer minor units (1/100 of the currency unit). */
object Money {
  val presetSymbols = listOf("₹", "$", "€", "£", "¥")

  fun toMinor(amount: Double): Long =
    BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()

  /** Parses user-typed text such as "12", "12.5" or "12.50"; anything invalid is 0. */
  fun parseMinor(text: String): Long {
    val clean = text.trim().replace(",", "")
    if (clean.isEmpty()) return 0
    return try {
      BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong().coerceAtLeast(0)
    } catch (_: NumberFormatException) {
      0
    }
  }

  /** Plain editable text for a stored amount, e.g. 1250 -> "12.5", 1200 -> "12", 0 -> "0". */
  fun toEditText(minor: Long): String {
    val value = BigDecimal(minor).movePointLeft(2).stripTrailingZeros()
    return if (value.signum() == 0) "0" else value.toPlainString()
  }

  /** "₹1,234.50". Negative values get a leading minus sign. */
  fun format(minor: Long, symbol: String, forceSign: Boolean = false): String =
    sign(minor, forceSign) + symbol + number(abs(minor), symbol)

  /** "₹1,235" – rounded to whole units for headline figures. */
  fun formatWhole(minor: Long, symbol: String): String {
    val whole = BigDecimal(abs(minor)).movePointLeft(2).setScale(0, RoundingMode.HALF_UP)
    return (if (minor < 0 && whole.signum() != 0) "-" else "") + symbol + wholeNumber(whole, symbol)
  }

  /** Short axis label such as 1.2k. */
  fun formatCompact(minor: Long, symbol: String): String {
    val units = abs(minor) / 100.0
    val text = when {
      units >= 1_000_000 -> trimDecimals(units / 1_000_000) + "M"
      units >= 10_000 -> trimDecimals(units / 1_000) + "k"
      units >= 1_000 -> String.format(Locale.US, "%,.0f", units)
      else -> String.format(Locale.US, "%.0f", units)
    }
    return symbol + text
  }

  /** The rupee is where a new install starts, whatever the phone's region says; the first-run setup or Settings changes it. */
  fun defaultSymbol(): String = presetSymbols.first()

  private fun sign(minor: Long, forceSign: Boolean) = when {
    minor < 0 -> "-"
    forceSign && minor > 0 -> "+"
    else -> ""
  }

  private fun number(minorAbs: Long, symbol: String): String =
    group((minorAbs / 100).toString(), symbol) + "." + (minorAbs % 100).toString().padStart(2, '0')

  private fun wholeNumber(value: BigDecimal, symbol: String): String = group(value.toBigInteger().toString(), symbol)

  private fun group(digits: String, symbol: String): String =
    if (symbol == "₹") groupIndian(digits) else groupWestern(digits)

  // java.text only supports one grouping size, so the lakh/crore pattern (12,34,567) is done by hand.
  private fun groupIndian(digits: String): String {
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
    return head + "," + digits.takeLast(3)
  }

  private fun groupWestern(digits: String): String = digits.reversed().chunked(3).joinToString(",").reversed()

  private fun trimDecimals(value: Double): String {
    val text = String.format(Locale.US, "%.1f", value)
    return if (text.endsWith(".0")) text.dropLast(2) else text
  }
}
