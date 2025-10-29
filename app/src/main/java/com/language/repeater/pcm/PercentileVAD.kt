package com.language.repeater.pcm

class PercentileVAD(
  private val windowSize: Int = 200,      // 窗口帧数（例如 200 frames ~ 2s @10ms）
  private val percentile: Float = 0.1f,   // 使用 10% 分位数作为噪声基线
  private val upDb: Float = 6f,           // dB margin 上阈（比如 +6dB）
  private val downDb: Float = 3f,         // 下阈
  private val hangoverFrames: Int = 8
) {
  private val window = ArrayDeque<Float>()
  private var isVoice = false
  private var hang = 0

  // helper: convert RMS-linear to dB
  private fun toDb(r: Float) = 20f * kotlin.math.log10((r.coerceAtLeast(1e-9f)))

  fun process(frameEnergy: Float): Boolean {
    if (window.size == windowSize) window.removeFirst()
    window.addLast(frameEnergy)
    // 每帧或每隔几帧计算一次分位（这里简单每帧）
    val arr = window.sorted()
    val idx = ((arr.size - 1) * percentile).toInt().coerceIn(0, arr.size - 1)
    val noiseRms = arr[idx]
    val noiseDb = toDb(noiseRms)
    val frameDb = toDb(frameEnergy)

    val thOn = noiseDb + upDb
    val thOff = noiseDb + downDb

    if (!isVoice) {
      if (frameDb > thOn) { isVoice = true; hang = hangoverFrames }
    } else {
      if (frameDb < thOff) {
        if (hang > 0) hang-- else isVoice = false
      } else hang = hangoverFrames
    }
    return isVoice
  }
}
