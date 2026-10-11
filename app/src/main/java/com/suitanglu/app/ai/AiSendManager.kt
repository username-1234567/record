package com.suitanglu.app.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.suitanglu.app.data.SettingsRepository
import com.suitanglu.app.data.db.AppDatabase
import com.suitanglu.app.data.db.AiRequestLog
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.util.ImageUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** 流式输出实时状态（供 UI 直接展示正在生成的回复） */
data class StreamingState(val markerId: Long, val text: String)

class AiSendManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _streaming = MutableStateFlow<StreamingState?>(null)
    val streaming: StateFlow<StreamingState?> = _streaming

    private val _activeRecordingId = MutableStateFlow<Long?>(null)
    val activeRecordingId: StateFlow<Long?> = _activeRecordingId

    /** 录音结束后自动发送（pending + failed） */
    fun enqueueAutoSend(recordingId: Long) {
        val request = OneTimeWorkRequestBuilder<AiSendWorker>()
            .setInputData(workDataOf(AiSendWorker.KEY_RECORDING_ID to recordingId))
            .setConstraints(Constraints.Builder().build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("ai_send_$recordingId", ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * 发送队列。
     * @param onlyFailed true=重发失败；false=全部重发（failed + pending）
     * 某条失败后停止后续发送。
     */
    suspend fun sendQueue(recordingId: Long, onlyFailed: Boolean): SendResult {
        if (!busy.compareAndSet(false, true)) {
            return SendResult(0, 0, 0, busy = true)
        }
        _activeRecordingId.value = recordingId
        try {
            val markers = db.markerDao().getByRecording(recordingId)
                .filter { if (onlyFailed) it.aiStatus == Marker.AI_FAILED else it.aiStatus in setOf(Marker.AI_FAILED, Marker.AI_PENDING) }
            var ok = 0
            var failed = 0
            var skipped = 0
            for (marker in markers) {
                val success = try { sendMarker(marker) } catch (e: Exception) {
                    markFailed(marker, e.message ?: e.javaClass.simpleName)
                    failed++
                    false
                }
                if (success) {
                    ok++
                } else {
                    // 某条失败后跳过后续自动发送
                    skipped = markers.size - ok - failed
                    break
                }
            }
            return SendResult(markers.size, ok, failed, skipped = skipped)
        } finally {
            _activeRecordingId.value = null
            _streaming.value = null
            busy.set(false)
        }
    }

    /** 发送单条标记；失败抛异常 */
    private suspend fun sendMarker(marker: Marker): Boolean {
        val now = System.currentTimeMillis()
        db.markerDao().updateAi(marker.id, Marker.AI_SENDING, null, null, now)
        val logId = db.aiRequestLogDao().insert(
            AiRequestLog(markerId = marker.id, model = currentModel(), prompt = currentPrompt(), status = "sending"),
        )
        try {
            val images = db.markerImageDao().getByMarker(marker.id).map { File(it.filePath) }.filter { it.exists() }
            if (images.isEmpty()) throw IllegalStateException("标记下没有图片")
            val g = settings.generalConfigNow()
            val ai = settings.aiConfigNow()
            if (ai.apiKey.isBlank()) throw IllegalStateException("未配置 API Key，请到设置页填写")
            val client = DeepSeekClient(ai, g.compressImages, g.compressQuality)
            var buffer = StringBuilder()
            var lastFlush = 0L
            val response = client.chat(ai.prompt, images) { delta ->
                buffer.append(delta)
                val t = System.currentTimeMillis()
                if (t - lastFlush > 400) {
                    lastFlush = t
                    _streaming.value = StreamingState(marker.id, buffer.toString())
                }
            }
            val t = System.currentTimeMillis()
            db.markerDao().updateAi(marker.id, Marker.AI_SUCCESS, response, null, t)
            db.aiRequestLogDao().finish(logId, "success", response.take(20000), null, t)
            return true
        } catch (e: Exception) {
            markFailed(marker, e.message ?: e.javaClass.simpleName)
            db.aiRequestLogDao().finish(
                logId, "failed", null, (e.message ?: e.javaClass.simpleName).take(2000),
                System.currentTimeMillis(),
            )
            return false
        }
    }

    private suspend fun markFailed(marker: Marker, error: String) {
        db.markerDao().updateAi(marker.id, Marker.AI_FAILED, null, error, System.currentTimeMillis())
    }

    private suspend fun currentModel() = settings.aiConfigNow().model
    private suspend fun currentPrompt() = settings.aiConfigNow().prompt

    suspend fun sendSingle(markerId: Long): Boolean {
        val marker = db.markerDao().getById(markerId) ?: return false
        return try {
            sendMarker(marker)
        } catch (_: Exception) {
            false
        }
    }
}

data class SendResult(val total: Int, val success: Int, val failed: Int, val skipped: Int = 0, val busy: Boolean = false) {
    fun message(): String = when {
        busy -> "已有发送任务在进行中，请稍后"
        total == 0 -> "没有需要发送的标记"
        failed == 0 -> "发送完成：$success 条成功"
        else -> "第 ${success + 1} 条发送失败已停止${if (skipped > 0) "，跳过 $skipped 条" else ""}，成功 $success 条。网络良好时请重试"
    }
}
