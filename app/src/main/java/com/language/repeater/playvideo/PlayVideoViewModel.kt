package com.language.repeater.playvideo

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.language.repeater.pcm.LocalVoiceSentenceDetector
import com.language.repeater.utils.FFmpegUtil
import com.language.repeater.utils.ToastUtil
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.pcm.PcmDataUtil
import com.language.repeater.pcm.Sentence
import com.language.repeater.pcm.WaveformPoint
import com.language.repeater.utils.Md5Util
import com.language.repeater.utils.ScreenUtil
import com.language.repeater.utils.SentenceFileStoreUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Date: 2025-10-10
 * Time: 19:35
 * Description:
 */
class PlayVideoViewModel(application: Application) : AndroidViewModel(application) {
  companion object {
    private const val TAG = "wangzixu"
    private const val MB = 1024.0 * 1024.0
  }

  //音视频地址
  var playUriStateFlow = MutableStateFlow<Uri?>(null)

  //分段取pcm数据的数据流
  var pcmLoaderStateFlow = MutableStateFlow<PCMSegmentLoader?>(null)

  //画全量波形图的数据流
  var allWaveDataFlow = MutableStateFlow<List<WaveformPoint>>(listOf())

  //句子划分的数据流
  var sentencesFlow = MutableStateFlow<List<Sentence>>(listOf())

  var uniqueKey: String = ""
  var curPcmFile: File? = null

  fun parseUriToPcm(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
    Log.i(TAG, "parseUriToPcm begin: $uri")
    try {
      uniqueKey = Md5Util.generateFastUniqueKey(application, uri)
      val time = System.currentTimeMillis()
      Log.i(TAG, "parseUriToPcm uniqueKey: $uniqueKey")
      //通过ffmpeg把视频文件解析成原始音频文件
      val path = FFmpegUtil.extractPcmFileByFFmpeg(application, uri, uniqueKey)

      Log.i(
        "wangzixu",
        "FFmpegUtil解码耗时 ${(System.currentTimeMillis() - time).toFloat() / 1000}"
      )
      val file = File(path)
      curPcmFile = file
      val pcmLoader = PCMSegmentLoader(file)

      launch(Dispatchers.IO) {
        loadAllWaveData(file)
      }

      launch(Dispatchers.IO) {
        loadSentenceData(file, uniqueKey)
      }

      playUriStateFlow.value = uri
      pcmLoaderStateFlow.value = pcmLoader
      Log.i(TAG, "parseUriToPcm 转换成pcm文件成功:${file.length() / MB} mb")
    } catch (e: Exception) {
      e.printStackTrace()
      Log.i(TAG, "parseUriToPcm error:$e")
      ToastUtil.toast("parseUriToPcm error:$e")
    }
  }

  private fun loadAllWaveData(file: File) {
    val data = PcmDataUtil.readAllPcmToWavePoint(file, ScreenUtil.getScreenSize().width)
    allWaveDataFlow.value = data
  }

  private suspend fun loadSentenceData(file: File, key: String) {
    var list = SentenceFileStoreUtil.loadData(application, key)
    //var list = listOf<Sentence>()
    if (list.isNullOrEmpty()) {
      //用于绘制波形的数据
      val time = System.currentTimeMillis()

      //创建VAD分离器
      val config = LocalVoiceSentenceDetector.SentenceDetectorConfig()
      list = LocalVoiceSentenceDetector().detectSentences(PcmDataUtil.readPcmFile(file), config)

      //V2版本
      //val detectorV2 = VoiceSentenceDetectorV2(application)
      //list = detectorV2.detectSentences(file, VoiceSentenceDetectorV2.Config())

      SentenceFileStoreUtil.saveData(application, key, list)
      Log.i("wangzixu", "检测句子耗时 ${(System.currentTimeMillis() - time).toFloat() / 1000}")
    } else {
      Log.i("wangzixu", "key: $key 句子已经存在, 直接使用")
    }

    sentencesFlow.value = list
    Log.i("wangzixu", "V2检测到 ${list.size} 句话:")
    val timeStringsV2 = sentenceToTimeString(list)
    timeStringsV2.forEachIndexed { index, timeStr ->
      Log.i("wangzixu", "句子 ${index + 1}: $timeStr")
    }
  }

  suspend fun reloadSentencesAuto() = withContext(Dispatchers.IO) {
    val file = curPcmFile ?: return@withContext
    val key = uniqueKey
    val config = LocalVoiceSentenceDetector.SentenceDetectorConfig()
    val list = LocalVoiceSentenceDetector().detectSentences(PcmDataUtil.readPcmFile(file), config)
    SentenceFileStoreUtil.saveData(application, key, list)
    sentencesFlow.value = list
  }

  suspend fun saveSentenceDataToFile() = withContext(Dispatchers.IO) {
    val list = sentencesFlow.value
    if (list.isEmpty()) {
      withContext(Dispatchers.Main) {
        ToastUtil.toast("句子列表为空, 无法存储句子信息")
      }
      return@withContext
    }

    if (Md5Util.isRandomKey(uniqueKey)) {
      withContext(Dispatchers.Main) {
        ToastUtil.toast("当前的文件key是随机的, 无法存储句子信息")
      }
      return@withContext
    }

    // 按 start 升序排列
    val sorted = list.sortedBy { it.start }
    val merged = mutableListOf<Sentence>()
    var current = sorted.first()
    // 依次检查是否重叠
    for (i in 1 until sorted.size) {
      val next = sorted[i]
      if (next.start <= current.end) {
        // 有重叠：取较大的 end 值进行合并
        Log.i("wangzixu", "saveSentenceDataToFile 重叠了, 第 ${i - 1}和$i 句")
        current.end = maxOf(current.end, next.end)
      } else {
        // 无重叠：保存当前区间，移动到下一个
        merged.add(current)
        current = next
      }
    }
    // 最后一个也别忘了加进去
    merged.add(current)
    SentenceFileStoreUtil.saveData(application, uniqueKey, merged)
    Log.i("wangzixu", "saveSentenceDataToFile success")

    withContext(Dispatchers.Main) {
      ToastUtil.toast("保存成功")
    }
  }

  fun deleteSentence(sen: Sentence?) {
    val sentences = sentencesFlow.value
    if (sen != null && sentences.contains(sen)) {
      val list = mutableListOf<Sentence>()
      list.addAll(sentencesFlow.value)
      list.remove(sen)
      sentencesFlow.value = list
    }
  }

  fun splitSentence(sen: Sentence?, pos: Float) {
    val sentences = sentencesFlow.value
    if (sen != null && sentences.contains(sen)) {
      val list = mutableListOf<Sentence>()
      list.addAll(sentencesFlow.value)
      val newSen = Sentence(sen.start, (pos - 0.5f).coerceIn(sen.start, pos))
      sen.start = pos
      val index = list.indexOf(sen)
      list.add(index, newSen)
      sentencesFlow.value = list
    }
  }

  /**
   * 转换为时间格式（用于调试）
   */
  @SuppressLint("DefaultLocale")
  fun sentenceToTimeString(segments: List<Sentence>): List<String> {
    return segments.map { (start, end) ->
      String.format("%.2fs - %.2fs (%.2fs)", start, end, end - start)
    }
  }
}