package com.language.repeater.playvideo.playlist

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.Player
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
  private val player: Player
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

  companion object {
    private val COVER_RADIUS = 6f.toDp()

    // 定义 Payload 常量
    const val PAYLOAD_PLAY_STATE = "PAYLOAD_PLAY_STATE"
  }

  // 当前高亮的索引
  var currentPlayingIndex: Int = -1

  // 播放器状态 (true=播放中, false=暂停)
  var isPlayerPlaying: Boolean = false

  // ViewHolder 现在持有 Binding 对象
  inner class ViewHolder(val binding: PlaylistSheetItemBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {
    init {
      // --- 5. 点击事件 ---
      binding.root.setOnClickListener(this)
      binding.btnDelete.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
      if (v == binding.root) {
        if (bindingAdapterPosition == currentPlayingIndex) {
          if (player.isPlaying) player.pause() else player.play()
        } else {
          player.seekToDefaultPosition(bindingAdapterPosition)
          //player.prepare()
          //player.play()
        }
      } else if (v == binding.btnDelete) {
        // 获取当前的绝对位置 (防止列表刷新后位置不对)
        val currentPos = bindingAdapterPosition
        if (currentPos != RecyclerView.NO_POSITION && currentPos in 0 until player.mediaItemCount) {
          player.removeMediaItem(currentPos)
        }
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    // 使用 ViewBinding 加载布局
    val binding = PlaylistSheetItemBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return ViewHolder(binding)
  }

  // 处理局部刷新的核心方法
  override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
    if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_PLAY_STATE)) {
      // 如果 Payload 包含播放状态更新，仅刷新状态 UI，跳过图片加载
      bindPlayerState(holder, position)
    } else {
      // 否则执行全量绑定
      super.onBindViewHolder(holder, position, payloads)
    }
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val mediaItem = player.getMediaItemAt(position)
    val binding = holder.binding // 获取 binding 引用，减少重复代码

    // --- 1. 设置标题 ---
    val displayName = mediaItem.mediaMetadata.title?.toString()
      ?: mediaItem.localConfiguration?.uri?.lastPathSegment
      ?: "未知视频"
    binding.tvTitle.text = displayName

    // --- 2. 设置时长 ---
    if (position == currentPlayingIndex && player.duration != C.TIME_UNSET) {
      binding.tvDuration.text = TimeFormatUtil.formatTimeMillis(player.duration)
      binding.tvDuration.visibility = View.VISIBLE
    } else {
      binding.tvDuration.visibility = View.GONE
    }

    // --- 3. 设置封面 (Coil) ---
    val uri = mediaItem.localConfiguration?.uri
    if (uri != null) {
      if (binding.ivCover.tag != uri) {
        Log.i("PlaylistSheetFragment", "binding.ivCover.tag 封面变化了")
        binding.ivCover.tag = uri // 记录当前加载的 URI
        binding.ivCover.load(uri) {
          crossfade(true)
          placeholder(R.color.PastelGreen)
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

  // 提取出来的状态绑定逻辑，供全量刷新和局部刷新复用
  private fun bindPlayerState(holder: ViewHolder, position: Int) {
    val binding = holder.binding
    val isCurrentItem = (position == currentPlayingIndex)

    // 1. 设置时长 (仅当前播放项显示)
    if (isCurrentItem && player.duration != C.TIME_UNSET) {
      binding.tvDuration.text = TimeFormatUtil.formatTimeMillis(player.duration)
      binding.tvDuration.visibility = View.VISIBLE
    } else {
      binding.tvDuration.visibility = View.GONE
    }

    // XML 中的 selector 会根据这个状态自动切换背景颜色，且保留点击水波纹
    binding.root.isActivated = isCurrentItem

    // 2. 样式与图标
    if (isCurrentItem) {
      // 高亮状态
      binding.tvTitle.setTextColor(ResourcesUtil.getColor(R.color.PlayListTextColorSelected))
      binding.ivPlayState.visibility = View.VISIBLE
      if (isPlayerPlaying) {
        binding.ivPlayState.setImageResource(androidx.media3.session.R.drawable.media3_icon_play)
      } else {
        binding.ivPlayState.setImageResource(androidx.media3.session.R.drawable.media3_icon_pause)
      }
    } else {
      // 普通状态
      binding.tvTitle.setTextColor(ResourcesUtil.getColor(R.color.PlayListTextColor))
      binding.ivPlayState.visibility = View.INVISIBLE
    }
  }

  override fun getItemCount(): Int {
    return player.mediaItemCount
  }
}