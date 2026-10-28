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

/** Why a notification was not turned into an entry; shown to the user so a miss is never a mystery. */
enum class SkipReason(val label: String) {
  EMPTY("Nothing to read"),
  CHAT("From a chat app"),
  SECURITY("A code or security message"),
  FAILED("A failed or reversed payment"),
  UPCOMING("A reminder or something still to come"),
  PROMO("Looks like an offer"),
  NO_AMOUNT("No amount of money in it"),
  NO_DIRECTION("Doesn't say if money left or arrived"),
  BALANCE_ONLY("Only shows a balance")
}

sealed interface ParseResult {
  data class Found(val transaction: ParsedTransaction) : ParseResult
  data class Skipped(val reason: SkipReason) : ParseResult
}

/**
 * Reads amount, direction and merchant out of bank, card, UPI and wallet notifications or SMS. It works entirely on
 * the phone with no network, and it is deliberately careful: it would rather skip a message than invent a
 * transaction, and the user confirms every entry anyway. Every skip carries a reason.
 *
 * What it understands: "debited / credited / spent / paid / received / sent / withdrawn / deposited / refunded /
 * charged / deducted / transferred" and their short forms (Dr, Cr), "paid you" versus "you paid", card names that
 * contain the word credit or debit, Rs / INR / ₹ / $ / € / £ and Indian digit grouping, "Amt: 500", UPI references
 * and VPAs for the merchant, and balance lines that must not be mistaken for the amount.
 */
object NotificationParser {
  // ---- currency and numbers ---------------------------------------------------------------------

  private const val CURRENCY = "(?:rs\\.?|inr|usd|eur|gbp|aed|sgd|cad|aud|[₹\\$€£])"
  private const val NUMBER = "(\\d{1,3}(?:,\\d{2,3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)"

  private val amountPatterns = listOf(
    // Rs 250, Rs.250, Rs:250, INR 1,25,000.00, ₹ 250, $24.00
    Regex("(?<![a-z])$CURRENCY\\s*[:\\-]?\\s*$NUMBER(?![\\d])", RegexOption.IGNORE_CASE),
    // 250 INR, 250.00 rupees, 250/-
    Regex("(?<![\\d.,])$NUMBER\\s*(?:inr|rupees?|usd|dollars?|eur|euros?|gbp|aed|/-)(?![a-z])", RegexOption.IGNORE_CASE),
    // Amt: 250.00, amount of 250, Amount 250
    Regex("\\b(?:amt|amount|amnt|txn amt|value)\\s*(?:of)?\\s*[:\\-]?\\s*(?:$CURRENCY)?\\s*$NUMBER(?![\\d])", RegexOption.IGNORE_CASE)
  )

  /** Words that, right before a figure, mean it is the balance or a limit rather than the transaction. */
  private val balanceBefore = Regex(
    "(?:avl|avail(?:able)?|bal(?:ance)?|limit|outstanding|total due|min(?:imum)? due|credit line)\\W*(?:bal(?:ance)?\\W*)?(?:is|of|:|-)?\\W*$",
    RegexOption.IGNORE_CASE
  )

  // ---- what to skip -----------------------------------------------------------------------------

  private fun rules(vararg patterns: String) = patterns.map { Regex(it, RegexOption.IGNORE_CASE) }

  private val securityRules = rules(
    "\\botp\\b", "one[- ]time password", "verification code", "security code", "\\bpasscode\\b", "\\bcvv\\b",
    "do not share", "don'?t share", "never share", "\\bpin\\b.*(?:expire|valid|generate|reset)", "login (?:attempt|alert)"
  )
  private val failedRules = rules(
    "\\bfailed\\b", "\\bdeclined\\b", "unsuccessful", "not successful", "could not be (?:processed|completed)",
    "unable to (?:process|complete)", "\\bcancell?ed\\b", "\\brejected\\b", "\\bfailure\\b", "transaction (?:was )?not (?:completed|processed)"
  )
  private val upcomingRules = rules(
    "will be (?:debited|credited|charged|deducted)", "to be (?:debited|charged|deducted)", "request(?:ed|s)?\\b.*(?:money|payment|rs|₹|\\$)",
    "payment request", "collect request", "wants you to pay", "is requesting", "\\bpay now\\b", "bill (?:is )?(?:due|generated)",
    "\\bdue (?:on|date|by|in)\\b", "minimum (?:amount )?due", "total (?:amount )?due", "payment (?:is )?due", "\\bupcoming\\b", "\\bscheduled\\b", "refunds? .{0,40}\\binitiated", "\\binitiated .{0,24}refund",
    "auto[- ]?pay (?:is )?(?:set|scheduled|activated)", "\\bmandate\\b.*(?:created|registered|set)", "\\bremind(?:er)?\\b", "statement (?:is )?(?:ready|generated)"
  )
  private val promoRules = rules(
    "\\boffers?\\b", "\\bcoupon", "\\bvouchers?\\b", "reward points?", "\\bget (?:up to |flat )?(?:rs\\.?|₹|\\$|\\d+%)", "\\bcashback (?:up to|of up to)",
    "pre[- ]?approved", "apply now", "limited[- ]time", "\\bwin\\b", "\\bsale\\b", "\\bdiscount\\b", "\\bemi (?:starts|from|offer)", "\\bearn (?:up to )?(?:rs\\.?|₹|\\$)",
    "\\blottery\\b", "\\bclaim (?:now|your)\\b", "\\bunsubscribe\\b", "\\bfree\\b.*(?:rs\\.?|₹|\\$)"
  )

