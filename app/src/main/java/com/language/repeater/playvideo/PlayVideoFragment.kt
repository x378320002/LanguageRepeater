package com.language.repeater.playvideo

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.language.repeater.TestPageKey
import com.language.repeater.databinding.VideoPlayFragmentBinding
import com.language.repeater.defaultNavOptions
import com.language.repeater.foundation.BaseFragment
import com.language.repeater.playvideo.components.HeadsetComponent
import com.language.repeater.playvideo.components.PlayAllWaveComponent
import com.language.repeater.playvideo.components.PlayCoreComponent
import com.language.repeater.playvideo.components.PlayUIActComponent
import com.language.repeater.playvideo.components.PlayScrollWaveComponent
import com.language.repeater.playvideo.components.SelectFileComponent


class PlayVideoFragment: BaseFragment(), Player.Listener  {
  companion object {
    const val TAG = "wangzixu"
  }

  private var _binding: VideoPlayFragmentBinding? = null
  val binding get() = _binding!!
  val viewModel: PlayVideoViewModel by activityViewModels()

  var playComponent = PlayCoreComponent()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.i(TAG, "$TAG onCreate")
    addComponent(playComponent)
    addComponent(SelectFileComponent())
    addComponent(HeadsetComponent())
    addComponent(PlayScrollWaveComponent())
    addComponent(PlayAllWaveComponent())
    addComponent(PlayUIActComponent())
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    Log.i(TAG, "$TAG onCreateView")
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

    binding.exoVideoView.player = playComponent.player

    bindClickAction()
  }

  private fun bindClickAction() {
    binding.goTestPage.setOnClickListener {
      //startActivity(Intent(requireContext(), TestActivity::class.java))
      findNavController().navigate(TestPageKey, defaultNavOptions)
    }

    binding.testAction.setOnClickListener {
    }
  }

  override fun onPause() {
    super.onPause()
    Log.i(TAG, "$TAG onPause")
  }

  override fun onResume() {
    super.onResume()
    Log.i(TAG, "$TAG onResume")
  }

  override fun onStart() {
    super.onStart()
    Log.i(TAG, "$TAG onStart")
  }

  override fun onStop() {
    super.onStop()
    Log.i(TAG, "$TAG onStop")
  }

  override fun onDestroyView() {
    super.onDestroyView()
    Log.i(TAG, "$TAG onDestroyView")
  }

  override fun onDestroy() {
    super.onDestroy()
    Log.i(TAG, "$TAG onDestroy")
  }
}