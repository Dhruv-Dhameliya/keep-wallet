package com.keepwallet.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import kotlinx.coroutines.launch
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import com.keepwallet.app.ui.components.Eyebrow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CallSplit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.domain.effectiveMonthlyLimit
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.ReceiptStore
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.AmountInput
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.frequentEntries
import com.keepwallet.app.domain.BudgetImpact
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.ui.AppData
import com.keepwallet.app.ui.EditorState
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.LocalCurrency
import com.keepwallet.app.ui.components.SegmentedToggle
import com.keepwallet.app.ui.components.heroFontSize
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.components.panel
import com.keepwallet.app.ui.theme.extra
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The add / edit form, shown as a full page that slides up. Being a page and not a bottom sheet means
 * there is no drag-to-dismiss gesture to fight with while scrolling, and the keypad and keyboard always
 * have a predictable amount of room.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorScreen(
  viewModel: MainViewModel,
  state: EditorState,
  data: AppData,
  receipts: ReceiptStore
) {
  var showDatePicker by remember { mutableStateOf(false) }
  var categoryOpen by remember { mutableStateOf(false) }
  var accountMenu by remember { mutableStateOf(false) }
  var receiptMenu by remember { mutableStateOf(false) }
  var viewingReceipt by remember { mutableStateOf(false) }
  var showSplit by remember { mutableStateOf(false) }
  val duplicate by viewModel.duplicate.collectAsStateWithLifecycle()
  var cameraUri by remember { mutableStateOf<Uri?>(null) }

  val context = LocalContext.current
  val focus = LocalFocusManager.current
  val imeVisible = WindowInsets.isImeVisible

  val categories = data.categories.filter { it.type == state.type }
  val category = data.categories.firstOrNull { it.id == state.categoryId }
  val account = data.accounts.firstOrNull { it.id == state.accountId }
  val isIncome = state.type == TxType.INCOME
  val isEditing = state.editingId != null
  val impact = if (isIncome) null else Analytics.budgetImpact(category, data.transactions, state.date, state.amountMinor, state.editingId)
  val noun = if (isIncome) "income" else "expense"

  val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) viewModel.attachReceipt(uri)
  }
  var scanAfterCamera by remember { mutableStateOf(false) }
  var scanMenu by remember { mutableStateOf(false) }
  var voiceFallback by remember { mutableStateOf(false) }
  val scanning by viewModel.scanning.collectAsStateWithLifecycle()
  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
    val uri = cameraUri
    cameraUri = null
    if (saved && uri != null) {
      if (scanAfterCamera) viewModel.scanReceipt(uri) else viewModel.attachReceipt(uri)
    }
    scanAfterCamera = false
  }
  val scanGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) viewModel.scanReceipt(uri)
  }
  val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val spoken = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
    if (result.resultCode == android.app.Activity.RESULT_OK && spoken != null) viewModel.applyVoiceText(spoken)
  }
  fun startVoice() {
    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
      .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      .putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
      .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say it, like \"spent 250 on lunch\"")
    try {
      voice.launch(intent)
    } catch (_: Exception) {
      voiceFallback = true
    }
  }
  fun takePhoto() {
    try {
      val dir = File(context.cacheDir, "camera").apply { mkdirs() }
      dir.listFiles()?.forEach { it.delete() } // earlier shots were already copied into the app
      val file = File.createTempFile("receipt_", ".jpg", dir)
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      cameraUri = uri
      camera.launch(uri)
    } catch (e: Exception) {
      android.util.Log.w("KeepWallet", "Could not start the camera", e)
      viewModel.showMessage("No camera app was found")
    }
  }

  // A shortcut can ask for the camera or the microphone as soon as the form is up.
  val pendingLaunch by viewModel.pendingLaunch.collectAsStateWithLifecycle()
  androidx.compose.runtime.LaunchedEffect(pendingLaunch) {
    when (pendingLaunch) {
      "SCAN" -> {
        viewModel.consumeLaunch()
        scanAfterCamera = true
        takePhoto()
      }
      "VOICE" -> {
        viewModel.consumeLaunch()
        startVoice()
      }
    }
  }

  if (voiceFallback) {
    VoiceFallbackSheet(onApply = viewModel::applyVoiceText, onDismiss = { voiceFallback = false })
  }

  Surface(
    modifier = Modifier.fillMaxSize().testTag("entry_sheet"),
    color = MaterialTheme.colorScheme.background
  ) {
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
      TopAppBar(
        title = { Text((if (isEditing) "Edit " else "Add ") + noun, maxLines = 1) },
        navigationIcon = {
          IconButton(onClick = viewModel::closeEditor, modifier = Modifier.testTag("cancel_entry")) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancel")
          }
        },
        actions = {
          if (!isEditing) {
            Box {
              IconButton(onClick = { scanMenu = true }, modifier = Modifier.testTag("scan_receipt")) {
                Icon(Icons.Rounded.DocumentScanner, contentDescription = "Scan a receipt")
              }
              DropdownMenu(expanded = scanMenu, onDismissRequest = { scanMenu = false }) {
                DropdownMenuItem(
                  text = { Text("Scan with the camera") },
                  leadingIcon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                  onClick = {
                    scanMenu = false
                    scanAfterCamera = true
                    takePhoto()
                  },
                  modifier = Modifier.testTag("scan_camera")
                )
                DropdownMenuItem(
                  text = { Text("Pick a receipt photo") },
                  leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                  onClick = {
                    scanMenu = false
                    scanGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                  },
                  modifier = Modifier.testTag("scan_gallery")
                )
              }
            }
            IconButton(onClick = ::startVoice, modifier = Modifier.testTag("voice_entry")) {
              Icon(Icons.Rounded.Mic, contentDescription = "Speak an entry")
            }
          }
        },
        expandedHeight = 48.dp,
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
      if (scanning) {
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().testTag("scan_progress"))
      }

      // Top to bottom: what it is, who it was with, when and where, a note, the receipt, the category; then the
      // amount and the keypad. The upper part scrolls only on a very small screen; the amount, keypad and Save
      // never leave it.
      Column(
        modifier = Modifier
          .weight(1f, fill = false)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        SegmentedToggle(
          options = listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income"),
          selected = state.type,
          onSelect = viewModel::setEntryType,
          modifier = Modifier.fillMaxWidth().testTag("type_toggle")
        )

        // Details come first.
        CompactField(
          value = state.merchant,
          onValueChange = viewModel::setEntryMerchant,
          placeholder = if (isIncome) "Where is it from?" else "What was it for?",
          icon = Icons.Rounded.Storefront,
          modifier = Modifier.fillMaxWidth(),
          tag = "merchant_input",
          imeAction = ImeAction.Next,
          onNext = { focus.moveFocus(FocusDirection.Down) },
          onClear = { viewModel.setEntryMerchant("") }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PickerChip(
            onClick = { showDatePicker = true },
            icon = Icons.Rounded.CalendarToday,
            text = DateTimeFormatter.ofPattern("d MMM yyyy").format(state.date),
            modifier = Modifier.weight(1f).testTag("date_field")
          )
          Box(Modifier.weight(1f)) {
            PickerChip(
              onClick = { accountMenu = true },
              icon = accountIcon(account?.kind ?: com.keepwallet.app.data.AccountKind.BANK),
              text = account?.name ?: "No account",
              modifier = Modifier.fillMaxWidth().testTag("account_field"),
              trailingArrow = true
            )
            DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
              data.accounts.forEach { option ->
                DropdownMenuItem(
                  text = {
                    Column {
                      Text(option.name)
                      Text(accountKindLabel(option.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  },
                  leadingIcon = { Icon(accountIcon(option.kind), contentDescription = null) },
                  onClick = {
                    viewModel.setEntryAccount(option.id)
                    accountMenu = false
                  },
                  modifier = Modifier.testTag("account_option_${option.name}")
                )
              }
            }
          }
        }
        CompactField(
          value = state.note,
          onValueChange = viewModel::setEntryNote,
          placeholder = "Add a note",
          icon = Icons.AutoMirrored.Rounded.Notes,
          modifier = Modifier.fillMaxWidth(),
          tag = "note_input",
          imeAction = ImeAction.Done,
          onNext = { focus.clearFocus() }
        )

        // The receipt and the split are plain links.
        Box(Modifier.fillMaxWidth()) {
          Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (state.receiptFile == null) {
              TextLink("Add receipt", onClick = { receiptMenu = true }, modifier = Modifier.testTag("add_receipt"))
            } else {
              TextLink("View receipt", onClick = { viewingReceipt = true }, modifier = Modifier.testTag("view_receipt"))
              TextLink("Remove receipt", onClick = viewModel::removeReceipt, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("remove_receipt"))
            }
            if (!isEditing && state.amountMinor > 0 && categories.size >= 2) {
              TextLink("Split across categories", onClick = { showSplit = true }, modifier = Modifier.testTag("split_button"))
            }
          }
          DropdownMenu(expanded = receiptMenu, onDismissRequest = { receiptMenu = false }) {
            DropdownMenuItem(
              text = { Text("Take a photo") },
              leadingIcon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
              onClick = {
                receiptMenu = false
                takePhoto()
              },
              modifier = Modifier.testTag("receipt_camera")
            )
            DropdownMenuItem(
              text = { Text("Choose from gallery") },
              leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
              onClick = {
                receiptMenu = false
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
              },
              modifier = Modifier.testTag("receipt_gallery")
            )
          }
        }

        // Then the category, with what is left of its budget; tap to choose.
        if (state.ruleNote != null) {
          Text(state.ruleNote, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp).testTag("rule_note"))
        }
        PickerChip(
          onClick = { categoryOpen = true },
          iconContent = { CategoryBadge(category, size = 26.dp) },
          text = category?.name ?: "Choose a category",
          modifier = Modifier.fillMaxWidth().testTag("category_field"),
          trailing = if (!isIncome && category != null && category.monthlyLimitMinor > 0) "${money(leftThisMonth(category, data, state), whole = true)} left" else null,
          trailingArrow = true,
          height = 46.dp
        )
        if (!isIncome && category != null && impact != null && state.amountMinor > 0) {
          Text(
            if (impact.leftAfterMinor >= 0) "${money(impact.leftAfterMinor, whole = true)} left after saving" else "${money(-impact.leftAfterMinor, whole = true)} over the ${category.name} budget",
            style = MaterialTheme.typography.labelMedium,
            color = if (impact.leftAfterMinor >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = 4.dp).testTag("budget_impact")
          )
        }
      }

      // The amount and the keypad are the bottom of the page.
      Column(Modifier.padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AmountDisplay(state.amountText, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp), onClear = viewModel::clearAmount)
        val suggestions = remember(data.transactions, state.type) { Analytics.frequentEntries(data.transactions, state.type) }
        if (!isEditing && state.merchant.isBlank() && suggestions.isNotEmpty()) {
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("suggestions"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { suggestion ->
              Row(
                Modifier
                  .clip(RoundedCornerShape(50))
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                  .clickable(role = Role.Button) { viewModel.applySuggestion(suggestion) }
                  .padding(horizontal = 10.dp, vertical = 5.dp)
                  .testTag("suggestion_${suggestion.merchant}"),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(suggestion.merchant, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Text("  ${money(suggestion.lastAmountMinor, whole = suggestion.lastAmountMinor % 100 == 0L)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
              }
            }
          }
          Spacer(Modifier.height(4.dp))
        }
        // One-tap amounts that add up: +20, +50, +100, +500.
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf(20L, 50L, 100L, 500L).forEach { whole ->
            Box(
              Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(role = Role.Button) { viewModel.addToAmount(whole * 100) }
                .padding(vertical = 5.dp)
                .testTag("quick_amount_$whole"),
              contentAlignment = Alignment.Center
            ) {
              Text("+${money(whole * 100, whole = true)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
            }
          }
        }
        NumericKeypad(
          onKey = viewModel::pressKey,
          keyHeight = if (LocalView.current.height < 1500) 36.dp else 42.dp
        )
        Button(
          onClick = viewModel::saveEntry,
          enabled = state.amountMinor > 0 && category != null && account != null,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .height(48.dp)
            .testTag("save_entry"),
          shape = RoundedCornerShape(14.dp)
        ) {
          Text((if (isEditing) "Update " else "Save ") + noun, style = MaterialTheme.typography.titleSmall)
        }
      }
    }
  }

  // Choosing a category opens a sheet, so the page itself never has to make room for the grid.
  if (categoryOpen) {
    com.keepwallet.app.ui.components.FormSheet(
      onDismissRequest = { categoryOpen = false },
      title = { Text(if (isIncome) "Income type" else "Category") },
      text = {
        Column(Modifier.fillMaxWidth().testTag("category_grid"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          categories.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              row.forEach { option ->
                CategoryTile(
                  category = option,
                  selected = option.id == state.categoryId,
                  hint = if (!isIncome && option.monthlyLimitMinor > 0) "${money(leftThisMonth(option, data, state), whole = true)} left" else null,
                  modifier = Modifier.weight(1f)
                ) {
                  viewModel.setEntryCategory(option.id)
                  categoryOpen = false
                }
              }
              repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { categoryOpen = false }) { Text("Done") } }
    )
  }

  if (showSplit) {
    SplitSheet(
      totalMinor = state.amountMinor,
      categories = categories,
      firstCategoryId = category?.id ?: categories.first().id,
      onSave = { parts ->
        showSplit = false
        viewModel.saveSplit(parts)
      },
      onDismiss = { showSplit = false }
    )
  }

  duplicate?.let { warning ->
    AlertDialog(
      onDismissRequest = viewModel::dismissDuplicate,
      title = { Text("Add this again?") },
      text = { Text("You added ${warning.existing.merchant} for ${money(warning.existing.amountMinor)} ${warning.whenText}. Save it a second time?") },
      confirmButton = { TextButton(onClick = viewModel::saveAnyway, modifier = Modifier.testTag("duplicate_add")) { Text("Add anyway") } },
      dismissButton = { TextButton(onClick = viewModel::dismissDuplicate, modifier = Modifier.testTag("duplicate_cancel")) { Text("Cancel") } }
    )
  }

  if (showDatePicker) {
    val pickerState = rememberDatePickerState(
      initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(onClick = {
          pickerState.selectedDateMillis?.let { millis ->
            viewModel.setEntryDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
          }
          showDatePicker = false
        }) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
    ) {
      DatePicker(state = pickerState)
    }
  }

  if (viewingReceipt && state.receiptFile != null) {
    ReceiptViewer(state.receiptFile, receipts, onClose = { viewingReceipt = false })
  }
}

