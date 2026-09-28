package com.moneytracker.app

import com.moneytracker.app.data.db.TransactionDao
import org.junit.Assert.assertEquals
import org.junit.Test

class Fts5SearchTest {

    @Test
    fun testTrigramQuerySanitizationPartialToken() {
        val query = "swig"
        val sanitized = TransactionDao.sanitizeFts5Query(query)
        assertEquals("\"swig\"", sanitized)
    }

    @Test
    fun testTrigramQuerySanitizationMultipleTokensWithSpecialChars() {
        val query = "amazon@pay - offer"
        val sanitized = TransactionDao.sanitizeFts5Query(query)
        assertEquals("\"amazon\" AND \"pay\" AND \"offer\"", sanitized)
    }

    @Test
    fun testTrigramQuerySanitizationEmptySpecialString() {
        val query = "---***@"
        val sanitized = TransactionDao.sanitizeFts5Query(query)
        assertEquals("", sanitized)
    }

    @Test
    fun testTrigramQuerySanitizationOrDisjunction() {
        val query = "swig OR zomato"
        val sanitized = TransactionDao.sanitizeFts5Query(query)
        assertEquals("\"swig\" OR \"zomato\"", sanitized)
    }
}
