# PlayVideoFragment 迁移到 Compose 的分阶段方案

## 落地方式（用户明确要求）

- **方案文档落到项目根目录**：`/Users/wangzixu/AndroidStudioProjects/LanguageRepeater/COMPOSE_MIGRATION_PLAN.md`（本文档的副本，方便日常查阅）。
- **代码交付方式 = 骨架 + 大量教学注释**：本次目的是**教会用户 Compose**，不是替他写完。因此我只会：
  - 完成"阶段 0"这种机械操作（重命名、Fragment 薄壳）——因为跟 Setting 页 Fragment 几乎是同款翻版，全写。
  - 从"阶段 1"起，每个 Composable 文件我搭好：`@Composable` 函数签名 + 参数 + `Modifier` 位、必要的 import、少量骨架结构（比如 `Column { ... }`），**具体内部实现留一份编号 TODO 列表 + 详细的中文教学注释**，用户对着注释一步步填。
  - 教学注释包括：这个 API 是什么、为什么用它、常见坑、可用文档参考、Setting 页里对应的样例位置（如果有）。
  - **不代替用户完成阶段 1 的填充**。等你填完一部分给我看，我再进入阶段 2 或帮你 review。

## Context（背景与目标）

工程正在从 Android View 体系逐步迁移到 Jetpack Compose，Setting 页已完成（commit `6771db2`）。现在要迁移的是**播放视频页**，是工程里最重的页面：

- **播放主页**：`PlayVideoFragment`（103 行薄壳）+ `PlayerViewModel`（246 行）+ 6 个 Component（`playvideo/components/`，主控件 `PlayUIActComponent` 达 536 行）
- **两套布局**：竖屏 `video_play_fragment.xml`（568 行）、横屏 `layout-land/video_play_fragment.xml`（475 行，右侧滑入面板）
- **两个复杂自定义 View**：`ScrollWaveformView`（882 行 Canvas + 手势 + AB 拖动）、`GestureCardView`（194 行手势容器）
- **3 个 BottomSheet**：`PlaylistSheetFragment` / `HistorySheetFragment` / `SleepTimerSheetFragment`
- **播放器**：Media3 `PlayerView`，状态透传自 `PlaybackCore` 单例（1039 行 Repository）

用户明确表示：**最终目标是全 Compose，但因为不熟 Compose，需要一个循序渐进、每阶段都能跑的迁移过程**。因此本方案把工作切成 8 个阶段，从"AndroidView 包裹 + Compose 骨架"起步，逐步用 Compose 替换掉每个子模块，最难的 Canvas 波形放到最后。

---

## 既定风格（参考 Setting 页已定型的规约）

所有阶段都遵循 Setting 页奠定的模式：

1. **Compose-in-Fragment**：`MainActivity` 完全不动，`NavGraph` 里只把 `fragment<PlayVideoFragment, ...>` 换成 `fragment<PlayVideoFragmentCompose, ...>`。新的 Fragment 在 `onCreateView` 里返回 `ComposeView.apply { setContent { ... } }`。**不引入 `navigation-compose`**，返回按钮/跳转设置页依然走 `findNavController()`。
2. **三层拆分**：`PlayVideoFragmentCompose`（薄壳，持有 Activity API、导航、Fragment 生命周期挂钩）+ `PlayVideoScreen`（无状态 Composable，State Hoisting）+ `PlayerViewModel`（`StateFlow` + Actions）。
3. **主题**：`MaterialTheme(colorScheme = if (nightMode) darkColorScheme() else lightColorScheme())` 就地包裹，暂不抽全局 Theme 文件（与 Setting 一致）。
4. **状态**：`PlaybackCore` 的 `StateFlow` 经 `PlayerViewModel` 透传，Composable 内 `collectAsState()` 消费；对话框/菜单开合用 `remember { mutableStateOf(false) }`。
5. **旧 View 版本保留**：旧 `PlayVideoFragment.kt` **保持原名不动**（因为 `playvideo/components/` 下 6 个 Component 里用了 `BaseComponent<PlayVideoFragment>` 泛型，改名会连锁修改整个 components/ 目录），新建 `PlayVideoFragmentCompose.kt` 作为 Compose 薄壳，NavGraph 里改注册新 Fragment。阶段 8 全部迁移完成后删除旧 Fragment，届时可把 `PlayVideoFragmentCompose` 重命名回 `PlayVideoFragment`。
6. **图标**：优先 `com.composables.icons.tabler.outline`，其次 Material Icons。
7. **点击**：如需防抖，工程里没有 `DuplicatedClickFilter`，Composable 里可用 `remember { mutableStateOf(0L) }` 简单节流或直接不做（Compose 本身重组语义已避免了大部分重复触发）。

