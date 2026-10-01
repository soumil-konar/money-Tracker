package com.moneytracker.app

import androidx.paging.PagingSource
import com.moneytracker.app.ai.FinanceRagEngine
import com.moneytracker.app.ai.GeminiApiClient
import com.moneytracker.app.ai.OnDeviceAiEngine
import com.moneytracker.app.data.db.CategorySpendAggregate
import com.moneytracker.app.data.db.TransactionDao
import com.moneytracker.app.data.db.TransactionEmbeddingDao
import com.moneytracker.app.data.db.TransactionEmbeddingEntity
import com.moneytracker.app.data.db.TransactionEntity
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FinanceRagEngineRetrievalTest {

    private lateinit var fakeTransactionDao: FakeTransactionDao
    private lateinit var fakeEmbeddingDao: FakeEmbeddingDao
    private lateinit var onDeviceAiEngine: OnDeviceAiEngine
    private lateinit var ragEngine: FinanceRagEngine

    private var cloudEmbeddingCallCount = 0

    @Before
    fun setUp() {
        cloudEmbeddingCallCount = 0
        fakeTransactionDao = FakeTransactionDao()
        fakeEmbeddingDao = FakeEmbeddingDao()
        onDeviceAiEngine = OnDeviceAiEngine()

        val mockCloudClient = object : GeminiApiClient() {
            override suspend fun generateEmbedding(
                text: String,
                apiKey: String,
                model: String,
                outputDimensionality: Int,
            ): Result<List<Float>> {
                cloudEmbeddingCallCount++
                return Result.failure(IllegalStateException("Simulated cloud failure"))
            }
        }

        ragEngine = FinanceRagEngine(
            transactionDao = fakeTransactionDao,
            embeddingDao = fakeEmbeddingDao,
            geminiApiClient = mockCloudClient,
            onDeviceAiEngine = onDeviceAiEngine,
        )
    }

    private fun createTx(
        id: Long,
        merchant: String,
        amount: Double,
        category: TransactionCategory = TransactionCategory.FOOD,
        occurredAtMillis: Long = System.currentTimeMillis(),
        note: String? = null,
        smsBody: String? = null,
    ): TransactionRecord {
        return TransactionRecord(
            id = id,
            amount = amount,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = occurredAtMillis,
            merchant = merchant,
            category = category,
            accountId = 1L,
            sourceSender = "HDFCBK",
            smsBody = smsBody,
            confidence = 0.95,
            status = TransactionStatus.POSTED,
            note = note,
            countsTowardBudget = true,
            accountName = "HDFC Bank",
            accountKind = AccountKind.BANK,
        )
    }

    @Test
    fun `offline retrieval uses FTS5 without calling broken vector or cloud routines`() = runBlocking {
        val swiggyTx = createTx(1L, "SWIGGY BANGALORE", 450.0, note = "Dinner Indiranagar")
        val amazonTx = createTx(2L, "AMAZON PAY", 1299.0, category = TransactionCategory.SHOPPING)
        fakeTransactionDao.allRecords.addAll(listOf(swiggyTx, amazonTx))

        // Retrieve offline with empty API key
        val context = ragEngine.retrieveContext(
            query = "How much did I spend at Swiggy?",
            apiKey = "",
            currentBudgetLimit = 25000.0,
            allPosted = listOf(swiggyTx, amazonTx),
        )

        // Tier 3 should never have been invoked
        assertEquals(0, cloudEmbeddingCallCount)

        // Verifies FTS5 substring retrieval found Swiggy
        assertNotNull(context)
        assertEquals(1, context.transactions.size)
        assertEquals("SWIGGY BANGALORE", context.transactions.first().merchant)
        assertTrue(context.macroSummary.contains("SWIGGY BANGALORE") || context.macroSummary.contains("Total Spent"))
    }

    @Test
    fun `temporal range query resolves via deterministic SQL without calling vector routines`() = runBlocking {
        val now = System.currentTimeMillis()
        val txThisMonth = createTx(10L, "Starbucks", 350.0, occurredAtMillis = now)
        fakeTransactionDao.allRecords.add(txThisMonth)

        val context = ragEngine.retrieveContext(
            query = "Show my expenses this month",
            apiKey = "",
            currentBudgetLimit = 10000.0,
            allPosted = listOf(txThisMonth),
        )

        assertEquals(0, cloudEmbeddingCallCount)
        assertTrue(context.transactions.any { it.id == 10L })
    }

    @Test
    fun `retrieval limits context to top 15 records avoiding context dilution`() = runBlocking {
        val largeList = (1..30).map { i ->
            createTx(
                id = i.toLong(),
                merchant = "Merchant $i",
                amount = 100.0 * i,
                occurredAtMillis = System.currentTimeMillis() - (i * 1000L),
            )
        }
        fakeTransactionDao.allRecords.addAll(largeList)

        val context = ragEngine.retrieveContext(
            query = "How are my expenses?",
            apiKey = "",
            currentBudgetLimit = 50000.0,
            allPosted = largeList,
        )

        // Compact context constraint: bounded at 15
        assertTrue(context.transactions.size <= 15)
        assertEquals(15, context.transactions.size)
    }

    @Test
    fun `failed cloud embedding degrades cleanly to FTS5 and SQL without SHA-256 fallback`() = runBlocking {
        val tx = createTx(5L, "Uber India", 420.0, category = TransactionCategory.TRAVEL)
        fakeTransactionDao.allRecords.add(tx)

        // API key is present, but cloudClient fails
        val context = ragEngine.retrieveContext(
            query = "Show my Uber rides",
            apiKey = "test-api-key",
            currentBudgetLimit = 20000.0,
            allPosted = listOf(tx),
        )

        // Cloud embedding was attempted once, failed, but gracefully recovered via FTS5
        assertEquals(1, cloudEmbeddingCallCount)
        assertEquals(1, context.transactions.size)
        assertEquals("Uber India", context.transactions.first().merchant)
    }

    @Test
    fun `on device ai engine embedding returns failure preventing cryptographic avalanche`() {
        val result = onDeviceAiEngine.generateEmbeddingOnDevice("Test Transaction")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }
}