/** How much of a category's monthly limit is still free in the month of the entry (ignoring the entry being edited). */
private fun leftThisMonth(category: CategoryEntity, data: AppData, state: EditorState): Long {
  val span = Periods.monthSpan(YearMonth.from(state.date))
  val others = data.transactions.filter { it.id != state.editingId }
  val spent = Analytics.spentInCategory(Analytics.inSpan(others, span), category.id)
  return com.keepwallet.app.domain.Analytics.effectiveMonthlyLimit(category, others, YearMonth.from(state.date)) - spent
}

@Composable
private fun CategoryTile(
  category: CategoryEntity,
  selected: Boolean,
  hint: String?,
  modifier: Modifier = Modifier,
  onClick: () -> Unit
) {
  val shape = RoundedCornerShape(16.dp)
  val primary = MaterialTheme.colorScheme.primary
  Column(
    modifier = modifier
      .heightIn(min = 96.dp)
      .panel(shape, if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), 0.5f)
      .border(if (selected) 2.dp else 0.dp, if (selected) primary else Color.Transparent, shape)
      .clickable(role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 6.dp, vertical = 10.dp)
      .testTag("category_option_${category.name}")
      .semantics { contentDescription = category.name + if (selected) ", selected" else "" },
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    CategoryBadge(category, size = 40.dp)
    Spacer(Modifier.height(6.dp))
    Text(
      category.name,
      style = MaterialTheme.typography.labelMedium,
      textAlign = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    if (hint != null) {
      Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
  }
}

@Composable
private fun AmountDisplay(amountText: String, modifier: Modifier = Modifier, onClear: () -> Unit) {
  val symbol = LocalCurrency.current
  Row(
    modifier = modifier
      .semantics { contentDescription = "Amount $symbol$amountText. Long press to clear." }
      .combinedClickable(onClick = {}, onLongClick = onClear),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center
  ) {
    Text(symbol, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(4.dp))
    Text(
      amountText,
      fontSize = heroFontSize(symbol + amountText, 40),
      fontWeight = FontWeight.Bold,
      color = if (amountText == "0") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      modifier = Modifier.testTag("amount_display")
    )
    Box(
      Modifier
        .padding(start = 3.dp)
        .width(3.dp)
        .height(32.dp)
        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
    )
  }
}

@Composable
fun ReceiptThumbnail(name: String, receipts: ReceiptStore, maxSidePx: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
  val bitmap by produceState<ImageBitmap?>(initialValue = null, name, maxSidePx) {
    value = withContext(Dispatchers.IO) { receipts.load(name, maxSidePx)?.asImageBitmap() }
  }
  Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
    bitmap?.let { Image(it, contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize()) }
  }
}

