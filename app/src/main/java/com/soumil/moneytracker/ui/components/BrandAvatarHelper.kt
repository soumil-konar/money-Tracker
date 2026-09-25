package com.soumil.moneytracker.ui.components

import androidx.compose.ui.graphics.Color
import java.util.Locale

data class BrandBadge(
    val brandName: String,
    val shortCode: String,
    val containerColor: Color,
    val contentColor: Color,
)

object BrandAvatarHelper {

    fun resolve(merchant: String): BrandBadge? {
        val lower = merchant.lowercase(Locale.ENGLISH).trim()

        return when {
            // Food & Quick Commerce
            "swiggy" in lower || "instamart" in lower -> BrandBadge(
                brandName = "Swiggy",
                shortCode = "SW",
                containerColor = Color(0xFFFFEDE0),
                contentColor = Color(0xFFFC8019),
            )
            "zomato" in lower || "blinkit" in lower && "zomato" in lower -> BrandBadge(
                brandName = "Zomato",
                shortCode = "ZM",
                containerColor = Color(0xFFFFEBEE),
                contentColor = Color(0xFFCB202D),
            )
            "blinkit" in lower || "grofers" in lower -> BrandBadge(
                brandName = "Blinkit",
                shortCode = "BK",
                containerColor = Color(0xFFFFF9C4),
                contentColor = Color(0xFFF57F17),
            )
            "zepto" in lower -> BrandBadge(
                brandName = "Zepto",
                shortCode = "ZP",
                containerColor = Color(0xFFEDE7F6),
                contentColor = Color(0xFF512DA8),
            )
            "domino" in lower -> BrandBadge(
                brandName = "Domino's",
                shortCode = "DM",
                containerColor = Color(0xFFE3F2FD),
                contentColor = Color(0xFF0D47A1),
            )
            "starbucks" in lower || "tata starbucks" in lower -> BrandBadge(
                brandName = "Starbucks",
                shortCode = "SB",
                containerColor = Color(0xFFE8F5E9),
                contentColor = Color(0xFF00704A),
            )
            "mcdonald" in lower || "mcd" in lower -> BrandBadge(
                brandName = "McDonald's",
                shortCode = "MD",
                containerColor = Color(0xFFFFF8E1),
                contentColor = Color(0xFFD32F2F),
            )
            "kfc" in lower -> BrandBadge(
                brandName = "KFC",
                shortCode = "KFC",
                containerColor = Color(0xFFFFEBEE),
                contentColor = Color(0xFFA3080C),
            )

            // E-Commerce & Retail
            "amazon" in lower || "amzn" in lower -> BrandBadge(
                brandName = "Amazon",
                shortCode = "AZ",
                containerColor = Color(0xFFFFF8E1),
                contentColor = Color(0xFFFF9900),
            )
            "flipkart" in lower -> BrandBadge(
                brandName = "Flipkart",
                shortCode = "FK",
                containerColor = Color(0xFFE3F2FD),
                contentColor = Color(0xFF2874F0),
            )
            "myntra" in lower -> BrandBadge(
                brandName = "Myntra",
                shortCode = "MY",
                containerColor = Color(0xFFFCE4EC),
                contentColor = Color(0xFFFF3F6C),
            )
            "bigbasket" in lower || "bb daily" in lower -> BrandBadge(
                brandName = "BigBasket",
                shortCode = "BB",
                containerColor = Color(0xFFF1F8E9),
                contentColor = Color(0xFF689F38),
            )
            "dmart" in lower || "d-mart" in lower -> BrandBadge(
                brandName = "DMart",
                shortCode = "DM",
                containerColor = Color(0xFFE8F5E9),
                contentColor = Color(0xFF2E7D32),
            )
            "nykaa" in lower -> BrandBadge(
                brandName = "Nykaa",
                shortCode = "NY",
                containerColor = Color(0xFFFCE4EC),
                contentColor = Color(0xFFFC2779),
            )
            "tata" in lower && ("neu" in lower || "cliq" in lower || "1mg" in lower) -> BrandBadge(
                brandName = "Tata",
                shortCode = "TT",
                containerColor = Color(0xFFEDE7F6),
                contentColor = Color(0xFF4A148C),
            )

            // Mobility & Travel
            "uber" in lower -> BrandBadge(
                brandName = "Uber",
                shortCode = "UB",
                containerColor = Color(0xFFECEFF1),
                contentColor = Color(0xFF111111),
            )
            "ola" in lower || "ani technologies" in lower -> BrandBadge(
                brandName = "Ola",
                shortCode = "OL",
                containerColor = Color(0xFFF9FBE7),
                contentColor = Color(0xFF827717),
            )
            "rapido" in lower -> BrandBadge(
                brandName = "Rapido",
                shortCode = "RP",
                containerColor = Color(0xFFFFF9C4),
                contentColor = Color(0xFFF57F17),
            )
            "irctc" in lower -> BrandBadge(
                brandName = "IRCTC",
                shortCode = "IR",
                containerColor = Color(0xFFE3F2FD),
                contentColor = Color(0xFF0D47A1),
            )
            "makemytrip" in lower || "mmt" in lower -> BrandBadge(
                brandName = "MakeMyTrip",
                shortCode = "MMT",
                containerColor = Color(0xFFFFEBEE),
                contentColor = Color(0xFFE53935),
            )
            "indigo" in lower -> BrandBadge(
                brandName = "IndiGo",
                shortCode = "6E",
                containerColor = Color(0xFFE8EAF6),
                contentColor = Color(0xFF001B94),
            )

            // Subscriptions & Tech
            "netflix" in lower -> BrandBadge(
                brandName = "Netflix",
                shortCode = "NF",
                containerColor = Color(0xFF262626),
                contentColor = Color(0xFFE50914),
            )
            "spotify" in lower -> BrandBadge(
                brandName = "Spotify",
                shortCode = "SP",
                containerColor = Color(0xFFE8F5E9),
                contentColor = Color(0xFF1DB954),
            )
            "apple" in lower || "itunes" in lower || "icloud" in lower -> BrandBadge(
                brandName = "Apple",
                shortCode = "AP",
                containerColor = Color(0xFFF5F5F7),
                contentColor = Color(0xFF1D1D1F),
            )
            "google" in lower || "youtube" in lower || "play store" in lower -> BrandBadge(
                brandName = "Google",
                shortCode = "GO",
                containerColor = Color(0xFFE8F0FE),
                contentColor = Color(0xFF1A73E8),
            )
            "openai" in lower || "chatgpt" in lower -> BrandBadge(
                brandName = "OpenAI",
                shortCode = "AI",
                containerColor = Color(0xFFE0F2F1),
                contentColor = Color(0xFF00695C),
            )

            // Telecom
            "jio" in lower -> BrandBadge(
                brandName = "Jio",
                shortCode = "JIO",
                containerColor = Color(0xFFE3F2FD),
                contentColor = Color(0xFF0A3C91),
            )
            "airtel" in lower -> BrandBadge(
                brandName = "Airtel",
                shortCode = "AIR",
                containerColor = Color(0xFFFFEBEE),
                contentColor = Color(0xFFE40000),
            )

            // Fintech & Payments
            "cred" in lower -> BrandBadge(
                brandName = "CRED",
                shortCode = "CR",
                containerColor = Color(0xFFF5F5F5),
                contentColor = Color(0xFF1A1A1A),
            )
            "zerodha" in lower -> BrandBadge(
                brandName = "Zerodha",
                shortCode = "ZR",
                containerColor = Color(0xFFE3F2FD),
                contentColor = Color(0xFF387ED1),
            )

            // ATM / Cash
            "atm" in lower || "cash withdrawal" in lower -> BrandBadge(
                brandName = "ATM",
                shortCode = "ATM",
                containerColor = Color(0xFFE8F5E9),
                contentColor = Color(0xFF2E7D32),
            )

            else -> null
        }
    }
}
