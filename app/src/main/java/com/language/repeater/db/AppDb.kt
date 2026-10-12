package com.language.repeater.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// 1. 指定包含哪些表 (entities)，以及版本号 (version)
@Database(
  entities = [
    VideoEntity::class,
    PlaybackListEntity::class
  ],
  version = 1,
  exportSchema = true
)
abstract class AppDb : RoomDatabase() {

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
          .fallbackToDestructiveMigration(true) // 如果改了表结构不想处理迁移，可以用这个暴力清除旧数据
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}