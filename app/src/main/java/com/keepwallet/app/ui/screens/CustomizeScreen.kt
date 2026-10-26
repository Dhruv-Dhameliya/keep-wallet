package com.keepwallet.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepwallet.app.data.WidgetKind
import com.keepwallet.app.ui.MainViewModel
import com.keepwallet.app.ui.components.AppCard
import com.keepwallet.app.ui.components.SubScreenHeader
import kotlin.math.roundToInt

private val RowHeight = 76.dp

/**
 * Holds the order while a section is being dragged. Rows sit in fixed-height slots; as the finger crosses
 * into a neighbour's slot the two swap, and the order is saved when the finger lifts.
 */
@Stable
private class ReorderState(
  initial: List<Long>,
  private val rowPx: Float,
  private val onLift: () -> Unit,
  private val onStep: () -> Unit,
  private val onCommit: (List<Long>) -> Unit
) {
  var order by mutableStateOf(initial)
  var draggingId by mutableStateOf<Long?>(null)
  var dragOffset by mutableFloatStateOf(0f)

  fun start(id: Long) {
    draggingId = id
    dragOffset = 0f
    onLift()
  }

  fun dragBy(id: Long, dy: Float) {
    dragOffset += dy
    val current = order.indexOf(id)
    if (current < 0) return
    val target = ((current * rowPx + dragOffset) / rowPx).roundToInt().coerceIn(0, order.lastIndex)
    if (target != current) {
      order = order.toMutableList().also {
        it.removeAt(current)
        it.add(target, id)
      }
      // Keep the finger and the row together after the neighbours have shifted.
      dragOffset -= (target - current) * rowPx
      onStep()
    }
  }

  fun end() {
    if (draggingId == null) return
    draggingId = null
    dragOffset = 0f
    onCommit(order)
  }
}

/**
 * Choose what the home screen shows. Press a section and drag it to where you want it; the new order is
 * saved when you let go. The same moves are available as accessibility actions for screen reader users.
 */