---

## 阶段划分总览

| 阶段 | 目标 | 主要学习点 | 交付后可用状态 |
|---|---|---|---|
| 0 | 备份旧实现，搭空壳 | ComposeView、Fragment 集成 | 竖屏进入是空白 Compose 页（临时） |
| 1 | 竖屏骨架，AndroidView 兜底 | AndroidView、Column/Row/Box、collectAsState、Slider | 竖屏能播放、切歌、拖 seek，功能不缺 |
| 2 | 菜单和编辑操作 Compose 化 | DropdownMenu、AnimatedVisibility、remember | 速度菜单/定时菜单/更多菜单/字幕菜单/编辑栏都是 Compose |
| 3 | 文件选择、字幕加载 | rememberLauncherForActivityResult、LaunchedEffect | 加视频 / 加字幕流程走 Compose |
| 4 | 横屏适配 | LocalConfiguration、Compose 动画（slideIn/slideOut） | 横屏也走 Compose，右侧面板动画顺滑 |
| 5 | 三个 BottomSheet 迁移 | ModalBottomSheet、LazyColumn、items+key、拖动手势 | 三个 Sheet 都是纯 Compose，去掉 `BasePlaySheetFragment` 依赖 |
| 6 | GestureCardView Compose 化 | pointerInput、detectTapGestures、awaitEachGesture | 手势不再依赖自定义 View |
| 7 | ScrollWaveformView Compose 化（最难） | Canvas、DrawScope、drawWithCache、复杂 hitTest | 波形全 Compose 绘制 |
| 8 | 清理 | —— | 删除旧 XML、旧 Component、旧自定义 View、旧 Fragment |

---

## 阶段 0：新建 Compose 薄壳（旧 Fragment 保留原名）

**改动清单**：

- **保留原名**：`app/src/main/java/com/language/repeater/playvideo/PlayVideoFragment.kt`（旧 View 版本）**不改名不动内容**，因为 `playvideo/components/` 下 6 个 Component 用了 `BaseComponent<PlayVideoFragment>` 泛型，改名会连锁修改。
- **新建**：`app/src/main/java/com/language/repeater/playvideo/PlayVideoFragmentCompose.kt`（Compose 薄壳，`onCreateView` 返回 `ComposeView`）
- **新建目录**：`app/src/main/java/com/language/repeater/playvideo/compose/`
- **新建**：`app/src/main/java/com/language/repeater/playvideo/compose/PlayVideoScreen.kt`（占位 Composable）
- **修改 NavGraph**：`app/src/main/java/com/language/repeater/MainActivity.kt` 里把 `fragment<PlayVideoFragment, PlayVideoPageKey>` 改成 `fragment<PlayVideoFragmentCompose, PlayVideoPageKey>`，并同步 import 换成新类。
- 参考模式：`app/src/main/java/com/language/repeater/setting/SettingFragment.kt` 的写法（注意 Setting 页因为没有 Component 依赖，可以直接把旧的 View 版本改名为 `SettingFragmentView`；播放页命名策略略不同）。

**注意**：
- 阶段 0 交付后进入页面是空白 Compose 页，属于**临时中间态**，紧接着进入阶段 1。
- 想要临时回滚测试旧 View 版本，把 NavGraph 里注册的类改回 `PlayVideoFragment` 即可。

---

## 阶段 1：竖屏骨架（AndroidView 兜底，功能不缺）

**目标**：竖屏可以正常播放、切歌、看波形、拖 seek、切上下句，UI 结构完全 Compose，只有"视频"、"波形"、"手势容器"三个复杂控件用 AndroidView 包起来暂不动。

