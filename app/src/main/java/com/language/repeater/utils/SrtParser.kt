package com.language.repeater.utils

import kotlin.collections.isNotEmpty
import kotlin.collections.lastIndex

import android.content.Context
import android.net.Uri
import android.util.Log
import com.language.repeater.playvideo.model.SubtitleItem
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.regex.Pattern

object SrtParser {
  private const val TAG = "SrtParser"
  // 正则表达式匹配时间轴: 00:00:20,000 --> 00:00:24,400
  private val TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{1,2}):(\\d{1,2})[,\\.](\\d{3})")

  // 正则表达式匹配 HTML 标签 (用于清洗字幕内容)
  // 匹配如 <font color="...">, </font>, <b>, </i> 等
  private val HTML_TAG_PATTERN = Pattern.compile("<[^>]+>")

  /**
   * 新增：直接通过 Uri 解析字幕
   * 自动处理流的打开和关闭
   */
  fun parse(context: Context, uri: Uri): List<SubtitleItem> {
    return try {
      context.contentResolver.openInputStream(uri)?.use { inputStream ->
        parse(inputStream)
      } ?: emptyList()
    } catch (e: Exception) {
      e.printStackTrace()
      emptyList()
    }
  }

  /**
   * 原始流解析逻辑 (核心实现)
   */
  fun parse(inputStream: InputStream): List<SubtitleItem> {
    val result = ArrayList<SubtitleItem>()
    val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))

    var line: String?
    var index = 0
    var startTime = 0L
    var endTime = 0L
    val contentBuilder = StringBuilder()

    var state = 0

    while (reader.readLine().also { line = it } != null) {
      val text = line!!.trim()

      if (text.isEmpty()) {
        if (state == 2 && contentBuilder.isNotEmpty()) {
          // 【修改点】在保存前清洗文本
          val rawText = contentBuilder.toString().trim()
          val cleanText = stripHtmlTags(rawText)
          // 使用合并逻辑添加
          addOrMerge(result, SubtitleItem(index, startTime, endTime, cleanText))
          contentBuilder.clear()
          state = 0
        }
        continue
      }

      when (state) {
        0 -> {
          if (text.all { it.isDigit() }) {
            index = text.toIntOrNull() ?: (result.size + 1)
            state = 1
          } else if (text.contains("-->")) {
            parseTime(text)?.let { (start, end) ->
              startTime = start
              endTime = end
              state = 2
            }
          }
        }
        1 -> {
          if (text.contains("-->")) {
            parseTime(text)?.let { (start, end) ->
              startTime = start
              endTime = end
              state = 2
            }
          }
        }
        2 -> {
          if (contentBuilder.isNotEmpty()) {
            contentBuilder.append("\n")
          }
          contentBuilder.append(text)
        }
      }
    }

    if (state == 2 && contentBuilder.isNotEmpty()) {
      val rawText = contentBuilder.toString().trim()
      val cleanText = stripHtmlTags(rawText)
      // 使用合并逻辑添加
      addOrMerge(result, SubtitleItem(index, startTime, endTime, cleanText))
    }

    reader.close()

    for (i in result.indices) {
      val r = result[i]
      Log.d(TAG, "sentence $i, begin:${r.startTime}, end:${r.endTime}, content:${r.content}")
    }

    return result
  }

  /**
   * 辅助方法：添加条目到列表，并处理重复项合并
   * 如果当前句内容与上一句相同，且时间连续，则合并它们
   */
  private fun addOrMerge(list: MutableList<SubtitleItem>, newItem: SubtitleItem) {
    if (newItem.endTime - newItem.startTime < 500L) {
      return
    }
    if (list.isNotEmpty()) {
      val lastItem = list.last()
      if (newItem.startTime - lastItem.endTime <= 50) {
        if (lastItem.content == newItem.content || lastItem.content.contains(newItem.content)) {
          lastItem.endTime = maxOf(lastItem.endTime, newItem.endTime)
          return
        }
        if (newItem.content.contains(lastItem.content)) {
          lastItem.content = newItem.content
          lastItem.endTime = maxOf(lastItem.endTime, newItem.endTime)
          return
        }
      }
    }
    // 如果不满足合并条件，直接添加
    list.add(newItem)
  }

  /**
   * 辅助方法：去除字符串中的 HTML 标签
   * 输入: "One <font color="#ffff00">day</font> Rex..."
   * 输出: "One day Rex..."
   */
  private fun stripHtmlTags(htmlText: String): String {
    val matcher = HTML_TAG_PATTERN.matcher(htmlText)
    return matcher.replaceAll("") // 替换为空字符串
  }

  private fun parseTime(line: String): Pair<Long, Long>? {
    try {
      val parts = line.split("-->")
      if (parts.size != 2) return null

      val start = parseTimestamp(parts[0].trim())
      val end = parseTimestamp(parts[1].trim())

      return Pair(start, end)
    } catch (e: Exception) {
      return null
    }
  }

  private fun parseTimestamp(timestamp: String): Long {
    val matcher = TIME_PATTERN.matcher(timestamp)
    if (matcher.find()) {
      val h = matcher.group(1)?.toLong() ?: 0
      val m = matcher.group(2)?.toLong() ?: 0
      val s = matcher.group(3)?.toLong() ?: 0
      val ms = matcher.group(4)?.toLong() ?: 0
      return (h * 3600000) + (m * 60000) + (s * 1000) + ms
    }
    return 0L
  }
}