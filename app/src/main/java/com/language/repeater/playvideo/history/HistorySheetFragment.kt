package com.language.repeater.playvideo.history

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.dotlottie.dlplayer.Fit
import com.language.repeater.R
import com.language.repeater.databinding.PlaylistSheetFragmentBinding
import com.language.repeater.foundation.BasePlaySheetFragment
import com.language.repeater.playvideo.PlayerViewModel
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.LayoutUtil
import kotlinx.coroutines.launch

class HistorySheetFragment : BasePlaySheetFragment() {

  private val viewModel: PlayerViewModel by activityViewModels()

  private var _binding: PlaylistSheetFragmentBinding? = null
  private val binding get() = _binding!!

  private lateinit var adapter: HistoryListAdapter

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

    setupUI()
    observeData()
  }

  private fun setupUI() {
    binding.tvCount.visibility = View.GONE

    // 初始化 Adapter，传入回调
    adapter = HistoryListAdapter(
      onItemClick = { item ->
        viewModel.playHistoryItem(item)
        dismiss() // 播放后关闭弹窗
      },
      onMenuAction = { item, actionId ->
        when (actionId) {
          R.id.action_play -> {
            viewModel.playHistoryItem(item)
            dismiss()
          }
          //R.id.action_next_play -> viewModel.addNext(item)
          //R.id.action_add_more -> viewModel.addToEnd(item)
          R.id.action_delete -> viewModel.deleteHistory(item)
        }
      }
    )

    binding.rvPlaylist.apply {
      setHasFixedSize(true)
      layoutManager = LinearLayoutManager(context)
      this.adapter = this@HistorySheetFragment.adapter
      (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    binding.repeatMode.visibility = View.GONE
  }

  private fun observeData() {
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        // 显示 Loading
        showLoading()

        // 监听历史记录数据流
        // Flow 会在数据库变化时自动发射新数据，无需手动 refresh
        viewModel.getHistoryFlow().collect { historyList ->
          if (historyList.isNotEmpty()) {
            hideLoading()
          } else {
            // 可以处理空状态显示
            hideLoading()
          }
          for (i in historyList.indices) {
            val h = historyList[i]
            Log.i(TAG, "history: $i, time: ${h.history.lastPlayedTime}, title:${h.videoInfo.name}")
          }
          adapter.submitList(historyList.map { it.videoInfo })
        }
      }
    }
  }

  private fun showLoading() {
    binding.lottieAnimation.visibility = View.VISIBLE
    val config = Config.Builder()
      .useFrameInterpolation(true)
      .autoplay(true)
      .speed(1f)
      .loop(true)
      .layout(fit = Fit.FIT_WIDTH, LayoutUtil.Alignment.Center)
      .source(DotLottieSource.Asset("material_wave_loading.lottie"))
      .build()
    binding.lottieAnimation.load(config)
  }

  private fun hideLoading() {
    binding.lottieAnimation.stop()
    binding.lottieAnimation.visibility = View.GONE
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