class FakeTransactionDao : TransactionDao {
    val allRecords = mutableListOf<TransactionRecord>()

    override suspend fun queryTransactionsFtsRaw(matchQuery: String, limit: Int): List<TransactionRecord> {
        val cleanTerms = matchQuery.replace("\"", "").split("OR", "AND").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (cleanTerms.isEmpty()) return emptyList()

        return allRecords.filter { tx ->
            val corpus = "${tx.merchant} ${tx.note.orEmpty()} ${tx.smsBody.orEmpty()}".lowercase()
            cleanTerms.any { term -> corpus.contains(term) }
        }.take(limit)
    }

    override suspend fun getTransactionsBetween(startMillis: Long, endMillis: Long): List<TransactionRecord> {
        return allRecords.filter { it.occurredAtMillis in startMillis..endMillis }
    }

    override fun observeTransactions(): Flow<List<TransactionRecord>> = throw NotImplementedError()
    override fun pagedTransactions(): PagingSource<Int, TransactionRecord> = throw NotImplementedError()
    override fun getPagedTransactions(): PagingSource<Int, TransactionRecord> = throw NotImplementedError()
    override fun pagedFilteredTransactions(accountId: Long?, direction: String?, searchQuery: String): PagingSource<Int, TransactionRecord> = throw NotImplementedError()
    override fun observePostedTransactions(): Flow<List<TransactionRecord>> = throw NotImplementedError()
    override suspend fun fingerprintExists(fingerprint: String): Boolean = false
    override suspend fun findSimilarTransaction(amount: Double, direction: TransactionDirection, occurredAtMillis: Long, timeToleranceMillis: Long): TransactionEntity? = null
    override suspend fun getAllTransactions(): List<TransactionEntity> = emptyList()
    override suspend fun getLatestBalanceAnchor(accountId: Long): TransactionRecord? =
        allRecords.lastOrNull { it.accountId == accountId && it.availableBalance != null }
    override suspend fun getPostedTransactionsSince(accountId: Long, anchorTimeMillis: Long): List<TransactionRecord> =
        allRecords.filter { it.accountId == accountId && it.status == TransactionStatus.POSTED && it.occurredAtMillis >= anchorTimeMillis }
            .sortedBy { it.occurredAtMillis }
    override suspend fun getRecentTransactions(sinceMillis: Long): List<TransactionEntity> = emptyList()
    override suspend fun insert(transaction: TransactionEntity): Long = 0L
    override suspend fun updateStatus(transactionId: Long, status: String) {}
    override suspend fun updateBudgetInclusion(transactionId: Long, countsTowardBudget: Boolean) {}
    override suspend fun deleteById(transactionId: Long) {}
    override suspend fun update(transaction: TransactionEntity) {}
    override suspend fun getById(transactionId: Long): TransactionEntity? = null
    override suspend fun getRecentPostedTransactions(limit: Int): List<TransactionRecord> = allRecords.take(limit)
    override suspend fun getAllPostedTransactionTimestamps(): List<Long> = allRecords.map { it.occurredAtMillis }
    override fun observeMonthlySpent(startMillis: Long, endMillis: Long): Flow<Double> = throw NotImplementedError()
    override fun observeMonthlyIncome(startMillis: Long, endMillis: Long): Flow<Double> = throw NotImplementedError()
    override fun observeCategorySpendBreakdown(startMillis: Long, endMillis: Long): Flow<List<CategorySpendAggregate>> = throw NotImplementedError()
    override fun observeRecentPostedTransactions(limit: Int): Flow<List<TransactionRecord>> = throw NotImplementedError()
    override fun observeReviewCount(): Flow<Int> = throw NotImplementedError()
    override fun observeCardSpend(startMillis: Long, endMillis: Long): Flow<Double> = throw NotImplementedError()
}

class FakeEmbeddingDao : TransactionEmbeddingDao {
    val embeddings = mutableListOf<TransactionEmbeddingEntity>()
    override suspend fun insert(embedding: TransactionEmbeddingEntity): Long {
        embeddings.add(embedding)
        return embeddings.size.toLong()
    }
    override suspend fun getByTransactionId(transactionId: Long): TransactionEmbeddingEntity? = embeddings.firstOrNull { it.transactionId == transactionId }
    override suspend fun getAll(): List<TransactionEmbeddingEntity> = embeddings
    override suspend fun count(): Int = embeddings.size
    override suspend fun getUnembeddedTransactionIds(limit: Int): List<Long> = emptyList()
}
