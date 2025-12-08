package com.language.repeater.playvideo.playlist

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.launch

@SuppressLint("SetTextI18n")
class PlaylistSheetFragment(
  private val player: Player
) : BottomSheetDialogFragment() {

  companion object {
    const val TAG = PlayVideoFragment.TAG
  }

  // ViewBinding 变量
  private var _binding: PlaylistSheetFragmentBinding? = null
  // 这个属性只在 onCreateView 和 onDestroyView 之间有效
  private val binding get() = _binding!!

  private lateinit var adapter: PlaylistAdapter

  private val playerListener = object : Player.Listener {
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      Log.i("PlaylistSheetFragment", "onMediaItemTransition")
      updateListState()
      val newIndex = player.currentMediaItemIndex
      if (newIndex != -1) {
        // 使用 binding 访问 RecyclerView
        binding.rvPlaylist.smoothScrollToPosition(newIndex)
      }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      val isPlayingState = playingState()
      if (adapter.isPlayerPlaying != isPlayingState) {
        Log.i(TAG, "onIsPlayingChanged isPlayingState:$isPlayingState")
        adapter.isPlayerPlaying = isPlayingState
        val currentIndex = adapter.currentPlayingIndex
        if (currentIndex != -1) {
          adapter.notifyItemChanged(currentIndex, PlaylistAdapter.PAYLOAD_PLAY_STATE)
        }
      }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
      if (reason == TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
        "(${player.mediaItemCount})".also { binding.tvCount.text = it }
        adapter.notifyDataSetChanged()
      } else if (reason == TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
        //当前条目本来没有时间信息, 解析完获取到时间信息时, 会回调这个方法
        Log.i(TAG, "onTimelineChanged TIMELINE_CHANGE_REASON_SOURCE_UPDATE")
        val currentIndex = adapter.currentPlayingIndex
        if (currentIndex != -1) {
          adapter.notifyItemChanged(currentIndex, PlaylistAdapter.PAYLOAD_PLAY_STATE)
        }
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    // 初始化 ViewBinding
    _binding = PlaylistSheetFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // 初始化 Adapter
    adapter = PlaylistAdapter(player)

    //设置界面高度
    val displayMetrics = resources.displayMetrics
    val fixedHeight = (displayMetrics.heightPixels * 0.55f).toInt()
    val layoutParams = binding.root.layoutParams
    layoutParams.height = fixedHeight
    binding.root.layoutParams = layoutParams

    binding.rvPlaylist.setHasFixedSize(true)
    // 这样即使调用不带 payload 的 notifyItemChanged，也不会有闪烁（Cross-fade）效果
    (binding.rvPlaylist.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

    // 设置 RecyclerView
    binding.rvPlaylist.layoutManager = LinearLayoutManager(context)
    binding.rvPlaylist.adapter = adapter

    // 初始同步状态
    updateListState()
    binding.tvCount.text = "(${player.mediaItemCount})"

    // 注册监听
    player.addListener(playerListener)

    // 延迟滚动
    binding.root.post {
      if (player.currentMediaItemIndex != -1) {
        binding.rvPlaylist.scrollToPosition(player.currentMediaItemIndex)
      }
    }
  }

  private fun updateListState() {
    val oldIndex = adapter.currentPlayingIndex
    val newIndex = player.currentMediaItemIndex

    adapter.currentPlayingIndex = newIndex
    adapter.isPlayerPlaying = playingState()

    if (oldIndex != -1) adapter.notifyItemChanged(oldIndex, PlaylistAdapter.PAYLOAD_PLAY_STATE)
    if (newIndex != -1) adapter.notifyItemChanged(newIndex, PlaylistAdapter.PAYLOAD_PLAY_STATE)
  }

  private fun playingState(): Boolean {
    // 1. 如果真的在播放，肯定是 true
    if (player.isPlaying) return true

    // 2. 【防抖核心】如果正在缓冲(切歌中)，且用户意图是“播放(playWhenReady=true)”，
    //    那么 UI 上也应该保持“播放中”的状态，不要闪成暂停。
    if (player.playbackState == Player.STATE_BUFFERING && player.playWhenReady) {
      return true
    }

    return false
  }

  override fun onDestroyView() {
    super.onDestroyView()
    player.removeListener(playerListener)
    // 释放 binding，防止 Fragment 视图销毁但对象残留导致的内存泄漏
    _binding = null
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
