package com.moneytracker.app.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import com.moneytracker.app.ui.asCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StatementExportData(
    val title: String = "Financial Statement",
    val periodLabel: String,
    val accountFilter: String = "All Accounts",
    val transactions: List<TransactionRecord>,
    val generatedAtMillis: Long = System.currentTimeMillis(),
)

object StatementExportManager {

    private const val CRLF = "\r\n"

    // =========================================================================
    // CSV Exporter (RFC 4180 Compliant)
    // =========================================================================

    /**
     * Escapes a single string field according to RFC 4180 rules.
     * If the field contains comma, quote, or newline, it is enclosed in double quotes,
     * and any internal double quote is doubled ("").
     */
    fun escapeCsvField(value: String?): String {
        if (value == null) return ""
        val needsQuotes = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        return if (needsQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /**
     * Generates an RFC 4180-compliant CSV string for the given list of transactions.
     * Header columns: Date,Time,Merchant,Category,Direction,Amount,Account,Notes,Status
     */
    fun generateCsvString(transactions: List<TransactionRecord>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

        val sb = StringBuilder()
        // Header row
        val headers = listOf("Date", "Time", "Merchant", "Category", "Direction", "Amount", "Account", "Notes", "Status")
        sb.append(headers.joinToString(",") { escapeCsvField(it) }).append(CRLF)

        for (tx in transactions) {
            val date = Date(tx.occurredAtMillis)
            val dateStr = dateFormat.format(date)
            val timeStr = timeFormat.format(date)
            val merchant = tx.merchant
            val category = tx.category.label
            val direction = tx.direction.name
            val amount = String.format(Locale.US, "%.2f", tx.amount)
            val account = tx.accountName ?: "Unlinked"
            val notes = tx.note ?: ""
            val status = tx.status.name

            val row = listOf(dateStr, timeStr, merchant, category, direction, amount, account, notes, status)
            sb.append(row.joinToString(",") { escapeCsvField(it) }).append(CRLF)
        }

        return sb.toString()
    }

    /**
     * Writes RFC 4180-compliant CSV directly into an OutputStream (e.g. from SAF Uri).
     */
    suspend fun exportCsv(transactions: List<TransactionRecord>, outputStream: OutputStream) =
        withContext(Dispatchers.IO) {
            val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
            writer.write(generateCsvString(transactions))
            writer.flush()
        }

    // =========================================================================
    // Deterministic SHA-256 Ledger Integrity Hash
    // =========================================================================

    /**
     * Calculates the deterministic SHA-256 Ledger Integrity Hash.
     * Derived from concatenating all exported transaction IDs, amounts, and directions,
     * sorted deterministically by ID and timestamp.
     */
    fun computeLedgerIntegrityHash(transactions: List<TransactionRecord>): String {
        if (transactions.isEmpty()) {
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        }
        val sorted = transactions.sortedWith(compareBy({ it.id }, { it.occurredAtMillis }))
        val rawData = sorted.joinToString(separator = ";") { tx ->
            "${tx.id}:${String.format(Locale.US, "%.2f", tx.amount)}:${tx.direction}"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(rawData.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // =========================================================================
    // Print-Ready PDF Exporter (android.graphics.pdf.PdfDocument)
    // =========================================================================

    // Standard A4 dimensions in points: 595 x 842
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 559f
    private const val MARGIN_TOP = 40f
    private const val MARGIN_BOTTOM = 800f
    private const val ROW_HEIGHT = 20f

    /**
     * Generates a multi-page print-ready PDF statement and writes directly to an OutputStream.
     */
    suspend fun exportPdf(statementData: StatementExportData, outputStream: OutputStream) =
        withContext(Dispatchers.IO) {
            val pdfDocument = PdfDocument()

            try {
                val transactions = statementData.transactions
                val totalCredits = transactions.filter { it.direction == TransactionDirection.CREDIT && it.status == TransactionStatus.POSTED }.sumOf { it.amount }
                val totalDebits = transactions.filter { it.direction == TransactionDirection.DEBIT && it.status == TransactionStatus.POSTED }.sumOf { it.amount }
                val netDelta = totalCredits - totalDebits

                val integrityHash = computeLedgerIntegrityHash(transactions)
                val generationDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(statementData.generatedAtMillis))
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

                // Category aggregates
                val categoryMap = transactions.asSequence()
                    .filter { it.direction == TransactionDirection.DEBIT && it.status == TransactionStatus.POSTED }
                    .groupBy { it.category }
                    .mapValues { (_, list) -> list.sumOf { it.amount } }
                    .toList()
                    .sortedByDescending { it.second }

                // Measure pages
                // Page 1 header + summary + category breakdown takes ~260 points.
                // Available height for rows on Page 1: (MARGIN_BOTTOM - 260) = 540 -> ~27 rows.
                // Page 2+ available height: (MARGIN_BOTTOM - 80) = 720 -> ~36 rows.
                var txIndex = 0
                var pageNumber = 1
                val totalTxs = transactions.size

                while (txIndex < totalTxs || pageNumber == 1) {
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    var curY = MARGIN_TOP

                    // Header on first page vs continuation
                    if (pageNumber == 1) {
                        curY = drawFirstPageHeader(
                            canvas = canvas,
                            data = statementData,
                            totalCredits = totalCredits,
                            totalDebits = totalDebits,
                            netDelta = netDelta,
                            categoryMap = categoryMap,
                            startY = curY,
                        )
                    } else {
                        curY = drawContinuationHeader(
                            canvas = canvas,
                            data = statementData,
                            startY = curY,
                        )
                    }

                    // Table Header
                    curY = drawTableHeader(canvas, curY)

                    // Draw Table Rows
                    var rowIndex = 0
                    val isLastBatch: Boolean
                    while (txIndex < totalTxs && curY + ROW_HEIGHT <= MARGIN_BOTTOM - 60f) {
                        val tx = transactions[txIndex]
                        drawTableRow(
                            canvas = canvas,
                            tx = tx,
                            y = curY,
                            isEven = (rowIndex % 2 == 0),
                            dateFormat = dateFormat,
                        )
                        curY += ROW_HEIGHT
                        txIndex++
                        rowIndex++
                    }

                    isLastBatch = (txIndex >= totalTxs)

                    // If final page and space allows, draw the Ledger Integrity Seal
                    if (isLastBatch) {
                        drawIntegritySeal(canvas, integrityHash, generationDateStr, curY + 12f)
                    }

                    // Page Footer
                    drawPageFooter(canvas, pageNumber, generationDateStr)

                    pdfDocument.finishPage(page)
                    pageNumber++
                }

                pdfDocument.writeTo(outputStream)
                outputStream.flush()
            } finally {
                pdfDocument.close()
            }
        }

    // =========================================================================
    // PDF Drawing Subroutines
    // =========================================================================

    private fun drawFirstPageHeader(
        canvas: Canvas,
        data: StatementExportData,
        totalCredits: Double,
        totalDebits: Double,
        netDelta: Double,
        categoryMap: List<Pair<TransactionCategory, Double>>,
        startY: Float,
    ): Float {
        var y = startY
        val textPaint = Paint().apply { isAntiAlias = true }

        // Brand Banner
        val bannerPaint = Paint().apply {
            color = Color.rgb(15, 23, 42) // Slate 900
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + 44f), 8f, 8f, bannerPaint)

        textPaint.apply {
            color = Color.WHITE
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("MONEY TRACKER", MARGIN_LEFT + 14f, y + 28f, textPaint)

        textPaint.apply {
            color = Color.rgb(148, 163, 184)
            textSize = 10f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("OFFICIAL FINANCIAL STATEMENT", MARGIN_RIGHT - 14f, y + 28f, textPaint)
        y += 56f

        // Period & Account Metadata
        textPaint.apply {
            color = Color.rgb(30, 41, 59)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("Period: ${data.periodLabel}", MARGIN_LEFT, y + 10f, textPaint)
        canvas.drawText("Account Filter: ${data.accountFilter}", MARGIN_LEFT + 220f, y + 10f, textPaint)

        val countStr = "Total Records: ${data.transactions.size}"
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(countStr, MARGIN_RIGHT, y + 10f, textPaint)
        y += 22f

        // Summary Cards: 3 columns (Credits, Debits, Net Delta)
        val cardWidth = (MARGIN_RIGHT - MARGIN_LEFT - 16f) / 3f
        val cardHeight = 44f

        val cardBgPaint = Paint().apply {
            color = Color.rgb(241, 245, 249)
            isAntiAlias = true
        }

        // Card 1: Total Credits
        var cardLeft = MARGIN_LEFT
        canvas.drawRoundRect(RectF(cardLeft, y, cardLeft + cardWidth, y + cardHeight), 6f, 6f, cardBgPaint)
        textPaint.apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("TOTAL CREDITS", cardLeft + 8f, y + 15f, textPaint)
        textPaint.apply {
            color = Color.rgb(16, 185, 129) // Emerald
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("+${totalCredits.asCurrency()}", cardLeft + 8f, y + 34f, textPaint)

        // Card 2: Total Debits
        cardLeft += cardWidth + 8f
        canvas.drawRoundRect(RectF(cardLeft, y, cardLeft + cardWidth, y + cardHeight), 6f, 6f, cardBgPaint)
        textPaint.apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("TOTAL DEBITS", cardLeft + 8f, y + 15f, textPaint)
        textPaint.apply {
            color = Color.rgb(239, 68, 68) // Red
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(totalDebits.asCurrency(), cardLeft + 8f, y + 34f, textPaint)

        // Card 3: Net Delta
        cardLeft += cardWidth + 8f
        canvas.drawRoundRect(RectF(cardLeft, y, cardLeft + cardWidth, y + cardHeight), 6f, 6f, cardBgPaint)
        textPaint.apply {
            color = Color.rgb(100, 116, 139)
            textSize = 8.5f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("NET SAVINGS DELTA", cardLeft + 8f, y + 15f, textPaint)
        textPaint.apply {
            color = if (netDelta >= 0) Color.rgb(16, 185, 129) else Color.rgb(239, 68, 68)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        val deltaPrefix = if (netDelta >= 0) "+" else ""
        canvas.drawText("$deltaPrefix${netDelta.asCurrency()}", cardLeft + 8f, y + 34f, textPaint)
        y += cardHeight + 14f

        // Category Breakdown Pill Bar (Top 4 categories)
        if (categoryMap.isNotEmpty() && totalDebits > 0) {
            textPaint.apply {
                color = Color.rgb(51, 65, 85)
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("Top Category Spend Vectors:", MARGIN_LEFT, y + 8f, textPaint)
            y += 14f

            var catX = MARGIN_LEFT
            val topCategories = categoryMap.take(4)
            for ((cat, amount) in topCategories) {
                val pct = (amount / totalDebits * 100.0)
                val catText = "${cat.label}: ${amount.asCurrency()} (${String.format(Locale.US, "%.0f%%", pct)})"
                val pillWidth = textPaint.measureText(catText) + 16f

                val pillPaint = Paint().apply {
                    color = Color.rgb(226, 232, 240)
                    isAntiAlias = true
                }
                canvas.drawRoundRect(RectF(catX, y, catX + pillWidth, y + 18f), 4f, 4f, pillPaint)
                textPaint.apply {
                    color = Color.rgb(30, 41, 59)
                    textSize = 8f
                    typeface = Typeface.DEFAULT
                }
                canvas.drawText(catText, catX + 8f, y + 12f, textPaint)
                catX += pillWidth + 8f
            }
            y += 24f
        }

        return y
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        data: StatementExportData,
        startY: Float,
    ): Float {
        var y = startY
        val textPaint = Paint().apply { isAntiAlias = true }

        textPaint.apply {
            color = Color.rgb(71, 85, 105)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("Money Tracker Financial Statement (${data.periodLabel}) — Continued", MARGIN_LEFT, y + 10f, textPaint)
        y += 18f
        return y
    }

    private fun drawTableHeader(canvas: Canvas, startY: Float): Float {
        val headerPaint = Paint().apply {
            color = Color.rgb(30, 41, 59) // Slate 800
            isAntiAlias = true
        }
        canvas.drawRect(MARGIN_LEFT, startY, MARGIN_RIGHT, startY + ROW_HEIGHT, headerPaint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        // Columns: Date (80pt), Merchant (140pt), Category (90pt), Account (90pt), Direction (40pt), Amount (remaining)
        var x = MARGIN_LEFT + 6f
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Date", x, startY + 13f, textPaint)

        x += 65f
        canvas.drawText("Merchant / Description", x, startY + 13f, textPaint)

        x += 160f
        canvas.drawText("Category", x, startY + 13f, textPaint)

        x += 95f
        canvas.drawText("Account", x, startY + 13f, textPaint)

        x += 90f
        canvas.drawText("Type", x, startY + 13f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Amount", MARGIN_RIGHT - 6f, startY + 13f, textPaint)

        return startY + ROW_HEIGHT
    }

    private fun drawTableRow(
        canvas: Canvas,
        tx: TransactionRecord,
        y: Float,
        isEven: Boolean,
        dateFormat: SimpleDateFormat,
    ) {
        // Alternating row background
        val bgPaint = Paint().apply {
            color = if (isEven) Color.WHITE else Color.rgb(248, 250, 252)
        }
        canvas.drawRect(MARGIN_LEFT, y, MARGIN_RIGHT, y + ROW_HEIGHT, bgPaint)

        // Subtle bottom border
        val borderPaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 0.5f
        }
        canvas.drawLine(MARGIN_LEFT, y + ROW_HEIGHT, MARGIN_RIGHT, y + ROW_HEIGHT, borderPaint)

        val textPaint = Paint().apply {
            textSize = 8f
            isAntiAlias = true
            color = Color.rgb(30, 41, 59)
            typeface = Typeface.DEFAULT
        }

        var x = MARGIN_LEFT + 6f
        textPaint.textAlign = Paint.Align.LEFT
        val dateText = dateFormat.format(Date(tx.occurredAtMillis))
        canvas.drawText(dateText, x, y + 13f, textPaint)

        x += 65f
        val cleanMerchant = if (tx.merchant.length > 28) tx.merchant.take(26) + "…" else tx.merchant
        canvas.drawText(cleanMerchant, x, y + 13f, textPaint)

        x += 160f
        val catText = if (tx.category.label.length > 16) tx.category.label.take(14) + "…" else tx.category.label
        canvas.drawText(catText, x, y + 13f, textPaint)

        x += 95f
        val accountText = tx.accountName?.take(16) ?: "Unlinked"
        canvas.drawText(accountText, x, y + 13f, textPaint)

        x += 90f
        textPaint.color = if (tx.direction == TransactionDirection.CREDIT) Color.rgb(16, 185, 129) else Color.rgb(100, 116, 139)
        canvas.drawText(tx.direction.name, x, y + 13f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = if (tx.direction == TransactionDirection.CREDIT) Color.rgb(16, 185, 129) else Color.rgb(15, 23, 42)
        val amountStr = (if (tx.direction == TransactionDirection.CREDIT) "+" else "") + tx.amount.asCurrency()
        canvas.drawText(amountStr, MARGIN_RIGHT - 6f, y + 13f, textPaint)
    }

    private fun drawIntegritySeal(
        canvas: Canvas,
        integrityHash: String,
        generatedAt: String,
        startY: Float,
    ) {
        val y = startY
        val sealBoxPaint = Paint().apply {
            color = Color.rgb(241, 245, 249) // Slate 100
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(203, 213, 225)
            strokeWidth = 1f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val boxHeight = 56f
        canvas.drawRoundRect(RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + boxHeight), 6f, 6f, sealBoxPaint)
        canvas.drawRoundRect(RectF(MARGIN_LEFT, y, MARGIN_RIGHT, y + boxHeight), 6f, 6f, borderPaint)

        val textPaint = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        textPaint.apply {
            color = Color.rgb(15, 23, 42)
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("🔒 LEDGER INTEGRITY AUDIT HASH (SHA-256)", MARGIN_LEFT + 10f, y + 16f, textPaint)

        textPaint.apply {
            color = Color.rgb(51, 65, 85)
            textSize = 7.5f
            typeface = Typeface.MONOSPACE
        }
        canvas.drawText(integrityHash, MARGIN_LEFT + 10f, y + 32f, textPaint)

        textPaint.apply {
            color = Color.rgb(100, 116, 139)
            textSize = 7f
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("Air-gapped verification hash • Exported at $generatedAt UTC", MARGIN_LEFT + 10f, y + 46f, textPaint)
    }

    private fun drawPageFooter(canvas: Canvas, pageNumber: Int, timestamp: String) {
        val textPaint = Paint().apply {
            color = Color.rgb(148, 163, 184)
            textSize = 7.5f
            isAntiAlias = true
        }

        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Generated privately on-device with Money Tracker", MARGIN_LEFT, MARGIN_BOTTOM + 20f, textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Page $pageNumber", MARGIN_RIGHT, MARGIN_BOTTOM + 20f, textPaint)
    }
}
