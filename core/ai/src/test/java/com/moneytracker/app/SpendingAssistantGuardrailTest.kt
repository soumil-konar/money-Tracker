package com.moneytracker.app

import com.moneytracker.app.ai.SpendingAssistantGuardrail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingAssistantGuardrailTest {

    @Test
    fun `test greetings are recognized and return charming greeting`() {
        val greetings = listOf("hi", "Hello", "HEY", "good morning", "Namaste", "yo", "Hiya!")
        for (greeting in greetings) {
            assertTrue("Expected '$greeting' to be recognized as greeting", SpendingAssistantGuardrail.isGreeting(greeting))
            assertFalse("Greeting '$greeting' should not be flagged as off-topic", SpendingAssistantGuardrail.isOffTopic(greeting))
        }

        val greetingResponse = SpendingAssistantGuardrail.getGreetingResponse()
        assertTrue(greetingResponse.contains("Spending Assistant"))
        assertTrue(greetingResponse.contains("🪙"))
    }

    @Test
    fun `test random off-topic questions are flagged as off-topic`() {
        val offTopicQueries = listOf(
            "What is the capital of France?",
            "Write a python script to reverse a string",
            "Who won the cricket World Cup?",
            "Can you write a poem about the stars?",
            "How do airplanes stay in the sky?",
            "Tell me a joke about dogs",
            "What is the weather in Delhi today?",
            "How tall is Mount Everest?",
            "Who is the president of the United States?",
            "Explain the theory of relativity",
            "What is quantum entanglement?",
            "How to bake a chocolate cake?",
        )

        for (query in offTopicQueries) {
            val isOff = SpendingAssistantGuardrail.isOffTopic(query)
            assertTrue("Expected query '$query' to be flagged as off-topic", isOff)

            val cuteResponse = SpendingAssistantGuardrail.getCuteOffTopicResponse(query)
            assertNotNull(cuteResponse)
            assertTrue("Expected cute response for '$query' to be cheerful and non-empty", cuteResponse.length > 20)
            assertTrue(
                "Response should contain financial guidance emojis",
                cuteResponse.contains("🪙") || cuteResponse.contains("🐷") || cuteResponse.contains("💳") || cuteResponse.contains("🧮"),
            )
        }
    }

    @Test
    fun `test on-topic financial queries are permitted`() {
        val onTopicQueries = listOf(
            "How much did I spend on food this month?",
            "What are my credit card debits?",
            "Am I within my budget?",
            "Show me my transactions at Swiggy",
            "Where did my money go on Friday?",
            "What is my safe daily spend?",
            "How much did I pay for electricity?",
            "Did I pay my Netflix subscription?",
            "What are my top expenses?",
            "Can you give me tips to save money?",
            "Coffee",
            "Zomato orders",
            "Breakdown of travel expenses",
            "Show me dining expenses in Indiranagar",
            "How much balance do I have left?",
            "Did I receive my salary?",
            "How much cash did I withdraw from ATM?",
        )

        for (query in onTopicQueries) {
            val isOff = SpendingAssistantGuardrail.isOffTopic(query)
            assertFalse("Expected financial query '$query' to be on-topic", isOff)
        }
    }

    @Test
    fun `test known merchants from ledger prevent false off-topic flags`() {
        val customMerchants = setOf("Ramesh General Store", "Artisan Bakery", "Decathlon Sports")

        val query1 = "How much did I pay at Ramesh General Store?"
        val query2 = "Show me spends at Decathlon Sports"

        // Without known merchants, may still match "spend" or "pay", but with known merchants it explicitly matches
        assertFalse(SpendingAssistantGuardrail.isOffTopic(query1, knownMerchants = customMerchants))
        assertFalse(SpendingAssistantGuardrail.isOffTopic(query2, knownMerchants = customMerchants))
    }

    @Test
    fun `test known accounts from ledger prevent false off-topic flags`() {
        val customAccounts = setOf("ICICI Sapphiro", "HDFC Salary Account", "Petty Cash Wallet")

        val query = "Check my ICICI Sapphiro"
        assertFalse(SpendingAssistantGuardrail.isOffTopic(query, knownAccounts = customAccounts))
    }

    @Test
    fun `test cute responses have variety and delightful personality`() {
        val q1 = "Tell me about quantum physics"
        val q2 = "Can dogs eat watermelon?"

        val resp1 = SpendingAssistantGuardrail.getCuteOffTopicResponse(q1)
        val resp2 = SpendingAssistantGuardrail.getCuteOffTopicResponse(q2)

        assertTrue(resp1.isNotBlank())
        assertTrue(resp2.isNotBlank())
        assertTrue(resp1.contains("expenses") || resp1.contains("budget") || resp1.contains("money") || resp1.contains("spending"))
        assertTrue(resp2.contains("expenses") || resp2.contains("budget") || resp2.contains("money") || resp2.contains("spending"))
    }
}

