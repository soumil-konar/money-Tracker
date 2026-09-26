package com.moneytracker.app.bank

import com.moneytracker.app.data.model.AccountKind
import java.util.Locale

data class BankDefinition(
    val canonicalName: String,
    val senderPrefixes: List<String>,
    val keywords: List<String>,
)

data class BankAccountDetection(
    val isBankAccount: Boolean,
    val institutionName: String?,
    val accountLastFour: String?,
    val isDebitCard: Boolean = false,
    val debitCardLastFour: String? = null,
    val rejectionReason: String? = null,
)

object BankDetector {

    val KNOWN_BANKS = listOf(
        BankDefinition(
            canonicalName = "State Bank of India",
            senderPrefixes = listOf("SBIINB", "SBIBNK", "SBIPSG", "ATMSBI", "SBISMS", "SBICRD", "SBI"),
            keywords = listOf("state bank of india", "state bank", "sbi a/c", "sbi account", "sbi", "sbi inb"),
        ),
        BankDefinition(
            canonicalName = "HDFC Bank",
            senderPrefixes = listOf("HDFCBK", "HDFCCC", "HDFCTP", "HDFC"),
            keywords = listOf("hdfc bank", "hdfc a/c", "hdfc", "hdfcbank"),
        ),
        BankDefinition(
            canonicalName = "ICICI Bank",
            senderPrefixes = listOf("ICICIB", "ICICIC", "ICICIT", "ICICI"),
            keywords = listOf("icici bank", "icici a/c", "icici", "icicibank", "imobile"),
        ),
        BankDefinition(
            canonicalName = "Axis Bank",
            senderPrefixes = listOf("AXISBK", "AXISBN", "AXIS"),
            keywords = listOf("axis bank", "axis a/c", "axis", "axisbank"),
        ),
        BankDefinition(
            canonicalName = "Kotak Bank",
            senderPrefixes = listOf("KOTAKB", "KOTAKM", "KOTAK"),
            keywords = listOf("kotak mahindra bank", "kotak bank", "kotak a/c", "kotak 811", "kotak"),
        ),
        BankDefinition(
            canonicalName = "Punjab National Bank",
            senderPrefixes = listOf("PNBSMS", "PNBBNK", "PNB"),
            keywords = listOf("punjab national bank", "pnb bank", "pnb a/c", "pnb"),
        ),
        BankDefinition(
            canonicalName = "Bank of Baroda",
            senderPrefixes = listOf("BOBTXN", "BOBSMS", "BARODA", "BOB"),
            keywords = listOf("bank of baroda", "baroda bank", "bob a/c", "bob world", "bob"),
        ),
        BankDefinition(
            canonicalName = "Canara Bank",
            senderPrefixes = listOf("CANBNK", "CNRBK", "CANARA"),
            keywords = listOf("canara bank", "canara a/c", "canara ai1", "canara"),
        ),
        BankDefinition(
            canonicalName = "Union Bank of India",
            senderPrefixes = listOf("UNIONB", "UBIN", "UNION"),
            keywords = listOf("union bank of india", "union bank", "union a/c", "ubin"),
        ),
        BankDefinition(
            canonicalName = "IndusInd Bank",
            senderPrefixes = listOf("INDBNK", "INDUS", "INDUSD"),
            keywords = listOf("indusind bank", "indusind a/c", "indusind"),
        ),
        BankDefinition(
            canonicalName = "IDFC FIRST Bank",
            senderPrefixes = listOf("IDFCFB", "IDFCBK", "IDFC"),
            keywords = listOf("idfc first bank", "idfc bank", "idfc a/c", "idfc first", "idfc"),
        ),
        BankDefinition(
            canonicalName = "Yes Bank",
            senderPrefixes = listOf("YESBNK", "YESB", "YES"),
            keywords = listOf("yes bank", "yes a/c", "yesbank"),
        ),
        BankDefinition(
            canonicalName = "Federal Bank",
            senderPrefixes = listOf("FEDBNK", "FEDERAL"),
            keywords = listOf("federal bank", "fed a/c", "federal"),
        ),
        BankDefinition(
            canonicalName = "Indian Bank",
            senderPrefixes = listOf("INDIANB", "INDBK"),
            keywords = listOf("indian bank", "indian bank a/c"),
        ),
        BankDefinition(
            canonicalName = "Bank of India",
            senderPrefixes = listOf("BOIIND", "BOISMS", "BOI"),
            keywords = listOf("bank of india", "boi a/c", "boi"),
        ),
        BankDefinition(
            canonicalName = "Central Bank of India",
            senderPrefixes = listOf("CBISMS", "CENTRAL"),
            keywords = listOf("central bank of india", "central bank a/c", "cbi a/c"),
        ),
        BankDefinition(
            canonicalName = "RBL Bank",
            senderPrefixes = listOf("RBLBNK", "RBL"),
            keywords = listOf("rbl bank", "rbl a/c", "rbl"),
        ),
        BankDefinition(
            canonicalName = "AU Small Finance Bank",
            senderPrefixes = listOf("AUBANK", "AUBNK"),
            keywords = listOf("au small finance bank", "au bank", "aubank"),
        ),
        BankDefinition(
            canonicalName = "Standard Chartered",
            senderPrefixes = listOf("SCISMS", "SCBL", "STANCHAR"),
            keywords = listOf("standard chartered bank", "standard chartered", "sc bank", "stanchart"),
        ),
        BankDefinition(
            canonicalName = "Citibank",
            senderPrefixes = listOf("CITIBK", "CITI"),
            keywords = listOf("citibank", "citi bank", "citi"),
        ),
        BankDefinition(
            canonicalName = "HSBC",
            senderPrefixes = listOf("HSBCBK", "HSBC"),
            keywords = listOf("hsbc bank", "hsbc"),
        ),
        BankDefinition(
            canonicalName = "Paytm Payments Bank",
            senderPrefixes = listOf("PAYTMB"),
            keywords = listOf("paytm payments bank", "paytm bank"),
        ),
        BankDefinition(
            canonicalName = "Airtel Payments Bank",
            senderPrefixes = listOf("AIRBNK"),
            keywords = listOf("airtel payments bank", "airtel bank"),
        ),
    )

