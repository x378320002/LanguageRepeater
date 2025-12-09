package com.language.repeater.loading

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log.v
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.dotlottie.dlplayer.Fit
import com.language.repeater.R
import com.language.repeater.databinding.DialogLoadingLayoutBinding
import com.language.repeater.databinding.VideoPlayFragmentBinding
import com.lottiefiles.dotlottie.core.model.Config
import com.lottiefiles.dotlottie.core.util.DotLottieSource
import com.lottiefiles.dotlottie.core.util.LayoutUtil

class LoadingDialogFragment : DialogFragment() {

  companion object {
    const val TAG = "LoadingDialogFragment"

    fun newInstance(): LoadingDialogFragment {
      return LoadingDialogFragment()
    }
  }

  private var _binding: DialogLoadingLayoutBinding? = null
  private val binding get() = _binding!!

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // 设置为不可取消，避免用户点击空白处关闭
    isCancelable = false
    setStyle(STYLE_NO_FRAME, R.style.LoadingDialogTheme)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    _binding = DialogLoadingLayoutBinding.inflate(inflater, container, false)
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

    return binding.root
  }

  override fun onDismiss(dialog: DialogInterface) {
    super.onDismiss(dialog)
    binding.lottieAnimation.stop()
  }
}
