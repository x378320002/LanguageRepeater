package com.language.repeater.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
  version = 2,
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
          // 暂时允许在主线程查询（仅用于测试，正式版建议删掉 fallbackToDestructiveMigration）
           .fallbackToDestructiveMigration(true) // 如果改了表结构不想处理迁移，可以用这个暴力清除旧数据
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}