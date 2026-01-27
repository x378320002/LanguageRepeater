package com.language.repeater.pcm

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Date: 2025-08-11
 * Time: 20:35
 * Description:
 */
object FFmpegUtil {
  private const val TAG = "wangzixu"
  private const val SUFFIX = ".wav"
  private const val WAV_DIR = "wav"

  /**
   * 获取 SENTENCE_DIR 文件夹的大小
   *
   * @param context 上下文
   * @return 文件夹的大小（以字节为单位）
   */
  suspend fun getDirectorySize(context: Context): Long = withContext(Dispatchers.IO) {
    var size: Long = 0
    val dir = context.getExternalFilesDir(WAV_DIR)
    if (dir != null && dir.exists() && dir.isDirectory) {
      dir.listFiles()?.forEach { file ->
        size += file.length()
      }
    }
    size
  }

  suspend fun clearTempData(
    context: Context, except: List<String>
  ) = withContext(Dispatchers.IO) {
    val wavDir = context.getExternalFilesDir(WAV_DIR)
    if (wavDir != null && wavDir.exists() && wavDir.isDirectory) {
      wavDir.listFiles()?.forEach { file->
        val fileName = file.name
        Log.i("wangzixu_clearTempData","FFmpegUtil fileName:$fileName")
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

  suspend fun delete(context: Context, key: String) = withContext(Dispatchers.IO) {
    try {
      val file = getWavFile(context, key)
      if (file.exists()) {
        file.delete()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  fun getWavFile(context: Context, key: String): File {
    val outputDir = context.getExternalFilesDir(WAV_DIR)
    if (outputDir != null && !outputDir.exists()) {
      outputDir.mkdirs()
    }
    return File(outputDir, key + SUFFIX)
  }

  suspend fun extractWavFileByFFmpeg(context: Context, input: Uri, key: String): String =
    withContext(Dispatchers.IO) {
      val outputFile = getWavFile(context, key)
      if (outputFile.exists() && outputFile.length() > 0) {
        Log.i(TAG, "FFmpegKit extractPcmFileByFFmpeg 文件已经存在, 直接返回")
        return@withContext outputFile.absolutePath
      }

      val outPath = outputFile.absolutePath
      val str = FFmpegKitConfig.getSafParameterForRead(context, input)
      // ffmpeg 命令
      //val cmd =
      //  "-y -i $str -vn -ac 1 -ar 16000 -f s16le -c:a pcm_s16le $outPath"
      //val cmd = "-y -i $str -vn -ac 1 -ar 16000 -c:a pcm_s16le $outPath"
      // 增加了: -map_metadata -1 (清除元数据)
      // 增加了: -fflags +bitexact (强制标准头部，避免写入额外的编码信息)
      val cmd =
        "-y -i $str -vn -ac 1 -ar 16000 -map_metadata -1 -fflags +bitexact -c:a pcm_s16le $outPath"
      Log.i(TAG, "FFmpegKit 开始解析成pcm -> cmd:$cmd")
      val session = FFmpegKit.execute(cmd)
      val returnCode = session.returnCode
      if (ReturnCode.isSuccess(returnCode)) {
        Log.i(TAG, "FFmpegKit success")
        outPath
      } else {
        Log.i(TAG, "FFmpegKit failure:${returnCode}, ${session.failStackTrace}")
        if (outputFile.length() > 0) {
          outputFile.delete()
        }
        throw Exception("extractPcmFileByFFmpeg failed:${returnCode}")
      }
    }
}