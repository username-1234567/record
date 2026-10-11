package com.suitanglu.app.ai

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class AiSendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recordingId = inputData.getLong(KEY_RECORDING_ID, -1)
        if (recordingId <= 0) return Result.failure()
        val container = (applicationContext as com.suitanglu.app.RecordApp).container
        return try {
            val result = container.aiSendManager.sendQueue(recordingId, onlyFailed = false)
            Result.success(
                workDataOf(
                    "success" to result.success,
                    "failed" to result.failed,
                ),
            )
        } catch (e: Exception) {
            // 标记级失败已在 AiSendManager 内处理；走到这里的异常直接失败，避免无限重试
            Result.failure()
        }
    }

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
    }
}
