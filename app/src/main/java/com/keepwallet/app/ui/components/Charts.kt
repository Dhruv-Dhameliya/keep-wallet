package com.keepwallet.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepwallet.app.domain.Bucket
import com.keepwallet.app.domain.CategoryTotal
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.Treemap
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Rounds [value] up to a tidy axis maximum such as 100, 250, 500, 1000. */
internal fun niceAxisMax(value: Long): Long {
  if (value <= 0) return 100
  val v = value.toDouble()
  val magnitude = 10.0.pow(floor(log10(v)))
  val steps = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 7.5, 10.0)
  return (steps.first { it * magnitude >= v } * magnitude).toLong()
}

private fun axisMaxMinor(buckets: List<Bucket>): Long =
  niceAxisMax((buckets.maxOfOrNull { it.valueMinor } ?: 0L).let { if (it <= 0) 10_000L else it })

private fun describe(buckets: List<Bucket>, symbol: String): String =
  buckets.joinToString(", ") { "${it.label} ${Money.formatWhole(it.valueMinor, symbol)}" }

/** Which x labels to draw so they never collide: every n-th, always including the highlighted bucket. */
private fun labelStep(count: Int, widthPx: Float, minLabelPx: Float): Int =
  max(1, kotlin.math.ceil(count * minLabelPx / widthPx).toInt())

private class ChartGeometry(
  val left: Float, val top: Float, val width: Float, val height: Float
) {
  val bottom: Float get() = top + height
}

@Composable
fun BarChart(
  buckets: List<Bucket>,
  modifier: Modifier = Modifier,
  height: Dp = 170.dp,
  color: Color = MaterialTheme.colorScheme.primary
) {
  val symbol = LocalCurrency.current
  val measurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  val valueStyle = labelStyle.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val emptyColor = MaterialTheme.colorScheme.surfaceVariant
  var selected by remember(buckets) { mutableIntStateOf(-1) }
  val density = LocalDensity.current
  val reveal = rememberReveal(buckets)

  if (buckets.isEmpty()) return
  val axisMax = axisMaxMinor(buckets)
  val hasData = buckets.any { it.valueMinor > 0 }
  val showAllValues = buckets.size <= 8

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .semantics { contentDescription = "Bar chart. " + if (hasData) describe(buckets, symbol) else "No spending in this period" }
      .pointerInput(buckets) {
        detectTapGestures { tap ->
          val axisWidth = with(density) { 40.dp.toPx() }
          val plotWidth = size.width - axisWidth
          val index = ((tap.x - axisWidth) / (plotWidth / buckets.size)).toInt()
          selected = if (index in buckets.indices && index != selected) index else -1
        }
      }
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val axisLabels = listOf(0L, axisMax / 2, axisMax)
      val axisWidth = 40.dp.toPx()
      val topPad = 18.dp.toPx()
      val bottomPad = 20.dp.toPx()
      val geo = ChartGeometry(axisWidth, topPad, size.width - axisWidth, size.height - topPad - bottomPad)

      axisLabels.forEach { value ->
        val y = geo.bottom - geo.height * value / axisMax
        drawLine(gridColor, Offset(geo.left, y), Offset(geo.left + geo.width, y), strokeWidth = 1.dp.toPx())
        val label = measurer.measure(Money.formatCompact(value, symbol), labelStyle)
        drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
      }

      val slot = geo.width / buckets.size
      val barWidth = min(slot * 0.62f, 28.dp.toPx())
      val step = labelStep(buckets.size, geo.width, 26.dp.toPx())
      buckets.forEachIndexed { index, bucket ->
        val centerX = geo.left + slot * index + slot / 2
        val barHeight = geo.height * bucket.valueMinor / axisMax * reveal.value
        val isSelected = index == selected
        val barColor = when {
          bucket.valueMinor <= 0 -> emptyColor
          bucket.isCurrent || isSelected -> color
          else -> color.copy(alpha = 0.5f)
        }
        val h = max(barHeight, 3.dp.toPx())
        drawRoundRect(
          color = barColor,
          topLeft = Offset(centerX - barWidth / 2, geo.bottom - h),
          size = Size(barWidth, h),
          cornerRadius = CornerRadius(min(6.dp.toPx(), barWidth / 2))
        )
        if (bucket.valueMinor > 0 && (showAllValues || isSelected)) {
          drawCenteredText(measurer, Money.formatCompact(bucket.valueMinor, symbol), valueStyle, centerX, geo.bottom - h - 4.dp.toPx(), above = true, maxX = size.width)
        }
        if (index % step == 0 || bucket.isCurrent) {
          drawCenteredText(measurer, bucket.label, labelStyle, centerX, geo.bottom + 4.dp.toPx(), above = false, maxX = size.width)
        }
      }
    }
    if (!hasData) {
      Text(
        "No spending in this period",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .align(Alignment.Center)
          .padding(start = 40.dp)
          .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 4.dp)
      )
    }
  }
}

