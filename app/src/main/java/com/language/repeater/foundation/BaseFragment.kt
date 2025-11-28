package com.language.repeater.foundation

import android.R.attr.fragment
import android.os.Bundle
import android.util.Log.v
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import com.language.repeater.loading.LoadingDialogFragment
import kotlin.jvm.java

/**
 * Date: 2025-11-14
 * Time: 10:57
 * Description:
 */
abstract class BaseFragment: Fragment() {
  private val components = mutableListOf<BaseComponent<*>>()

  @MainThread
  @Suppress("UNCHECKED_CAST")
  fun <F: BaseFragment> addComponent(component: BaseComponent<F>) {
    for (com in components) {
      if (com.javaClass == component.javaClass) {
        throw IllegalArgumentException("addComponent error, $component 已经有同类型的了")
      }
    }
    components.add(component)
    component.attach(this as F) //this as F, as转换失败强制抛出异常
  }

  override fun onDestroy() {
    super.onDestroy()
    components.clear()
  }

  //region loading dialog
  private var loadingDialog: LoadingDialogFragment? = null
  fun showLoading() {
    if (loadingDialog == null) {
      loadingDialog = LoadingDialogFragment.newInstance()
    }
    if (!loadingDialog!!.isAdded) {
      loadingDialog!!.show(parentFragmentManager, LoadingDialogFragment.TAG)
      //onPause()
    }
  }

  fun hideLoading() {
    if (loadingDialog != null) {
      loadingDialog?.dismiss()
      loadingDialog = null
      //onResume()
    }
  }
  //endregion
}