package com.suitanglu.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.suitanglu.app.R
import com.suitanglu.app.RecordApp
import com.suitanglu.app.data.db.PauseEvent
import com.suitanglu.app.data.db.Recording
import com.suitanglu.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "com.suitanglu.app.action.START"
        const val ACTION_PAUSE = "com.suitanglu.app.action.PAUSE"
        const val ACTION_RESUME = "com.suitanglu.app.action.RESUME"
        const val ACTION_STOP = "com.suitanglu.app.action.STOP"
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply { action = ACTION_START }
            context.startForegroundService(intent)
        }

        fun sendAction(context: Context, action: String) {
            context.startService(Intent(context, RecordingService::class.java).apply { this.action = action })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recorder: MediaRecorder? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null
    private var timerJob: Job? = null
    private var recordingId: Long = 0
    private var startTime: Long = 0
    private var segmentStart: Long = 0
    private var accumulatedMs: Long = 0
    private var paused = false
    private var interrupted = false
    private var interruptReason: String? = null
    private var sizeLimitBytes = 0L
    private val busy = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseByUser()
            ACTION_RESUME -> resumeByUser()
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "录音状态", NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startRecording() {
        if (recorder != null || !busy.compareAndSet(false, true)) return
        stopping.set(false)
        val container = (application as RecordApp).container
        scope.launch {
            try {
                audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                sizeLimitBytes = container.settings.generalConfigNow().sizeLimitMb.takeIf { it > 0 }
                    ?.let { it.toLong() * 1024 * 1024 } ?: 0L

                startTime = System.currentTimeMillis()
                val rec = container.recordingRepository.startRecording(startTime)
                recordingId = rec.id
                File(rec.filePath).parentFile?.mkdirs()

                recorder = MediaRecorder(applicationContext).apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    // 设备支持的最高音质：AAC 48kHz 立体声 256kbps
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(48000)
                    setAudioEncodingBitRate(256000)
                    setAudioChannels(2)
                    setOutputFile(rec.filePath)
                    prepare()
                    start()
                }
                segmentStart = System.currentTimeMillis()
                requestAudioFocus()
                RecordingBus.update(
                    RecordingState(
                        recordingId = recordingId,
                        title = rec.title,
                        filePath = rec.filePath,
                        startTime = startTime,
                        elapsedMs = 0,
                        paused = false,
                        interrupted = false,
                    ),
                )
                startForegroundCompat(buildNotification(0, false))
                startTimer()
            } catch (e: Exception) {
                busy.set(false)
                releaseRecorder()
                // startForegroundService 启动后必须先进入前台再停止
                runCatching { startForegroundCompat(buildNotification(0, true)) }
                scope.launch {
                    runCatching {
                        container.recordingRepository.insertPauseEvent(
                            recordingId, 0, PauseEvent.TYPE_AUTO_INTERRUPT, "录音启动失败：${e.message}",
                        )
                    }
                }
                RecordingBus.update(null)
                delay(300)
                stopSelf()
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            var lastDbSave = 0L
            var lastSizeCheck = 0L
            while (isActive && recorder != null) {
                val now = System.currentTimeMillis()
                val elapsed = if (!paused) accumulatedMs + (now - segmentStart) else accumulatedMs
                RecordingBus.update(currentState().copy(elapsedMs = elapsed))
                if (now - lastDbSave > 10_000) {
                    // 进程被杀时用于恢复
                    (application as RecordApp).container.db.recordingDao()
                        .updateDuration(recordingId, elapsed, now)
                    lastDbSave = now
                }
                if (sizeLimitBytes > 0 && now - lastSizeCheck > 5_000) {
                    val size = File(currentState().filePath).length()
                    if (size >= sizeLimitBytes) {
                        stopRecording()
                        return@launch
                    }
                    lastSizeCheck = now
                }
                delay(500)
            }
        }
    }

    private fun currentState(): RecordingState =
        RecordingBus.state.value ?: RecordingState(recordingId, "", "", startTime, 0, paused, interrupted)

    private fun pauseByUser() {
        doPause(PauseEvent.TYPE_USER_PAUSE, null)
    }

    private fun doPause(type: String, reason: String?) {
        val rec = recorder ?: return
        if (paused) return
        try {
            rec.pause()
            val now = System.currentTimeMillis()
            accumulatedMs += now - segmentStart
            paused = true
            if (type == PauseEvent.TYPE_AUTO_INTERRUPT) interrupted = true
            interruptReason = reason
            scope.launch {
                (application as RecordApp).container.recordingRepository.insertPauseEvent(
                    recordingId, accumulatedMs, type, reason,
                )
            }
            updateNotification()
        } catch (_: Exception) {
        }
    }

    private fun resumeByUser() {
        doResume(false)
    }

    private fun doResume(afterInterrupt: Boolean) {
        val rec = recorder ?: return
        if (!paused) return
        try {
            rec.resume()
            paused = false
            segmentStart = System.currentTimeMillis()
            if (afterInterrupt) interrupted = false
            interruptReason = null
            scope.launch {
                (application as RecordApp).container.recordingRepository.insertPauseEvent(
                    recordingId, accumulatedMs, PauseEvent.TYPE_RESUME, null,
                )
            }
            updateNotification()
        } catch (_: Exception) {
        }
    }

    private fun stopRecording() {
        if (recorder == null) {
            stopSelf()
            return
        }
        if (!stopping.compareAndSet(false, true)) return
        val now = System.currentTimeMillis()
        val elapsed = if (!paused) accumulatedMs + (now - segmentStart) else accumulatedMs
        val id = recordingId
        val interruptedFlag = interrupted
        releaseFocus()
        releaseRecorder()
        timerJob?.cancel()
        RecordingBus.update(null)
        scope.launch {
            val container = (application as RecordApp).container
            container.recordingRepository.finalizeRecording(id, now, elapsed, interruptedFlag)
            if (elapsed > 0) {
                container.aiSendManager.enqueueAutoSend(id)
            }
            stopSelf()
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> doPause(
                        PauseEvent.TYPE_AUTO_INTERRUPT,
                        when (change) {
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "其他应用短暂占用音频"
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "来电或其他应用打断"
                            else -> "音频焦点丢失"
                        },
                    )
                    AudioManager.AUDIOFOCUS_GAIN -> doResume(afterInterrupt = true)
                }
            }
            .build()
        am.requestAudioFocus(focusRequest!!)
    }

    private fun releaseFocus() {
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun releaseRecorder() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val s = currentState()
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID, buildNotification(s.elapsedMs, s.paused),
        )
    }

    private fun buildNotification(elapsedMs: Long, isPaused: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pauseOrResume = if (isPaused) {
            NotificationCompat.Action(
                R.drawable.ic_launcher_foreground, "继续",
                actionPendingIntent(ACTION_RESUME, 1),
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_launcher_foreground, "暂停",
                actionPendingIntent(ACTION_PAUSE, 2),
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (isPaused) "录音已暂停" else "正在录音")
            .setContentText(formatElapsed(elapsedMs))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(pauseOrResume)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_launcher_foreground, "停止",
                    actionPendingIntent(ACTION_STOP, 3),
                ),
            )
            .build()
    }

    private fun actionPendingIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, RecordingService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun formatElapsed(ms: Long): String {
        val total = ms / 1000
        return String.format("%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
    }

    override fun onDestroy() {
        timerJob?.cancel()
        scope.cancel()
        releaseFocus()
        releaseRecorder()
        RecordingBus.update(null)
        super.onDestroy()
    }
}