**新增 Composable 拆分**（在 `playvideo/compose/` 下）：

| Composable | 文件 | 内容 |
|---|---|---|
| `PlayVideoScreen` | `PlayVideoScreen.kt` | 顶层，Scaffold + Column，接 ViewModel 状态和回调 |
| `PlayTopBar` | `PlayTopBar.kt` | `+` / 历史 / 设置 三按钮（对应 XML `top_bar`） |
| `VideoTitle` | 同 Screen 内 | 显示 `currentMediaItem.mediaMetadata.title` |
| `VideoPlayerBox` | `VideoPlayerBox.kt` | `AndroidView` 包 `GestureCardView` + 内嵌 `PlayerView`，右下角覆盖两个 IconButton（字幕、全屏）用 Compose |
| `WaveformBox` | `WaveformBox.kt` | `AndroidView` 包 `ScrollWaveformView`，通过 `update = {}` 把 `sentencesFlow`、`allWaveDataFlow`、AB 状态推给它 |
| `VideoSeekSlider` | 同 Screen 内 | **Compose M3 `Slider`**，绑 `currentPosition` / `duration` |
| `SubActionsRow` | `SubActionsRow.kt` | AB / 复读 / 速度 / 定时 / 全屏 / 更多 6 个按钮的空壳（这一阶段仅 IconButton + 点了先不做事或 TODO） |
| `PlaybackControlRow` | `PlaybackControlRow.kt` | **全 Compose**：上一句 / 回句首 / 播放暂停 / 下一句 / 播放列表 |

**ViewModel 上要暴露/补的东西**（保守增量，不动 `PlaybackCore`）：

- `duration: StateFlow<Long>`（从 `playerInstance` 派生）
- 一批 Actions：`onSeek(seconds)`、`onTogglePlayPause()`、`onSeekPrevSentence()`、`onSeekNextSentence()`、`onBackSentenceHead()`——它们内部转发到 `PlaybackCore` 已有的方法。
- 现有的 `isUiPlaying`、`currentPosition`、`currentMediaItem`、`sentencesFlow`、`allWaveDataFlow`、`playSpeedState`、`repeatable`、`curAbSentenceFlow`、`editMode` 可以直接被 Composable 消费。

**Fragment 里保留哪些非 UI 逻辑**：

- BottomSheet 弹出：`PlaylistSheetFragment().show(childFragmentManager, ...)` 等，接口以 `onOpenPlaylist / onOpenHistory / onOpenSleepTimer` 从 Screen 传上来。
- 导航到设置页：`findNavController().navigate(SettingPageKey, defaultNavOptions)`，同样以 `onOpenSetting` 传上来。
- 系统 UI（`enableEdgeToEdge`、状态栏亮暗）：目前 `BaseFragment` 或 `BaseActivity` 已处理，不动。

**保留但会瘦身的 Component**（这个阶段只让它们负责喂数据给两个 AndroidView）：

- `PlayScrollWaveComponent` → 逻辑挪成 ViewModel 里的派生 StateFlow / 在 `AndroidView.update` 里直接 apply，Component 可以清空但**先保留文件**，避免连锁修改。
- 其他 5 个 Component 阶段 1 暂不动，靠 `activityViewModels()` 拿到的 `PlayerViewModel` 依然工作。

**学习点**：`ComposeView` 集成、`AndroidView`（factory / update / release）、`collectAsState`、State Hoisting、`Column / Row / Box / Scaffold`、`Slider`、`IconButton`、`painterResource`。

---

## 阶段 2：菜单和编辑操作 Compose 化

**目标**：把所有 `PopupMenu` 换成 `DropdownMenu`，编辑栏 `FlexboxLayout` 换成 `Row + AnimatedVisibility`。

**改动清单**：

