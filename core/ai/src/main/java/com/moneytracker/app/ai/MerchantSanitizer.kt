package com.moneytracker.app.ai

import java.util.Locale

object MerchantSanitizer {

    private val PRESERVED_ACRONYMS = setOf(
        "UPI", "ATM", "IRCTC", "OTP", "INR", "SMS", "VPA", "POS", "IMPS", "NEFT", "RTGS",
        "BBPS", "NPCI", "HPCL", "BPCL", "IOCL", "KFC", "BMW", "HDFC", "ICICI", "SBI",
        "PNB", "BOB", "DTH", "ACT", "BESCOM", "TNEB", "MSEDCL", "BWSSB", "USA", "UK"
    )

    private val DISCLAIMER_LINE_PATTERNS = listOf(
        Regex("""(?i)^\s*not you\b.*"""),
        Regex("""(?i)^\s*if not you\b.*"""),
        Regex("""(?i)^\s*sms\s+block.*"""),
        Regex("""(?i)^\s*blockupi\b.*"""),
        Regex("""(?i)^\s*call\s+helpline.*"""),
        Regex("""(?i)^\s*call\s+\d+.*"""),
        Regex("""(?i)^\s*cust\s*id\b.*"""),
        Regex("""(?i)^\s*(?:axis|hdfc|sbi|icici|kotak|pnb|bob|yes)\s+bank\b.*"""),
        Regex("""(?i)^\s*helpline\b.*"""),
        Regex("""(?i)^\s*report\s+(?:dispute|fraud).*"""),
    )

    private val UPI_PREFIX_REGEX = Regex("""(?i)^UPI/(?:P2[MP]|REV)/\d+/""")
    private val UPI_ANY_PREFIX_REGEX = Regex("""(?i)^UPI/(?:[A-Za-z0-9_-]+/)?(?:\d+/)+""")
    private val UPI_SIMPLE_PREFIX_REGEX = Regex("""(?i)^UPI/""")

    private val TRAILING_DISCLAIMER_REGEX = Regex(
        """(?i)\s*(?:/|\b)(?:not you\?|sms\s+blockupi|blockupi|cust\s*id|axis\s+bank|hdfc\s+bank|icici\s+bank|sbi\s+bank|call\s+helpline|call\s+\d+).*$"""
    )

    /**
     * Cleans and formats raw merchant/payee strings from SMS alerts or AI extractions into
     * human-readable titles. Handles:
     * - Stripping UPI prefixes (UPI/P2M/<rrn>/, UPI/P2P/<rrn>/, etc.)
     * - Joining multi-line wrapped payees and stopping before disclaimers
     * - Stripping fraud warnings and bank boilerplate (e.g. "Not you?", "Axis Bank")
     * - Converting all-caps names to Title Case while preserving standard acronyms (e.g. "UPI", "IRCTC", "ATM")
     */
    fun sanitizeMerchantName(rawMerchant: String): String {
        if (rawMerchant.isBlank()) return ""

        // 1. Process multi-line wraps: take only lines up to the first disclaimer
        val rawLines = rawMerchant.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val contentLines = mutableListOf<String>()

        for (line in rawLines) {
            val isDisclaimer = DISCLAIMER_LINE_PATTERNS.any { it.containsMatchIn(line) }
            if (isDisclaimer) {
                break
            }
            contentLines.add(line)
        }

        var text = if (contentLines.isNotEmpty()) {
            contentLines.joinToString(" ")
        } else {
            rawLines.firstOrNull() ?: rawMerchant
        }.trim()

        // 2. Strip delimited UPI prefix (e.g. UPI/P2M/626969941600/ or UPI/P2P/...)
        text = text.replace(UPI_PREFIX_REGEX, "")
            .replace(UPI_ANY_PREFIX_REGEX, "")
            .trim()

        if (text.startsWith("UPI/", ignoreCase = true)) {
            text = text.replace(UPI_SIMPLE_PREFIX_REGEX, "").trim()
        }

        // 3. Strip inline disclaimers or trailing bank footers
        text = text.replace(TRAILING_DISCLAIMER_REGEX, "").trim()

        // Strip trailing or leading slashes, dashes, colons, or punctuation noise
        text = text.trim('/', '-', ':', ',', '.', ' ')

        // Also if trailing bank footer remains
        for (pattern in DISCLAIMER_LINE_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                text = text.replace(pattern, "").trim()
            }
        }
        text = text.trim('/', '-', ':', ',', '.', ' ')

        if (text.isBlank()) return ""

        // 4. Convert all-caps payee names to Title Case, preserving standard acronyms
        return toTitleCasePreservingAcronyms(text)
    }

    /**
     * Converts all-caps payee strings to readable Title Case while preserving common acronyms
     * (e.g., "UPI", "ATM", "IRCTC"). Mixed-case brand names (e.g., "McDonald's", "PhonePe")
     * are preserved intact.
     */
    fun toTitleCasePreservingAcronyms(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val words = trimmed.split(Regex("\\s+"))
        val isEntirelyUppercase = trimmed.filter { it.isLetter() }.all { it.isUpperCase() }

        val transformedWords = words.map { word ->
            val leadPunct = word.takeWhile { !it.isLetterOrDigit() }
            val trailPunct = word.takeLastWhile { !it.isLetterOrDigit() }
            val core = word.substring(leadPunct.length, word.length - trailPunct.length)

            if (core.isEmpty()) {
                word
            } else if (PRESERVED_ACRONYMS.contains(core.uppercase(Locale.ROOT))) {
                leadPunct + core.uppercase(Locale.ROOT) + trailPunct
            } else if (isEntirelyUppercase || (core.length > 1 && core.all { it.isUpperCase() })) {
                val titleCased = core.first().uppercase(Locale.ROOT) + core.drop(1).lowercase(Locale.ROOT)
                leadPunct + titleCased + trailPunct
            } else {
                word
            }
        }

        return transformedWords.joinToString(" ")
    }

    /**
     * Validates whether a candidate string is a plausible merchant name rather than
     * a currency amount (e.g. "Rs600000", "₹5000", "INR 1200"), pure digits, or generic noise.
     */
    fun isValidMerchantName(name: String): Boolean {
        val trimmed = name.trim().lowercase(Locale.ENGLISH)
        if (trimmed.length < 2) return false
        if (trimmed.matches(Regex("""^(?:rs\.?|inr|re\.?|₹|\$)\s*[0-9,]+.*"""))) return false
        if (trimmed.matches(Regex("""^[0-9,.\s]+$"""))) return false
        val garbage = setOf(
            "merchant", "transaction", "alert", "notification", "bank alert", "savings account",
            "current account", "credit card", "debit card", "your account", "bank"
        )
        if (garbage.contains(trimmed)) return false
        return true
    }
}
