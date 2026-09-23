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
        TransactionFtsEntity::class,
        TransactionEmbeddingEntity::class,
        BudgetEntity::class,
        SubscriptionEntity::class,
        ScheduledTransactionEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(FinanceTypeConverters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun transactionEmbeddingDao(): TransactionEmbeddingDao
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `countsTowardBudget` INTEGER NOT NULL DEFAULT 1",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `transactions_fts` USING FTS4(
                        `merchant`,
                        `note`,
                        `sourceSender`,
                        `smsBody`,
                        content=`transactions`
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `transactions_fts`(`docid`, `merchant`, `note`, `sourceSender`, `smsBody`)
                    SELECT `id`, `merchant`, `note`, `sourceSender`, `smsBody` FROM `transactions`
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `transaction_embeddings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `documentText` TEXT NOT NULL,
                        `embeddingCsv` TEXT NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_embeddings_transactionId` ON `transaction_embeddings` (`transactionId`)",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure all existing rows are indexed in FTS
                db.execSQL(
                    """
                    INSERT OR REPLACE INTO `transactions_fts`(`docid`, `merchant`, `note`, `sourceSender`, `smsBody`)
                    SELECT `id`, `merchant`, `note`, `sourceSender`, `smsBody` FROM `transactions`
                    """.trimIndent(),
                )
                // Install triggers to automatically synchronize transactions with transactions_fts
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `transactions_ai` AFTER INSERT ON `transactions` BEGIN
                        INSERT INTO `transactions_fts`(`docid`, `merchant`, `note`, `sourceSender`, `smsBody`)
                        VALUES (new.`id`, new.`merchant`, new.`note`, new.`sourceSender`, new.`smsBody`);
                    END;
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `transactions_ad` AFTER DELETE ON `transactions` BEGIN
                        DELETE FROM `transactions_fts` WHERE `docid` = old.`id`;
                    END;
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TRIGGER IF NOT EXISTS `transactions_au` AFTER UPDATE ON `transactions` BEGIN
                        DELETE FROM `transactions_fts` WHERE `docid` = old.`id`;
                        INSERT INTO `transactions_fts`(`docid`, `merchant`, `note`, `sourceSender`, `smsBody`)
                        VALUES (new.`id`, new.`merchant`, new.`note`, new.`sourceSender`, new.`smsBody`);
                    END;
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `accounts` ADD COLUMN `currentBalance` REAL NOT NULL DEFAULT 0.0",
                )
                db.execSQL(
                    "ALTER TABLE `accounts` ADD COLUMN `balanceUpdatedAtMillis` INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `availableBalance` REAL",
                )
            }
        }

        fun create(context: Context): FinanceDatabase =
            Room.databaseBuilder(
                context,
                FinanceDatabase::class.java,
                "money-tracker.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}

