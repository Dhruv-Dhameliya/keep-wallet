package com.keepwallet.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepwallet.app.data.CategoryEntity
import com.keepwallet.app.data.TransactionEntity
import com.keepwallet.app.data.TxType
import com.keepwallet.app.domain.Money
import com.keepwallet.app.ui.theme.CategoryIcons

import com.keepwallet.app.ui.theme.extra
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val LocalCurrency = compositionLocalOf { "$" }

@Composable
fun money(minor: Long, whole: Boolean = false, sign: Boolean = false): String {
  val symbol = LocalCurrency.current
  return if (whole) Money.formatWhole(minor, symbol) else Money.format(minor, symbol, forceSign = sign)
}

/**
 * The app's card: a Material 3 elevated card (or an outlined one when [border] is given). Pass [container] for a
 * coloured card (tinted pockets, highlights). A tappable card gets the standard M3 ripple and state layer.
 */
@Composable
fun AppCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
  border: Color? = null,
  content: @Composable ColumnScope.() -> Unit
) {
  val shape = MaterialTheme.shapes.large
  val colors = CardDefaults.cardColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface)
  // Plain cards are flat with a fine outline; coloured cards (highlights, pockets) carry no outline at all.
  val plain = container == MaterialTheme.colorScheme.surfaceContainerLow
  val stroke = border?.let { BorderStroke(1.dp, it) } ?: if (plain) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
  val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
  if (onClick != null) {
    Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = stroke, content = content)
  } else {
    Card(modifier = modifier, shape = shape, colors = colors, elevation = elevation, border = stroke, content = content)
  }
}

@Composable
fun SectionTitle(
  title: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    if (actionLabel != null && onAction != null) {
      TextButton(onClick = onAction) {
        Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
      }
    }
  }
}

/** Material 3 single-choice segmented button, such as Expense | Income or Week | Month. */
@Composable
fun <T> SegmentedToggle(
  options: List<Pair<T, String>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier
) {
  SingleChoiceSegmentedButtonRow(modifier = modifier) {
    options.forEachIndexed { index, (value, label) ->
      SegmentedButton(
        selected = value == selected,
        onClick = { onSelect(value) },
        shape = SegmentedButtonDefaults.itemShape(index, options.size),
        colors = SegmentedButtonDefaults.colors(
          activeContainerColor = MaterialTheme.colorScheme.primary,
          activeContentColor = MaterialTheme.colorScheme.onPrimary,
          activeBorderColor = MaterialTheme.colorScheme.primary,
          inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
          inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.testTag("segment_$label"),
        icon = { SegmentedButtonDefaults.Icon(active = value == selected, activeContent = { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize)) }) },
        label = {
          Text(
            label,
            style = if (options.size > 2) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false
          )
        }
      )
    }
  }
}

@Composable
fun CategoryBadge(
  iconKey: String,
  color: Color,
  modifier: Modifier = Modifier,
  size: Dp = 40.dp
) {
  Box(
    modifier = modifier
      .size(size)
      .clip(CircleShape)
      .background(color.copy(alpha = 0.18f)),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = CategoryIcons.get(iconKey),
      contentDescription = null,
      tint = color,
      modifier = Modifier.size(size * 0.55f)
    )
  }
}

/** Loads a small stored picture by file name (cached); provided by the activity, empty in previews and tests. */
val LocalPhotoLoader = androidx.compose.runtime.staticCompositionLocalOf<(String) -> androidx.compose.ui.graphics.ImageBitmap?> { { null } }

/** The round badge of a category: its picture, its emoji, or its icon on a tinted disc. */
@Composable
fun CategoryBadge(category: CategoryEntity?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
  val photo = category?.photoFile?.let { name -> val load = LocalPhotoLoader.current; androidx.compose.runtime.remember(name) { load(name) } }
  val color = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurfaceVariant
  when {
    photo != null -> androidx.compose.foundation.Image(
      bitmap = photo,
      contentDescription = null,
      contentScale = androidx.compose.ui.layout.ContentScale.Crop,
      modifier = modifier.size(size).clip(CircleShape)
    )
    category?.emoji != null -> Box(
      modifier = modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
      contentAlignment = Alignment.Center
    ) { Text(category.emoji, fontSize = (size.value * 0.5f).sp, maxLines = 1, softWrap = false) }
    else -> CategoryBadge(iconKey = category?.iconKey ?: "other", color = color, modifier = modifier, size = size)
  }
}

@Composable
fun AppProgressBar(
  fraction: Float,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
  track: Color = MaterialTheme.colorScheme.surfaceVariant,
  height: Dp = 8.dp
) {
  val clamped = fraction.coerceIn(0f, 1f)
  // Starts empty and fills to the value, then follows any later change smoothly.
  var target by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
  androidx.compose.runtime.LaunchedEffect(clamped) { target = clamped }
  val shown by androidx.compose.animation.core.animateFloatAsState(target, MaterialTheme.motionScheme.slowEffectsSpec(), label = "progress")
  LinearProgressIndicator(
    progress = { shown },
    modifier = modifier.fillMaxWidth().height(height),
    color = color,
    trackColor = track,
    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
    gapSize = 0.dp,
    drawStopIndicator = {}
  )
}

/** "< October 2026 >" style navigator. Tapping the label can open a picker. */
@Composable
fun PeriodNavigator(
  label: String,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
  onLabelClick: (() -> Unit)? = null,
  previousDescription: String = "Previous",
  nextDescription: String = "Next"
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically
  ) {
    IconButton(onClick = onPrevious) {
      Icon(Icons.Rounded.ChevronLeft, contentDescription = previousDescription)
    }
    val labelModifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .let { if (onLabelClick != null) it.clickable(role = Role.Button, onClick = onLabelClick) else it }
      .padding(horizontal = 8.dp, vertical = 8.dp)
    Text(
      label,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = labelModifier,
      maxLines = 1
    )
    IconButton(onClick = onNext) {
      Icon(Icons.Rounded.ChevronRight, contentDescription = nextDescription)
    }
  }
}

