package com.language.repeater.playvideo.history

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED
import androidx.media3.common.Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE
import androidx.media3.common.Timeline
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.dotlottie.dlplayer.Fit
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.language.repeater.R
import com.language.repeater.databinding.PlaylistSheetFragmentBinding
import com.language.repeater.playvideo.BasePlaySheetFragment
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.playvideo.playlist.PlaylistAdapter
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.LayoutUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("SetTextI18n")
class HistorySheetFragment(
  internal val playVideoFragment: PlayVideoFragment,
  private val player: Player
) : BasePlaySheetFragment(player) {

  // ViewBinding 变量
  private var _binding: PlaylistSheetFragmentBinding? = null
  // 这个属性只在 onCreateView 和 onDestroyView 之间有效
  private val binding get() = _binding!!

  private lateinit var adapter: HistoryListAdapter

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
    adapter = HistoryListAdapter(this, player)
    binding.rvPlaylist.setHasFixedSize(true)
    // 这样即使调用不带 payload 的 notifyItemChanged，也不会有闪烁（Cross-fade）效果
    (binding.rvPlaylist.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

    // 设置 RecyclerView
    binding.rvPlaylist.layoutManager = LinearLayoutManager(context)
    binding.rvPlaylist.adapter = adapter

    // 初始同步状态
    binding.tvCount.visibility = View.GONE
    binding.tvSheetTitle.text = "历史记录"

    loadHistory()
  }

  private fun loadHistory() {
    viewLifecycleOwner.lifecycleScope.launch {
      showLoading()
      withContext(Dispatchers.IO) {
        HistoryManager.observeHistory(requireContext()).collect {
          Log.i(TAG, "loadHistory size = ${it.size}")
          adapter.setData(it)
          cancel()
        }
      }
      hideLoading()
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    // 释放 binding，防止 Fragment 视图销毁但对象残留导致的内存泄漏
    _binding = null
  }

  private fun showLoading() {
    val config = Config.Builder()
      .useFrameInterpolation(true)
      .autoplay(true)
      .speed(1f)
      .loop(true)
      .layout(fit = Fit.FIT_WIDTH, LayoutUtil.Alignment.Center)
      .source(DotLottieSource.Asset("material_wave_loading.lottie")) // asset from the asset folder .json or .lottie
      .build()
    binding.lottieAnimation.load(config)
    binding.lottieAnimation.play()
  }

  private fun hideLoading() {
    binding.lottieAnimation.stop()
  }
}