  // ---- direction --------------------------------------------------------------------------------

  private class Cue(val regex: Regex, val type: TxType, val strength: Int)

  private fun cue(pattern: String, type: TxType, strength: Int) = Cue(Regex(pattern, RegexOption.IGNORE_CASE), type, strength)

  /** "credit card" and "debit card" name a card, they do not say which way the money went. */
  private val cardNouns = Regex("\\b(?:credit|debit)\\s+(?:card|cards|limit|line|balance|bal)\\b|\\bavailable credit\\b|\\bcredit\\s*a/?c\\b", RegexOption.IGNORE_CASE)

  /** "paid you", "sent you": the other person paid, so it is money in. Checked first and masked out afterwards. */
  private val paidYou = Regex("\\b(?:paid|sent|transferred|has sent|has paid|just paid|just sent)\\s+(?:you|u)\\b", RegexOption.IGNORE_CASE)

  private val cues = listOf(
    cue("\\bdebited\\b", TxType.EXPENSE, 4),
    cue("\\bcredited\\b", TxType.INCOME, 4),
    cue("\\b(?:debit|dr)\\s*(?:of|alert|txn|transaction|[:-])", TxType.EXPENSE, 3),
    cue("\\b(?:credit|cr)\\s*(?:of|alert|txn|transaction|[:-])", TxType.INCOME, 3),
    cue("\\bupi/dr/|\\bdr\\.?\\b(?=\\s*(?:to|at|rs|inr|₹|\\$))", TxType.EXPENSE, 3),
    cue("\\bupi/cr/|\\bcr\\.?\\b(?=\\s*(?:from|by|rs|inr|₹|\\$))", TxType.INCOME, 3),
    cue("\\b(?:withdrawn|withdrawal|deducted|spent|charged|purchase[ds]?)\\b", TxType.EXPENSE, 3),
    cue("\\b(?:deposited|refund(?:ed)?|reversed|salary|received|cashback credited|interest credited)\\b", TxType.INCOME, 3),
    cue("\\b(?:paid|payment of|payment to|sent|transferred to|transfer(?:red)? of|used at|txn of|trf to|bill payment|recharge of)\\b", TxType.EXPENSE, 2),
    cue("\\bcharge of\\b|\\ba charge\\b", TxType.EXPENSE, 3),
    cue("\\bwas used (?:for|at|on)\\b|\\bused (?:for|at)\\b|\\busing (?:your )?[a-z ]{0,30}card\\b|\\b(?:txn|transaction) (?:of|for|amount)\\b", TxType.EXPENSE, 2),
    cue("\\b(?:payment received|money received|received from|credited to)\\b", TxType.INCOME, 3),
    cue("\\b(?:debit|debits)\\b", TxType.EXPENSE, 1),
    cue("\\b(?:credit|credits)\\b", TxType.INCOME, 1)
  )

  private fun direction(raw: String): TxType? {
    var text = cardNouns.replace(raw, " card ")
    val incoming = paidYou.containsMatchIn(text)
    text = paidYou.replace(text, " ")
    var best: Triple<TxType, Int, Int>? = null // type, strength, position
    for (cue in cues) {
      val hit = cue.regex.find(text) ?: continue
      val candidate = Triple(cue.type, cue.strength, hit.range.first)
      val current = best
      if (current == null || candidate.second > current.second || (candidate.second == current.second && candidate.third < current.third)) best = candidate
    }
    if (incoming && (best == null || best.second < 4)) return TxType.INCOME
    return best?.first
  }

  // ---- merchants ----------------------------------------------------------------------------------

