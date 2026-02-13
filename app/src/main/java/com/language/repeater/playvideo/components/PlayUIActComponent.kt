package com.language.repeater.playvideo.components

import android.R.attr.visibility
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.view.get
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.findNavController
import androidx.transition.Slide
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.language.repeater.R
import com.language.repeater.SettingPageKey
import com.language.repeater.defaultNavOptions
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playcore.SleepTimerManager
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.playvideo.history.HistorySheetFragment
import com.language.repeater.playvideo.playlist.PlaylistSheetFragment
import com.language.repeater.playvideo.sleeptimer.SleepTimerSheetFragment
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description: 耳机播控处理逻辑
 */
class PlayUIActComponent : BaseComponent<PlayVideoFragment>(), View.OnClickListener {
  companion object {
    const val TAG = PlayVideoFragment.Companion.TAG
  }

  var isDragging = false

  @SuppressLint("SetTextI18n")
  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()
    setUIAction()
    observeData()
    setSubtitleStyle()
  }

  @SuppressLint("SourceLockedOrientationActivity")
  @UnstableApi
  private fun setUIAction() {
    fragment.binding.voiceRepeatSwitch.setOnClickListener(this)
    fragment.binding.playPauseBtn.setOnClickListener(this)
    fragment.binding.seekPreSentence.setOnClickListener(this)
    fragment.binding.seekNextSentence.setOnClickListener(this)
    fragment.binding.backSentenceHead.setOnClickListener(this)
    fragment.binding.playList.setOnClickListener(this)
    fragment.binding.mergePre.setOnClickListener(this)
    fragment.binding.mergeNext.setOnClickListener(this)
    fragment.binding.deleteSentence.setOnClickListener(this)
    fragment.binding.splitSentence.setOnClickListener(this)
    fragment.binding.insertSentence.setOnClickListener(this)
    fragment.binding.subActionMore.setOnClickListener(this)
    fragment.binding.ivSetting.setOnClickListener(this)
    fragment.binding.setTimer.setOnClickListener(this)
    fragment.binding.playSpeed.setOnClickListener(this)
    fragment.binding.switchDragAb.setOnClickListener(this)

    fragment.binding.editLayout.visibility = View.VISIBLE
    fragment.binding.exoVideoView.setShowFastForwardButton(false)
    fragment.binding.exoVideoView.setShowRewindButton(false)
    fragment.binding.exoVideoView.setFullscreenButtonState(fragment.isLandScreen)

    if (fragment.isLandScreen) {
      fragment.activity?.onBackPressedDispatcher?.addCallback(fragment, object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (fragment.isLandScreen) {
            fragment.activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
          }
        }
      })

      fragment.binding.exoVideoView.setOnClickListener {
        toggleCardVisibility(
          fragment.binding.landOverlayLayout,
          fragment.binding.landOverlayLayout?.visibility == View.GONE
        )
      }

      setupSeekBarLogic()
    }

    fragment.binding.switchFullScreen.setOnClickListener {
      if (fragment.isLandScreen) {
        fragment.activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
      } else {
        fragment.activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
      }
    }
  }

  private fun setupSeekBarLogic() {
    // 1. 监听用户的拖动操作
    fragment.binding.videoSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      var isPlayWhenStart = false

      override fun onStartTrackingTouch(seekBar: SeekBar) {
        isDragging = true
        isPlayWhenStart = fragment.viewModel.isUiPlaying.value
        fragment.viewModel.pause()
        isDragging = true
      }

      override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
        // 如果是用户手指拖动引起的进度改变
        val player = fragment.viewModel.getPlayer() ?: return
        if (fromUser) {
          val duration = player.duration
          if (duration > 0) {
            // 计算拖动到了哪一秒 (可选：在这里更新当前时间 TextView 显示)
            val targetPosition = (progress.toLong() * duration) / 1000L
            fragment.viewModel.updatePosition(targetPosition)
          }
        }
      }

      override fun onStopTrackingTouch(seekBar: SeekBar) {
        isDragging = false
        val player = fragment.viewModel.getPlayer() ?: return
        val duration = player.duration
        if (duration > 0) {
          // 用户松手，真正执行视频跳转
          val targetPosition = (seekBar.progress.toLong() * duration) / 1000L
          player.seekTo(targetPosition)
        }
        if (isPlayWhenStart) {
          fragment.viewModel.play()
        }
      }
    })
  }

  //targetCard 是你要做动画的卡片，isShowing 表示你接下来要让它显示还是隐藏
  fun toggleCardVisibility(targetCard: View?, isShowing: Boolean) {
    if (targetCard == null) return
    val slideTransition = Slide(Gravity.END).apply {
      duration = 300 // 动画时长（毫秒）
      addTarget(targetCard)
      interpolator = FastOutSlowInInterpolator()
    }

    //将自定义的动画交给 TransitionManager
    TransitionManager.beginDelayedTransition(fragment.binding.root, slideTransition)
    //真正改变 View 的可见性触发动画
    targetCard.visibility = if (isShowing) View.VISIBLE else View.GONE
  }

  private fun observeData() {
    fragment.viewModel.playerInstance.onEach {
      fragment.binding.exoVideoView.player = it
    }.launchIn(uiScope)

    val showEdit = fragment.viewModel.editMode.value
    fragment.binding.switchDragAb.isSelected = showEdit
    if (showEdit) {
      fragment.binding.editLayout.visibility = View.VISIBLE
    } else {
      fragment.binding.editLayout.visibility = View.GONE
    }
    fragment.viewModel.editMode.onEach {
      if (it != fragment.binding.switchDragAb.isSelected) {
        fragment.binding.switchDragAb.isSelected = it
        if (it) {
          TransitionManager.beginDelayedTransition(fragment.binding.root)
          fragment.binding.editLayout.visibility = View.VISIBLE
        } else {
          TransitionManager.beginDelayedTransition(fragment.binding.root)
          fragment.binding.editLayout.visibility = View.GONE
        }
      }
    }.launchIn(uiScope)

    fragment.viewModel.playSpeedState.onEach { speed ->
      when (speed) {
        0.25f -> fragment.binding.playSpeedTv.text = "0.25"
        0.5f -> fragment.binding.playSpeedTv.text = "0.5X"
        0.75f -> fragment.binding.playSpeedTv.text = "0.75"
        1.0f -> fragment.binding.playSpeedTv.text = "1.0X"
        1.25f -> fragment.binding.playSpeedTv.text = "1.25"
        1.5f -> fragment.binding.playSpeedTv.text = "1.5X"
        2.0f -> fragment.binding.playSpeedTv.text = "2.0X"
        else -> fragment.binding.playSpeedTv.text = "UnKnown" // 如果有其他速度值，显示为未知速度
      }
    }.launchIn(uiScope)

    fragment.viewModel.repeatable.onEach {
      fragment.binding.voiceRepeatSwitch.isSelected = it
      fragment.binding.audioProgressWaveView.isRepeated = it
      fragment.binding.audioProgressWaveView.invalidate()
    }.launchIn(uiScope)

    fragment.viewModel.isUiPlaying.onEach {
      fragment.binding.playPauseBtn.isSelected = it
    }.launchIn(uiScope)

    fragment.viewModel.currentMediaItem.onEach {
      val title = it?.mediaMetadata?.title ?: "UnKnown"
      fragment.binding.tvTitle.text = title
    }.launchIn(uiScope)

    fragment.viewModel.currentPosition.onEach { position ->
      val player = fragment.viewModel.playerInstance.value ?: return@onEach
      val seekBar = fragment.binding.videoSeekBar ?: return@onEach
      val duration = player.duration
      if (duration > 0 && !isDragging) {
        // 我们在 XML 里把 max 设置成了 1000，所以这里算千分比
        val progressPercentage = (position * 1000 / duration).toInt()
        seekBar.progress = progressPercentage
      }
    }.launchIn(uiScope)

    SleepTimerManager.remainingSeconds.onEach { seconds ->
      if (seconds > 0) {
        fragment.binding.setTimerTv.visibility = View.VISIBLE
        fragment.binding.setTimerTv.text = SleepTimerManager.formatTime(seconds)
      } else {
        fragment.binding.setTimerTv.visibility = View.GONE
      }
    }.launchIn(uiScope)
  }

  @OptIn(UnstableApi::class)
  private fun setSubtitleStyle() {
    val subtitleView = fragment.binding.exoVideoView.subtitleView
    if (subtitleView != null) {
      // 2. 设置样式
      // 参数顺序：文字颜色, 背景色, 窗口色, 边缘类型, 边缘颜色, 字体(Typeface)
      val style = CaptionStyleCompat(
        Color.WHITE,             // 文字改成亮黄色
        Color.TRANSPARENT, // 背景半透明黑 (0x00000000 为全透明)
        Color.TRANSPARENT,        // 窗口背景透明
        CaptionStyleCompat.EDGE_TYPE_OUTLINE, // 描边 (OUTLINE, DROP_SHADOW, NONE)
        Color.BLACK,              // 描边黑色
        null                      // 字体 (Typeface)，如果要自定义字体传 Typeface 对象
      )

      subtitleView.setStyle(style)
      // 3. 设置字体大小 (单位：像素) -> 建议转换成 sp
      // SubtitleView.VIEW_TYPE_WEB (默认) 支持分数大小，VIEW_TYPE_CANVAS 支持固定大小
      // 这里设置占视频高度的 5% (默认是 0.0533)
      subtitleView.setFractionalTextSize(0.09f)
//      subtitleView.setFixedTextSize(Dimension.DP,20f)

      // 或者强制固定大小 (不推荐，全屏时会显得太小)
      // subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)

      // 4. 设置位置 (底部距离)
      // 0.9f 表示在屏幕 90% 的位置 (靠近底部)
      subtitleView.setBottomPaddingFraction(0.05f)
    }
  }

  override fun onClick(v: View?) {
    when (v) {
      fragment.binding.playSpeed -> {
        showSpeedMenu()
      }

      fragment.binding.setTimer -> {
        if (fragment.isLandScreen) {
          showTimeSetMenu()
        } else {
          val sheet = SleepTimerSheetFragment()
          sheet.show(fragment.childFragmentManager, "SleepTimer")
        }
      }

      fragment.binding.ivSetting -> {
        fragment.findNavController().navigate(SettingPageKey, defaultNavOptions)
      }

      fragment.binding.subActionMore -> {
        showMoreMenu()
      }

      fragment.binding.insertSentence -> {
        fragment.viewModel.insertSentence()
      }

      fragment.binding.mergePre -> {
        fragment.viewModel.mergePreSentence()
      }

      fragment.binding.mergeNext -> {
        fragment.viewModel.mergeNextSentence()
      }

      fragment.binding.deleteSentence -> {
        deleteCurSen()
      }

      fragment.binding.splitSentence -> {
        splitCurSen()
      }

      fragment.binding.voiceRepeatSwitch -> {
        fragment.viewModel.toggleRepeat()
      }

      fragment.binding.playPauseBtn -> {
        fragment.viewModel.togglePlayPause()
      }

      fragment.binding.seekPreSentence -> {
        fragment.viewModel.seekToPreviousSentence()
      }

      fragment.binding.seekNextSentence -> {
        fragment.viewModel.seekToNextSentence()
      }

      fragment.binding.backSentenceHead -> {
        fragment.viewModel.backToSentenceHead()
      }

      fragment.binding.playList -> {
        val sheet = PlaylistSheetFragment()
        sheet.show(fragment.childFragmentManager, "PlaylistSheet")
      }

      fragment.binding.switchDragAb -> {
        if (fragment.binding.switchDragAb.isSelected) {
          fragment.viewModel.editMode(false)
        } else {
          fragment.viewModel.editMode(true)
        }
      }
    }
  }

  private fun showSpeedMenu() {
    val popup = ResourcesUtil.createLightPopMenu(context, fragment.binding.playSpeed)
    popup.menuInflater.inflate(R.menu.menu_play_speed, popup.menu)
    popup.setOnMenuItemClickListener { menuItem ->
      when (menuItem.itemId) {
        R.id.action_0_25 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(0.25f)
        }

        R.id.action_0_5 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(0.5f)
        }

        R.id.action_0_75 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(0.75f)
        }

        R.id.action_1_0 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(1.0f)
        }

        R.id.action_1_25 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(1.25f)
        }

        R.id.action_1_5 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(1.5f)
        }

        R.id.action_2_0 -> {
          fragment.viewModel.getPlayer()?.setPlaybackSpeed(2.0f)
        }
      }
      true
    }
    popup.show()
  }

  private fun showMoreMenu() {
    val popup = ResourcesUtil.createLightPopMenu(context, fragment.binding.subActionMore)
    popup.menuInflater.inflate(R.menu.menu_sub_action_more, popup.menu)
    //val isEditMode = fragment.viewModel.editMode.value
    //popup.menu[0].setTitle(if (isEditMode) {R.string.exit_edit_mode} else {R.string.enter_edit_mode})
    popup.setOnMenuItemClickListener { menuItem ->
      when (menuItem.itemId) {
        R.id.action_split_auto -> {
          autoLoadSentences(true)
        }

        R.id.action_split_subtitle -> {
          autoLoadSentences(false)
        }

        R.id.action_history_list -> {
          val sheet = HistorySheetFragment()
          sheet.show(fragment.childFragmentManager, "HistorySheet")
        }

        //R.id.action_edit_mode -> {
        //  fragment.viewModel.editMode(!isEditMode)
        //}
      }
      true
    }
    popup.show()
  }

  private fun showTimeSetMenu() {
    val popup = ResourcesUtil.createLightPopMenu(context, fragment.binding.setTimerTv)
    popup.menuInflater.inflate(R.menu.menu_set_timer, popup.menu)
    popup.setOnMenuItemClickListener { menuItem ->
      when (menuItem.itemId) {
        R.id.action_off -> {
          SleepTimerManager.stopTimer()
        }

        R.id.action_10 -> {
          SleepTimerManager.startTimerMinutes(10)
        }

        R.id.action_20 -> {
          SleepTimerManager.startTimerMinutes(20)
        }

        R.id.action_30 -> {
          SleepTimerManager.startTimerMinutes(30)
        }

        R.id.action_45 -> {
          SleepTimerManager.startTimerMinutes(45)
        }

        R.id.action_60 -> {
          SleepTimerManager.startTimerMinutes(60)
        }

        R.id.action_cur_item_left -> {
          SleepTimerManager.startByCurrentItem(fragment.viewModel.getPlayer())
        }
      }
      true
    }
    popup.show()
  }

  private fun autoLoadSentences(auto: Boolean) {
    if (!auto) {
      val item = fragment.viewModel.currentMediaItem.value
      val subUri = item?.localConfiguration?.subtitleConfigurations?.firstOrNull()?.uri
      if (subUri == null) {
        ToastUtil.toast("当前视频没有对应的字幕文件")
      }
      return
    }

    val isPlaying = fragment.viewModel.isUiPlaying.value
    MaterialAlertDialogBuilder(context)
      .setTitle("重新生成句子信息")
      .setMessage("确定要重新生成断句信息吗？这会覆盖当前的句子列表信息")
      .setPositiveButton("自动分割") { _, _ ->
        fragment.viewModel.loadSentenceData(auto)
      }
      .setNegativeButton("取消", null)
      .setOnDismissListener {
        if (isPlaying) {
          fragment.viewModel.play()
        }
      }
      .show()
    if (isPlaying) {
      fragment.viewModel.pause()
    }
  }

  private fun autoLoadSentences() {
    val isPlaying = fragment.viewModel.isUiPlaying.value
    MaterialAlertDialogBuilder(context)
      .setTitle("重新生成断句信息")
      .setMessage("确定要重新生成断句信息吗？这会覆盖当前的句子信息")
      .setNeutralButton("基于字幕分割(如果有)") { dialog, _ ->
        fragment.viewModel.loadSentenceData(false)
      }
      .setPositiveButton("自动分割") { _, _ ->
        fragment.viewModel.loadSentenceData(true)
      }
      .setNegativeButton("取消", null)
      .setOnDismissListener {
        if (isPlaying) {
          fragment.viewModel.play()
        }
      }
      .show()
    if (isPlaying) {
      fragment.viewModel.pause()
    }
  }

  private fun splitCurSen() {
    val isPlaying = fragment.viewModel.isUiPlaying.value
    MaterialAlertDialogBuilder(context)
      .setTitle("确认拆分")
      .setMessage("确定要拆分当前句子吗？")
      .setPositiveButton("确认") { _, _ ->
        fragment.viewModel.splitCurrentSentence()
      }
      .setNegativeButton("取消", null)
      .setOnDismissListener {
        if (isPlaying) {
          fragment.viewModel.play()
        }
      }
      .show()
    if (isPlaying) {
      fragment.viewModel.pause()
    }
  }

  private fun deleteCurSen() {
    val isPlaying = fragment.viewModel.isUiPlaying.value
    MaterialAlertDialogBuilder(context)
      .setTitle("确认删除")
      .setMessage("确定要删除当前AB句吗？")
      .setPositiveButton("删除") { _, _ ->
        fragment.viewModel.deleteCurrentSentence()
      }
      .setNegativeButton("取消", null)
      .setOnDismissListener {
        if (isPlaying) {
          fragment.viewModel.play()
        }
      }
      .show()
    fragment.viewModel.pause()
  }
}