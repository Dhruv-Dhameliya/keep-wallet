package com.keepwallet.app.domain

import kotlin.math.max
import kotlin.math.min

data class TreemapCell<T>(val item: T, val x: Float, val y: Float, val width: Float, val height: Float)

/** Squarified treemap layout that fills a [width] x [height] area proportionally to each item's value. */
object Treemap {
  fun <T> layout(items: List<Pair<T, Double>>, width: Float, height: Float): List<TreemapCell<T>> {
    val sorted = items.filter { it.second > 0.0 }.sortedByDescending { it.second }
    if (sorted.isEmpty() || width <= 0f || height <= 0f) return emptyList()

    val total = sorted.sumOf { it.second }
    val scale = (width.toDouble() * height) / total
    val areas = sorted.map { (it.second * scale).toFloat() }

    val cells = mutableListOf<TreemapCell<T>>()
    var x = 0f
    var y = 0f
    var w = width
    var h = height
    var index = 0
    while (index < sorted.size) {
      val side = min(w, h)
      var end = index + 1
      var rowSum = areas[index]
      while (end < sorted.size) {
        val candidate = rowSum + areas[end]
        if (worstRatio(areas, index, end + 1, candidate, side) <= worstRatio(areas, index, end, rowSum, side)) {
          rowSum = candidate
          end++
        } else {
          break
        }
      }
      if (w >= h) {
        val columnWidth = rowSum / h
        var cy = y
        for (i in index until end) {
          val cellHeight = areas[i] / columnWidth
          cells += TreemapCell(sorted[i].first, x, cy, columnWidth, cellHeight)
          cy += cellHeight
        }
        x += columnWidth
        w -= columnWidth
      } else {
        val rowHeight = rowSum / w
        var cx = x
        for (i in index until end) {
          val cellWidth = areas[i] / rowHeight
          cells += TreemapCell(sorted[i].first, cx, y, cellWidth, rowHeight)
          cx += cellWidth
        }
        y += rowHeight
        h -= rowHeight
      }
      index = end
    }
    return cells
  }

  private fun worstRatio(areas: List<Float>, from: Int, to: Int, sum: Float, side: Float): Float {
    var maxArea = Float.MIN_VALUE
    var minArea = Float.MAX_VALUE
    for (i in from until to) {
      maxArea = max(maxArea, areas[i])
      minArea = min(minArea, areas[i])
    }
    val sideSquared = side * side
    val sumSquared = sum * sum
    return max(sideSquared * maxArea / sumSquared, sumSquared / (sideSquared * minArea))
  }
}
