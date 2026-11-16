package com.dayline.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.focus.FocusTimerController
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: ReminderSync,
    private val focus: FocusTimerController,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        sync.syncAll()
        focus.restore()
        focus.checkDue()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

}
