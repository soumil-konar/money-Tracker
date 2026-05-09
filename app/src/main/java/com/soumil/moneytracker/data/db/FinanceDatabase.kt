package com.soumil.moneytracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        SubscriptionEntity::class,
        ScheduledTransactionEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(FinanceTypeConverters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun scheduledTransactionDao(): ScheduledTransactionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `scheduled_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `merchant` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `scheduledForMillis` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `accountId` INTEGER,
                        `sourceSender` TEXT NOT NULL,
                        `smsBody` TEXT,
                        `kind` TEXT NOT NULL,
                        `fingerprint` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_scheduled_transactions_fingerprint` ON `scheduled_transactions` (`fingerprint`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scheduled_transactions_scheduledForMillis` ON `scheduled_transactions` (`scheduledForMillis`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_scheduled_transactions_accountId` ON `scheduled_transactions` (`accountId`)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `accounts` ADD COLUMN `institutionName` TEXT",
                )
                db.execSQL(
                    "ALTER TABLE `accounts` ADD COLUMN `cardType` TEXT",
                )
                db.execSQL(
                    "ALTER TABLE `accounts` ADD COLUMN `lastFourDigits` TEXT",
                )
                db.execSQL(
                    "ALTER TABLE `accounts` ADD COLUMN `isRupayCreditCard` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        fun create(context: Context): FinanceDatabase =
            Room.databaseBuilder(
                context,
                FinanceDatabase::class.java,
                "money-tracker.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}