    private val nonBankSenderKeywords = listOf(
        "swiggy", "zomato", "amazon", "flipkart", "myntra", "zepto", "blinkit",
        "uber", "ola", "rapido", "irctc", "makemytrip", "cred", "paytm", "phonepe",
        "gpay", "slice", "uni", "lazypay", "simpl", "jio", "airtel", "vi", "vodafone",
        "idea", "bsnl", "netflix", "spotify", "prime", "hotstar", "youtube", "dream11",
        "rummy", "promo", "offer", "discount", "survey", "otp", "notice",
    )

    private val bankAccountLastFourRegexes = listOf(
        // "A/c ...1234", "A/c No. 1234", "Account XX1234", "A/c ••1234", "A/c ending in 1234"
        Regex("""(?i)(?:a/c|acct|account|acc)\s*(?:no\.?|number|ending|ending with|ending in)?[#:\s.\-•xX*]*([0-9]{4})\b"""),
        // "ending with 1234", "ending in 1234", "ending 1234"
        Regex("""(?i)\b(?:ending|ending with|ending in)\s*(?:a/c|acct|account)?[#:\s.\-•xX*]*([0-9]{4})\b"""),
        // "from A/c 1234", "to A/c 1234", "in A/c 1234"
        Regex("""(?i)\b(?:from|to|in|using|via)\s+(?:a/c|acct|account)\s*[#:\s.\-•xX*]*([0-9]{4})\b"""),
        // Masked account: "**1234", "XX1234", "••1234", "...1234"
        Regex("""(?i)\b(?:[xX*•]{2,}|\.{2,})([0-9]{4})\b"""),
        Regex("""(?i)\b[xX*•]{1,3}([0-9]{4})\b"""),
    )

