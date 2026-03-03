package com.language.repeater.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.language.repeater.playvideo.model.CurPlayListEntity
import com.language.repeater.playvideo.model.HistoryEntity
import com.language.repeater.playvideo.model.VideoEntity

// 给 Context 扩展一个 historyDao 属性
val Context.historyDao
  get() = AppDb.instance(this).historyDao()

// 给 Context 扩展一个 playlistDao 属性
val Context.curPlayListDao
  get() = AppDb.instance(this).curPlayListDao()

// 给 Context 扩展一个 videoInfoDao 属性
val Context.videoInfoDao
  get() = AppDb.instance(this).videoInfoDao()

// 1. 指定包含哪些表 (entities)，以及版本号 (version)
@Database(
  entities = [VideoEntity::class, HistoryEntity::class, CurPlayListEntity::class],
  version = 3,
  exportSchema = false
)
abstract class AppDb : RoomDatabase() {

  // 2. 提供获取 DAO 的抽象方法
  abstract fun historyDao(): HistoryDao
  abstract fun videoInfoDao(): VideoInfoDao
  abstract fun curPlayListDao(): CurPlayListDao

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
          .addMigrations(object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
              // 执行 ALTER TABLE 添加列，并设置默认值 0
              db.execSQL("ALTER TABLE video_info_table ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
              // 2. 处理 CurPlayListEntity 的索引变更
              // 先删除旧的普通索引（如果存在）
              db.execSQL("DROP INDEX IF EXISTS index_current_list_table_videoId")
              // 注意：创建唯一索引前，必须确保没有重复的 videoId
              // 如果有重复数据，需要先清理或合并（参考之前的逻辑）
              // 这里假设你已经处理了重复，或者表目前没有重复数据
              db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_current_list_table_videoId ON current_list_table(videoId)")
            }
          })
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}