  private const val NAME = "([\\p{L}\\p{N}][\\p{L}\\p{N}&'’.\\- ]{1,32}?)"
  private const val NAME_END = "(?=\\s+(?:on|via|using|from|ref\\w*|bal|avl|upi|dated|vide|at|a/c|ac|acct|account|card|txn|transaction|is|has|was|for|towards|through|thru|info|umn)\\b|[.,;():!]|\\s*\$)"

  private val vpaPattern = Regex("\\b(?:vpa|upi id|upi)[:\\s-]+([A-Za-z0-9._-]{2,})@[A-Za-z0-9]+", RegexOption.IGNORE_CASE)
  private val upiInfo = Regex("\\bupi[/-](?:dr|cr)?[/-]?\\d{6,}[/-]([A-Za-z][A-Za-z0-9 &._']{2,30}?)(?:[/-]|\\s{2,}|\$)", RegexOption.IGNORE_CASE)
  private val achInfo = Regex("\\b(?:ach|nach|ecs|neft|imps|rtgs)\\s*(?:d|c|dr|cr)?[-/ ]+([A-Za-z][A-Za-z0-9 &._']{2,30}?)(?:[-/]|\\s{2,}|\$)", RegexOption.IGNORE_CASE)
  private val posInfo = Regex("\\bpos\\s*\\d*\\s+([A-Za-z][A-Za-z0-9 &._']{2,30}?)(?:\\s+on\\b|[.,]|\$)", RegexOption.IGNORE_CASE)
  // "at / to / from" name the other side far more reliably than "for", which is tried last.
  private val debitMerchants = listOf(
    Regex("\\b(?:at|to|towards|paid to|sent to|trf to)\\s+$NAME$NAME_END", RegexOption.IGNORE_CASE),
    Regex("\\bfor\\s+$NAME$NAME_END", RegexOption.IGNORE_CASE)
  )
  private val creditMerchants = listOf(
    Regex("\\b(?:from|by)\\s+$NAME$NAME_END", RegexOption.IGNORE_CASE),
    Regex("\\b(?:as|for)\\s+$NAME$NAME_END", RegexOption.IGNORE_CASE)
  )
  private val rejectMerchant = Regex(
    "^(?:your|you|a/c|ac|acct|account|bank|xx+\\d*|rs|inr|usd|eur|gbp|the|this|my|upi|card|ending|no|id|info|ref|txn|transaction|credit|debit|wallet|payment|bill|order|using|on|dated|date|via|vide|at|to|from|by|through|thru|neft|imps|rtgs)\\b|^\\d|\\bxx+\\d|\\d{5,}",
    RegexOption.IGNORE_CASE
  )
  /** Sender names such as "AD-HDFCBK" or "VM-SBIUPI" are not merchants. */
  private val senderId = Regex("^[A-Z]{2}-[A-Z0-9]{4,8}$")
  private val genericTitles = Regex(
    "^(?:upi|alert|alerts|transaction|txn|bank|notification|message|messages|sms|payment|payments|wallet|account|credit|debit|new message|dear (?:customer|user)|hi|hello)\\b",
    RegexOption.IGNORE_CASE
  )

  // ---- the entry point --------------------------------------------------------------------------

  /** Convenience: the transaction, or null when the text is not one. */
  fun parse(title: String?, text: String?): ParsedTransaction? =
    (analyse(title, text) as? ParseResult.Found)?.transaction

  fun analyse(title: String?, text: String?): ParseResult {
    val full = listOfNotNull(title?.trim(), text?.trim()).filter { it.isNotEmpty() }
      .joinToString(". ").replace(Regex("\\s+"), " ")
    if (full.isEmpty()) return ParseResult.Skipped(SkipReason.EMPTY)

    // A message with a clear "debited/credited" and a figure is a transaction even if it also carries a footer such
    // as "call us if not you" or "bal"; the skip lists only decide when that clear signal is missing.
    securityRules.firstOrNull { it.containsMatchIn(full) }?.let { return ParseResult.Skipped(SkipReason.SECURITY) }
    failedRules.firstOrNull { it.containsMatchIn(full) }?.let { return ParseResult.Skipped(SkipReason.FAILED) }
    upcomingRules.firstOrNull { it.containsMatchIn(full) }?.let { return ParseResult.Skipped(SkipReason.UPCOMING) }

    val type = direction(full)
    val amount = findAmount(full)
    if (type == null || amount == null) {
      return ParseResult.Skipped(
        when {
          promoRules.any { it.containsMatchIn(full) } -> SkipReason.PROMO
          amount == null && balanceWord.containsMatchIn(full) && hasAnyFigure(full) -> SkipReason.BALANCE_ONLY
          amount == null -> SkipReason.NO_AMOUNT
          else -> SkipReason.NO_DIRECTION
        }
      )
    }
    // Promotional wording only wins when the direction cue is weak (a sale notice that says "paid" in passing).
    if (promoRules.any { it.containsMatchIn(full) } && !strongCue(full)) return ParseResult.Skipped(SkipReason.PROMO)

    val merchant = findMerchant(full, type) ?: title?.let(::cleanTitle)
    return ParseResult.Found(ParsedTransaction(amount, type, merchant))
  }

