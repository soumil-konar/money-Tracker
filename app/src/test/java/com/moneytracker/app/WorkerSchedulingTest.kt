package com.moneytracker.app

import com.moneytracker.app.worker.EmailSyncWorker
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkerSchedulingTest {

    @Test
    fun `email sync worker has unique work identifier`() {
        assertEquals("periodic_email_sync", EmailSyncWorker.UNIQUE_WORK_NAME)
    }
}