@Composable
fun CustomizeScreen(viewModel: MainViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
  val data by viewModel.data.collectAsStateWithLifecycle()
  val sorted = remember(data.widgets) { data.widgets.sortedBy { it.position } }
  val byId = remember(sorted) { sorted.associateBy { it.id } }
  val rowPx = with(LocalDensity.current) { RowHeight.toPx() }
  val haptics = LocalHapticFeedback.current
  val reorder = remember(rowPx) {
    ReorderState(
      initial = sorted.map { it.id },
      rowPx = rowPx,
      onLift = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
      onStep = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
      onCommit = { viewModel.reorderWidgets(it) }
    )
  }
  var addMenu by remember { mutableStateOf(false) }

  // Follow the database (after an add or remove) unless a drag is in progress.
  LaunchedEffect(sorted.map { it.id }) { if (reorder.draggingId == null) reorder.order = sorted.map { it.id } }

  Column(modifier.fillMaxSize()) {
    SubScreenHeader("Customize home", onBack)
    Column(
      Modifier
        .weight(1f)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)
        .testTag("customize_page"),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        "Press and drag a section to move it. Use the switch to show or hide it. Changes are saved automatically.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      LayoutsCard(viewModel)
      AppCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(RowHeight * reorder.order.size).testTag("customize_list")) {
          reorder.order.forEachIndexed { index, id ->
            key(id) {
              val widget = byId[id]
              if (widget != null) {
                val dragging = id == reorder.draggingId
                val slotY = index * rowPx
                val settled by animateFloatAsState(slotY, spring(stiffness = Spring.StiffnessMediumLow), label = "slot")
                val y = if (dragging) slotY + reorder.dragOffset else settled
                Box(
                  Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .zIndex(if (dragging) 1f else 0f)
                    .offset { IntOffset(0, y.roundToInt()) }
                    .then(
                      if (dragging) Modifier.shadow(10.dp, RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                      else Modifier
                    )
                    .pointerInput(id) {
                      detectDragGesturesAfterLongPress(
                        onDragStart = { reorder.start(id) },
                        onDrag = { change, amount -> change.consume(); reorder.dragBy(id, amount.y) },
                        onDragEnd = { reorder.end() },
                        onDragCancel = { reorder.end() }
                      )
                    }
                    .semantics {
                      customActions = listOf(
                        CustomAccessibilityAction("Move up") { viewModel.moveWidget(id, -1); true },
                        CustomAccessibilityAction("Move down") { viewModel.moveWidget(id, 1); true }
                      )
                    }
                    .testTag("customize_row_${widget.kind.name}")
                ) {
                  Row(Modifier.fillMaxSize().padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      Modifier
                        .size(48.dp)
                        .pointerInput(id) {
                          detectDragGestures(
                            onDragStart = { reorder.start(id) },
                            onDrag = { change, amount -> change.consume(); reorder.dragBy(id, amount.y) },
                            onDragEnd = { reorder.end() },
                            onDragCancel = { reorder.end() }
                          )
                        }
                        .testTag("drag_handle_${widget.kind.name}"),
                      contentAlignment = Alignment.Center
                    ) {
                      Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = "Drag to reorder ${WidgetTitle(widget.kind)}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                      Text(WidgetTitle(widget.kind), style = MaterialTheme.typography.titleSmall)
                      Text(
                        WidgetDescription(widget.kind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                      )
                    }
                    IconButton(onClick = { viewModel.removeWidget(widget.id) }, modifier = Modifier.testTag("remove_${widget.kind.name}")) {
                      Icon(Icons.Rounded.Delete, contentDescription = "Remove ${WidgetTitle(widget.kind)}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                      checked = widget.enabled,
                      onCheckedChange = { viewModel.setWidgetEnabled(widget.id, it) },
                      modifier = Modifier.testTag("toggle_${widget.kind.name}")
                    )
                  }
                  if (index < reorder.order.lastIndex && !dragging) {
                    HorizontalDivider(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                  }
                }
              }
            }
          }
        }
      }
      Box {
        OutlinedButton(onClick = { addMenu = true }, modifier = Modifier.fillMaxWidth().testTag("add_section")) {
          Icon(Icons.Rounded.Add, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Add section")
        }
        DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
          WidgetKind.entries.forEach { kind ->
            DropdownMenuItem(
              text = {
                Column {
                  Text(WidgetTitle(kind))
                  Text(WidgetDescription(kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              },
              onClick = {
                viewModel.addWidget(kind)
                addMenu = false
              },
              modifier = Modifier.testTag("add_${kind.name}")
            )
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

/** Ready-made Home arrangements and the ones you saved: one tap rearranges Home, and you can save the current one. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LayoutsCard(viewModel: MainViewModel) {
  val saved by viewModel.layouts.collectAsStateWithLifecycle()
  var naming by remember { mutableStateOf(false) }
  AppCard(Modifier.fillMaxWidth().testTag("layouts_card")) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Layouts", style = MaterialTheme.typography.titleSmall)
      androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        com.keepwallet.app.data.BuiltInLayouts.all.forEach { layout ->
          androidx.compose.material3.AssistChip(
            onClick = { viewModel.applyLayout(layout) },
            label = { Text(layout.name) },
            modifier = Modifier.testTag("layout_${layout.name.lowercase().replace(' ', '_')}")
          )
        }
      }
      saved.forEach { layout ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("saved_layout_${layout.name}")) {
          Text(layout.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
          androidx.compose.material3.TextButton(onClick = { viewModel.applyLayout(layout) }, modifier = Modifier.testTag("apply_layout_${layout.name}")) { Text("Apply") }
          IconButton(onClick = { viewModel.deleteLayout(layout.name) }, modifier = Modifier.testTag("delete_layout_${layout.name}")) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete layout ${layout.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      OutlinedButton(onClick = { naming = true }, modifier = Modifier.fillMaxWidth().testTag("save_layout")) { Text("Save this layout") }
    }
  }
  if (naming) {
    var name by remember { mutableStateOf("") }
    com.keepwallet.app.ui.components.FormSheet(
      onDismissRequest = { naming = false },
      title = { Text("Name this layout") },
      text = {
        androidx.compose.material3.OutlinedTextField(
          value = name, onValueChange = { name = it.take(24) }, label = { Text("Name") }, singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("layout_name")
        )
      },
      confirmButton = {
        androidx.compose.material3.TextButton(
          enabled = name.isNotBlank(),
          onClick = {
            viewModel.saveCurrentLayout(name)
            naming = false
          },
          modifier = Modifier.testTag("layout_save_confirm")
        ) { Text("Save") }
      },
      dismissButton = { androidx.compose.material3.TextButton(onClick = { naming = false }) { Text("Cancel") } }
    )
  }
}
