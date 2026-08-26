package com.language.repeater.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.language.repeater.playbacklist.PlaybackListEntity
import com.language.repeater.playvideo.model.HistoryEntity
import com.language.repeater.playvideo.model.VideoEntity

// 给 Context 扩展一个 historyDao 属性
val Context.historyDao
  get() = AppDb.instance(this).historyDao()

// 给 Context 扩展一个 videoInfoDao 属性
val Context.videoInfoDao
  get() = AppDb.instance(this).videoInfoDao()

// 1. 指定包含哪些表 (entities)，以及版本号 (version)
@Database(
  entities = [
    VideoEntity::class,
    HistoryEntity::class,
    PlaybackListEntity::class
  ],
  version = 5,
  exportSchema = true
)
abstract class AppDb : RoomDatabase() {

  // 2. 提供获取 DAO 的抽象方法
  abstract fun historyDao(): HistoryDao
  abstract fun videoInfoDao(): VideoInfoDao
  abstract fun playbackListDao(): PlaybackListDao

  // 3. 单例模式 (Singleton)
  // 保证全应用只有一个数据库实例，避免性能开销
  companion object {
    @Volatile
    private var INSTANCE: AppDb? = null

    fun instance(context: Context): AppDb {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDb::class.java,
          "repeater_database.db" // 数据库文件名
        )
          // .fallbackToDestructiveMigration(true) // 如果改了表结构不想处理迁移，可以用这个暴力清除旧数据
          .addMigrations(
            object : Migration(2, 3) {
              override fun migrate(db: SupportSQLiteDatabase) {
                // 旧版本可能已经存在重复项，先保留最早的一条再创建唯一索引。
                db.execSQL("ALTER TABLE video_info_table ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                  """
                    DELETE FROM current_list_table
                    WHERE playlistId NOT IN (
                      SELECT MIN(playlistId)
                      FROM current_list_table
                      GROUP BY videoId
                    )
                  """.trimIndent()
                )
                db.execSQL("DROP INDEX IF EXISTS index_current_list_table_videoId")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_current_list_table_videoId ON current_list_table(videoId)")
              }
            },
            object : Migration(3, 4) {
              override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                  """
                    CREATE TABLE IF NOT EXISTS playback_list_table (
                      mediaId TEXT NOT NULL,
                      sortOrder INTEGER NOT NULL,
                      PRIMARY KEY(mediaId),
                      FOREIGN KEY(mediaId) REFERENCES video_info_table(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                  """.trimIndent()
                )
                db.execSQL(
                  """
                    INSERT INTO playback_list_table(mediaId, sortOrder)
                    SELECT source.videoId,
                           (source.rowNumber - 1) * 1024
                    FROM (
                      SELECT current.videoId AS videoId,
                             (
                               SELECT COUNT(*)
                               FROM current_list_table previous
                               WHERE previous.sortOrder < current.sortOrder
                                  OR (previous.sortOrder = current.sortOrder AND previous.playlistId <= current.playlistId)
                             ) AS rowNumber
                      FROM current_list_table current
                    ) source
                    INNER JOIN video_info_table video
                      ON video.id = source.videoId
                  """.trimIndent()
                )
                db.execSQL(
                  "CREATE INDEX IF NOT EXISTS index_playback_list_table_sortOrder ON playback_list_table(sortOrder)"
                )
                // 旧列表数据迁移完成后移除旧表，确保版本 4 schema 只保留新链路。
                db.execSQL("DROP TABLE IF EXISTS current_list_table")
              }
            },
            object : Migration(4, 5) {
              override fun migrate(db: SupportSQLiteDatabase) {
                // 重构后 Entity 列表已变化（移除 Session 表、新增 PlaybackList 表），
                // 但 v4 迁移已创建 playback_list_table，此处仅占位使版本号与 schema 对齐。
              }
            }
          )
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}