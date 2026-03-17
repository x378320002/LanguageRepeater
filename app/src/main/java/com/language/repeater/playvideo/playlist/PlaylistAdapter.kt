package com.language.repeater.playvideo.playlist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import com.language.repeater.R
import com.language.repeater.databinding.PlaylistSheetItemBinding
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.utils.TimeFormatUtil

class PlaylistAdapter(
  private val onItemClick: (Int) -> Unit,
  private val onMenuAction: (Int, Int) -> Unit
) : ListAdapter<MediaItem, PlaylistAdapter.ViewHolder>(MediaItemDiffCallback()) {

  companion object {
    const val PAYLOAD_PLAY_STATE = "PAYLOAD_PLAY_STATE"
  }

  var currentPlayingIndex: Int = -1
  var isPlayerPlaying: Boolean = false
  var currentDuration: Long = C.TIME_UNSET

  inner class ViewHolder(val binding: PlaylistSheetItemBinding) :
    RecyclerView.ViewHolder(binding.root) {

    init {
      binding.root.setOnClickListener {
        val pos = bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
          onItemClick(pos)
        }
      }
      binding.btnMore.setOnClickListener {
        val pos = bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
          showPopupMenu(it, pos)
        }
      }
    }

    private fun showPopupMenu(anchor: View, position: Int) {
      val popup = ResourcesUtil.createLightPopMenu(anchor.context, anchor)
      popup.menuInflater.inflate(R.menu.menu_playlist_item, popup.menu)
      if (position == 0) {
        popup.menu.findItem(R.id.action_move_to_first)?.isEnabled = false
      }
      popup.setOnMenuItemClickListener { menuItem ->
        onMenuAction(position, menuItem.itemId)
        true
      }
      popup.show()
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
    val mediaItem = getItem(position)
    val binding = holder.binding

    val displayName = mediaItem.mediaMetadata.title?.toString()
      ?: mediaItem.localConfiguration?.uri?.lastPathSegment
      ?: "未知视频"
    binding.tvTitle.text = displayName

    val uri = (mediaItem.mediaMetadata.artworkUri ?: mediaItem.localConfiguration?.uri)?.toString()

    if (uri != null && uri.isNotEmpty()) {
      if (binding.ivCover.tag != uri) {
        binding.ivCover.tag = uri
        binding.ivCover.load(uri) {
          crossfade(true)
          error(R.drawable.ic_music)
        }
      }
    } else {
      binding.ivCover.tag = null
      binding.ivCover.setImageResource(R.drawable.ic_music)
    }

    bindPlayerState(holder, position)
  }

  private fun bindPlayerState(holder: ViewHolder, position: Int) {
    val binding = holder.binding
    val isCurrentItem = (position == currentPlayingIndex)

    if (isCurrentItem && currentDuration != C.TIME_UNSET) {
      binding.tvDuration.text = TimeFormatUtil.formatTimeMillis(currentDuration)
      binding.tvDuration.visibility = View.VISIBLE
    } else {
      binding.tvDuration.visibility = View.GONE
    }

    binding.root.isActivated = isCurrentItem

    if (isCurrentItem) {
      binding.ivPlayState.visibility = View.VISIBLE
      if (isPlayerPlaying) {
        binding.ivPlayState.setImageResource(R.drawable.ic_play)
      } else {
        binding.ivPlayState.setImageResource(R.drawable.ic_pause)
      }
    } else {
      binding.ivPlayState.visibility = View.INVISIBLE
    }
  }
}

class MediaItemDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
  override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
    return oldItem.mediaId == newItem.mediaId
  }

  override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
    return oldItem.mediaId == newItem.mediaId
  }
}
