package com.language.repeater.playvideo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.language.repeater.databinding.VideoPlayFragmentBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class PlayVideoFragment: Fragment() {
  private var _binding: VideoPlayFragmentBinding? = null
  private val binding get() = _binding!!

  private var exoPlayer: ExoPlayer? = null
  private val viewModel: PlayVideoViewModel by viewModels()

  //当前所有的语音片段
  private var voiceSegments = listOf<Pair<Float, Float>>()
  //当前正在读的语音片段
  private var curSegment: Pair<Float, Float>? = null

  val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult(),
    object: ActivityResultCallback<ActivityResult> {
      override fun onActivityResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
          val videoUri = result.data?.data
          if (videoUri != null) {
            //这里你可以用 videoUri 播放视频或读取内容
            Log.d("VideoSelect", "Selected video uri: $videoUri")
            binding.filePathTv.text = videoUri.toString()
            viewModel.parseUriToPcm(videoUri)
          }
        }
      }
    })

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    exoPlayer = ExoPlayer.Builder(requireContext()).build().apply {
      repeatMode = Player.REPEAT_MODE_ALL
    }
    lifecycleScope.launch {
      viewModel.playUriStateFlow.collect {
        if (it != null) {
          val mediaItem = MediaItem.fromUri(it)
          exoPlayer?.setMediaItem(mediaItem)
          exoPlayer?.prepare()
          if (isResumed) {
            exoPlayer?.play()
          }
        }
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = VideoPlayFragmentBinding.inflate(inflater, container, false)
    val view = binding.root
    return view
  }

  @OptIn(UnstableApi::class)
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }

    binding.selectFileBtn.setOnClickListener {
      val intent = Intent(Intent.ACTION_PICK).apply {
        type = "video/*" // 只选择视频
      }
      launcher.launch(intent)
    }

    binding.exoVideoView.player = exoPlayer
    binding.exoVideoView.showController()

    binding.voiceNext.setOnClickListener {
      seekToNextOrPreSegment(true)
    }
    binding.voicePrevious.setOnClickListener {
      seekToNextOrPreSegment(false)
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          viewModel.pcmLoaderStateFlow.collect { loader ->
            if (loader != null) {
              // 加载PCM文件
              binding.audioProgressWaveView.setPCMLoader(loader)
              binding.audioWaveView.setPcmData(loader.allData)
              voiceSegments = loader.getVoiceSegments()
              curSegment = voiceSegments.getOrNull(0)
//              binding.audioProgressWaveView.onSeekListener = { position ->
//                when {
//                  position < 0 -> {
//                    // 开始拖动，暂停播放
//                    //userIsSeeking = true
//                    if (exoPlayer?.isPlaying == true) {
//                      exoPlayer?.pause()
//                    }
//                  }
//                  else -> {
//                    // 拖动结束，跳转到新位置
//                    //userIsSeeking = false
//                    view.post {
//                      exoPlayer?.seekTo((position * 1000).toLong())
//                      exoPlayer?.play()
//                    }
//                  }
//                }
//              }
            }
          }
        }

        launch {
          while (true) {
            if (exoPlayer?.isPlaying == true) {
              var cur = exoPlayer?.currentPosition?.toFloat() ?: -1f
              if (cur != -1f) {
                var curSec = cur / 1000
                val seg = curSegment
                if (seg != null) {
                  if (curSec >= seg.second) {
                    //跳回开始
                    exoPlayer?.seekTo((seg.first * 1000).toLong())
                    curSec = seg.first
                    cur = seg.first * 1000
                  }
                }

                val duration = exoPlayer?.duration ?: -1
                if (duration > 0) {
                  binding.audioProgressWaveView.updatePosition(curSec)
                  binding.audioWaveView.updatePosition(cur/duration)
                }
              }
            }
            delay(16)
          }
        }
      }
    }
  }

  private fun seekToNextOrPreSegment(isNext: Boolean) {
    val segments = voiceSegments
    val player = exoPlayer
    if (segments.isNotEmpty() && player != null) {
      val cur = player.currentPosition.toFloat() / 1000
      //计算当前是哪句
      var targetSeg: Pair<Float, Float>? = null
      for (i in segments.indices) {
        val seg = segments[i]
        if (cur >= seg.first && cur <= seg.second) {
          targetSeg = if (isNext) {
            segments.getOrNull(i + 1)
          } else {
            segments.getOrNull(i - 1)
          }
          break
        }
      }
      seekToSegment(targetSeg)
    }
  }

  private fun seekToSegment(segmentation: Pair<Float, Float>?) {
    curSegment = segmentation
    if (segmentation != null) {
      exoPlayer?.seekTo((segmentation.first * 1000).toLong())
    }
  }

  override fun onPause() {
    super.onPause()
    exoPlayer?.pause()
  }

  override fun onResume() {
    super.onResume()
    exoPlayer?.play()
  }

  override fun onDestroyView() {
    super.onDestroyView()
    exoPlayer?.stop()
  }

  override fun onDestroy() {
    super.onDestroy()
    exoPlayer?.release()
    exoPlayer = null
  }
}