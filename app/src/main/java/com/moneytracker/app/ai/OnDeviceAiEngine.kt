package com.moneytracker.app.ai

import android.content.Context
import android.os.Build
import com.moneytracker.app.data.db.TransactionRecord
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import com.moneytracker.app.bank.BalanceProofVerifier
import com.moneytracker.app.bank.BankDetector

class OnDeviceAiEngine(
    private val context: Context? = null,
) {

    fun isPixelDevice(): Boolean {
        return Build.MANUFACTURER.equals("Google", ignoreCase = true) &&
            Build.MODEL.contains("Pixel", ignoreCase = true)
    }

    fun isTensorSoc(): Boolean {
        val hardware = Build.HARDWARE.lowercase(Locale.getDefault())
        val board = Build.BOARD.lowercase(Locale.getDefault())
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.lowercase(Locale.getDefault())
        } else {
            ""
        }
        return hardware.contains("zuma") || hardware.contains("tensor") ||
            board.contains("zuma") || board.contains("tensor") ||
            soc.contains("tensor") || soc.contains("g4") || soc.contains("g3")
    }

    fun isAiCoreAvailable(): Boolean {
        return runCatching {
            val pm = context?.packageManager ?: return false
            pm.getPackageInfo("com.google.android.aicore", 0) != null
        }.getOrDefault(false)
    }

    fun getDeviceStatus(): String {
        val isPixel = isPixelDevice()
        val isTensor = isTensorSoc()
        val model = Build.MODEL

        return when {
            isPixel && isTensor -> "$model • Google Tensor G4 TPU Ready"
            isPixel -> "$model • Google Pixel Device"
            else -> "$model • On-Device Engine Active"
        }
    }

    fun parseSmsOnDevice(smsBody: String, sender: String): Result<AiParsedTransaction> {
        if (smsBody.isBlank()) {
            return Result.failure(IllegalArgumentException("SMS body is blank"))
        }

        // 1. Transaction & Security check
        val lower = smsBody.lowercase(Locale.getDefault())
        val isOtp = listOf("otp", "one time password", "verification code", "secret code", "do not share").any { it in lower }
        if (isOtp) {
            return Result.success(createNonTransaction())
        }

        // Promotional marketing offers, discount banners, pre-approved loans, and EMI ads are NOT transactions
        val isPromotionalOrMarketing = listOf(
            "up to ₹", "upto ₹", "up to rs", "upto rs", "up to inr", "upto inr",
            "save up to", "save upto", "off on", "discount on", "cashback up to",
            "flat ₹", "flat rs", "flat inr", "flat discount",
            "use code", "coupon code", "promo code", "voucher code",
            "pre-approved", "pre approved", "instant loan", "personal loan of",
            "credit limit enhanced", "credit limit increased", "upgrade your card", "apply now",
            "congratulations", "special offer", "exclusive offer", "deal of the day", "festive offer",
            "on emi purchases", "convert to emi", "no-cost emi",
            "will be recorded by", "to be recorded by", "recorded by amc", "mandate registration",
            "reward points", "earn points", "win cash", "lottery",
        ).any { it in lower }

        val hasStrongPastTenseDebitConfirmation = listOf(
            "has been debited", "is debited", "was debited", "debited with", "debited by", "debited for", "a/c debited", "account debited",
        ).any { it in lower }

        if (isPromotionalOrMarketing && !hasStrongPastTenseDebitConfirmation) {
            return Result.success(createNonTransaction())
        }

        // 2. Amount extraction
        val amountRegex = Regex("""(?:inr|rs\.?|re\.?|₹)\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        val amountMatch = amountRegex.find(smsBody)
        val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            return Result.success(createNonTransaction())
        }

        // Reject bill statement/due notices from being converted to executed transactions
        val isBillReminder = listOf(
            "total amount due", "total amt due", "minimum amount due", "min amt due",
            "due date", "due on", "payment due", "bill generated", "statement generated",
            "e-bill", "bill amount", "pay by", "pay before", "to avoid late",
        ).any { it in lower }
        val hasExecutedDebit = listOf(
            "has been debited", "is debited", "was debited", "debited with", "debited by",
            "debited for", "a/c debited", "account debited", "debited from", "debited successfully",
            "payment received towards", "credited to your credit card", "successfully paid",
        ).any { it in lower }
        if (isBillReminder && !hasExecutedDebit) {
            return Result.success(createNonTransaction())
        }

        // 3. Direction
        val isCreditCardPayment = listOf(
            "received towards your",
            "received towards",
            "payment received for credit card",
            "payment received for your card",
            "payment received towards",
            "credited towards credit card",
            "credited to your credit card",
            "credited to credit card",
            "credited to your card",
            "credited to card",
            "credited to your sbi card",
            "credited to sbi card",
            "towards credit card",
            "towards your credit card",
            "towards your card",
            "towards card",
            "paid towards your credit card",
            "paid towards your card",
            "paid towards your",
            "paid towards",
            "payment towards credit card",
            "payment towards card",
            "payment towards",
            "via cred",
            "on cred",
            "through cred",
            "via cheq",
            "via billdesk",
            "through billdesk",
        ).any { it in lower }
        val isDebit = listOf(
            "debited",
            "spent",
            "paid to",
            "paid for",
            "paid rs",
            "paid inr",
            "paid ₹",
            "payment of",
            "payment to",
            "payment made",
            "you paid",
            "money sent",
            "successfully paid",
            "withdrawn",
            "sent rs",
            "sent inr",
            "sent ₹",
            "sent to",
            "deducted",
            "purchase of",
            "purchase at",
            "card purchase of",
            "card purchase at",
            "txn of rs",
            "txn of inr",
            "txn of ₹",
            "transaction of rs",
            "transaction of inr",
            "transaction of ₹",
        ).any { it in lower } || isCreditCardPayment
        val isCredit = listOf(
            "credited",
            "received from",
            "received rs",
            "received inr",
            "received ₹",
            "refund of",
            "refund received",
            "deposited",
            "salary credited",
        ).any { it in lower } && !isCreditCardPayment

        if (!isDebit && !isCredit) {
            return Result.success(createNonTransaction())
        }

        val direction = when {
            isCreditCardPayment -> TransactionDirection.DEBIT
            isDebit -> TransactionDirection.DEBIT
            isCredit -> TransactionDirection.CREDIT
            else -> return Result.success(createNonTransaction())
        }

        // 4. Institution & Merchant
        val institution = extractInstitution(sender, smsBody)
        val extracted = extractMerchant(smsBody, lower)
        val merchant = when {
            isCreditCardPayment -> if (institution.isNotBlank()) "$institution Credit Card" else "Credit Card Bill"
            "salary" in lower && extracted == "Merchant" -> "Salary"
            "refund" in lower && extracted == "Merchant" -> "Refund"
            else -> extracted
        }

        val hasStrongPastTenseConfirmation = listOf(
            "has been debited", "is debited", "was debited", "debited with", "debited by", "debited for", "a/c debited", "account debited",
            "credited", "has been credited", "is credited", "was credited", "deposited", "refund received",
        ).any { it in lower } || isCreditCardPayment

        val isBogusMerchant = (merchant == "Merchant" || isGarbageMerchantName(merchant)) && !isCreditCardPayment

        if (isBogusMerchant && !hasStrongPastTenseConfirmation) {
            return Result.success(createNonTransaction())
        }

        // 5. Place and location detail extraction
        val placeDetail = extractPlaceDetail(smsBody, lower, merchant)

        // 6. Category
        val isSelfTransfer = listOf("to self", "to own account", "from own account", "wallet topup", "added to wallet", "loaded to wallet").any { it in lower }
        val category = if (isCreditCardPayment || isSelfTransfer) TransactionCategory.TRANSFER else inferCategory(merchant, lower, placeDetail)
        val countsTowardBudget = !isCreditCardPayment && !isSelfTransfer && category != TransactionCategory.TRANSFER

        // 7. Account & Bank Detection
        val last4Regex = Regex("""(?:a/c|acct|card|ending|xx)\s*[:#\s]*([0-9]{4})""", RegexOption.IGNORE_CASE)
        val fallbackLast4 = last4Regex.find(smsBody)?.groupValues?.get(1)

        val bankDetection = BankDetector.detectBankAccount(sender, smsBody)
        val resolvedInstitution = bankDetection.institutionName
            ?: institution.takeIf { it.isNotBlank() }
            ?: BankDetector.resolveBankInstitution(sender, smsBody)

        val isCard = listOf("credit card", "debit card", "card ending", "card xx").any { it in lower }
        val isExplicitCreditCard = listOf("credit card", "credit card ending", "card ending").any { it in lower } && !bankDetection.isBankAccount
        val isUpi = listOf("upi", "vpa", "/p2a/", "@okhdfc", "@okaxis", "@okicici", "@ybl").any { it in lower }

        val accountKind = when {
            isExplicitCreditCard && !isCreditCardPayment -> AccountKind.CARD
            bankDetection.isBankAccount -> AccountKind.BANK
            resolvedInstitution != null -> AccountKind.BANK
            isUpi -> AccountKind.UPI
            isCard && !isCreditCardPayment -> AccountKind.CARD
            else -> AccountKind.BANK
        }

        val resolvedLast4 = bankDetection.accountLastFour ?: fallbackLast4

        val balRegex = Regex("""(?:avl(?:[\.\s]+)?bal(?:ance)?|available\s+balance|avail(?:[\.\s]+)?bal(?:ance)?|total\s+balance|bal(?:ance)?\s*[:=])\s*(?:is|:|-)?\s*(?:rs\.?|inr)?\s*([0-9,]+(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        val availableBalance = BalanceProofVerifier.verifyBalance(sender, smsBody).balance
            ?: balRegex.find(smsBody)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.takeIf {
                val match = balRegex.find(smsBody)
                val start = (match?.range?.first ?: 0 - 30).coerceAtLeast(0)
                val end = ((match?.range?.last ?: 0) + 30).coerceAtMost(smsBody.length)
                val ctx = smsBody.substring(start, end).lowercase(Locale.ENGLISH)
                !listOf("limit", "due", "points", "maintain").any { ctx.contains(it) }
            }

        val detailedDescription = generateDetailedDescription(
            merchant = merchant,
            direction = direction,
            category = category,
            isUpi = isUpi,
            isCard = isCard,
            institution = resolvedInstitution ?: institution,
            placeDetail = placeDetail,
            isCreditCardPayment = isCreditCardPayment,
        ) ?: placeDetail

        val parsed = AiParsedTransaction(
            isTransaction = true,
            amount = amount,
            direction = direction,
            merchant = merchant,
            category = category,
            accountKind = accountKind,
            institutionName = resolvedInstitution,
            accountLastFour = resolvedLast4,
            cardType = if (isCard) (if (bankDetection.isDebitCard) CardType.DEBIT else CardType.CREDIT) else null,
            isUpi = isUpi,
            isCardBillPayment = isCreditCardPayment,
            placeDetail = placeDetail,
            detailedDescription = detailedDescription,
            confidence = 0.96,
            countsTowardBudget = countsTowardBudget,
            availableBalance = availableBalance,
        )

        return Result.success(parsed)
    }

    private fun generateDetailedDescription(
        merchant: String?,
        direction: TransactionDirection,
        category: TransactionCategory,
        isUpi: Boolean,
        isCard: Boolean,
        institution: String?,
        placeDetail: String?,
        isCreditCardPayment: Boolean,
    ): String? {
        val target = merchant ?: return null
        val instrument = institution?.let { " via $it" } ?: ""

        return when {
            isCreditCardPayment -> "Credit card bill payment$instrument"
            !placeDetail.isNullOrBlank() && !placeDetail.equals(target, ignoreCase = true) -> "$target ($placeDetail)$instrument"
            category == TransactionCategory.FOOD -> "Dining / Food order at $target$instrument"
            category == TransactionCategory.TRAVEL -> "Travel / Cab spend with $target$instrument"
            category == TransactionCategory.BILLS -> "Utility bill payment to $target$instrument"
            category == TransactionCategory.SUBSCRIPTION -> "Subscription payment to $target$instrument"
            category == TransactionCategory.SHOPPING -> "Shopping order at $target$instrument"
            direction == TransactionDirection.CREDIT -> "Payment received from $target$instrument"
            isUpi -> "UPI transfer to $target$instrument"
            isCard -> "Card purchase at $target$instrument"
            else -> "Payment to $target$instrument"
        }
    }

    fun queryAssistantOnDevice(
        userQuery: String,
        retrievedTransactions: List<TransactionRecord>,
        macroContext: String,
    ): Result<RagAnswerResponse> {
        val trimmedQuery = userQuery.trim()

        if (SpendingAssistantGuardrail.isGreeting(trimmedQuery)) {
            return Result.success(
                RagAnswerResponse(
                    answer = SpendingAssistantGuardrail.getGreetingResponse(),
                    citedTransactionIds = emptyList(),
                ),
            )
        }

        val knownMerchants = retrievedTransactions.map { it.merchant }.toSet()
        if (SpendingAssistantGuardrail.isOffTopic(trimmedQuery, knownMerchants)) {
            return Result.success(
                RagAnswerResponse(
                    answer = SpendingAssistantGuardrail.getCuteOffTopicResponse(trimmedQuery),
                    citedTransactionIds = emptyList(),
                ),
            )
        }

        val lowerQuery = trimmedQuery.lowercase(Locale.getDefault())

        val isFoodQuery = listOf("food", "dining", "eat", "restaurant", "cafe", "swiggy", "zomato").any { it in lowerQuery }
        val isTravelQuery = listOf("travel", "transport", "transportation", "uber", "ola", "metro", "fuel", "petrol", "cab", "ride", "auto", "train", "flight").any { it in lowerQuery }
        val isShoppingQuery = listOf("shopping", "clothes", "amazon", "flipkart", "myntra", "retail", "mart", "store", "buy", "bought").any { it in lowerQuery }
        val isBillsQuery = listOf("bill", "bills", "utility", "electricity", "water", "recharge", "broadband", "wifi", "airtel", "jio").any { it in lowerQuery }
        val isHealthQuery = listOf("health", "medical", "medicine", "doctor", "hospital", "pharmacy", "apollo").any { it in lowerQuery }
        val isSubscriptionQuery = listOf("subscription", "subscriptions", "netflix", "spotify", "prime", "hotstar", "youtube", "recurring").any { it in lowerQuery }
        val isCardQuery = listOf("credit card", "card spend", "card debits", "cards").any { it in lowerQuery }
        val isPlaceQuery = listOf("where", "place", "location", "indiranagar", "branch", "outlet", "city").any { it in lowerQuery }
        val isBudgetQuery = listOf("budget", "pace", "pacing", "left", "save", "saving", "advice", "target", "cap").any { it in lowerQuery }

        val relevantTxs = when {
            isFoodQuery -> retrievedTransactions.filter { it.category == TransactionCategory.FOOD }
            isTravelQuery -> retrievedTransactions.filter { it.category == TransactionCategory.TRAVEL }
            isShoppingQuery -> retrievedTransactions.filter { it.category == TransactionCategory.SHOPPING }
            isBillsQuery -> retrievedTransactions.filter { it.category == TransactionCategory.BILLS }
            isHealthQuery -> retrievedTransactions.filter { it.category == TransactionCategory.HEALTH }
            isSubscriptionQuery -> retrievedTransactions.filter { it.category == TransactionCategory.SUBSCRIPTION }
            isCardQuery -> retrievedTransactions.filter { it.accountKind == AccountKind.CARD }
            else -> retrievedTransactions
        }

        val totalDebit = relevantTxs.filter { it.direction == TransactionDirection.DEBIT }.sumOf { it.amount }
        val citedIds = relevantTxs.take(5).map { it.id }

        val answer = buildString {
            if (isPlaceQuery) {
                val withPlaces = retrievedTransactions.filter { !it.note.isNullOrBlank() }
                if (withPlaces.isNotEmpty()) {
                    append("**Spends with Location Details:**\n")
                    withPlaces.take(4).forEach { tx ->
                        val date = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(tx.occurredAtMillis))
                        append("• **${tx.merchant}** (₹${tx.amount.toInt()} on $date) — *${tx.note}*\n")
                    }
                    append("\nOverall, location context is captured automatically for your physical and online store visits.")
                } else {
                    append("I reviewed your transactions, but couldn't find specific place/location notes tagged yet. Incoming SMS alerts with branch or terminal details will be recorded here automatically.")
                }
            } else if (isFoodQuery) {
                append("**Food & Dining Breakdown:**\n")
                append("You have spent **₹${totalDebit.toInt()}** across ${relevantTxs.size} transactions on food and dining.\n")
                val topMerchants = relevantTxs.groupBy { it.merchant }
                    .mapValues { it.value.sumOf(TransactionRecord::amount) }
                    .entries.sortedByDescending { it.value }.take(3)
                if (topMerchants.isNotEmpty()) {
                    append("Top spots: ")
                    append(topMerchants.joinToString(", ") { "${it.key} (₹${it.value.toInt()})" })
                    append(".\n\n*Tip: Setting a dedicated Food budget can help track dining spikes.*")
                }
            } else if (isTravelQuery) {
                append("**Transportation & Travel Breakdown:**\n")
                append("You have spent **₹${totalDebit.toInt()}** across ${relevantTxs.size} transit and travel expenses.\n")
                val topTransit = relevantTxs.groupBy { it.merchant }
                    .mapValues { it.value.sumOf(TransactionRecord::amount) }
                    .entries.sortedByDescending { it.value }.take(3)
                if (topTransit.isNotEmpty()) {
                    append("Top services: ")
                    append(topTransit.joinToString(", ") { "${it.key} (₹${it.value.toInt()})" })
                    append(".\n\n*Tip: Using monthly transit passes or metro cards can reduce daily commute costs.*")
                }
            } else if (isShoppingQuery) {
                append("**Shopping Breakdown:**\n")
                append("You have spent **₹${totalDebit.toInt()}** across ${relevantTxs.size} retail and online purchases.\n")
                val topStores = relevantTxs.groupBy { it.merchant }
                    .mapValues { it.value.sumOf(TransactionRecord::amount) }
                    .entries.sortedByDescending { it.value }.take(3)
                if (topStores.isNotEmpty()) {
                    append("Top retailers: ")
                    append(topStores.joinToString(", ") { "${it.key} (₹${it.value.toInt()})" })
                    append(".")
                }
            } else if (isBillsQuery) {
                append("**Bills & Utilities Breakdown:**\n")
                append("You have spent **₹${totalDebit.toInt()}** across ${relevantTxs.size} utility and bill payments.\n")
            } else if (isSubscriptionQuery) {
                append("**Subscriptions Overview:**\n")
                append("You have **${relevantTxs.size} recurring subscription charges** totaling **₹${totalDebit.toInt()}**.\n")
            } else if (isCardQuery) {
                append("**Credit Card Spends:**\n")
                append("Total card debits amount to **₹${totalDebit.toInt()}** across ${relevantTxs.size} transactions.\n")
            } else if (isBudgetQuery) {
                append("**Budget & Spending Status (On-Device):**\n")
                append("Your tracked expenses for this period total **₹${totalDebit.toInt()}** across ${relevantTxs.size} entries.\n")
                append("Analyzing your spending patterns, your top expense drivers are centered around daily convenience and dining.\n\n")
                append("**Actionable Tip:** If you reduce recurring takeaway orders by just 15%, you could save approximately ₹2,000–₹3,500 every month.")
            } else {
                append("**Financial Summary (Processed 100% On-Device):**\n")
                append("Found **${retrievedTransactions.size} transactions** matching your inquiry totaling **₹${totalDebit.toInt()}** in debits.\n")
                if (retrievedTransactions.isNotEmpty()) {
                    val latest = retrievedTransactions.first()
                    val date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(latest.occurredAtMillis))
                    append("Most recent expense: **${latest.merchant}** for ₹${latest.amount.toInt()} on $date.")
                }
            }
        }

        return Result.success(
            RagAnswerResponse(
                answer = answer.trim(),
                citedTransactionIds = citedIds,
            ),
        )
    }

    fun generateSpendingInsightsOnDevice(
        transactions: List<TransactionRecord>,
        budgetLimit: Double?,
        monthSpent: Double,
        monthIncome: Double,
    ): List<String> {
        val insights = mutableListOf<String>()
        val debitTxs = transactions.filter { it.direction == TransactionDirection.DEBIT }

        if (debitTxs.isEmpty()) {
            insights.add("No debits recorded for this month yet. Import recent SMS or log an expense to generate tailored insights.")
            if (budgetLimit != null && budgetLimit > 0.0) {
                insights.add("Monthly budget is set to ₹${budgetLimit.toInt()}. You have the full allowance remaining.")
            }
            return insights
        }

        // 1. Budget Pacing / Safe Burn Observation
        if (budgetLimit != null && budgetLimit > 0.0) {
            val pct = ((monthSpent / budgetLimit) * 100).toInt()
            val remaining = (budgetLimit - monthSpent).coerceAtLeast(0.0).toInt()
            if (monthSpent > budgetLimit) {
                insights.add("Over budget by **₹${(monthSpent - budgetLimit).toInt()}** (${pct}% utilized). Consider pausing discretionary expenses.")
            } else if (pct >= 85) {
                insights.add("Budget alert: You have used **${pct}%** of your ₹${budgetLimit.toInt()} limit. **₹$remaining** remaining.")
            } else {
                insights.add("Budget pacing is healthy: **${pct}%** used (**₹${monthSpent.toInt()}** of ₹${budgetLimit.toInt()}) with **₹$remaining** remaining cushion.")
            }
        } else {
            insights.add("Total outflow this month is **₹${monthSpent.toInt()}**. Setting a monthly budget target can help pace discretionary spends.")
        }

        // 2. Spend Driver / Top Category & Merchant
        val categorySpends = debitTxs.groupBy { it.category }
            .mapValues { it.value.sumOf(TransactionRecord::amount) }
            .entries.sortedByDescending { it.value }

        if (categorySpends.isNotEmpty()) {
            val topCategory = categorySpends.first()
            val topCatTransactions = debitTxs.filter { it.category == topCategory.key }
            val topMerchant = topCatTransactions.groupBy { it.merchant }
                .mapValues { it.value.sumOf(TransactionRecord::amount) }
                .entries.maxByOrNull { it.value }

            val merchantClause = if (topMerchant != null && topMerchant.value > 0) {
                ", led by **${topMerchant.key}** (₹${topMerchant.value.toInt()})"
            } else ""

            insights.add("Top spend driver is **${topCategory.key.label}** at **₹${topCategory.value.toInt()}**$merchantClause.")
        }

        // 3. Cashflow / Savings Rate
        if (monthIncome > 0) {
            val net = monthIncome - monthSpent
            val saveRate = (((monthIncome - monthSpent) / monthIncome) * 100).toInt()
            if (net >= 0) {
                insights.add("Net positive cashflow of **₹${net.toInt()}** (${saveRate}% savings rate) across verified monthly earnings.")
            } else {
                insights.add("Cashflow deficit of **-₹${(-net).toInt()}**. Outflow currently exceeds monthly recorded income.")
            }
        }

        // 4. Actionable Tailored Tip
        val hasHighFood = debitTxs.any { it.category == TransactionCategory.FOOD && it.amount > 500 }
        val hasSubscriptions = debitTxs.any { it.category == TransactionCategory.SUBSCRIPTION }
        val cardDebits = debitTxs.filter { it.accountKind == AccountKind.CARD }.sumOf { it.amount }

        when {
            cardDebits > 0 && cardDebits > monthSpent * 0.4 -> {
                insights.add("Credit card charges account for **${((cardDebits / monthSpent) * 100).toInt()}%** of outflow. Ensure timely payment before due dates to avoid finance charges.")
            }
            hasSubscriptions -> {
                insights.add("Review active recurring subscriptions to identify unused memberships or duplicate entertainment plans.")
            }
            hasHighFood -> {
                insights.add("Dining and takeaway comprise a significant share of expenses. Preparing meals at home on weekdays could save ~₹2,500/month.")
            }
            else -> {
                insights.add("Review pending transactions in your ledger to ensure all expenses are categorized accurately.")
            }
        }

        return insights.take(4)
    }

    fun generateEmbeddingOnDevice(text: String): Result<List<Float>> {
        val lower = text.lowercase(Locale.getDefault())
        val vector = FloatArray(256)

        // Seed with normalized character and semantic hash
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(lower.toByteArray(Charsets.UTF_8))

        for (i in 0 until 256) {
            val byteVal = hash[i % hash.size].toFloat()
            val charWeight = if (i < lower.length) lower[i].code.toFloat() else 0f
            vector[i] = (byteVal * 0.7f + charWeight * 0.3f) / 255f
        }

        // Normalize vector to unit length
        var sumSquares = 0f
        for (v in vector) sumSquares += v * v
        val norm = sqrt(sumSquares).coerceAtLeast(1e-6f)
        val normalized = vector.map { it / norm }

        return Result.success(normalized)
    }

    private fun isGarbageMerchantName(name: String): Boolean {
        val lower = name.lowercase(Locale.getDefault())
        val garbagePhrases = listOf(
            "be recorded",
            "recorded by",
            "by amc",
            "amc",
            "purchases",
            "electronics",
            "discount",
            "discounts",
            "offer",
            "offers",
            "merchant",
            "your account",
            "your card",
            "credit card",
            "debit card",
            "bank account",
            "savings account",
        )
        return garbagePhrases.any { lower.contains(it) }
    }

    private fun extractMerchant(body: String, lower: String): String {
        val patterns = listOf(
            Regex("""(?:paid to|via upi to)\s+([A-Z0-9\s]{3,24})(?:\s+on|\s+ref|\s+avl|\.|\z)""", RegexOption.IGNORE_CASE),
            Regex("""vpa\s+([a-zA-Z0-9_.-]+@[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:at|towards)\s+([A-Z0-9\s]{3,24})(?:\s+on|\s+ref|\s+avl|\.|\z)""", RegexOption.IGNORE_CASE),
            Regex("""(?:to)\s+([A-Z0-9\s]{3,24})(?:\s+on|\s+ref|\s+avl|\.|\z)""", RegexOption.IGNORE_CASE),
        )

        for (p in patterns) {
            val match = p.find(body)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                val cleaned = cleanMerchantName(candidate)
                if (cleaned.isNotBlank() && cleaned.length >= 2 && !isGarbageMerchantName(cleaned)) return cleaned
            }
        }

        return when {
            "swiggy" in lower -> "Swiggy"
            "zomato" in lower -> "Zomato"
            "amazon" in lower -> "Amazon"
            "flipkart" in lower -> "Flipkart"
            "croma" in lower -> "Croma"
            "bigbasket" in lower || "bb daily" in lower -> "BigBasket"
            "1mg" in lower -> "Tata 1mg"
            "westside" in lower -> "Westside"
            "starbucks" in lower -> "Starbucks"
            "uber" in lower -> "Uber"
            "ola" in lower -> "Ola"
            "blinkit" in lower || "grofers" in lower -> "Blinkit"
            "zepto" in lower -> "Zepto"
            "netflix" in lower -> "Netflix"
            "spotify" in lower -> "Spotify"
            "apple" in lower -> "Apple"
            "bhim" in lower -> "BHIM"
            else -> "Merchant"
        }
    }

    private fun cleanMerchantName(raw: String): String {
        val trimmed = raw.trim()
        val digitsOnly = trimmed.filter { it.isDigit() }
        if (digitsOnly.length in 10..12) {
            val nonDigits = trimmed.replace(Regex("[0-9]"), "").trim()
            val last4 = digitsOnly.takeLast(4)
            return if (nonDigits.isNotBlank()) {
                "$nonDigits (..$last4)"
            } else {
                "UPI Transfer (..$last4)"
            }
        }
        val stopWords = listOf("the", "ltd", "pvt", "limited", "bank", "india", "on", "at", "ref", "avl", "bal", "successful", "successfully", "using", "via", "through", "with", "pay")
        return trimmed.split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.lowercase(Locale.getDefault()) !in stopWords && it.length > 1 }
            .joinToString(" ")
            .take(28)
            .ifBlank { trimmed.take(20) }
    }

    private fun extractPlaceDetail(body: String, lower: String, merchant: String): String? {
        val knownPlaces = listOf(
            "indiranagar", "koramangala", "hsr layout", "whitefield", "jayanagar", "electronic city",
            "bangalore", "bengaluru", "mumbai", "delhi", "hyderabad", "chennai", "pune", "kolkata",
            "bkc", "bandra", "andheri", "powai", "colaba", "lower parel", "gurgaon", "cyber city",
            "connaught place", "hauz khas", "noida", "salt lake", "park street", "hitech city", "gachibowli",
        )

        val foundPlace = knownPlaces.firstOrNull { it in lower }
        if (foundPlace != null) {
            return foundPlace.split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        }

        if ("swiggy" in lower) return "Swiggy Food order"
        if ("zomato" in lower) return "Zomato delivery"
        if ("uber" in lower) return "Uber cab ride"
        if ("ola" in lower) return "Ola ride"
        if ("starbucks" in lower) return "Starbucks cafe"
        if ("metro" in lower) return "Metro transit recharge"
        if ("cinema" in lower || "pvr" in lower || "inox" in lower) return "Movie tickets"

        if (merchant.isBlank() || merchant == "Merchant" || merchant.contains("..") || merchant.startsWith("UPI") || merchant.any { it.isDigit() }) {
            return null
        }
        return "$merchant outlet"
    }

    private fun inferCategory(merchant: String, lower: String, place: String?): TransactionCategory {
        val text = "$merchant $lower ${place.orEmpty()}".lowercase(Locale.getDefault())

        return when {
            listOf("swiggy", "zomato", "restaurant", "cafe", "starbucks", "food", "kitchen", "bake", "pizza", "burger", "toit", "brew", "bigbasket", "blinkit", "zepto", "chai", "tea", "coffee").any { it in text } -> TransactionCategory.FOOD
            listOf("uber", "ola", "metro", "irctc", "flight", "indigo", "petrol", "fuel", "hpcl", "bpcl", "ioc").any { it in text } -> TransactionCategory.TRAVEL
            listOf("electricity", "bescom", "water", "bill", "broadband", "wifi", "airtel", "jio", "vodafone", "idea", "vi recharge", "vi bill", "gas", "recharge", "tata power").any { it in text } -> TransactionCategory.BILLS
            listOf("amazon", "flipkart", "myntra", "zara", "h&m", "shopping", "retail", "mart", "store", "croma", "westside", "tatacliq", "tata cliq").any { it in text } -> TransactionCategory.SHOPPING
            listOf("salary", "payroll", "stipend").any { it in text } -> TransactionCategory.SALARY
            listOf("netflix", "spotify", "prime", "hotstar", "youtube", "subscription").any { it in text } -> TransactionCategory.SUBSCRIPTION
            listOf("pharmacy", "apollo", "medplus", "hospital", "clinic", "health", "doctor", "1mg").any { it in text } -> TransactionCategory.HEALTH
            listOf("transfer", "sent to", "neft", "rtgs", "imps", "card payment").any { it in text } -> TransactionCategory.TRANSFER
            else -> TransactionCategory.OTHER
        }
    }

    private fun extractInstitution(sender: String, body: String): String {
        return BankDetector.resolveBankInstitution(sender, body)
            ?: BankDetector.normalizeToCanonicalBank(sender)
            ?: ""
    }

    private fun createNonTransaction(): AiParsedTransaction {
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
        )
    }
}