@Composable
fun EmptyState(
  icon: ImageVector,
  title: String,
  body: String,
  modifier: Modifier = Modifier,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 28.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(56.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
    }
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Text(
      body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center
    )
    if (actionLabel != null && onAction != null) {
      Spacer(Modifier.height(4.dp))
      Button(onClick = onAction) { Text(actionLabel) }
    }
  }
}

/** One transaction line, shared by Home, Activity and the review lists. */
@Composable
fun TransactionRow(
  transaction: TransactionEntity,
  category: CategoryEntity?,
  accountName: String?,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  showTime: Boolean = false,
  /** Draw the row as its own tonal card (turn off when it already sits inside a card). */
  framed: Boolean = true,
  onClick: (() -> Unit)? = null
) {
  val isTransfer = transaction.type == TxType.TRANSFER_OUT || transaction.type == TxType.TRANSFER_IN
  val isIncome = transaction.type == TxType.INCOME || transaction.type == TxType.TRANSFER_IN
  val clickModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
  val shape = RoundedCornerShape(if (framed) 16.dp else 12.dp)
  Row(
    modifier = modifier
      .fillMaxWidth()
      .let { if (framed) it.padding(vertical = 2.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow) else it.clip(shape) }
      .then(clickModifier)
      .padding(vertical = if (framed) 7.dp else 6.dp, horizontal = if (framed) 12.dp else 0.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (isTransfer) {
      Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(androidx.compose.material.icons.Icons.Rounded.SwapHoriz, contentDescription = "Transfer", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(22.dp))
      }
    } else {
      CategoryBadge(category, size = 36.dp)
    }
    Spacer(Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        transaction.merchant,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        subtitle ?: listOfNotNull(category?.name, accountName).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
      Text(
        money(if (isIncome) transaction.amountMinor else -transaction.amountMinor, sign = isIncome),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = when {
          isTransfer -> MaterialTheme.colorScheme.secondary
          isIncome -> MaterialTheme.extra.income
          else -> MaterialTheme.colorScheme.onSurface
        }
      )
      if (showTime) {
        Text(
          timeFormatter.format(Instant.ofEpochMilli(transaction.timestamp).atZone(ZoneId.systemDefault())),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Shrinks big headline figures as they get longer so they stay on one line (e.g. ₹12,34,56,789). */
fun heroFontSize(text: String, base: Int): androidx.compose.ui.unit.TextUnit {
  val size = when {
    text.length <= 9 -> base
    text.length <= 12 -> (base * 0.8f).toInt()
    text.length <= 15 -> (base * 0.65f).toInt()
    else -> (base * 0.52f).toInt()
  }
  return size.sp
}

/**
 * A row that spreads its children across the width but wraps the last ones onto a new line when they
 * don't fit (small screens, large system fonts, very large amounts) instead of squeezing them.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ResponsiveRow(
  modifier: Modifier = Modifier,
  verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
  content: @Composable () -> Unit
) {
  androidx.compose.foundation.layout.FlowRow(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalArrangement = Arrangement.spacedBy(4.dp),
    itemVerticalAlignment = verticalAlignment
  ) { content() }
}

/** Single-line text that shrinks until it fits its width, for figures that can get very long. */
@Composable
fun AutoFitText(
  text: String,
  style: androidx.compose.ui.text.TextStyle,
  modifier: Modifier = Modifier,
  minSize: Float = 10f,
  color: Color = Color.Unspecified
) {
  var size by androidx.compose.runtime.remember(text, style) {
    androidx.compose.runtime.mutableFloatStateOf(style.fontSize.value.takeIf { !it.isNaN() } ?: 16f)
  }
  Text(
    text = text,
    style = style.copy(fontSize = size.sp),
    color = color,
    maxLines = 1,
    softWrap = false,
    modifier = modifier,
    onTextLayout = { if (it.didOverflowWidth && size > minSize) size = (size * 0.92f).coerceAtLeast(minSize) }
  )
}

/** Title bar with a back arrow, for full-screen pages such as Balance or Customize: a Material 3 top app bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
  TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    modifier = modifier,
    navigationIcon = {
      IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
      }
    },
    actions = { trailing?.invoke() },
    windowInsets = WindowInsets(0),
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
  )
}

/** The title bar of a top-level tab: a Material 3 top app bar with the page title and optional actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, modifier: Modifier = Modifier, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
  TopAppBar(
    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    modifier = modifier,
    actions = actions,
    windowInsets = WindowInsets(0),
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
  )
}

/** A slim "< October 2026 >" control for top bars: small chevrons, one line of text, tap the text to pick. */
@Composable
fun CompactPeriodNav(
  label: String,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
  onLabelClick: (() -> Unit)? = null,
  previousDescription: String = "Previous",
  nextDescription: String = "Next"
) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onPrevious, modifier = Modifier.size(34.dp)) {
      Icon(Icons.Rounded.ChevronLeft, contentDescription = previousDescription, modifier = Modifier.size(22.dp))
    }
    Text(
      label,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .let { if (onLabelClick != null) it.clickable(role = Role.Button, onClick = onLabelClick) else it }
        .padding(horizontal = 4.dp, vertical = 6.dp)
    )
    IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
      Icon(Icons.Rounded.ChevronRight, contentDescription = nextDescription, modifier = Modifier.size(22.dp))
    }
  }
}
