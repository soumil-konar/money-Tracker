package com.moneytracker.app.parser

import java.util.Locale

object PromotionalDetector {

    private val productPricingRegexes = listOf(
        Regex("""(?i)(?:^|[\s\(\[@])@\s*(?:₹|rs\.?|inr)\s*([0-9,]+)"""),
        Regex("""(?i)\b(?:starting\s+@|starting\s+at|starting\s+from|deals?\s+@|priced\s+@)\s*(?:₹|rs\.?|inr)\s*([0-9,]+)"""),
        Regex("""(?i)\b(?:for\s+just|just|only|as\s+low\s+as)\s*(?:₹|rs\.?|inr)\s*([0-9,]+)"""),
        Regex("""(?i)\b(?:under|below)\s*(?:₹|rs\.?|inr)\s*([0-9,]+)"""),
        Regex("""(?i)\b(?:₹|rs\.?|inr)\s*([0-9,]+)\s*(?:only|deal|sale)\b"""),
    )

    private val campaignIncentiveRegexes = listOf(
        Regex("""(?i)\bassured\s+cashback\b"""),
        Regex("""(?i)\bcashback\s+(?:till|valid\s+till|on\s+each|on\s+every|on\s+your|up\s*to|upto|alert)\b"""),
        Regex("""(?i)\b(?:get|win|earn|receive)\s+(?:up\s*to|upto|flat)?\s*(?:₹|rs\.?|inr)?\s*\d+\s*(?:%|cashback|reward|scratch\s*card)"""),
        Regex("""(?i)\b(?:up\s*to|upto)\s*(?:₹|rs\.?|inr)\s*\d+\s*(?:cashback|off|discount|rewards?)"""),
        Regex("""(?i)\bmake\s+(?:any\s+)?\d+\s+(?:upi|payment|transaction|lite|recharge)s?\b"""),
        Regex("""(?i)\b(?:pay|spend|send)\s+(?:₹|rs\.?|inr)?\s*\d+\s*(?:\+|or\s+more)?\s+(?:on|to|and|for)\s+.*(?:get|win|earn|cashback|scratch)"""),
        Regex("""(?i)\bstand\s+a\s+chance\s+to\s+win\b"""),
        Regex("""(?i)\b(?:spin|scratch)\s+(?:to\s+win|and\s+win|&\s+win)\b"""),
        Regex("""(?i)\b(?:only|valid)?\s*till\s+\d{1,2}(?::\d{2})?\s*(?:am|pm)\b"""),
    )

    private val promotionalKeywords = listOf(
        "shop now",
        "buy now",
        "order now",
        "grab now",
        "grab today",
        "grab your",
        "grab ultra-light",
        "book now",
        "avail now",
        "explore now",
        "check offers",
        "view offers",
        "start investing",
        "claim now",
        "claim your",
        "tap to claim",
        "claim offer",
        "claim reward",
        "claim coupon",
        "claim voucher",
        "scratch now",
        "tap to scratch",
        "scratch card",
        "spin now",
        "spin to win",
        "spin the wheel",
        "play & win",
        "play and win",
        "refer & earn",
        "refer and earn",
        "refer a friend",
        "invite & earn",
        "invite friends",
        "invite your friends",
        "recharge now",
        "use code",
        "coupon code",
        "promo code",
        "voucher code",
        "apply code",
        "apply coupon",
        "deal of the day",
        "deals of the day",
        "flash sale",
        "mega sale",
        "clearance sale",
        "festive offer",
        "special offer",
        "exclusive offer",
        "limited time offer",
        "limited period offer",
        "bumper offer",
        "mega offer",
        "pre-approved",
        "pre approved",
        "instant loan",
        "personal loan",
        "business loan",
        "credit limit enhanced",
        "credit limit increased",
        "lifetime free card",
        "apply for card",
        "apply for credit card",
        "convert to emi",
        "on emi purchases",
        "no-cost emi",
        "no cost emi",
        "supercoins",
        "super coins",
        "reward points",
        "bonus cash",
        "mystery reward",
        "secret reward",
        "hurry!",
        "don't miss out",
        "offer ends",
    )

