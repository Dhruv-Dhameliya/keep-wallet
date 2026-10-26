package com.keepwallet.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.DetectedEntryEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.notify.NotificationParser
import com.keepwallet.app.ui.MainViewModel
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import com.keepwallet.app.ui.components.SubScreenHeader
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.extra

internal fun isListenerGranted(context: Context): Boolean =
  NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

private fun launchSafely(context: Context, intent: Intent) {
  try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  } catch (_: ActivityNotFoundException) {
    // Some devices don't expose this screen; nothing else we can do.
  }
}

/** Everything about detecting payments from notifications: the switch, permissions, review list and a test tool. */
@Composable
fun DetectionScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val captureEnabled by viewModel.captureEnabled.collectAsStateWithLifecycle()
  val data by viewModel.data.collectAsStateWithLifecycle()

  var listenerGranted by remember { mutableStateOf(isListenerGranted(context)) }
  var alertsAllowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
  var showAccessDialog by remember { mutableStateOf(false) }
  var testTitle by remember { mutableStateOf("HDFC Bank") }
  var testText by remember { mutableStateOf("Rs 250 debited at Metro transit") }
  var ruleEditing by remember { mutableStateOf<com.keepwallet.app.data.PaymentRuleEntity?>(null) }
  var ruleNew by remember { mutableStateOf(false) }

  // Coming back from the system settings screens: re-check what the user granted.
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    listenerGranted = isListenerGranted(context)
    alertsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
  }
  val postNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    alertsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled()
  }
  val preview = remember(testTitle, testText) { NotificationParser.parse(testTitle, testText) }

  Column(modifier.fillMaxSize()) {
    SubScreenHeader("Detect payments", onBack)
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("detection_list"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        SettingsGroup {
          Column(Modifier.padding(16.dp)) {
            Text(
              "Keep Wallet can spot payment and deposit alerts from your banking and UPI apps and offer to add them as entries. " +
                "It needs notification access, which Android asks you to grant separately.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          SettingsRow(
            title = "Detect transactions",
            subtitle = if (captureEnabled) "On" else "Off – no notifications are read",
            subtitleTag = "capture_status",
            trailing = {
              Switch(
                checked = captureEnabled,
                onCheckedChange = { enabled ->
                  viewModel.setCaptureEnabled(enabled)
                  if (enabled) {
                    if (!listenerGranted) showAccessDialog = true
                    if (Build.VERSION.SDK_INT >= 33 && !alertsAllowed) postNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                },
                modifier = Modifier.testTag("capture_switch")
              )
            }
          )
        }
      }

      item {
        SettingsSectionTitle("Permissions")
        SettingsGroup {
          StatusRow(
            label = "Notification access",
            ok = listenerGranted, okText = "Granted", badText = "Not granted",
            actionLabel = if (listenerGranted) "Manage" else "Grant",
            onAction = { launchSafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            tag = "listener_status"
          )
          HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
          StatusRow(
            label = "Quick-action alerts",
            ok = alertsAllowed, okText = "Allowed", badText = "Blocked",
            actionLabel = "Settings",
            onAction = {
              launchSafely(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            },
            tag = "alerts_status"
          )
        }
        if (captureEnabled && !listenerGranted) {
          Text(
            "Detection is on but Android hasn't given Keep Wallet access yet, so nothing can be detected. " +
              "If the switch is greyed out on a manually installed app, open App info, tap the ⋮ menu and choose Allow restricted settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extra.warning,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
          )
        }
        if (!alertsAllowed) {
          Text(
            "Alerts are blocked, so detected entries only appear inside Keep Wallet (on Home and below).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
          )
        }
      }

      item {
        SettingsGroup {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
              "Private by design: only the amount, direction, merchant and app name are kept – never the message itself. " +
                "Nothing becomes an entry until you review and save it, and unreviewed detections are deleted after 7 days.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      item {
        SettingsSectionTitle("Waiting for review (${data.detected.size})")
        SettingsGroup {
          if (data.detected.isEmpty()) {
            Text(
              "Nothing detected yet. Detected payments show up here until you add or dismiss them.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp)
            )
          }
          data.detected.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            DetectedRow(entry, onAdd = { viewModel.reviewDetected(entry.id) }, onDismiss = { viewModel.discardDetected(entry.id) })
          }
        }
      }

      item {
        SettingsSectionTitle("Rules (${data.rules.size})")
        SettingsGroup {
          Text(
            "A rule files detected payments for you: anything whose name contains the words you choose gets that category (and account) when you review it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp)
          )
          data.rules.forEachIndexed { index, rule ->
            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingsRow(
              title = "${rule.match} → ${data.categories.firstOrNull { it.id == rule.categoryId }?.name ?: "–"}",
              subtitle = listOfNotNull(rule.accountId?.let { id -> data.accounts.firstOrNull { it.id == id }?.name }, rule.displayName?.let { "named \"$it\"" }).joinToString(" · ").ifEmpty { "Keeps your usual account and the original name" },
              onClick = { ruleEditing = rule },
              modifier = Modifier.testTag("rule_row_${rule.match}")
            )
          }
          TextButton(onClick = { ruleNew = true }, modifier = Modifier.padding(horizontal = 8.dp).testTag("add_rule")) { Text("Add a rule") }
        }
      }

      item {
        SettingsSectionTitle("Try the detector")
        SettingsGroup {
          Column(Modifier.padding(16.dp)) {
            Text(
              "Paste the text of a bank or UPI alert to see what Keep Wallet would pick up. " +
                "\"Detect\" runs it through the real pipeline, so detection has to be on.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
              value = testTitle, onValueChange = { testTitle = it.take(80) },
              label = { Text("Notification title") }, singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("test_title")
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
              value = testText, onValueChange = { testText = it.take(300) },
              label = { Text("Notification text") }, maxLines = 3,
              modifier = Modifier.fillMaxWidth().testTag("test_text")
            )
            Spacer(Modifier.height(8.dp))
            Text(
              preview?.let {
                val label = if (it.type == TxType.INCOME) "Deposit" else "Payment"
                "$label of ${money(it.amountMinor)}" + (it.merchant?.let { m -> " · $m" } ?: "")
              } ?: "Nothing would be detected from this text.",
              style = MaterialTheme.typography.labelLarge,
              color = if (preview != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.testTag("test_preview")
            )
            Spacer(Modifier.height(10.dp))
            Button(
              onClick = { viewModel.simulateNotification(testTitle, testText) },
              modifier = Modifier.fillMaxWidth().testTag("test_detect"),
              shape = RoundedCornerShape(14.dp)
            ) { Text("Detect") }
          }
        }
      }
    }
  }

  if (ruleNew || ruleEditing != null) {
    val editing = ruleEditing
    RuleSheet(
      existing = editing,
      categories = data.categories,
      accounts = data.accounts,
      onSave = {
        viewModel.saveRule(it)
        ruleNew = false
        ruleEditing = null
      },
      onDelete = editing?.let { r ->
        {
          viewModel.deleteRule(r)
          ruleEditing = null
        }
      },
      onDismiss = {
        ruleNew = false
        ruleEditing = null
      }
    )
  }

  if (showAccessDialog) {
    AlertDialog(
      onDismissRequest = { showAccessDialog = false },
      title = { Text("Allow notification access") },
      text = {
        Text(
          "On the next screen, switch on Keep Wallet. Android shows this warning for every app that reads notifications. " +
            "Keep Wallet only looks for payment amounts, keeps nothing but the parsed details, and never saves anything without your confirmation.\n\n" +
            "You can say no and keep using the app normally; detection simply stays inactive."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showAccessDialog = false
            launchSafely(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
          },
          modifier = Modifier.testTag("access_continue")
        ) { Text("Open settings") }
      },
      dismissButton = {
        TextButton(onClick = {
          showAccessDialog = false
          viewModel.setCaptureEnabled(false)
        }) { Text("Not now") }
      }
    )
  }
}

@Composable
private fun StatusRow(
  label: String,
  ok: Boolean,
  okText: String,
  badText: String,
  actionLabel: String,
  onAction: () -> Unit,
  tag: String
) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.titleSmall)
      Text(
        if (ok) okText else badText,
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) MaterialTheme.extra.income else MaterialTheme.extra.warning,
        modifier = Modifier.testTag(tag)
      )
    }
    OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) { Text(actionLabel) }
  }
}

