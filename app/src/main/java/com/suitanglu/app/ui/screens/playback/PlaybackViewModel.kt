package com.suitanglu.app.ui.screens.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.suitanglu.app.AppGraph
import com.suitanglu.app.data.db.Marker
import com.suitanglu.app.data.db.MarkerImage
import com.suitanglu.app.data.db.PauseEvent
import com.suitanglu.app.data.db.Recording
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class PlaybackViewModel(app: Application, val recordingId: Long) : AndroidViewModel(app) {

    private val container = AppGraph.get(app)

    private val _recording = MutableStateFlow<Recording?>(null)
    val recording: StateFlow<Recording?> = _recording

    val markers: StateFlow<List<Marker>> = container.db.markerDao().observeByRecording(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pauseEvents: StateFlow<List<PauseEvent>> = container.db.pauseEventDao().observeByRecording(recordingId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val streaming = container.aiSendManager.streaming

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed

    private val _sendResult = MutableStateFlow<String?>(null)
    val sendResult: StateFlow<String?> = _sendResult

    private var ticker: Job? = null

    init {
        viewModelScope.launch {
            val rec = container.db.recordingDao().getById(recordingId)
            _recording.value = rec
            val file = rec?.filePath?.takeIf { it.isNotBlank() }?.let { File(it) }
            if (file != null && file.exists()) {
                val p = ExoPlayer.Builder(getApplication()).build().apply {
                    setMediaItem(MediaItem.fromUri(file.toURI().toString()))
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _playing.value = isPlaying
                            if (isPlaying) startTicker() else tickOnce()
                        }
                    })
                }
                _player.value = p
                _duration.value = p.duration.coerceAtLeast(0)
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                tickOnce()
                delay(200)
            }
        }
    }

    private fun tickOnce() {
        val p = _player.value ?: return
        _position.value = p.currentPosition.coerceAtLeast(0)
        _duration.value = p.duration.coerceAtLeast(0)
    }

    fun togglePlay() {
        val p = _player.value ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    /** keepPlaying=null 沿用当前状态；true=跳转后继续播；false=跳转后暂停 */
    fun seekTo(ms: Long, keepPlaying: Boolean? = null) {
        val p = _player.value ?: return
        val max = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = ms.coerceIn(0, max)
        val wasPlaying = p.isPlaying
        p.seekTo(target)
        when (keepPlaying) {
            null -> if (wasPlaying && !p.isPlaying) p.play()
            true -> if (!p.isPlaying) p.play()
            false -> if (p.isPlaying) p.pause()
        }
        tickOnce()
    }

    fun skip(deltaMs: Long) {
        val p = _player.value ?: return
        seekTo(p.currentPosition + deltaMs)
    }

    fun setSpeed(v: Float) {
        _speed.value = v
        _player.value?.let { it.playbackParameters = it.playbackParameters.withSpeed(v) }
    }

    /** 点击标记：跳转并沿用点击前的播放状态 */
    fun jumpToMarker(marker: Marker) {
        seekTo(marker.offsetMs, keepPlaying = _player.value?.isPlaying)
    }

    fun resendFailed() {
        viewModelScope.launch {
            _sendResult.value = container.aiSendManager.sendQueue(recordingId, onlyFailed = true).message()
        }
    }

    fun resendAll() {
        viewModelScope.launch {
            _sendResult.value = container.aiSendManager.sendQueue(recordingId, onlyFailed = false).message()
        }
    }

    fun resendOne(markerId: Long) {
        viewModelScope.launch {
            val ok = container.aiSendManager.sendSingle(markerId)
            _sendResult.value = if (ok) "重新发送成功" else "发送失败，请稍后再试"
        }
    }

    suspend fun imagesOf(marker: Marker): List<MarkerImage> =
        container.db.markerImageDao().getByMarker(marker.id)

    fun consumeSendResult() {
        _sendResult.value = null
    }

    override fun onCleared() {
        ticker?.cancel()
        _player.value?.release()
        super.onCleared()
    }
}
