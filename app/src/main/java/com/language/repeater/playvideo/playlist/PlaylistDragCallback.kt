package com.language.repeater.playvideo.playlist

import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 播放列表拖拽排序的 ItemTouchHelper.Callback
 * 拖动开始记录起始位置，过程中 notifyItemMoved 实时跟手同时维护 dragList，
 * 松手后 submitList 修正 currentList，并用起止位置调一次 changeItemPositionSilent
 *
 * ItemTouchHelper.Callback和ListAdapter适配有问题
 * ListAdapter内的list无法外接更改，导致只能用submitList进行更新，
 * 如果每次submit，异步更新有可能和ui动画冲突不说，拖动到边界时也会明显不跟手，难用，
 * 如果最后submit，会导致界面错乱。
 * 每次最后更新都会有bug。后期可以改成原始adapter+自己的diffUtil，onMove时实时更新adapter数据list就好
 * 目前不更新最终数据，关闭再打开就好了。
 */
class PlaylistDragCallback(
  private val adapter: PlaylistAdapter,
  private val fragment: PlaylistSheetFragment
) : ItemTouchHelper.Callback() {

  companion object {
    private const val TAG = "PlaylistDragCallback"
  }

  private var dragFromPosition: Int = RecyclerView.NO_POSITION
  private var dragToPosition: Int = RecyclerView.NO_POSITION

  override fun getMovementFlags(
    recyclerView: RecyclerView,
    viewHolder: RecyclerView.ViewHolder
  ): Int {
    val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
    val swipeFlags = 0
    return makeMovementFlags(dragFlags, swipeFlags)
  }

  override fun onMove(
    recyclerView: RecyclerView,
    viewHolder: RecyclerView.ViewHolder,
    target: RecyclerView.ViewHolder
  ): Boolean {
    val fromPosition = viewHolder.bindingAdapterPosition
    val toPosition = target.bindingAdapterPosition
    if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION || toPosition == fromPosition) {
      return false
    }
    if (fromPosition >= adapter.itemCount || toPosition >= adapter.itemCount) {
      return false
    }
    dragToPosition = toPosition
    adapter.notifyItemMoved(fromPosition, toPosition)
    return true
  }

  override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    // 不支持侧滑，不做任何事情
  }

  override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
    super.onSelectedChanged(viewHolder, actionState)
    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
      dragFromPosition = viewHolder.bindingAdapterPosition
      dragToPosition = dragFromPosition
      viewHolder.itemView.performHapticFeedback(
        android.view.HapticFeedbackConstants.LONG_PRESS,
        android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
      )
    }
  }

  override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
    super.clearView(recyclerView, viewHolder)
    Log.i(TAG, "Drag end, from:$dragFromPosition, to:$dragToPosition")
    if (dragFromPosition != RecyclerView.NO_POSITION
      && dragFromPosition != dragToPosition
    ) {
      fragment.viewModel.moveItem(dragFromPosition, dragToPosition)

      //val list = adapter.currentList.toMutableList()
      //val r = list.removeAt(index = dragFromPosition)
      //list.add(dragToPosition, r)
      //adapter.submitList(list)
    }
    dragFromPosition = RecyclerView.NO_POSITION
    dragToPosition = RecyclerView.NO_POSITION
  }

  override fun isLongPressDragEnabled(): Boolean {
    return true
  }
}
