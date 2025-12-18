package com.language.repeater.playvideo.playlist

import android.annotation.SuppressLint
import androidx.media3.common.C
import com.language.repeater.playvideo.BasePlaySheetFragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.language.repeater.databinding.PlaylistSheetFragmentBinding
import com.language.repeater.playvideo.PlayerViewModel // 确保导入正确的 VM
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.launch

class PlaylistSheetFragment : BasePlaySheetFragment() {
  // 共享 Activity 级别的 ViewModel
  private val viewModel: PlayerViewModel by activityViewModels()

  private var _binding: PlaylistSheetFragmentBinding? = null
  private val binding get() = _binding!!

  private var player: Player? = null
  private lateinit var adapter: PlaylistAdapter

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = PlaylistSheetFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    setupRecyclerView()
    observeViewModel()
  }

  private fun setupRecyclerView() {
    // 初始化 Adapter，传入点击回调
    adapter = PlaylistAdapter(
      onItemClick = { index ->
        // 点击播放/暂停或切歌
        if (index == adapter.currentPlayingIndex) {
          viewModel.togglePlayPause()
        } else {
          viewModel.playItem(index)
        }
      },
      onDeleteClick = { index ->
        // 删除条目
        viewModel.deleteItem(index)
      }
    )

    binding.rvPlaylist.apply {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(context)
      this.adapter = this@PlaylistSheetFragment.adapter
      // 禁用变更动画防止闪烁
      (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }
  }

  @SuppressLint("SetTextI18n")
  private fun observeViewModel() {
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        // 1. 监听连接状态 & 初始化数据
        // 只有连接成功后，才能获取到 Player 里的列表数据
        launch {
          viewModel.playerState.collect { p ->
            player = p
            refreshFullList() // 初始加载列表
            updateListState() // 初始同步状态
          }
        }

        // 2. 监听列表内容刷新事件 (增删、排序、加载字幕)
        launch {
          viewModel.playlistRefreshEvent.collect {
            refreshFullList()
            binding.tvCount.text = "(${viewModel.mediaItemCount.value})"
          }
        }

        // 3. 监听切歌 (MediaItemTransition)
        launch {
          viewModel.currentMediaItem.collect {
            updateListState()
            scrollToCurrent()
          }
        }

        // 4. 监听播放状态 (UI防抖状态)
        launch {
          viewModel.isUiPlaying.collect { isPlaying ->
            if (adapter.isPlayerPlaying != isPlaying) {
              adapter.isPlayerPlaying = isPlaying
              notifyCurrentItemChanged()
            }
          }
        }
      }
    }
  }

  /**
   * 从 Player 获取完整列表并提交给 Adapter
   * 注意：viewModel.getPlayer() 可能为空，需判空
   */
  private fun refreshFullList() {
    val player = player ?: return
    val currentItems = ArrayList<MediaItem>()
    for (i in 0 until player.mediaItemCount) {
      currentItems.add(player.getMediaItemAt(i))
    }
    adapter.submitList(currentItems)
  }

  /**
   * 更新 Adapter 的高亮索引和播放状态
   */
  private fun updateListState() {
    val player = player ?: return

    val oldIndex = adapter.currentPlayingIndex
    val newIndex = player.currentMediaItemIndex

    adapter.currentPlayingIndex = newIndex
    adapter.isPlayerPlaying = viewModel.isUiPlaying.value
    // 更新当前时长以便 Adapter 显示
    adapter.currentDuration = if (player.duration != C.TIME_UNSET) player.duration else 0L

    // 刷新旧位置 (取消高亮)
    if (oldIndex != -1 && oldIndex < adapter.itemCount) {
      adapter.notifyItemChanged(oldIndex, PlaylistAdapter.PAYLOAD_PLAY_STATE)
    }
    // 刷新新位置 (设置高亮)
    if (newIndex != -1 && newIndex < adapter.itemCount) {
      adapter.notifyItemChanged(newIndex, PlaylistAdapter.PAYLOAD_PLAY_STATE)
    }
  }

  private fun notifyCurrentItemChanged() {
    val index = adapter.currentPlayingIndex
    if (index != -1) {
      adapter.notifyItemChanged(index, PlaylistAdapter.PAYLOAD_PLAY_STATE)
    }
  }

  private fun scrollToCurrent() {
    val player = viewModel.getPlayer() ?: return
    val index = player.currentMediaItemIndex
    if (index != -1) {
      binding.rvPlaylist.smoothScrollToPosition(index)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
