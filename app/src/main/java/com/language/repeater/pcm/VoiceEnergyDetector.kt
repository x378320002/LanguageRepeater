package com.language.repeater.pcm

/**
 * 用来根据某个帧的能量, 来检测是否是语音
 */
class VoiceEnergyDetector(
  private val alpha: Float = 0.995f,
  private val onFactor: Float = 2.0f,
  private val offFactor: Float = 1.3f,
  private val hangFrames: Int = 8,
  private val minThreshold: Float = 0.005f, // 最小阈值保护
  private val maxThreshold: Float = 0.5f    // 最大阈值保护
) {
  private var noise = 0f
  private var isVoice = false
  private var hang = 0

  /**
   * 处理一帧能量，返回是否为语音
   * frameEnergy: 0..1
   */
  fun isActive(frameEnergy: Float): Boolean {
    val e = frameEnergy.coerceIn(0f, 1f)
    if (noise == 0f) noise = e.coerceAtLeast(1e-6f)

    // 防止瞬态把 noise 拉高太快：只在 e < noise * onFactor 时允许更新
    if (e <= noise * onFactor) {
      noise = alpha * noise + (1 - alpha) * e
    } else {
      // 当有很强声音时，仍给 noise 一个非常小的上升速率（防止长期高噪声被永久抬高）
      noise = 0.999f * noise + 0.001f * e
    }

    val thOn = (noise * onFactor).coerceIn(minThreshold, maxThreshold)
    val thOff = (noise * offFactor).coerceIn(minThreshold, maxThreshold)

    if (!isVoice) {
      if (e > thOn) {
        isVoice = true
        hang = hangFrames
      }
    } else {
      if (e < thOff) {
        if (hang > 0) hang-- else isVoice = false
      } else {
        hang = hangFrames
      }
    }
    return isVoice
  }

  fun currentThreshold(): Float {
    return (noise * onFactor).coerceIn(minThreshold, maxThreshold)
  }
}
