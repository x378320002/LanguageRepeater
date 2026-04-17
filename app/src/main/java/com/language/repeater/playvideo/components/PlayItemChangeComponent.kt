package com.language.repeater.playvideo.components

import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.media3.common.util.SystemClock
import androidx.media3.common.util.UnstableApi
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.language.repeater.R
import com.language.repeater.foundation.BaseComponent
import com.language.repeater.playvideo.PlayVideoFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Date: 2025-11-14
 * Time: 16:21
 * Description: 耳机播控处理逻辑
 */
class PlayItemChangeComponent: BaseComponent<PlayVideoFragment>() {
  companion object {
    const val TAG = PlayVideoFragment.Companion.TAG
  }

  val viewModel
    get() = fragment.viewModel

  @UnstableApi
  override fun onCreateView() {
    super.onCreateView()

    viewModel.currentMediaItem.onEach {
      val title = it?.mediaMetadata?.title ?: "UnKnown"
      fragment.binding.tvTitle.text = title

      //extractCoverAndGeneratePalette(it?.localConfiguration?.uri)
    }.launchIn(uiScope)

    //val drawable = ContextCompat.getDrawable(context, R.drawable.whole_bg_1)
    //// 2. 使用 KTX 扩展函数直接转换为 Bitmap
    //// toBitmap() 可以接收参数，例如 toBitmap(width, height) 来指定输出大小，不传则使用默认内部大小
    //val bitmap = drawable?.toBitmap()
    //if (bitmap != null) {
    //  Palette.from(bitmap).generate { palette ->
    //    if (palette != null) {
    //      generateDynamicBackground(palette)
    //    }
    //  }
    //}

    ////全量波形图数据填充
    //fragment.binding.audioWaveView.visibility = View.VISIBLE
    //viewModel.allWaveDataFlow.onEach {
    //  fragment.binding.audioWaveView.setPcmData(it)
    //}.launchIn(uiScope)
    //
    ////波形进度的更新
    //fragment.viewModel.currentPosition.onEach {
    //  //处理波形图的更新
    //  val total = fragment.viewModel.getPlayer()?.duration
    //  if (it >= 0 && total != null) {
    //    fragment.binding.audioWaveView.updatePosition(it.toFloat() / total)
    //  }
    //}.launchIn(uiScope)
  }

  // 假设这段代码在你的 Activity 或 Fragment 中
  fun extractCoverAndGeneratePalette(videoUri: Uri?) {
    // 启动一个生命周期感知的协程
    uiScope.launch {
      val context = fragment.requireContext()
      // 1. 构建 ImageRequest，不绑定任何 View
      val request = ImageRequest.Builder(context)
        .data(videoUri)
        .size(30 ,30)
        .build()

      // 2. 挂起函数：直接执行请求并等待结果
      val result = context.imageLoader.execute(request)

      var bitmap: Bitmap? = null
      // 3. 判断是否成功
      if (result is SuccessResult) {
        // Coil 3 的核心变化：result.image 是跨平台的 Image 接口
        // 在 Android 上，如果你请求的是位图，它实际上是 BitmapImage
        val coilImage = result.image
        bitmap = (coilImage as? BitmapImage)?.bitmap
      } else {
        // 处理失败的情况 (例如视频解析失败、URI无效等)
        // 可以给背景设置一个默认的深色
        // 1. 获取 Drawable 对象
        val drawable = ContextCompat.getDrawable(context, R.drawable.whole_bg_2)
        // 2. 使用 KTX 扩展函数直接转换为 Bitmap
        // toBitmap() 可以接收参数，例如 toBitmap(width, height) 来指定输出大小，不传则使用默认内部大小
        bitmap = drawable?.toBitmap(30, 30)
      }

      if (bitmap != null) {
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        //  fragment.binding.playBgView?.setRenderEffect(
        //    RenderEffect.createBlurEffect(
        //      25f,
        //      25f,
        //      Shader.TileMode.CLAMP
        //    )
        //  )
        //}
        //fragment.binding.playBgView?.setImageBitmap(bitmap)

        //Palette.from(bitmap).generate { palette ->
        //  val color = palette?.darkVibrantSwatch?.rgb ?: return@generate
        //
        //}
      }
    }
  }

  /**
   * 核心方法：根据提取出的 Palette 样本，生成质感渐变
   */
  private fun generateDynamicBackground(palette: Palette) {
    // =============================================
    // [策略] 如何选择颜色来实现“深色质感”？
    // 1. 我们需要两个颜色来做渐变（Top 和 Bottom）。
    // 2. Top Color：我们倾向于用图片中原本的暗色（DarkVibrant 或 DarkMuted）。
    // 3. Bottom Color：为了确保沉浸感，底部通常直接收敛到黑色或极深的灰色。
    // =============================================

    // 默认兜底颜色（防止图片提取不出颜色）
    val defaultColor = "#212121".toColorInt() // 深灰色
    val blackColor = "#0F2156FF".toColorInt()  // 纯黑

    // 决定顶部颜色：优先用柔和暗色，其次用鲜艳暗色，最后兜底
    val topColor = palette.darkVibrantSwatch?.rgb ?: defaultColor
    val endColor = palette.mutedSwatch?.rgb ?: blackColor

    // 4. 创建渐变 drawable
    // Orientation.TOP_BOTTOM：从上到下渐变
    // intArrayOf： Top color 渐变到 Bottom color (纯黑)
    val gradientDrawable = GradientDrawable(
      GradientDrawable.Orientation.TL_BR,
      intArrayOf(topColor, endColor)
    )

    // 也可以尝试径向渐变，更有“光晕”感
    // gradientDrawable.gradientType = GradientDrawable.RADIAL_GRADIENT
    // gradientDrawable.gradientRadius = 1000f

    // 5. 将生成的渐变背景设置给 View
    //backgroundView?.background = gradientDrawable
    //fragment.binding.root.setBackgroundDrawable(gradientDrawable)
    fragment.binding.root.setBackgroundColor(topColor)
    fragment.binding.setTimerTv.setBackgroundColor(topColor)
    fragment.binding.playSpeedTv.setBackgroundColor(topColor)
  }
}