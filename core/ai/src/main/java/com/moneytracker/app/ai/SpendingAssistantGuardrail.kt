package com.moneytracker.app.ai

import java.util.Locale
import kotlin.math.abs

/**
 * Guardrail evaluator for the Spending Assistant (RAG).
 *
 * Ensures the assistant remains strictly within personal finance, spending habits,
 * budget pacing, transactions, and Money Tracker topics. Any random or off-topic
 * query immediately returns a delightful, cute response without unnecessary model calls.
 */
object SpendingAssistantGuardrail {

    private val GREETING_TOKENS = setOf(
        "hi", "hello", "hey", "hola", "namaste", "good morning", "good evening",
        "good afternoon", "sup", "yo", "howdy", "hiya",
    )

    private val CUTE_OFF_TOPIC_RESPONSES = listOf(
        "Beep boop! 🪙✨ I'm just a little financial piggy bank assistant! 🐷 I only know about your coins, rupees, budgets, and spending habits. Ask me about your expenses, coffee runs, or savings goals! 🍰💳",
        "Aww, that's a bit outside my coin jar! 🍯🪙 I only have eyes for your finances, budgets, and savings! Ask me how much you spent this week or how your monthly budget is pacing! 📊✨",
        "Whoopsie! 🐾 My tiny calculator brain only understands money, budgets, and expenses! 🧮💖 I can't help with that, but I'd love to tell you where your money went or how to save for a rainy day! ☔🪙",
        "Pardon my piggy manners! 🐷✨ I'm trained exclusively on your expenses, transactions, budgets, and wallets! Ask me about your food spending, shopping habits, or account balances! 🥑💳",
        "Hold your rupees! 🪙🌟 While I'd love to chat about everything, my superpower is analyzing your personal finances and spending! Ask me how much you spent on dining or how close you are to your budget limit! 📈✨",
    )

    private const val GREETING_RESPONSE =
        "Hello there! 🪙✨ I'm your Spending Assistant! Ask me anything about your expenses, budget pacing, or where your money went this month! 💳📊"

    // Primary financial terms, verbs, and ledger units
    private val FINANCIAL_KEYWORDS = setOf(
        "money", "spend", "spent", "spending", "spends", "cost", "costs", "costed",
        "costing", "expense", "expenses", "transaction", "transactions", "budget",
        "budgets", "budgeting", "save", "saving", "savings", "saved", "income",
        "inflow", "outflow", "cashflow", "balance", "balances", "account", "accounts",
        "bank", "banks", "card", "cards", "credit", "debit", "cash", "atm", "wallet",
        "wallets", "rupee", "rupees", "inr", "rs", "₹", "pay", "pays", "paid",
        "paying", "payment", "payments", "bill", "bills", "billing", "subscription",
        "subscriptions", "subscribe", "subscribed", "transfer", "transfers", "transferred",
        "salary", "salaries", "refund", "refunds", "recharge", "recharges", "utility",
        "utilities", "emi", "emis", "loan", "loans", "tax", "taxes", "statement",
        "statements", "receipt", "receipts", "ledger", "afford", "affordable",
        "finance", "finances", "financial", "pace", "pacing", "overspend",
        "overspending", "invest", "investment", "investments", "deposit", "deposits",
        "deposited", "withdraw", "withdrawal", "withdrawals", "withdrew", "due",
        "dues", "interest", "fee", "fees", "charge", "charges", "charged", "purchase",
        "purchases", "purchased", "bought", "buy", "buying", "shopping", "shop",
        "order", "orders", "ordered", "paisa", "rupay", "visa", "mastercard",
    )

    // Common expense categories, merchant brands, and typical billing services
    private val CATEGORY_AND_ITEM_KEYWORDS = setOf(
        // Food & Dining
        "food", "dining", "restaurant", "restaurants", "cafe", "cafes", "coffee",
        "swiggy", "zomato", "blinkit", "zepto", "instamart", "groceries", "grocery",
        "starbucks", "mcdonald", "domino", "subway", "kfc",
        // Travel & Transit
        "travel", "trip", "transit", "transport", "transportation", "uber", "ola",
        "rapido", "cab", "cabs", "taxi", "taxis", "metro", "bus", "train", "flight",
        "flights", "airline", "airlines", "fuel", "petrol", "diesel", "toll", "parking",
        "irctc", "makemytrip", "indigo",
        // Shopping & Retail
        "amazon", "flipkart", "myntra", "ajio", "zara", "retail", "mart",
        // Utilities & Bills
        "electricity", "wifi", "broadband", "internet", "airtel", "jio",
        "vi", "vodafone", "cylinder", "lpg", "rent", "maintenance", "dth",
        // Health & Medical
        "hospital", "hospitals", "clinic", "pharmacy", "apollo", "pharmeasy", "1mg",
        // Subscriptions & Entertainment
        "netflix", "spotify", "prime", "hotstar", "youtube", "bookmyshow",
    )

