package com.moneytracker.app.backup

import android.os.Build
import com.moneytracker.app.data.db.AccountEntity
import com.moneytracker.app.data.db.BudgetEntity
import com.moneytracker.app.data.db.FinanceDatabase
import com.moneytracker.app.data.db.ScheduledTransactionEntity
import com.moneytracker.app.data.db.SubscriptionEntity
import com.moneytracker.app.data.db.TransactionEntity
import com.moneytracker.app.data.model.AccountKind
import com.moneytracker.app.data.model.CardType
import com.moneytracker.app.data.model.ScheduledTransactionKind
import com.moneytracker.app.data.model.SubscriptionState
import com.moneytracker.app.data.model.TransactionCategory
import com.moneytracker.app.data.model.TransactionDirection
import com.moneytracker.app.data.model.TransactionStatus
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

sealed class BackupRestoreResult {
    data class Success(
        val accountsCount: Int,
        val transactionsCount: Int,
        val budgetsCount: Int,
        val subscriptionsCount: Int,
    ) : BackupRestoreResult()

    data class Error(val message: String) : BackupRestoreResult()
}

object BackupManager {

    private val MAGIC_BYTES = byteArrayOf('M'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val FORMAT_VERSION: Byte = 1
    private const val SALT_SIZE_BYTES = 16
    private const val IV_SIZE_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH_BITS = 256

    /**
     * Encrypts plaintext bytes using AES-256-GCM and PBKDF2WithHmacSHA256 key derivation.
     */
    fun encryptData(plaintext: ByteArray, passphrase: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_SIZE_BYTES).also { random.nextBytes(it) }

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

        val ciphertext = cipher.doFinal(plaintext)

        val buffer = ByteBuffer.allocate(MAGIC_BYTES.size + 1 + SALT_SIZE_BYTES + IV_SIZE_BYTES + ciphertext.size)
        buffer.put(MAGIC_BYTES)
        buffer.put(FORMAT_VERSION)
        buffer.put(salt)
        buffer.put(iv)
        buffer.put(ciphertext)
        return buffer.array()
    }

