package com.suitanglu.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 录音实时状态，前台服务与 UI 共享 */
data class RecordingState(
    val recordingId: Long,
    val title: String,
    val filePath: String,
    val startTime: Long,
    val elapsedMs: Long,
    val paused: Boolean,
    val interrupted: Boolean,
    val interruptReason: String? = null,
    val markerCount: Int = 0,
)

object RecordingBus {
    private val _state = MutableStateFlow<RecordingState?>(null)
    val state: StateFlow<RecordingState?> = _state

    fun update(state: RecordingState?) {
        _state.value = state
    }
}
