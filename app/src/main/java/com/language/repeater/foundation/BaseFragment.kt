package com.language.repeater.foundation

import android.R.attr.fragment
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.language.repeater.loading.LoadingDialogFragment

/**
 * Date: 2025-11-14
 * Time: 10:57
 * Description:
 */
abstract class BaseFragment: Fragment() {
  @Suppress("UNCHECKED_CAST")
  inline fun <reified F: BaseFragment> addComponent(component: BaseComponent<F>) {
    if (this !is F) {
      throw IllegalArgumentException("addComponent error, type mismatching! ${this::class.simpleName} add ${component}<${F::class.simpleName}>")
    }
    if (component.hasAttached) {
      throw IllegalArgumentException("addComponent error, has added! ${this::class.simpleName} add ${component}<${F::class.simpleName}>")
    }
    component.attach(this) //this as F, as转换失败强制抛出异常
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