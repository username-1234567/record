package com.suitanglu.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "recordings")
data class Recording(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "未命名",
    val courseName: String? = null,
    val courseSlotId: Int? = null,
    val filePath: String,
    val fileHash: String? = null,
    val startTime: Long,
    val endTime: Long? = null,
    val durationMs: Long = 0,
    val status: String = STATUS_RECORDING,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_RECORDING = "recording"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_INTERRUPTED = "interrupted"
        const val STATUS_AUDIO_DELETED = "audio_deleted"
    }
}

@Serializable
@Entity(tableName = "markers")
data class Marker(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val offsetMs: Long,
    val absoluteTime: Long,
    val userText: String? = null,
    val aiStatus: String = AI_PENDING,
    val aiResponse: String? = null,
    val aiError: String? = null,
    val sendOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val AI_PENDING = "pending"
        const val AI_SENDING = "sending"
        const val AI_SUCCESS = "success"
        const val AI_FAILED = "failed"
    }
}

@Serializable
@Entity(tableName = "marker_images")
data class MarkerImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val markerId: Long,
    val filePath: String,
    val fileHash: String? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "pause_events")
data class PauseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long,
    val offsetMs: Long,
    val type: String,
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_USER_PAUSE = "user_pause"
        const val TYPE_AUTO_INTERRUPT = "auto_interrupt"
        const val TYPE_RESUME = "resume"
    }
}

@Serializable
@Entity(tableName = "ai_request_logs")
data class AiRequestLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val markerId: Long,
    val model: String,
    val prompt: String,
    val status: String,
    val response: String? = null,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)