@Composable
fun LineChart(
  buckets: List<Bucket>,
  modifier: Modifier = Modifier,
  height: Dp = 170.dp,
  color: Color = MaterialTheme.colorScheme.primary
) {
  val symbol = LocalCurrency.current
  val measurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
  val valueStyle = labelStyle.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val surface = MaterialTheme.colorScheme.surface
  var selected by remember(buckets) { mutableIntStateOf(-1) }
  val density = LocalDensity.current
  val reveal = rememberReveal(buckets)

  if (buckets.isEmpty()) return
  val axisMax = axisMaxMinor(buckets)
  val hasData = buckets.any { it.valueMinor > 0 }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .semantics { contentDescription = "Line chart. " + if (hasData) describe(buckets, symbol) else "No spending in this period" }
      .pointerInput(buckets) {
        detectTapGestures { tap ->
          val axisWidth = with(density) { 40.dp.toPx() }
          val plotWidth = size.width - axisWidth
          val index = ((tap.x - axisWidth) / (plotWidth / buckets.size)).toInt()
          selected = if (index in buckets.indices && index != selected) index else -1
        }
      }
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val axisWidth = 40.dp.toPx()
      val topPad = 18.dp.toPx()
      val bottomPad = 20.dp.toPx()
      val geo = ChartGeometry(axisWidth, topPad, size.width - axisWidth, size.height - topPad - bottomPad)

      listOf(0L, axisMax / 2, axisMax).forEach { value ->
        val y = geo.bottom - geo.height * value / axisMax
        drawLine(gridColor, Offset(geo.left, y), Offset(geo.left + geo.width, y), strokeWidth = 1.dp.toPx())
        val label = measurer.measure(Money.formatCompact(value, symbol), labelStyle)
        drawText(label, topLeft = Offset(0f, y - label.size.height / 2f))
      }

      val slot = geo.width / buckets.size
      fun pointX(i: Int) = geo.left + slot * i + slot / 2
      fun pointY(i: Int) = geo.bottom - geo.height * buckets[i].valueMinor / axisMax * reveal.value

      if (hasData) {
        val line = Path().apply {
          moveTo(pointX(0), pointY(0))
          for (i in 1 until buckets.size) {
            val midX = (pointX(i - 1) + pointX(i)) / 2
            cubicTo(midX, pointY(i - 1), midX, pointY(i), pointX(i), pointY(i))
          }
        }
        val area = Path().apply {
          addPath(line)
          lineTo(pointX(buckets.lastIndex), geo.bottom)
          lineTo(pointX(0), geo.bottom)
          close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)), startY = geo.top, endY = geo.bottom))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
      }

      val step = labelStep(buckets.size, geo.width, 26.dp.toPx())
      val showDots = buckets.size <= 14
      buckets.forEachIndexed { index, bucket ->
        val x = pointX(index)
        val y = pointY(index)
        val isSelected = index == selected
        if (hasData && (showDots || isSelected || bucket.isCurrent)) {
          drawCircle(surface, radius = 5.dp.toPx(), center = Offset(x, y))
          drawCircle(color, radius = if (isSelected || bucket.isCurrent) 4.dp.toPx() else 3.dp.toPx(), center = Offset(x, y))
        }
        if (isSelected) {
          drawCenteredText(measurer, Money.formatCompact(bucket.valueMinor, symbol), valueStyle, x, y - 9.dp.toPx(), above = true, maxX = size.width)
        }
        if (index % step == 0 || bucket.isCurrent) {
          drawCenteredText(measurer, bucket.label, labelStyle, x, geo.bottom + 4.dp.toPx(), above = false, maxX = size.width)
        }
      }
    }
    if (!hasData) {
      Text(
        "No spending in this period",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
          .align(Alignment.Center)
          .padding(start = 40.dp)
          .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 4.dp)
      )
    }
  }
}

