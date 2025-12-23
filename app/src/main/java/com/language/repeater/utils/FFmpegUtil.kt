package com.language.repeater.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.language.repeater.pcm.PcmConfig
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
  private const val PCM_SUFFIX = ".pcm"

  fun extractWavByFFmpeg(
    context: Context,
    input: String,
    onSuccess: (outPut: String) -> Unit,
    onFail: (errInfo: String?) -> Unit,
  ) {
    // 传入 content:// URI 字符串
    if (input.isEmpty()) return

    val outputDir = context.getExternalFilesDir("audio")
    if (outputDir != null && !outputDir.exists()) {
      outputDir.mkdirs()
    }
    val outputFile = File(outputDir, "output.wav")
    if (outputFile.length() > 0) {
      outputFile.delete()
    }
    val outPath = outputFile.absolutePath
    val str = FFmpegKitConfig.getSafParameterForRead(context, input.toUri())

    // ffmpeg 命令
    val cmd = "-y -i $str -vn -acodec pcm_s16le -ar 16000 -ac 1 $outPath"
    Log.i(TAG, "FFmpegKit cmd:$cmd")
    FFmpegKit.executeAsync(cmd) { session ->
      val returnCode = session.returnCode
      if (ReturnCode.isSuccess(returnCode)) {
        Log.i(TAG, "FFmpegKit success")
        onSuccess(outPath)
      } else {
        Log.i(TAG, "FFmpegKit failure:${returnCode}, ${session.failStackTrace}")
        onFail(null)
        if (outputFile.length() > 0) {
          outputFile.delete()
        }
      }
    }
  }

  suspend fun extractPcmFileByFFmpeg(context: Context, input: Uri, key: String): String =
    withContext(Dispatchers.IO) {
      val outputDir = context.getExternalFilesDir("pcm")
      if (outputDir != null && !outputDir.exists()) {
        outputDir.mkdirs()
      }
      val outputFile = File(outputDir, key + PCM_SUFFIX)
      if (outputFile.exists() && outputFile.length() > 0) {
        Log.i(TAG, "FFmpegKit extractPcmFileByFFmpeg 文件已经存在, 直接返回")
        return@withContext outputFile.absolutePath
      }

      val outPath = outputFile.absolutePath
      val str = FFmpegKitConfig.getSafParameterForRead(context, input)
      // ffmpeg 命令
      //val cmd = "-y -i $str -vn -acodec pcm_s16le -ar 16000 -ac 1 $outPut"
      val cmd =
        "-y -i $str -vn -ac 1 -ar ${PcmConfig.PCM_SAMPLE_RATE} -f s16le -c:a pcm_s16le $outPath"
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

  fun extractPcmFileByFFmpeg(
    context: Context,
    input: Uri?,
    onSuccess: (outPut: String) -> Unit,
    onFail: (errInfo: String?) -> Unit,
  ) {
    if (input == null) return

    val outputDir = context.getExternalFilesDir("pcm")
    if (outputDir != null && !outputDir.exists()) {
      outputDir.mkdirs()
    }
    val outputFile = File(outputDir, "output.pcm")
    if (outputFile.length() > 0) {
      outputFile.delete()
    }
    val outPath = outputFile.absolutePath

    val str = FFmpegKitConfig.getSafParameterForRead(context, input)
    // ffmpeg 命令
    //val cmd = "-y -i $str -vn -acodec pcm_s16le -ar 16000 -ac 1 $outPut"
    val cmd = "-y -i $str -vn -ac 1 -ar 8000 -f s16le -c:a pcm_s16le $outPath"
    Log.i(TAG, "FFmpegKit cmd:$cmd")
    FFmpegKit.executeAsync(cmd) { session ->
      val returnCode = session.returnCode
      if (ReturnCode.isSuccess(returnCode)) {
        Log.i(TAG, "FFmpegKit success")
        onSuccess(outPath)
      } else {
        Log.i(TAG, "FFmpegKit failure:${returnCode}, ${session.failStackTrace}")
        onFail(null)
        if (outputFile.length() > 0) {
          outputFile.delete()
        }
      }
    }
  }
}