    private val confirmedTransactionRegexes = listOf(
        Regex("""(?i)\b(?:has\s+been|is|was)\s+debited\b"""),
        Regex("""(?i)\bdebited\s+(?:with|by|for|from)\b"""),
        Regex("""(?i)\b(?:a/c|account|acct)\s+debited\b"""),
        Regex("""(?i)\b(?:has\s+been|is|was)\s+credited\b"""),
        Regex("""(?i)\bcredited\s+(?:with|to|for)\b"""),
        Regex("""(?i)\b(?:a/c|account|acct)\s+credited\b"""),
        Regex("""(?i)\bpaid\s+(?:₹|rs\.?|inr)\s*[0-9,]+(?:\.\d{1,2})?\s+(?:to|for)\b"""),
        Regex("""(?i)(?:₹|rs\.?|inr)\s*[0-9,]+(?:\.\d{1,2})?\s+paid\s+(?:to|for)\b"""),
        Regex("""(?i)\bpaid\s+(?:to|for)\s+[a-z0-9\s@&._-]+\s+successfully\b"""),
        Regex("""(?i)\bpayment\s+of\s+(?:₹|rs\.?|inr)\s*[0-9,]+(?:\.\d{1,2})?\s+to\s+.*\s+successful\b"""),
        Regex("""(?i)\bpayment\s+successful:\s*(?:₹|rs\.?|inr)"""),
        Regex("""(?i)\bsuccessfully\s+paid\s+(?:₹|rs\.?|inr)"""),
        Regex("""(?i)\bsent\s+(?:₹|rs\.?|inr)\s*[0-9,]+(?:\.\d{1,2})?\s+to\b"""),
        Regex("""(?i)\breceived\s+(?:₹|rs\.?|inr)\s*[0-9,]+(?:\.\d{1,2})?\s+from\b"""),
        Regex("""(?i)\bmoney\s+sent\s+to\b"""),
        Regex("""(?i)\bmoney\s+received\s+from\b"""),
        Regex("""(?i)\b(?:spent|spent\s+at)\s+(?:₹|rs\.?|inr)\s*[0-9,]+"""),
        Regex("""(?i)\b(?:withdrawn|atm\s+withdrawal)\b"""),
        Regex("""(?i)\bcashback\s+of\s+(?:₹|rs\.?|inr)\s*[0-9,]+(?:\.\d{1,2})?\s+credited\b"""),
        Regex("""(?i)\brefund\s+of\s+(?:₹|rs\.?|inr)\s*[0-9,]+(?:\.\d{1,2})?\s+(?:credited|processed)\b"""),
    )

    fun isPromotional(vararg texts: String?): Boolean {
        return findPromotionalReason(*texts) != null
    }

    fun findPromotionalReason(vararg texts: String?): String? {
        val nonBlankTexts = texts.filterNotNull().map(String::trim).filter(String::isNotEmpty)
        if (nonBlankTexts.isEmpty()) return null

        val combined = nonBlankTexts.joinToString(" ")
        val lowerCombined = combined.lowercase(Locale.ENGLISH)

        val hasConfirmedTransaction = hasConfirmedTransactionSignal(combined)

        val titleText = nonBlankTexts.firstOrNull().orEmpty().lowercase(Locale.ENGLISH)
        if (isPromotionalTitle(titleText) && !hasConfirmedTransaction) {
            return "Promotional Title: $titleText"
        }

        for (regex in productPricingRegexes) {
            val match = regex.find(combined)
            if (match != null) {
                if (!hasConfirmedTransaction || hasPromotionalCallToAction(lowerCombined)) {
                    return "Merchandise Ad: ${match.value}"
                }
            }
        }

        for (regex in campaignIncentiveRegexes) {
            val match = regex.find(combined)
            if (match != null && !hasConfirmedTransaction) {
                return "Marketing Campaign: ${match.value}"
            }
        }

        for (keyword in promotionalKeywords) {
            if (lowerCombined.contains(keyword) && !hasConfirmedTransaction) {
                return "Promotional Keyword: '$keyword'"
            }
        }

        return null
    }

    fun hasConfirmedTransactionSignal(text: String): Boolean {
        return confirmedTransactionRegexes.any { it.containsMatchIn(text) }
    }

    private fun isPromotionalTitle(title: String): Boolean {
        if (title.isBlank()) return false
        val promotionalTitleKeywords = listOf(
            "assured cashback",
            "cashback till",
            "cashback offer",
            "cashback alert",
            "special offer",
            "exclusive offer",
            "limited offer",
            "deal of the day",
            "deals of the day",
            "mega deals",
            "scratch & win",
            "scratch and win",
            "spin to win",
            "spin the wheel",
            "refer & earn",
            "refer and earn",
            "pre-approved",
            "instant loan",
            "personal loan",
            "flat ₹",
            "flat rs",
            "flat discount",
            "flash sale",
            "mega sale",
        )
        if (promotionalTitleKeywords.any { title.contains(it) }) return true
        if (title.contains("@ ₹") || title.contains("@ rs") || title.contains("@ inr")) return true
        return false
    }

    private fun hasPromotionalCallToAction(lowerCombined: String): Boolean {
        val ctas = listOf(
            "shop now", "buy now", "order now", "grab now", "grab today", "grab ultra-light",
            "avail now", "claim now", "claim your", "tap to claim", "recharge now",
            "apply now", "book now", "explore now", "scratch now", "spin now"
        )
        return ctas.any { lowerCombined.contains(it) }
    }
}
