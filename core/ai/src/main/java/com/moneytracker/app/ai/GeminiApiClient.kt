package com.moneytracker.app.ai

import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

sealed class GeminiApiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class RateLimitExceededException(
        message: String = "AI service rate limit reached. Please wait a moment before trying again.",
        cause: Throwable? = null,
    ) : GeminiApiException(message, cause)

    class ServiceUnavailableException(
        message: String = "AI service is temporarily unavailable (503). Please try again shortly.",
        cause: Throwable? = null,
    ) : GeminiApiException(message, cause)

    class NetworkTimeoutException(
        message: String = "Connection to AI service timed out. Please check your internet connection.",
        cause: Throwable? = null,
    ) : GeminiApiException(message, cause)

    class InvalidApiKeyException(
        message: String = "Google AI Studio API key is invalid or unauthorized. Please check your settings.",
        cause: Throwable? = null,
    ) : GeminiApiException(message, cause)

    class NetworkUnavailableException(
        message: String = "Network connection failed. Please check your internet connection.",
        cause: Throwable? = null,
    ) : GeminiApiException(message, cause)

    class ClientErrorException(
        val statusCode: Int,
        message: String = "AI service request failed ($statusCode).",
        cause: Throwable? = null,
    ) : GeminiApiException(message, cause)
}


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

