package com.language.repeater.utils

import android.R.attr.data
import android.content.Context
import com.google.common.hash.Hashing.md5
import com.language.repeater.pcm.Sentence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

object SentenceFileStoreUtil {
  // 使用默认的 Json 配置对象
  private val json = Json {
    ignoreUnknownKeys = true // 忽略 JSON 中多余的字段
    prettyPrint = false // 节省空间，不进行美化打印
  }
  private const val FILE_SUFFIX = "_sentences.json"

  /**
   * 根据 MD5 获取对应的文件
   */
  private fun getFile(context: Context, md5: String): File {
    return File(context.filesDir, md5 + FILE_SUFFIX)
  }

  /**
   * 保存数据 (I/O 操作，必须在后台线程调用!)
   *
   * @param context Context
   * @param md5 视频文件的 MD5
   * @param data 要保存的数据 (List<SentenceSegment>)
   */
  suspend fun saveData(context: Context, md5: String, data: List<Sentence>) = withContext(Dispatchers.IO) {
    try {
      // 1. 使用 kotlinx.serialization 将 List 转换为 JSON 字符串
      val jsonString = json.encodeToString(data)

      // 2. 写入文件
      getFile(context, md5).writeText(jsonString)

    } catch (e: IOException) {
      e.printStackTrace()
    } catch (e: Exception) {
      // 捕获序列化错误，例如数据类型不匹配等
      e.printStackTrace()
    }
  }

  /**
   * @param context Context
   * @param md5 视频文件的 MD5
   * @return 找到的数据, 或 null
   */
  suspend fun loadData(context: Context, md5: String) = withContext<List<Sentence>?>(Dispatchers.IO) {
    try {
      val file = getFile(context, md5)

      if (!file.exists()) {
        null
      }

      // 1. 读取文件内容
      val jsonString = file.readText()

      if (jsonString.isEmpty()) {
        null
      }

      // 2. 使用 kotlinx.serialization 将 JSON 字符串反序列化回 List<SentenceSegment>
      json.decodeFromString<List<Sentence>>(jsonString)
    } catch (e: Exception) {
      // 文件读取、JSON解析或反序列化错误
      e.printStackTrace()
      null
    }
  }
}