@Composable
private fun DetectedRow(entry: DetectedEntryEntity, onAdd: () -> Unit, onDismiss: () -> Unit) {
  val income = entry.type == TxType.INCOME
  Column(Modifier.fillMaxWidth().padding(16.dp).testTag("detected_row_${entry.id}")) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(entry.merchant, style = MaterialTheme.typography.titleSmall)
        Text("${if (income) "Deposit" else "Payment"} · from ${entry.sourceApp}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(
        money(if (income) entry.amountMinor else -entry.amountMinor, sign = income),
        style = MaterialTheme.typography.titleSmall,
        color = if (income) MaterialTheme.extra.income else MaterialTheme.colorScheme.onSurface
      )
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Dismiss") }
      Button(onClick = onAdd, modifier = Modifier.weight(1f)) { Text("Review & add") }
    }
  }
}

/** Create or edit a rule: the words to look for, the category, optionally an account and a tidier name. */
@Composable
private fun RuleSheet(
  existing: com.keepwallet.app.data.PaymentRuleEntity?,
  categories: List<com.keepwallet.app.data.CategoryEntity>,
  accounts: List<com.keepwallet.app.data.AccountEntity>,
  onSave: (com.keepwallet.app.data.PaymentRuleEntity) -> Unit,
  onDelete: (() -> Unit)?,
  onDismiss: () -> Unit
) {
  var match by remember { mutableStateOf(existing?.match.orEmpty()) }
  var categoryId by remember { mutableStateOf(existing?.categoryId ?: categories.firstOrNull { it.type == TxType.EXPENSE }?.id) }
  var accountId by remember { mutableStateOf(existing?.accountId) }
  var name by remember { mutableStateOf(existing?.displayName.orEmpty()) }
  com.keepwallet.app.ui.components.FormSheet(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "New rule" else "Edit rule") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = match, onValueChange = { match = it.take(40) }, label = { Text("When the name contains") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("rule_match")
        )
        ChoiceField(
          label = "Category",
          value = categories.firstOrNull { it.id == categoryId }?.name ?: "Choose",
          options = categories,
          optionLabel = { it.name },
          onPick = { categoryId = it.id },
          tag = "rule_category"
        )
        ChoiceField(
          label = "Account",
          value = accounts.firstOrNull { it.id == accountId }?.name ?: "Keep my usual",
          options = listOf<com.keepwallet.app.data.AccountEntity?>(null) + accounts,
          optionLabel = { it?.name ?: "Keep my usual" },
          onPick = { accountId = it?.id },
          tag = "rule_account"
        )
        OutlinedTextField(
          value = name, onValueChange = { name = it.take(40) }, label = { Text("Rename to (optional)") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("rule_name")
        )
        if (onDelete != null) {
          TextButton(onClick = onDelete, modifier = Modifier.testTag("rule_delete")) { Text("Delete rule", color = MaterialTheme.colorScheme.error) }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = match.isNotBlank() && categoryId != null,
        onClick = {
          onSave(
            com.keepwallet.app.data.PaymentRuleEntity(
              id = existing?.id ?: 0, match = match.trim(), categoryId = categoryId!!, accountId = accountId, displayName = name.trim().ifEmpty { null }
            )
          )
        },
        modifier = Modifier.testTag("rule_save")
      ) { Text("Save") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}
