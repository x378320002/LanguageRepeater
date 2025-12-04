package com.language.repeater.test

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.media3.exoplayer.ExoPlayer
import com.language.repeater.databinding.TestFragmentBinding
import com.language.repeater.foundation.BaseFragment
import com.language.repeater.playvideo.PlayVideoViewModel
import com.language.repeater.record.LocalRecordManager
import com.language.repeater.record.IAudioRecordListener
import com.language.repeater.record.IAudioRecordManger
import java.io.File


@SuppressLint("SetTextI18n")
class TestFragment: BaseFragment() {
  private var _binding: TestFragmentBinding? = null
  private val binding get() = _binding!!

  companion object {
    const val TAG = "wangzixu"
  }

  // 假设你已经处理了运行时权限请求
  private val requestPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
      if (isGranted) {
        startRecording()
      } else {
        Toast.makeText(requireContext(), "Permission denied", Toast.LENGTH_SHORT).show()
      }
    }

  private val viewModel: PlayVideoViewModel by activityViewModels()
  private lateinit var audioRecorderManager: IAudioRecordManger
  private var exoPlayer: ExoPlayer? = null
  private var aacFilePath: String? = null

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = TestFragmentBinding.inflate(inflater, container, false)
    val view = binding.root
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }

// 1. 初始化 Manager
    audioRecorderManager = LocalRecordManager(this)

    // 2. 设置监听器
    audioRecorderManager.setRecordListener(object : IAudioRecordListener {
      override fun onRecordStart() {
        Log.d(TAG, "Recording started...")
        binding.recordStatus.text = "录音开始, 等待说话声..."
        // 更新 UI 为 "录音中..."
      }

      override fun onRecordDetect() {
        super.onRecordDetect()
        Log.d(TAG, "onRecordDetect")
        binding.recordStatus.text = "录音检测到说话..."
      }

      override fun onRecordProgress(elapsed: Long, volume: Int) {
        super.onRecordProgress(elapsed, volume)
        val str = "录音检测到说话,音量值: $volume"
        Log.d(TAG, str)
        binding.recordStatus.text = str
      }

      override fun onRecordFailure(type: Int, error: Throwable?, errInfo: String?) {
        super.onRecordFailure(type, error, errInfo)
        Log.d(TAG, "onRecordFailure. type:$type, errInfo: $errInfo")
        binding.recordStatus.text = "录音失败:$errInfo"
        Toast.makeText(requireContext(), "Error: $errInfo", Toast.LENGTH_SHORT).show()
      }

      override fun onRecordComplete(filePath: String, duration: Long) {
        super.onRecordComplete(filePath, duration)
        Log.d(TAG, "onRecordComplete. aacFilePath: $filePath")
        this@TestFragment.aacFilePath = filePath

        aacFilePath = filePath
        binding.recordStatus.text = "录音成功"
        val file = File(filePath)
        binding.aacFile.text = "路径:$filePath"
        val size1 = file.length() / 1024
        binding.aacFileSize.text = "录音文件大小:$size1 kb, 时长:${duration.toFloat()/1000} 秒"

        //viewModel.parseUriToPcm(Uri.fromFile(file))
      }
    })

    var text = "12"
    binding.beginRecord.setOnClickListener {
      // 检查权限
//      when {
//        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
//          // 权限已有，开始录音
//          startRecording()
//        }
//        else -> {
//          // 请求权限
//          requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
//        }
//      }

      text = text + text
      binding.startEllipsize.text = text
    }

    binding.finishRecord.setOnClickListener {
      audioRecorderManager.stop()
    }

//    binding.playRecord.setOnClickListener {
//      if (exoPlayer == null) {
//        exoPlayer = ExoPlayer.Builder(requireContext()).build().apply {
//          repeatMode = Player.REPEAT_MODE_OFF
//        }
//      }
//      if (!aacFilePath.isNullOrEmpty()) {
//        val mediaItem = MediaItem.fromUri(aacFilePath!!)
//        exoPlayer?.setMediaItem(mediaItem)
//        exoPlayer?.prepare()
//        exoPlayer?.play()
//      }
//    }
  }

  private fun startRecording() {
    val localVad = binding.localVadOn.isChecked
    audioRecorderManager.start(localVad)
  }

  override fun onDestroyView() {
    super.onDestroyView()
    // 确保在退出时停止录音并释放资源
    audioRecorderManager.stop()
    exoPlayer?.stop()
    exoPlayer?.release()
  }

  private fun getFilePath(uri: Uri): String {
    return try {
      // 首先检查是否是 file:// URI
      if (uri.scheme == "file") {
        return uri.path ?: "未知文件"
      }

      // 对于 content:// URI，尝试多种方法获取路径
      val context = requireContext()

      // 方法1: 通过 MediaStore.Files.FileColumns.DATA 获取
      if (uri.authority?.contains("media") == true) {
        val projection = arrayOf(android.provider.MediaStore.Files.FileColumns.DATA)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
          val columnIndex = it.getColumnIndex(android.provider.MediaStore.Files.FileColumns.DATA)
          if (columnIndex != -1 && it.moveToFirst()) {
            val path = it.getString(columnIndex)
            if (!path.isNullOrEmpty()) {
              return path
            }
          }
        }
      }

      // 方法2: 通过 OpenableColumns 获取文件名并创建临时文件
      val fileName = getFileNameFromUri(uri)
      if (fileName != "未知文件") {
        val inputStream = context.contentResolver.openInputStream(uri)
        inputStream?.use { input ->
          val tempFile = File(context.cacheDir, fileName)
          val outputStream = java.io.FileOutputStream(tempFile)
          outputStream.use { output ->
            input.copyTo(output)
            return tempFile.absolutePath
          }
        }
      }

      // 方法3: 如果以上都失败，创建一个默认的临时文件
      val inputStream = context.contentResolver.openInputStream(uri)
      inputStream?.use { input ->
        val tempFile = File(context.cacheDir, "temp_file_${System.currentTimeMillis()}")
        val outputStream = java.io.FileOutputStream(tempFile)
        outputStream.use { output ->
          input.copyTo(output)
          return tempFile.absolutePath
        }
      }

      "未知文件"
    } catch (e: Exception) {
      // 打印错误日志以便调试
      e.printStackTrace()
      "未知文件"
    }
  }

  private fun getFileNameFromUri(uri: Uri): String {
    return try {
      val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
      cursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && it.moveToFirst()) {
          it.getString(nameIndex) ?: "temp_file"
        } else {
          "temp_file"
        }
      } ?: "temp_file"
    } catch (e: Exception) {
      "temp_file"
    }
  }

  fun text() {

  }
}