package com.keepwallet.app.domain

import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.TxType

/** What was understood from a spoken or typed sentence such as "spent 250 on lunch at Subway". */
data class VoiceEntry(
  val amountMinor: Long,
  val type: TxType,
  val categoryId: Long?,
  val merchant: String?
)

/**
 * Reads a sentence into an entry. Runs on the phone and only needs the text the system recogniser hands back, so it
 * works offline and can be tested without a microphone.
 */
object VoiceParser {
  private val incomeWords = listOf("earned", "received", "income", "salary", "got paid", "credited", "refund", "deposit", "bonus", "cashback", "paid me", "sold")
  private val currencyWords = setOf("rupees", "rupee", "rs", "dollars", "dollar", "bucks", "euros", "euro", "pounds", "pound", "yen", "inr", "usd", "eur", "gbp")
  private val fillers = setOf("i", "just", "spent", "spend", "paid", "pay", "bought", "buy", "got", "received", "earned", "add", "an", "a", "the", "my", "of", "worth", "expense", "income", "entry", "new", "me", "some", "and")
  private val prepositions = listOf(" at ", " from ", " to ", " on ", " for ", " in ")

  private val ones = mapOf(
    "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
    "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
    "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
  )
  private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)

  /** Words that hint at a category name even when the category is called something else. */
  private val hints: Map<String, List<String>> = mapOf(
    "dining" to listOf("lunch", "dinner", "breakfast", "coffee", "tea", "restaurant", "cafe", "snack", "pizza", "burger", "meal", "food", "drinks"),
    "groceries" to listOf("grocery", "groceries", "vegetables", "fruit", "supermarket", "milk", "bread"),
    "transport" to listOf("bus", "metro", "train", "taxi", "uber", "ola", "cab", "fuel", "petrol", "diesel", "parking", "toll", "auto", "ride", "flight"),
    "subscriptions" to listOf("netflix", "spotify", "subscription", "prime", "youtube", "hotstar"),
    "housing" to listOf("rent", "electricity", "water", "gas", "maintenance", "internet", "wifi"),
    "shopping" to listOf("shopping", "clothes", "shoes", "amazon", "flipkart", "shirt"),
    "salary" to listOf("salary", "paycheck", "wages"),
    "health" to listOf("doctor", "medicine", "pharmacy", "hospital", "medical")
  )

  fun parse(sentence: String, categories: List<CategoryEntity>): VoiceEntry? {
    val text = sentence.lowercase().replace(Regex("""(?<!\d),|,(?!\d)|;"""), " ").replace(Regex("""\s+"""), " ").trim()
    val amount = amount(text) ?: return null
    if (amount <= 0) return null
    val income = incomeWords.any { it in text }
    val type = if (income) TxType.INCOME else TxType.EXPENSE
    val candidates = categories.filter { it.type == type }
    return VoiceEntry(amount, type, category(text, candidates)?.id, merchant(text))
  }

  // ---- amount --------------------------------------------------------------------------------------

  private fun amount(text: String): Long? {
    // "2k", "2.5k", "1,250.50", "250"
    Regex("""(\d+(?:\.\d+)?)\s?k\b""").find(text)?.let { m -> m.groupValues[1].toDoubleOrNull()?.let { return Money.toMinor(it * 1000) } }
    Regex("""\d{1,3}(?:,\d{3})+(?:\.\d+)?|\d+(?:\.\d+)?""").find(text.replace(" ", " "))?.let { m ->
      val value = Money.parseMinor(m.value)
      if (value > 0) return value
    }
    val words = text.split(' ').filter { it.isNotEmpty() }.map { it.trim('.', '!', '?') }.filter { it !in currencyWords }
    var best: Long? = null
    var i = 0
    while (i < words.size) {
      val (value, used) = numberWords(words, i)
      if (used > 0) {
        best = value * 100
        break
      }
      i++
    }
    return best
  }

  /** Reads "twenty five", "one hundred and fifty", "two thousand" from position [start]; returns (value, words used). */
  private fun numberWords(words: List<String>, start: Int): Pair<Long, Int> {
    var total = 0L
    var current = 0L
    var used = 0
    var i = start
    while (i < words.size) {
      val w = words[i]
      when {
        w == "and" && used > 0 && i + 1 < words.size && (words[i + 1] in ones || words[i + 1] in tens) -> Unit
        w in ones -> current += ones.getValue(w)
        w in tens -> current += tens.getValue(w)
        w == "a" && used == 0 && i + 1 < words.size && words[i + 1] in setOf("hundred", "thousand") -> current += 1
        w == "hundred" && (used > 0) -> current = (if (current == 0L) 1 else current) * 100
        w == "thousand" && (used > 0) -> {
          total += (if (current == 0L) 1 else current) * 1000
          current = 0
        }
        else -> break
      }
      used++
      i++
    }
    return if (used == 0 || (used == 1 && words[start] == "and")) 0L to 0 else (total + current) to used
  }

  // ---- category and merchant -----------------------------------------------------------------------

  private fun category(text: String, candidates: List<CategoryEntity>): CategoryEntity? {
    val spoken = text.split(' ').map { it.trim('.', '!', '?') }.toSet()
    // 1. The category's own name, or a word that starts like it ("grocer" -> Groceries).
    candidates.firstOrNull { c ->
      val name = c.name.lowercase()
      name in text || spoken.any { it.length >= 4 && (name.startsWith(it) || it.startsWith(name.take(5))) }
    }?.let { return it }
    // 2. Everyday words that point at the usual category names.
    for ((categoryName, words) in hints) {
      if (words.any { it in spoken }) candidates.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }?.let { return it }
    }
    return null
  }

  private fun merchant(text: String): String? {
    // After "at", "from", "to", "on" or "for": the thing that was bought or the place it was bought at.
    val padded = " $text "
    val after = prepositions.mapNotNull { p -> padded.indexOf(p).takeIf { it >= 0 }?.let { it to padded.substring(it + p.length) } }
      .minByOrNull { it.first }?.second
    val source = after ?: text
    val words = source.split(' ').map { it.trim('.', '!', '?') }.filter { it.isNotEmpty() && it !in fillers && it !in currencyWords && !it.first().isDigit() && it !in ones && it !in tens && it != "hundred" && it != "thousand" }
    val phrase = words.take(3).joinToString(" ")
    if (phrase.isBlank()) return null
    return phrase.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }.take(30)
  }
}
