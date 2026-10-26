package com.keepwallet.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.sp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.AccountEntity
import com.keepwallet.app.data.AccountKind
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.CategoryGroup
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Analytics
import com.keepwallet.app.domain.Money
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.CategoryBadge
import com.keepwallet.app.ui.components.FormSheet
import com.keepwallet.app.ui.components.LocalCurrency
import com.keepwallet.app.ui.components.SegmentedToggle
import com.keepwallet.app.ui.components.SubScreenHeader
import com.keepwallet.app.ui.components.ViewModeToggle
import com.keepwallet.app.ui.components.money
import com.keepwallet.app.ui.theme.CategoryIcons
import com.keepwallet.app.ui.theme.CategoryPalette
import kotlinx.coroutines.launch

@Composable
fun ManageScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
  var newCategoryType by remember { mutableStateOf<TxType?>(null) }
  val gridViews by viewModel.gridViews.collectAsStateWithLifecycle()
  val grid = "categories" in gridViews

  Column(modifier.fillMaxSize()) {
    SubScreenHeader(
      "Categories", onBack,
      trailing = { ViewModeToggle(grid = grid, onChange = { viewModel.setGridView("categories", it) }, modifier = Modifier.padding(end = 12.dp)) }
    )
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("manage_list"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 20.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      listOf(TxType.EXPENSE to "Spending categories", TxType.INCOME to "Income categories").forEach { (type, title) ->
        item(key = "title_$type") {
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { newCategoryType = type }, modifier = Modifier.testTag("add_category_${type.name}")) {
              Icon(Icons.Rounded.Add, contentDescription = null)
              Spacer(Modifier.width(4.dp))
              Text("Add")
            }
          }
        }
        val categories = data.categories.filter { it.type == type }
        if (categories.isEmpty()) {
          item(key = "empty_$type") {
            Text("No categories yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        if (grid) {
          val rows = categories.chunked(2)
          items(rows.size, key = { "grid_${type}_${rows[it].first().id}" }) { index ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              rows[index].forEach { category ->
                AppCard(Modifier.weight(1f).testTag("category_row_${category.name}"), onClick = { editingCategory = category }) {
                  Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryBadge(category, size = 34.dp)
                    Text(category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(
                      if (type == TxType.EXPENSE) limitSummary(category) else "Income",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 2
                    )
                  }
                }
              }
              if (rows[index].size == 1) Spacer(Modifier.weight(1f))
            }
          }
        } else {
          items(categories.size, key = { categories[it].id }) { index ->
            val category = categories[index]
            AppCard(Modifier.fillMaxWidth().testTag("category_row_${category.name}"), onClick = { editingCategory = category }) {
              Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(category, size = 38.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                  Text(category.name, style = MaterialTheme.typography.titleSmall)
                  Text(
                    if (type == TxType.EXPENSE) {
                      val group = if (category.group == CategoryGroup.ESSENTIAL) "Essential" else "Flexible"
                      "$group · ${limitSummary(category)}"
                    } else "Income",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = "Edit ${category.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }

  val categoryDialogType = newCategoryType
  if (editingCategory != null || categoryDialogType != null) {
    CategoryEditorDialog(
      viewModel = viewModel,
      existing = editingCategory,
      newType = categoryDialogType ?: TxType.EXPENSE,
      allCategories = data.categories,
      onDismiss = {
        editingCategory = null
        newCategoryType = null
      }
    )
  }
}

@Composable
private fun limitSummary(category: CategoryEntity): String {
  val monthly = if (category.monthlyLimitMinor > 0) "${money(category.monthlyLimitMinor, whole = true)}/month" else null
  val weekly = if (category.weeklyLimitMinor > 0) "${money(category.weeklyLimitMinor, whole = true)}/week" else null
  return listOfNotNull(monthly, weekly).joinToString(" · ").ifEmpty { "No limits" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
  viewModel: MainViewModel,
  existing: CategoryEntity?,
  newType: TxType,
  allCategories: List<CategoryEntity>,
  onDismiss: () -> Unit
) {
  val symbol = LocalCurrency.current
  val type = existing?.type ?: newType
  var name by remember { mutableStateOf(existing?.name.orEmpty()) }
  var iconKey by remember { mutableStateOf(existing?.iconKey ?: if (type == TxType.INCOME) "salary" else "other") }
  var color by remember { mutableStateOf(existing?.color ?: CategoryPalette[4].toArgb()) }
  val scope = rememberCoroutineScope()
  var emoji by remember { mutableStateOf(existing?.emoji) }
  var photo by remember { mutableStateOf(existing?.photoFile) }
  var look by remember { mutableStateOf(if (existing?.photoFile != null) "Photo" else if (existing?.emoji != null) "Emoji" else "Icon") }
  val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) scope.launch {
      viewModel.importCategoryPhoto(uri)?.let { name ->
        if (photo != null && photo != existing?.photoFile) viewModel.discardCategoryPhoto(photo!!)
        photo = name
      }
    }
  }
  var group by remember { mutableStateOf(existing?.group ?: CategoryGroup.FLEXIBLE) }
  var monthly by remember { mutableStateOf(existing?.monthlyLimitMinor?.takeIf { it > 0 }?.let(Money::toEditText).orEmpty()) }
  var weekly by remember { mutableStateOf(existing?.weeklyLimitMinor?.takeIf { it > 0 }?.let(Money::toEditText).orEmpty()) }
  var rollover by remember { mutableStateOf(existing?.rollover ?: false) }
  var deleteStep by remember { mutableStateOf<Int?>(null) } // null = none, otherwise number of entries to move
  var moveTarget by remember { mutableStateOf<Long?>(null) }
  val duplicate = allCategories.any { it.type == type && it.id != existing?.id && it.name.equals(name.trim(), ignoreCase = true) }
  val canSave = name.isNotBlank() && !duplicate

  FormSheet(
    onDismissRequest = onDismiss,
    title = { Text(if (existing == null) "New ${if (type == TxType.EXPENSE) "spending" else "income"} category" else "Edit category") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it.take(24) },
          label = { Text("Name") },
          isError = duplicate,
          supportingText = { if (duplicate) Text("You already have a category with this name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("category_name")
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          CategoryBadge(
            CategoryEntity(name = name, iconKey = iconKey, color = color, type = type, emoji = if (look == "Emoji") emoji else null, photoFile = if (look == "Photo") photo else null),
            size = 48.dp,
            modifier = Modifier.testTag("category_preview")
          )
          SegmentedToggle(
            options = listOf("Icon" to "Icon", "Emoji" to "Emoji", "Photo" to "Photo"),
            selected = look,
            onSelect = { look = it },
            modifier = Modifier.weight(1f).testTag("category_look")
          )
        }
        when (look) {
          "Icon" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryIcons.keys.forEach { key ->
              val selected = key == iconKey
              Box(
                Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(if (selected) Color(color).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                  .clickable(role = Role.RadioButton) { iconKey = key }
                  .semantics { contentDescription = "Icon $key" },
                contentAlignment = Alignment.Center
              ) {
                Icon(CategoryIcons.get(key), contentDescription = null, tint = if (selected) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
          "Emoji" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = emoji.orEmpty(),
              onValueChange = { typed -> emoji = firstEmoji(typed) },
              label = { Text("Type or paste an emoji") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("category_emoji")
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              SuggestedEmoji.forEach { option ->
                Box(
                  Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (option == emoji) Color(color).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(role = Role.RadioButton) { emoji = option }
                    .testTag("emoji_$option"),
                  contentAlignment = Alignment.Center
                ) { Text(option, fontSize = 22.sp) }
              }
            }
          }
          else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
              modifier = Modifier.testTag("category_photo_pick")
            ) { Text(if (photo == null) "Choose a photo" else "Choose another") }
            if (photo != null) {
              TextButton(
                onClick = {
                  if (photo != existing?.photoFile) viewModel.discardCategoryPhoto(photo!!)
                  photo = null
                },
                modifier = Modifier.testTag("category_photo_remove")
              ) { Text("Remove") }
            }
          }
        }
        Text("Colour", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          CategoryPalette.forEach { swatch ->
            val selected = swatch.toArgb() == color
            Box(
              Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(swatch)
                .clickable(role = Role.RadioButton) { color = swatch.toArgb() }
                .semantics { contentDescription = if (selected) "Selected colour" else "Colour" },
              contentAlignment = Alignment.Center
            ) {
              if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
            }
          }
        }
        if (type == TxType.EXPENSE) {
          Text("Budget group", style = MaterialTheme.typography.labelLarge)
          SegmentedToggle(
            options = listOf(CategoryGroup.ESSENTIAL to "Essential", CategoryGroup.FLEXIBLE to "Flexible"),
            selected = group,
            onSelect = { group = it },
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = monthly, onValueChange = { monthly = sanitizeAmount(it) },
            label = { Text("Monthly limit ($symbol)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("category_monthly")
          )
          OutlinedTextField(
            value = weekly, onValueChange = { weekly = sanitizeAmount(it) },
            label = { Text("Weekly limit ($symbol)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("category_weekly")
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("Carry over what is left", style = MaterialTheme.typography.titleSmall)
              Text("Unspent monthly budget is added to next month.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = rollover, onCheckedChange = { rollover = it }, modifier = Modifier.testTag("category_rollover"))
          }
        }
        if (existing != null) {
          TextButton(
            onClick = {
              scope.launch {
                val count = viewModel.transactionCountForCategory(existing.id)
                moveTarget = allCategories.firstOrNull { it.type == type && it.id != existing.id }?.id
                deleteStep = count
              }
            },
            modifier = Modifier.testTag("category_delete")
          ) { Text("Delete category", color = MaterialTheme.colorScheme.error) }
        }
      }
    },
    confirmButton = {
      TextButton(
        enabled = canSave,
        onClick = {
          viewModel.saveCategory(
            (existing ?: CategoryEntity(name = "", iconKey = iconKey, color = color, type = type)).copy(
              name = name.trim(),
              iconKey = iconKey,
              color = color,
              group = group,
              monthlyLimitMinor = if (type == TxType.EXPENSE) Money.parseMinor(monthly) else 0,
              weeklyLimitMinor = if (type == TxType.EXPENSE) Money.parseMinor(weekly) else 0,
              rollover = type == TxType.EXPENSE && rollover,
              emoji = if (look == "Emoji") emoji?.takeIf { it.isNotBlank() } else null,
              photoFile = if (look == "Photo") photo else null
            )
          )
          // A picture picked but then not used is not kept.
          if (photo != null && look != "Photo" && photo != existing?.photoFile) viewModel.discardCategoryPhoto(photo!!)
          onDismiss()
        },
        modifier = Modifier.testTag("category_save")
      ) { Text("Save") }
    },
    dismissButton = {
      TextButton(onClick = {
        if (photo != null && photo != existing?.photoFile) viewModel.discardCategoryPhoto(photo!!)
        onDismiss()
      }) { Text("Cancel") }
    }
  )

  val entries = deleteStep
  if (existing != null && entries != null) {
    val others = allCategories.filter { it.type == type && it.id != existing.id }
    var targetMenu by remember { mutableStateOf(false) }
    AlertDialog(
      onDismissRequest = { deleteStep = null },
      title = { Text("Delete ${existing.name}?") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          when {
            entries == 0 -> Text("No entries use this category.")
            others.isEmpty() -> Text("$entries entries use this category and there is no other ${if (type == TxType.EXPENSE) "spending" else "income"} category to move them to. Add another category first.")
            else -> {
              Text("$entries ${if (entries == 1) "entry uses" else "entries use"} this category. Move ${if (entries == 1) "it" else "them"} to:")
              Box {
                OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth().testTag("move_entries_to")) {
                  Text(others.firstOrNull { it.id == moveTarget }?.name ?: "Choose", modifier = Modifier.weight(1f))
                  Icon(Icons.Rounded.ExpandMore, contentDescription = null)
                }
                DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                  others.forEach { option ->
                    DropdownMenuItem(
                      text = { Text(option.name) },
                      onClick = {
                        moveTarget = option.id
                        targetMenu = false
                      },
                      modifier = Modifier.testTag("move_target_${option.name}")
                    )
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          enabled = entries == 0 || (others.isNotEmpty() && moveTarget != null),
          onClick = {
            viewModel.deleteCategory(existing, if (entries > 0) moveTarget else null)
            deleteStep = null
            onDismiss()
          },
          modifier = Modifier.testTag("category_delete_confirm")
        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { deleteStep = null }) { Text("Keep") } }
    )
  }
}


/** Keeps only the first emoji (or other symbol) of whatever was typed or pasted. */
internal fun firstEmoji(typed: String): String? {
  val text = typed.trim()
  if (text.isEmpty()) return null
  val iterator = java.text.BreakIterator.getCharacterInstance()
  iterator.setText(text)
  return text.substring(0, iterator.next().takeIf { it > 0 } ?: text.length)
}

private val SuggestedEmoji = listOf(
  "\uD83C\uDF54", "\uD83C\uDF55", "\u2615", "\uD83D\uDED2", "\uD83E\uDD66", "\uD83C\uDF4E", "\uD83C\uDF77", "\uD83D\uDE8C", "\uD83D\uDE97", "\u26FD",
  "\u2708\uFE0F", "\uD83C\uDFE0", "\uD83D\uDCA1", "\uD83D\uDCF1", "\uD83D\uDCBB", "\uD83C\uDFAC", "\uD83C\uDFAE", "\uD83C\uDFB5", "\uD83D\uDCDA", "\uD83C\uDF93",
  "\uD83D\uDC8A", "\uD83C\uDFE5", "\uD83C\uDFCB\uFE0F", "\uD83D\uDC55", "\uD83D\uDC5F", "\uD83C\uDF81", "\uD83D\uDC36", "\uD83D\uDC31", "\uD83E\uDDD2", "\uD83D\uDC87",
  "\uD83D\uDD27", "\uD83E\uDDFE", "\uD83D\uDCB0", "\uD83D\uDCB3", "\uD83C\uDFE6", "\uD83D\uDCC8", "\uD83C\uDF89", "\u2728", "\uD83C\uDF34", "\u2764\uFE0F"
)
