package com.keepwallet.app.domain

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Turns a PIN into something safe to store: a slow, salted hash. The PIN itself is never written anywhere, and two
 * phones with the same PIN store different values.
 */
object PinHasher {
  private const val ITERATIONS = 60_000
  private const val KEY_BITS = 256

  fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }

  fun hash(pin: String, salt: String): String {
    val spec = PBEKeySpec(pin.toCharArray(), Base64.getDecoder().decode(salt), ITERATIONS, KEY_BITS)
    return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
  }

  /** Compares in constant time so the answer can't be learned from how long it took. */
  fun matches(pin: String, salt: String, expected: String): Boolean =
    MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())

  fun isValidPin(pin: String): Boolean = pin.length in 4..6 && pin.all { it in '0'..'9' }
}

/**
 * Slows down guessing: after [freeTries] wrong PINs in a row, further tries are refused for [cooldownMs], and the
 * wait grows each time the limit is hit again. A correct PIN resets it.
 */
class AttemptLimiter(private val freeTries: Int = 5, private val cooldownMs: Long = 30_000) {
  private var failures = 0
  private var blockedUntil = 0L
  private var rounds = 0

  fun failed(now: Long) {
    failures++
    if (failures >= freeTries) {
      rounds++
      failures = 0
      blockedUntil = now + cooldownMs * rounds
    }
  }

  fun remainingMs(now: Long): Long = (blockedUntil - now).coerceAtLeast(0)

  /** Wrong tries left before the next wait starts. */
  val triesLeft: Int get() = freeTries - failures

  fun reset() {
    failures = 0
    rounds = 0
    blockedUntil = 0
  }
}

/** When the app should ask for the PIN again after being away. */
object LockPolicy {
  /** [awayMs] is how long the app was out of sight; a timeout of 0 means every time. */
  fun shouldLock(timeoutSeconds: Int, awayMs: Long): Boolean = awayMs >= timeoutSeconds * 1000L
}
