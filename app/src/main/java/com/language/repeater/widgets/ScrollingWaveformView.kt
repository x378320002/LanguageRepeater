package com.language.repeater.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.language.repeater.pcm.PCMSegmentLoader
import com.language.repeater.pcm.Sentence
import com.language.repeater.pcm.WaveformPoint
import com.language.repeater.utils.CommonUtil
import com.language.repeater.utils.CommonUtil.toDp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * 滚动音频波形View
 * 特点：
 * 1. 中间有播放进度线
 * 2. 波形从右向左滚动
 * 3. 支持长音频的分段加载
 * 4. 预加载和缓存机制
 * 5. 支持手势拖动调整播放进度
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

  // ========== 拖动相关接口 ==========
  
  /**
   * 拖动监听器
   */
  interface OnSeekListener {
    /**
     * 拖动开始
     */
    fun onSeekStart()
    
    /**
     * 拖动中
     * @param position 当前拖动到的播放位置（秒）
     */
    fun onSeeking(position: Float)
    
    /**
     * 拖动结束
     * @param position 最终的播放位置（秒）
     */
    fun onSeekEnd(position: Float)
  }
  
  /** 拖动监听器 */
  private var onSeekListener: OnSeekListener? = null

  /** AB边界变化监听器 */
  interface OnABChangeListener {
    fun onABDragStart(dragAbResult: ABHitResult?)

    fun onABDragging(dragAbResult: ABHitResult?)

    fun onABDragEnd(dragAbResult: ABHitResult?)
  }
  
  /** AB边界变化监听器 */
  private var onABChangeListener: OnABChangeListener? = null

  // ========== 画笔 ==========
  //未播放画笔
  private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFF1565C0.toInt()
    style = Paint.Style.STROKE
    strokeWidth = 1f
    strokeCap = Paint.Cap.ROUND
  }

  // 用于绘制填充区域的画笔
  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = 0xFF64B5F6.toInt()
    //alpha = 1 // 0-255
  }

  //已经播放画笔
  private val playedWaveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFF90CAF9.toInt()
    style = Paint.Style.STROKE
    strokeWidth = 1f
    strokeCap = Paint.Cap.ROUND
  }

  // 用于绘制填充区域的画笔
  private val playedFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = 0xFF90CAF9.toInt()
    alpha = 180 // 0-255
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
    color = 0xFF0000FF.toInt()
    strokeWidth = 1f.toDp()
  }

  //中线画笔
  private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x20000000
    strokeWidth = 1f
  }

  //draw时间的画笔
  val timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

