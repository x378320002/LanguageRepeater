package com.language.repeater.playvideo.components

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.language.repeater.dataStore
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.subtitleStore
import com.language.repeater.utils.DataStoreKey
import com.language.repeater.utils.DataStoreKey.KEY_SUBTITLE_FOLDER
import com.language.repeater.playvideo.playlist.PlaylistManager
import com.language.repeater.utils.FFmpegUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Date: 2025-11-17
 * Time: 19:07
 * Description:
 */
class SelectFileComponent : BaseComponent<PlayVideoFragment>() {
  val openFileLauncher by lazy {
    fragment.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
      Log.d(PlayVideoFragment.TAG, "OpenMultipleDocuments uris size = ${it.size}")
      fragment.showLoading()
      fScope.launch {
        val preferences = context.subtitleStore.data.firstOrNull()

        //转成info给视频播放器
        var needToCheckSubFolder = false
        val infos = it.map { uri ->
          takePersistablePermission(uri)

          val info = PlaylistManager.getFileInfo(context, uri)
          val prefKey = stringPreferencesKey(info.id)
          val sub = preferences?.get(prefKey)
          if (!sub.isNullOrEmpty()) {
            info.subUri = sub
          } else {
            needToCheckSubFolder = true
          }
          val wav = FFmpegUtil.extractWavFileByFFmpeg(context, info.uri.toUri(), info.id)
          Log.d(PlayVideoFragment.TAG, "Selected video name:${info.name}, 字幕: ${info.subUri}, wav:$wav")
          info
        }

        //去文件夹寻找字幕
        if (needToCheckSubFolder) {
          val folder = context.dataStore.data.map { p -> p[KEY_SUBTITLE_FOLDER] }.firstOrNull()?.toUri()
          Log.d(PlayVideoFragment.TAG, "needToCheckSubFolder folder: $folder")
          val map = SubtitleAutoLoader.scanSubtitleFolder(context, folder)
          if (map.isNotEmpty()) {
            infos.forEach {info->
              if (info.subUri == null) {
                val key = info.name.substringBeforeLast(".")
                val tempUri = map[key]
                info.subUri = tempUri.toString()
                if (tempUri != null) {
                  val prefKey = stringPreferencesKey(info.id)
                  context.subtitleStore.edit { sp-> sp[prefKey] = tempUri.toString() }
                }
              }
            }
          }
        }

        withContext(Dispatchers.Main) {
          fragment.viewModel.addPlayList(infos, false)
          fragment.hideLoading()
        }
      }
    }
  }

  val openDirLauncher by lazy {
    fragment.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
      Log.d(PlayVideoFragment.TAG, "OpenDocumentTree uri: $uri")
      if (uri != null) {
        takePersistablePermission(uri)
        fScope.launch {
          context.dataStore.edit {
            it[DataStoreKey.KEY_SUBTITLE_FOLDER] = uri.toString()
          }
        }
      }
    }
  }

  val openSubtitleLauncher by lazy {
    fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        takePersistablePermission(uri)
        fragment.viewModel.onSubtitleSelected(uri)
      }
    }
  }

  val resultLauncher by lazy {
    fragment.registerForActivityResult(
      ActivityResultContracts.StartActivityForResult(),
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
              //fragment.viewModel.parseUriToPcm(uri)
              //getParentFolder(context, uri)
            }
          }
        }
      })
  }

  override fun onCreate() {
    super.onCreate()
    //resultLauncher
    openFileLauncher
    openDirLauncher
    openSubtitleLauncher
  }

  override fun onCreateView() {
    super.onCreateView()
    fragment.binding.selectFileBtn.setOnClickListener {
//      val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
//      intent.type = "*/*" // 必须设置为 */* 才能配合 MIME array
//      intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
//      intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
//      intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
//      intent.addCategory(Intent.CATEGORY_OPENABLE)
//      resultLauncher.launch(null)
      openFileLauncher.launch(arrayOf("audio/*", "video/*"))
    }

    fragment.binding.selectSubtitleDir.setOnClickListener {
      openDirLauncher.launch(null)
    }

    fragment.binding.selectSubtitle.setOnClickListener {
      openSubtitleLauncher.launch(arrayOf("text/*"))
    }
  }

  // 核心方法：申请持久权限
  private fun takePersistablePermission(uri: Uri) {
    try {
      val contentResolver = context.contentResolver
      // 关键代码：告诉系统我要永久接管这个 Uri 的读权限
      val takeFlags: Int =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
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