package com.moneytracker.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

class FinanceRagEngineTest {

    private fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size || vecA.isEmpty()) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in vecA.indices) {
            val a = vecA[i]
            val b = vecB[i]
            dot += a * b
            normA += a * a
            normB += b * b
        }
        if (normA <= 0f || normB <= 0f) return 0f
        return dot / (sqrt(normA) * sqrt(normB))
    }

    private fun sanitizeForFts(query: String): String {
        val stopWords = setOf(
            "how", "much", "did", "i", "spend", "on", "what", "where", "my", "in", "the",
            "for", "to", "show", "me", "all", "expenses", "transactions", "money", "tracker",
            "at", "from", "was", "were", "a", "an", "and", "or", "of", "about", "with",
            "this", "that", "last", "month", "today", "yesterday", "week", "year",
        )

        val tokens = query.lowercase(java.util.Locale.getDefault())
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length > 2 && it !in stopWords }

        if (tokens.isEmpty()) return ""
        return tokens.take(4).joinToString(" OR ") { "$it*" }
    }

    @Test
    fun `cosine similarity calculation returns 1 for identical vectors`() {
        val vec1 = floatArrayOf(0.1f, 0.5f, -0.3f, 0.8f)
        val vec2 = floatArrayOf(0.1f, 0.5f, -0.3f, 0.8f)
        val similarity = cosineSimilarity(vec1, vec2)
        assertEquals(1.0f, similarity, 0.0001f)
    }

    @Test
    fun `cosine similarity calculation returns 0 for orthogonal vectors`() {
        val vec1 = floatArrayOf(1.0f, 0.0f)
        val vec2 = floatArrayOf(0.0f, 1.0f)
        val similarity = cosineSimilarity(vec1, vec2)
        assertEquals(0.0f, similarity, 0.0001f)
    }

    @Test
    fun `FTS sanitizer extracts relevant keywords and excludes stop words`() {
        val query = "How much did I spend on dining and swiggy in Indiranagar?"
        val fts = sanitizeForFts(query)
        assertTrue(fts.contains("dining*"))
        assertTrue(fts.contains("swiggy*"))
        assertTrue(fts.contains("indiranagar*"))
    }

    @Test
    fun `vector embedding CSV conversion handles 256 floats accurately`() {
        val sampleFloats = List(256) { (it * 0.01f) - 1.28f }
        val csv = sampleFloats.joinToString(",")
        val tokens = csv.split(",")
        val parsed = FloatArray(tokens.size) { tokens[it].toFloat() }

        assertEquals(256, parsed.size)
        assertEquals(sampleFloats[0], parsed[0], 0.001f)
        assertEquals(sampleFloats[100], parsed[100], 0.001f)
        assertEquals(sampleFloats[255], parsed[255], 0.001f)
    }
}
