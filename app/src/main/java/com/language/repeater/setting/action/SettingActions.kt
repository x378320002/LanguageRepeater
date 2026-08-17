package com.language.repeater.setting.action

import com.language.repeater.app.action.IAction

data class ActionSentenceGapChange(val gap: Int) : IAction

data class ActionLongPressSpeedChange(val speed: Float) : IAction

data class ActionFullGestureChange(val enabled: Boolean) : IAction

data class ActionLeftBrightnessGestureChange(val enabled: Boolean) : IAction

data class ActionRightVolumeGestureChange(val enabled: Boolean) : IAction

data object ActionClearCache : IAction

data class ActionThemeChange(val isLight: Boolean): IAction