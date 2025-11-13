package com.language.repeater.record

/**
 * Date: 2025-11-10
 * Time: 18:11
 * Description:
 */
interface IAudioRecorderManger {
  companion object {
    const val FAIL_TYPE_NO_INPUT = 1
    const val FAIL_TYPE_CANCEL = 2
    const val FAIL_TYPE_ERROR = 3
    const val FAIL_TYPE_TOO_SHORT = 4
  }
  fun start()
  fun destroy()

  fun stop()
  fun setRecordListener(l: AudioRecordingListener?)
}