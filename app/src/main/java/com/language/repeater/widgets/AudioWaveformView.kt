package com.language.repeater.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.common.collect.Multimaps.index
import com.language.repeater.pcm.WaveformPoint

/**
 * 一个用于绘制音频PCM数据波形图的自定义View。
 */
class AudioWaveformView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

  // 1. 将绘图对象作为成员变量，避免在 onDraw 中重复创建
  private var pcmData: List<WaveformPoint> = listOf()
//  private var pathPoints: Array<PointF>? = null
  // 复用 Path 对象
  private val path = Path()

  // 用于绘制填充区域的画笔
  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    color = 0xFF64B5F6.toInt()
    //alpha = 1 // 0-255
  }

  // 用于绘制波形轮廓线的画笔
  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    color = 0xFF1565C0.toInt() // 对应 Color(0xFF1565C0)
    strokeWidth = 1f
  }

  // 用于绘制中心线的画笔
  private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x80D3D3D3.toInt() // 对应 Color.LightGray.copy(alpha = 0.5f)
    strokeWidth = 1f
  }

  private val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0x20000000.toInt() // 对应 Color.LightGray.copy(alpha = 0.5f)
    style = Paint.Style.FILL
  }

  /**
   * 设置要显示的音频PCM数据，并触发重绘。
   * @param data 原始PCM数据数组（通常是16-bit，范围从 -32768 到 32767）。
   */
  fun setPcmData(data: List<WaveformPoint>) {
    if (data != this.pcmData) {
      this.pcmData = data
//      pathPoints = null
      // 请求重新绘制 View
      invalidate()
    }
  }

  /**
   * 核心绘图逻辑。
   */
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    // 背景色建议通过 XML 的 android:background 属性设置，这里不再绘制
    // canvas.drawColor(0xFFFFF3E0.toInt()) // 对应 PastelOrange

    if (pcmData.isEmpty()) {
      return
    }

    val canvasWidth = width.toFloat()
    val canvasHeight = height.toFloat()
    val centerY = canvasHeight / 2f
    val availableHeight = centerY * 0.9f

    // 重置路径，以便下次重绘
    path.reset()
    // 从左侧中点开始
    path.moveTo(0f, centerY)

    // 计算每个数据点在X轴上的步长
    val xStep = canvasWidth / (pcmData.size - 1).coerceAtLeast(1)
//    if (pathPoints.isNullOrEmpty()) {
//      @SuppressLint("DrawAllocation")
//      pathPoints = Array(pcmData.size) {index ->
//        val value = pcmData[index]
//        // 归一化振幅并映射到视图高度
////        val amp = abs((value / 32768f) * centerY)
//        val amp = (value / 32768f) * centerY
//        val x = index * xStep
//        PointF(x, amp)
//      }
//    }
//    val points = pathPoints ?: return

    for (i in pcmData.indices) {
      val p = pcmData[i]
      val x = i * xStep
      val y = centerY - (p.max / 32768f) * availableHeight
      path.lineTo(x, y)
    }
    path.lineTo(canvasWidth, centerY) // 连接到右下角
    for (i in pcmData.lastIndex downTo 0) {
      val p = pcmData[i]
      val x = i * xStep
      val y = centerY - (p.min / 32768f) * availableHeight
      path.lineTo(x, y)
    }
    path.close() // 闭合路径

    canvas.drawPath(path, fillPaint)

    // 4. 绘制波形线条（轮廓）
    canvas.drawPath(path, strokePaint)

    // 5. 绘制中心线
    canvas.drawLine(0f, centerY, canvasWidth, centerY, centerLinePaint)

    canvas.drawRect(0f, 0f, canvasWidth * currentProgress, canvasHeight, playedPaint)
  }

  var currentProgress = 0f
  /**
   * 更新播放位置, 0-1
   */
  fun updatePosition(positionSeconds: Float) {
    currentProgress = positionSeconds
    invalidate()
  }
}