package com.language.repeater.playvideo.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.language.repeater.R

/**
 * 编辑栏 — 对应旧 XML 的 edit_layout (FlexboxLayout)
 * 5 个编辑操作按钮：合并前句、合并后句、分割句子、删除句子、插入句子
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
      .fillMaxWidth()
      .padding(horizontal = 15.dp, vertical = 6.dp)
      .background(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
      )
      .padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    EditActionButton(
      iconRes = R.drawable.ic_edit_merge_left,
      textRes = R.string.merge_pre,
      onClick = onMergePre,
    )

    EditActionButton(
      iconRes = R.drawable.ic_edit_merge_right,
      textRes = R.string.merge_next,
      onClick = onMergeNext,
    )

    EditActionButton(
      iconRes = R.drawable.ic_edit_split,
      textRes = R.string.split_sentence,
      onClick = onSplitSentence,
    )

    EditActionButton(
      iconRes = R.drawable.ic_edit_delete,
      textRes = R.string.delete_sentence,
      onClick = onDeleteSentence,
    )

    EditActionButton(
      iconRes = R.drawable.ic_edit_insert,
      textRes = R.string.insert_sentence,
      onClick = onInsertSentence,
    )
  }
}

@Composable
private fun EditActionButton(
  iconRes: Int,
  textRes: Int,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .width(44.dp)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
      ),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(
      painter = painterResource(iconRes),
      contentDescription = null,
      modifier = Modifier.size(20.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text = stringResource(textRes),
      fontSize = 10.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      maxLines = 2,
    )
  }
}
