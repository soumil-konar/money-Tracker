package com.moneytracker.app.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionalDetectorTest {

    @Test
    fun pricingAdvertisement_isDetectedAsPromotional() {
        val text = "Wireless Earbuds @ ₹199 only! Grab your deal today. Shop now on Flipkart."
        assertTrue(PromotionalDetector.isPromotional(text))
    }

    @Test
    fun cashbackIncentiveCampaign_isDetectedAsPromotional() {
        val text = "Assured Cashback till 11 PM! Make 2 payments of ₹20+ and earn up to ₹100 cashback."
        assertTrue(PromotionalDetector.isPromotional(text))
    }

    @Test
    fun actualDebitConfirmation_isNotPromotional() {
        val text = "Rs 450.00 debited from A/c XX1234 at Zomato on 12-Mar-25. Avl bal Rs 12,300.00"
        assertFalse(PromotionalDetector.isPromotional(text))
    }
}
