
import android.R.attr.path
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
  suspend fun readPcmFile(path: String): List<Short> = withContext(Dispatchers.IO) {
    val file = File(path)
    FileInputStream(file).use { input ->
      val buffer = ByteArray(8192)
      var bytesRead: Int
      val samples = mutableListOf<Short>()
      while (input.read(buffer).also { bytesRead = it } != -1) {
        // s16le -> 2字节小端
        val count = bytesRead / 2
        for (i in 0 until count) {
          val value = (buffer[i * 2 + 1].toInt() shl 8) or  (buffer[i * 2].toInt() and 0xFF)
          samples.add(value.toShort())
        }
      }
      samples
    }
  }

  /**
   * 二次处理, downsample, 供ui渲染, 原始数据太大了, 必须经过二次处理
   * 处理方式: 均方根
   */
  suspend fun downSample(data: List<Short>, width: Int): List<Int> = withContext(Dispatchers.IO) {
    if (data.size <= width) {
      return@withContext data.map { it.toInt() }
    }

    // Downsample：减少绘制点数
    val step = data.size.toFloat() / width.toFloat()
    Log.i(TAG, "step:$step")
    val result = mutableListOf<Int>()
    for (i in 0 until width) {
      val start = (i * step).toInt()
      val end = minOf((start + step).toInt(), data.size)

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
      val mixed = (rms * 0.9 + (max - min) * 1 * 0.2)
      result.add(mixed.toInt())
//      result.add(data[(start + end) / 2].toInt())
    }
    return@withContext result
  }
}