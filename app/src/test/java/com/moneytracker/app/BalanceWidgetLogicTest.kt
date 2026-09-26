package com.moneytracker.app

import com.moneytracker.app.data.db.AccountEntity
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.ui.asCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceWidgetLogicTest {

    @Test
    fun `test total bank balance calculation excludes credit card debt`() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "HDFC Salary", kind = AccountKind.BANK, currentBalance = 45000.0),
            AccountEntity(id = 2, name = "ICICI Savings", kind = AccountKind.BANK, currentBalance = 15000.0),
            AccountEntity(id = 3, name = "Amazon Pay ICICI", kind = AccountKind.CARD, currentBalance = 8000.0),
        )

        val totalBankBalance = accounts.filter { it.kind != AccountKind.CARD }.sumOf { it.currentBalance }
        assertEquals(60000.0, totalBankBalance, 0.001)
    }

    @Test
    fun `test account sorting prioritizes banks with highest balance followed by cards`() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "ICICI Card", kind = AccountKind.CARD, currentBalance = 20000.0),
            AccountEntity(id = 2, name = "HDFC Savings", kind = AccountKind.BANK, currentBalance = 10000.0),
            AccountEntity(id = 3, name = "SBI Salary", kind = AccountKind.BANK, currentBalance = 50000.0),
            AccountEntity(id = 4, name = "Axis Bank", kind = AccountKind.BANK, currentBalance = 500.0),
        )

        val sorted = accounts.sortedWith(
            compareByDescending<AccountEntity> { it.kind != AccountKind.CARD }
                .thenByDescending { it.currentBalance }
        )

        // Top 3 should be SBI (50k), HDFC (10k), Axis (500)
        assertEquals("SBI Salary", sorted[0].name)
        assertEquals("HDFC Savings", sorted[1].name)
        assertEquals("Axis Bank", sorted[2].name)
        assertEquals("ICICI Card", sorted[3].name)
    }

    @Test
    fun `test account name display formats with last four digits when available`() {
        val accWithDigits = AccountEntity(id = 1, name = "HDFC Bank", kind = AccountKind.BANK, lastFourDigits = "4128")
        val accWithoutDigits = AccountEntity(id = 2, name = "Cash Wallet", kind = AccountKind.CASH, lastFourDigits = null)

        val formatted1 = if (!accWithDigits.lastFourDigits.isNullOrBlank()) {
            "${accWithDigits.name} (•••• ${accWithDigits.lastFourDigits})"
        } else {
            accWithDigits.name
        }

        val formatted2 = if (!accWithoutDigits.lastFourDigits.isNullOrBlank()) {
            "${accWithoutDigits.name} (•••• ${accWithoutDigits.lastFourDigits})"
        } else {
            accWithoutDigits.name
        }

        assertEquals("HDFC Bank (•••• 4128)", formatted1)
        assertEquals("Cash Wallet", formatted2)
    }

    @Test
    fun `test currency formatting produces valid INR currency string`() {
        val amount = 125400.0
        val formatted = amount.asCurrency()
        assertTrue(formatted.contains("1,25,400") || formatted.contains("125,400"))
    }
}
