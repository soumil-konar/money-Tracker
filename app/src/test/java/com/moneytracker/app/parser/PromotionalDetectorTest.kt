package com.moneytracker.app.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromotionalDetectorTest {

    @Test
    fun `bhim wireless earbuds marketing offer is detected and ignored`() {
        val title = "🎧 Wireless Earbuds @ ₹199"
        val text = "Hear clearly, live fully! Grab ultra-light wireless earbuds for just ₹199. Shop now!"

        assertTrue(PromotionalDetector.isPromotional(title, text))
        assertNotNull(PromotionalDetector.findPromotionalReason(title, text))
    }

    @Test
    fun `bhim assured cashback marketing campaign is detected and ignored`() {
        val title = "Assured Cashback till 11 PM 🔥"
        val text = "Make any 2 UPI Lite payments of ₹20+ on BHIM today and get up to ₹20 cashback on each. Only till 11 PM."

        assertTrue(PromotionalDetector.isPromotional(title, text))
        assertNotNull(PromotionalDetector.findPromotionalReason(title, text))
    }

    @Test
    fun `gpay recharge cashback marketing prompt is detected and ignored`() {
        val title = "Google Pay"
        val text = "Recharge your mobile now and win up to ₹100 cashback! Offer valid today only."

        assertTrue(PromotionalDetector.isPromotional(title, text))
    }

    @Test
    fun `phonepe scratch and win campaign is detected and ignored`() {
        val title = "Scratch & Win up to ₹500"
        val text = "Send money to 3 friends to unlock your exclusive scratch card!"

        assertTrue(PromotionalDetector.isPromotional(title, text))
    }

    @Test
    fun `paytm loan pre-approval promotion is detected and ignored`() {
        val title = "Instant Loan Approved"
        val text = "Pre-approved personal loan of ₹2,50,000 at zero processing fee. Apply now!"

        assertTrue(PromotionalDetector.isPromotional(title, text))
    }

    @Test
    fun `cred shopping deal notification is detected and ignored`() {
        val title = "CRED Member Access"
        val text = "Smartwatches starting @ ₹1,499. Shop now on CRED Store."

        assertTrue(PromotionalDetector.isPromotional(title, text))
    }

    @Test
    fun `bank debit card discount campaign is detected and ignored`() {
        val title = "Special offer on your HDFC Bank Card"
        val text = "Enjoy 10% instant discount on Flipkart Big Billion Days with your HDFC card. Shop now!"

        assertTrue(PromotionalDetector.isPromotional(title, text))
    }

    @Test
    fun `bhim legitimate payment transaction is NOT classified as promotional`() {
        val title = "BHIM"
        val text = "Paid ₹150.00 to Chai Point successfully."

        assertFalse(PromotionalDetector.isPromotional(title, text))
        assertNull(PromotionalDetector.findPromotionalReason(title, text))
    }

    @Test
    fun `google pay legitimate payment transaction is NOT classified as promotional`() {
        val title = "Paid ₹280 to Chai Point"
        val text = "Paid using HDFC Bank ••5678"

        assertFalse(PromotionalDetector.isPromotional(title, text))
        assertNull(PromotionalDetector.findPromotionalReason(title, text))
    }

    @Test
    fun `phonepe legitimate payment transaction is NOT classified as promotional`() {
        val title = "Payment Successful"
        val text = "₹350 paid to Swiggy"

        assertFalse(PromotionalDetector.isPromotional(title, text))
        assertNull(PromotionalDetector.findPromotionalReason(title, text))
    }

    @Test
    fun `tata neu legitimate payment transaction is NOT classified as promotional`() {
        val title = "Tata Neu"
        val text = "Payment of ₹1,499.00 to Croma successful via Tata Pay UPI"

        assertFalse(PromotionalDetector.isPromotional(title, text))
        assertNull(PromotionalDetector.findPromotionalReason(title, text))
    }

    @Test
    fun `bank debit alert sms is NOT classified as promotional`() {
        val title = "HDFC Bank Alert"
        val text = "Alert: You have spent Rs. 450.00 at Starbucks using Debit Card ending 1234 on 24-Sep-26."

        assertFalse(PromotionalDetector.isPromotional(title, text))
        assertNull(PromotionalDetector.findPromotionalReason(title, text))
    }

    @Test
    fun `bank credit alert sms is NOT classified as promotional`() {
        val title = "SBI"
        val text = "Your A/C 9876 is credited with INR 25,000.00 on 27-Sep-26 by salary transfer."

        assertFalse(PromotionalDetector.isPromotional(title, text))
        assertNull(PromotionalDetector.findPromotionalReason(title, text))
    }

    @Test
    fun `actual cashback credited notification is NOT classified as promotional`() {
        val title = "Google Pay"
        val text = "Cashback of ₹20.00 credited to your A/C 1234 for UPI payment."

        assertFalse(PromotionalDetector.isPromotional(title, text))
        assertNull(PromotionalDetector.findPromotionalReason(title, text))
    }
}
