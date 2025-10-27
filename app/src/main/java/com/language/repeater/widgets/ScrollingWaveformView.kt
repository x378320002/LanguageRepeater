package com.language.repeater.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.pcm.WaveformPoint
import com.language.repeater.utils.CommonUtil
import com.language.repeater.utils.CommonUtil.toDp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.isNullOrEmpty
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

  /**
   * 每秒音频对应的像素宽度
   * 这个数字越大, 整体波形越长, 播放时移动的越快
   */
  val pixelsPerSecond: Float = 80f

  /** 背景颜色 */
  val waveBackgroundColor: Int = 0xFFF5F5F5.toInt()

  // ========== 画笔 ==========
  //未播放画笔
  private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFF1565C0.toInt()
    style = Paint.Style.STROKE
    strokeWidth = 2f
    strokeCap = Paint.Cap.ROUND
  }

  //已经播放画笔
  private val playedWaveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFF90CAF9.toInt()
    style = Paint.Style.STROKE
    strokeWidth = 2f
    strokeCap = Paint.Cap.ROUND
  }

  //进度条
  private val progressLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFFFF5722.toInt()
    strokeWidth = 1f.toDp()
    strokeCap = Paint.Cap.ROUND
  }

  //voice开始指示器
  private val voiceStartPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFFFF0000.toInt()
    strokeWidth = 1f.toDp()
  }

  //voice结束指示器
  private val voiceEndPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFF00FF00.toInt()
    strokeWidth = 1f.toDp()
  }

  //中线画笔
  private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x20000000
    strokeWidth = 1f
  }

  //draw时间的画笔
  val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFF666666.toInt()
    textSize = 30f
    textAlign = Paint.Align.CENTER
  }

  private val path = Path()

  // ========== 内部状态 ==========

  /** PCM数据加载器 */
  private var pcmLoader: PCMSegmentLoader? = null

  /** 当前播放位置（秒） */
  private var currentTime: Float = 0f

  /** 音频总时长（秒） */
  private var totalDuration: Float = 0f

  /** 波形数据缓存：时间窗口 -> 波形数据 */
  private val waveformCache = mutableMapOf<Int, List<WaveformPoint>>()

  /** 缓存窗口大小（秒） */
  private val cacheWindowSize = 5f

  /** 预加载的窗口数量 */
  private val preloadWindowCount = 2

  /** 协程作用域 */
  private val scope = CoroutineScope(Dispatchers.Main + Job())

  /** 当前可见时间范围 */
  private var visibleStartTime = 0f
  private var visibleEndTime = 0f

  //分句子信息
  private var sentences: List<Pair<Float, Float>>? = null

  // ========== 公共方法 ==========

  /**
   * 设置PCM文件
   */
  fun setPCMLoader(loader: PCMSegmentLoader, loadWindowComplete: ((Int)->Unit)? = null) {
    if (pcmLoader == loader) {
      return
    }

    pcmLoader = loader
    totalDuration = pcmLoader?.totalDuration ?: 0f
    currentTime = 0f

    waveformCache.clear()
    //提前绘制一遍, 清空上一个视频的内容
    invalidate()
    refreshWave(loadWindowComplete)
  }

  fun setSentenceData(data : List<Pair<Float, Float>>) {
    sentences = data
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
   * 重新计算可见时间范围
   */
  private fun refreshWave(loadWindowComplete: ((Int)->Unit)? = null) {
    if (width == 0) return

    // 中心线在屏幕中央
    val centerX = width / 2f
    val visibleDuration = centerX / pixelsPerSecond

    // 计算可见范围：中心线左右各一半
    visibleStartTime = (currentTime - visibleDuration).coerceAtLeast(0f)
    visibleEndTime = (currentTime + visibleDuration).coerceAtMost(totalDuration)

    checkAndLoadData(loadWindowComplete)

    // 准备已播放和未播放的波形数据
    prepareWaveData()

    invalidate()
  }

  /**
   * 检查并加载需要的数据
   */
  private fun checkAndLoadData(loadWindowComplete: ((Int)->Unit)? = null) {
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
        if (windowStartTime >= totalDuration) continue

        // 异步加载
        scope.launch(Dispatchers.IO) {
          try {
            val windowEndTime = min(
              windowStartTime + cacheWindowSize,
              totalDuration
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
              loadWindowComplete?.invoke(windowIndex)
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

  //需要绘制的数据
  private val leftData = mutableListOf<WaveformPoint>()
  private val rightData = mutableListOf<WaveformPoint>()
  private fun prepareWaveData() {
    val startTime = visibleStartTime
    val endTime = visibleEndTime
    val curTime = currentTime
    val startWindow = (startTime / cacheWindowSize).toInt()
    val endWindow = (endTime / cacheWindowSize).toInt()
    leftData.clear()
    rightData.clear()
    for (windowIndex in startWindow..endWindow) {
      val windowData = waveformCache[windowIndex] ?: continue
      windowData.forEachIndexed { index, point ->
        if (point.time in startTime..curTime) {
          leftData.add(point)
        } else if (point.time in curTime..endTime) {
          rightData.add(point)
        }
      }
    }
  }

  /**
   * 清理旧缓存
   */
  private fun cleanupOldCache(beforeWindow: Int) {
    val toRemove = waveformCache.keys.filter { it < beforeWindow }
    toRemove.forEach { waveformCache.remove(it) }
  }

  // ========== 绘制 ==========
  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    refreshWave()
  }

  /**
   * 时间转换为X坐标
   */
  private fun timeToX(timeSeconds: Float): Float {
    val centerX = width / 2f
    val offsetFromCurrent = timeSeconds - currentTime
    return centerX + offsetFromCurrent * pixelsPerSecond
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    // 背景
    canvas.drawColor(waveBackgroundColor)

    val centerY = height / 2f
    val centerX = width / 2f

    // 绘制中心线
    canvas.drawLine(0f, centerY, width.toFloat(), centerY, centerLinePaint)

    // 分别绘制已播放和未播放的波形
    drawWaveformSection(canvas, leftData, playedWaveformPaint)
    drawWaveformSection(canvas, rightData, waveformPaint)

    // 绘制进度指示器（竖线+小三角形）
    drawProgressIndicator(canvas, centerX)

    //绘制每句话的起始点标记
    drawSentenceSign(canvas)
  }

  /**
   * 绘制每句话的开始和结束点
   */
  private fun drawSentenceSign(canvas: Canvas) {
    if (leftData.isEmpty() || rightData.isEmpty()) {
      return
    }
    sentences?.forEach {seg->
      drawPoint(canvas, seg.first, voiceStartPaint)
      drawPoint(canvas, seg.second, voiceEndPaint)
    }
  }

  fun drawPoint(canvas: Canvas, time: Float, paint: Paint) {
    if (time < visibleStartTime || time > visibleEndTime) {
      return
    }
    val x = timeToX(time)
    val y = height / 2f
    canvas.drawLine(x, y - 12, x, y + 12, paint)
  }

  /**
   * 绘制波形片段
   */
  private fun drawWaveformSection(
    canvas: Canvas,
    waveformData: List<WaveformPoint>,
    paint: Paint
  ) {
    val centerY = height / 2f
    val maxAmplitude = 32767f
    val availableHeight = height / 2f * 0.9f

    // 绘制上半部分
    path.reset()
    var isFirst = true
    waveformData.forEach { point ->
      val x = timeToX(point.time)
      val y = centerY - (point.max / maxAmplitude) * availableHeight
      if (isFirst) {
        path.moveTo(x, y)
        isFirst = false
      } else {
        path.lineTo(x, y)
      }
    }

    // 绘制下半部分
    //如果只画轮廓打开下面的注释
    canvas.drawPath(path, paint)
    path.reset()
    isFirst = true
    waveformData.forEach { point ->
      val x = timeToX(point.time)
      val y = centerY - (point.min / maxAmplitude) * availableHeight
      if (isFirst) {
        path.moveTo(x, y)
        isFirst = false
      } else {
        path.lineTo(x, y)
      }
    }

    canvas.drawPath(path, paint)
  }

  /**
   * 绘制进度指示器
   */
  private fun drawProgressIndicator(canvas: Canvas, centerX: Float) {
    val textSize = textPaint.textSize
    textPaint.textAlign = Paint.Align.CENTER
    canvas.drawText(
      CommonUtil.formatTimeFloat(currentTime),
      width / 2f,
      textSize,
      textPaint
    )

    textPaint.textAlign = Paint.Align.LEFT
    canvas.drawText(
      CommonUtil.formatTime(0f),
      0f,
      textSize,
      textPaint
    )

    textPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(
      CommonUtil.formatTime(totalDuration),
      width.toFloat(),
      textSize,
      textPaint
    )

    // 绘制进度线
    canvas.drawLine(centerX, textSize + 10, centerX, height.toFloat(), progressLinePaint)
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

  /**
   * 更新播放位置
   * @param positionSeconds 当前播放位置（秒）
   */
  fun updatePosition(positionSeconds: Float) {
    if (pcmLoader == null) return
    currentTime = positionSeconds.coerceIn(0f, totalDuration)
    refreshWave()
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

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    cleanup()
  }
}

