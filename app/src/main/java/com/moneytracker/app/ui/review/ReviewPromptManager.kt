package com.moneytracker.app.ui.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.moneytracker.app.data.local.ReviewPreferences
import com.moneytracker.app.data.repo.FinanceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ReviewPromptManager(
    private val context: Context,
    private val reviewPreferences: ReviewPreferences,
    private val repository: FinanceRepository,
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context.applicationContext),
) {

    suspend fun isEligibleForReview(
        isUnderBudgetAtMonthClose: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = withContext(Dispatchers.IO) {
        val installTime = reviewPreferences.appInstallTimestamp.value
        val daysSinceInstall = TimeUnit.MILLISECONDS.toDays(nowMillis - installTime)
        if (daysSinceInstall < MIN_INSTALL_DAYS) {
            Log.d(TAG, "In-app review ineligible: app installed $daysSinceInstall days ago (min $MIN_INSTALL_DAYS days required).")
            return@withContext false
        }

        val lastPromptTime = reviewPreferences.lastReviewPromptTimestamp.value
        if (lastPromptTime > 0L) {
            val daysSinceLastPrompt = TimeUnit.MILLISECONDS.toDays(nowMillis - lastPromptTime)
            if (daysSinceLastPrompt < COOLDOWN_DAYS) {
                Log.d(TAG, "In-app review ineligible: cooldown active ($daysSinceLastPrompt days elapsed, min $COOLDOWN_DAYS required).")
                return@withContext false
            }
        }

        val distinctDays = repository.getDistinctTransactionDaysCount()
        val has14DistinctDays = distinctDays >= MIN_DISTINCT_DAYS
        val hasDelightMilestone = isUnderBudgetAtMonthClose || has14DistinctDays

        if (!hasDelightMilestone) {
            Log.d(TAG, "In-app review ineligible: milestone not met (distinctDays=$distinctDays, underBudgetAtMonthClose=$isUnderBudgetAtMonthClose).")
            return@withContext false
        }

        true
    }

    fun launchReviewIfEligible(
        activity: Activity,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
        isUnderBudgetAtMonthClose: Boolean = false,
        onComplete: () -> Unit = {},
    ) {
        scope.launch {
            val eligible = isEligibleForReview(isUnderBudgetAtMonthClose)
            if (!eligible) {
                onComplete()
                return@launch
            }

            // Record timestamp immediately upon prompt to prevent quota burnout
            reviewPreferences.setLastReviewPromptTimestamp(System.currentTimeMillis())

            val request = reviewManager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener {
                        onComplete()
                    }
                } else {
                    Log.w(TAG, "Review flow request failed: ${task.exception?.message}")
                    onComplete()
                }
            }
        }
    }

    companion object {
        private const val TAG = "ReviewPromptManager"
        const val MIN_INSTALL_DAYS = 14L
        const val COOLDOWN_DAYS = 90L
        const val MIN_DISTINCT_DAYS = 14
    }
}
