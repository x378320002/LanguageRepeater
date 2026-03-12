package com.language.repeater.playvideo.components

import android.view.Menu
import androidx.appcompat.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.language.repeater.R
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import com.language.repeater.utils.FileUtil.takePersistablePermission
import com.language.repeater.utils.ResourcesUtil
import com.language.repeater.utils.ToastUtil
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Date: 2026-03-10
 * Time: 14:59
 * Description:
 */
class SetSubtitleComponent : BaseComponent<PlayVideoFragment>() {
  companion object {
    private const val MENU_ID_SELECT_FILE = -1
    private const val MENU_ID_DISABLE_SUBTITLE = -2
  }

  val openSubtitleLauncher by lazy {
    fragment.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        takePersistablePermission(context, uri)
        fragment.viewModel.onSubtitleSelected(uri)
      }
    }
  }

  var popupMenu: PopupMenu? = null

  override fun onCreate() {
    super.onCreate()
    openSubtitleLauncher
  }

  override fun onCreateView() {
    super.onCreateView()

    fragment.binding.btnSubtitle.setOnClickListener {
      showSubtitlePop()
    }

    fragment.viewModel.currentMediaItem.onEach {
      popupMenu?.dismiss()
      popupMenu = null
    }.launchIn(uiScope)
  }

  data class SubtitleItem(
    val group: Tracks.Group,
    val trackIndex: Int
  )

  private fun showSubtitlePop() {
    val player = fragment.viewModel.getPlayer()
    if (player == null) {
      ToastUtil.toast("播放器未就绪")
      return
    }

    val popup = ResourcesUtil.createLightPopMenu(context, fragment.binding.btnSubtitle)
    val menu = popup.menu

    menu.add(Menu.NONE, MENU_ID_SELECT_FILE, 0, R.string.select_subtitle)

    val tracks = player.currentTracks
    val textTracks = tracks.groups.filter {
      it.type == C.TRACK_TYPE_TEXT
    }

    val subtitleItems = mutableListOf<SubtitleItem>()
    textTracks.forEach { group ->
      for (i in 0 until group.length) {
        subtitleItems.add(SubtitleItem(group, i))
      }
    }

    if (subtitleItems.isNotEmpty()) {
      menu.add(Menu.NONE, MENU_ID_DISABLE_SUBTITLE, 1, R.string.disable_subtitle)
    }

    subtitleItems.forEachIndexed { index, item ->
      val format = item.group.getTrackFormat(item.trackIndex)
      val label =
        format.label
          ?: format.language
          ?: format.sampleMimeType
          ?: context.getString(R.string.subtitle_track, index + 1)
      val menuItem = menu.add(Menu.NONE, index, index + 2, label)
      menuItem.isCheckable = true
      if (item.group.isTrackSelected(item.trackIndex)) {
        menuItem.isChecked = true
      } else {
        menuItem.isChecked = false
      }
    }

    popup.setOnMenuItemClickListener { menuItem ->
      when (menuItem.itemId) {
        MENU_ID_SELECT_FILE -> {
          openSubtitleLauncher.launch(arrayOf("text/*", "application/x-subrip"))
        }

        MENU_ID_DISABLE_SUBTITLE -> {
          player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
          ToastUtil.toast(R.string.subtitle_disabled)
        }

        else -> {
          val trackIndex = menuItem.itemId
          if (trackIndex >= 0 && trackIndex < subtitleItems.size) {
            val item = subtitleItems[menuItem.itemId]
            player.trackSelectionParameters = player.trackSelectionParameters
              .buildUpon()
              .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
              .setOverrideForType(
                TrackSelectionOverride(
                  item.group.mediaTrackGroup,
                  item.trackIndex
                )
              )
              .build()
            ToastUtil.toast(R.string.subtitle_switched)
          }
        }
      }
      true
    }
    popup.setOnDismissListener {
      popupMenu = null
    }
    popupMenu = popup
    popup.show()
  }
}