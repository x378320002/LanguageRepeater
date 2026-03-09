package com.language.repeater.playvideo.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.language.repeater.R
import com.language.repeater.dataStore
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.subtitleStore
import com.language.repeater.utils.DataStoreUtil.KEY_SUBTITLE_FOLDER
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.utils.FileUtil
import com.language.repeater.utils.FileUtil.takePersistablePermission
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.utils.ToastUtil
import com.language.repeater.utils.UriAccessUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocalOnlyOpenMultipleDocuments : ActivityResultContracts.OpenMultipleDocuments() {
  override fun createIntent(context: Context, input: Array<String>): Intent {
    return super.createIntent(context, input).apply {
      putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    }
  }
}

/**
 * Date: 2025-11-17
 * Time: 19:07
 * Description:
 */
class SelectFileComponent : BaseComponent<PlayVideoFragment>() {
  val openFileLauncher by lazy {
    fragment.registerForActivityResult(LocalOnlyOpenMultipleDocuments()) {
      Log.d(PlayVideoFragment.TAG, "OpenMultipleDocuments uris size = ${it.size}")
      if (it.isEmpty()) return@registerForActivityResult

      fragment.showLoading()
      fScope.launch(Dispatchers.IO) {
        val preferences = context.subtitleStore.data.firstOrNull()

        //转成info给视频播放器
        var needToCheckSubFolder = false
        val infos = it.map { uri ->
          takePersistablePermission(context, uri)

          val info = FileUtil.getFileInfo(context, uri)
          val prefKey = stringPreferencesKey(info.id)
          val sub = preferences?.get(prefKey)
          if (!sub.isNullOrEmpty()) {
            val subUri = sub.toUri()
            if (UriAccessUtil.canRead(context, subUri)) {
              info.subUri = sub
            } else {
              // 存储的字幕链接已失效：清空并移除映射，避免后续加载无效字幕
              context.subtitleStore.edit { sp -> sp.remove(prefKey) }
              info.subUri = null
              needToCheckSubFolder = true
            }
          } else {
            needToCheckSubFolder = true
          }
          val wav = FFmpegUtil.extractWavFileByFFmpeg(context, info.uri.toUri(), info.id)
          Log.d(PlayVideoFragment.TAG, "Selected video name:${info.name}, 字幕: ${info.subUri}, wav:$wav")
          info
        }

        if (infos.isEmpty()) return@launch

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
                if (tempUri != null) {
                  info.subUri = tempUri.toString()
                  val prefKey = stringPreferencesKey(info.id)
                  context.subtitleStore.edit { sp-> sp[prefKey] = tempUri.toString() }
                }
              }
            }
          }
        }

        withContext(Dispatchers.Main) {
          //fragment.viewModel.addPlayList(listOf(info, info), true)
          fragment.viewModel.addPlayList(infos, false)
          fragment.hideLoading()
        }
      }
    }
  }

  val openSubtitleLauncher by lazy {
    fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        takePersistablePermission(context, uri)
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
                takePersistablePermission(context, uri)
                // 处理每个文件
              }
            } else if (data != null) {
              // 单选
              val uri = data
              Log.d(PlayVideoFragment.TAG, "Selected video single, uri: $uri")
              takePersistablePermission(context, uri)
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
    openSubtitleLauncher
  }

  private fun showMenu() {
    val popup = ResourcesUtil.createLightPopMenu(context, fragment.binding.ivAdd)
    popup.menuInflater.inflate(R.menu.menu_add_media, popup.menu)
    popup.setOnMenuItemClickListener { menuItem ->
      when (menuItem.itemId) {
        R.id.action_media -> {
          openFileLauncher.launch(arrayOf("audio/*", "video/*"))
        }
        R.id.action_subtitle -> {
          if (fragment.viewModel.currentMediaItem.value != null)  {
            openSubtitleLauncher.launch(arrayOf("text/*", "application/x-subrip"))
          } else {
            ToastUtil.toast("当前没有视频, 无法设置字幕")
          }
        }
      }
      true
    }
    popup.show()
  }

  override fun onCreateView() {
    super.onCreateView()
    fragment.binding.ivAdd.setOnClickListener {
      //showMenu()
      openFileLauncher.launch(arrayOf("audio/*", "video/*"))
    }
    //fragment.binding.selectFileBtn.setOnClickListener {
    //  //val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    //  //intent.type = "*/*" // 必须设置为 */* 才能配合 MIME array
    //  //intent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/*"))
    //  //intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
    //  //intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
    //  //intent.addCategory(Intent.CATEGORY_OPENABLE)
    //  //resultLauncher.launch(intent)
    //  openFileLauncher.launch(arrayOf("audio/*", "video/*"))
    //}
    //
    //fragment.binding.selectSubtitleDir.setOnClickListener {
    //  openDirLauncher.launch(null)
    //}
    //
    //fragment.binding.selectSubtitle.setOnClickListener {
    //  openSubtitleLauncher.launch(arrayOf(
    //    "text/*",
    //    "application/x-subrip",
    //  ))
    //}
  }
}