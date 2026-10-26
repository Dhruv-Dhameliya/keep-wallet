package com.keepwallet.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.keepwallet.app.domain.MonthFigure
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** A smooth line of what you own at each month end, with a soft fill under it. */
@Composable
fun NetWorthChart(points: List<Pair<YearMonth, Long>>, modifier: Modifier = Modifier) {
  val line = MaterialTheme.colorScheme.primary
  val grid = MaterialTheme.colorScheme.outlineVariant
  Column(modifier.testTag("net_worth_chart")) {
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
      if (points.size < 2) return@Canvas
      val values = points.map { it.second.toFloat() }
      val low = values.min()
      val high = values.max()
      val span = (high - low).takeIf { it > 0f } ?: 1f
      val padTop = 8.dp.toPx()
      val padBottom = 8.dp.toPx()
      val usable = size.height - padTop - padBottom
      fun x(i: Int) = size.width * i / (points.size - 1)
      fun y(v: Float) = padTop + usable * (1f - (v - low) / span)
      drawLine(grid, Offset(0f, size.height - padBottom), Offset(size.width, size.height - padBottom), 1.dp.toPx())
      val path = Path().apply {
        moveTo(x(0), y(values[0]))
        for (i in 1 until points.size) {
          // Gentle curve: control points halfway across, at each end's height.
          val mid = (x(i - 1) + x(i)) / 2f
          cubicTo(mid, y(values[i - 1]), mid, y(values[i]), x(i), y(values[i]))
        }
      }
      val fill = Path().apply {
        addPath(path)
        lineTo(size.width, size.height - padBottom)
        lineTo(0f, size.height - padBottom)
        close()
      }
      drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), line.copy(alpha = 0f))))
      drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
      drawCircle(line, 4.dp.toPx(), Offset(x(points.size - 1), y(values.last())))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(points.firstOrNull()?.first?.let(::shortMonth).orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(points.lastOrNull()?.first?.let(::shortMonth).orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

private fun shortMonth(month: YearMonth): String = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + month.year

/** Twelve columns, one per month: what went out, with a thin marker for what came in. */
@Composable
fun MonthBars(months: List<MonthFigure>, modifier: Modifier = Modifier) {
  val spent = MaterialTheme.colorScheme.primary
  val income = MaterialTheme.extraIncome()
  val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
  val top = months.maxOfOrNull { maxOf(it.spentMinor, it.incomeMinor) }?.takeIf { it > 0 } ?: 1L
  Row(modifier.fillMaxWidth().height(120.dp).testTag("month_bars"), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    months.forEach { m ->
      Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
          Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
            val barW = size.width * 0.7f
            val left = (size.width - barW) / 2f
            val spentH = size.height * (m.spentMinor.toFloat() / top)
            if (spentH > 0f) drawRoundRect(spent.copy(alpha = if (m.hasActivity) 1f else 0.2f), Offset(left, size.height - spentH), Size(barW, spentH), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            else drawRoundRect(spent.copy(alpha = 0.15f), Offset(left, size.height - 3.dp.toPx()), Size(barW, 3.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            val incomeY = size.height * (1f - m.incomeMinor.toFloat() / top)
            if (m.incomeMinor > 0) drawLine(income, Offset(left - 2.dp.toPx(), incomeY), Offset(left + barW + 2.dp.toPx(), incomeY), 2.dp.toPx(), StrokeCap.Round)
          }
        }
        Text(
          m.month.month.getDisplayName(TextStyle.NARROW, locale),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun MaterialTheme.extraIncome() = com.keepwallet.app.ui.theme.LocalExtraColors.current.income