    private val cardIndicatorRegexes = listOf(
        Regex("""(?i)(?:credit\s+card|rupay\s+credit|card\s+ending)\s*(?:no\.?|number|ending|with|in)?\s*[*xX•.\-\s]*([0-9]{4})"""),
        Regex("""(?i)(?:on|using|for)\s+credit\s+card\s*[*xX•.\-\s]*([0-9]{4})"""),
    )

    private val debitCardIndicatorRegexes = listOf(
        Regex("""(?i)(?:debit\s+card)\s*(?:no\.?|number|ending|with|in)?\s*[*xX•.\-\s]*([0-9]{4})"""),
        Regex("""(?i)(?:on|using|for)\s+debit\s+card\s*[*xX•.\-\s]*([0-9]{4})"""),
    )

    /**
     * Resolves the canonical bank name if the sender or text matches a recognized Indian bank.
     * Returns null if the entity is not a recognized bank.
     */
    fun resolveBankInstitution(sender: String, body: String = ""): String? {
        val cleanSender = sender.trim().uppercase(Locale.ENGLISH)
        val cleanBody = body.lowercase(Locale.ENGLISH)

        // Extract TRAI code: e.g. "AD-HDFCBK" -> "HDFCBK", "VK-SBIINB" -> "SBIINB"
        val senderCode = cleanSender.substringAfter("-").trim()

        // 1. Check exact sender code matches first
        for (bank in KNOWN_BANKS) {
            if (bank.senderPrefixes.any { prefix -> senderCode.startsWith(prefix) || cleanSender.contains(prefix) }) {
                return bank.canonicalName
            }
        }

        // 2. Reject obvious non-bank entities
        val isExplicitNonBank = nonBankSenderKeywords.any {
            cleanSender.contains(it, ignoreCase = true) || senderCode.contains(it, ignoreCase = true)
        }
        if (isExplicitNonBank && !KNOWN_BANKS.any { it.canonicalName.lowercase() in cleanBody }) {
            return null
        }

        // 3. Search body for bank keywords (longest match first)
        for (bank in KNOWN_BANKS) {
            if (bank.keywords.any { it in cleanBody }) {
                return bank.canonicalName
            }
        }

        return null
    }

    /**
     * Returns true if the institution name corresponds to a recognized banking institution.
     */
    fun isLegitimateBank(institutionName: String?): Boolean {
        if (institutionName.isNullOrBlank()) return false
        val clean = institutionName.trim().lowercase(Locale.ENGLISH)
        return KNOWN_BANKS.any { bank ->
            bank.canonicalName.lowercase(Locale.ENGLISH) == clean ||
                clean.contains(bank.canonicalName.lowercase(Locale.ENGLISH)) ||
                bank.keywords.any { clean == it || clean.contains(it) }
        }
    }

    /**
     * Normalizes an institution name into its canonical bank name.
     * If not recognized, returns null instead of passing through garbage.
     */
    fun normalizeToCanonicalBank(institutionName: String?): String? {
        if (institutionName.isNullOrBlank()) return null
        val clean = institutionName.trim().lowercase(Locale.ENGLISH)
        for (bank in KNOWN_BANKS) {
            if (bank.canonicalName.lowercase(Locale.ENGLISH) == clean ||
                clean.contains(bank.canonicalName.lowercase(Locale.ENGLISH).removeSuffix(" bank")) ||
                bank.keywords.any { clean.contains(it) } ||
                bank.senderPrefixes.any { clean.contains(it.lowercase()) }
            ) {
                return bank.canonicalName
            }
        }
        return null
    }

