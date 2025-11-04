package com.language.repeater.test

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.language.repeater.databinding.TestFragmentBinding
import com.language.repeater.databinding.TestFragmentItemBinding
import androidx.navigation.findNavController
import com.language.repeater.PlayVideoPageKey
import com.language.repeater.defaultNavOptions
import java.io.File


class TestFragment: Fragment() {
  private var _binding: TestFragmentBinding? = null
  private val binding get() = _binding!!

  companion object {
    private const val REQUEST_CODE_PICK_FILE = 1001
  }

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

    setupFileSelection()
  }

  private fun setupFileSelection() {
    binding.selectFile.setOnClickListener {
      // 创建文件选择Intent
      val intent = Intent(Intent.ACTION_GET_CONTENT)
      intent.type = "audio/*" // 只选择音频文件
      intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/aac", "audio/mpeg", "audio/wav", "audio/mp3"))
      intent.addCategory(Intent.CATEGORY_OPENABLE)

      // 启动文件选择器
      startActivityForResult(
        Intent.createChooser(intent, "选择音频文件"),
        REQUEST_CODE_PICK_FILE
      )
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == android.app.Activity.RESULT_OK) {
      data?.data?.let { uri ->
        handleSelectedFile(uri)
      } ?: run {
        Toast.makeText(context, "未选择文件", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun handleSelectedFile(uri: Uri) {
    // 在这里处理选中的文件
    // 可以获取文件信息、读取文件内容等
    val filePath = getFilePath(uri)
    Log.i("wangzixu", "handleSelectedFile fileUri:$uri")
    Toast.makeText(context, "选中文件: $filePath", Toast.LENGTH_LONG).show()
    val file = File(filePath)
    val size = file.length().toFloat()/1024f
    val text = "$filePath, $size kb"
    Log.i("wangzixu", "handleSelectedFile fileInfo:$text")
    binding.fileInfo.text = text
    // 这里可以添加更多处理逻辑，比如：
    // 1. 读取文件内容
    // 2. 将文件路径传递给其他页面
    // 3. 在RecyclerView中显示文件列表等
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
}