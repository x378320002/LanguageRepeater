package com.language.repeater.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.language.repeater.playvideo.model.VideoEntity

// 1. 指定包含哪些表 (entities)，以及版本号 (version)
@Database(entities = [VideoEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

  // 2. 提供获取 DAO 的抽象方法
  abstract fun historyDao(): HistoryDao

  // 3. 单例模式 (Singleton)
  // 保证全应用只有一个数据库实例，避免性能开销
  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun instance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "repeater_database.db" // 数据库文件名
        )
          // 暂时允许在主线程查询（仅用于测试，正式版建议删掉 fallbackToDestructiveMigration）
          // .fallbackToDestructiveMigration() // 如果改了表结构不想处理迁移，可以用这个暴力清除旧数据
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}