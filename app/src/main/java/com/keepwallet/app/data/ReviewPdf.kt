package com.keepwallet.app.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.keepwallet.app.domain.Money
import com.keepwallet.app.domain.Periods
import com.keepwallet.app.domain.ReviewReport
import java.io.OutputStream

/**
 * Draws a review as a plain, printable PDF: figures, categories, month by month and net worth. Drawn with the
 * platform's own PDF writer, so nothing is sent anywhere and no library is needed.
 */
object ReviewPdf {
  private const val WIDTH = 595
  private const val HEIGHT = 842
  private const val MARGIN = 44f

  fun write(output: OutputStream, report: ReviewReport, symbol: String, appName: String = "Keep Wallet") {
    val document = PdfDocument()
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14171A.toInt(); textSize = 11f }
    val muted = Paint(ink).apply { color = 0xFF59616A.toInt(); textSize = 10f }
    val heading = Paint(ink).apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val title = Paint(ink).apply { textSize = 24f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val right = Paint(ink).apply { textAlign = Paint.Align.RIGHT }
    val rightBold = Paint(right).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
    val rule = Paint().apply { color = 0xFFE1E4E8.toInt(); strokeWidth = 1f }

    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNumber).create())
    var canvas = page.canvas
    var y = MARGIN + 20f

    fun newPageIfNeeded(needed: Float) {
      if (y + needed <= HEIGHT - MARGIN) return
      document.finishPage(page)
      pageNumber += 1
      page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNumber).create())
      canvas = page.canvas
      y = MARGIN + 10f
    }

    fun section(text: String) {
      newPageIfNeeded(40f)
      y += 16f
      canvas.drawText(text, MARGIN, y, heading)
      y += 6f
      canvas.drawLine(MARGIN, y, WIDTH - MARGIN, y, rule)
      y += 14f
    }

    fun row(left: String, value: String, bold: Boolean = false) {
      newPageIfNeeded(18f)
      canvas.drawText(left, MARGIN, y, ink)
      canvas.drawText(value, WIDTH - MARGIN, y, if (bold) rightBold else right)
      y += 17f
    }

    canvas.drawText(appName, MARGIN, y, muted)
    y += 30f
    canvas.drawText(report.title + (if (report.months.isEmpty()) "" else " in review"), MARGIN, y, title)
    y += 8f

    section("Summary")
    row("Income", Money.format(report.incomeMinor, symbol))
    row("Spent", Money.format(report.spentMinor, symbol))
    row("Kept", Money.format(report.netMinor, symbol), bold = true)
    report.savingsRatePercent?.let { row("Share of income kept", "$it%") }

    if (report.topCategories.isNotEmpty()) {
      section("Where it went")
      report.topCategories.forEach { row(it.category.name + "  (" + it.percentLabel + ")", Money.format(it.amountMinor, symbol)) }
    }

    if (report.months.isNotEmpty()) {
      section("Month by month")
      report.months.filter { it.hasActivity }.forEach { m ->
        row(Periods.monthLabel(m.month), "in " + Money.format(m.incomeMinor, symbol) + "   out " + Money.format(m.spentMinor, symbol))
      }
      report.best?.let { row("Best month", Periods.monthLabel(it.month) + "  " + Money.format(it.netMinor, symbol)) }
      report.worst?.let { row("Hardest month", Periods.monthLabel(it.month) + "  " + Money.format(it.netMinor, symbol)) }
    }

    report.forecast?.let { f ->
      section("Rest of the month")
      row("Spent so far", Money.format(f.spentSoFarMinor, symbol))
      row("Bills still to come", Money.format(f.billsLeftMinor, symbol))
      row("Expected total", Money.format(f.projectedSpendMinor, symbol), bold = true)
      f.projectedLeftMinor?.let { row(if (it >= 0) "Expected left of the budget" else "Expected over the budget", Money.format(kotlin.math.abs(it), symbol)) }
    }

    if (report.netWorth.isNotEmpty()) {
      section("Net worth at each month end")
      report.netWorth.forEach { (month, value) -> row(Periods.monthLabel(month), Money.format(value, symbol)) }
    }

    newPageIfNeeded(30f)
    y += 18f
    canvas.drawText("Made on this phone with $appName. Nothing was sent anywhere.", MARGIN, y, muted)
    document.finishPage(page)
    try {
      document.writeTo(output)
    } finally {
      // Some platform builds close the document themselves once it is written.
      runCatching { document.close() }
    }
  }
}
