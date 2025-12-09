package com.language.repeater.playvideo.history

import android.annotation.SuppressLint
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
import com.language.repeater.databinding.HistorySheetItemBinding
import com.language.repeater.databinding.PlaylistSheetItemBinding
import com.language.repeater.playvideo.model.VideoEntity
import com.language.repeater.playvideo.playlist.PlaylistAdapter.Companion.PAYLOAD_PLAY_STATE
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.utils.ResourcesUtil.toDp
import com.language.repeater.utils.TimeFormatUtil

class HistoryListAdapter(
  private val fragment: HistorySheetFragment,
  private val player: Player
) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

  companion object {
    private val COVER_RADIUS = 6f.toDp()
  }

  // ViewHolder 现在持有 Binding 对象
  inner class ViewHolder(val binding: HistorySheetItemBinding) : RecyclerView.ViewHolder(binding.root), View.OnClickListener {
    init {
      // --- 5. 点击事件 ---
      binding.root.setOnClickListener(this)
      binding.btnMore.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
      if (v == binding.root) {
        //当前列表如果正在播放这个条目, 无动作, 如果不是, 替换当前列表播放当前的
        val curPlayId = player.currentMediaItem?.mediaId
        val cur = data[bindingAdapterPosition]
        if (cur.id != curPlayId) {
          fragment.playVideoFragment.playComponent.addPlayUri(listOf(cur), true)
          player.seekTo(cur.positionMs)
        }
        fragment.dismiss()
      } else if (v == binding.btnMore) {
        //play current
        //add to next
      }
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    // 使用 ViewBinding 加载布局
    val binding = HistorySheetItemBinding.inflate(
      LayoutInflater.from(parent.context),
      parent,
      false
    )
    return ViewHolder(binding)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val mediaItem = data[position]
    val binding = holder.binding // 获取 binding 引用，减少重复代码

    // --- 1. 设置标题 ---
    binding.tvTitle.text = mediaItem.name

    // --- 3. 设置封面 (Coil) ---
    val uri = mediaItem.uri
    if (uri.isNotEmpty()) {
      if (binding.ivCover.tag != uri) {
        Log.i("PlaylistSheetFragment", "binding.ivCover.tag 封面变化了")
        binding.ivCover.tag = uri // 记录当前加载的 URI
        binding.ivCover.load(uri) {
          crossfade(true)
          placeholder(R.color.PastelOrange)
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

  override fun getItemCount(): Int {
    return data.size
  }

  private val data: MutableList<VideoEntity> = mutableListOf()

  @SuppressLint("NotifyDataSetChanged")
  fun setData(list: List<VideoEntity>) {
    data.clear()
    data.addAll(list)
    notifyDataSetChanged()
  }
}