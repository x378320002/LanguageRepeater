# 播放架构技术方案

## 目标

建立全局唯一、平台无关、只加载当前单媒体的 `PlaybackPlayer`。大型待播放列表独立为 `PlaybackList`，由 Room 持久化，禁止重复，使用稳定的 `mediaId` 作为唯一标识。

```text
Compose
 ├─ PlaybackPlayer
 ├─ PlaybackListController
 ├─ SentencePlaybackController
 └─ 页面级 ViewModel

PlaybackListController
 ├─ PlaybackListRepository
 ├─ DataStore currentMediaId
 └─ PlaybackPlayer

PlaybackService
 └─ Media3PlaybackPlayer
      └─ 单个 Media3 MediaItem
```

## 核心原则

- `PlaybackPlayer` 是全局唯一播放器接口实例和播放状态唯一发出者。
- 底层播放器始终只加载零个或一个媒体，不保存 PlaybackList。
- PlaybackList 是待播放列表，Room 是唯一数据源。
- PlaybackList 不允许重复，`mediaId` 是列表主键及所有列表操作的标识。
- 媒体 URI 只负责定位资源，不作为业务身份。
- 播放时间属于 `PlaybackPlayer`；当前列表媒体、相邻媒体和列表模式属于 `PlaybackListController`。
- Compose 直接观察 Player 和 Controller，不通过 ViewModel 复制播放状态。
- Compose 和业务层不获取 Media3、腾讯 SDK 或 AVPlayer 实例。
- 只有平台播放宿主创建和释放底层播放器；页面销毁只解绑视频输出。
- Player 不负责 Room、PlaybackList、字幕匹配、PCM、句子、AB 复读或历史。

## PlaybackPlayer

```kotlin
interface PlaybackPlayer {
  val state: StateFlow<PlaybackPlayerState>
  val events: SharedFlow<PlaybackPlayerEvent>

  fun prepare(
    media: PlaybackMedia,
    startPositionMs: Long = 0L,
    playWhenReady: Boolean = false,
  )

  fun play()
  fun pause()
  fun seekTo(positionMs: Long)
  fun setSpeed(speed: Float)
  fun setSubtitle(subtitleUri: String?)
  fun clear()
}
```

`PlaybackPlayerState` 只描述已确认的播放事实：

- `status`
- `currentMedia`
- `isPlaying`
- `positionMs`
- `durationMs`
- `speed`
- `error`

不包含缓冲位置、PlaybackList、列表索引或底层 SDK 状态码。

`PlaybackPlayerEvent` 用于一次性事件：

- `MediaChanged`
- `MediaEnded`
- `Failed`

`MediaEnded` 必须按媒体和播放代次去重，避免重复切换下一项。

## PlaybackMedia

```kotlin
data class PlaybackMedia(
  val mediaId: String,
  val uri: String,
  val title: String,
  val subtitleUri: String? = null,
  val mimeType: String? = null,
  val headers: Map<String, String> = emptyMap(),
)
```

- `mediaId` 是稳定业务身份，用于关联列表、历史、播放位置、字幕和句子。
- `uri` 是可变化或失效的资源位置，例如 Android `content://`、网络 URL 或 iOS 文件引用。
- 不允许根据 URI hash 临时生成 `mediaId`。

## 全局实例和生命周期

`MainApp` 持有唯一 `PlaybackGraph`。Graph 持有：

- `PlaybackPlayer`
- `PlaybackListRepository`
- `PlaybackListController`
- `SentencePlaybackController`
- `SubtitleManager`
- `PlaybackHistoryRepository`
- Android 视频输出绑定器
- Service 连接器

Android 中只有 `PlaybackService` 创建和释放 ExoPlayer。Service 创建后将实例 attach 到 `Media3PlaybackPlayer`，销毁前 detach。ViewModel 和 Composable 不创建、不释放播放器实现。

## PlaybackList

PlaybackList 是业务层的大型待播放列表，不是 SDK playlist。首版使用 Room `Flow<List<PlaybackListItem>>` 和 Compose `LazyColumn`。

```kotlin
data class PlaybackListItem(
  val mediaId: String,
  val title: String,
  val uri: String,
  val subtitleUri: String?,
  val savedPositionMs: Long,
  val sortOrder: Long,
)
```

数据库关系表：

```kotlin
@Entity(tableName = "playback_list_table")
data class PlaybackListEntity(
  @PrimaryKey val mediaId: String,
  val sortOrder: Long,
)
```

规则：

