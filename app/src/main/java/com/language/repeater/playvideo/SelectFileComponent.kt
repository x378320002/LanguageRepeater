package com.language.repeater.playvideo

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.language.repeater.foundation.BaseComponent

/**
 * Date: 2025-11-17
 * Time: 19:07
 * Description:
 */
class SelectFileComponent : BaseComponent<PlayVideoFragment>() {
  val pickMedia by lazy {
    fragment.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        Log.d(PlayVideoFragment.TAG, "Selected: $uri")
      } else {
        Log.d(PlayVideoFragment.TAG, "No media selected")
      }
    }
  }

  val openFileLauncher by lazy {
    fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      uri?.let { videoUri ->
        fragment.requireContext().contentResolver.takePersistableUriPermission(
          uri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        //这里你可以用 videoUri 播放视频或读取内容
        Log.d(PlayVideoFragment.TAG, "Selected video uri: $videoUri")
        fragment.viewModel.parseUriToPcm(videoUri)
      }
    }
  }

  val launcher by lazy {
    fragment.registerForActivityResult(
      ActivityResultContracts.StartActivityForResult(),
      object : ActivityResultCallback<ActivityResult> {
        override fun onActivityResult(result: ActivityResult) {
          if (result.resultCode == Activity.RESULT_OK) {
            val videoUri = result.data?.data
            if (videoUri != null) {
              //这里你可以用 videoUri 播放视频或读取内容
              Log.d(PlayVideoFragment.TAG, "Selected video uri: $videoUri")
              fragment.viewModel.parseUriToPcm(videoUri)
            }
          }
        }
      })
  }

  override fun onCreate() {
    super.onCreate()
    openFileLauncher
    launcher
    pickMedia
  }

  override fun onCreateView() {
    super.onCreateView()
    fragment.binding.selectFileBtn.setOnClickListener {

//      val intent = Intent(Intent.ACTION_PICK)
//      val intent = Intent(Intent.ACTION_GET_CONTENT)
//      intent.type = "*/*" // 只选择音频文件
//      intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
//      //intent.addCategory(Intent.CATEGORY_OPENABLE)
//      launcher.launch(intent)

      openFileLauncher.launch(arrayOf("audio/*", "video/*"))

//      pickMedia.launch(
//        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
//      )
    }
  }
}