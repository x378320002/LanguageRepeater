package com.language.repeater.setting

import android.R.attr.enabled
import android.annotation.SuppressLint
import android.app.Application
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.language.repeater.dataStore
import com.language.repeater.pcm.FFmpegUtil
import com.language.repeater.playcore.PlaybackCore
import com.language.repeater.sentence.SentenceStoreUtil
import com.language.repeater.utils.DataStoreUtil
import com.language.repeater.utils.observe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingViewModel(application: Application) : AndroidViewModel(application) {

    // -----------------------------------------------------------------------
    // State：把所有 DataStore 的 Flow 转为 StateFlow，供 Compose 用 collectAsState() 收集
    // -----------------------------------------------------------------------

    /** 字幕文件夹 URI 字符串，空字符串表示未设置 */
    val subtitleFolder: StateFlow<String> = DataStoreUtil.observeSubTitleFolder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "未设置")

    /** 句子最小间隔（ms） */
    val sentenceGap: StateFlow<Int> = DataStoreUtil.observeSentenceGap()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 600)

    /** 全屏手势开关 */
    val fullGesture: StateFlow<Boolean> = application.dataStore
        .observe(DataStoreUtil.KEY_FULL_GESTURE, false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 左侧亮度手势开关 */
    val leftBrightnessGesture: StateFlow<Boolean> = application.dataStore
        .observe(DataStoreUtil.KEY_LEFT_BRIGHTNESS_GESTURE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 右侧音量手势开关 */
    val rightVolumeGesture: StateFlow<Boolean> = application.dataStore
        .observe(DataStoreUtil.KEY_RIGHT_VOLUME_GESTURE, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 长按播放倍速 */
    val longPressSpeed: StateFlow<Float> = application.dataStore
        .observe(DataStoreUtil.KEY_LONG_PRESS_SPEED, 0.5f)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)

    /** 夜间模式开关 */
    val nightMode: StateFlow<Boolean> = DataStoreUtil.observeNightMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /**
     * 临时文件缓存大小（MB），用字符串直接展示，如 "1.23 MB"
     * 初始值为 "..." 表示正在计算中
     * 实现：在 init 里启动协程，计算 FFmpegUtil + SentenceStoreUtil 的目录大小后更新
     */
    val cacheSizeFlow = MutableStateFlow("...")
    init {
        viewModelScope.launch {
            cacheSizeFlow.value = calculateCacheSize()
        }
    }

    // -----------------------------------------------------------------------
    // Actions：把所有写操作封装成方法，SettingScreen 直接调用，不关心 DataStore 细节
    // -----------------------------------------------------------------------
    /** 保存句子最小间隔 */
    fun saveSentenceGap(gap: Int) {
        viewModelScope.launch {
            DataStoreUtil.saveSentenceGap(gap)
        }
    }

    /** 保存长按倍速 */
    fun saveLongPressSpeed(speed: Float) {
        viewModelScope.launch {
            application.dataStore.edit { it[DataStoreUtil.KEY_LONG_PRESS_SPEED] = speed }
        }
    }

    /** 保存全屏手势开关 */
    fun saveFullGesture(enabled: Boolean) {
        viewModelScope.launch {
            application.dataStore.edit {
                it[DataStoreUtil.KEY_FULL_GESTURE] = enabled
            }
        }
    }

    /** 保存左侧亮度手势开关 */
    fun saveLeftBrightnessGesture(enabled: Boolean) {
        viewModelScope.launch {
            application.dataStore.edit { it[DataStoreUtil.KEY_LEFT_BRIGHTNESS_GESTURE] = enabled }
        }
    }

    /** 保存右侧音量手势开关 */
    fun saveRightVolumeGesture(enabled: Boolean) {
        viewModelScope.launch {
            application.dataStore.edit { it[DataStoreUtil.KEY_RIGHT_VOLUME_GESTURE] = enabled }
        }
    }

    /** 保存夜间模式并通知系统切换 AppCompatDelegate */
    fun saveNightMode(enabled: Boolean) {
        viewModelScope.launch {
            application.dataStore.edit { it[DataStoreUtil.KEY_NIGHT_MODE] = enabled }
        }
    }

    /** 保存字幕文件夹 URI */
    fun saveSubtitleFolder(uriString: String) {
        viewModelScope.launch {
            application.dataStore.edit { it[DataStoreUtil.KEY_SUBTITLE_FOLDER] = uriString }
        }
    }

    /**
     * 清除缓存（WAV + 句子 JSON），清除后刷新缓存大小显示
     * 注意：需要先从 PlaybackCore 取出当前播放列表的 mediaId 列表，
     *       传给 FFmpegUtil.clearTempData 和 SentenceStoreUtil.clearTempData
     */
    fun clearCache() {
        viewModelScope.launch {
            //val player = PlaybackCore.getInstance(getApplication()).playerInstance.value
            //    ?: return@launch
            cacheSizeFlow.value = "..."
            val mediaIds = buildList<String> {
                //for (i in 0 until player.mediaItemCount) {
                //    add(player.getMediaItemAt(i).mediaId)
                //}
            }
            FFmpegUtil.clearTempData(application, mediaIds)
            FFmpegUtil.clearTempData(application, mediaIds)
            calculateCacheSize()
            delay(2000)
            cacheSizeFlow.value = "0.00M"
        }
    }

    @SuppressLint("DefaultLocale")
    private suspend fun calculateCacheSize(): String  = withContext(Dispatchers.IO){
        val size1 = FFmpegUtil.getDirectorySize(application)
        val size2 = SentenceStoreUtil.getDirectorySize(application)
        val totalSizeBytes = size1 + size2
        val totalSizeMB = totalSizeBytes / (1024 * 1024).toDouble()
        "${String.format("%.2f", totalSizeMB)} MB"
    }
}
