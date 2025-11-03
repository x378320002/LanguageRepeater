package com.language.repeater.loading

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.language.repeater.R

class LoadingDialogFragment : DialogFragment() {

  companion object {
    const val TAG = "LoadingDialogFragment"

    fun newInstance(): LoadingDialogFragment {
      return LoadingDialogFragment()
    }
  }

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
    return inflater.inflate(R.layout.dialog_loading_layout, container, false)
  }
}
