package com.moneytracker.app

import com.moneytracker.app.ai.GeminiApiClient
import com.moneytracker.app.ai.GeminiApiException
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiApiClientTest {

    @Test
    fun `default embedding model is text-embedding-004`() {
        assertEquals("text-embedding-004", GeminiApiClient.DEFAULT_EMBEDDING_MODEL)
    }

    @Test
    fun `network connection and read timeout constants meet platform modernization requirements`() {
        assertEquals(15_000, GeminiApiClient.CONNECT_TIMEOUT_MS)
        assertEquals(30_000, GeminiApiClient.READ_TIMEOUT_MS)
        assertEquals(3, GeminiApiClient.MAX_RETRIES)
    }

    @Test
    fun `domain exception hierarchy produces clear user-friendly messages`() {
        val rateLimitEx = GeminiApiException.RateLimitExceededException()
        assertTrue(rateLimitEx.message?.contains("rate limit", ignoreCase = true) == true)

        val serviceEx = GeminiApiException.ServiceUnavailableException()
        assertTrue(serviceEx.message?.contains("unavailable", ignoreCase = true) == true)

        val timeoutEx = GeminiApiException.NetworkTimeoutException()
        assertTrue(timeoutEx.message?.contains("timed out", ignoreCase = true) == true)

        val invalidKeyEx = GeminiApiException.InvalidApiKeyException()
        assertTrue(invalidKeyEx.message?.contains("invalid or unauthorized", ignoreCase = true) == true)

        val networkUnavailableEx = GeminiApiException.NetworkUnavailableException()
        assertTrue(networkUnavailableEx.message?.contains("network", ignoreCase = true) == true)

        val clientError = GeminiApiException.ClientErrorException(404, "Not found")
        assertEquals(404, clientError.statusCode)
    }


    @Test
    fun `client methods return InvalidApiKeyException when api key is blank`() = runTest {
        val client = GeminiApiClient()

        val testResult = client.testConnection(apiKey = "")
        assertTrue(testResult.isFailure)
        assertTrue(testResult.exceptionOrNull() is GeminiApiException.InvalidApiKeyException)

        val parseResult = client.parseSms(smsBody = "Spent 100", sender = "HDFC", apiKey = "")
        assertTrue(parseResult.isFailure)
        assertTrue(parseResult.exceptionOrNull() is GeminiApiException.InvalidApiKeyException)

        val insightsResult = client.generateSpendingInsights(
            transactions = emptyList(),
            budgetLimit = 5000.0,
            monthSpent = 1000.0,
            monthIncome = 10000.0,
            apiKey = "",
        )
        assertTrue(insightsResult.isFailure)
        assertTrue(insightsResult.exceptionOrNull() is GeminiApiException.InvalidApiKeyException)

        val embeddingResult = client.generateEmbedding(text = "sample", apiKey = "")
        assertTrue(embeddingResult.isFailure)
        assertTrue(embeddingResult.exceptionOrNull() is GeminiApiException.InvalidApiKeyException)

        val ragResult = client.queryRagSpendingAssistant(
            userQuery = "hello",
            retrievedTransactions = emptyList(),
            macroContext = "",
            apiKey = "",
        )
        assertTrue(ragResult.isFailure)
        assertTrue(ragResult.exceptionOrNull() is GeminiApiException.InvalidApiKeyException)
    }

    @Test
    fun `rag response parser extracts answer and cited transaction IDs correctly`() {
        val jsonText = """
        {
            "answer": "You spent a total of ₹1,450 on food and dining across Swiggy and Zomato.",
            "citedTransactionIds": [101, 102, 105]
        }
        """.trimIndent()

        val root = JSONObject(jsonText)
        val answer = root.optString("answer")
        val idsArray = root.optJSONArray("citedTransactionIds") ?: JSONArray()
        val citedIds = ArrayList<Long>()
        for (i in 0 until idsArray.length()) {
            citedIds.add(idsArray.getLong(i))
        }

        assertEquals("You spent a total of ₹1,450 on food and dining across Swiggy and Zomato.", answer)
        assertEquals(listOf(101L, 102L, 105L), citedIds)
    }

    @Test
    fun `ai transaction json payload parsing correctly deserializes fields`() {
        val jsonText = """
        {
            "isTransaction": true,
            "amount": 420.50,
            "direction": "DEBIT",
            "merchant": "Swiggy Indiranagar",
            "category": "FOOD",
            "accountKind": "BANK",
            "institutionName": "HDFC Bank",
            "accountLastFour": "4321",
            "isUpi": true,
            "isCardBillPayment": false,
            "placeDetail": "Indiranagar",
            "detailedDescription": "Food order delivered via Swiggy in Indiranagar",
            "availableBalance": 54200.00
        }
        """.trimIndent()

        val json = JSONObject(jsonText)
        assertTrue(json.optBoolean("isTransaction", false))
        assertEquals(420.50, json.optDouble("amount"), 0.001)
        assertEquals(TransactionDirection.DEBIT, TransactionDirection.valueOf(json.getString("direction")))
        assertEquals("Swiggy Indiranagar", json.optString("merchant"))
        assertEquals(TransactionCategory.FOOD, TransactionCategory.valueOf(json.getString("category")))
        assertEquals(AccountKind.BANK, AccountKind.valueOf(json.getString("accountKind")))
        assertEquals("HDFC Bank", json.optString("institutionName"))
        assertEquals("4321", json.optString("accountLastFour"))
        assertTrue(json.optBoolean("isUpi"))
        assertEquals(54200.00, json.optDouble("availableBalance"), 0.001)
    }
}
