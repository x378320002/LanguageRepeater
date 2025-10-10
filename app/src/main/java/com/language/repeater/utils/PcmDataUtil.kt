
import android.util.Log
import java.io.File
import java.io.FileInputStream
import kotlin.math.sqrt

/**
 * Date: 2025-08-19
 * Time: 19:38
 * Description:
 */
object PcmDataUtil {
  private const val TAG = "PcmDataUtil"
  //从指定pcm文件中读取字节数组, 转成short数组
  fun readPcmFile(path: String): List<Short> {
    val file = File(path)
    FileInputStream(file).use { input ->
      val buffer = ByteArray(8192)
      var bytesRead: Int
      val samples = mutableListOf<Short>()
      while (input.read(buffer).also { bytesRead = it } != -1) {
        // s16le -> 2字节小端
        val count = bytesRead / 2
        for (i in 0 until count) {
          val value = (buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)
          samples.add(value.toShort())
        }
      }
      return samples
    }
  }

  /**
   * 二次处理, downsample, 供ui渲染, 原始数据太大了, 必须经过二次处理
   * 处理方式: 均方根
   */
  suspend fun downSample(data: List<Short>, width: Int): List<Int> {
    if (data.size <= width) {
      return data.map { it.toInt() }
    }

    // Downsample：减少绘制点数
    val step = data.size / width
    Log.i(TAG, "step:$step")
    val result = mutableListOf<Int>()
    for (i in 0 until width) {
      val start = i * step
      val end = minOf(start + step, data.size)

      var sum = 0.0
      var max = Short.MIN_VALUE.toInt()
      var min = Short.MAX_VALUE.toInt()
      for (j in start until end) {
        val v = data[j].toInt()
        sum += v * v
        if (v > max) max = v
        if (v < min) min = v
      }
      val rms = sqrt(sum / (end - start)).toDouble()
      val mixed = (rms * 0.8 + (max - min) * 2 * 0.2)
      //val v = sqrt(data.slice(start until end).map { it * it }.average()).toInt().toShort()
      result.add(mixed.toInt())
    }
    return result
  }
}