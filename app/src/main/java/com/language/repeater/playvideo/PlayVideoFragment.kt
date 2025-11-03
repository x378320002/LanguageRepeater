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
import com.language.repeater.loading.LoadingDialogFragment
import com.language.repeater.pcm.Sentence
import com.language.repeater.widgets.ScrollingWaveformView
import com.language.repeater.widgets.ScrollingWaveformView.ABHitResult
import com.language.repeater.widgets.ScrollingWaveformView.OnSeekListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class PlayVideoFragment: Fragment() {
  companion object {
    const val TAG = "PlayVideoFragment"
  }

  private var _binding: VideoPlayFragmentBinding? = null
  private val binding get() = _binding!!

  private var loadingDialog: LoadingDialogFragment? = null

  private var exoPlayer: ExoPlayer? = null
  private val viewModel: PlayVideoViewModel by viewModels()

  //当前所有的语音片段
  private var voiceSegments = listOf<Sentence>()
  //当前正在读的语音片段
  private var curSegment: Sentence? = null
    set(value) {
      binding.audioProgressWaveView.curABSeg = value
      field = value
    }

  private var repeatable = false
  private var playWhenResume = true

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

    repeatable = binding.voiceRepeatSwitch.isChecked
    binding.voiceRepeatSwitch.setOnCheckedChangeListener { _, checked->
      repeatable = checked
      curSegment = findCurrentSegment()
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
      if (curSegment != null) {
        val index = voiceSegments.indexOf(curSegment)
        if (index >= 0 && index < voiceSegments.lastIndex) {
          seekToSegment(voiceSegments[index + 1])
        }
      }
    }
    binding.voicePrevious.setOnClickListener {
      if (curSegment != null) {
        val index = voiceSegments.indexOf(curSegment)
        if (index > 0 && index <= voiceSegments.lastIndex) {
          seekToSegment(voiceSegments[index - 1])
        }
      }
    }

    binding.reloadSentence.setOnClickListener {
      lifecycleScope.launch {
        //showLoading()
        viewModel.reloadSentencesAuto()
        //hideLoading()
      }
    }

    binding.saveSentence.setOnClickListener {
      lifecycleScope.launch {
        //showLoading()
        viewModel.saveSentenceDataToFile(voiceSegments)
        //hideLoading()
      }
    }

    //拖动波形图的逻辑
    binding.audioProgressWaveView.setOnSeekListener(object : OnSeekListener {
      var isPlayWhenStart = false
      override fun onSeekStart() {
        isPlayWhenStart = exoPlayer?.isPlaying == true
        if (isPlayWhenStart) {
          exoPlayer?.pause()
        }
      }

      override fun onSeeking(position: Float) {
      }

      override fun onSeekEnd(position: Float) {
        exoPlayer?.seekTo((position * 1000).toLong())
        curSegment = findCurrentSegment()
        if (isPlayWhenStart) {
          exoPlayer?.play()
        }
      }
    })

    //拖动AB边界的逻辑
    binding.audioProgressWaveView.setOnABChangeListener(object : ScrollingWaveformView.OnABChangeListener{
      var isPlayWhenStart = false
      override fun onABDragStart(dragAbResult: ABHitResult?) {
        isPlayWhenStart = exoPlayer?.isPlaying == true
        if (isPlayWhenStart) {
          exoPlayer?.pause()
        }
      }

      override fun onABDragging(dragAbResult: ABHitResult?) {
      }

      override fun onABDragEnd(dragAbResult: ABHitResult?) {
        if (isPlayWhenStart) {
          exoPlayer?.play()
        }
      }
    })

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch {
          viewModel.pcmLoaderStateFlow.collect { loader ->
            if (loader != null) {
              // 加载PCM文件
              binding.audioProgressWaveView.setPCMLoader(loader) {
                Log.i("wangzixu", "audioProgressWaveView loadWindow $it")
              }
            }
          }
        }

        launch {
          viewModel.allWaveDataFlow.collect {
            binding.audioWaveView.setPcmData(it)
            hideLoading()
          }
        }

        launch {
          viewModel.sentencesFlow.collect {
            binding.audioProgressWaveView.setSentenceData(it)
            voiceSegments = it
            if (repeatable) {
              var seg = findCurrentSegment()
              if (seg == null) {
                seg = voiceSegments.getOrNull(0)
              }
              curSegment = seg
            } else {
              curSegment = null
            }
          }
        }

        launch {
          while (true) {
            if (exoPlayer?.isPlaying == true) {
              var cur = exoPlayer?.currentPosition?.toFloat() ?: -1f
              if (cur != -1f) {
                var curSec = cur / 1000

                //处理复读的逻辑
                if (repeatable) {
                  if (curSegment == null) {
                    curSegment = findCurrentSegment()
                  }
                  val seg = curSegment
                  if (seg != null && curSec >= seg.end) {
                    //跳回开始
                    exoPlayer?.seekTo((seg.start * 1000).toLong())
                    curSec = seg.start
                    cur = seg.start * 1000
                  }
                }

                //处理波形图的更新
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

  private fun findCurrentSegment(): Sentence? {
    if (!repeatable) return null

    val segments = voiceSegments
    val player = exoPlayer
    //计算当前是哪句
    var targetSeg: Sentence? = null
    if (segments.isNotEmpty() && player != null) {
      val cur = player.currentPosition.toFloat() / 1000
      for (i in segments.indices) {
        val seg = segments[i]
        if (cur <= seg.end) {
          targetSeg = seg
          break
        }
      }
    }
    return targetSeg
  }

  private fun seekToSegment(segmentation: Sentence?) {
    curSegment = segmentation
    if (segmentation != null) {
      exoPlayer?.seekTo((segmentation.start * 1000).toLong())
    }
  }

  override fun onPause() {
    super.onPause()
    Log.i("wangzixu", "$TAG onPause")
    playWhenResume = exoPlayer?.isPlaying == true
    exoPlayer?.pause()
  }

  override fun onResume() {
    super.onResume()
    Log.i("wangzixu", "$TAG onResume")
    if (playWhenResume) {
      exoPlayer?.play()
    }
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

  private fun showLoading() {
    if (loadingDialog == null) {
      loadingDialog = LoadingDialogFragment.newInstance()
    }
    if (!loadingDialog!!.isAdded) {
      loadingDialog!!.show(parentFragmentManager, LoadingDialogFragment.TAG)
      //onPause()
    }
  }

  private fun hideLoading() {
    if (loadingDialog != null) {
      loadingDialog?.dismiss()
      loadingDialog = null
      //onResume()
    }
  }
}