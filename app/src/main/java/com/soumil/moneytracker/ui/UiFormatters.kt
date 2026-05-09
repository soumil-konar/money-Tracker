package com.soumil.moneytracker.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val inrFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 0
}

private val compactDateFormatter = DateTimeFormatter.ofPattern("dd MMM")
private val detailedDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")

fun Double.asCurrency(): String = inrFormatter.format(this)

fun Long.asDayMonth(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(compactDateFormatter)

fun Long.asFullDate(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(detailedDateFormatter)

