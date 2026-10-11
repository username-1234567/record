package com.suitanglu.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings WHERE deletedAt IS NULL AND status != 'audio_deleted' ORDER BY startTime DESC")
    fun observeActive(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE deletedAt IS NULL ORDER BY startTime DESC")
    suspend fun getAllActive(): List<Recording>

    @Query("SELECT * FROM recordings")
    suspend fun getAll(): List<Recording>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): Recording?

    @Query("SELECT * FROM recordings WHERE status = 'recording'")
    suspend fun getUnfinished(): List<Recording>

    @Query("SELECT * FROM recordings WHERE fileHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): Recording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Query("UPDATE recordings SET durationMs = :durationMs, updatedAt = :now WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long, now: Long)

    @Query("UPDATE recordings SET fileHash = :hash, updatedAt = :now WHERE id = :id")
    suspend fun updateHash(id: Long, hash: String, now: Long)

    @Query("UPDATE recordings SET filePath = :path, updatedAt = :now WHERE id = :id")
    suspend fun updateFilePath(id: Long, path: String, now: Long)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)
}

data class MarkerWithImages(
    val marker: Marker,
    val images: List<MarkerImage>,
)

@Dao
interface MarkerDao {
    @Query("SELECT * FROM markers WHERE recordingId = :recordingId ORDER BY offsetMs ASC")
    fun observeByRecording(recordingId: Long): Flow<List<Marker>>

    @Query("SELECT * FROM markers WHERE recordingId = :recordingId ORDER BY sendOrder ASC, offsetMs ASC")
    suspend fun getByRecording(recordingId: Long): List<Marker>

    @Query("SELECT * FROM markers ORDER BY offsetMs ASC")
    fun observeAll(): Flow<List<Marker>>

    @Query("SELECT * FROM markers ORDER BY offsetMs ASC")
    suspend fun getAll(): List<Marker>

    @Query("SELECT * FROM markers WHERE id = :id")
    suspend fun getById(id: Long): Marker?

    @Query("SELECT COUNT(*) FROM markers WHERE recordingId = :recordingId")
    suspend fun countByRecording(recordingId: Long): Int

    @Query("SELECT COUNT(*) FROM markers WHERE recordingId = :recordingId AND aiStatus IN ('failed','pending')")
    suspend fun countUnsent(recordingId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(marker: Marker): Long

    @Update
    suspend fun update(marker: Marker)

    @Query("UPDATE markers SET aiStatus = :status, aiResponse = COALESCE(:response, aiResponse), aiError = :error, updatedAt = :now WHERE id = :id")
    suspend fun updateAi(id: Long, status: String, response: String?, error: String?, now: Long)

    @Query("DELETE FROM markers WHERE recordingId = :recordingId")
    suspend fun deleteByRecording(recordingId: Long)
}

@Dao
interface MarkerImageDao {
    @Query("SELECT * FROM marker_images WHERE markerId = :markerId ORDER BY orderIndex ASC")
    suspend fun getByMarker(markerId: Long): List<MarkerImage>

    @Query("SELECT * FROM marker_images")
    suspend fun getAll(): List<MarkerImage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<MarkerImage>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: MarkerImage): Long

    @Query("DELETE FROM marker_images WHERE markerId = :markerId")
    suspend fun deleteByMarker(markerId: Long)
}

@Dao
interface PauseEventDao {
    @Query("SELECT * FROM pause_events WHERE recordingId = :recordingId ORDER BY offsetMs ASC")
    fun observeByRecording(recordingId: Long): Flow<List<PauseEvent>>

    @Query("SELECT * FROM pause_events")
    suspend fun getAll(): List<PauseEvent>

    @Insert
    suspend fun insert(event: PauseEvent): Long

    @Query("DELETE FROM pause_events WHERE recordingId = :recordingId")
    suspend fun deleteByRecording(recordingId: Long)
}

@Dao
interface AiRequestLogDao {
    @Insert
    suspend fun insert(log: AiRequestLog): Long

    @Query("SELECT * FROM ai_request_logs")
    suspend fun getAll(): List<AiRequestLog>

    @Query("UPDATE ai_request_logs SET status = :status, response = :response, error = :error, finishedAt = :finishedAt WHERE id = :id")
    suspend fun finish(id: Long, status: String, response: String?, error: String?, finishedAt: Long)
}

