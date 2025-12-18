package com.language.repeater.playvideo.playlist

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import com.language.repeater.R
import com.language.repeater.databinding.PlaylistSheetItemBinding
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.utils.ResourcesUtil.toDp
import com.language.repeater.utils.TimeFormatUtil

class PlaylistAdapter(
  private val onItemClick: (Int) -> Unit,
  private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

  companion object {
    private val COVER_RADIUS = 6f.toDp()
    const val PAYLOAD_PLAY_STATE = "PAYLOAD_PLAY_STATE"
  }

  // 数据源
  private var items: List<MediaItem> = emptyList()

  // 状态
  var currentPlayingIndex: Int = -1
  var isPlayerPlaying: Boolean = false

  // 当前播放时的总时长 (用于显示当前条目的时间)
  var currentDuration: Long = C.TIME_UNSET

  @SuppressLint("NotifyDataSetChanged")
  fun submitList(newItems: List<MediaItem>) {
    this.items = newItems
    notifyDataSetChanged()
  }

  inner class ViewHolder(val binding: PlaylistSheetItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    init {
      binding.root.setOnClickListener {
        val pos = bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
          onItemClick(pos)
        }
      }
      binding.btnDelete.setOnClickListener {
        val pos = bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
          onDeleteClick(pos)
        }
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = PlaylistSheetItemBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
    if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_PLAY_STATE)) {
      bindPlayerState(holder, position)
    } else {
      super.onBindViewHolder(holder, position, payloads)
    }
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val mediaItem = items[position]
    val binding = holder.binding

    // 1. 设置标题
    val displayName = mediaItem.mediaMetadata.title?.toString()
      ?: mediaItem.localConfiguration?.uri?.lastPathSegment
      ?: "未知视频"
    binding.tvTitle.text = displayName

    // 2. 设置封面 (Coil)
    val uri = mediaItem.localConfiguration?.uri
    if (uri != null) {
      if (binding.ivCover.tag != uri) {
        binding.ivCover.tag = uri
        binding.ivCover.load(uri) {
          crossfade(true)
          // 假设你在 colors.xml 定义了 PastelGreen，如果没有请换成 R.drawable.xxx
          placeholder(R.color.PastelGreen)
          error(androidx.media3.session.R.drawable.media_session_service_notification_ic_music_note)
          transformations(RoundedCornersTransformation(COVER_RADIUS))
          scale(Scale.FILL)
        }
      }
    } else {
      binding.ivCover.tag = null
      binding.ivCover.setImageResource(androidx.media3.session.R.drawable.media_session_service_notification_ic_music_note)
    }

    bindPlayerState(holder, position)
  }

  private fun bindPlayerState(holder: ViewHolder, position: Int) {
    val binding = holder.binding
    val isCurrentItem = (position == currentPlayingIndex)

    // 1. 设置时长
    // 逻辑：如果是当前播放的，显示实时获取的时长；如果不是，显示隐藏 (除非你在 MediaItem metadata 里存了时长)
    if (isCurrentItem && currentDuration != C.TIME_UNSET) {
      binding.tvDuration.text = TimeFormatUtil.formatTimeMillis(currentDuration)
      binding.tvDuration.visibility = View.VISIBLE
    } else {
      binding.tvDuration.visibility = View.GONE
    }

    // 2. 激活状态背景
    binding.root.isActivated = isCurrentItem

    // 3. 样式与图标
    if (isCurrentItem) {
      binding.tvTitle.setTextColor(ResourcesUtil.getColor(R.color.PlayListTextColorSelected))
      binding.ivPlayState.visibility = View.VISIBLE

      if (isPlayerPlaying) {
        binding.ivPlayState.setImageResource(androidx.media3.session.R.drawable.media3_icon_play)
      } else {
        binding.ivPlayState.setImageResource(androidx.media3.session.R.drawable.media3_icon_pause)
      }
    } else {
      binding.tvTitle.setTextColor(ResourcesUtil.getColor(R.color.PlayListTextColor))
      binding.ivPlayState.visibility = View.INVISIBLE
    }
  }

  override fun getItemCount(): Int = items.size
}