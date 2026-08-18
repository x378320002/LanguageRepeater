# LanguageRepeater — Compose Multiplatform 演进计划

## 目标

1. **去除 XML / View 体系，全面迁移到 Jetpack Compose**
2. **播放系统与 Media3 解耦，支持后续替换为腾讯播放器**
3. **最终迁移到 Compose Multiplatform（Android + iOS）**

---

## 核心原则

> **从现在开始，任何新代码禁止在业务层 import `androidx.media3.*`。**
> 例外只有两个文件：`PlaybackCore.kt`（存量）和 `Media3PlayerEngine.kt`（适配层）。

---

## 阶段规划

```
阶段 1: 建立抽象层（不动 View，不动包结构）
    ↓
阶段 2: Compose 迁移只走新抽象层（旧 View 继续活着）
    ↓
阶段 3: 旧 View 全部删除（此时耦合已断）
    ↓
阶段 4: 整理分包 + 换腾讯播放器
    ↓
阶段 5: KMP 拆分
```

---

## 阶段 1：建立抽象层

### 目标
在现有结构下，把 `PlaybackCore` 包一层 `PlayerEngine`，切断业务层对 Media3 的依赖。

### 新建文件

| 文件 | 职责 |
|---|---|
| `player/PlayerEngine.kt` | 纯播放能力抽象接口，不 import media3 |
| `player/PlayerEngineState.kt` | 播放状态数据类，不 import media3 |
| `player/MediaSource.kt` | 媒体源数据类，替代 MediaItem |
| `player/Media3PlayerEngine.kt` | 适配层：把 PlaybackCore 包装成 PlayerEngine |

### 修改文件

| 文件 | 改动 |
|---|---|
| `PlayerViewModel.kt` | 改为依赖 `PlayerEngine` 而非 `PlaybackCore` |

### 关键规则
- `PlaybackCore` 内部一行不改，风险最小
- 旧 View 的 Component 继续通过 `PlayerViewModel` 工作，无感知
- `PlayerViewModel` 不再向外漏 `Player` / `MediaItem` 类型

---

## 阶段 2：Compose 组件实现（只依赖 PlayerEngine）

### 目标
实现所有 Compose 组件，并且**从一开始就只依赖 `PlayerEngine` 抽象层**，不 import media3。

### 当前已完成的 Compose 组件
- `PlayVideoScreen.kt` — 主播放页骨架（空壳，待填充）
- `SettingScreen.kt` — 设置页（已完成）
- `PlayTopBar.kt` — 顶部栏（已完成）

### 待实现的 Compose 组件
| 组件 | 状态 | 说明 |
|---|---|---|
| `VideoPlayerBox.kt` | 空壳 | 视频渲染区，需要接入 PlayerEngine |
| `WaveformBox.kt` | 空壳 | 波形区，阶段 1 用 AndroidView 包装旧 View |
| `PlaybackControlRow.kt` | 空壳 | 播放控制栏，需要接入 PlayerEngine 的 isPlaying 状态 |
| `SubActionsRow.kt` | 空壳 | 副操作栏，需要接入 PlayerEngine 的 speed 等状态 |

### 实现顺序
1. `PlaybackControlRow` — 最简单，只依赖 isPlaying
2. `SubActionsRow` — 依赖 speed、repeat 等状态
3. `VideoPlayerBox` — 需要处理视频渲染，最复杂
4. `WaveformBox` — 阶段 1 用 AndroidView 包装旧 ScrollWaveformView

---

## 阶段 3：删除旧 View

当 Compose 版功能对齐后，删除：

- `PlayVideoFragment.kt`
- `playvideo/components/` 下 6 个 Component
- 所有 XML 布局
- `PlayVideoFragmentCompose.kt`、`SettingFragment.kt` 壳 Fragment

---

## 阶段 4：整理分包 + 换腾讯播放器

### 整理后的包结构
```
player/       → PlayerEngine、PlayerEngineState、MediaSource
playback/     → PlaybackController、句子/复读业务编排
ui/           → Compose 界面
```

### 换腾讯播放器
```kotlin
class TencentPlayerEngine(...) : PlayerEngine
```

---

## 阶段 5：KMP 拆分

```
shared/
├── commonMain/
│   ├── player/
│   ├── playback/
│   └── ui/
├── androidMain/
│   └── player/Media3PlayerEngine.kt / TencentPlayerEngine.kt
└── iosMain/
    └── player/AVPlayerEngine.kt
```

---

## 当前状态

- [x] Navigation3 迁移完成
- [x] MainActivity 纯 Compose
- [x] 阶段 1：PlayerEngine 抽象层
- [ ] 阶段 2：Compose 组件实现（只依赖 PlayerEngine）
- [ ] 阶段 3：删除旧 View
- [ ] 阶段 4：整理分包
- [ ] 阶段 5：KMP 拆分