- **播放速度菜单**：`PlayUIActComponent` 里的 `PopupMenu(0.5x/0.75x/.../2.0x)` → `DropdownMenu`。内部状态 `var speedMenuOpen by remember { mutableStateOf(false) }`。当前速度从 `playSpeedState` 读。
- **睡眠定时菜单**：同上，选项列表复用 `SleepTimerManager` 定义的档位。
- **更多菜单**：同上（对应 `sub_action_more`）。
- **字幕按钮菜单**：`SetSubtitleComponent` 里的 PopupMenu → `DropdownMenu`（选文件 / 关闭 / 切换 track）。
- **编辑栏**（`edit_layout` FlexboxLayout + 5 按钮：merge_pre / merge_next / split / delete / insert）：
  - 用 `AnimatedVisibility(editMode) { Row { IconButton x 5 } }` 替换。
  - 5 个按钮的 onClick 转发到 ViewModel 新增的 Actions（其内部再调 `PlaybackCore` 已有方法）。
- **`SubActionsRow`** 阶段 1 的空壳这时候填实：AB 拖动开关（对应 `switch_drag_ab`）、复读开关（`voice_repeat_switch`）、速度按钮 +文本、定时按钮 +文本、全屏切换、更多。

**学习点**：`DropdownMenu` / `DropdownMenuItem`、`AnimatedVisibility` 的进入/退出动画、Composable 内部临时状态 `remember { mutableStateOf(...) }`、`derivedStateOf`（速度/定时文案派生显示时可用）。

---

## 阶段 3：文件选择、字幕加载 Composable 化

**目标**：把 `SelectFileComponent` 和 `SetSubtitleComponent` 里基于 Fragment `registerForActivityResult` 的逻辑搬到 Composable 内。

**改动清单**：

- 在 `PlayVideoScreen` 里用 `rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments())` 定义文件选择器；`+` 按钮点击时 `launcher.launch(...)`。
- 拿到 Uri 后：`LaunchedEffect(uri) { viewModel.onPickVideoFiles(uris) }`。
- 字幕文件夹的选择、字幕 Uri 的选择用同样思路。
- 复用 `SubtitleAutoLoader.kt`（248 行）里的字幕自动扫描逻辑，只是入口从 Component 挪到 ViewModel。
- 完成后 `SelectFileComponent.kt` 和 `SetSubtitleComponent.kt` 可清空（**文件保留**，等阶段 8 一起清）。

**学习点**：`rememberLauncherForActivityResult`、`ActivityResultContracts`、`LaunchedEffect` 副作用、Composable 里如何触发一次性动作。

---

## 阶段 4：横屏适配

**目标**：横屏也走 Compose，右侧滑入面板效果保留。

**改动清单**：

- 在 `PlayVideoScreen` 顶部：`val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE`。
- 用 `if (isLandscape) LandscapeLayout(...) else PortraitLayout(...)` 分支。两个私有 Composable 复用同一批子 Composable（TopBar / SubActionsRow / PlaybackControlRow / WaveformBox / ...），仅编排不同。
- **横屏右侧滑入面板**：`AnimatedVisibility(showEditPanel, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it })`。
- 左侧 `show_edit_panel` 切换按钮用 `IconButton` + 一个 `remember` 或 ViewModel 里的 `showLandOverlay` 状态。
- 阶段 4 结束后**竖屏 XML 布局文件 `video_play_fragment.xml`（layout & layout-land 两份）暂不删**，等阶段 8 统一清理。

**学习点**：`LocalConfiguration`、`BoxWithConstraints`（可选，用于按宽度断点）、`AnimatedVisibility` 的 `slideIn/slideOut/fadeIn/fadeOut`、`Modifier.weight`、`Modifier.fillMaxHeight/Width`。

---

## 阶段 5：三个 BottomSheet 迁移到 ModalBottomSheet

**目标**：完全去掉 `BottomSheetDialogFragment`，用 M3 `ModalBottomSheet`。

**改动清单**：

- 在 `PlayVideoScreen` 里维护三个开合状态：`showPlaylist / showHistory / showSleepTimer`（`rememberSaveable` 或就 `remember`）。
- 每个 Sheet 一个 Composable：
  - `PlaylistSheet`（`playvideo/compose/PlaylistSheet.kt`）：`ModalBottomSheet` + `LazyColumn`，`items(playlist, key = { it.id })`；循环模式 chip；拖动排序可以先做**简化版**（长按 + `detectDragGesturesAfterLongPress` + `Modifier.offset` + `mutableStateListOf`），或先引入 `sh.calvin.reorderable` / 类似轻量库。若想最小依赖，先实现"长按 + 上下点击移动"这种降级版，拖动排序留 TODO。
  - `HistorySheet`：`ModalBottomSheet` + `LazyColumn`。
  - `SleepTimerSheet`：`ModalBottomSheet` + Column + 一组 Button。
