package com.suitanglu.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Recording::class, Marker::class, MarkerImage::class, PauseEvent::class, AiRequestLog::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun markerDao(): MarkerDao
    abstract fun markerImageDao(): MarkerImageDao
    abstract fun pauseEventDao(): PauseEventDao
    abstract fun aiRequestLogDao(): AiRequestLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "app.db",
            ).build().also { instance = it }
        }

        fun openAt(context: Context, name: String, dirPath: String): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .createFromFile(java.io.File(dirPath, name))
                .allowMainThreadQueries()
                .build()
    }
}
