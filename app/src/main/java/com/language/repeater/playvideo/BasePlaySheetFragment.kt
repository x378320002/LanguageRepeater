package com.language.repeater.playvideo

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED
import androidx.media3.common.Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE
import androidx.media3.common.Timeline
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.language.repeater.R
import com.language.repeater.databinding.PlaylistSheetFragmentBinding
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.playvideo.playlist.PlaylistAdapter
import com.language.repeater.utils.ScreenUtil
import kotlinx.coroutines.launch

@SuppressLint("SetTextI18n")
open class BasePlaySheetFragment : BottomSheetDialogFragment() {

  companion object {
    const val TAG = "wangzixu_BasePlaySheetFragment"
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    //设置界面高度
    view.updateLayoutParams {
      val fixedHeight = (ScreenUtil.getScreenSize().height * 0.6f).toInt()
      height = fixedHeight
    }
  }

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
    dialog.setOnShowListener {
      val behavior = dialog.behavior
      behavior.state = BottomSheetBehavior.STATE_EXPANDED
      behavior.skipCollapsed = true
      behavior.isHideable = true

      behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
        // 监听状态变化
        override fun onStateChanged(bottomSheet: View, newState: Int) {
          if (newState == BottomSheetBehavior.STATE_HIDDEN) {
            // 【关键步骤】
            // 如果进入了 HIDDEN 状态，说明用户已经通过滑动把列表拖出去了。
            // 此时我们需要关闭 Dialog，但必须先【禁用】掉系统自带的退出动画。
            // 否则就会出现“列表没了，背景还在慢慢淡出”的怪异现象。
            dialog.window?.setWindowAnimations(0) // 0 表示无动画
            dismiss()
          }
        }

        // 监听滑动进度 (实时改变背景透明度)
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
          // slideOffset 含义:
          // > 0: 从折叠态(collapsed)向上拖到展开态(expanded)
          // 0: 折叠态
          // < 0: 从折叠态向下拖到隐藏(hidden) (-1 表示完全隐藏)

          // 我们只关心向下拖拽的过程 (slideOffset < 0)
          if (slideOffset < 0) {
            // 默认的背景遮罩透明度通常是 0.6f (或者你主题里设置的值)
            val baseDimAmount = 0.4f

            // 计算新的透明度：随着 offset 变小(-1)，dim 也变小(0)
            // 1 + (-0.5) = 0.5 -> 剩一半透明度
            val newDim = baseDimAmount * (1 + slideOffset)

            // 实时设置给 Window，让背景跟着手指变淡
            // 注意：这就实现了“列表滑多少，背景就淡多少”的丝滑效果
            if (newDim >= 0) {
              dialog.window?.setDimAmount(newDim)
            }
          }
        }
      })
    }
    return dialog
  }

  override fun getTheme(): Int {
    return R.style.CustomBottomSheetDialogTheme
  }
}