- 数据源全部从 `PlayerViewModel`（或直接 `PlaybackCore`）拿。`HistoryListAdapter` / `PlaylistAdapter` 里的 diff 逻辑不再需要——LazyColumn 的 key 参数自动搞定。
- 完成后：`PlaylistSheetFragment.kt` / `HistorySheetFragment.kt` / `SleepTimerSheetFragment.kt` / `BasePlaySheetFragment.kt` 可清空/删除（阶段 8 一起处理）。对应的 XML 布局同样阶段 8 一起删。

**学习点**：`ModalBottomSheet` + `rememberModalBottomSheetState`、`LazyColumn` + `items(key = )`、`SheetValue`、拖动手势（`detectDragGesturesAfterLongPress`）、`mutableStateListOf`。

---

## 阶段 6：GestureCardView Compose 化

**目标**：视频区的手势不再依赖 `GestureCardView` / `GestureCardViewCustomLongClick`，改用 `Modifier.pointerInput`。

**改动清单**：

- 把阶段 1 的 `VideoPlayerBox` 里的 `AndroidView(GestureCardView)` 换成 `Box(Modifier.pointerInput(Unit) { ... })`，内部 `AndroidView` 只包 `PlayerView`。
- 手势逻辑迁自 `PlayGestureComponent`（233 行）：
  - 单击 → 播放/暂停：`detectTapGestures(onTap = ...)`
  - 双击 → 回到句首：`detectTapGestures(onDoubleTap = ...)`
  - 长按 → 倍速：`detectTapGestures(onLongPress = { ... }, onPress = { tryAwaitRelease(); ... })`
  - 纵向 fling → 上/下句：`awaitEachGesture` + `VelocityTracker`
  - 左侧上下滑 → 亮度：`detectVerticalDragGestures`（按 `change.position.x` 与容器宽度比判断左/右）
  - 右侧上下滑 → 音量：同上
- 各个手势之间的冲突消解建议用 `awaitEachGesture` + `awaitFirstDown` + `awaitTouchSlopOrCancellation` 模式，一次消化整段手势。
- 手势提示文字（`gesture_tips_text`）用 Composable 版：`AnimatedVisibility` 显示"1.5x 倍速"这种。

**学习点**：`Modifier.pointerInput`、`detectTapGestures`、`detectVerticalDragGestures` / `detectHorizontalDragGestures`、`awaitEachGesture`、`VelocityTracker`、`awaitPointerEventScope`。

---

## 阶段 7：ScrollWaveformView Compose 化（最难，最后做）

**目标**：波形图完全 Compose 化，去掉 `AndroidView` 包装。

**改动清单**：

- 新建 `playvideo/compose/WaveformCanvas.kt`：`Canvas(modifier = Modifier.fillMaxWidth().height(waveHeight))` 内 `drawXxx(...)`。
- 需要迁的绘制内容（对照 `ScrollWaveformView.kt`）：
  - 波形条（对 `allWaveDataFlow` 的振幅数组按缩放绘制柱状/线条）
  - 中心线（当前播放位置指示线）
  - 句子边界的标记线
  - AB 区间的圆形徽标（两个可拖动的圆点）
  - 拖动 seek：整体水平拖动改变 offset，`pointerInput { detectHorizontalDragGestures { _, dx -> offset += dx } }`
  - AB 边界拖动：hitTest 圆形 → 只拖那个点
- 性能：波形数据大时用 `remember(allWaveData) { ... }` 缓存派生结构；必要时 `drawWithCache` 缓存 `Path`。
- **保守做法**：阶段 7 可拆成 7a（先做基础绘制 + 拖动 seek）+ 7b（AB 拖动 + 编辑相关），中间可以工程稳定运行。