- `mediaId` 主键从数据库层禁止重复。
- Repository 的 replace/add 输入按 `mediaId` 去重，首次出现优先。
- `sortOrder` 使用稀疏 `Long`，初始间隔 1024。
- 选择、删除和移动公开 API 全部使用 `mediaId`，不使用易失效的 index。
- Room 是列表唯一事实源，禁止从播放器 SDK 反向提取列表。

## PlaybackListController

Controller 负责把 PlaybackList 与单媒体 Player 协调起来：

- 恢复上次当前媒体。
- 选择指定 `mediaId`。
- 上一项、下一项和播放模式。
- 添加、替换、删除和移动列表项。
- 切换前保存旧媒体位置。
- 读取目标媒体保存位置并只 prepare 当前一个媒体。
- 播放结束后决定下一项。
- 使用串行控制和 generation 防止快速切换竞态。

当前媒体删除规则：

1. 优先选择删除项之后的媒体。
2. 没有下一项时选择上一项。
3. 保持删除前的播放或暂停状态。
4. 列表为空时清除 Player、当前会话和当前学习状态。
5. 删除列表关系不删除媒体信息、历史、字幕或保存位置。

播放结束规则：

- 单项循环：当前位置归零并继续当前媒体。
- 顺序播放：选择下一项；末尾停留在 Ended。
- 列表循环：末尾选择第一项。
- 播放完成媒体的位置重置为零。
- 不使用底层播放器的多媒体 repeat/list 能力。

## 位置保存和恢复

位置按 `mediaId` 保存，不属于 Player 的持久化职责。

保存时机：

- 暂停。
- seek 完成。
- 切换媒体前。
- 播放结束或错误。
- Service detach。
- 播放期间低频保存。

当前媒体 ID 使用 DataStore 保存。恢复优先级：

1. DataStore 当前媒体。
2. PlaybackList 第一项。

无效 URI 从列表移除后继续寻找下一项。播放器已有当前媒体时不得被异步恢复覆盖。

## 字幕、PCM、句子、AB 和历史

- `SubtitleManager` 负责字幕匹配、持久化和当前单媒体字幕更新；不得遍历或重建 PlaybackList。
- `MediaAnalysisRepository` 负责 PCM/WAV 缓存和波形，切换媒体时取消旧任务并校验 `mediaId`。
- `SentenceRepository` 负责字幕/VAD 分句和句子编辑、保存，不操作播放器。
- `SentencePlaybackController` 观察 Player 的媒体和 position，负责当前句、上一句、下一句、回句首和 AB 回跳。
- `PlaybackHistoryRepository` 按 `mediaId` 管理历史。

## Compose

Compose 直接观察全局对象：

- `PlaybackPlayer.state`
- `PlaybackListController.state`
- `SentencePlaybackController.state`
- `PlaybackHistoryRepository` 的 Flow

页面 ViewModel 只保留文件导入、SAF 权限、编辑模式和页面事件。

视频区域通过 Android 专用视频输出绑定器连接 `PlayerView`。业务 Composable 和 ViewModel 不接收 Media3 `Player`；Composable 销毁时只解绑画面。

PlaybackList UI 使用 `mediaId` 作为 LazyColumn key，所有操作回调传 `mediaId`。

## Media3 边界

Media3 类型只允许存在于：

- `player/media3/`
- `PlaybackService`
- MediaSession/通知等 Android 平台基础设施

业务模型、Repository、Controller、句子、历史和 Compose 页面不得 import Media3。

Media3 实现只允许使用单媒体 `setMediaItem()`，禁止业务代码使用：

- `setMediaItems`
- `addMediaItem(s)`
- `moveMediaItem`
- `removeMediaItem`
- `currentMediaItemIndex`
- `PlaceHoldItem`

## 数据迁移

数据库由 version 3 升至 version 4：

1. 创建新的 PlaybackList 表，以 `mediaId` 为主键。
2. 从旧 `current_list_table` 按原顺序迁移并去重。
3. 将旧 `videoId` 映射为 `mediaId`。
4. 转换为稀疏 `Long sortOrder`。
5. 当前媒体 ID 继续由 DataStore 保存。
6. 不使用 destructive migration。

## 完成标准

- 全局只有一个 `PlaybackPlayer` 实例。
- Player 是播放状态唯一发出者。
- 底层播放器最多持有一个媒体。
- PlaybackList 由 Room 唯一管理并按 `mediaId` 禁止重复。
- 列表操作不依赖 Media3 index。
- 播放结束由 Controller 选择下一项。
- 字幕只更新当前单媒体。
- PCM、句子、AB、历史均不属于 Player 实现。
- Compose 不取得底层 SDK Player。
- 删除 `PlaybackCore`、旧 `PlayerEngine`、旧 PlaylistManager、PlaceHold 逻辑和旧 View 体系。
