package com.moneytracker.app.export

import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class StatementExportManagerTest {

    private val zoneId = ZoneId.of("UTC")

    private fun dateToMillis(year: Int, month: Int, day: Int, hour: Int = 10, minute: Int = 30): Long {
        return LocalDate.of(year, month, day)
            .atTime(hour, minute, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }

    private fun createTx(
        id: Long,
        amount: Double,
        direction: TransactionDirection = TransactionDirection.DEBIT,
        occurredAtMillis: Long = dateToMillis(2026, 9, 15, 14, 30),
        merchant: String = "Starbucks",
        category: TransactionCategory = TransactionCategory.FOOD,
        status: TransactionStatus = TransactionStatus.POSTED,
        countsTowardBudget: Boolean = true,
        accountName: String? = "HDFC Salary A/c",
        note: String? = null,
    ): TransactionRecord = TransactionRecord(
        id = id,
        amount = amount,
        direction = direction,
        occurredAtMillis = occurredAtMillis,
        merchant = merchant,
        category = category,
        accountId = 1L,
        sourceSender = "HDFC-BANK",
        smsBody = null,
        confidence = 1.0,
        status = status,
        note = note,
        countsTowardBudget = countsTowardBudget,
        accountName = accountName,
        accountKind = AccountKind.BANK,
        availableBalance = null,
    )

    @Test
    fun `test RFC 4180 CSV escaping rules`() {
        // Plain string without special chars
        assertEquals("CleanMerchant", StatementExportManager.escapeCsvField("CleanMerchant"))

        // String with comma -> must be enclosed in quotes
        assertEquals("\"Merchant, Inc.\"", StatementExportManager.escapeCsvField("Merchant, Inc."))

        // String with double quotes -> internal quotes doubled and enclosed in quotes
        assertEquals("\"Special \"\"Offer\"\" Store\"", StatementExportManager.escapeCsvField("Special \"Offer\" Store"))

        // String with newline -> enclosed in quotes
        assertEquals("\"Line1\nLine2\"", StatementExportManager.escapeCsvField("Line1\nLine2"))

        // Null value -> empty string
        assertEquals("", StatementExportManager.escapeCsvField(null))
    }

    @Test
    fun `test CSV generation with headers and multiple transactions`() {
        val tx1 = createTx(
            id = 101,
            amount = 450.50,
            occurredAtMillis = dateToMillis(2026, 9, 10, 9, 15),
            merchant = "Blue Tokai Coffee",
            category = TransactionCategory.FOOD,
            direction = TransactionDirection.DEBIT,
            accountName = "ICICI Bank",
            note = "Morning latte",
            status = TransactionStatus.POSTED,
        )

        val tx2 = createTx(
            id = 102,
            amount = 75000.00,
            occurredAtMillis = dateToMillis(2026, 9, 1, 11, 0),
            merchant = "Acme Corp, Payroll",
            category = TransactionCategory.SALARY,
            direction = TransactionDirection.CREDIT,
            accountName = "HDFC Bank",
            note = "Monthly salary; bonus included",
            status = TransactionStatus.POSTED,
        )

        val csv = StatementExportManager.generateCsvString(listOf(tx1, tx2))
        val lines = csv.split("\r\n").filter { it.isNotBlank() }

        // Line 0: Header
        assertEquals("Date,Time,Merchant,Category,Direction,Amount,Account,Notes,Status", lines[0])

        // Line 1: tx1
        assertTrue(lines[1].contains("2026-09-10"))
        assertTrue(lines[1].contains("Blue Tokai Coffee"))
        assertTrue(lines[1].contains("Food"))
        assertTrue(lines[1].contains("DEBIT"))
        assertTrue(lines[1].contains("450.50"))
        assertTrue(lines[1].contains("ICICI Bank"))
        assertTrue(lines[1].contains("Morning latte"))
        assertTrue(lines[1].contains("POSTED"))

        // Line 2: tx2 with commas in merchant and note (must be quoted)
        assertTrue(lines[2].contains("\"Acme Corp, Payroll\""))
        assertTrue(lines[2].contains("75000.00"))
        assertTrue(lines[2].contains("CREDIT"))
    }

    @Test
    fun `test SHA-256 Ledger Integrity Hash is deterministic`() {
        val txList1 = listOf(
            createTx(id = 1, amount = 100.0, direction = TransactionDirection.DEBIT),
            createTx(id = 2, amount = 250.75, direction = TransactionDirection.CREDIT),
            createTx(id = 3, amount = 1200.0, direction = TransactionDirection.DEBIT),
        )

        // Same transactions in different initial order
        val txList2 = listOf(
            createTx(id = 3, amount = 1200.0, direction = TransactionDirection.DEBIT),
            createTx(id = 1, amount = 100.0, direction = TransactionDirection.DEBIT),
            createTx(id = 2, amount = 250.75, direction = TransactionDirection.CREDIT),
        )

        val hash1 = StatementExportManager.computeLedgerIntegrityHash(txList1)
        val hash2 = StatementExportManager.computeLedgerIntegrityHash(txList2)

        // Must be exactly 64 hexadecimal characters
        assertEquals(64, hash1.length)
        assertTrue(hash1.matches(Regex("^[0-9a-f]{64}$")))

        // Deterministic sorting ensures identical hash regardless of input order
        assertEquals(hash1, hash2)
    }

    @Test
    fun `test SHA-256 Ledger Integrity Hash sensitivity to changes`() {
        val originalList = listOf(
            createTx(id = 1, amount = 500.00, direction = TransactionDirection.DEBIT),
            createTx(id = 2, amount = 1000.00, direction = TransactionDirection.CREDIT),
        )

        val modifiedAmountList = listOf(
            createTx(id = 1, amount = 500.01, direction = TransactionDirection.DEBIT), // 1 cent difference
            createTx(id = 2, amount = 1000.00, direction = TransactionDirection.CREDIT),
        )

        val modifiedIdList = listOf(
            createTx(id = 999, amount = 500.00, direction = TransactionDirection.DEBIT),
            createTx(id = 2, amount = 1000.00, direction = TransactionDirection.CREDIT),
        )

        val hashOriginal = StatementExportManager.computeLedgerIntegrityHash(originalList)
        val hashModAmount = StatementExportManager.computeLedgerIntegrityHash(modifiedAmountList)
        val hashModId = StatementExportManager.computeLedgerIntegrityHash(modifiedIdList)

        assertNotEquals(hashOriginal, hashModAmount)
        assertNotEquals(hashOriginal, hashModId)
    }

    @Test
    fun `test SHA-256 Ledger Integrity Hash for empty list returns standard SHA-256 empty string`() {
        val emptyHash = StatementExportManager.computeLedgerIntegrityHash(emptyList())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash)
    }

    @Test
    fun `test exportCsv writes to OutputStream properly`() = kotlinx.coroutines.runBlocking {
        val tx = createTx(id = 1, amount = 100.0)
        val outputStream = ByteArrayOutputStream()

        StatementExportManager.exportCsv(listOf(tx), outputStream)

        val writtenContent = outputStream.toString("UTF-8")
        assertTrue(writtenContent.startsWith("Date,Time,Merchant,Category,Direction,Amount,Account,Notes,Status"))
        assertTrue(writtenContent.contains("100.00"))
    }
}