@Composable
fun ReceiptViewer(name: String, receipts: ReceiptStore, onClose: () -> Unit) {
  Dialog(onDismissRequest = onClose) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
      Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ReceiptThumbnail(
          name = name, receipts = receipts, maxSidePx = 1400,
          modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).clip(RoundedCornerShape(12.dp)),
          scale = ContentScale.Fit
        )
        TextButton(onClick = onClose) { Text("Close") }
      }
    }
  }
}

@Composable
fun NumericKeypad(onKey: (String) -> Unit, modifier: Modifier = Modifier, keyHeight: Dp = 48.dp) {
  val view = LocalView.current
  val rows = listOf(
    listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
    listOf(".", "0", AmountInput.BACKSPACE)
  )
  val shape = RoundedCornerShape(14.dp)
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    rows.forEach { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        row.forEach { key ->
          Box(
            modifier = Modifier
              .weight(1f)
              .height(keyHeight)
              .panel(shape, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), 0.5f)
              .clickable(role = Role.Button) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onKey(key)
              }
              .testTag("key_$key")
              .semantics { contentDescription = if (key == AmountInput.BACKSPACE) "Backspace" else key },
            contentAlignment = Alignment.Center
          ) {
            if (key == AmountInput.BACKSPACE) {
              Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null, modifier = Modifier.size(22.dp))
            } else {
              Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
          }
        }
      }
    }
  }
}

