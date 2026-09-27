package com.moneytracker.app.ui.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory

class ReviewPromptManager(private val context: Context) {

    private val reviewManager = ReviewManagerFactory.create(context.applicationContext)

    fun launchReviewIfEligible(activity: Activity, onComplete: () -> Unit = {}) {
        val request = reviewManager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener { _ ->
                    onComplete()
                }
            } else {
                Log.w(TAG, "Review flow request failed: ${task.exception?.message}")
                onComplete()
            }
        }
    }

    companion object {
        private const val TAG = "ReviewPromptManager"
    }
}