    // Dedicated financial query phrases & multi-word inquiry patterns
    private val FINANCIAL_INQUIRY_PATTERNS = listOf(
        "how much did", "how much was", "how much have", "how much do", "how much am",
        "how much spent", "how much i spent", "how much is my", "how much are my",
        "how many transactions", "how many expenses",
        "where did i spend", "where did my money", "where was my money", "where is my money",
        "what did i spend", "what did i buy", "what were my expenses", "what are my expenses",
        "what are my debits", "what are my spends",
        "safe daily spend", "daily spend", "cut down on", "tips to save", "saving tips",
        "spending breakdown", "expense breakdown", "budget pacing", "budget status",
        "budget limit", "am i on track", "can i afford", "how am i pacing",
        "how is my budget", "am i overspending", "top spend", "spend driver",
        "biggest expense", "highest expense", "lowest expense", "most expensive",
        "recent transactions", "recent expenses", "recent purchases", "recent spends",
        "money spent", "spending trend", "monthly summary", "monthly spend",
        "spent today", "spent yesterday", "spent this week", "spent this month",
        "expenses today", "expenses this month", "transactions this month",
    )

    /**
     * Checks if the query is a simple greeting.
     */
    fun isGreeting(query: String): Boolean {
        val cleaned = query.trim().lowercase(Locale.getDefault())
            .replace(Regex("""[^\w\s]"""), "")
            .trim()
        return cleaned in GREETING_TOKENS
    }

    /**
     * Returns a charming greeting response.
     */
    fun getGreetingResponse(): String = GREETING_RESPONSE

    /**
     * Evaluates whether a query is off-topic.
     *
     * @param query The user's input query.
     * @param knownMerchants Optional set of user's known merchant names from the ledger.
     * @param knownAccounts Optional set of user's account names from the ledger.
     * @return true if the query is unrelated to personal finances, false otherwise.
     */
    fun isOffTopic(
        query: String,
        knownMerchants: Set<String> = emptySet(),
        knownAccounts: Set<String> = emptySet(),
    ): Boolean {
        val normalized = query.lowercase(Locale.getDefault()).trim()
        if (normalized.isBlank()) return true

        // 1. Direct greeting check
        if (isGreeting(normalized)) return false

        // 2. Check for dedicated financial inquiry patterns
        if (FINANCIAL_INQUIRY_PATTERNS.any { pattern -> pattern in normalized }) {
            return false
        }

        // 3. Tokenize words by non-alphanumeric (excluding currency symbols like ₹)
        val tokens = normalized
            .replace(Regex("""[^\p{L}\p{Nd}₹]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }

        // 4. Check tokens against primary financial keywords
        if (tokens.any { it in FINANCIAL_KEYWORDS }) {
            return false
        }

        // 5. Check tokens against category & item keywords
        if (tokens.any { it in CATEGORY_AND_ITEM_KEYWORDS }) {
            return false
        }

        // 6. Check if query mentions any known merchant from the user's transactions
        if (knownMerchants.isNotEmpty()) {
            for (merchant in knownMerchants) {
                val cleanMerchant = merchant.lowercase(Locale.getDefault()).trim()
                if (cleanMerchant.length >= 3 && cleanMerchant in normalized) {
                    return false
                }
            }
        }

        // 7. Check if query mentions any known account name
        if (knownAccounts.isNotEmpty()) {
            for (account in knownAccounts) {
                val cleanAccount = account.lowercase(Locale.getDefault()).trim()
                if (cleanAccount.length >= 3 && cleanAccount in normalized) {
                    return false
                }
            }
        }

        // None of the financial, category, inquiry, or ledger terms matched
        return true
    }

    /**
     * Returns a rotating or deterministic cute message based on query hash.
     */
    fun getCuteOffTopicResponse(query: String = ""): String {
        val index = if (query.isBlank()) {
            (System.currentTimeMillis() % CUTE_OFF_TOPIC_RESPONSES.size).toInt()
        } else {
            abs(query.hashCode()) % CUTE_OFF_TOPIC_RESPONSES.size
        }
        return CUTE_OFF_TOPIC_RESPONSES[index]
    }
}
