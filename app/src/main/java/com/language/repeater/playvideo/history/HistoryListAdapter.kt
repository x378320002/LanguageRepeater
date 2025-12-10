package com.language.repeater.playvideo.history

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
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
import com.language.repeater.playvideo.model.toMediaItem
import com.language.repeater.playvideo.playlist.PlaylistAdapter.Companion.PAYLOAD_PLAY_STATE
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.utils.ResourcesUtil.toDp
import com.language.repeater.utils.TimeFormatUtil
import kotlinx.coroutines.launch

class HistoryListAdapter(
  private val fragment: HistorySheetFragment,
  private val player: Player,
) : RecyclerView.Adapter<HistoryListAdapter.ViewHolder>() {

  companion object {
    private val COVER_RADIUS = 6f.toDp()
  }

  // ViewHolder 现在持有 Binding 对象
  inner class ViewHolder(val binding: HistorySheetItemBinding) :
    RecyclerView.ViewHolder(binding.root), View.OnClickListener {
    init {
      // --- 5. 点击事件 ---
      binding.root.setOnClickListener(this)
      binding.btnMore.setOnClickListener(this)
    }

    override fun onClick(v: View) {
      if (v == binding.root) {
        //当前列表如果正在播放这个条目, 无动作, 如果不是, 替换当前列表播放当前的
        val curPlayId = player.currentMediaItem?.mediaId
        val cur = data[bindingAdapterPosition]
        if (cur.id != curPlayId) {
          val mediaItem = cur.toMediaItem()
          player.setMediaItem(mediaItem)
          player.prepare()
          player.seekTo(cur.positionMs)
        } else {
          player.play()
        }
        fragment.dismiss()
      } else if (v == binding.btnMore) {
        showPopupMenu(v, bindingAdapterPosition)
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


  private fun showPopupMenu(anchor: View, position: Int) {
    val item = data[position]
    // 创建 PopupMenu，锚点是点击的那个图标
    val popup = PopupMenu(anchor.context, anchor)

    // 填充菜单布局
    popup.menuInflater.inflate(R.menu.menu_history_item, popup.menu)

    // 设置点击监听
    popup.setOnMenuItemClickListener { menuItem ->
      // 将点击事件传给外部处理
      when (menuItem.itemId) {
        R.id.action_play -> {
          val curPlayId = player.currentMediaItem?.mediaId
          if (item.id != curPlayId) {
            val mediaItem = item.toMediaItem()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.seekTo(item.positionMs)
          } else {
            player.play()
          }
        }

        R.id.action_next_play -> {
          val mediaItem = item.toMediaItem()
          if (player.mediaItemCount > 0) {
            val curIndex = player.currentMediaItemIndex
            player.addMediaItem(curIndex + 1, mediaItem)
          } else {
            player.setMediaItem(mediaItem)
          }
        }

        R.id.action_add_more -> {
          val mediaItem = item.toMediaItem()
          player.addMediaItem(mediaItem)
        }

        R.id.action_delete -> {
          fragment.lifecycleScope.launch {
            HistoryManager.deleteHistory(anchor.context, item)
            data.remove(item)
            notifyItemRemoved(position)
          }
        }
      }
      true // 返回 true 表示事件已处理
    }
    // 显示菜单
    popup.show()
  }
}