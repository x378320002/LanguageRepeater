package com.language.repeater.playvideo

import android.os.Bundle
import android.os.Process
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
import com.language.repeater.playvideo.components.PlayAllWaveComponent
import com.language.repeater.playvideo.components.PlayScrollWaveComponent
import com.language.repeater.playvideo.components.PlayUIActComponent
import com.language.repeater.playvideo.components.PlayViewComponent
import com.language.repeater.playvideo.components.SelectFileComponent


class PlayVideoFragment: BaseFragment(), Player.Listener  {
  companion object {
    const val TAG = "wangzixu"
  }

  lateinit var binding: VideoPlayFragmentBinding
  val viewModel: PlayerViewModel by activityViewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.i(TAG, "onCreate, pid:${Process.myPid()}, currentThread:${Thread.currentThread().name}")
    //addComponent(playComponent)
    //addComponent(HeadsetComponent())
    addComponent(SelectFileComponent())
    addComponent(PlayViewComponent())
    addComponent(PlayScrollWaveComponent())
    addComponent(PlayAllWaveComponent())
    addComponent(PlayUIActComponent())
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    //Log.i(TAG, "$TAG onCreateView")
    binding = VideoPlayFragmentBinding.inflate(inflater, container, false)
    return binding.root
  }

  @OptIn(UnstableApi::class)
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
      val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
      insets
    }

    bindClickAction()
  }

  private fun bindClickAction() {
    //binding.goTestPage.setOnClickListener {
    //  //startActivity(Intent(requireContext(), TestActivity::class.java))
    //  findNavController().navigate(TestPageKey, defaultNavOptions)
    //}

    binding.testAction.setOnClickListener {
    }
  }
}