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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.language.repeater.databinding.VideoPlayFragmentBinding
import kotlinx.coroutines.launch


class PlayVideoFragment: Fragment() {
  private var _binding: VideoPlayFragmentBinding? = null
  private val binding get() = _binding!!

  private var exoPlayer: ExoPlayer? = null
  private val viewModel: PlayVideoViewModel by viewModels()

  val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult(),
    object: ActivityResultCallback<ActivityResult> {
      override fun onActivityResult(result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK) {
          val videoUri = result.data?.data
          if (videoUri != null) {
            //这里你可以用 videoUri 播放视频或读取内容
            Log.d("VideoSelect", "Selected video uri: $videoUri")
            binding.filePathTv.text = videoUri.toString()

            val mediaItem = MediaItem.fromUri(videoUri)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
            exoPlayer?.play()

            viewModel.parseUriToPcm(videoUri)
          }
        }
      }
    })

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = VideoPlayFragmentBinding.inflate(inflater, container, false)
    val view = binding.root
    return view
  }

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

    exoPlayer = ExoPlayer.Builder(requireContext()).build().apply {
      repeatMode = Player.REPEAT_MODE_ALL
    }
    binding.exoVideoView.player = exoPlayer

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.audioDataStateFlow.collect { data ->
          if (data != null) {
            binding.audioWaveView.setPcmData(data)
          }
        }
      }
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
    exoPlayer?.release()
    exoPlayer = null
  }
}