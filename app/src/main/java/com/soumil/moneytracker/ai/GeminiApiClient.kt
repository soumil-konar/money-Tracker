package com.soumil.moneytracker.ai

import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.local.AiPreferences
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.CardType
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AiParsedTransaction(
    val isTransaction: Boolean,
    val amount: Double?,
    val direction: TransactionDirection?,
    val merchant: String?,
    val category: TransactionCategory,
    val accountKind: AccountKind,
    val institutionName: String?,
    val accountLastFour: String?,
    val cardType: CardType?,
    val isUpi: Boolean,
    val isCardBillPayment: Boolean,
    val placeDetail: String?,
    val detailedDescription: String? = placeDetail,
    val confidence: Double = 0.95,
    val countsTowardBudget: Boolean = !isCardBillPayment && category != TransactionCategory.TRANSFER,
    val availableBalance: Double? = null,
)

data class RagAnswerResponse(
    val answer: String,
    val citedTransactionIds: List<Long>,
)

class GeminiApiClient {

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun parseSms(
        smsBody: String,
        sender: String,
        apiKey: String,
        model: String = AiPreferences.DEFAULT_MODEL,
    ): Result<AiParsedTransaction> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Google AI Studio API key is not configured."))
        }

        // Try selected model first, then fallback model if rate limited or not found
        val candidateModels = listOf(model, AiPreferences.FALLBACK_MODEL).distinct()
        var lastError: Throwable? = null

        for (candidate in candidateModels) {
            try {
                val parsed = executeParseRequest(smsBody, sender, apiKey, candidate)
                return@withContext Result.success(parsed)
            } catch (t: Throwable) {
                lastError = t
                // Continue to fallback candidate
            }
        }

        Result.failure(lastError ?: RuntimeException("Failed to parse SMS with AI."))
    }

    suspend fun generateSpendingInsights(
        transactions: List<TransactionRecord>,
        budgetLimit: Double?,
        monthSpent: Double,
        monthIncome: Double,
        apiKey: String,
        model: String = AiPreferences.DEFAULT_MODEL,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key is not configured."))
        }

        try {
            val topSpends = transactions
                .filter { it.direction == TransactionDirection.DEBIT }
                .take(15)
                .joinToString("\n") {
                    "- ${it.merchant}: ₹${it.amount} (${it.category.label}) ${it.note?.let { n -> "[$n]" } ?: ""}"
                }

            val prompt = """
                You are a smart, friendly personal finance assistant for an Indian user.
                Analyze this month's financial status:
                - Monthly Budget: ${budgetLimit?.let { "₹$it" } ?: "Not set"}
                - Total Spent: ₹$monthSpent
                - Total Income: ₹$monthIncome
                - Recent top transactions:
                $topSpends

                Provide exactly 3 or 4 concise, high-value bullet points. Include:
                1. A quick check on budget pacing or saving rate.
                2. An observation on the biggest spend driver (e.g. Food, Shopping, Subscriptions) or a notable place/merchant.
                3. One actionable, practical money-saving tip tailored to these expenses.
                Keep each bullet under 25 words. Do not use Markdown headers, only start each line with bullet point symbol "*".
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            val responseBody = postHttpRequest(
                endpointUrl = "$baseUrl/$model:generateContent?key=$apiKey",
                jsonPayload = requestJson.toString(),
            )

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val text = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()

            val bullets = text.lines()
                .map { it.trim().removePrefix("*").removePrefix("-").trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") }

            if (bullets.isNotEmpty()) {
                Result.success(bullets)
            } else {
                Result.failure(RuntimeException("No insights generated."))
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun testConnection(
        apiKey: String,
        model: String = AiPreferences.DEFAULT_MODEL,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be blank."))
        }
        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Ping. Reply with: Connected to $model successfully.")
                            })
                        })
                    })
                })
            }

            val response = postHttpRequest(
                endpointUrl = "$baseUrl/$model:generateContent?key=$apiKey",
                jsonPayload = requestJson.toString(),
                timeoutMs = 6000,
            )

            val root = JSONObject(response)
            val modelVersion = root.optString("modelVersion", model)
            Result.success("Connected to $modelVersion successfully.")
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun generateEmbedding(
        text: String,
        apiKey: String,
        model: String = "gemini-embedding-2",
        outputDimensionality: Int = 256,
    ): Result<List<Float>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key is not configured."))
        }
        try {
            val payload = JSONObject().apply {
                put("content", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", text)
                        })
                    })
                })
                put("outputDimensionality", outputDimensionality)
            }

            val endpoint = "$baseUrl/$model:embedContent?key=$apiKey"
            val responseBody = postHttpRequest(endpointUrl = endpoint, jsonPayload = payload.toString(), timeoutMs = 6000)
            val root = JSONObject(responseBody)
            val embeddingObj = root.optJSONObject("embedding")
                ?: return@withContext Result.failure(IllegalStateException("No embedding returned: $responseBody"))
            val valuesArray = embeddingObj.optJSONArray("values")
                ?: return@withContext Result.failure(IllegalStateException("Empty embedding values: $responseBody"))

            val list = ArrayList<Float>(valuesArray.length())
            for (i in 0 until valuesArray.length()) {
                list.add(valuesArray.getDouble(i).toFloat())
            }
            Result.success(list)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun queryRagSpendingAssistant(
        userQuery: String,
        retrievedTransactions: List<TransactionRecord>,
        macroContext: String,
        apiKey: String,
        model: String = AiPreferences.DEFAULT_MODEL,
    ): Result<RagAnswerResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("API key is not configured."))
        }
        try {
            val transactionsContext = if (retrievedTransactions.isEmpty()) {
                "No specific transactions matched the search query."
            } else {
                val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                retrievedTransactions.take(25).joinToString("\n") { tx ->
                    val place = tx.note?.takeIf { it.isNotBlank() }?.let { " | Note/Place: $it" } ?: ""
                    "[ID: ${tx.id}] ${dateFormat.format(java.util.Date(tx.occurredAtMillis))} | ${tx.direction} ₹${tx.amount} | ${tx.merchant} (${tx.category.label}) | Account: ${tx.accountName ?: "Unknown"}$place"
                }
            }

            val prompt = """
                You are an intelligent, friendly, and precise personal finance assistant for Money Tracker in India.
                The user is asking a question about their spending, income, or financial habits.

                --- FINANCIAL MACRO CONTEXT ---
                $macroContext

                --- RELEVANT RETRIEVED TRANSACTIONS ---
                $transactionsContext

                --- USER QUESTION ---
                "$userQuery"

                --- INSTRUCTIONS ---
                1. Answer the user's question directly, accurately, and conversationally.
                2. Use the exact numbers, merchants, categories, and dates from the retrieved transactions above. Do not make up or hallucinate transactions.
                3. If the user asks where money was spent, mention the exact place/detail note from the data (e.g. Indiranagar, Swiggy order, etc.).
                4. Provide practical, encouraging financial observations or tips when relevant.
                5. At the very end of your response, on a new line, list ONLY the IDs of the transactions you directly cited or used in the exact format:
                   CITATIONS: [id1, id2, ...]
                   (If none were used, output: CITATIONS: [])
            """.trimIndent()

            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                })
            }

            // Fallback strategy: user-selected model first, then fallback model
            val candidateModels = listOf(model, AiPreferences.FALLBACK_MODEL).distinct()
            var responseBody: String? = null
            var lastError: Throwable? = null

            for (candidate in candidateModels) {
                try {
                    responseBody = postHttpRequest(
                        endpointUrl = "$baseUrl/$candidate:generateContent?key=$apiKey",
                        jsonPayload = requestJson.toString(),
                        timeoutMs = 12000,
                    )
                    break
                } catch (t: Throwable) {
                    lastError = t
                }
            }

            if (responseBody == null) {
                return@withContext Result.failure(lastError ?: RuntimeException("Failed to query Spending Assistant."))
            }

            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            val fullText = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                .orEmpty()

            val citationRegex = Regex("""CITATIONS:\s*\[([0-9,\s]*)\]""", RegexOption.IGNORE_CASE)
            val match = citationRegex.find(fullText)
            val citedIds = if (match != null) {
                match.groupValues[1].split(",")
                    .mapNotNull { it.trim().toLongOrNull() }
            } else {
                emptyList()
            }

            val cleanAnswer = fullText.replace(citationRegex, "").trim()

            Result.success(RagAnswerResponse(answer = cleanAnswer, citedTransactionIds = citedIds))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun executeParseRequest(
        smsBody: String,
        sender: String,
        apiKey: String,
        model: String,
    ): AiParsedTransaction {
        val prompt = "Analyze this financial SMS alert from sender '$sender':\n\"$smsBody\""

        val schema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("isTransaction", JSONObject().apply {
                    put("type", "BOOLEAN")
                    put("description", "True if this SMS represents an actual debit, credit, or bill payment transaction. False for OTPs, marketing, loans, or pending collect requests.")
                })
                put("amount", JSONObject().apply {
                    put("type", "NUMBER")
                    put("description", "Transaction amount in INR")
                })
                put("direction", JSONObject().apply {
                    put("type", "STRING")
                    put("enum", JSONArray(listOf("CREDIT", "DEBIT")))
                })
                put("merchant", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Clean merchant or beneficiary name (e.g. Swiggy, Starbucks, Amazon, or person name) without VPA handles, bank noise, or reference numbers.")
                })
                put("category", JSONObject().apply {
                    put("type", "STRING")
                    put("enum", JSONArray(listOf("FOOD", "TRAVEL", "BILLS", "SHOPPING", "TRANSFER", "SALARY", "SUBSCRIPTION", "HEALTH", "OTHER")))
                })
                put("accountKind", JSONObject().apply {
                    put("type", "STRING")
                    put("enum", JSONArray(listOf("BANK", "CARD", "WALLET", "UPI", "CASH")))
                })
                put("institutionName", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Bank or card institution e.g. HDFC Bank, SBI, ICICI Bank, Axis Bank.")
                })
                put("accountLastFour", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Last 4 digits of bank account or card if mentioned.")
                })
                put("cardType", JSONObject().apply {
                    put("type", "STRING")
                    put("enum", JSONArray(listOf("CREDIT", "DEBIT", "NONE")))
                })
                put("isUpi", JSONObject().apply {
                    put("type", "BOOLEAN")
                })
                put("isCardBillPayment", JSONObject().apply {
                    put("type", "BOOLEAN")
                    put("description", "True if money was paid to clear a credit card bill.")
                })
                put("placeDetail", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "Specific detail of where or what the spend was done e.g. 'Indiranagar Bangalore branch', 'Swiggy Food order', 'Metro Card recharge', or specific UPI VPA context.")
                })
                put("detailedDescription", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "A rich, descriptive explanation of what took place (e.g., 'Dinner order on Swiggy via HDFC Credit Card', 'Monthly Netflix subscription auto-debit', 'Flight booking on MakeMyTrip', 'Metro SmartCard recharge via PhonePe UPI', 'Chai & snacks at Indiranagar branch'). Synthesize what was purchased, the platform, and payment instrument context.")
                })
            })
            put("required", JSONArray(listOf("isTransaction", "direction", "merchant", "category", "accountKind", "placeDetail", "detailedDescription")))
        }

        val requestPayload = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("responseSchema", schema)
                put("temperature", 0.1)
            })
        }

        val responseBody = postHttpRequest(
            endpointUrl = "$baseUrl/$model:generateContent?key=$apiKey",
            jsonPayload = requestPayload.toString(),
            timeoutMs = 7000,
        )

        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Empty candidates in Gemini response: $responseBody")
        val candidate = candidates.getJSONObject(0)
        val content = candidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        val jsonText = parts.getJSONObject(0).getString("text")

        val resultObj = JSONObject(jsonText)
        val isTransaction = resultObj.optBoolean("isTransaction", false)
        val amount = resultObj.optDouble("amount").takeIf { !it.isNaN() && it > 0.0 }
        val directionStr = resultObj.optString("direction")
        val direction = when (directionStr.uppercase()) {
            "CREDIT" -> TransactionDirection.CREDIT
            "DEBIT" -> TransactionDirection.DEBIT
            else -> null
        }
        val merchant = resultObj.optString("merchant").takeIf { it.isNotBlank() }
        val categoryStr = resultObj.optString("category")
        val category = runCatching { TransactionCategory.valueOf(categoryStr) }
            .getOrDefault(TransactionCategory.OTHER)
        val accountKindStr = resultObj.optString("accountKind")
        val accountKind = runCatching { AccountKind.valueOf(accountKindStr) }
            .getOrDefault(AccountKind.BANK)
        val institutionName = resultObj.optString("institutionName").takeIf { it.isNotBlank() }
        val accountLastFour = resultObj.optString("accountLastFour")
            .filter(Char::isDigit)
            .takeLast(4)
            .takeIf { it.length == 4 }
        val cardTypeStr = resultObj.optString("cardType")
        val cardType = when (cardTypeStr.uppercase()) {
            "CREDIT" -> CardType.CREDIT
            "DEBIT" -> CardType.DEBIT
            else -> null
        }
        val isUpi = resultObj.optBoolean("isUpi", false)
        val isCardBillPayment = resultObj.optBoolean("isCardBillPayment", false)
        val placeDetail = resultObj.optString("placeDetail").takeIf { it.isNotBlank() }
        val detailedDescription = resultObj.optString("detailedDescription").takeIf { it.isNotBlank() } ?: placeDetail

        return AiParsedTransaction(
            isTransaction = isTransaction,
            amount = amount,
            direction = direction,
            merchant = merchant,
            category = category,
            accountKind = accountKind,
            institutionName = institutionName,
            accountLastFour = accountLastFour,
            cardType = cardType,
            isUpi = isUpi,
            isCardBillPayment = isCardBillPayment,
            placeDetail = placeDetail,
            detailedDescription = detailedDescription,
            confidence = 0.95,
        )
    }

    private fun postHttpRequest(
        endpointUrl: String,
        jsonPayload: String,
        timeoutMs: Int = 8000,
    ): String {
        val url = URL(endpointUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.doOutput = true

        OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
            writer.write(jsonPayload)
            writer.flush()
        }

        val statusCode = connection.responseCode
        val isError = statusCode !in 200..299
        val inputStream = if (isError) connection.errorStream else connection.inputStream

        val response = BufferedReader(InputStreamReader(inputStream ?: connection.inputStream, "UTF-8")).use { reader ->
            reader.readText()
        }

        if (isError) {
            val errorMsg = runCatching {
                JSONObject(response).optJSONObject("error")?.optString("message")
            }.getOrNull() ?: "HTTP $statusCode: $response"
            throw RuntimeException("Gemini API Error ($statusCode): $errorMsg")
        }

        return response
    }
}