private fun DrawScope.drawCenteredText(
  measurer: TextMeasurer,
  text: String,
  style: TextStyle,
  centerX: Float,
  anchorY: Float,
  above: Boolean,
  maxX: Float
) {
  val layout: TextLayoutResult = measurer.measure(text, style, maxLines = 1)
  val x = (centerX - layout.size.width / 2f).coerceIn(0f, max(0f, maxX - layout.size.width))
  val y = if (above) anchorY - layout.size.height else anchorY
  drawText(layout, topLeft = Offset(x, y))
}

/** Donut chart of category shares with the total in the centre. */
@Composable
fun DonutChart(
  slices: List<CategoryTotal>,
  totalMinor: Long,
  centerLabel: String,
  modifier: Modifier = Modifier,
  size: Dp = 220.dp,
  thickness: Dp = 30.dp
) {
  val symbol = LocalCurrency.current
  val emptyTrack = MaterialTheme.colorScheme.surfaceVariant
  val reveal = rememberReveal(slices)
  Box(
    modifier = modifier
      .size(size)
      .semantics {
        contentDescription = "Donut chart. " + if (slices.isEmpty()) "No data" else
          slices.joinToString(", ") { "${it.category.name} ${it.percent} percent" }
      },
    contentAlignment = Alignment.Center
  ) {
    Canvas(Modifier.fillMaxSize().padding(thickness / 2)) {
      val stroke = thickness.toPx()
      val arcSize = Size(this.size.width, this.size.height)
      if (slices.isEmpty() || totalMinor <= 0) {
        drawArc(emptyTrack, 0f, 360f, useCenter = false, topLeft = Offset.Zero, size = arcSize, style = Stroke(stroke))
      } else {
        var start = -90f
        val gap = if (slices.size > 1) 2.5f else 0f
        slices.forEach { slice ->
          val sweep = slice.share * 360f * reveal.value
          drawArc(
            color = Color(slice.category.color),
            startAngle = start + gap / 2,
            sweepAngle = max(sweep - gap, 0.5f),
            useCenter = false,
            topLeft = Offset.Zero,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt)
          )
          start += sweep
        }
      }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      AutoFitText(
        text = Money.formatWhole(totalMinor, symbol),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = thickness + 2.dp)
      )
      Text(centerLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** Proportional "pocket" tiles: each category's area matches its share of spending. */
@Composable
fun TreemapChart(
  slices: List<CategoryTotal>,
  modifier: Modifier = Modifier,
  height: Dp = 260.dp,
  onSliceClick: ((CategoryTotal) -> Unit)? = null
) {
  val symbol = LocalCurrency.current
  BoxWithConstraints(modifier = modifier.fillMaxWidth().height(height)) {
    val gap = 4.dp
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    val cells = remember(slices, widthPx, heightPx) {
      Treemap.layout(slices.map { it to it.amountMinor.toDouble() }, widthPx, heightPx)
    }
    val density = LocalDensity.current
    cells.forEach { cell ->
      val slice = cell.item
      val tileColor = Color(slice.category.color)
      with(density) {
        val widthDp = cell.width.toDp()
        val heightDp = cell.height.toDp()
        Box(
          modifier = Modifier
            .offset(cell.x.toDp(), cell.y.toDp())
            .size(widthDp, heightDp)
            .padding(gap / 2)
            .clip(RoundedCornerShape(16.dp))
            .background(tileColor.copy(alpha = 0.2f))
            .let { if (onSliceClick != null) it.then(Modifier.pointerInput(slice) { detectTapGestures { onSliceClick(slice) } }) else it }
            .semantics { contentDescription = "${slice.category.name}, ${Money.formatWhole(slice.amountMinor, symbol)}, ${slice.percent} percent" }
        ) {
          if (widthDp > 56.dp && heightDp > 44.dp) {
            Column(
              modifier = Modifier.fillMaxSize().padding(10.dp),
              verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
            ) {
              if (widthDp >= 96.dp) {
                Box(
                  Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                  Text(slice.category.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              } else {
                Spacer(Modifier.height(0.dp))
              }
              Column {
                Text(
                  Money.formatWhole(slice.amountMinor, symbol),
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  slice.percentLabel,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }
  }
}

/** A 0 → 1 animation that restarts whenever [key] changes; read `.value` inside a draw block to avoid recomposing. */
@Composable
private fun rememberReveal(key: Any?): androidx.compose.animation.core.Animatable<Float, androidx.compose.animation.core.AnimationVector1D> {
  val progress = remember { androidx.compose.animation.core.Animatable(0f) }
  androidx.compose.runtime.LaunchedEffect(key) {
    progress.snapTo(0f)
    progress.animateTo(1f, androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing))
  }
  return progress
}
