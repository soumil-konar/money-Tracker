package com.moneytracker.app

import com.moneytracker.app.ai.GeminiApiClient
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
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
