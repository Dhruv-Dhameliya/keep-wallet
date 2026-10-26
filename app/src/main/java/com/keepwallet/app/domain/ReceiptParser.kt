package com.keepwallet.app.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** What could be read from a receipt photo; every field is optional because printed receipts vary wildly. */
data class ReceiptScan(val amountMinor: Long?, val merchant: String?, val date: LocalDate?)

/**
 * Turns the text found on a receipt photo into a total, a shop name and a date. Everything here is plain text
 * handling, so it runs on the phone and can be tested without a camera.
 */
object ReceiptParser {
  private val amountToken = Regex("""(?<![\d.,])\d{1,3}(?:[,. ]\d{3})*[.,]\d{2}(?![\d])|(?<![\d.,])\d+[.,]\d{2}(?![\d])""")
  private val wholeToken = Regex("""(?<![\d.,])\d{1,7}(?![\d.,])""")

  private val strongTotal = listOf("grand total", "total due", "amount due", "balance due", "net payable", "net amount", "amount payable", "total payable", "total amount")
  private val plainTotal = listOf("total")
  private val weakTotal = listOf("amount", "payable", "sum")
  private val notTotal = listOf("subtotal", "sub total", "sub-total", "tax", "gst", "vat", "cgst", "sgst", "change", "cash", "tender", "discount", "saving", "tip", "round", "points", "items", "qty", "quantity")
  private val notMerchant = listOf(
    "receipt", "invoice", "tax", "bill", "tel", "phone", "ph:", "mob", "www", "http", "gst", "vat", "date", "time", "order",
    "table", "cashier", "thank", "welcome", "customer", "copy", "address", "street", "road", "total", "amount", "qty"
  )

  fun parse(text: String, today: LocalDate = LocalDate.now()): ReceiptScan {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    return ReceiptScan(amount(lines), merchant(lines), date(text, today))
  }

  // ---- amount --------------------------------------------------------------------------------------

  /** "1,234.50" and "1.234,50" both become 123450. Returns null when the token is not a price. */
  internal fun toMinor(token: String): Long? {
    val clean = token.replace(" ", "")
    val lastDot = clean.lastIndexOf('.')
    val lastComma = clean.lastIndexOf(',')
    val decimalAt = maxOf(lastDot, lastComma)
    return if (decimalAt >= 0 && clean.length - decimalAt - 1 == 2) {
      val whole = clean.substring(0, decimalAt).filter { it.isDigit() }.ifEmpty { "0" }
      val cents = clean.substring(decimalAt + 1)
      (whole.toLongOrNull() ?: return null) * 100 + cents.toLong()
    } else {
      clean.filter { it.isDigit() }.toLongOrNull()?.times(100)
    }
  }

  private fun amountsIn(line: String): List<Long> = amountToken.findAll(line).mapNotNull { toMinor(it.value) }.toList()

  private fun amount(lines: List<String>): Long? {
    // Words that mean "not the total" are blanked out first, so "Sub Total" no longer counts as "Total".
    fun masked(line: String): String = notTotal.fold(line.lowercase()) { acc, bad -> acc.replace(bad, " ".repeat(bad.length)) }

    fun tier(keys: List<String>): Long? {
      val found = mutableListOf<Long>()
      lines.forEachIndexed { index, line ->
        val lower = masked(line)
        val key = keys.firstOrNull { it in lower } ?: return@forEachIndexed
        var values = amountsIn(line)
        if (values.isEmpty()) values = lines.getOrNull(index + 1)?.let(::amountsIn).orEmpty()
        if (values.isEmpty()) {
          // "TOTAL 450": a whole number straight after the word.
          wholeToken.findAll(lower.substringAfter(key)).firstOrNull()?.let { whole -> toMinor(whole.value)?.let { values = listOf(it) } }
        }
        found += values
      }
      return found.maxOrNull()
    }
    tier(strongTotal)?.let { return it }
    tier(plainTotal)?.let { return it }
    tier(weakTotal)?.let { return it }
    // No label at all: the biggest price on the receipt is almost always the total.
    return lines.filter { line -> notTotal.none { it in line.lowercase() } }.flatMap(::amountsIn).maxOrNull()
  }

  // ---- merchant ------------------------------------------------------------------------------------

  private fun merchant(lines: List<String>): String? {
    val line = lines.take(8).firstOrNull { raw ->
      val lower = raw.lowercase()
      val letters = raw.count { it.isLetter() }
      letters >= 3 && letters >= raw.length / 2 && notMerchant.none { it in lower } && amountsIn(raw).isEmpty()
    } ?: return null
    val cleaned = line.trim(' ', '*', '-', '=', '_', '#', '.', ',').take(40)
    val shout = cleaned.none { it.isLowerCase() }
    return if (shout) cleaned.lowercase().split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } } else cleaned
  }

  // ---- date ----------------------------------------------------------------------------------------

  private val months = (1..12).associateBy { java.time.Month.of(it).getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase() }
  private val numeric = Regex("""(?<!\d)(\d{1,4})[/\-.](\d{1,2})[/\-.](\d{2,4})(?!\d)""")
  private val named = Regex("""(?i)(?<!\d)(\d{1,2})[ \-]([a-z]{3})[a-z]*[ \-,.]*(\d{2,4})|([a-z]{3})[a-z]*[ .]+(\d{1,2})(?:st|nd|rd|th)?,?[ ]+(\d{4})""")

  private fun year(raw: String): Int = raw.toInt().let { if (raw.length <= 2) 2000 + it else it }

  private fun date(text: String, today: LocalDate): LocalDate? {
    fun sane(d: LocalDate?): LocalDate? = d?.takeIf { it.year >= 2000 && !it.isAfter(today.plusDays(1)) }
    numeric.find(text)?.let { m ->
      val (a, b, c) = m.destructured
      val first = a.toInt()
      val second = b.toInt()
      val tries = if (a.length == 4) listOf(Triple(first, second, c.toInt()).let { (y, mo, d) -> runCatching { LocalDate.of(y, mo, d) }.getOrNull() })
      else {
        val y = year(c)
        val dayFirst = runCatching { LocalDate.of(y, second, first) }.getOrNull()
        val monthFirst = runCatching { LocalDate.of(y, first, second) }.getOrNull()
        if (first > 12) listOf(dayFirst) else if (second > 12) listOf(monthFirst) else listOf(dayFirst, monthFirst)
      }
      tries.firstNotNullOfOrNull { sane(it) }?.let { return it }
    }
    named.find(text)?.let { m ->
      val g = m.groupValues
      val result = if (g[1].isNotEmpty()) {
        val month = months[g[2].lowercase()]
        month?.let { runCatching { LocalDate.of(year(g[3]), it, g[1].toInt()) }.getOrNull() }
      } else {
        val month = months[g[4].lowercase()]
        month?.let { runCatching { LocalDate.of(g[6].toInt(), it, g[5].toInt()) }.getOrNull() }
      }
      sane(result)?.let { return it }
    }
    return null
  }
}
