package com.language.repeater.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Log
import android.view.View
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.min

/**
 * 滚动音频波形View
 * 特点：
 * 1. 中间有播放进度线
 * 2. 波形从右向左滚动
 * 3. 支持长音频的分段加载
 * 4. 预加载和缓存机制
 */
class ScrollingWaveformView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  // ========== 配置参数 ==========

  /** 每秒音频对应的像素宽度 */
  val pixelsPerSecond: Float = 100f
  val secondPerPixel: Float = 1/100f

  /** 波形颜色 */
  val waveformColor: Int = 0xFF2196F3.toInt()

  /** 已播放波形颜色 */
  val playedWaveformColor: Int = 0xFF90CAF9.toInt()

  /** 进度线颜色 */
  val progressLineColor: Int = 0xFFFF5722.toInt()

  /** 背景颜色 */
  val waveBackgroundColor: Int = 0xFFF5F5F5.toInt()

  // ========== 画笔 ==========
  //未播放画笔
  private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = waveformColor
    style = Paint.Style.FILL_AND_STROKE
    strokeWidth = 2f
    strokeCap = Paint.Cap.ROUND
  }

  //已经播放画笔
  private val playedWaveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = playedWaveformColor
    style = Paint.Style.FILL_AND_STROKE
    strokeWidth = 2f
    strokeCap = Paint.Cap.ROUND
  }

  //进度条
  private val progressLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = progressLineColor
    strokeWidth = 3f
  }

  //进度条三角形
  val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = progressLineColor
    style = Paint.Style.FILL
  }

  //中线画笔
  private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x20000000
    strokeWidth = 1f
  }

  private val path = Path()

  // ========== 内部状态 ==========

  /** PCM数据加载器 */
  private var pcmLoader: PCMSegmentLoader? = null

  /** 当前播放位置（秒） */
  private var currentPositionSeconds: Float = 0f

  /** 音频总时长（秒） */
  private var totalDurationSeconds: Float = 0f

  /** 波形数据缓存：时间窗口 -> 波形数据 */
  private val waveformCache = mutableMapOf<Int, List<WaveformPoint>>()

  /** 缓存窗口大小（秒） */
  private val cacheWindowSize = 5f

  /** 预加载的窗口数量 */
  private val preloadWindowCount = 3

  /** 协程作用域 */
  private val scope = CoroutineScope(Dispatchers.Main + Job())

  /** 当前可见时间范围 */
  private var visibleStartTime = 0f
  private var visibleEndTime = 0f

  /** 是否正在播放 */
  private var isPlaying = false

  // ========== 公共方法 ==========

  /**
   * 设置PCM文件
   */
  fun setPCMLoader(loader: PCMSegmentLoader) {
    pcmLoader = loader
    totalDurationSeconds = pcmLoader?.durationSeconds ?: 0f
    currentPositionSeconds = 0f

    waveformCache.clear()
    recalculateVisibleRange()

    // 预加载初始数据
    loadWaveformData(0f, cacheWindowSize * preloadWindowCount)

    invalidate()
  }

