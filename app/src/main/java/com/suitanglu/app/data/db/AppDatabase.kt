package com.suitanglu.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Recording::class, Marker::class, MarkerImage::class, PauseEvent::class, AiRequestLog::class, MarkerTurn::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
    abstract fun markerDao(): MarkerDao
    abstract fun markerImageDao(): MarkerImageDao
    abstract fun pauseEventDao(): PauseEventDao
    abstract fun aiRequestLogDao(): AiRequestLogDao
    abstract fun markerTurnDao(): MarkerTurnDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2：新增 marker_turns 多轮追问表（历史数据保留）；
         *  已有成功回复的老标记回填为 turn 0，在新对话 UI 中可见、可重新生成 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `marker_turns` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`markerId` INTEGER NOT NULL, " +
                        "`orderIndex` INTEGER NOT NULL, " +
                        "`question` TEXT, " +
                        "`answer` TEXT, " +
                        "`status` TEXT NOT NULL, " +
                        "`error` TEXT, " +
                        "`promptTokens` INTEGER NOT NULL, " +
                        "`cacheHitTokens` INTEGER NOT NULL, " +
                        "`completionTokens` INTEGER NOT NULL, " +
                        "`finishReason` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_marker_turns_markerId` ON `marker_turns` (`markerId`)")
                db.execSQL(
                    "INSERT INTO marker_turns " +
                        "(markerId, orderIndex, question, answer, status, error, promptTokens, cacheHitTokens, completionTokens, finishReason, createdAt, updatedAt) " +
                        "SELECT id, 0, NULL, aiResponse, 'success', NULL, 0, 0, 0, NULL, createdAt, updatedAt " +
                        "FROM markers WHERE aiStatus = 'success' AND aiResponse IS NOT NULL",
                )
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "app.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
