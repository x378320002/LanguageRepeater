package com.language.repeater.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/** 字幕目录里的一个 srt/vtt 文件。 */
data class SubtitleFile(
  val uri: Uri,
  val name: String,
  /** 相对字幕根目录的路径，如 "English/lesson01.srt"。 */
  val relativePath: String
) {
  /** 去掉扩展名后的文件名，用于和视频名匹配。 */
  val baseName: String
    get() = name.substringBeforeLast(".")

  /** 不含点的扩展名，如 srt、vtt。 */
  val extension: String
    get() = name.substringAfterLast(".", "").lowercase()

  /** 相对字幕根目录的父目录；根目录直属文件为空串。 */
  val relativeDirectory: String
    get() = relativePath.substringBeforeLast('/', "")
}

/** 递归扫描结果；[truncated] 表示目录树过大，只返回了部分结果。 */
data class SubtitleScanResult(
  val files: List<SubtitleFile>,
  val truncated: Boolean
)

/**
 * 递归扫描字幕目录，按扩展名筛选 srt/vtt，不依赖系统 MIME 映射。
 *
 * 用户设置 Download 这类大目录后，字幕往往散落在多级子目录里，因此需要递归。
 * 为避免超大目录树长时间占用 IO，扫描设有目录数、条目数和结果数上限，
 * 达到上限时提前结束并通过 [SubtitleScanResult.truncated] 告知调用方。
 *
 * 子目录通过 DocumentsContract 按批查询（display name + mime type 一次取回），
 * 不对每个文件单独调用 DocumentFile 属性，避免大目录下逐项查询导致扫描过慢。
 */
object SubtitleFolderScanner {
  private const val MAX_DIRECTORIES = 512
  private const val MAX_FILES = 4000
  private const val MAX_ENTRIES = 20000

  private val CHILD_PROJECTION = arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE
  )

  fun isSubtitleName(name: String): Boolean {
    return name.endsWith(".srt", ignoreCase = true) ||
      name.endsWith(".vtt", ignoreCase = true)
  }

  /**
   * 递归列出字幕目录下的 srt/vtt 文件。
   *
   * 目录为空返回空结果；目录未授权、已删除或读取失败返回 null，调用方据此提示重新选择目录。
   */
  fun scan(context: Context, folderUri: Uri?): SubtitleScanResult? {
    if (folderUri == null) {
      return null
    }
    val root = try {
      DocumentFile.fromTreeUri(context, folderUri)
    } catch (_: Exception) {
      null
    } ?: return null

    val usable = try {
      root.isDirectory && root.canRead()
    } catch (_: Exception) {
      false
    }
    if (!usable) {
      return null
    }

    val rootDocumentId = try {
      DocumentsContract.getTreeDocumentId(folderUri)
    } catch (_: Exception) {
      return null
    }
    val resolver = context.contentResolver
    val files = mutableListOf<SubtitleFile>()
    val pending = ArrayDeque<DirectoryNode>()
    val visited = HashSet<String>()
    pending.addLast(DirectoryNode(documentId = rootDocumentId, relativePath = ""))
    var visitedDirectories = 0
    var examinedEntries = 0
    var truncated = false

    while (pending.isNotEmpty()) {
      if (visitedDirectories >= MAX_DIRECTORIES || examinedEntries >= MAX_ENTRIES) {
        truncated = true
        break
      }

      val node = pending.removeFirst()
      if (!visited.add(node.documentId)) {
        continue
      }
      visitedDirectories += 1

      val childrenUri = try {
        DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, node.documentId)
      } catch (_: Exception) {
        continue
      }
      val cursor = try {
        resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)
      } catch (_: Exception) {
        null
      } ?: continue

      cursor.use { childCursor ->
        while (childCursor.moveToNext()) {
          examinedEntries += 1
          if (examinedEntries > MAX_ENTRIES) {
            truncated = true
            break
          }

          val documentId = childCursor.getString(0) ?: continue
          val name = childCursor.getString(1) ?: continue
          // 跳过隐藏目录和隐藏文件，避免缩略图缓存等无关内容拖慢扫描
          if (name.startsWith(".")) {
            continue
          }
          val relativePath = if (node.relativePath.isEmpty()) {
            name
          } else {
            "${node.relativePath}/$name"
          }
          val isDirectory =
            childCursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR

          if (isDirectory) {
            pending.addLast(DirectoryNode(documentId, relativePath))
            continue
          }

          if (!isSubtitleName(name)) {
            continue
          }
          val uri = try {
            DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
          } catch (_: Exception) {
            continue
          }
          files += SubtitleFile(uri, name, relativePath)
          if (files.size >= MAX_FILES) {
            truncated = true
            break
          }
        }
      }

      if (truncated) {
        break
      }
    }

    return SubtitleScanResult(files = files, truncated = truncated)
  }

  private data class DirectoryNode(
    val documentId: String,
    val relativePath: String
  )
}
