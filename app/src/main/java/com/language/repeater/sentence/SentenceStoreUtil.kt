package com.language.repeater.sentence

import android.content.Context
import android.util.Log
import com.language.repeater.json
import com.language.repeater.playvideo.model.VideoEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object SentenceStoreUtil {
  private const val FILE_SUFFIX = "_sentences.json"
  private const val SENTENCE_DIR = "sentences"

  suspend fun clearTempData(
    context: Context, except: List<String>
  ) = withContext(Dispatchers.IO) {
    val dir = context.getExternalFilesDir(SENTENCE_DIR)
    if (dir != null && dir.exists() && dir.isDirectory) {
      dir.listFiles()?.forEach { file->
        val fileName = file.name
        Log.i("wangzixu_clearTempData","SentenceStoreUtil fileName:$fileName")
        // 如果文件名包含 except 中任意一个字符串，则跳过
        val shouldKeep = except.any { exceptKey ->
          fileName.contains(exceptKey)
        }

        if (!shouldKeep) {
          runCatching {
            file.delete()
          }
        }
      }
    }
  }

  suspend fun deleteData(context: Context, key: String) = withContext(Dispatchers.IO) {
    try {
      val file = getFile(context, key)
      if (file.exists()) {
        file.delete()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  /**
   * 根据 MD5 获取对应的文件
   */
  private fun getFile(context: Context, key: String): File {
    val outputDir = context.getExternalFilesDir(SENTENCE_DIR)
    if (outputDir != null && !outputDir.exists()) {
      outputDir.mkdirs()
    }
    return File(outputDir, key + FILE_SUFFIX)
  }

  /**
   * 保存数据 (I/O 操作，必须在后台线程调用!)
   *
   * @param context Context
   * @param com.google.common.hash.Hashing.md5 视频文件的 MD5
   * @param data 要保存的数据 (List<SentenceSegment>)
   */
  suspend fun saveData(context: Context, key: String, data: List<Sentence>) =
    withContext(Dispatchers.IO) {
      try {
        // 1. 使用 kotlinx.serialization 将 List 转换为 JSON 字符串
        val jsonString = json.encodeToString(data)

        // 2. 写入文件
        getFile(context, key).writeText(jsonString)
      } catch (e: IOException) {
        e.printStackTrace()
      } catch (e: Exception) {
        // 捕获序列化错误，例如数据类型不匹配等
        e.printStackTrace()
      }
    }

  /**
   * @param context Context
   * @param key 视频文件的 MD5
   * @return 找到的数据, 或 null
   */
  suspend fun loadData(context: Context, key: String) =
    withContext<List<Sentence>?>(Dispatchers.IO) {
      try {
        val file = getFile(context, key)

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