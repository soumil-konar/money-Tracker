package com.soumil.moneytracker

import android.content.Context
import com.soumil.moneytracker.data.db.FinanceDatabase
import com.soumil.moneytracker.data.repo.FinanceRepository
import com.soumil.moneytracker.parser.SmsParser

class AppContainer(context: Context) {
    private val database = FinanceDatabase.create(context)
    private val parser = SmsParser()

    val repository = FinanceRepository(
        accountDao = database.accountDao(),
        budgetDao = database.budgetDao(),
        scheduledTransactionDao = database.scheduledTransactionDao(),
        subscriptionDao = database.subscriptionDao(),
        transactionDao = database.transactionDao(),
        parser = parser,
    )
}

