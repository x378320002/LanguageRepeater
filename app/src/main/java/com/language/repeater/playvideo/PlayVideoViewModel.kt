package com.language.repeater.playvideo

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.utils.ToastUtil
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.pcm.PcmDataUtil
import com.language.repeater.pcm.VoiceSentenceDetectorV2
import com.language.repeater.pcm.WaveformPoint
import com.language.repeater.utils.ScreenUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

  //音视频地址
  var playUriStateFlow = MutableStateFlow<Uri?>(null)
  //分段取pcm数据的数据流
  var pcmLoaderStateFlow = MutableStateFlow<PCMSegmentLoader?>(null)
  //画全量波形图的数据流
  var allWaveDataFlow = MutableStateFlow<List<WaveformPoint>>(listOf())
  //句子划分的数据流
  var sentencesFlow = MutableStateFlow<List<Pair<Float, Float>>>(listOf())

  fun parseUriToPcm(uri: Uri) {
    Log.i(TAG, "parseUriToPcm begin:$uri")
    viewModelScope.launch(Dispatchers.IO) {
      try {
        //通过ffmpeg把视频文件解析成原始音频文件
        val path = FFmpegUtil.extractPcmFileByFFmpeg(application, uri)
        val file = File(path)
        val pcmLoader = PCMSegmentLoader(file)

        launch(Dispatchers.IO) {
          loadAllWaveData(file)
        }
        launch(Dispatchers.IO) {
          loadSentenceData(file)
        }

        pcmLoaderStateFlow.value = pcmLoader
        playUriStateFlow.value = uri
        Log.i(TAG, "parseUriToPcm 转换成pcm文件成功:${file.length() / MB}MB")

//        //读取原始音频文件, 从pcm数据文件读取原始的short数组
//        val oriData = com.language.repeater.pcm.PcmDataUtil.readPcmFile(path)
//        val oriDataSizeInMB = oriData.size * Short.SIZE_BYTES / MB
//        Log.i(TAG, "parseUriToPcm oriDataSize:${oriDataSizeInMB}MB")
//
//        //因为原始音频文件数据量通常很大, 采样处理生成和屏幕像素宽度差不多的数据量
//        val sampleData = com.language.repeater.pcm.PcmDataUtil.downSample(oriData, ScreenUtil.getScreenSize().width)
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

  private fun loadAllWaveData(file: File) {
    val data = PcmDataUtil.readAllPcmData(file, ScreenUtil.getScreenSize().width)
    allWaveDataFlow.value = data
  }

  private fun loadSentenceData(file: File) {
    //V1版本
//    // 1. 准备PCM数据
//    val startSample = 0
//    val sampleCount = (durationSeconds * sampleRate).toInt()
//    val pcmData = loadSegmentBySample(startSample, sampleCount)
//    //用于绘制波形的数据
//    var time = System.currentTimeMillis()
//    allData = downsampleToWaveform(pcmData,
//      ScreenUtil.getScreenSize().width,
//      0f,
//      1f / sampleRate)
//    // 2. 创建VAD分离器
//    val segmentation = VoiceSentenceDetectorV1()
//    // 3. 使用默认配置分离
//    voiceSegmentsV1 = segmentation.segment(pcmData)
//    Log.i("wangzixu", "耗时 ${(System.currentTimeMillis()-time).toFloat()/1000}")
//    Log.i("wangzixu", "检测到 ${voiceSegmentsV1.size} 句话:")
////    voiceSegmentsV1.forEachIndexed { index, (start, end) ->
////      Log.i("wangzixu", "句子 ${index + 1}: [$start, $end]")
////    }
//    // 5. 查看时间格式
//    val timeStrings = segmentation.segmentsToTimeString(voiceSegmentsV1)
//    timeStrings.forEachIndexed { index, timeStr ->
//      Log.i("wangzixu", "句子 ${index + 1}: $timeStr")
//    }

    //V2版本
    val time = System.currentTimeMillis()
    val detectorV2 = VoiceSentenceDetectorV2(application)
    val sentences = detectorV2.detectSentences(file)
    Log.i("wangzixu", "V2耗时 ${(System.currentTimeMillis()-time).toFloat()/1000}")
    Log.i("wangzixu", "V2检测到 ${sentences.size} 句话:")
    val timeStringsV2 = sentenceToTimeString(sentences)
    timeStringsV2.forEachIndexed { index, timeStr ->
      Log.i("wangzixu", "句子 ${index + 1}: $timeStr")
    }
    sentencesFlow.value = sentences
  }

  /**
   * 转换为时间格式（用于调试）
   */
  @SuppressLint("DefaultLocale")
  fun sentenceToTimeString(segments: List<Pair<Float, Float>>): List<String> {
    return segments.map { (start, end) ->
      String.format("%.2fs - %.2fs (%.2fs)", start, end, end - start)
    }
  }
}