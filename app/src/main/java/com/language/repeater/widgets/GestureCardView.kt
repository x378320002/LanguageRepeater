package com.language.repeater.widgets

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.cardview.widget.CardView
import kotlin.math.abs

class GestureCardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
) : CardView(context, attrs, defStyleAttr) {

  companion object {
    const val STATE_INIT = 0
    const val STATE_LEFT_SCROLL = 1
    const val STATE_RIGHT_SCROLL = 2
    const val STATE_OTHER_SCROLL = 3
  }

  private var gestureListener: OnGestureListener? = null
  var detectLeftScroll = true //是否检测左侧上下滑
  var detectRightScroll = true //是否检测右侧上下滑

  // 记录手指按下的坐标，用于计算长按位置和移动判断
  private var downX = 0f
  private var downY = 0f
  // 标记当前是否已经触发了长按
  private var isLongPressed = false
  // 0:未识别, 1:左侧1/3上下滑, 2:右侧1/3上下滑, 3, 其他
  private var scrollState = 0

  // 用于检测点击、双击、Fling
  private val gestureDetector: GestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
    override fun onDown(e: MotionEvent): Boolean {
      // Down 事件时重置状态
      scrollState = STATE_INIT
      isLongPressed = false
      downX = e.x
      downY = e.y
      // 返回 true 表示我们要消费这个 Down 事件，以便接收后续事件
      return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
      gestureListener?.onClick()
      return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
      gestureListener?.onDoubleClick()
      return true
    }

    override fun onScroll(
      e1: MotionEvent?,
      e2: MotionEvent,
      distanceX: Float,
      distanceY: Float,
    ): Boolean {
      if (scrollState == STATE_INIT && e1 != null) {
        val isVerticalScroll = abs(distanceY) > abs(distanceX)
        scrollState = if (detectLeftScroll && isVerticalScroll && e1.x < width / 3) {
          STATE_LEFT_SCROLL
        } else if (detectRightScroll && isVerticalScroll && e1.x > width * 2 / 3) {
          STATE_RIGHT_SCROLL
        } else {
          STATE_OTHER_SCROLL
        }
      }

      when (scrollState) {
        STATE_LEFT_SCROLL -> {
          gestureListener?.onLeftVerticalScroll(distanceX, distanceY)
        }
        STATE_RIGHT_SCROLL -> {
          gestureListener?.onRightVerticalScroll(distanceX, distanceY)
        }
        else -> {
          gestureListener?.onHorizontalScroll(distanceX, distanceY)
        }
      }
      return true
    }

    override fun onLongPress(e: MotionEvent) {
      isLongPressed = true
      gestureListener?.onLongPressed(e.x, e.y)
    }

    override fun onFling(
      e1: MotionEvent?,
      e2: MotionEvent,
      velocityX: Float,
      velocityY: Float,
    ): Boolean {
      // 只有在没有长按的情况下才认为是 Fling
      // 如果用户长按后抬起，通常不应该触发 Fling
      if (!isLongPressed) {
        gestureListener?.onFling(velocityX, velocityY)
      }
      return true
    }
  })

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(ev: MotionEvent): Boolean {
    // 将事件传递给 GestureDetector
    val handled = gestureDetector.onTouchEvent(ev)
    val action = ev.actionMasked
    if (isLongPressed && action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      isLongPressed = false
      gestureListener?.onLongPressedEnd()
    }
    return handled
  }

  /**
   * 设置手势监听器
   */
  fun setOnGestureListener(listener: OnGestureListener) {
    this.gestureListener = listener
  }

  /**
   * 手势回调接口
   */
  interface OnGestureListener {
    fun onClick()
    fun onDoubleClick()
    fun onLongPressed(x: Float, y: Float)
    fun onLongPressedEnd()
    fun onHorizontalScroll(deltaX: Float, deltaY: Float)
    fun onFling(velocityX: Float, velocityY: Float)
    fun onLeftVerticalScroll(deltaX: Float, deltaY: Float)
    fun onRightVerticalScroll(deltaX: Float, deltaY: Float)
  }
}