**学习点**：`Canvas`、`DrawScope`（`drawLine / drawRect / drawCircle / drawPath / translate / clipRect`）、`drawWithCache`、复杂 `pointerInput`、`hitTest`、`onSizeChanged`、坐标系变换。

⚠️ 这个阶段可能吃力，如果时间/精力不够，允许**一直卡在阶段 6**，`ScrollWaveformView` 长期用 `AndroidView` 兜底也是合理的。

---

## 阶段 8：清理

**删除清单**（确认各阶段都跑通后再做）：

- `app/src/main/res/layout/video_play_fragment.xml`
- `app/src/main/res/layout-land/video_play_fragment.xml`
- `app/src/main/res/layout/playlist_sheet_fragment.xml`
- `app/src/main/res/layout/playlist_sheet_item.xml`
- `app/src/main/res/layout/history_sheet_item.xml`
- `app/src/main/res/layout/layout_sleep_timer_sheet.xml`
- `app/src/main/java/com/language/repeater/playvideo/PlayVideoFragment.kt`（旧 View 版本，阶段 8 删除后可把 `PlayVideoFragmentCompose` 重命名回 `PlayVideoFragment`）
- `app/src/main/java/com/language/repeater/playvideo/components/` 整个目录（6 个 Component）
- `app/src/main/java/com/language/repeater/playvideo/playlist/PlaylistSheetFragment.kt` 及其 Adapter
- `app/src/main/java/com/language/repeater/playvideo/history/HistorySheetFragment.kt` 及其 Adapter
- `app/src/main/java/com/language/repeater/playvideo/sleeptimer/SleepTimerSheetFragment.kt`
- `app/src/main/java/com/language/repeater/foundation/BasePlaySheetFragment.kt`
- `app/src/main/java/com/language/repeater/foundation/BaseComponent.kt`（如无其他地方使用）
- 阶段 7 完成后：`app/src/main/java/com/language/repeater/widgets/ScrollWaveformView.kt`、`AllWaveformView.kt`（后者 XML 已注释掉）
- 阶段 6 完成后：`app/src/main/java/com/language/repeater/widgets/GestureCardView.kt`、`GestureCardViewCustomLongClick.kt`
- `app/build.gradle.kts` 里的 `viewBinding = true`（如整个工程都改完后）——**这一条要看还有没有其他 ViewBinding 使用者，谨慎处理，可能保留**

---

## 待复用的既有资产（无需重造）

- **DataStore 观察扩展**：`app/src/main/java/com/language/repeater/utils/DataStoreUtil.kt` 里的 `fun <T> DataStore<Preferences>.observe(key, default)` 直接用。
- **Setting 页里的通用组件**（可考虑抽出为 `foundation/compose/`）：`MyTopAppBar`、`MyDivider`、`SettingClickItem` 等目前在 `SettingScreen.kt` 内是 private，如果 PlayVideoScreen 也需要顶部返回栏 + 标题的模式，可以从阶段 2 开始考虑抽公共文件 `foundation/compose/CommonComposables.kt`。当前 Setting 页无返回按钮的顶部栏，PlayVideoScreen 的 `PlayTopBar` 造型也不同，是否抽公共**看阶段 2 时的实际手感再决定**，不强制。
- **图标**：`com.composables.icons.tabler.outline.R.drawable.tabler_ic_xxx_outline` 系列，Setting 页已经在用。
- **状态透传**：`PlayerViewModel` 里所有 `StateFlow` / `SharedFlow` 都可以直接 `collectAsState()`。
- **PlaybackCore Actions**：现有的 `seekToNextSentence()`, `seekToPreviousSentence()`, `seekTo(seconds)`, `setPlaySpeed()`, `setSleepTimer()`, `toggleRepeat()` 等方法一律复用，不改 Repository 层。

---

## 关键文件（本次会新增或修改的）

