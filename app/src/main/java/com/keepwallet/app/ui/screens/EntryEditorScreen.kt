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
  var showForeign by remember { mutableStateOf(false) }
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

  var step by remember { mutableStateOf(1) }
  var detailsOpen by remember { mutableStateOf(true) }
  val scroll = rememberScrollState()
  val scope = androidx.compose.runtime.rememberCoroutineScope()

  if (showForeign) {
    val symbol = LocalCurrency.current
    val rates by viewModel.rates.collectAsStateWithLifecycle()
    ForeignSheet(
      homeCode = com.keepwallet.app.data.CurrencyRates.codeForSymbol(symbol),
      homeSymbol = symbol,
      rates = rates.perUsd,
      initialCode = state.foreignCode,
      initialMinor = state.foreignMinor,
      onApply = { code, foreign, home ->
        viewModel.applyForeignAmount(code, foreign, home)
        showForeign = false
      },
      onDismiss = { showForeign = false }
    )
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
        expandedHeight = 52.dp,
        windowInsets = WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
      if (scanning) {
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().testTag("scan_progress"))
      }

      // Three steps in one page: tapping a step opens that part.
      EntrySteps(
        step = step,
        onAmount = {
          step = 1
          categoryOpen = false
          scope.launch { scroll.animateScrollTo(0) }
        },
        onCategory = {
          step = 2
          categoryOpen = true
          scope.launch { scroll.animateScrollTo(0) }
        },
        onDetails = {
          step = 3
          categoryOpen = false
          detailsOpen = true
          scope.launch { scroll.animateScrollTo(scroll.maxValue) }
        }
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .verticalScroll(scroll)
          .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(Modifier.height(6.dp))
        SegmentedToggle(
          options = listOf(TxType.EXPENSE to "Expense", TxType.INCOME to "Income"),
          selected = state.type,
          onSelect = viewModel::setEntryType,
          modifier = Modifier.fillMaxWidth().testTag("type_toggle")
        )

        AmountDisplay(state.amountText, modifier = Modifier.padding(vertical = 8.dp), onClear = viewModel::clearAmount)

        // One-tap amounts that add up: +20, +50, +100, +500.
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(20L, 50L, 100L, 500L).forEach { whole ->
            Box(
              Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(role = Role.Button) { viewModel.addToAmount(whole * 100) }
                .padding(vertical = 8.dp)
                .testTag("quick_amount_$whole"),
              contentAlignment = Alignment.Center
            ) {
              Text("+${money(whole * 100, whole = true)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
            }
          }
        }

        // Habits: the things you enter again and again, one tap to fill the name, category, account and amount.
        val suggestions = remember(data.transactions, state.type) { Analytics.frequentEntries(data.transactions, state.type) }
        if (!isEditing && state.merchant.isBlank() && suggestions.isNotEmpty()) {
          Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("suggestions")) {
            Eyebrow("Quick fill", modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              suggestions.forEach { suggestion ->
                Row(
                  Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Button) { viewModel.applySuggestion(suggestion) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("suggestion_${suggestion.merchant}"),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(suggestion.merchant, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                  Text("  ${money(suggestion.lastAmountMinor, whole = suggestion.lastAmountMinor % 100 == 0L)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
              }
            }
          }
        }

        // The keypad sits right under the amount; choosing a category swaps it for the category grid.
        androidx.compose.animation.AnimatedContent(
          targetState = categoryOpen,
          transitionSpec = {
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) togetherWith
              androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
          },
          label = "pad"
        ) { showGrid ->
          if (showGrid) {
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
                      step = 1
                    }
                  }
                  repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
              }
            }
          } else {
            NumericKeypad(
              onKey = viewModel::pressKey,
              keyHeight = if (LocalView.current.height < 1500) 40.dp else 46.dp
            )
          }
        }
        Spacer(Modifier.height(10.dp))

        // The chosen category, with what is left of its budget; tap to change.
        FieldRow(
          onClick = {
            categoryOpen = !categoryOpen
            step = if (categoryOpen) 2 else 1
          },
          modifier = Modifier.testTag("category_field"),
          leading = { CategoryBadge(category, size = 32.dp) },
          label = if (isIncome) "Income type" else "Category",
          value = category?.name ?: "Choose a category",
          trailing = {
            if (!categoryOpen && !isIncome && category != null && category.monthlyLimitMinor > 0) {
              Text(
                "${money(leftThisMonth(category, data, state), whole = true)} left",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = if (categoryOpen) "Hide categories" else "Show categories", tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        )
        Spacer(Modifier.height(8.dp))
        if (state.ruleNote != null) {
          Text(
            state.ruleNote,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp).testTag("rule_note")
          )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
          if (!isEditing && state.amountMinor > 0 && categories.size >= 2) {
            TextButton(onClick = { showSplit = true }, modifier = Modifier.testTag("split_button")) {
              Icon(Icons.Rounded.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("Split")
            }
          }
          TextButton(onClick = { showForeign = true }, modifier = Modifier.testTag("foreign_button")) {
            Icon(Icons.Rounded.CurrencyExchange, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (state.foreignCode != null) "Other currency" else "Paid in another currency")
          }
        }
        if (state.foreignCode != null && state.foreignMinor != null) {
          val info = com.keepwallet.app.data.CurrencyRates.info(state.foreignCode)
          Text(
            "${com.keepwallet.app.domain.Money.format(state.foreignMinor, info.symbol)} (${info.code}) converted at today's rate",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp).testTag("foreign_note")
          )
        }
        if (!isIncome && category != null) {
          BudgetImpactCard(impact = impact, categoryName = category.name)
          Spacer(Modifier.height(8.dp))
        }

        // Everything else is optional and folds away.
        Row(
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = if (detailsOpen) "Hide details" else "Show details") {
              detailsOpen = !detailsOpen
              step = if (detailsOpen) 3 else 1
            }
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .testTag("details_toggle"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Details (optional)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
          com.keepwallet.app.ui.components.ChevronToggle(detailsOpen)
        }
        com.keepwallet.app.ui.components.ExpandArea(detailsOpen, Modifier.fillMaxWidth()) {
          Column(Modifier.fillMaxWidth().testTag("details_panel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = state.merchant,
              onValueChange = viewModel::setEntryMerchant,
              modifier = Modifier.fillMaxWidth().testTag("merchant_input"),
              label = { Text(if (isIncome) "Income" else "Expense") },
              leadingIcon = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
              trailingIcon = {
                if (state.merchant.isNotEmpty()) {
                  IconButton(onClick = { viewModel.setEntryMerchant("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                  }
                }
              },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
              keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
              shape = RoundedCornerShape(16.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FieldRow(
                onClick = { showDatePicker = true },
                modifier = Modifier.weight(1f).testTag("date_field"),
                leading = { Icon(Icons.Rounded.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                label = "Date",
                value = DateTimeFormatter.ofPattern("d MMM yyyy").format(state.date)
              )
              Box(Modifier.weight(1f)) {
                FieldRow(
                  onClick = { accountMenu = true },
                  modifier = Modifier.testTag("account_field"),
                  leading = { Icon(accountIcon(account?.kind ?: com.keepwallet.app.data.AccountKind.BANK), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                  label = "Account",
                  value = account?.name ?: "None"
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
            // One line, however long: Enter finishes typing instead of starting a new line.
            OutlinedTextField(
              value = state.note,
              onValueChange = viewModel::setEntryNote,
              modifier = Modifier.fillMaxWidth().testTag("note_input"),
              label = { Text("Note (optional)") },
              leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
              singleLine = true,
              keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
              keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
              shape = RoundedCornerShape(16.dp)
            )
            Box(Modifier.fillMaxWidth()) {
              ReceiptRow(
                receiptFile = state.receiptFile,
                receipts = receipts,
                onAttach = { receiptMenu = true },
                onRemove = viewModel::removeReceipt,
                onView = { viewingReceipt = true }
              )
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
          }
        }
        Spacer(Modifier.height(12.dp))
      }

      Button(
        onClick = viewModel::saveEntry,
        enabled = state.amountMinor > 0 && category != null && account != null,
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .height(54.dp)
          .testTag("save_entry"),
        shape = RoundedCornerShape(16.dp)
      ) {
        Text((if (isEditing) "Update " else "Save ") + noun, style = MaterialTheme.typography.titleSmall)
      }
    }
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
    Text(symbol, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.width(4.dp))
    Text(
      amountText,
      fontSize = heroFontSize(symbol + amountText, 48),
      fontWeight = FontWeight.Bold,
      color = if (amountText == "0") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      modifier = Modifier.testTag("amount_display")
    )
    Box(
      Modifier
        .padding(start = 3.dp)
        .width(3.dp)
        .height(40.dp)
        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
    )
  }
}

@Composable
private fun FieldRow(
  onClick: () -> Unit,
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  leading: @Composable () -> Unit,
  trailing: (@Composable () -> Unit)? = null
) {
  val shape = RoundedCornerShape(16.dp)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .panel(shape, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), 0.5f)
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp)
      .heightIn(min = 40.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    leading()
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    trailing?.invoke()
  }
}

@Composable
private fun BudgetImpactCard(impact: BudgetImpact?, categoryName: String) {
  val extra = MaterialTheme.extra
  val over = impact != null && impact.leftAfterMinor < 0
  AppCard(
    modifier = Modifier.fillMaxWidth().testTag("budget_impact"),
    container = if (over) extra.warningContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
    border = null
  ) {
    Column(Modifier.padding(14.dp)) {
      if (impact == null) {
        Text("No monthly limit set for $categoryName", style = MaterialTheme.typography.titleSmall)
        Text(
          "Set one in Plan to see what's left after each purchase.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        Text(
          if (over) "${money(-impact.leftAfterMinor, whole = true)} over budget after saving"
          else "${money(impact.leftAfterMinor, whole = true)} left after saving",
          style = MaterialTheme.typography.titleSmall,
          color = if (over) extra.warning else MaterialTheme.colorScheme.onSurface
        )
        Text(
          "Your $categoryName budget is ${money(impact.limitMinor, whole = true)} with ${money(impact.spentMinor, whole = true)} spent.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun ReceiptRow(
  receiptFile: String?,
  receipts: ReceiptStore,
  onAttach: () -> Unit,
  onRemove: () -> Unit,
  onView: () -> Unit
) {
  if (receiptFile == null) {
    Surface(
      onClick = onAttach,
      modifier = Modifier.fillMaxWidth().testTag("add_receipt"),
      shape = RoundedCornerShape(16.dp),
      color = Color.Transparent,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("Add receipt (optional)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
      }
    }
  } else {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      ReceiptThumbnail(
        name = receiptFile, receipts = receipts, maxSidePx = 360,
        modifier = Modifier
          .size(72.dp)
          .clip(RoundedCornerShape(14.dp))
          .clickable(role = Role.Button, onClick = onView)
          .semantics { contentDescription = "Receipt photo. Tap to enlarge." }
      )
      Column(Modifier.weight(1f)) {
        Text("Receipt attached", style = MaterialTheme.typography.titleSmall)
        Text("Stored only on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      TextButton(onClick = onRemove) { Text("Remove", color = MaterialTheme.colorScheme.error) }
    }
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
  Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    rows.forEach { row ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

/** The three parts of the form as a row of steps; the active one is filled, finished ones are tinted. */
@Composable
private fun EntrySteps(step: Int, onAmount: () -> Unit, onCategory: () -> Unit, onDetails: () -> Unit) {
  @Composable
  fun Item(number: Int, label: String, tag: String, onClick: () -> Unit) {
    val active = step == number
    val done = step > number
    Row(
      Modifier
        .clip(RoundedCornerShape(50))
        .clickable(role = Role.Tab, onClick = onClick)
        .padding(horizontal = 4.dp, vertical = 4.dp)
        .testTag(tag),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        Modifier
          .size(22.dp)
          .clip(androidx.compose.foundation.shape.CircleShape)
          .background(
            when {
              active -> MaterialTheme.colorScheme.primary
              done -> MaterialTheme.colorScheme.primaryContainer
              else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
          ),
        contentAlignment = Alignment.Center
      ) {
        Text(
          number.toString(),
          style = MaterialTheme.typography.labelSmall,
          color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Spacer(Modifier.width(6.dp))
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
    }
  }
  Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    Item(1, "Amount", "step_amount", onAmount)
    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Item(2, "Category", "step_category", onCategory)
    Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Item(3, "Details", "step_details", onDetails)
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
