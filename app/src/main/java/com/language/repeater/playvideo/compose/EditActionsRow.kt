package com.language.repeater.playvideo.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.ExperimentalFoundationStyleApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.language.repeater.R

/**
 * 五个编辑操作等宽排列，图标与单行标签居中显示。
 * 操作包括合并前句、合并后句、分割句子、删除句子和插入句子。
 */
@Composable
fun EditActionsRow(
  onMergePre: () -> Unit,
  onMergeNext: () -> Unit,
  onSplitSentence: () -> Unit,
  onDeleteSentence: () -> Unit,
  onInsertSentence: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .padding(horizontal = 15.dp)
      .fillMaxWidth()
      .height(60.dp),
    horizontalArrangement = Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EditActionButton(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
      iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_bear_left_2_outline,
      textRes = R.string.merge_pre,
      onClick = onMergePre,
    )

    EditActionButton(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
      iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_bear_right_2_outline,
      textRes = R.string.merge_next,
      onClick = onMergeNext,
    )

    EditActionButton(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
      iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_cut_outline,
      textRes = R.string.split_sentence,
      onClick = onSplitSentence,
    )

    EditActionButton(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
      iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline,
      textRes = R.string.delete_sentence,
      onClick = onDeleteSentence,
    )

    EditActionButton(
      modifier = Modifier
        .weight(1f)
        .fillMaxHeight(),
      iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_row_insert_top_outline,
      textRes = R.string.insert_sentence,
      onClick = onInsertSentence,
    )
  }
}

@OptIn(ExperimentalFoundationStyleApi::class)
@Composable
private fun EditActionButton(
  iconRes: Int,
  textRes: Int,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val color = if (isPressed) {
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }

  CompositionLocalProvider(
    LocalContentColor provides color
  ) {
    Column(
      modifier = modifier
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          onClick = onClick,
        ),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(20.dp)
      )
      Text(
        text = stringResource(textRes),
        fontSize = 9.sp,
        lineHeight = 14.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}