**新增（阶段 0-1）**：
- `app/src/main/java/com/language/repeater/playvideo/compose/PlayVideoScreen.kt`
- `app/src/main/java/com/language/repeater/playvideo/compose/PlayTopBar.kt`
- `app/src/main/java/com/language/repeater/playvideo/compose/VideoPlayerBox.kt`
- `app/src/main/java/com/language/repeater/playvideo/compose/WaveformBox.kt`
- `app/src/main/java/com/language/repeater/playvideo/compose/SubActionsRow.kt`
- `app/src/main/java/com/language/repeater/playvideo/compose/PlaybackControlRow.kt`

**修改**：
- `app/src/main/java/com/language/repeater/playvideo/PlayVideoFragment.kt`（重写为 Compose 薄壳）
- `app/src/main/java/com/language/repeater/playvideo/PlayerViewModel.kt`（补 Actions 和派生 StateFlow，尽量少改）

**重命名**：
- 无（迁移期间旧 `PlayVideoFragment.kt` **保持原名不动**，新 Compose 版本用 `PlayVideoFragmentCompose.kt`；阶段 8 全部清理后可把 `PlayVideoFragmentCompose` 改回 `PlayVideoFragment`）

**后续阶段新增**：
- 阶段 5：`playvideo/compose/PlaylistSheet.kt` / `HistorySheet.kt` / `SleepTimerSheet.kt`
- 阶段 7：`playvideo/compose/WaveformCanvas.kt`

**始终不动**：
- `MainActivity.kt`、`activity_nav_host.xml`、导航图
- `PlaybackCore.kt`、`PlaybackService.kt`
- `AppDb` 及所有 Dao
- Setting 页所有文件

---

## 风险与注意事项

1. **AndroidView 的 update 频率**：`update` lambda 每次重组都会跑，波形/播放器等重控件里注意"只在真需要时"apply。可以 `remember(sentences) { ... }` 做等值判断。
2. **PlayerView 生命周期**：`AndroidView(factory = { PlayerView(it).apply { player = ...; useController = false } })`，在 `onRelease` 里 `player = null` 避免泄漏。
3. **横屏 <-> 竖屏切换的状态保留**：Compose 页里所有临时状态用 `rememberSaveable` 防止转屏丢失；播放状态本来就在 `PlaybackCore` 单例，天然保留。
4. **ScrollWaveformView 阶段 7 太难时**：允许长期保留 `AndroidView` 兜底，工程仍是"Compose 化"的成果。
5. **阶段间的 commit 建议**：每个阶段做完各自一次 commit，commit message 参考 `6771db2 setting页改成compose实现` 的风格，例如 `playvideo阶段1: 竖屏骨架Compose化 + AndroidView兜底`。
6. **不改 `build.gradle.kts` 的 Compose 相关配置**：Setting 页迁移时已加齐所有依赖，播放页不需要再动 Gradle。
7. **`viewBinding = true` 保留**：迁移中间态里旧的 `PlayVideoFragment.kt` 依旧要用 ViewBinding，`viewBinding` 编译标志只在阶段 8 全部清理后才考虑关掉。

---

## 验证方式

每个阶段结束后，人工在设备上跑一次以下场景（不写自动化测试）：

- 阶段 1：进入播放页 → 加视频 → 播放 → 拖 seek → 切上下句 → 点播放列表按钮（应弹出旧的 View BottomSheet）→ 点设置按钮（应导航到 Compose 版 Setting）
- 阶段 2：所有菜单点开 → 项目切换生效 → 编辑模式开关 → 编辑按钮点击有响应
- 阶段 3：加视频文件、切换字幕、字幕自动扫描
- 阶段 4：旋转屏幕 → 横竖都可用 → 横屏面板滑入滑出流畅
- 阶段 5：三个 Sheet 都能开合，Playlist 拖动排序（若已做）正常
- 阶段 6：视频区所有手势（单击/双击/长按倍速/纵滑上下句/左侧亮度/右侧音量）符合原体验
- 阶段 7：波形显示、播放指示线、AB 拖动、seek 拖动无异常
- 阶段 8：全量回归

**回滚策略**：因为旧的 `PlayVideoFragment.kt` 一直保留到阶段 8，任一阶段出严重问题都可以在 `MainActivity.kt` 的 NavGraph 里把注册的类临时改回 `PlayVideoFragment`（去掉 `Compose` 后缀）。
