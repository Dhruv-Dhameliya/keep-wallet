package com.keepwallet.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Keep Wallet logo. It is the supplied artwork with a transparent background, so it can be tinted for any
 * surface: white on the balance card, ink on a page, light on dark.
 */
@Composable
fun KeepWalletMark(
  modifier: Modifier = Modifier,
  size: Dp = 24.dp,
  color: Color = Color.White,
  @Suppress("UNUSED_PARAMETER") knockout: Color = Color.Unspecified,
  @Suppress("UNUSED_PARAMETER") coin: Color = Color.Unspecified
) {
  androidx.compose.foundation.Image(
    painter = androidx.compose.ui.res.painterResource(if (size <= 64.dp) com.keepwallet.app.R.drawable.logo_mark_flat else com.keepwallet.app.R.drawable.logo_mark),
    contentDescription = null,
    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color),
    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
    modifier = modifier.size(size)
  )
}

/**
 * An amount with the whole part large and the cents small, the way a card statement sets a balance.
 * Text that doesn't end in two decimals (a whole-number amount, hidden dots) is drawn as one size.
 */
@Composable
fun AmountText(
  text: String,
  style: TextStyle,
  modifier: Modifier = Modifier,
  color: Color = Color.Unspecified,
  centsScale: Float = 0.58f
) {
  val dot = text.lastIndexOf('.')
  val hasCents = dot > 0 && text.length - dot == 3 && text.substring(dot + 1).all { it.isDigit() }
  val shown = if (hasCents) {
    buildAnnotatedString {
      append(text.substring(0, dot))
      withStyle(SpanStyle(fontSize = style.fontSize * centsScale, fontWeight = FontWeight.SemiBold)) { append(text.substring(dot)) }
    }
  } else {
    buildAnnotatedString { append(text) }
  }
  Text(shown, style = style, color = color, maxLines = 1, softWrap = false, modifier = modifier)
}

/** A chevron that turns upside down when its section is open. */
@Composable
fun ChevronToggle(expanded: Boolean, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
  val angle by animateFloatAsState(if (expanded) 180f else 0f, tween(220), label = "chevron")
  Icon(
    Icons.Rounded.ExpandMore,
    contentDescription = if (expanded) "Collapse" else "Expand",
    tint = tint,
    modifier = modifier.rotate(angle)
  )
}

/** Reveals [content] below a header with a smooth height animation. */
@Composable
fun ExpandArea(visible: Boolean, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = expandVertically(tween(com.keepwallet.app.ui.theme.Motion.Medium, easing = com.keepwallet.app.ui.theme.Motion.Standard)) + fadeIn(tween(com.keepwallet.app.ui.theme.Motion.Medium)),
    exit = shrinkVertically(tween(com.keepwallet.app.ui.theme.Motion.Medium, easing = com.keepwallet.app.ui.theme.Motion.Standard)) + fadeOut(tween(com.keepwallet.app.ui.theme.Motion.Short))
  ) {
    Column(content = content)
  }
}

/** Small capitals label above a figure, e.g. "SPENT THIS MONTH". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines: Int = 1) {
  Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelSmall,
    letterSpacing = 1.1.sp,
    color = color,
    maxLines = maxLines,
    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    modifier = modifier
  )
}

/** Two icon buttons, list and grid, for screens whose items can be shown either way. */
@Composable
fun ViewModeToggle(grid: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .panel(RoundedCornerShape(14.dp), MaterialTheme.colorScheme.surfaceVariant, 0.5f)
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    listOf(false to "List view", true to "Grid view").forEach { (isGrid, label) ->
      val selected = grid == isGrid
      Box(
        Modifier
          .size(width = 34.dp, height = 30.dp)
          .clip(RoundedCornerShape(11.dp))
          .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
          .clickable(role = Role.RadioButton) { onChange(isGrid) }
          .semantics {
            contentDescription = label
            this.selected = selected
          }
          .testTag(if (isGrid) "view_grid" else "view_list"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          if (isGrid) Icons.Rounded.GridView else Icons.Rounded.ViewAgenda,
          contentDescription = null,
          tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(18.dp)
        )
      }
    }
  }
}

/** A small rounded label, e.g. the income and expense figures on the balance card. */
@Composable
fun StatPill(
  text: String,
  modifier: Modifier = Modifier,
  container: Color = Color.White.copy(alpha = 0.16f),
  content: Color = Color.White,
  textTag: String? = null,
  leading: (@Composable () -> Unit)? = null
) {
  Row(
    modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 5.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (leading != null) {
      leading()
      Spacer(Modifier.width(5.dp))
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, modifier = if (textTag != null) Modifier.testTag(textTag) else Modifier)
  }
}

/** A dot used as a small colour key. */
@Composable
fun ColorDot(color: Color, size: Dp = 10.dp) {
  Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Row that sits at the end of an expanded area and lines its buttons up. */
@Composable
fun ActionRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    content()
  }
}

/** Sizes for headline figures, so the whole app sets them the same way. */
object Figure {
  val hero: TextUnit = 46.sp
}

private class ConfettiPiece(
  val x: Float, val delay: Float, val speed: Float, val sway: Float, val phase: Float,
  val width: Float, val height: Float, val spin: Float, val colorIndex: Int
)

/**
 * A short burst of confetti falling over the whole screen. It only draws (no touch handling), so it never gets in the
 * way, and it re-plays whenever [burst] changes to a new non-zero number.
 */
@Composable
fun ConfettiBurst(burst: Int, modifier: Modifier = Modifier) {
  if (burst == 0) return
  val progress = androidx.compose.runtime.remember(burst) { androidx.compose.animation.core.Animatable(0f) }
  androidx.compose.runtime.LaunchedEffect(burst) {
    progress.animateTo(1f, androidx.compose.animation.core.tween(2600, easing = androidx.compose.animation.core.LinearEasing))
  }
  val scheme = MaterialTheme.colorScheme
  val colors = androidx.compose.runtime.remember(scheme) {
    listOf(scheme.primary, scheme.tertiary, scheme.secondary, Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF22C55E))
  }
  val pieces = androidx.compose.runtime.remember(burst) {
    val random = kotlin.random.Random(burst * 7919)
    List(72) {
      ConfettiPiece(
        x = random.nextFloat(), delay = random.nextFloat() * 0.25f, speed = 0.75f + random.nextFloat() * 0.5f,
        sway = 10f + random.nextFloat() * 22f, phase = random.nextFloat() * 6.28f,
        width = 6f + random.nextFloat() * 6f, height = 10f + random.nextFloat() * 8f,
        spin = (random.nextFloat() - 0.5f) * 720f, colorIndex = random.nextInt(6)
      )
    }
  }
  androidx.compose.foundation.Canvas(modifier.fillMaxSize()) {
    val t = progress.value
    if (t <= 0f || t >= 1f) return@Canvas
    pieces.forEach { p ->
      val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
      if (local <= 0f) return@forEach
      val x = p.x * size.width + kotlin.math.sin(local * 9f + p.phase) * p.sway.dp.toPx()
      val y = (-0.06f + local * p.speed * 1.15f) * size.height
      val alpha = if (local > 0.75f) (1f - local) / 0.25f else 1f
      rotate(p.spin * local, androidx.compose.ui.geometry.Offset(x, y)) {
        drawRect(
          colors[p.colorIndex].copy(alpha = alpha),
          topLeft = androidx.compose.ui.geometry.Offset(x - p.width.dp.toPx() / 2, y - p.height.dp.toPx() / 2),
          size = androidx.compose.ui.geometry.Size(p.width.dp.toPx(), p.height.dp.toPx())
        )
      }
    }
  }
}

