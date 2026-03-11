package com.language.repeater.setting

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.preferences.core.edit
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.language.repeater.MyApp
import com.language.repeater.R
import com.language.repeater.dataStore
import com.language.repeater.databinding.SetttingFragmentBinding
import com.language.repeater.foundation.BaseFragment
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.playvideo.PlayerViewModel
import com.language.repeater.sentence.SentenceStoreUtil
import com.language.repeater.utils.DataStoreUtil
import com.language.repeater.utils.FileUtil
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


@SuppressLint("SetTextI18n")
class SettingFragment: BaseFragment() {
  private var _binding: SetttingFragmentBinding? = null
  private val binding get() = _binding!!

  companion object {
    const val TAG = "wangzixu_SettingFragment"
  }

  private val viewModel: PlayerViewModel by activityViewModels()

  val openDirLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    val context = context ?: return@registerForActivityResult
    Log.d(PlayVideoFragment.TAG, "OpenDocumentTree uri: $uri")
    if (uri != null) {
      FileUtil.takePersistablePermission(context, uri)
      lifecycleScope.launch {
        DataStoreUtil.saveSubTitleFolder(uri.toString())
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    _binding = SetttingFragmentBinding.inflate(inflater, container, false)
    val view = binding.root
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUIAction()
    observeData()
  }

  private fun setUIAction() {
    binding.ibBack.setOnClickListener {
      findNavController().navigateUp()
    }

    binding.settingFullGesture.setOnClickListener {
      binding.fullGestureSwitch.isChecked = !binding.fullGestureSwitch.isChecked
    }
    binding.fullGestureSwitch.setOnCheckedChangeListener { _, isChecked ->
      viewLifecycleOwner.lifecycleScope.launch {
        requireContext().dataStore.edit {
          it[DataStoreUtil.KEY_FULL_GESTURE] = isChecked
        }
      }
    }
    binding.fullGestureInfo.setOnClickListener {
      showFullGestureInfo()
    }

    binding.settingClearTemp.setOnClickListener {
      val player = viewModel.getPlayer()
      if (player != null) {
        // 必须在主线程提取数据
        val items = mutableListOf<String>()
        for (i in 0 until player.mediaItemCount) {
          items.add(player.getMediaItemAt(i).mediaId)
        }
        lifecycleScope.launch {
          FFmpegUtil.clearTempData(requireContext(), items)
          SentenceStoreUtil.clearTempData(requireContext(), items)
          binding.settingClearTempDesc.text = "0 M"
          ToastUtil.toast("清除成功")
        }
      }
    }

    binding.settingSubFolder.setOnClickListener {
      openDirLauncher.launch(null)
    }

    binding.settingSentenceGap.setOnClickListener {
      val dialogView = LayoutInflater.from(context)
        .inflate(R.layout.dialog_edit_num, null)
      val etNumber = dialogView.findViewById<TextInputEditText>(R.id.et_number)
      etNumber.setText("${MyApp.instance.sentenceGap}")
      etNumber.setSelection(etNumber.text?.length ?: 0)
      MaterialAlertDialogBuilder(requireContext())
        .setTitle("设置最小间隔")
        .setView(dialogView)
        .setMessage("自动分割句子时, 小于此间隔的连续句子被视作成同一句")
        .setPositiveButton("确认") { _, _ ->
          val value = etNumber.text.toString().toIntOrNull()
          if (value != null) {
            lifecycleScope.launch {
              DataStoreUtil.saveSentenceGap(value)
            }
          }
        }
        .setNegativeButton("取消", null)
        .show()
    }
  }

  private fun showFullGestureInfo() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.setting_full_gesture_title)
      .setMessage(R.string.setting_full_gesture_desc)
      .show()
  }

  @SuppressLint("DefaultLocale")
  private fun observeData() {
    viewLifecycleOwner.lifecycleScope.launch {
      val size1 = FFmpegUtil.getDirectorySize(requireContext())
      val size2 = SentenceStoreUtil.getDirectorySize(requireContext())
      val totalSizeBytes = size1 + size2
      val totalSizeMB = totalSizeBytes / (1024 * 1024).toDouble()
      binding.settingClearTempDesc.text = "${String.format("%.2f", totalSizeMB)} MB"
    }

    DataStoreUtil.observeSubTitleFolder().onEach {
      if (it.isEmpty()) {
        binding.settingSubFolderDesc.setText(R.string.setting_sub_folder_desc)
      } else {
        val name = FileUtil.getDirUriName(requireContext(), it)
        binding.settingSubFolderDesc.text = name
      }
    }.launchIn(viewLifecycleOwner.lifecycleScope)

    DataStoreUtil.observeSentenceGap().onEach {
      binding.settingSentenceGapDesc.text = "$it"
    }.launchIn(viewLifecycleOwner.lifecycleScope)

    requireContext().dataStore.data.map {
      it[DataStoreUtil.KEY_FULL_GESTURE] ?: false
    }.onEach {
      binding.fullGestureSwitch.isChecked = it
    }.launchIn(viewLifecycleOwner.lifecycleScope)
  }
}