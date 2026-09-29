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

    @Test
    fun preApprovedPersonalLoanAlert_isDetectedAsPromotional() {
        val text = "Congratulations! Pre-approved personal loan of Rs 5,00,000 on ICICI Bank A/C 1007. Transfer up to Rs600000. Apply now."
        assertTrue(PromotionalDetector.isPromotional(text))
    }

    @Test
    fun creditLimitEnhancementAlert_isDetectedAsPromotional() {
        val text = "Special offer! Credit limit on your ICICI Bank Card ending 1007 has been enhanced to Rs 6,00,000. Avail now."
        assertTrue(PromotionalDetector.isPromotional(text))
    }

    @Test
    fun legitimateLoanEmiDebit_isNotPromotional() {
        val text = "Dear Customer, A/c debited with Rs 15,200.00 on 05-Sep-26 towards Home Loan EMI. Avl Bal: Rs 45,000.00"
        assertFalse(PromotionalDetector.isPromotional(text))
    }
}
