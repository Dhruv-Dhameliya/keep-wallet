package com.keepwallet.app.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.domain.PinHasher
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.FormSheet
import com.keepwallet.app.ui.components.KeepWalletMark
import com.keepwallet.app.ui.theme.extra

/** The phone's fingerprint (or face) unlock, behind an interface so screens and tests don't need an activity. */
interface BiometricGate {
  fun available(): Boolean
  fun prompt(onSuccess: () -> Unit)
}

val LocalBiometric = staticCompositionLocalOf<BiometricGate?> { null }

class BiometricHelper(private val activity: FragmentActivity) : BiometricGate {
  private val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG

  override fun available(): Boolean =
    BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

  override fun prompt(onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
      activity,
      ContextCompat.getMainExecutor(activity),
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
      }
    )
    val info = BiometricPrompt.PromptInfo.Builder()
      .setTitle("Unlock Keep Wallet")
      .setNegativeButtonText("Use PIN")
      .setAllowedAuthenticators(authenticators)
      .build()
    prompt.authenticate(info)
  }
}

/** Covers the whole app until the right PIN is typed (or the fingerprint matches). */
@Composable
fun LockScreen(viewModel: MainViewModel) {
  val length by viewModel.pinLength.collectAsStateWithLifecycle()
  val error by viewModel.lockError.collectAsStateWithLifecycle()
  val useBiometric by viewModel.biometricEnabled.collectAsStateWithLifecycle()
  val gate = LocalBiometric.current
  val canUseBiometric = useBiometric && gate?.available() == true
  var entered by remember { mutableStateOf("") }

  LaunchedEffect(canUseBiometric) {
    if (canUseBiometric) gate?.prompt { viewModel.unlockWithBiometric() }
  }
  LaunchedEffect(entered) {
    if (entered.length == length) {
      if (!viewModel.unlockWithPin(entered)) entered = ""
    }
  }

  val extra = MaterialTheme.extra
  Surface(Modifier.fillMaxSize().testTag("lock_screen"), color = MaterialTheme.colorScheme.background) {
    Column(
      Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(extra.cardStart, extra.cardEnd))),
        contentAlignment = Alignment.Center
      ) { KeepWalletMark(size = 44.dp, knockout = extra.cardStart) }
      Spacer(Modifier.height(16.dp))
      Text("Keep Wallet is locked", style = MaterialTheme.typography.titleLarge)
      Text("Enter your PIN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(20.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.testTag("lock_dots")) {
        repeat(length) { index ->
          Box(
            Modifier
              .size(14.dp)
              .clip(CircleShape)
              .background(if (index < entered.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
          )
        }
      }
      Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
        error?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.testTag("lock_error"))
        }
      }
      listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del")).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 5.dp)) {
          row.forEach { key ->
            when (key) {
              "bio" -> if (canUseBiometric) {
                PadKey("lock_biometric", "Use fingerprint") { gate?.prompt { viewModel.unlockWithBiometric() } }.also { }
              } else {
                Spacer(Modifier.size(68.dp))
              }
              "del" -> PadKey("lock_backspace", "Delete") {
                if (entered.isNotEmpty()) entered = entered.dropLast(1)
                viewModel.clearLockError()
              }
              else -> PadKey("lock_key_$key", key) {
                if (entered.length < length) entered += key
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PadKey(tag: String, label: String, onClick: () -> Unit) {
  Box(
    Modifier
      .size(68.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceContainerHigh)
      .clickable(role = Role.Button, onClick = onClick)
      .semantics { contentDescription = label }
      .testTag(tag),
    contentAlignment = Alignment.Center
  ) {
    when (tag) {
      "lock_backspace" -> Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null)
      "lock_biometric" -> Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      else -> Text(label, fontSize = 24.sp, fontWeight = FontWeight.Medium)
    }
  }
}

// ---- Sheets used by Settings --------------------------------------------------------------------

private fun digitsOnly(text: String): String = text.filter { it.isDigit() }.take(6)

@Composable
private fun PinField(value: String, label: String, tag: String, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = { onChange(digitsOnly(it)) },
    label = { Text(label) },
    singleLine = true,
    visualTransformation = PasswordVisualTransformation(),
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    modifier = Modifier.fillMaxWidth().testTag(tag)
  )
}

/** Choose a PIN of 4 to 6 digits (typed twice), and whether the fingerprint may open the app too. */
@Composable
fun PinSetupSheet(biometricAvailable: Boolean, onSave: (pin: String, biometric: Boolean) -> Unit, onDismiss: () -> Unit) {
  var pin by remember { mutableStateOf("") }
  var again by remember { mutableStateOf("") }
  var biometric by remember { mutableStateOf(biometricAvailable) }
  val valid = PinHasher.isValidPin(pin) && pin == again
  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text("Set a PIN") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "4 to 6 digits. The PIN is stored only as a scrambled hash on this phone. If you forget it, the only way back in is to reinstall, which erases your data, so keep a backup.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PinField(pin, "New PIN", "pin_new") { pin = it }
        PinField(again, "Repeat PIN", "pin_repeat") { again = it }
        if (pin.isNotEmpty() && again.isNotEmpty() && pin != again) {
          Text("The two PINs don't match.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pin_mismatch"))
        }
        if (biometricAvailable) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = biometric, onCheckedChange = { biometric = it }, modifier = Modifier.testTag("pin_biometric"))
            Text("Also unlock with fingerprint", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    },
    confirmButton = { TextButton(enabled = valid, onClick = { onSave(pin, biometric) }, modifier = Modifier.testTag("pin_save")) { Text("Turn on") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

/** Asks for the current PIN before something sensitive (turning the lock off). [onConfirm] returns false for a wrong PIN. */
@Composable
fun PinPromptSheet(title: String, confirmLabel: String, onConfirm: (String) -> Boolean, onDismiss: () -> Unit) {
  var pin by remember { mutableStateOf("") }
  var wrong by remember { mutableStateOf(false) }
  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PinField(pin, "Current PIN", "pin_current") {
          pin = it
          wrong = false
        }
        if (wrong) Text("That isn't the PIN.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pin_wrong"))
      }
    },
    confirmButton = {
      TextButton(
        enabled = pin.length >= 4,
        onClick = { if (!onConfirm(pin)) wrong = true },
        modifier = Modifier.testTag("pin_confirm")
      ) { Text(confirmLabel) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

/** Change the PIN: the old one, then a new one twice. */
@Composable
fun PinChangeSheet(onChange: (current: String, new: String) -> Boolean, onDismiss: () -> Unit) {
  var current by remember { mutableStateOf("") }
  var pin by remember { mutableStateOf("") }
  var again by remember { mutableStateOf("") }
  var wrong by remember { mutableStateOf(false) }
  val valid = current.length >= 4 && PinHasher.isValidPin(pin) && pin == again
  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text("Change PIN") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PinField(current, "Current PIN", "pin_current") {
          current = it
          wrong = false
        }
        PinField(pin, "New PIN", "pin_new") { pin = it }
        PinField(again, "Repeat new PIN", "pin_repeat") { again = it }
        if (wrong) Text("The current PIN isn't right.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("pin_wrong"))
      }
    },
    confirmButton = { TextButton(enabled = valid, onClick = { if (!onChange(current, pin)) wrong = true }, modifier = Modifier.testTag("pin_save")) { Text("Change PIN") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}
