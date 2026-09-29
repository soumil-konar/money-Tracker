package com.moneytracker.app.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test verifying MIGRATION_12_13 correctly recreates the FTS
 * virtual table and synchronisation triggers regardless of whether the device's
 * SQLite supports FTS5 (falls back to FTS4 with unicode61 tokenizer).
 *
 * Requires the exported JSON schemas in `core/database/schemas/`.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test-db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FinanceDatabase::class.java,
    )

    // ------------------------------------------------------------------ //
    //  MIGRATION 12 → 13 : FTS rebuild + trigger synchronization
    // ------------------------------------------------------------------ //

    @Test
    fun migration_12_to_13_creates_fts_table_and_backfills() {
        // --- Arrange: create a v12 database with one account + one transaction ---
        val db = helper.createDatabase(testDbName, 12).apply {
            execSQL(
                """
                INSERT INTO accounts (id, name, kind, isSystemGenerated, isRupayCreditCard,
                    currentBalance, isBalanceVerified)
                VALUES (1, 'TestBank', 'BANK', 0, 0, 1000.0, 0)
                """.trimIndent(),
            )
            insert(
                "transactions",
                SQLiteDatabase.CONFLICT_NONE,
                ContentValues().apply {
                    put("amount", 250.0)
                    put("direction", "DEBIT")
                    put("occurredAtMillis", System.currentTimeMillis())
                    put("merchant", "Swiggy")
                    put("category", "FOOD")
                    put("accountId", 1L)
                    put("sourceSender", "AD-HDFC")
                    put("smsBody", "You spent INR 250 at Swiggy")
                    put("confidence", 0.95)
                    put("fingerprint", "fp-swiggy-001")
                    put("status", "POSTED")
                    put("countsTowardBudget", 1)
                    put("createdAtMillis", System.currentTimeMillis())
                },
            )
            close()
        }

        // --- Act: run migration 12 → 13 ---
        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            13,
            true, // validateDroppedTables
            FinanceDatabase.MIGRATION_12_13,
        )

        // --- Assert 1: transactions_fts table exists and backfilled ---
        val ftsCursor = migratedDb.query(
            "SELECT * FROM transactions_fts WHERE transactions_fts MATCH '\"Swiggy\"'",
        )
        assertTrue("FTS backfill should populate at least one row", ftsCursor.count > 0)
        ftsCursor.close()

        // --- Assert 2: AFTER INSERT trigger fires ---
        migratedDb.execSQL(
            """
            INSERT INTO transactions (amount, direction, occurredAtMillis, merchant, category,
                accountId, sourceSender, smsBody, confidence, fingerprint, status,
                countsTowardBudget, createdAtMillis)
            VALUES (100.0, 'DEBIT', ${System.currentTimeMillis()}, 'Zomato', 'FOOD',
                1, 'AD-HDFC', 'You spent INR 100 at Zomato', 0.9, 'fp-zomato-001', 'POSTED',
                1, ${System.currentTimeMillis()})
            """.trimIndent(),
        )
        val insertCursor = migratedDb.query(
            "SELECT * FROM transactions_fts WHERE transactions_fts MATCH '\"Zomato\"'",
        )
        assertTrue("AFTER INSERT trigger should sync new row to FTS", insertCursor.count > 0)
        insertCursor.close()

        // --- Assert 3: AFTER UPDATE trigger fires ---
        migratedDb.execSQL(
            "UPDATE transactions SET merchant = 'Uber Eats' WHERE fingerprint = 'fp-zomato-001'",
        )
        val updateCursorOld = migratedDb.query(
            "SELECT * FROM transactions_fts WHERE transactions_fts MATCH '\"Zomato\"'",
        )
        assertEquals("Old FTS row should be deleted after UPDATE", 0, updateCursorOld.count)
        updateCursorOld.close()

        val updateCursorNew = migratedDb.query(
            "SELECT * FROM transactions_fts WHERE transactions_fts MATCH '\"Uber\"'",
        )
        assertTrue("New FTS row should appear after UPDATE", updateCursorNew.count > 0)
        updateCursorNew.close()

        // --- Assert 4: AFTER DELETE trigger fires ---
        migratedDb.execSQL(
            "DELETE FROM transactions WHERE fingerprint = 'fp-swiggy-001'",
        )
        val deleteCursor = migratedDb.query(
            "SELECT * FROM transactions_fts WHERE transactions_fts MATCH '\"Swiggy\"'",
        )
        assertEquals("FTS row should be removed after DELETE", 0, deleteCursor.count)
        deleteCursor.close()

        migratedDb.close()
    }

    @Test
    fun migration_12_to_13_preserves_all_other_tables() {
        val db = helper.createDatabase(testDbName, 12).apply {
            execSQL(
                """
                INSERT INTO accounts (id, name, kind, isSystemGenerated, isRupayCreditCard,
                    currentBalance, isBalanceVerified)
                VALUES (1, 'TestBank', 'BANK', 0, 0, 500.0, 0)
                """.trimIndent(),
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(
            testDbName,
            13,
            true,
            FinanceDatabase.MIGRATION_12_13,
        )

        val cursor = migratedDb.query("SELECT * FROM accounts WHERE id = 1")
        assertTrue("Account row must survive migration", cursor.count == 1)
        cursor.moveToFirst()
        val nameIdx = cursor.getColumnIndex("name")
        assertEquals("TestBank", cursor.getString(nameIdx))
        cursor.close()
        migratedDb.close()
    }
}