//  /** 波形数据缓存：时间窗口 -> 波形数据 */
//  private val waveformCache = mutableMapOf<Int, List<WaveformPoint>>()

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
  private var sentences: List<Sentence>? = null

  // ========== 手势相关 ==========
  
  /** 是否正在拖动 */
  private var isDragging = false
  
  /** 拖动开始时的播放位置 */
  private var dragStartTime = 0f

  private var dragABResult: ABHitResult? = null

  /** 当前拖动的AB边界类型 ("A" 或 "B") */
  private var draggingABType: String? = null

  /** 拖动开始时的AB边界时间 */
  private var dragABStartTime = 0f

  // ========== 公共方法 ==========

  /**
   * 设置拖动监听器
   */
  fun setOnSeekListener(listener: OnSeekListener?) {
    onSeekListener = listener
  }

  /**
   * 设置AB边界变化监听器
   */
  fun setOnABChangeListener(listener: OnABChangeListener?) {
    onABChangeListener = listener
  }

  /**
   * 设置PCM文件
   */
  fun setPCMLoader(loader: PCMSegmentLoader, pos: Long, loadWindowComplete: ((Int)->Unit)? = null) {
    if (pcmLoader == loader) {
      return
    }

    pcmLoader = loader
    totalDuration = pcmLoader?.totalDuration ?: 0f
    currentTime = pos / 1000f

    //waveformCache.clear()
    //提前绘制一遍, 清空上一个视频的内容
    //invalidate()
    refreshWave(loadWindowComplete)
  }

  fun setSentenceData(data : List<Sentence>) {
    sentences = data
    invalidate()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)
    if (width != 0) {
      refreshWave()
    }
  }

  /**
   * 重新计算可见时间范围
   */
  private fun refreshWave(loadWindowComplete: ((Int)->Unit)? = null) {
    if (width == 0 || pcmLoader == null) return

    // 中心线在屏幕中央
    val centerX = width / 2f
    val visibleDuration = centerX / pixelsPerSecond

    // 计算可见范围：中心线左右各一半
    visibleStartTime = (currentTime - visibleDuration).coerceAtLeast(0f)
    visibleEndTime = (currentTime + visibleDuration).coerceAtMost(totalDuration)

    checkAndLoadData(loadWindowComplete)
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
      if (!loader.waveformCache.containsKey(windowIndex)) {
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
              loader.waveformCache[windowIndex] = waveformData
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
    cleanupOldCache(startWindow - preloadWindowCount)
  }

  //需要绘制的数据
  private val leftData = mutableListOf<WaveformPoint>()
  private val rightData = mutableListOf<WaveformPoint>()
  private fun prepareWaveData() {
    val loader = pcmLoader ?: return
    val startTime = visibleStartTime
    val endTime = visibleEndTime
    val curTime = currentTime
    val startWindow = (startTime / cacheWindowSize).toInt()
    val endWindow = (endTime / cacheWindowSize).toInt()
    leftData.clear()
    rightData.clear()
    for (windowIndex in startWindow..endWindow) {
      val windowData = loader.waveformCache[windowIndex] ?: continue
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
    val loader = pcmLoader?:return
    val toRemove = loader.waveformCache.keys.filter { it < beforeWindow }
    toRemove.forEach { loader.waveformCache.remove(it) }
  }

  // ========== 触摸事件处理 ==========
  /** 手势检测器 */
  private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

    override fun onDown(e: MotionEvent): Boolean {
      // 记录拖动开始时的播放位置
      dragStartTime = currentTime
      dragABResult = checkABHitTest(e.x, e.y)?.apply {
        onABChangeListener?.onABDragStart(this)
      }
      return true
    }

    override fun onScroll(
      e1: MotionEvent?,
      e2: MotionEvent,
      distanceX: Float,
      distanceY: Float
    ): Boolean {
      if (pcmLoader == null) {
        return false
      }
      val dragResult = dragABResult
      if (dragResult != null) {
        //拖动AB的逻辑
        handleABDrag(distanceX)
      } else {
        if (!isDragging) {
          // 开始拖动
          isDragging = true
          onSeekListener?.onSeekStart()
        }

        // 计算拖动对应的时间偏移
        // distanceX > 0 表示向左滑动（时间增加）
        // distanceX < 0 表示向右滑动（时间减少）
        val deltaTime = distanceX / pixelsPerSecond
        val newTime = (currentTime + deltaTime).coerceIn(0f, totalDuration)

        // 更新当前时间并刷新显示
        currentTime = newTime
        refreshWave()

        // 通知监听器
        onSeekListener?.onSeeking(currentTime)
      }
      return true
    }
  })

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    // 先让手势检测器处理
    val gestureHandled = gestureDetector.onTouchEvent(event)
    
    // 处理拖动结束
    when (event.action) {
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        if (isDragging) {
          // 拖动结束，通知监听器最终位置
          onSeekListener?.onSeekEnd(currentTime)
          isDragging = false
        }

        val dragResult = dragABResult
        if (dragResult != null) {
          // AB边界拖动结束
          onABChangeListener?.onABDragEnd(dragResult)
          dragABResult = null
        }
      }
    }
    
    return gestureHandled || super.onTouchEvent(event)
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

    // 准备已播放和未播放的波形数据
    // 分别绘制已播放和未播放的波形
    prepareWaveData()
    drawWaveformSection(canvas, leftData, waveformPaint, playedFillPaint)
    drawWaveformSection(canvas, rightData, waveformPaint, fillPaint)

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
    sentences?.forEach { seg->
      drawSignPoint(canvas, seg.start, voiceStartPaint)
      drawSignPoint(canvas, seg.end, voiceEndPaint)
      if (curABSeg == seg) {
        drawAB(canvas, seg.start, "A")
        drawAB(canvas, seg.end, "B")
      }
    }
  }

  val abBgRadius = 6f.toDp()
  val abBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = 0xFF00FFFF.toInt()
  }
  val abTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFFFF0000.toInt()
    textSize = 10f.toDp()
    textAlign = Paint.Align.CENTER
    typeface = Typeface.DEFAULT_BOLD
  }
  var abTextOffsetY = 0f

  private fun drawAB(canvas: Canvas, time: Float, text: String) {
    if (time < visibleStartTime || time > visibleEndTime) {
      return
    }

    val x = timeToX(time)
    val y = height / 2f
    
    // 绘制白色实心圆背景
    canvas.drawCircle(x, y, abBgRadius, abBgPaint)
    
    // 绘制红色字体 "A"
    // 计算文字的垂直居中位置
    if (abTextOffsetY == 0f) {
      val fontMetrics = abTextPaint.fontMetrics
      abTextOffsetY = (fontMetrics.ascent + fontMetrics.descent) / 2
    }

    val textY = y - abTextOffsetY
    canvas.drawText(text, x, textY, abTextPaint)
  }

  private fun drawSignPoint(canvas: Canvas, time: Float, paint: Paint) {
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
    paint: Paint,
    fillPaint: Paint
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
//    canvas.drawPath(path, paint)
//    path.reset()
//    isFirst = true
//    waveformData.forEach { point ->
//      val x = timeToX(point.time)
//      val y = centerY - (point.min / maxAmplitude) * availableHeight
//      if (isFirst) {
//        path.moveTo(x, y)
//        isFirst = false
//      } else {
//        path.lineTo(x, y)
//      }
//    }

    //绘制成实心的
    for (i in waveformData.lastIndex downTo 0) {
      val point = waveformData[i]
      val x = timeToX(point.time)
      val y = centerY - (point.min / maxAmplitude) * availableHeight
      path.lineTo(x, y)
    }
    path.close()

    canvas.drawPath(path, fillPaint)
    canvas.drawPath(path, paint)
  }

  /**
   * 绘制进度指示器
   */
  private fun drawProgressIndicator(canvas: Canvas, centerX: Float) {
    val textSize = timeTextPaint.textSize
    timeTextPaint.textAlign = Paint.Align.CENTER
    canvas.drawText(
      CommonUtil.formatTimeFloat(currentTime),
      width / 2f,
      textSize,
      timeTextPaint
    )

    timeTextPaint.textAlign = Paint.Align.LEFT
    canvas.drawText(
      CommonUtil.formatTime(0f),
      0f,
      textSize,
      timeTextPaint
    )

    timeTextPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(
      CommonUtil.formatTime(totalDuration),
      width.toFloat(),
      textSize,
      timeTextPaint
    )

    // 绘制进度线
    canvas.drawLine(centerX, textSize + 10, centerX, height.toFloat(), progressLinePaint)
  }

  /**
   * 更新播放位置
   * @param positionSeconds 当前播放位置（秒）
   */
  fun updatePosition(positionSeconds: Float) {
    if (pcmLoader == null) return
    // 如果正在拖动，不要更新位置，避免冲突
    if (isDragging) return
    
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
    //waveformCache.clear()
  }

  /**
   * AB边界点击检测结果
   */
  data class ABHitResult(
    val sentence: Sentence,
    val abType: String // "A" 或 "B"
  )

  var curABSeg: Sentence? = null

  /**
   * 检查点击是否在AB边界上
   * @param x 点击的X坐标
   * @param y 点击的Y坐标
   * @return 如果点击在AB边界上，返回ABHitResult，否则返回null
   */
  private fun checkABHitTest(x: Float, y: Float): ABHitResult? {
    val sentence = curABSeg ?: return null
    if (sentences == null) return null

    val hitRadius = abBgRadius * 2 // 扩大点击检测范围

    // 检查是否点击在A边界上
    val curABA = timeToX(sentence.start)
    if (curABA >= 0f && curABA <= width && x in (curABA - hitRadius)..(curABA + hitRadius)) {
      return ABHitResult(sentence, "A")
    }

    // 检查是否点击在B边界上
    val curABB = timeToX(sentence.end)
    if (curABB >= 0f && curABB <= width && x in (curABB - hitRadius)..(curABB + hitRadius)) {
      return ABHitResult(sentence, "B")
    }
    return null
  }

  /**
   * 处理AB边界拖动
   * @param distanceX 拖动的距离（像素）
   */
  private fun handleABDrag(distanceX: Float) {
    val result = dragABResult ?: return
    val sentence = result.sentence
    val deltaTime = distanceX / pixelsPerSecond
    
    // 根据拖动的AB类型更新时间
    var hasChanged = false
    when (result.abType) {
      "A" -> {
        val newStartTime = sentence.start - deltaTime
        // 确保A不能超过B
        if (newStartTime >= 0 && newStartTime < sentence.end) {
          sentence.start = newStartTime
          hasChanged = true

        }
      }
      "B" -> {
        val newEndTime = sentence.end - deltaTime
        // 确保B不能小于A
        if (newEndTime > sentence.start && newEndTime <= totalDuration) {
          sentence.end = newEndTime
          hasChanged = true
        }
      }
    }

    if (hasChanged) {
      // 刷新显示
      invalidate()
      // 通知监听器
      onABChangeListener?.onABDragging(result)
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    cleanup()
  }
}
