package com.language.repeater.playvideo.playlist

import android.annotation.SuppressLint
import com.language.repeater.foundation.BasePlaySheetFragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.language.repeater.R
import com.language.repeater.databinding.PlaylistSheetFragmentBinding
import com.language.repeater.playvideo.PlayerViewModel
import com.language.repeater.playvideo.model.isPlaceHold
import com.language.repeater.datastore.DsManager
import kotlinx.coroutines.launch

class PlaylistSheetFragment : BasePlaySheetFragment() {
  val viewModel: PlayerViewModel by activityViewModels()

  private var _binding: PlaylistSheetFragmentBinding? = null
  private val binding get() = _binding!!

  private var currentPlayer: Player? = null
  private lateinit var adapter: PlaylistAdapter
  private var currentRepeatMode: Int = Player.REPEAT_MODE_ONE

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

    setupUi()
    observeViewModel()
  }

  private fun setupUi() {
    binding.tvSheetTitle.setText(R.string.current_list_title)
    binding.repeatMode.visibility = View.VISIBLE
    binding.repeatMode.setOnClickListener {
      when (currentRepeatMode) {
        Player.REPEAT_MODE_OFF -> {
          viewModel.setPlayerRepeatMode(Player.REPEAT_MODE_ONE)
        }
        Player.REPEAT_MODE_ONE -> {
          viewModel.setPlayerRepeatMode(Player.REPEAT_MODE_ALL)
        }
        else -> {
          viewModel.setPlayerRepeatMode(Player.REPEAT_MODE_OFF)
        }
      }
    }

    adapter = PlaylistAdapter(
      onItemClick = { index ->
        if (index == adapter.currentPlayingIndex) {
          viewModel.togglePlayPause()
        } else {
          viewModel.saveCurrentPos()
          viewModel.playItem(index)
        }
      },
      onMenuAction = { index, actionId ->
        when (actionId) {
          R.id.action_move_to_first -> viewModel.moveItemToFirst(index)
          R.id.action_delete -> viewModel.deleteItem(index)
        }
      }
    )

    binding.rvPlaylist.apply {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(context)
      this.adapter = this@PlaylistSheetFragment.adapter
      (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    val dragCallback = PlaylistDragCallback(adapter, this)
    val itemTouchHelper = ItemTouchHelper(dragCallback)
    itemTouchHelper.attachToRecyclerView(binding.rvPlaylist)
  }

  @SuppressLint("SetTextI18n")
  private fun observeViewModel() {
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          DsManager.observe(DsManager.KEY_PLAYER_PLAY_MODE, 1).collect {
            currentRepeatMode = it
            when (it) {
              Player.REPEAT_MODE_ONE -> {
                binding.repeatMode.setImageResource(R.drawable.ic_repeat_one)
              }
              Player.REPEAT_MODE_ALL -> {
                binding.repeatMode.setImageResource(R.drawable.ic_repeat_all)
              }
              else -> {
                binding.repeatMode.setImageResource(R.drawable.ic_repeat_off)
              }
            }
          }
        }

        launch {
          viewModel.playerInstance.collect { p ->
            currentPlayer = p
            refreshFullList()
            updateListState()
          }
        }

        launch {
          viewModel.playlistRefreshEvent.collect {
            refreshFullList()
            binding.tvCount.text = "(${viewModel.mediaItemCount.value})"
          }
        }

        launch {
          viewModel.currentMediaItem.collect {
            updateListState()
            scrollToCurrent()
          }
        }

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

  private fun refreshFullList() {
    val player = currentPlayer ?: return
    val currentItems = ArrayList<MediaItem>()
    for (i in 0 until player.mediaItemCount) {
      val item = player.getMediaItemAt(i)
      if (!item.isPlaceHold()) {
        currentItems.add(item)
      }
    }
    adapter.submitList(currentItems) {
      updateListState()
    }
  }

  /**
   * 更新 Adapter 的高亮索引和播放状态
   */
  private fun updateListState() {
    val player = currentPlayer ?: return

    // 当前播放项是 PlaceHold（循环过渡瞬间），索引对不上 Adapter，跳过本次更新
    if (player.currentMediaItem?.isPlaceHold() == true) {
      return
    }

    val oldIndex = adapter.currentPlayingIndex
    val newIndex = player.currentMediaItemIndex

    adapter.currentPlayingIndex = newIndex
    adapter.isPlayerPlaying = viewModel.isUiPlaying.value
    // 直接传 player.duration，保留 TIME_UNSET 语义，由 Adapter 判断是否显示时长
    adapter.currentDuration = player.duration

    if (oldIndex != -1 && oldIndex < adapter.itemCount) {
      adapter.notifyItemChanged(oldIndex, PlaylistAdapter.PAYLOAD_PLAY_STATE)
    }
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