    /**
     * Decrypts ciphertext bytes using AES-256-GCM and PBKDF2WithHmacSHA256 key derivation.
     */
    fun decryptData(encryptedBytes: ByteArray, passphrase: String): ByteArray {
        val minHeaderSize = MAGIC_BYTES.size + 1 + SALT_SIZE_BYTES + IV_SIZE_BYTES
        if (encryptedBytes.size < minHeaderSize + 16) {
            throw IllegalArgumentException("Corrupt backup file: size too small.")
        }

        val buffer = ByteBuffer.wrap(encryptedBytes)
        val magic = ByteArray(MAGIC_BYTES.size)
        buffer.get(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw IllegalArgumentException("Invalid backup file: header magic mismatch.")
        }

        val version = buffer.get()
        if (version != FORMAT_VERSION) {
            throw IllegalArgumentException("Unsupported backup format version: $version.")
        }

        val salt = ByteArray(SALT_SIZE_BYTES)
        buffer.get(salt)

        val iv = ByteArray(IV_SIZE_BYTES)
        buffer.get(iv)

        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Serializes database tables into an encrypted backup payload.
     */
    suspend fun createEncryptedBackup(
        accountDao: com.moneytracker.app.data.db.AccountDao,
        transactionDao: com.moneytracker.app.data.db.TransactionDao,
        budgetDao: com.moneytracker.app.data.db.BudgetDao,
        subscriptionDao: com.moneytracker.app.data.db.SubscriptionDao,
        passphrase: String,
    ): ByteArray {
        val rootJson = JSONObject()

        val metaJson = JSONObject().apply {
            put("app", "MoneyTracker")
            put("schemaVersion", 1)
            put("exportTimestamp", System.currentTimeMillis())
            put("device", Build.MODEL ?: "Android")
        }
        rootJson.put("metadata", metaJson)

        // 1. Accounts
        val accounts = accountDao.getAccounts()
        val accountsArray = JSONArray()
        for (acc in accounts) {
            accountsArray.put(JSONObject().apply {
                put("id", acc.id)
                put("name", acc.name)
                put("kind", acc.kind.name)
                put("institutionName", acc.institutionName)
                put("cardType", acc.cardType?.name)
                put("lastFourDigits", acc.lastFourDigits)
                put("isRupayCreditCard", acc.isRupayCreditCard)
                put("isSystemGenerated", acc.isSystemGenerated)
                put("currentBalance", acc.currentBalance)
                put("balanceUpdatedAtMillis", acc.balanceUpdatedAtMillis)
                put("balanceProofSnippet", acc.balanceProofSnippet)
                put("balanceProofSource", acc.balanceProofSource)
                put("isBalanceVerified", acc.isBalanceVerified)
            })
        }
        rootJson.put("accounts", accountsArray)

        // 2. Transactions
        val transactions = transactionDao.getAllTransactions()
        val txsArray = JSONArray()
        for (tx in transactions) {
            txsArray.put(JSONObject().apply {
                put("id", tx.id)
                put("amount", tx.amount)
                put("direction", tx.direction.name)
                put("occurredAtMillis", tx.occurredAtMillis)
                put("merchant", tx.merchant)
                put("category", tx.category.name)
                put("accountId", tx.accountId)
                put("sourceSender", tx.sourceSender)
                put("smsBody", tx.smsBody)
                put("confidence", tx.confidence)
                put("fingerprint", tx.fingerprint)
                put("status", tx.status.name)
                put("note", tx.note)
                put("countsTowardBudget", tx.countsTowardBudget)
                put("availableBalance", tx.availableBalance)
                put("createdAtMillis", tx.createdAtMillis)
            })
        }
        rootJson.put("transactions", txsArray)

        // 3. Budgets
        val budgets = budgetDao.getAll()
        val budgetsArray = JSONArray()
        for (b in budgets) {
            budgetsArray.put(JSONObject().apply {
                put("id", b.id)
                put("monthKey", b.monthKey)
                put("category", b.category?.name)
                put("amountLimit", b.amountLimit)
            })
        }
        rootJson.put("budgets", budgetsArray)

        // 4. Subscriptions
        val subscriptions = subscriptionDao.getAll()
        val subsArray = JSONArray()
        for (s in subscriptions) {
            subsArray.put(JSONObject().apply {
                put("id", s.id)
                put("merchant", s.merchant)
                put("amount", s.amount)
                put("billingCycleDays", s.billingCycleDays)
                put("nextDueAtMillis", s.nextDueAtMillis)
                put("accountId", s.accountId)
                put("state", s.state.name)
            })
        }
        rootJson.put("subscriptions", subsArray)

        val plaintextBytes = rootJson.toString().toByteArray(Charsets.UTF_8)
        return encryptData(plaintextBytes, passphrase)
    }

    /**
     * Decrypts and restores records into the local database, preserving relational integrity.
     */
    suspend fun restoreEncryptedBackup(
        backupBytes: ByteArray,
        passphrase: String,
        accountDao: com.moneytracker.app.data.db.AccountDao,
        transactionDao: com.moneytracker.app.data.db.TransactionDao,
        budgetDao: com.moneytracker.app.data.db.BudgetDao,
        subscriptionDao: com.moneytracker.app.data.db.SubscriptionDao,
    ): BackupRestoreResult {
        val decryptedBytes = try {
            decryptData(backupBytes, passphrase)
        } catch (e: GeneralSecurityException) {
            return BackupRestoreResult.Error("Incorrect passphrase or corrupted backup payload.")
        } catch (e: Exception) {
            return BackupRestoreResult.Error(e.message ?: "Failed to decrypt backup.")
        }

        val json = try {
            JSONObject(String(decryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            return BackupRestoreResult.Error("Invalid backup JSON payload.")
        }

        val accountIdMap = mutableMapOf<Long, Long>()
        var accountsRestored = 0
        var txsRestored = 0
        var budgetsRestored = 0
        var subsRestored = 0

        // 1. Restore Accounts
        val existingAccounts = accountDao.getAccounts()
        val accountsArray = json.optJSONArray("accounts") ?: JSONArray()
        for (i in 0 until accountsArray.length()) {
            val accJson = accountsArray.getJSONObject(i)
            val oldId = accJson.getLong("id")
            val name = accJson.getString("name")
            val kind = AccountKind.valueOf(accJson.getString("kind"))
            val lastFour = if (accJson.has("lastFourDigits") && !accJson.isNull("lastFourDigits")) accJson.getString("lastFourDigits") else null

            // Check if matching account already exists locally
            val matched = existingAccounts.firstOrNull {
                it.kind == kind && it.name.equals(name, ignoreCase = true) &&
                    (lastFour == null || it.lastFourDigits == lastFour)
            }

            if (matched != null) {
                accountIdMap[oldId] = matched.id
            } else {
                val newAccount = AccountEntity(
                    name = name,
                    kind = kind,
                    institutionName = if (accJson.has("institutionName") && !accJson.isNull("institutionName")) accJson.getString("institutionName") else null,
                    cardType = if (accJson.has("cardType") && !accJson.isNull("cardType")) CardType.valueOf(accJson.getString("cardType")) else null,
                    lastFourDigits = lastFour,
                    isRupayCreditCard = accJson.optBoolean("isRupayCreditCard", false),
                    isSystemGenerated = accJson.optBoolean("isSystemGenerated", false),
                    currentBalance = accJson.optDouble("currentBalance", 0.0),
                    balanceUpdatedAtMillis = if (accJson.has("balanceUpdatedAtMillis") && !accJson.isNull("balanceUpdatedAtMillis")) accJson.getLong("balanceUpdatedAtMillis") else null,
                    balanceProofSnippet = if (accJson.has("balanceProofSnippet") && !accJson.isNull("balanceProofSnippet")) accJson.getString("balanceProofSnippet") else null,
                    balanceProofSource = if (accJson.has("balanceProofSource") && !accJson.isNull("balanceProofSource")) accJson.getString("balanceProofSource") else null,
                    isBalanceVerified = accJson.optBoolean("isBalanceVerified", false),
                )
                val newId = accountDao.insert(newAccount)
                if (newId != -1L) {
                    accountIdMap[oldId] = newId
                    accountsRestored++
                }
            }
        }

        // 2. Restore Transactions
        val txsArray = json.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txsArray.length()) {
            val txJson = txsArray.getJSONObject(i)
            val fingerprint = txJson.getString("fingerprint")
            if (transactionDao.fingerprintExists(fingerprint)) continue

            val oldAccountId = if (txJson.has("accountId") && !txJson.isNull("accountId")) txJson.getLong("accountId") else null
            val remappedAccountId = oldAccountId?.let { accountIdMap[it] ?: it }

            val tx = TransactionEntity(
                amount = txJson.getDouble("amount"),
                direction = TransactionDirection.valueOf(txJson.getString("direction")),
                occurredAtMillis = txJson.getLong("occurredAtMillis"),
                merchant = txJson.getString("merchant"),
                category = TransactionCategory.valueOf(txJson.getString("category")),
                accountId = remappedAccountId,
                sourceSender = txJson.getString("sourceSender"),
                smsBody = if (txJson.has("smsBody") && !txJson.isNull("smsBody")) txJson.getString("smsBody") else null,
                confidence = txJson.optDouble("confidence", 1.0),
                fingerprint = fingerprint,
                status = TransactionStatus.valueOf(txJson.getString("status")),
                note = if (txJson.has("note") && !txJson.isNull("note")) txJson.getString("note") else null,
                countsTowardBudget = txJson.optBoolean("countsTowardBudget", true),
                availableBalance = if (txJson.has("availableBalance") && !txJson.isNull("availableBalance")) txJson.getDouble("availableBalance") else null,
                createdAtMillis = txJson.optLong("createdAtMillis", System.currentTimeMillis()),
            )
            val insertedId = transactionDao.insert(tx)
            if (insertedId != -1L) {
                txsRestored++
            }
        }

        // 3. Restore Budgets
        val budgetsArray = json.optJSONArray("budgets") ?: JSONArray()
        for (i in 0 until budgetsArray.length()) {
            val bJson = budgetsArray.getJSONObject(i)
            val category = if (bJson.has("category") && !bJson.isNull("category")) TransactionCategory.valueOf(bJson.getString("category")) else null
            val budget = BudgetEntity(
                monthKey = bJson.getString("monthKey"),
                category = category,
                amountLimit = if (bJson.has("amountLimit")) bJson.getDouble("amountLimit") else bJson.optDouble("amount", 0.0),
            )
            budgetDao.insert(budget)
            budgetsRestored++
        }

        // 4. Restore Subscriptions
        val subsArray = json.optJSONArray("subscriptions") ?: JSONArray()
        for (i in 0 until subsArray.length()) {
            val sJson = subsArray.getJSONObject(i)
            val merchant = sJson.getString("merchant")
            if (subscriptionDao.findByMerchant(merchant) != null) continue

            val oldAccountId = if (sJson.has("accountId") && !sJson.isNull("accountId")) sJson.getLong("accountId") else null
            val remappedAccountId = oldAccountId?.let { accountIdMap[it] ?: it }

            val sub = SubscriptionEntity(
                merchant = merchant,
                amount = sJson.getDouble("amount"),
                billingCycleDays = sJson.getInt("billingCycleDays"),
                nextDueAtMillis = sJson.getLong("nextDueAtMillis"),
                accountId = remappedAccountId,
                state = SubscriptionState.valueOf(sJson.getString("state")),
            )
            subscriptionDao.insert(sub)
            subsRestored++
        }

        return BackupRestoreResult.Success(
            accountsCount = accountsRestored,
            transactionsCount = txsRestored,
            budgetsCount = budgetsRestored,
            subscriptionsCount = subsRestored,
        )
    }
}
