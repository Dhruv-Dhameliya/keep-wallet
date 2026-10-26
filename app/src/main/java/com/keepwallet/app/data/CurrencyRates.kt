package com.keepwallet.app.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One currency the converter offers. */
data class CurrencyInfo(val code: String, val name: String, val symbol: String)

/** Exchange rates as units of each currency per one US dollar, plus where they came from. */
data class RateTable(val perUsd: Map<String, Double>, val updatedAt: Long?, val builtIn: Boolean)

object CurrencyRates {
  val currencies: List<CurrencyInfo> = listOf(
    CurrencyInfo("USD", "US dollar", "$"), CurrencyInfo("EUR", "Euro", "€"), CurrencyInfo("GBP", "British pound", "£"),
    CurrencyInfo("INR", "Indian rupee", "₹"), CurrencyInfo("JPY", "Japanese yen", "¥"), CurrencyInfo("AUD", "Australian dollar", "A$"),
    CurrencyInfo("CAD", "Canadian dollar", "C$"), CurrencyInfo("CHF", "Swiss franc", "Fr"), CurrencyInfo("CNY", "Chinese yuan", "¥"),
    CurrencyInfo("SGD", "Singapore dollar", "S$"), CurrencyInfo("AED", "UAE dirham", "د.إ"), CurrencyInfo("SAR", "Saudi riyal", "﷼"),
    CurrencyInfo("KRW", "South Korean won", "₩"), CurrencyInfo("MXN", "Mexican peso", "$"), CurrencyInfo("BRL", "Brazilian real", "R$"),
    CurrencyInfo("ZAR", "South African rand", "R"), CurrencyInfo("NZD", "New Zealand dollar", "NZ$"), CurrencyInfo("SEK", "Swedish krona", "kr"),
    CurrencyInfo("NOK", "Norwegian krone", "kr"), CurrencyInfo("DKK", "Danish krone", "kr"), CurrencyInfo("HKD", "Hong Kong dollar", "HK$"),
    CurrencyInfo("THB", "Thai baht", "฿"), CurrencyInfo("IDR", "Indonesian rupiah", "Rp"), CurrencyInfo("MYR", "Malaysian ringgit", "RM"),
    CurrencyInfo("PHP", "Philippine peso", "₱"), CurrencyInfo("TRY", "Turkish lira", "₺"), CurrencyInfo("PLN", "Polish zloty", "zł"),
    CurrencyInfo("CZK", "Czech koruna", "Kč"), CurrencyInfo("PKR", "Pakistani rupee", "₨"), CurrencyInfo("BDT", "Bangladeshi taka", "৳"),
    CurrencyInfo("LKR", "Sri Lankan rupee", "Rs"), CurrencyInfo("NPR", "Nepalese rupee", "₨"), CurrencyInfo("VND", "Vietnamese dong", "₫"),
    CurrencyInfo("EGP", "Egyptian pound", "E£"), CurrencyInfo("NGN", "Nigerian naira", "₦"), CurrencyInfo("ILS", "Israeli shekel", "₪")
  )

  /** Rough rates built into the app so the converter works with no connection; the update button replaces them. */
  val builtIn: Map<String, Double> = mapOf(
    "USD" to 1.0, "EUR" to 0.86, "GBP" to 0.75, "INR" to 88.0, "JPY" to 150.0, "AUD" to 1.52, "CAD" to 1.38, "CHF" to 0.80,
    "CNY" to 7.1, "SGD" to 1.30, "AED" to 3.6725, "SAR" to 3.75, "KRW" to 1400.0, "MXN" to 18.5, "BRL" to 5.4, "ZAR" to 17.5,
    "NZD" to 1.70, "SEK" to 9.5, "NOK" to 10.1, "DKK" to 6.4, "HKD" to 7.8, "THB" to 32.5, "IDR" to 16600.0, "MYR" to 4.2,
    "PHP" to 57.5, "TRY" to 41.0, "PLN" to 3.7, "CZK" to 21.0, "PKR" to 280.0, "BDT" to 122.0, "LKR" to 300.0, "NPR" to 141.0,
    "VND" to 26000.0, "EGP" to 48.0, "NGN" to 1500.0, "ILS" to 3.4
  )

  fun info(code: String): CurrencyInfo = currencies.firstOrNull { it.code == code } ?: CurrencyInfo(code, code, code)

  /** The currency a symbol most likely means, to start the converter in the user's own currency. */
  fun codeForSymbol(symbol: String): String = when (symbol) {
    "₹" -> "INR"
    "€" -> "EUR"
    "£" -> "GBP"
    "¥" -> "JPY"
    else -> "USD"
  }

  /** [amount] in [from] expressed in [to]; null when either currency has no rate. */
  fun convert(amount: Double, from: String, to: String, table: Map<String, Double>): Double? {
    val a = table[from] ?: return null
    val b = table[to] ?: return null
    if (a <= 0.0) return null
    return amount / a * b
  }

  /** Encodes a table for storage in the settings. */
  fun encode(table: RateTable): String = JSONObject().apply {
    put("updatedAt", table.updatedAt ?: 0L)
    put("rates", JSONObject(table.perUsd.mapValues { it.value }))
  }.toString()

  fun decode(json: String?): RateTable? = runCatching {
    if (json == null) return null
    val o = JSONObject(json)
    val rates = o.getJSONObject("rates")
    val map = rates.keys().asSequence().associateWith { rates.getDouble(it) }.filterValues { it > 0.0 }
    if (map.size < 5) null else RateTable(map, o.optLong("updatedAt", 0L).takeIf { it > 0 }, builtIn = false)
  }.getOrNull()

  /** Reads the response of the free open.er-api.com endpoint; null when it is not what we expect. */
  fun parseResponse(body: String, now: Long): RateTable? = runCatching {
    val o = JSONObject(body)
    if (o.optString("result") != "success") return null
    val rates = o.getJSONObject("rates")
    val map = rates.keys().asSequence().associateWith { rates.getDouble(it) }.filterValues { it > 0.0 }
    if (map.size < 5 || map["USD"] == null) null else RateTable(map, now, builtIn = false)
  }.getOrNull()

  /** The one network call in the app, made only when the user taps "Update rates". Blocking: call off the main thread. */
  fun fetch(now: Long): RateTable? = try {
    val connection = (URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection).apply {
      connectTimeout = 8_000
      readTimeout = 8_000
      requestMethod = "GET"
    }
    try {
      if (connection.responseCode != 200) null else parseResponse(connection.inputStream.bufferedReader().use { it.readText() }, now)
    } finally {
      connection.disconnect()
    }
  } catch (_: Exception) {
    null
  }
}