//  /**
//   * 设置PCM文件
//   */
//  fun setPCMFile(pcmFile: File) {
//    pcmLoader = PCMSegmentLoader(pcmFile)
//    totalDurationSeconds = pcmLoader?.durationSeconds ?: 0f
//    currentPositionSeconds = 0f
//
//    waveformCache.clear()
//    recalculateVisibleRange()
//
//    // 预加载初始数据
//    loadWaveformData(0f, cacheWindowSize * preloadWindowCount)
//
//    invalidate()
//  }

  /**
   * 更新播放位置
   * @param positionSeconds 当前播放位置（秒）
   */
  fun updatePosition(positionSeconds: Float) {
    currentPositionSeconds = positionSeconds.coerceIn(0f, totalDurationSeconds)
    recalculateVisibleRange()

    // 检查是否需要加载新数据
    checkAndLoadData()

    invalidate()
  }

  /**
   * 开始播放动画
   */
  fun startPlayback() {
    isPlaying = true
  }

  /**
   * 停止播放动画
   */
  fun stopPlayback() {
    isPlaying = false
  }

  /**
   * 跳转到指定位置
   */
  fun seekTo(positionSeconds: Float) {
    updatePosition(positionSeconds)
  }

  /**
   * 清理资源
   */
  fun cleanup() {
    scope.cancel()
    waveformCache.clear()
  }

  // ========== 内部方法 ==========

  /**
   * 重新计算可见时间范围
   */
  private fun recalculateVisibleRange() {
    if (width == 0) return

    // 中心线在屏幕中央
    val centerX = width / 2f
    val visibleDuration = centerX / pixelsPerSecond

    // 计算可见范围：中心线左右各一半
    visibleStartTime = (currentPositionSeconds - visibleDuration).coerceAtLeast(0f)
    visibleEndTime = (currentPositionSeconds + visibleDuration).coerceAtMost(totalDurationSeconds)
  }

  /**
   * 检查并加载需要的数据
   */
  private fun checkAndLoadData() {
    val loader = pcmLoader ?: return

    // 计算需要哪些窗口的数据
    val startWindow = (visibleStartTime / cacheWindowSize).toInt()
    val endWindow = (visibleEndTime / cacheWindowSize).toInt() + 1
    val preloadEnd = endWindow + preloadWindowCount

    // 加载缺失的窗口
    for (windowIndex in startWindow..preloadEnd) {
      if (!waveformCache.containsKey(windowIndex)) {
        val windowStartTime = windowIndex * cacheWindowSize

        // 超出音频范围则跳过
        if (windowStartTime >= totalDurationSeconds) continue

        // 异步加载
        scope.launch(Dispatchers.IO) {
          try {
            val windowEndTime = min(
              windowStartTime + cacheWindowSize,
              totalDurationSeconds
            )
            val duration = windowEndTime - windowStartTime

            // 计算需要多少数据点
            val pointsNeeded = (duration * pixelsPerSecond).toInt()

            val waveformData = loader.loadWaveformData(
              startTimeSeconds = windowStartTime,
              durationSeconds = duration,
              targetPoints = pointsNeeded
            )

            withContext(Dispatchers.Main) {
              waveformCache[windowIndex] = waveformData
              invalidate()
            }
          } catch (e: Exception) {
            e.printStackTrace()
          }
        }
      }
    }

    // 清理过远的缓存
    cleanupOldCache(startWindow - 2)
  }

  /**
   * 清理旧缓存
   */
  private fun cleanupOldCache(beforeWindow: Int) {
    val toRemove = waveformCache.keys.filter { it < beforeWindow }
    toRemove.forEach { waveformCache.remove(it) }
  }

  /**
   * 加载波形数据
   */
  private fun loadWaveformData(startTime: Float, duration: Float) {
    val loader = pcmLoader ?: return

    scope.launch(Dispatchers.IO) {
      try {
        val startWindow = (startTime / cacheWindowSize).toInt()
        val endWindow = ((startTime + duration) / cacheWindowSize).toInt() + 1

        for (windowIndex in startWindow until endWindow) {
          if (waveformCache.containsKey(windowIndex)) continue

          val windowStartTime = windowIndex * cacheWindowSize
          if (windowStartTime >= totalDurationSeconds) continue

          val windowEndTime = min(
            windowStartTime + cacheWindowSize,
            totalDurationSeconds
          )
          val windowDuration = windowEndTime - windowStartTime
          val pointsNeeded = (windowDuration * pixelsPerSecond).toInt()

          val waveformData = loader.loadWaveformData(
            startTimeSeconds = windowStartTime,
            durationSeconds = windowDuration,
            targetPoints = pointsNeeded
          )

          withContext(Dispatchers.Main) {
            waveformCache[windowIndex] = waveformData
            invalidate()
          }
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  /**
   * 时间转换为X坐标
   */
  private fun timeToX(timeSeconds: Float): Float {
    val centerX = width / 2f
    val offsetFromCurrent = timeSeconds - currentPositionSeconds
    return centerX + offsetFromCurrent * pixelsPerSecond
  }

  /**
   * 从缓存中获取指定时间范围的波形数据
   */
  private fun getWaveformInRange(startTime: Float, endTime: Float): List<Pair<Float, WaveformPoint>> {
    val result = mutableListOf<Pair<Float, WaveformPoint>>()
    val startWindow = (startTime / cacheWindowSize).toInt()
    val endWindow = (endTime / cacheWindowSize).toInt()

    for (windowIndex in startWindow..endWindow) {
      val windowData = waveformCache[windowIndex] ?: continue
      val windowStartTime = windowIndex * cacheWindowSize

      windowData.forEachIndexed { index, point ->
        val pointTime = windowStartTime + (index.toFloat() / windowData.size) * cacheWindowSize

        if (point.time in startTime..endTime) {
          result.add(Pair(pointTime, point))
        }
      }
    }

    return result
  }

  // ========== 绘制 ==========

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    recalculateVisibleRange()
    checkAndLoadData()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    // 背景
    canvas.drawColor(waveBackgroundColor)

    val centerY = height / 2f
    val centerX = width / 2f

    // 绘制中心线
    canvas.drawLine(0f, centerY, width.toFloat(), centerY, centerLinePaint)

    // 获取可见范围的波形数据
    val waveformData = getWaveformInRange(visibleStartTime, visibleEndTime)

    if (waveformData.isEmpty()) {
      // 显示加载提示
      drawLoadingIndicator(canvas)
      return
    }

    // 分别绘制已播放和未播放的波形
    drawWaveformSection(canvas, waveformData, visibleStartTime, currentPositionSeconds, playedWaveformPaint)
    drawWaveformSection(canvas, waveformData, currentPositionSeconds, visibleEndTime, waveformPaint)

    // 绘制进度线
    canvas.drawLine(centerX, 0f, centerX, height.toFloat(), progressLinePaint)

    // 绘制进度指示器（小三角形）
    drawProgressIndicator(canvas, centerX)
  }

  /**
   * 绘制波形片段
   */
  private fun drawWaveformSection(
    canvas: Canvas,
    waveformData: List<Pair<Float, WaveformPoint>>,
    startTime: Float,
    endTime: Float,
    paint: Paint
  ) {
    val centerY = height / 2f
    val maxAmplitude = 32767f
    val availableHeight = height / 2f * 0.9f

    // 过滤出指定时间范围的数据
    val sectionData = waveformData.filter { it.second.time in startTime..endTime }

    if (sectionData.isEmpty()) return

    // 绘制上半部分
    path.reset()
    var isFirst = true

    sectionData.forEach { (time, point) ->
      val x = timeToX(point.time)
      val y = centerY - (point.max / maxAmplitude) * availableHeight

      if (isFirst) {
        path.moveTo(x, y)
        isFirst = false
      } else {
        path.lineTo(x, y)
      }
    }

    //canvas.drawPath(path, paint)

    // 绘制下半部分
    //path.reset()
    //isFirst = true

    for (i in sectionData.lastIndex downTo 0) {
      val time = sectionData[i].second.time
      val point = sectionData[i].second
      val x = timeToX(time)
      val y = centerY - (point.min / maxAmplitude) * availableHeight
      path.lineTo(x, y)
    }

//    sectionData.forEach { (time, point) ->
//      val x = timeToX(time)
//      val y = centerY - (point.min / maxAmplitude) * availableHeight
//
//      if (isFirst) {
//        path.moveTo(x, y)
//        isFirst = false
//      } else {
//        path.lineTo(x, y)
//      }
//    }

    path.close()

    canvas.drawPath(path, paint)
  }

  /**
   * 绘制进度指示器
   */
  private fun drawProgressIndicator(canvas: Canvas, x: Float) {
    val triangleSize = 15f

    // 顶部三角形
    path.reset()
    path.moveTo(x, 0f)
    path.lineTo(x - triangleSize, triangleSize)
    path.lineTo(x + triangleSize, triangleSize)
    path.close()

    canvas.drawPath(path, progressLinePaint)

    // 底部三角形
    path.reset()
    path.moveTo(x, height.toFloat())
    path.lineTo(x - triangleSize, height - triangleSize)
    path.lineTo(x + triangleSize, height - triangleSize)
    path.close()

    canvas.drawPath(path, progressLinePaint)
  }

  /**
   * 绘制加载指示器
   */
  private fun drawLoadingIndicator(canvas: Canvas) {
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = 0xFF666666.toInt()
      textSize = 40f
      textAlign = Paint.Align.CENTER
    }

    canvas.drawText(
      "加载中...",
      width / 2f,
      height / 2f,
      textPaint
    )
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    cleanup()
  }
}

// ========== 数据类 ==========

// ========== PCM加载器（简化版，与之前的类似）==========

