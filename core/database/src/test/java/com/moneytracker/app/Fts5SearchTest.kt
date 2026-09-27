package com.moneytracker.app

import org.junit.Assert.assertEquals
import org.junit.Test

class Fts5SearchTest {

    private fun sanitizeFts5Query(rawQuery: String): String {
        val clean = rawQuery.replace(Regex("""[^\w\s]"""), " ").trim()
        val tokens = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return "\"\""
        return tokens.joinToString(" AND ") { "\"$it*\"" }
    }

    @Test
    fun testTrigramQuerySanitizationPartialToken() {
        val query = "swig"
        val sanitized = sanitizeFts5Query(query)
        assertEquals("\"swig*\"", sanitized)
    }

    @Test
    fun testTrigramQuerySanitizationMultipleTokensWithSpecialChars() {
        val query = "amazon@pay - offer"
        val sanitized = sanitizeFts5Query(query)
        assertEquals("\"amazon*\" AND \"pay*\" AND \"offer*\"", sanitized)
    }

    @Test
    fun testTrigramQuerySanitizationEmptySpecialString() {
        val query = "---***@"
        val sanitized = sanitizeFts5Query(query)
        assertEquals("\"\"", sanitized)
    }
}
