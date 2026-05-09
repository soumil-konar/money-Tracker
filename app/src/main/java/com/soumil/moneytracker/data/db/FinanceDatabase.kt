package com.soumil.moneytracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        SubscriptionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(FinanceTypeConverters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun subscriptionDao(): SubscriptionDao

    companion object {
        fun create(context: Context): FinanceDatabase =
            Room.databaseBuilder(
                context,
                FinanceDatabase::class.java,
                "money-tracker.db",
            ).build()
    }
}

