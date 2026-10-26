package com.keepwallet.app.domain

/** State machine behind the on-screen numeric keypad. Text is always a valid non-negative decimal. */
object AmountInput {
  const val BACKSPACE = "DEL"
  private const val MAX_INTEGER_DIGITS = 9
  private const val MAX_DECIMALS = 2

  fun press(current: String, key: String): String {
    val text = current.ifEmpty { "0" }
    return when (key) {
      BACKSPACE -> {
        val next = text.dropLast(1)
        if (next.isEmpty() || next == "-") "0" else next
      }
      "." -> if ('.' in text) text else "$text."
      in "0".."9" -> {
        val dot = text.indexOf('.')
        when {
          dot >= 0 && text.length - dot - 1 >= MAX_DECIMALS -> text
          dot < 0 && text == "0" -> key
          dot < 0 && text.length >= MAX_INTEGER_DIGITS -> text
          else -> text + key
        }
      }
      else -> text
    }
  }
}
