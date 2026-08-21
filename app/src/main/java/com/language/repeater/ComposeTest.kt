package com.language.repeater

import android.R.attr.name
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * @author wangzixu
 * Created on 2026-08-21
 */
private const val TAG = "PointerDemo"
@Composable
fun PointerEventDemo() {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.LightGray)

      // A
      .pointerInput(Unit) {
        logPointer("A", PointerEventPass.Main)
      }

      // B
      .pointerInput(Unit) {
        logPointer("B", PointerEventPass.Main)
      }
  ) {

    Box(
      modifier = Modifier
        .size(250.dp)
        .background(Color.White)

        // C
        .pointerInput(Unit) {
          logPointer("C", PointerEventPass.Main)
        }

        // D
        .pointerInput(Unit) {
          logPointer("D", PointerEventPass.Main)
        }
    )
  }
}


suspend fun PointerInputScope.logPointer(
  name: String,
  pass: PointerEventPass
) {

  awaitPointerEventScope {
    while (true) {

      val initial = awaitPointerEvent(
        PointerEventPass.Initial
      )

      Log.d(
        TAG,
        "Initial $name consumed = ${
          initial.changes.firstOrNull()?.isConsumed
        }"
      )

      initial.changes.forEach {
        it.consume()
      }

      val main = awaitPointerEvent(
        PointerEventPass.Main
      )

      Log.d(
        TAG,
        "Main $name consumed = ${
          main.changes.firstOrNull()?.isConsumed
        }"
      )

      val final = awaitPointerEvent(
        PointerEventPass.Final
      )

      Log.d(
        TAG,
        "Final $name consumed = ${
          final.changes.firstOrNull()?.isConsumed
        }"
      )
    }
  }
}