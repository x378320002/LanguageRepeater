package com.language.repeater

import android.R.attr.data
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.LayoutInflaterCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.Fade
import androidx.transition.Slide
import androidx.transition.TransitionManager
import com.language.repeater.databinding.TestFragmentBinding
import com.language.repeater.databinding.TestFragmentItemBinding
import com.language.repeater.databinding.VideoPlayFragmentBinding
import androidx.navigation.findNavController
import androidx.navigation.navOptions


class TestFragment: Fragment() {
  private var _binding: TestFragmentBinding? = null
  private val binding get() = _binding!!


  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = TestFragmentBinding.inflate(inflater, container, false)
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

    binding.testRv.layoutManager = LinearLayoutManager(context)
    binding.testRv.setHasFixedSize(true)
    binding.testRv.adapter = TestAdapter()
  }
}

class TestAdapter : RecyclerView.Adapter<TestViewHolder>() {
  override fun onCreateViewHolder(
    parent: ViewGroup,
    viewType: Int,
  ): TestViewHolder {
    val binding = TestFragmentItemBinding.inflate(LayoutInflater.from(parent.context), parent,
      false)
    return TestViewHolder(binding)
  }

  override fun onBindViewHolder(
    holder: TestViewHolder,
    position: Int,
  ) {
    holder.bind.textItemTv.text = "Text $position Text $position Text $position Text $position Text $position Text $position "
  }

  override fun getItemCount(): Int {
    return 100
  }
}

class TestViewHolder(val bind: TestFragmentItemBinding): RecyclerView.ViewHolder(bind.root) {
    init {
      bind.textItemTv.setOnClickListener {
        it.findNavController().navigate(PlayVideoPageKey, defaultNavOptions)
      }
    }
}