    /**
     * Accurately determines if a given message represents an authentic Bank Account.
     * Differentiates bank accounts from credit cards, wallets, and non-bank merchants.
     */
    fun detectBankAccount(sender: String, body: String): BankAccountDetection {
        val institution = resolveBankInstitution(sender, body)
        if (institution == null) {
            return BankAccountDetection(
                isBankAccount = false,
                institutionName = null,
                accountLastFour = null,
                rejectionReason = "Sender '$sender' is not a recognized banking institution.",
            )
        }

        val lowerBody = body.lowercase(Locale.ENGLISH)

        // Check if message is exclusively a Credit Card alert with NO bank account debit
        val creditCardMatch = cardIndicatorRegexes.firstNotNullOfOrNull { it.find(body) }
        val hasExplicitCreditCard = creditCardMatch != null || listOf("credit card", "credit card ending", "card ending").any { it in lowerBody }
        val hasDebitFromBankAccount = listOf(
            "debited from a/c", "debited from account", "from a/c", "from account", "a/c debited", "account debited",
            "credited to a/c", "credited to account", "to a/c", "to account", "a/c credited", "account credited",
        ).any { it in lowerBody }

        val bankAccountLastFour = extractBankAccountLastFour(body)
        val debitCardMatch = debitCardIndicatorRegexes.firstNotNullOfOrNull { it.find(body) }
        val debitCardLastFour = debitCardMatch?.groupValues?.getOrNull(1)

        // Standalone Credit Card transaction (not a debit from a bank account)
        if (hasExplicitCreditCard && !hasDebitFromBankAccount && bankAccountLastFour == null) {
            return BankAccountDetection(
                isBankAccount = false,
                institutionName = institution,
                accountLastFour = creditCardMatch?.groupValues?.getOrNull(1),
                rejectionReason = "Identified as a credit card alert, not a bank savings/current account.",
            )
        }

        // Positive check for Bank Account
        val hasBankAccountSignals = hasDebitFromBankAccount ||
            bankAccountLastFour != null ||
            listOf("a/c", "acct", "account", "savings", "current", "salary account", "chq", "cheque").any { it in lowerBody } ||
            debitCardLastFour != null

        if (hasBankAccountSignals) {
            return BankAccountDetection(
                isBankAccount = true,
                institutionName = institution,
                accountLastFour = bankAccountLastFour ?: debitCardLastFour,
                isDebitCard = debitCardLastFour != null,
                debitCardLastFour = debitCardLastFour,
            )
        }

        // If from recognized bank sender and contains transaction verbs, default to bank account
        val hasTxnVerbs = listOf("debited", "credited", "spent", "received", "withdrawn", "deposited", "transferred", "upi").any { it in lowerBody }
        if (hasTxnVerbs) {
            return BankAccountDetection(
                isBankAccount = true,
                institutionName = institution,
                accountLastFour = bankAccountLastFour,
            )
        }

        return BankAccountDetection(
            isBankAccount = false,
            institutionName = institution,
            accountLastFour = null,
            rejectionReason = "Message from bank does not contain account transaction or balance signals.",
        )
    }

    fun extractBankAccountLastFour(body: String): String? {
        // Exclude credit card last 4 if credit card is explicitly mentioned right next to it
        val cardMatch = cardIndicatorRegexes.firstNotNullOfOrNull { it.find(body) }
        val cardDigits = cardMatch?.groupValues?.getOrNull(1)

        for (regex in bankAccountLastFourRegexes) {
            val match = regex.find(body)
            if (match != null) {
                val digits = match.groupValues[1]
                if (digits != cardDigits) {
                    return digits
                }
            }
        }
        return null
    }

    fun extractCardLastFour(body: String): String? {
        val cardMatch = cardIndicatorRegexes.firstNotNullOfOrNull { it.find(body) }
            ?: debitCardIndicatorRegexes.firstNotNullOfOrNull { it.find(body) }
        return cardMatch?.groupValues?.getOrNull(1)
            ?.filter(Char::isDigit)
            ?.takeLast(4)
            ?.takeIf { it.length == 4 }
    }
}
