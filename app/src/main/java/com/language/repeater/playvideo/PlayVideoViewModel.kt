package com.language.repeater.playvideo

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.language.repeater.utils.FFmpegUtil
import com.language.repeater.utils.ScreenUtil
import com.language.repeater.utils.ToastUtil
import com.language.repeater.widgets.PCMSegmentLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Date: 2025-10-10
 * Time: 19:35
 * Description:
 */
class PlayVideoViewModel(application: Application): AndroidViewModel(application) {
  companion object {
    private const val TAG = "PlayVideoViewModel"
    private const val MB = 1024.0 * 1024.0
  }

  val audioDataStateFlow = MutableStateFlow<List<Int>?>(null)
  var pcmLoaderStateFlow = MutableStateFlow<PCMSegmentLoader?>(null)

  fun parseUriToPcm(uri: Uri) {
    Log.i(TAG, "parseUriToPcm begin:$uri")
    viewModelScope.launch(Dispatchers.IO) {
      try {
        //通过ffmpeg把视频文件解析成原始音频文件
        val path = FFmpegUtil.extractPcmFileByFFmpeg(application, uri)
        val file = File(path)
        val pcmLoader = PCMSegmentLoader(File(path))
        pcmLoader.loadAll()
        pcmLoaderStateFlow.value = pcmLoader
        Log.i(TAG, "parseUriToPcm 转换成pcm文件成功:${file.length() / MB}MB")

//        //读取原始音频文件, 从pcm数据文件读取原始的short数组
//        val oriData = PcmDataUtil.readPcmFile(path)
//        val oriDataSizeInMB = oriData.size * Short.SIZE_BYTES / MB
//        Log.i(TAG, "parseUriToPcm oriDataSize:${oriDataSizeInMB}MB")
//
//        //因为原始音频文件数据量通常很大, 采样处理生成和屏幕像素宽度差不多的数据量
//        val sampleData = PcmDataUtil.downSample(oriData, ScreenUtil.getScreenSize().width)
//        val sampleDataSizeInMB = sampleData.size * Int.SIZE_BYTES / MB
//
//        val time = (System.currentTimeMillis() - beginTime)/1000
//        Log.i(TAG, "parseUriToPcm success sampleDataSize:${sampleDataSizeInMB}MB, time:$time 秒")
//
//        audioDataStateFlow.value = sampleData
      } catch (e: Exception) {
        e.printStackTrace()
        Log.i(TAG, "parseUriToPcm error:$e")
        ToastUtil.toast("parseUriToPcm error:$e")
      }
    }
  }

}