package com.language.repeater.setting.action

import com.language.repeater.app.action.IAction

sealed interface SettingAction : IAction

data class ActionSentenceGapChange(val gap: Int) : SettingAction

data class ActionLongPressSpeedChange(val speed: Float) : SettingAction

data class ActionFullGestureChange(val enabled: Boolean) : SettingAction

data class ActionLeftBrightnessGestureChange(val enabled: Boolean) : SettingAction

data class ActionRightVolumeGestureChange(val enabled: Boolean) : SettingAction

data class ActionSubtitleFolderChange(val uri: String) : SettingAction

data object ActionClearCache : SettingAction

data class ActionThemeChange(val isLight: Boolean) : SettingAction
