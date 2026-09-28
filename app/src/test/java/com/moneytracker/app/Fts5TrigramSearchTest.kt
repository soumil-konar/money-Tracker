package com.moneytracker.app

import com.moneytracker.app.ai.FinanceRagEngine
import com.moneytracker.app.data.db.TransactionDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

class Fts5TrigramSearchTest {

    private lateinit var connection: Connection

    @Before
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        val statement = connection.createStatement()

        // Create content table and FTS5 trigram virtual table mirroring MIGRATION_10_11
        statement.execute(
            """
            CREATE TABLE transactions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                merchant TEXT NOT NULL,
                note TEXT,
                smsBody TEXT
            );
            """.trimIndent()
        )

        statement.execute(
            """
            CREATE VIRTUAL TABLE transactions_fts USING FTS5(
                merchant, note, smsBody,
                content='transactions',
                content_rowid='id',
                tokenize='trigram'
            );
            """.trimIndent()
        )

        // Synchronization triggers as defined in MIGRATION_10_11
        statement.execute(
            """
            CREATE TRIGGER transactions_ai AFTER INSERT ON transactions BEGIN
                INSERT INTO transactions_fts(rowid, merchant, note, smsBody)
                VALUES (new.id, new.merchant, new.note, new.smsBody);
            END;
            """.trimIndent()
        )

        statement.execute(
            """
            CREATE TRIGGER transactions_ad AFTER DELETE ON transactions BEGIN
                INSERT INTO transactions_fts(transactions_fts, rowid, merchant, note, smsBody)
                VALUES('delete', old.id, old.merchant, old.note, old.smsBody);
            END;
            """.trimIndent()
        )

        statement.execute(
            """
            CREATE TRIGGER transactions_au AFTER UPDATE ON transactions BEGIN
                INSERT INTO transactions_fts(transactions_fts, rowid, merchant, note, smsBody)
                VALUES('delete', old.id, old.merchant, old.note, old.smsBody);
                INSERT INTO transactions_fts(rowid, merchant, note, smsBody)
                VALUES (new.id, new.merchant, new.note, new.smsBody);
            END;
            """.trimIndent()
        )

        // Seed initial transactions
        statement.execute("INSERT INTO transactions (merchant, note, smsBody) VALUES ('SWIGGY BANGALORE', 'Dinner delivery', 'Rs 450 debited to Swiggy');")
        statement.execute("INSERT INTO transactions (merchant, note, smsBody) VALUES ('AMAZON PAY INDIA', 'Household items', 'Spent INR 1299 at Amazon Pay');")
        statement.execute("INSERT INTO transactions (merchant, note, smsBody) VALUES ('AMZN MKTPLACE', 'Kindle book', 'Paid Rs 199 to AMZN Mktplace');")
        statement.execute("INSERT INTO transactions (merchant, note, smsBody) VALUES ('STARBUCKS COFFEE', 'Indiranagar outlet', 'Rs 350 debited at Starbucks');")
        statement.close()
    }

    @After
    fun tearDown() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    private fun searchFts(query: String): List<String> {
        val sanitized = TransactionDao.sanitizeFts5Query(query)
        if (sanitized.isBlank()) return emptyList()

        val results = mutableListOf<String>()
        val ps = connection.prepareStatement("SELECT merchant FROM transactions_fts WHERE transactions_fts MATCH ?")
        ps.setString(1, sanitized)
        val rs = ps.executeQuery()
        while (rs.next()) {
            results.add(rs.getString("merchant"))
        }
        rs.close()
        ps.close()
        return results
    }

    @Test
    fun `fts5 trigram partial substring matches swig to SWIGGY BANGALORE`() {
        val results = searchFts("swig")
        assertEquals(1, results.size)
        assertTrue(results.contains("SWIGGY BANGALORE"))
    }

    @Test
    fun `fts5 trigram partial substring matches amzn to AMZN MKTPLACE`() {
        val results = searchFts("amzn")
        assertEquals(1, results.size)
        assertTrue(results.contains("AMZN MKTPLACE"))
    }

    @Test
    fun `fts5 trigram partial substring matches amazon to AMAZON PAY INDIA`() {
        val results = searchFts("amazon")
        assertEquals(1, results.size)
        assertTrue(results.contains("AMAZON PAY INDIA"))
    }

    @Test
    fun `fts5 trigram matches location notes like indira in STARBUCKS COFFEE`() {
        val results = searchFts("indira")
        assertEquals(1, results.size)
        assertTrue(results.contains("STARBUCKS COFFEE"))
    }

    @Test
    fun `query sanitization handles colons, hyphens, and quotes safely without sqlite syntax error`() {
        val complexQuery = """\"swig\": 'bangalore' - [delivery]"""
        val sanitized = TransactionDao.sanitizeFts5Query(complexQuery)
        assertEquals("\"swig\" AND \"bangalore\" AND \"delivery\"", sanitized)

        val results = searchFts(complexQuery)
        assertEquals(1, results.size)
        assertEquals("SWIGGY BANGALORE", results.first())
    }

    @Test
    fun `query sanitization returns empty string for punctuation-only inputs avoiding crash`() {
        val emptyPunctuation = "---***@#:::\"\""
        val sanitized = TransactionDao.sanitizeFts5Query(emptyPunctuation)
        assertEquals("", sanitized)

        val results = searchFts(emptyPunctuation)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `query sanitization preserves explicit OR disjunctions for broad candidate retrieval`() {
        val orQuery = "swig OR starb"
        val sanitized = TransactionDao.sanitizeFts5Query(orQuery)
        assertEquals("\"swig\" OR \"starb\"", sanitized)

        val results = searchFts(orQuery)
        assertEquals(2, results.size)
        assertTrue(results.contains("SWIGGY BANGALORE"))
        assertTrue(results.contains("STARBUCKS COFFEE"))
    }

    @Test
    fun `fts5 triggers synchronize insertions, updates, and deletions cleanly`() {
        val statement = connection.createStatement()

        // 1. Insert new transaction
        statement.execute("INSERT INTO transactions (merchant, note, smsBody) VALUES ('ZOMATO LIMITED', 'Lunch order', 'Paid INR 280');")
        val insertMatch = searchFts("zomato")
        assertEquals(1, insertMatch.size)
        assertEquals("ZOMATO LIMITED", insertMatch.first())

        // 2. Update existing transaction
        statement.execute("UPDATE transactions SET merchant = 'ZOMATO HYDERABAD' WHERE merchant = 'ZOMATO LIMITED';")
        val oldMatch = searchFts("limited")
        assertTrue(oldMatch.isEmpty())
        val updateMatch = searchFts("hyderabad")
        assertEquals(1, updateMatch.size)
        assertEquals("ZOMATO HYDERABAD", updateMatch.first())

        // 3. Delete transaction
        statement.execute("DELETE FROM transactions WHERE merchant = 'ZOMATO HYDERABAD';")
        val deleteMatch = searchFts("hyderabad")
        assertTrue(deleteMatch.isEmpty())

        statement.close()
    }

    @Test
    fun `FinanceRagEngine sanitizeForFts5 wraps keywords in double quotes for trigram matching`() {
        val query = "Where did I spend on dining and swiggy in Indiranagar last month?"
        val fts = FinanceRagEngine.sanitizeForFts5(query)
        assertFalse(fts.contains("*"))
        assertTrue(fts.contains("\"dining\""))
        assertTrue(fts.contains("\"swiggy\""))
        assertTrue(fts.contains("\"indiranagar\""))
    }
}
