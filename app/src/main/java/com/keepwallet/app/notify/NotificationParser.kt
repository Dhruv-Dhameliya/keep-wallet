package com.keepwallet.app.notify

import com.keepwallet.app.data.TxType
import java.math.BigDecimal
import java.math.RoundingMode

data class ParsedTransaction(
  val amountMinor: Long,
  val type: TxType,
  /** Best guess at who was paid or who paid; null when the text doesn't say. */
  val merchant: String?
)

/**
 * Extracts amount, direction and merchant from bank / UPI / card notification text.
 * Deliberately conservative: it would rather miss a message than invent a transaction,
 * and the user confirms every detected entry anyway.
 */
object NotificationParser {
  private const val CURRENCY = "(?:rs\\.?|inr|usd|eur|gbp|[₹\\$€£¥])"
  private const val NUMBER = "(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)"

  private val amountPatterns = listOf(
    Regex("(?<![a-z])$CURRENCY\\s*$NUMBER", RegexOption.IGNORE_CASE),
    Regex("(?<![\\d.,])$NUMBER\\s*(?:inr|rupees|usd|eur|gbp)\\b", RegexOption.IGNORE_CASE)
  )

  private val debitWords = Regex(
    "\\b(?:debited|spent|paid|sent|withdrawn|purchase[d]?|charged|deducted|payment of)\\b", RegexOption.IGNORE_CASE
  )
  private val creditWords = Regex(
    "\\b(?:credited|received|deposited|refund(?:ed)?|salary)\\b", RegexOption.IGNORE_CASE
  )

  /** Messages that look financial but are not completed transactions. */
  private val ignorePatterns = listOf(
    "\\botp\\b", "one[- ]time password", "verification code", "\\bfailed\\b", "\\bdeclined\\b", "unsuccessful",
    "will be (?:debited|credited|charged)", "request(?:ed|s)?\\b.*(?:money|payment|rs|₹|\\$)",
    "payment request", "collect request", "\\boffer\\b", "\\bcoupon", "\\bvoucher", "reward points?",
    "bill (?:is )?due", "minimum (?:amount )?due", "due (?:on|date)", "\\bget (?:up to )?(?:rs|₹|\\$)"
  ).map { Regex(it, RegexOption.IGNORE_CASE) }

  private val balanceWords = Regex("(?:avl|avail(?:able)?|bal(?:ance)?|limit|outstanding)\\W*(?:bal(?:ance)?\\W*)?$", RegexOption.IGNORE_CASE)

  private val vpaPattern = Regex("\\bvpa\\s+([A-Za-z0-9._-]+)@[A-Za-z0-9]+", RegexOption.IGNORE_CASE)

  private const val MERCHANT_BODY = "([\\p{L}\\p{N}][\\p{L}\\p{N}&'’.\\- ]{1,30}?)"
  private const val MERCHANT_END = "(?=\\s+(?:on|via|using|from|ref|bal|avl|upi|dated|vide|at)\\b|[.,;(!]|\\s*\$)"
  private val debitMerchant = Regex("\\b(?:at|to|for|towards)\\s+$MERCHANT_BODY$MERCHANT_END", RegexOption.IGNORE_CASE)
  private val creditMerchant = Regex("\\b(?:from|as|for)\\s+$MERCHANT_BODY$MERCHANT_END", RegexOption.IGNORE_CASE)
  private val rejectMerchant = Regex(
    "^(?:your|you|a/c|ac|acct|account|bank|xx+\\d*|rs|inr|usd|eur|gbp|the|this|my|upi|card|ending|no|id)\\b|^\\d",
    RegexOption.IGNORE_CASE
  )

  fun parse(title: String?, text: String?): ParsedTransaction? {
    val full = listOfNotNull(title?.trim(), text?.trim()).filter { it.isNotEmpty() }
      .joinToString(". ").replace(Regex("\\s+"), " ")
    if (full.isEmpty()) return null
    if (ignorePatterns.any { it.containsMatchIn(full) }) return null

    val type = direction(full) ?: return null
    val amountMinor = findAmount(full) ?: return null
    val merchant = findMerchant(full, type) ?: title?.let(::cleanTitle)
    return ParsedTransaction(amountMinor, type, merchant)
  }

  private fun direction(text: String): TxType? {
    val debit = debitWords.find(text)?.range?.first
    val credit = creditWords.find(text)?.range?.first
    return when {
      debit == null && credit == null -> null
      debit == null -> TxType.INCOME
      credit == null -> TxType.EXPENSE
      debit < credit -> TxType.EXPENSE
      else -> TxType.INCOME
    }
  }

  private fun findAmount(text: String): Long? {
    val candidates = amountPatterns.flatMap { pattern ->
      pattern.findAll(text).map { it.range.first to it.groupValues[1] }.toList()
    }.sortedBy { it.first }
    for ((start, digits) in candidates) {
      val before = text.substring(maxOf(0, start - 24), start)
      if (balanceWords.containsMatchIn(before)) continue
      val value = try {
        BigDecimal(digits.replace(",", ""))
      } catch (_: NumberFormatException) {
        continue
      }
      val minor = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2)
      if (minor.signum() > 0 && minor < BigDecimal("100000000000")) return minor.toLong()
    }
    return null
  }

  private fun findMerchant(text: String, type: TxType): String? {
    vpaPattern.find(text)?.groupValues?.get(1)?.let { handle ->
      val clean = clean(handle)
      if (clean.length > 2) return clean
    }
    val pattern = if (type == TxType.EXPENSE) debitMerchant else creditMerchant
    for (match in pattern.findAll(text)) {
      val candidate = clean(match.groupValues[1])
      if (candidate.length > 2 && !rejectMerchant.containsMatchIn(candidate)) return candidate
    }
    return null
  }

  private fun cleanTitle(title: String): String? {
    val candidate = clean(title)
    return candidate.takeIf { it.length > 2 && !rejectMerchant.containsMatchIn(it) }
  }

  private fun clean(raw: String): String =
    raw.trim().replace(Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N})]+$"), "").take(30).trim()
}
