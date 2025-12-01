package com.language.repeater.playvideo.components

import android.R.attr.data
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment

/**
 * Date: 2025-11-17
 * Time: 19:07
 * Description:
 */
class SelectFileComponent : BaseComponent<PlayVideoFragment>() {
//  val pickMedia by lazy {
//    fragment.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
//      if (uri != null) {
//        Log.d(PlayVideoFragment.TAG, "Selected: $uri")
//      } else {
//        Log.d(PlayVideoFragment.TAG, "No media selected")
//      }
//    }
//  }

//  val openFileLauncher by lazy {
//    fragment.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
//      Log.d(PlayVideoFragment.TAG, "uris size = ${uris.size}")
////      uris?.let { videoUri ->
////        fragment.requireContext().contentResolver.takePersistableUriPermission(
////          uri,
////          Intent.FLAG_GRANT_READ_URI_PERMISSION
////        )
////        //这里你可以用 videoUri 播放视频或读取内容
////        Log.d(PlayVideoFragment.TAG, "Selected video uri: $videoUri")
////        fragment.viewModel.parseUriToPcm(videoUri)
////      }
//    }
//  }

  val launcher by lazy {
    fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult(),
      object : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
          if (result.resultCode == Activity.RESULT_OK) {
            val clipData = result.data?.clipData
            val data = result.data?.data
            if (clipData != null) {
              // 多选
              Log.d(PlayVideoFragment.TAG, "Selected video mutable, uri: ${clipData.itemCount}")
              for (i in 0 until clipData.itemCount) {
                val uri = clipData.getItemAt(i).uri
                takePersistablePermission(uri)
                // 处理每个文件

              }
            } else if (data != null) {
              // 单选
              val uri = data
              Log.d(PlayVideoFragment.TAG, "Selected video single, uri: $uri")
              takePersistablePermission(uri)
              fragment.viewModel.parseUriToPcm(uri)
            }
          }
        }
      })
  }

  override fun onCreate() {
    super.onCreate()
    launcher
  }

  override fun onCreateView() {
    super.onCreateView()
    fragment.binding.selectFileBtn.setOnClickListener {
      val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
      intent.type = "*/*" // 必须设置为 */* 才能配合 MIME array
      intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
      intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
      intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
      intent.addCategory(Intent.CATEGORY_OPENABLE)
      launcher.launch(intent)

//      openFileLauncher.launch(arrayOf("audio/*", "video/*"))

//      pickMedia.launch(
//        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
//      )
    }
  }

  // 核心方法：申请持久权限
  private fun takePersistablePermission(uri: Uri) {
    try {
      val contentResolver = context.contentResolver
      // 关键代码：告诉系统我要永久接管这个 Uri 的读权限
      val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
      contentResolver.takePersistableUriPermission(uri, takeFlags)
      // 成功后，你就可以把 uri.toString() 存入 Room 或 SharedPreferences 了
      // 下次直接用 Uri.parse(string) 就能播放
    } catch (e: SecurityException) {
      e.printStackTrace()
      // 某些特殊云端文件可能不支持持久权限，这里要做异常处理
      Log.d(PlayVideoFragment.TAG, "takePersistablePermission failed:${e.message}, uri: $uri")
    }
  }
}