package com.language.repeater.record

import kotlin.time.Duration

interface AudioRecordingListener {
  //录音开始
  fun onRecordStart() {}

  //识别到有人开始说话了的回调, 一次录音过程只回调一次, 比如初次识别到音量大于x时回调
  fun onRecordDetect() {}

  //录音过程中录音时长和音量值的回调,
  fun onRecordProgress(elapsed: Long, volume: Int) {}

  //录音完成成功的回调
  fun onRecordComplete(filePath: String, duration: Long) {}

  //录音失败的回调
  fun onRecordFailure(type: Int, error: Throwable? = null, errInfo: String? = null) {}

  //录音结束的回调, 不区分取消/错误/完成，用于清理状态
  fun onStopAudioRecording(){}
}