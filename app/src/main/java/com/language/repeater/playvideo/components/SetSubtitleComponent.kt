package com.language.repeater.playvideo.components

import androidx.activity.result.contract.ActivityResultContracts
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.utils.FileUtil.takePersistablePermission
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.launch

/**
 * Date: 2026-03-10
 * Time: 14:59
 * Description:
 */
class SetSubtitleComponent: BaseComponent<PlayVideoFragment>() {
  val openSubtitleLauncher by lazy {
    fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        takePersistablePermission(context, uri)
        fragment.viewModel.onSubtitleSelected(uri)
      }
    }
  }

  override fun onCreate() {
    super.onCreate()
    openSubtitleLauncher
  }

  override fun onCreateView() {
    super.onCreateView()

    fragment.binding.btnSubtitle.setOnClickListener {
      showSubtitlePop()
    }
  }

  //if (fragment.viewModel.currentMediaItem.value != null) {
  //  openSubtitleLauncher.launch(arrayOf("text/*", "application/x-subrip"))
  //} else {
  //  ToastUtil.toast("当前没有视频, 无法设置字幕")
  //}

  private fun showSubtitlePop() {
    //获取当前player的字幕列表
    //弹出PopupMenu
    //首位固定添加一个item,文案是@string/select_subtitle
    //如果字幕列表不为空,第二位固定添加一个禁用字幕的item, 点击后隐藏字幕
    //每一个字幕条目添加一个对应的item, 点击后显示选中的字幕轨
  }
}