open class GeminiApiClient {

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    suspend fun parseSms(
        smsBody: String,
        sender: String,
        apiKey: String,
        model: String = DEFAULT_MODEL,
    ): Result<AiParsedTransaction> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiApiException.InvalidApiKeyException("Google AI Studio API key is not configured."))
        }

        // Try selected model first, then fallback model if rate limited or not found
        val candidateModels = listOf(model, FALLBACK_MODEL).distinct()
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
        model: String = DEFAULT_MODEL,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiApiException.InvalidApiKeyException("API key is not configured."))
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
                endpointUrl = "$baseUrl/$model:generateContent",
                apiKey = apiKey,
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
        model: String = DEFAULT_MODEL,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiApiException.InvalidApiKeyException("API Key cannot be blank."))
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
                endpointUrl = "$baseUrl/$model:generateContent",
                apiKey = apiKey,
                jsonPayload = requestJson.toString(),
            )

            val root = JSONObject(response)
            val modelVersion = root.optString("modelVersion", model)
            Result.success("Connected to $modelVersion successfully.")
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    open suspend fun generateEmbedding(
        text: String,
        apiKey: String,
        model: String = DEFAULT_EMBEDDING_MODEL,
        outputDimensionality: Int = 256,
    ): Result<List<Float>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiApiException.InvalidApiKeyException("API key is not configured."))
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

            val endpoint = "$baseUrl/$model:embedContent"
            val responseBody = postHttpRequest(endpointUrl = endpoint, apiKey = apiKey, jsonPayload = payload.toString())
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
        model: String = DEFAULT_MODEL,
    ): Result<RagAnswerResponse> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(GeminiApiException.InvalidApiKeyException("API key is not configured."))
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
                5. GUARDRAILS & OFF-TOPIC QUESTIONS:
                   If the user asks an inquiry that is unrelated to personal finances, spending, money, budgets, savings, transactions, accounts, or the app (such as trivia, general knowledge, coding, creative writing, science, recipes, homework, weather, or random chit-chat):
                   DO NOT answer the off-topic question. Instead, reply with a cute, friendly, and playful message explaining that you are a cute financial piggy bank assistant who only knows about their money and budgets, and invite them to ask about their expenses, coffee runs, or savings instead! (e.g. "Beep boop! 🪙✨ I'm just a little financial piggy bank assistant! 🐷 I only know about your coins, rupees, budgets, and spending habits. Ask me about your expenses, coffee runs, or savings goals! 🍰💳").
                6. At the very end of your response, on a new line, list ONLY the IDs of the transactions you directly cited or used in the exact format:
                   CITATIONS: [id1, id2, ...]
                   (If none were used or if the question was off-topic, output: CITATIONS: [])
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
            val candidateModels = listOf(model, FALLBACK_MODEL).distinct()
            var responseBody: String? = null
            var lastError: Throwable? = null

            for (candidate in candidateModels) {
                try {
                    responseBody = postHttpRequest(
                        endpointUrl = "$baseUrl/$candidate:generateContent",
                        apiKey = apiKey,
                        jsonPayload = requestJson.toString(),
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

    private suspend fun executeParseRequest(
        smsBody: String,
        sender: String,
        apiKey: String,
        model: String,
    ): AiParsedTransaction {
        val senderPrefix = if (sender.isNotBlank()) "Sender: $sender\n" else ""
        val prompt = """
You are an expert financial transaction parsing engine.
Your task is to parse raw banking SMS and notification text into structured JSON.

### Extraction Rules:
1. "isTransaction": boolean. Set to false if the message is an OTP, promotional spam, balance-only inquiry, or security alert without a financial debit/credit event.
2. "amount": number. The exact numerical currency amount debited or credited.
3. "direction": "DEBIT" or "CREDIT".
4. "accountLast4": string or null. The last 4 digits of the card or bank account (e.g. "6942" for "XX6942").
5. "merchant": string. The human-readable recipient or sender name to be displayed as the main transaction title on the user's dashboard:
   - When encountering UPI strings like "UPI/P2M/<rrn>/<payee>" or "UPI/P2P/<rrn>/<payee>", strip the protocol, mode, and RRN code. Extract ONLY the payee name.
   - If the payee name is split across newlines (e.g., "PRAKASH\nCHANDRA CHA"), join the fragments with a single space.
   - Strictly strip out fraud warnings, disclaimers, and bank footers (e.g., "Not you?", "SMS BLOCKUPI", "Axis Bank", "Call helpline").
   - Convert all-caps payee names to Title Case (e.g., "PRAKASH CHANDRA CHA" -> "Prakash Chandra Cha"), keeping standard acronyms intact (e.g., "UPI", "IRCTC", "ATM").
6. "category": string. One of: "FOOD", "SHOPPING", "BILLS", "ENTERTAINMENT", "TRANSPORT", "GROCERY", "HEALTH", "INVESTMENT", "TRANSFER", "OTHER".
7. "availableBalance": number or null.

### Output Format:
Return raw JSON ONLY. No markdown code blocks, no backticks (```), no conversational filler.

### Examples:
Input:
INR 1200.00 debited
A/c no. XX6942
26-09-26, 23:09:50
UPI/P2M/626969941600/Raju Wines
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
Output:
{"isTransaction":true,"amount":1200.0,"direction":"DEBIT","merchant":"Raju Wines","accountLast4":"6942","category":"ENTERTAINMENT","availableBalance":null}

Input:
INR 10.00 debited
A/c no. XX6942
28-09-26, 18:38:35
UPI/P2M/627192752017/PRAKASH
CHANDRA CHA
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
Output:
{"isTransaction":true,"amount":10.0,"direction":"DEBIT","merchant":"Prakash Chandra Cha","accountLast4":"6942","category":"FOOD","availableBalance":null}

Input:
$senderPrefix$smsBody
Output:
        """.trimIndent()

        val schema = JSONObject().apply {
            put("type", "OBJECT")
            put("properties", JSONObject().apply {
                put("isTransaction", JSONObject().apply {
                    put("type", "BOOLEAN")
                    put("description", "Set to false if OTP, promotional spam, balance inquiry, or security alert without a financial debit/credit event.")
                })
                put("amount", JSONObject().apply {
                    put("type", "NUMBER")
                    put("description", "The exact numerical currency amount debited or credited.")
                })
                put("direction", JSONObject().apply {
                    put("type", "STRING")
                    put("enum", JSONArray(listOf("DEBIT", "CREDIT")))
                })
                put("accountLast4", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "The last 4 digits of card or bank account.")
                })
                put("merchant", JSONObject().apply {
                    put("type", "STRING")
                    put("description", "The clean human-readable recipient or sender name.")
                })
                put("category", JSONObject().apply {
                    put("type", "STRING")
                    put("enum", JSONArray(listOf("FOOD", "SHOPPING", "BILLS", "ENTERTAINMENT", "TRANSPORT", "GROCERY", "HEALTH", "INVESTMENT", "TRANSFER", "OTHER")))
                })
                put("availableBalance", JSONObject().apply {
                    put("type", "NUMBER")
                    put("description", "Available balance if mentioned in message, otherwise null.")
                })
            })
            put("required", JSONArray(listOf("isTransaction")))
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
            endpointUrl = "$baseUrl/$model:generateContent",
            apiKey = apiKey,
            jsonPayload = requestPayload.toString(),
        )

        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates")
            ?: throw IllegalStateException("Empty candidates in Gemini response: $responseBody")
        val candidate = candidates.getJSONObject(0)
        val content = candidate.getJSONObject("content")
        val parts = content.getJSONArray("parts")
        val rawJsonText = parts.getJSONObject(0).getString("text")

        val sanitizedJson = sanitizeJsonText(rawJsonText)
        val resultObj = JSONObject(sanitizedJson)
        val rawIsTransaction = resultObj.optBoolean("isTransaction", true)
        if (!rawIsTransaction) {
            return AiParsedTransaction(
                isTransaction = false,
                amount = null,
                direction = null,
                merchant = null,
                category = TransactionCategory.OTHER,
                accountKind = AccountKind.BANK,
                institutionName = null,
                accountLastFour = null,
                cardType = null,
                isUpi = false,
                isCardBillPayment = false,
                placeDetail = null,
                confidence = 0.0,
                countsTowardBudget = false,
                availableBalance = null,
            )
        }

        val amount = if (resultObj.has("amount") && !resultObj.isNull("amount")) {
            resultObj.optDouble("amount").takeIf { !it.isNaN() && it > 0.0 }
        } else null

        val directionStr = resultObj.optString("direction", "").trim().uppercase(java.util.Locale.ROOT)
        val direction = when {
            directionStr.contains("DEBIT") -> TransactionDirection.DEBIT
            directionStr.contains("CREDIT") -> TransactionDirection.CREDIT
            else -> null
        }

        val rawMerchant = resultObj.optString("merchant", "").trim()
        val cleanedMerchant = MerchantSanitizer.sanitizeMerchantName(rawMerchant)
        val isBogusMerchant = cleanedMerchant.isBlank() || cleanedMerchant.equals("Merchant", ignoreCase = true) ||
            cleanedMerchant.startsWith("be recorded", ignoreCase = true) || cleanedMerchant.contains("recorded by amc", ignoreCase = true)

        val isTransaction = amount != null && direction != null && !isBogusMerchant
        val merchant = if (isTransaction) cleanedMerchant else null

        val categoryStr = resultObj.optString("category", "").trim().uppercase(java.util.Locale.ROOT)
        val category = mapCategory(categoryStr)

        val accountLastFour = resultObj.optString("accountLast4", "")
            .ifEmpty { resultObj.optString("accountLastFour", "") }
            .filter(Char::isDigit)
            .takeLast(4)
            .takeIf { it.length == 4 }

        val availableBalance = if (resultObj.has("availableBalance") && !resultObj.isNull("availableBalance")) {
            val bal = resultObj.optDouble("availableBalance")
            if (!bal.isNaN()) bal else null
        } else null

        val lowerRaw = smsBody.lowercase(java.util.Locale.ROOT)
        val isUpi = resultObj.optBoolean("isUpi", false) || listOf("upi", "vpa", "/p2a/", "/p2m/", "/p2p/", "@").any { it in lowerRaw } || sender.contains("upi", ignoreCase = true)
        val isCreditCard = listOf("credit card", "card ending", "card xx").any { it in lowerRaw }
        val isCardBillPayment = resultObj.optBoolean("isCardBillPayment", false) ||
            listOf("payment received towards", "credit card bill", "credited to your credit card").any { it in lowerRaw } ||
            (category == TransactionCategory.TRANSFER && isCreditCard)

        val accountKindStr = resultObj.optString("accountKind", "")
        val accountKind = runCatching { AccountKind.valueOf(accountKindStr) }.getOrNull() ?: when {
            isCreditCard && !isCardBillPayment -> AccountKind.CARD
            isUpi -> AccountKind.UPI
            else -> AccountKind.BANK
        }

        val cardTypeStr = resultObj.optString("cardType", "")
        val cardType = when (cardTypeStr.uppercase(java.util.Locale.ROOT)) {
            "CREDIT" -> CardType.CREDIT
            "DEBIT" -> CardType.DEBIT
            else -> if (isCreditCard) CardType.CREDIT else null
        }

        val institutionName = resultObj.optString("institutionName").takeIf { it.isNotBlank() }
            ?: com.moneytracker.app.bank.BankDetector.resolveBankInstitution(sender, smsBody)

        val placeDetail = resultObj.optString("placeDetail").takeIf { it.isNotBlank() }
        val detailedDescription = resultObj.optString("detailedDescription").takeIf { it.isNotBlank() }
            ?: merchant?.let { "$it - AI Verified" }
            ?: placeDetail

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
            confidence = 0.98,
            countsTowardBudget = !isCardBillPayment && category != TransactionCategory.TRANSFER,
            availableBalance = availableBalance,
        )
    }

    private fun sanitizeJsonText(raw: String): String {
        var text = raw.trim()
        val fenceRegex = Regex("""```(?:json)?\s*([\s\S]*?)\s*```""", RegexOption.IGNORE_CASE)
        val fenceMatch = fenceRegex.find(text)
        if (fenceMatch != null) {
            text = fenceMatch.groupValues[1].trim()
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```").trim()
            if (text.endsWith("```")) {
                text = text.removeSuffix("```").trim()
            }
        }
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1).trim()
        }
        return text
    }

    private fun mapCategory(cat: String): TransactionCategory {
        return when {
            cat.contains("FOOD") || cat.contains("DINING") || cat.contains("GROCERY") -> TransactionCategory.FOOD
            cat.contains("SHOPPING") || cat.contains("RETAIL") -> TransactionCategory.SHOPPING
            cat.contains("BILL") || cat.contains("UTILITY") -> TransactionCategory.BILLS
            cat.contains("TRANSPORT") || cat.contains("TRAVEL") || cat.contains("CAB") -> TransactionCategory.TRAVEL
            cat.contains("ENTERTAINMENT") || cat.contains("MOVIE") || cat.contains("SUBSCRIPTION") -> TransactionCategory.SUBSCRIPTION
            cat.contains("HEALTH") || cat.contains("MEDICAL") -> TransactionCategory.HEALTH
            cat.contains("SALARY") -> TransactionCategory.SALARY
            cat.contains("TRANSFER") || cat.contains("INVESTMENT") -> TransactionCategory.TRANSFER
            else -> TransactionCategory.OTHER
        }
    }

    private suspend fun postHttpRequest(
        endpointUrl: String,
        apiKey: String,
        jsonPayload: String,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
        maxRetries: Int = MAX_RETRIES,
    ): String {
        var lastException: Throwable? = null
        var currentBackoff = INITIAL_BACKOFF_MS

        for (attempt in 0..maxRetries) {
            try {
                val url = URL(endpointUrl)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("x-goog-api-key", apiKey)
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    doOutput = true
                }

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
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $statusCode response"

                    // Immediate fail for invalid/unauthorized API keys
                    if (statusCode == 401 || statusCode == 403) {
                        throw GeminiApiException.InvalidApiKeyException(
                            message = "Google AI Studio API key is invalid or unauthorized: $errorMsg",
                        )
                    }

                    // Retry on transient rate limits (429) or service unavailable (503)
                    val isTransient = statusCode == 429 || statusCode == 503
                    if (isTransient && attempt < maxRetries) {
                        val jitter = Random.nextLong(0, (currentBackoff * 0.3).toLong().coerceAtLeast(50L))
                        val sleepDuration = currentBackoff + jitter
                        delay(sleepDuration)
                        currentBackoff *= 2
                        continue
                    }

                    // Map final HTTP errors into domain exceptions
                    throw when (statusCode) {
                        429 -> GeminiApiException.RateLimitExceededException(
                            message = "AI service rate limit reached. Please wait a moment before trying again.",
                        )
                        503 -> GeminiApiException.ServiceUnavailableException(
                            message = "AI service is temporarily unavailable (503). Please retry shortly.",
                        )
                        else -> GeminiApiException.ClientErrorException(
                            statusCode = statusCode,
                            message = "AI service request failed with HTTP $statusCode: $errorMsg",
                        )
                    }
                }

                return response
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (domainEx: GeminiApiException) {
                throw domainEx
            } catch (timeout: java.net.SocketTimeoutException) {
                lastException = timeout
                if (attempt < maxRetries) {
                    val jitter = Random.nextLong(0, (currentBackoff * 0.3).toLong().coerceAtLeast(50L))
                    delay(currentBackoff + jitter)
                    currentBackoff *= 2
                } else {
                    throw GeminiApiException.NetworkTimeoutException(
                        message = "AI connection timed out after $maxRetries attempts. Please check your internet connection.",
                        cause = timeout,
                    )
                }
            } catch (ioe: java.io.IOException) {
                lastException = ioe
                if (attempt < maxRetries) {
                    val jitter = Random.nextLong(0, (currentBackoff * 0.3).toLong().coerceAtLeast(50L))
                    delay(currentBackoff + jitter)
                    currentBackoff *= 2
                } else {
                    throw GeminiApiException.NetworkUnavailableException(
                        message = "Unable to reach Google AI service: ${ioe.message ?: "Network error"}",
                        cause = ioe,
                    )
                }
            }
        }
        throw lastException?.let {
            GeminiApiException.NetworkUnavailableException("Failed to execute Gemini API request after $maxRetries attempts.", it)
        } ?: GeminiApiException.ClientErrorException(500, "Unknown network execution error.")
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        const val FALLBACK_MODEL = "gemini-3.1-flash-lite"
        const val DEFAULT_EMBEDDING_MODEL = "text-embedding-004"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_RETRIES = 3
        const val INITIAL_BACKOFF_MS = 1000L
    }
}