/**
 * One bill across several categories (a supermarket receipt with groceries and household things). Each part picks a
 * category and an amount; Save is available once the parts add up to the total.
 */
@Composable
private fun SplitSheet(
  totalMinor: Long,
  categories: List<CategoryEntity>,
  firstCategoryId: Long,
  onSave: (List<Pair<Long, Long>>) -> Unit,
  onDismiss: () -> Unit
) {
  val symbol = LocalCurrency.current
  val half = totalMinor / 2
  val rows = remember {
    androidx.compose.runtime.mutableStateListOf(
      firstCategoryId to com.keepwallet.app.domain.Money.toEditText(totalMinor - half),
      (categories.firstOrNull { it.id != firstCategoryId }?.id ?: firstCategoryId) to com.keepwallet.app.domain.Money.toEditText(half)
    )
  }
  val assigned = rows.sumOf { com.keepwallet.app.domain.Money.parseMinor(it.second) }
  val left = totalMinor - assigned
  com.keepwallet.app.ui.components.FormSheet(
    onDismissRequest = onDismiss,
    title = { Text("Split ${money(totalMinor)}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEachIndexed { index, (categoryId, amount) ->
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1.2f)) {
              ChoiceField(
                label = "Part ${index + 1}",
                value = categories.firstOrNull { it.id == categoryId }?.name ?: "Choose",
                options = categories,
                optionLabel = { it.name },
                onPick = { rows[index] = it.id to amount },
                tag = "split_category_$index"
              )
            }
            OutlinedTextField(
              value = amount,
              onValueChange = { rows[index] = categoryId to sanitizeAmount(it) },
              label = { Text(symbol) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
              modifier = Modifier.weight(1f).testTag("split_amount_$index")
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          TextButton(onClick = { rows += (categories.firstOrNull { c -> rows.none { it.first == c.id } }?.id ?: firstCategoryId) to "" }, modifier = Modifier.testTag("split_add_part")) {
            Text("Add another part")
          }
          if (rows.size > 2) TextButton(onClick = { rows.removeAt(rows.lastIndex) }, modifier = Modifier.testTag("split_remove_part")) { Text("Remove last") }
        }
        Text(
          when {
            left == 0L -> "All of it is assigned."
            left > 0 -> "${money(left)} left to assign"
            else -> "${money(-left)} too much"
          },
          style = MaterialTheme.typography.labelLarge,
          color = if (left == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.extra.warning,
          modifier = Modifier.testTag("split_left")
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = left == 0L && rows.all { com.keepwallet.app.domain.Money.parseMinor(it.second) > 0 },
        onClick = { onSave(rows.map { it.first to com.keepwallet.app.domain.Money.parseMinor(it.second) }) },
        modifier = Modifier.testTag("split_save")
      ) { Text("Save split") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

/** When the phone has no speech recogniser the sentence can be typed instead; it is read the same way. */
@Composable
private fun VoiceFallbackSheet(onApply: (String) -> Boolean, onDismiss: () -> Unit) {
  var text by remember { mutableStateOf("") }
  com.keepwallet.app.ui.components.FormSheet(
    onDismissRequest = onDismiss,
    title = { Text("Say or type it") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "No speech recogniser was found on this phone. Type it the way you would say it.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
          value = text,
          onValueChange = { text = it.take(120) },
          label = { Text("For example: spent 250 on lunch at Subway") },
          modifier = Modifier.fillMaxWidth().testTag("voice_text")
        )
      }
    },
    confirmButton = {
      androidx.compose.material3.TextButton(
        onClick = { if (onApply(text)) onDismiss() },
        enabled = text.isNotBlank(),
        modifier = Modifier.testTag("voice_apply")
      ) { Text("Fill the form") }
    },
    dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}
