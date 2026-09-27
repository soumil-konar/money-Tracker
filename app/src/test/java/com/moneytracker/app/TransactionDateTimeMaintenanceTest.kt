package com.moneytracker.app

import com.moneytracker.app.data.db.TransactionEntity
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionDraft
import com.moneytracker.app.data.model.TransactionStatus
import com.moneytracker.app.ui.asFullDate
import com.moneytracker.app.ui.asTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TransactionDateTimeMaintenanceTest {

    @Test
    fun `asTime formats timestamps accurately in local timezone`() {
        val zone = ZoneId.systemDefault()
        val fixedDateTime = ZonedDateTime.of(2026, 9, 27, 14, 30, 0, 0, zone)
        val millis = fixedDateTime.toInstant().toEpochMilli()

        val formattedTime = millis.asTime()
        assertEquals("02:30 pm", formattedTime.lowercase())

        val morningDateTime = ZonedDateTime.of(2026, 9, 27, 9, 5, 0, 0, zone)
        val morningMillis = morningDateTime.toInstant().toEpochMilli()
        assertEquals("09:05 am", morningMillis.asTime().lowercase())
    }

    @Test
    fun `date selection preserves existing time component`() {
        val zone = ZoneId.systemDefault()
        // Initial transaction was recorded at 04:45 PM on Sept 25, 2026
        val initialZdt = ZonedDateTime.of(2026, 9, 25, 16, 45, 0, 0, zone)
        val initialMillis = initialZdt.toInstant().toEpochMilli()

        // User picks Sept 27, 2026 in DatePicker (which returns UTC midnight for that date)
        val pickedUtcMidnight = LocalDate.of(2026, 9, 27).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val selectedDate = Instant.ofEpochMilli(pickedUtcMidnight)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
        val currentTime = Instant.ofEpochMilli(initialMillis)
            .atZone(zone)
            .toLocalTime()
        val newMillis = selectedDate.atTime(currentTime)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val resultZdt = Instant.ofEpochMilli(newMillis).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 27), resultZdt.toLocalDate())
        assertEquals(16, resultZdt.hour)
        assertEquals(45, resultZdt.minute)
    }

    @Test
    fun `time selection preserves existing date component`() {
        val zone = ZoneId.systemDefault()
        // Initial transaction was recorded on Sept 27, 2026 at 10:00 AM
        val initialZdt = ZonedDateTime.of(2026, 9, 27, 10, 0, 0, 0, zone)
        val initialMillis = initialZdt.toInstant().toEpochMilli()

        // User changes time to 21:15 (09:15 PM) via TimePicker
        val newHour = 21
        val newMinute = 15

        val currentDate = Instant.ofEpochMilli(initialMillis)
            .atZone(zone)
            .toLocalDate()
        val newTime = LocalTime.of(newHour, newMinute)
        val updatedMillis = currentDate.atTime(newTime)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val resultZdt = Instant.ofEpochMilli(updatedMillis).atZone(zone)
        assertEquals(LocalDate.of(2026, 9, 27), resultZdt.toLocalDate())
        assertEquals(21, resultZdt.hour)
        assertEquals(15, resultZdt.minute)
    }

    @Test
    fun `transactions on same day are sorted chronologically by time descending`() {
        val zone = ZoneId.systemDefault()
        val txMorning = TransactionEntity(
            id = 1,
            amount = 120.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = ZonedDateTime.of(2026, 9, 27, 8, 30, 0, 0, zone).toInstant().toEpochMilli(),
            merchant = "Morning Coffee",
            category = TransactionCategory.FOOD,
            accountId = 1L,
            sourceSender = "MANUAL",
            smsBody = null,
            confidence = 1.0,
            fingerprint = "fp1",
            status = TransactionStatus.POSTED,
        )
        val txAfternoon = TransactionEntity(
            id = 2,
            amount = 450.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = ZonedDateTime.of(2026, 9, 27, 13, 15, 0, 0, zone).toInstant().toEpochMilli(),
            merchant = "Lunch Bistro",
            category = TransactionCategory.FOOD,
            accountId = 1L,
            sourceSender = "MANUAL",
            smsBody = null,
            confidence = 1.0,
            fingerprint = "fp2",
            status = TransactionStatus.POSTED,
        )
        val txNight = TransactionEntity(
            id = 3,
            amount = 890.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = ZonedDateTime.of(2026, 9, 27, 20, 45, 0, 0, zone).toInstant().toEpochMilli(),
            merchant = "Dinner House",
            category = TransactionCategory.FOOD,
            accountId = 1L,
            sourceSender = "MANUAL",
            smsBody = null,
            confidence = 1.0,
            fingerprint = "fp3",
            status = TransactionStatus.POSTED,
        )

        val list = listOf(txMorning, txNight, txAfternoon)
        val sortedList = list.sortedWith(
            compareByDescending<TransactionEntity> { it.occurredAtMillis }
                .thenByDescending { it.id }
        )

        assertEquals("Dinner House", sortedList[0].merchant)
        assertEquals("Lunch Bistro", sortedList[1].merchant)
        assertEquals("Morning Coffee", sortedList[2].merchant)
    }

    @Test
    fun `updating transaction copies updated occurredAtMillis timestamp`() {
        val zone = ZoneId.systemDefault()
        val originalTime = ZonedDateTime.of(2026, 9, 27, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val updatedTime = ZonedDateTime.of(2026, 9, 27, 18, 30, 0, 0, zone).toInstant().toEpochMilli()

        val existing = TransactionEntity(
            id = 42,
            amount = 500.0,
            direction = TransactionDirection.DEBIT,
            occurredAtMillis = originalTime,
            merchant = "Supermarket",
            category = TransactionCategory.SHOPPING,
            accountId = 1L,
            sourceSender = "MANUAL",
            smsBody = null,
            confidence = 1.0,
            fingerprint = "fp42",
            status = TransactionStatus.POSTED,
        )

        val draft = TransactionDraft(
            amount = 550.0,
            direction = TransactionDirection.DEBIT,
            merchant = "Supermarket Superstore",
            category = TransactionCategory.SHOPPING,
            accountId = 1L,
            note = "Updated groceries",
            occurredAtMillis = updatedTime,
            countsTowardBudget = true,
        )

        val updated = existing.copy(
            amount = draft.amount,
            direction = draft.direction,
            occurredAtMillis = draft.occurredAtMillis,
            merchant = draft.merchant.trim(),
            category = draft.category,
            accountId = draft.accountId,
            confidence = 1.0,
            status = TransactionStatus.POSTED,
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            countsTowardBudget = draft.countsTowardBudget,
        )

        assertEquals(updatedTime, updated.occurredAtMillis)
        assertEquals(550.0, updated.amount, 0.001)
        assertEquals("Supermarket Superstore", updated.merchant)
    }
}