  private fun strongCue(text: String): Boolean = Regex("\\b(?:debited|credited|withdrawn|deposited|spent|received|deducted)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)

  private fun hasAnyFigure(text: String): Boolean = amountPatterns.any { it.containsMatchIn(text) }

  private fun findAmount(text: String): Long? {
    val candidates = amountPatterns.flatMap { pattern ->
      pattern.findAll(text).map { it.range.first to it.groupValues[1] }.toList()
    }.sortedBy { it.first }
    for ((start, digits) in candidates) {
      val before = text.substring(maxOf(0, start - 26), start)
      if (balanceBefore.containsMatchIn(before)) continue
      // "45,50" with one comma and two digits after it is a European decimal; "1,000" and "1,00,000" are groups.
      val normalised = if (decimalComma.matches(digits)) digits.replace(',', '.') else digits.replace(",", "")
      val value = try {
        BigDecimal(normalised)
      } catch (_: NumberFormatException) {
        continue
      }
      val minor = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2)
      if (minor.signum() > 0 && minor < BigDecimal("100000000000")) return minor.toLong()
    }
    return null
  }

  private val decimalComma = Regex("^\\d{1,3},\\d{2}$")
  private val whoPaidYou =Regex("([\\p{L}][\\p{L}.' -]{1,28}?)\\s+(?:has\\s+)?(?:just\\s+)?(?:paid|sent|transferred)\\s+(?:you|u)\\b", RegexOption.IGNORE_CASE)
  private val balanceWord = Regex("\\b(?:bal(?:ance)?|avl|available)\\b", RegexOption.IGNORE_CASE)
  private val atmWord = Regex("\\batm\\b", RegexOption.IGNORE_CASE)

  private fun findMerchant(text: String, type: TxType): String? {
    if (type == TxType.INCOME) {
      whoPaidYou.find(text)?.groupValues?.get(1)?.let { name ->
        val clean = pretty(clean(name.substringAfterLast(". ")))
        if (clean.length > 2 && !rejectMerchant.containsMatchIn(clean)) return clean
      }
    }
    if (type == TxType.EXPENSE && atmWord.containsMatchIn(text)) return "ATM withdrawal"
    vpaPattern.find(text)?.groupValues?.get(1)?.let { handle ->
      val clean = fromHandle(handle)
      if (clean.length > 2 && !rejectMerchant.containsMatchIn(clean)) return clean
    }
    for (pattern in listOf(upiInfo, achInfo, posInfo)) {
      pattern.find(text)?.groupValues?.get(1)?.let { raw ->
        val clean = pretty(clean(raw))
        if (clean.length > 2 && !rejectMerchant.containsMatchIn(clean)) return clean
      }
    }
    val patterns: List<Regex> = if (type == TxType.EXPENSE) debitMerchants else creditMerchants
    for (pattern in patterns) {
      for (match in pattern.findAll(text)) {
        val candidate = pretty(clean(match.groupValues[1]))
        if (candidate.length > 2 && !rejectMerchant.containsMatchIn(candidate)) return candidate
      }
    }
    return null
  }

  /** "john.doe" or "swiggy" from a UPI handle, as a name. */
  private fun fromHandle(handle: String): String =
    clean(handle).split('.', '_', '-').filter { it.isNotEmpty() && !it.all(Char::isDigit) }
      .joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.titlecase() } }

  private fun cleanTitle(title: String): String? {
    val candidate = clean(title)
    if (senderId.matches(title.trim()) || genericTitles.containsMatchIn(candidate)) return null
    return pretty(candidate).takeIf { it.length > 2 && !rejectMerchant.containsMatchIn(it) }
  }

  private fun clean(raw: String): String =
    raw.trim().replace(Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N})]+$"), "").take(30).trim()

  /** "AMAZON PAY INDIA" becomes "Amazon Pay India"; mixed case is left as written. */
  private fun pretty(name: String): String {
    val letters = name.filter { it.isLetter() }
    if (letters.length < 3 || letters.any { it.isLowerCase() }) return name
    return name.lowercase().split(' ').filter { it.isNotEmpty() }.joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
  }
}
