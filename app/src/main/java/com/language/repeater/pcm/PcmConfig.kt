package com.language.repeater.pcm

/**
 * Date: 2025-10-13
 * Time: 17:16
 * Description:
 */
object PcmConfig {
  //音频文件采样数
  const val PCM_SAMPLE_RATE = 16000
  const val PCM_CHANNEL = 1
  const val PCM_BIT_DEPTH = 16
  const val BYTES_PER_SAMPLE = 2 //每个采样有几个字节, 根据采样深度和通道数计算 = (btiDepth/8)*channel
}