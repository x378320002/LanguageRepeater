package com.language.repeater.widgets

import android.R.attr.action
import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 全局响应手势的View, 不影响拦截内部子View的点击,
 * 内部子View如果要滑动, 可以调用requestDisallowInterceptTouchEvent来禁用本View的手势
 * 默认本View会响应各种手势
 */
class GestureCardView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
) : CardView(context, attrs, defStyleAttr) {

  companion object {
    const val TAG = "GestureCardView"
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
  private var downTime = 0L
  private var downEventTime = 0L

  private val scope = CoroutineScope(Dispatchers.Main)

  // 标记当前是否已经触发了长按
  private var isLongPressed = false
  private var longPressedJob: Job? = null
  private val longDuration = 500L

  // 0:未识别, 1:左侧1/3上下滑, 2:右侧1/3上下滑, 3, 其他
  private var scrollState = 0

  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

  private var isIntercept = false

  // 用于检测点击、双击、Fling
  private val gestureDetector: GestureDetector =
    GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
      override fun onDown(e: MotionEvent): Boolean {
        Log.i(TAG, "onDown e:$e")
        // Down 事件时重置状态
        scrollState = STATE_INIT
        downX = e.x
        downY = e.y
        // 返回 true 表示我们要消费这个 Down 事件，以便接收后续事件
        return true
      }

      override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (isLongPressed) return true
        Log.i(TAG, "onSingleTapConfirmed")
        cancelLongClick()
        gestureListener?.onClick()
        return true
      }

      override fun onDoubleTap(e: MotionEvent): Boolean {
        if (isLongPressed) return true
        Log.i(TAG, "onDoubleTap")
        cancelLongClick()
        gestureListener?.onDoubleClick()
        return true
      }

      override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float,
      ): Boolean {
        if (isLongPressed) return false
        cancelLongClick()

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
    }).apply {
      setIsLongpressEnabled(false)
    }

  override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
    cancelLongClick()
    super.requestDisallowInterceptTouchEvent(disallowIntercept)
  }

  private fun beginLongPressed() {
    longPressedJob?.cancel()
    longPressedJob = scope.launch {
      delay(longDuration)
      Log.i(TAG, "onLongPress: longDuration:$longDuration")
      isLongPressed = true
      longPressedJob = null
      gestureListener?.onLongPressed()
    }
  }

  private fun cancelLongClick() {
    if (longPressedJob != null) {
      Log.i(TAG, "cancelLongClick")
      longPressedJob?.cancel()
      longPressedJob = null
    }
  }

  override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    val action = ev.actionMasked
    if (action == MotionEvent.ACTION_DOWN) {
      downX = ev.x
      downY = ev.y
      downTime = ev.downTime
      downEventTime = ev.eventTime
      isIntercept = false
      isLongPressed = false
      beginLongPressed()
    }

    val result = super.dispatchTouchEvent(ev)

    if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
      cancelLongClick()
      if (isLongPressed) {
        isLongPressed = false
        gestureListener?.onLongPressedEnd()
      }
    }
    return result
  }

  override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
    if (!isLongPressed && ev.actionMasked == MotionEvent.ACTION_MOVE) {
      val dx = ev.x - downX
      val dy = ev.y - downY
      if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
        val time = SystemClock.uptimeMillis()
        val fake = MotionEvent.obtain(
          time - 1,
          time - 1,
          MotionEvent.ACTION_DOWN,
          downX,
          downY,
          0
        )
        gestureDetector.onTouchEvent(fake)
        fake.recycle()
        isIntercept = true
      }
    }
    //Log.i(TAG, "intercept: ${isIntercept || isLongPressed}")
    return isIntercept || isLongPressed
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(ev: MotionEvent): Boolean {
    if (ev.actionMasked == MotionEvent.ACTION_UP) {
      Log.i(TAG, "onTouchEvent ACTION_UP")
    }
    val handled = gestureDetector.onTouchEvent(ev)
    //注意: 这里的MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
    // onInterceptTouchEvent返回true, 不能保证onTouchEvent的调用, 所以不能依赖onInterceptTouchEvent和onTouchEvent
    // 来协作处理ACTION_UP和ACTION_CANCEL时取消长按/onLongPressedEnd的逻辑
    // 为了保证一定调用onLongPressedEnd, 需要在dispatchTouchEvent中处理
    // (因为只有在onInterceptTouchEvent true时, 当前事件需要先取消targetView, 后续的事件再分发给本类, 但是当前已经是最后一个事件了)

    //MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
    //    cancelLongClick()
    //    if (isLongPressed) {
    //      isLongPressed = false
    //      gestureListener?.onLongPressedEnd()
    //    }
    //}
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
    fun onLongPressed()
    fun onLongPressedEnd()
    fun onHorizontalScroll(deltaX: Float, deltaY: Float)
    fun onFling(velocityX: Float, velocityY: Float)
    fun onLeftVerticalScroll(deltaX: Float, deltaY: Float)
    fun onRightVerticalScroll(deltaX: Float, deltaY: Float)
  }
}
