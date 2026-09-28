package com.moneytracker.app.ai

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Locale

sealed class NanoAvailabilityStatus {
    object Ready : NanoAvailabilityStatus()
    object DownloadingModel : NanoAvailabilityStatus()
    object UnsupportedDevice : NanoAvailabilityStatus()
    data class Error(val message: String) : NanoAvailabilityStatus()
}

class AiCoreNanoManager(
    private val context: Context? = null,
    initialStatus: NanoAvailabilityStatus? = null,
    private val customInferenceRunner: (suspend (String) -> String)? = null,
) {

    private val _availabilityStatus = MutableStateFlow(
        initialStatus ?: evaluateAvailability(),
    )
    val availabilityStatus: StateFlow<NanoAvailabilityStatus> = _availabilityStatus.asStateFlow()

    private var generativeModelInstance: Any? = null

    companion object {
        const val AICORE_PACKAGE = "com.google.android.aicore"
        const val INFERENCE_TIMEOUT_MS = 2500L

        const val FINANCIAL_PROMPT_TEMPLATE = """Extract transaction details into raw JSON ONLY.
Keys:
- "amount": Double
- "direction": "DEBIT" or "CREDIT"
- "merchant": String (Clean brand name, e.g. "Swiggy", "HDFC Bank", "Uber")
- "accountLast4": String or null
- "availableBalance": Double or null
- "category": String ("FOOD", "SHOPPING", "BILLS", "TRANSPORT", "INVESTMENT", "ENTERTAINMENT", "OTHER")
- "isTransaction": Boolean (false for OTPs, ads, fraud alerts)
No markdown wrappers, no backticks, no explanatory prose.

Sender: %s
Message: %s"""
    }

    fun isAvailable(): Boolean {
        return _availabilityStatus.value is NanoAvailabilityStatus.Ready
    }

    fun setStatusForTesting(status: NanoAvailabilityStatus) {
        _availabilityStatus.value = status
    }

    private fun evaluateAvailability(): NanoAvailabilityStatus {
        if (customInferenceRunner != null) {
            return NanoAvailabilityStatus.Ready
        }
        val ctx = context ?: return NanoAvailabilityStatus.UnsupportedDevice
        return runCatching {
            val pm = ctx.packageManager
            val isPackagePresent = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(AICORE_PACKAGE, PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getPackageInfo(AICORE_PACKAGE, 0)
                }
            }.getOrNull() != null

            if (!isPackagePresent) {
                return@runCatching NanoAvailabilityStatus.UnsupportedDevice
            }

            // On devices with AICore package present, verify GenerativeModel class accessibility
            try {
                Class.forName("com.google.ai.edge.aicore.GenerativeModel")
                NanoAvailabilityStatus.Ready
            } catch (e: Throwable) {
                NanoAvailabilityStatus.Error("AICore binding unavailable: ${e.message}")
            }
        }.getOrElse { e ->
            NanoAvailabilityStatus.Error(e.message ?: "Failed to check AICore availability")
        }
    }

    suspend fun extractFinancialEntity(
        rawText: String,
        sender: String,
    ): Result<AiParsedTransaction> = withContext(Dispatchers.Default) {
        if (rawText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Message body is blank"))
        }

        if (!isAvailable()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini Nano / AICore is not ready (Status: ${_availabilityStatus.value})"),
            )
        }

        val prompt = String.format(Locale.ROOT, FINANCIAL_PROMPT_TEMPLATE, sender, rawText)

        val rawInferenceResult = runCatching {
            withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                executeInference(prompt)
            }
        }.getOrElse { err ->
            return@withContext Result.failure(err)
        } ?: return@withContext Result.failure(
            java.util.concurrent.TimeoutException("Gemini Nano inference timed out after ${INFERENCE_TIMEOUT_MS}ms"),
        )

        parseFinancialJson(rawInferenceResult, rawText, sender)
    }

    private suspend fun executeInference(prompt: String): String {
        customInferenceRunner?.let { runner ->
            return runner(prompt)
        }

        // Production invocation using com.google.ai.edge.aicore.GenerativeModel
        return withContext(Dispatchers.IO) {
            try {
                val model = getOrCreateGenerativeModel()
                val generateMethod = model.javaClass.getMethod("generateContent", String::class.java)
                val response = generateMethod.invoke(model, prompt)
                    ?: throw IllegalStateException("Received null response from AICore GenerativeModel")

                val textProperty = response.javaClass.getMethod("getText")
                val text = textProperty.invoke(response) as? String
                    ?: throw IllegalStateException("Empty text content generated by Gemini Nano")

                text
            } catch (t: Throwable) {
                val cause = t.cause ?: t
                throw cause
            }
        }
    }

    @Synchronized
    private fun getOrCreateGenerativeModel(): Any {
        generativeModelInstance?.let { return it }
        val modelClass = Class.forName("com.google.ai.edge.aicore.GenerativeModel")
        val instance = modelClass.getDeclaredConstructor().newInstance()
        generativeModelInstance = instance
        return instance
    }

    fun parseFinancialJson(
        rawOutput: String,
        rawText: String,
        sender: String,
    ): Result<AiParsedTransaction> {
        val sanitizedJson = sanitizeJsonOutput(rawOutput)
            ?: return Result.failure(IllegalArgumentException("Could not extract valid JSON from Gemini Nano response: $rawOutput"))

        return runCatching {
            val json = JSONObject(sanitizedJson)

            val isTransaction = json.optBoolean("isTransaction", true)
            if (!isTransaction) {
                return@runCatching AiParsedTransaction(
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

            val amount = if (json.has("amount") && !json.isNull("amount")) {
                json.optDouble("amount")
            } else null

            val directionStr = json.optString("direction", "").trim().uppercase(Locale.ROOT)
            val direction = when {
                directionStr.contains("DEBIT") -> TransactionDirection.DEBIT
                directionStr.contains("CREDIT") -> TransactionDirection.CREDIT
                else -> null
            }

            val merchantRaw = json.optString("merchant", "").trim()
            val merchant = merchantRaw.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

            val rawCategory = json.optString("category", "").trim().uppercase(Locale.ROOT)
            val category = mapCategory(rawCategory)

            val accountLast4 = json.optString("accountLast4", "")
                .filter { it.isDigit() }
                .takeLast(4)
                .takeIf { it.length == 4 }

            val availableBalance = if (json.has("availableBalance") && !json.isNull("availableBalance")) {
                val bal = json.optDouble("availableBalance")
                if (!bal.isNaN()) bal else null
            } else null

            val lowerRaw = rawText.lowercase(Locale.ROOT)
            val isUpi = listOf("upi", "vpa", "/p2a/", "@").any { it in lowerRaw } || sender.contains("upi", ignoreCase = true)
            val isCreditCard = listOf("credit card", "card ending", "card xx").any { it in lowerRaw }
            val isCardBillPayment = listOf("payment received towards", "credit card bill", "credited to your credit card").any { it in lowerRaw } ||
                (category == TransactionCategory.TRANSFER && isCreditCard)

            val accountKind = when {
                isCreditCard && !isCardBillPayment -> AccountKind.CARD
                isUpi -> AccountKind.UPI
                else -> AccountKind.BANK
            }

            val cardType = if (isCreditCard) CardType.CREDIT else null

            AiParsedTransaction(
                isTransaction = true,
                amount = amount,
                direction = direction,
                merchant = merchant,
                category = category,
                accountKind = accountKind,
                institutionName = null,
                accountLastFour = accountLast4,
                cardType = cardType,
                isUpi = isUpi,
                isCardBillPayment = isCardBillPayment,
                placeDetail = null,
                detailedDescription = merchant?.let { "$it - On-Device AI Verified" },
                confidence = 0.98,
                countsTowardBudget = !isCardBillPayment && category != TransactionCategory.TRANSFER,
                availableBalance = availableBalance,
            )
        }
    }

    private fun sanitizeJsonOutput(raw: String): String? {
        var text = raw.trim()

        // Strip markdown ```json ... ``` or ``` ... ```
        if (text.startsWith("```")) {
            text = text.removePrefix("```json").removePrefix("```").trim()
            if (text.endsWith("```")) {
                text = text.removeSuffix("```").trim()
            }
        }

        // Find outermost JSON object
        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return text.substring(startIndex, endIndex + 1).trim()
        }

        return null
    }

    private fun mapCategory(cat: String): TransactionCategory {
        return when {
            cat.contains("FOOD") || cat.contains("DINING") -> TransactionCategory.FOOD
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
}
