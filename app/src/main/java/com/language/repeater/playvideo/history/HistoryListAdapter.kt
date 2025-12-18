package com.language.repeater.playvideo.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.size.Scale
import coil3.transform.RoundedCornersTransformation
import com.language.repeater.R
import com.language.repeater.databinding.HistorySheetItemBinding
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.utils.ResourcesUtil.toDp

class HistoryListAdapter(
  private val onItemClick: (VideoEntity) -> Unit,
  private val onMenuAction: (VideoEntity, Int) -> Unit
) : ListAdapter<VideoEntity, HistoryListAdapter.ViewHolder>(VideoEntityDiffCallback()) {

  companion object {
    private val COVER_RADIUS = 6f.toDp()
  }

  inner class ViewHolder(val binding: HistorySheetItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    init {
      binding.root.setOnClickListener {
        val pos = bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
          onItemClick(getItem(pos))
        }
      }
      binding.btnMore.setOnClickListener {
        val pos = bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
          showPopupMenu(it, getItem(pos))
        }
      }
    }

    private fun showPopupMenu(anchor: View, item: VideoEntity) {
      val popup = PopupMenu(anchor.context, anchor)
      popup.menuInflater.inflate(R.menu.menu_history_item, popup.menu)
      popup.setOnMenuItemClickListener { menuItem ->
        onMenuAction(item, menuItem.itemId)
        true
      }
      popup.show()
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val binding = HistorySheetItemBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val item = getItem(position)
    val binding = holder.binding

    binding.tvTitle.text = item.name

    // 显示时间信息 (示例)
    //val dateStr = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.lastPlayedTime))
    //val posStr = TimeFormatUtil.formatTimeMillis(item.positionMs)
    //binding.tvInfo.text = "$dateStr • 进度 $posStr" // 假设 item_history_entry 有 tvInfo

    // 加载封面
    if (item.uri.isNotEmpty()) {
      if (binding.ivCover.tag != item.uri) {
        binding.ivCover.tag = item.uri
        binding.ivCover.load(item.uri) {
          crossfade(true)
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
  }
}

// DiffUtil 回调，用于 ListAdapter 高效刷新
class VideoEntityDiffCallback : DiffUtil.ItemCallback<VideoEntity>() {
  override fun areItemsTheSame(oldItem: VideoEntity, newItem: VideoEntity): Boolean {
    return oldItem == newItem
  }

  override fun areContentsTheSame(oldItem: VideoEntity, newItem: VideoEntity): Boolean {
    return oldItem == newItem
  }
}