package com.suitanglu.app.ui.screens.recording

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.CameraMode
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.service.RecordingBus
import com.suitanglu.app.service.RecordingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RecordingViewModel(app: Application) : AndroidViewModel(app) {

    private val container = AppGraph.get(app)

    val recordingState = RecordingBus.state

    val generalConfig: StateFlow<com.suitanglu.app.data.GeneralConfig> = container.settings.generalConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.suitanglu.app.data.GeneralConfig())

    private val _pendingImages = MutableStateFlow<List<File>>(emptyList())
    val pendingImages: StateFlow<List<File>> = _pendingImages

    private val _markerCount = MutableStateFlow(0)
    val markerCount: StateFlow<Int> = _markerCount

    val streaming = container.aiSendManager.streaming

    private val recId: Long get() = RecordingBus.state.value?.recordingId ?: 0L

    init {
        viewModelScope.launch {
            // 只在录音 ID 变化时查库，避免跟随计时器每 500ms 查一次
            RecordingBus.state
                .map { it?.recordingId }
                .distinctUntilChanged()
                .collect { id ->
                    _markerCount.value = if (id != null) {
                        container.db.markerDao().countByRecording(id)
                    } else 0
                }
        }
    }

    /** 裁剪输出是 FileProvider 的 content:// Uri，统一复制到待用目录再使用 */
    fun addCroppedImage(uri: Uri) {
        viewModelScope.launch {
            val target = withContext(Dispatchers.IO) {
                val dir = File(getApplication<Application>().cacheDir, "pending").apply { mkdirs() }
                val f = File(dir, "P_${System.currentTimeMillis()}.jpg")
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        f.outputStream().use { input.copyTo(it) }
                    }
                }
                if (f.exists() && f.length() > 0) f else null
            } ?: return@launch
            _pendingImages.value = _pendingImages.value + target
        }
    }

    fun removePending(file: File) {
        _pendingImages.value = _pendingImages.value - file
        file.delete()
    }

    /** 保存标记：图片移动到 images 目录，立即入库 */
    fun saveMarker(text: String, onDone: (Boolean) -> Unit) {
        val state = RecordingBus.state.value
        if (state == null) {
            onDone(false)
            return
        }
        val images = _pendingImages.value.filter { it.exists() }
        if (images.isEmpty()) {
            onDone(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val imagesDir = container.recordingRepository.imagesDir
            val moved = images.map { f ->
                val target = File(imagesDir, "marker_${state.recordingId}_${System.currentTimeMillis()}_${f.name.hashCode()}.jpg")
                if (f.renameTo(target)) target else {
                    f.copyTo(target, overwrite = true); f.delete(); target
                }
            }
            container.recordingRepository.saveMarker(
                recordingId = state.recordingId,
                offsetMs = state.elapsedMs,
                userText = text.trim().takeIf { it.isNotEmpty() },
                imageFiles = moved,
            )
            withContext(Dispatchers.Main) {
                _pendingImages.value = emptyList()
                _markerCount.value += 1
                onDone(true)
            }
        }
    }

    fun pauseOrResume() {
        val ctx = getApplication<Application>()
        val s = RecordingBus.state.value ?: return
        if (s.paused) RecordingService.sendAction(ctx, RecordingService.ACTION_RESUME)
        else RecordingService.sendAction(ctx, RecordingService.ACTION_PAUSE)
    }

    fun stop(onStopped: (Long) -> Unit) {
        val ctx = getApplication<Application>()
        val id = recId
        RecordingService.sendAction(ctx, RecordingService.ACTION_STOP)
        onStopped(id)
    }

    fun aiStatusSummary(): String {
        val count = _markerCount.value
        return when {
            count == 0 -> "尚无标记"
            else -> "$count 个标记，录音结束后将自动发送 AI 讲解"
        }
    }

    suspend fun markerStatusCounts(recordingId: Long): Triple<Int, Int, Int> {
        val ms = container.db.markerDao().getByRecording(recordingId)
        return Triple(
            ms.count { it.aiStatus == Marker.AI_SUCCESS },
            ms.count { it.aiStatus == Marker.AI_FAILED },
            ms.count { it.aiStatus == Marker.AI_PENDING || it.aiStatus == Marker.AI_SENDING },
        )
    }

    fun isSystemCamera(mode: String) = mode != CameraMode.IN_APP
}
