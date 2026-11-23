# Google Play 上架操作手册（复读机 / Language Repeater）

这份文档按“小白照着点”的方式写。你不需要改代码，先按顺序把素材和 Play Console 填完。

先说一个最重要的概念：

> **截图、图标、Feature graphic 只上传到 Play Console 的“商店列表”，不会打进 APK/AAB。**
> 你把它们放在电脑上的 `play-store/` 文件夹里只是为了方便管理；真正生效是在 Play Console 的网页里点“上传”。

---

## 0. 这个目录里有什么

```
play-store/
├── README.md                     ← 你正在看的这份
├── APP_CONTENT_COPY.md           ← App content 声明可以直接复制的文字
├── store-listing-draft.md        ← 商店标题/短描述/完整描述草稿
├── icon/
│   └── ic_launcher-512x512.png   ← 应用图标（512×512，已符合要求）
├── feature-graphic/
│   ├── feature-graphic-1024x500.jpg  ← 已做好的 Feature graphic 占位图，可直接上传
│   └── feature-graphic.html          ← 这张图的源文件，想改可以用浏览器打开
├── screenshots/
│   ├── phone/                    ← 手机截图放这里（已裁好 4 张，可直接上传）
│   ├── originals/                ← 原始截图备份，不要上传
│   ├── tablet-7/                 ← 7 寸平板截图放这里（可选）
│   └── tablet-10/                ← 10 寸平板截图放这里（可选）
└── videos/README.md              ← 审核用演示视频怎么录
```

---

## 1. 先准备新截图（不要用现在仓库里的截图）

仓库里现在的三张截图有两个问题：

1. 比例是 1080×2400 / 2400×1080，长边超过短边的 2 倍，Play 不接受；
2. 画面里有腾讯视频水印和第三方动画内容，有版权风险。

### 1.1 准备一个“自己的”样例视频

- 用手机自拍一段 10–30 秒的视频，或者用你拥有版权的素材；
- 不要出现电影、电视剧、动漫、别人的视频；
- 视频文件重命名为干净的英文名，例如 `sample.mp4`，避免截图里出现 `Screenrecorder-2026-...` 这种文件名；
- 最好配一个简单的 `.srt` 字幕，方便截“字幕/句子”效果。

### 1.2 在手机上截图

同时按 **电源键 + 音量减**。建议截这些画面（4–8 张）：

1. 竖屏播放器：视频 + 波形 + A/B 标记；
2. 句子编辑：合并上句/下句、分割、删除、插入；
3. 横屏全屏：右侧浮动控制栏；
4. 添加媒体 / 媒体库；
5. 设置页（暗色模式、手势、隐私政策入口）；
6. 字幕选择 / 倍速 / 定时关闭。

截图前建议：

- 开启勿扰模式，清掉通知；
- 关掉 VPN（状态栏的 VPN 图标不好看）；
- 电量尽量充足；
- 手机语言切成你商店默认语言（英文列表就用英文系统，中文列表就用中文系统）。

### 1.3 把截图传到 Mac

任选一种：

- **AirDrop（最简单）**：手机相册里选中截图 → 分享 → AirDrop → 选你的 Mac；
- **USB**：用 Android File Transfer / Mac 的“文件”应用，找到 `Pictures/Screenshots` 或 `DCIM/Screenshots`；
- **adb**（如果装了 Android SDK）：
  ```bash
  adb pull /sdcard/Pictures/Screenshots/ ~/Downloads/
  ```
  有些手机是 `/sdcard/DCIM/Screenshots/`。

然后把图片拖进：

```
play-store/screenshots/phone/
```

平板截图对应放进 `tablet-7/` 或 `tablet-10/`。没有平板可以先只传手机截图，不影响发布。

---

## 2. 把截图处理成 Play 接受的尺寸

> 当前已经把首批 4 张截图裁好，放在 `play-store/screenshots/phone/`：
>
> - `01-player-video-1080x1920.jpg`
> - `02-landscape-1920x1080.jpg`
> - `03-audio-dark-1080x1920.jpg`
> - `04-audio-light-1080x1920.jpg`
>
> 原始尺寸截图在 `play-store/screenshots/originals/`，不要上传那一份。以后新增截图再按下面的方法裁。

### 2.1 Play 的硬性要求

- 格式：**JPEG 或 24-bit PNG（不能有透明通道）**；
- 单边：**320–3840 px**；
- 长边不能超过短边的 **2 倍**（也就是比例不能比 2:1 更“长”）；
- 手机截图：至少 2 张，最多 8 张；建议 4–8 张；
- 推荐尺寸：竖屏 **1080×1920**，横屏 **1920×1080**；
- 平板：建议 4 张以上（1600×2560 / 2560×1600 都可以）。

你现在手机截出来通常是 1080×2400，需要裁成 1080×1920。

### 2.2 用“预览”App 裁（不写命令）

1. 双击打开截图；
2. 菜单 **工具 → 裁剪**；
3. 拖动裁剪框，把多余的上下部分去掉，尽量裁成 9:16；
4. 菜单 **工具 → 调整大小**：
   - 宽度填 `1080`，高度填 `1920`；
   - 如果裁剪后的比例已经接近 9:16，勾选“按比例缩放”；
5. 菜单 **文件 → 导出**，格式选 **JPEG**，质量 90 左右，保存到 `play-store/screenshots/phone/`。

> 不要直接把 1080×2400 硬压成 1080×1920，那样画面会变形。先裁剪，再调整大小。

### 2.3 用命令行裁（可选）

在项目根目录执行：

```bash
cd ~/AndroidStudioProjects/LanguageRepeater

# 竖屏：1080x2400 -> 1080x1920（居中裁掉上下）
sips -c 1920 1080 -s format jpeg 你的截图.jpg --out play-store/screenshots/phone/01.jpg

# 横屏：2400x1080 -> 1920x1080（居中裁掉左右）
sips -c 1080 1920 -s format jpeg 你的截图.jpg --out play-store/screenshots/phone/02.jpg

# 检查尺寸
sips -g pixelWidth -g pixelHeight play-store/screenshots/phone/01.jpg
```

`sips -c` 是居中裁剪；如果它把你不想裁掉的上方标题栏裁掉了，就用“预览”手动把裁剪框往下拖一点，优先保留应用界面。

### 2.4 检查是否合格

对每张图执行：

```bash
sips -g pixelWidth -g pixelHeight play-store/screenshots/phone/01.jpg
```

只要满足：两边都在 320–3840 之间，长边 ≤ 短边 × 2，就可以。

---

## 3. 图标和 Feature graphic

### 3.1 应用图标

已准备好：

```
play-store/icon/ic_launcher-512x512.png
```

规格：512×512、32-bit PNG、带 alpha、533KB，符合 Play 要求。直接上传即可。

### 3.2 Feature graphic

已生成一张占位图：

```
play-store/feature-graphic/feature-graphic-1024x500.jpg
```

规格：1024×500、JPEG、无透明、原创，不含第三方影视内容。可以直接上传。

如果你想换成自己的设计：

- 尺寸必须是 **1024×500**；
- 格式：**JPEG 或 24-bit PNG（不能有透明）**；
- 不要用电影/电视剧画面，不要放“Google Play 排名/价格”等误导性文字；
- 可以用 Canva、Figma、Keynote 做，导出 JPEG 即可。

> Feature graphic 是“发布商店列表”的必填项，不是可选项。

---

## 4. 在 Play Console 创建应用

1. 打开 <https://play.google.com/console>；
2. 如果是新账号：先交 $25 注册费、完成身份验证；
3. 点 **Create app / 创建应用**；
4. 填写：
   - App name：建议 `Language Repeater - 复读机`（不超过 30 个字符）；
   - Default language：英文或中文（如果你面向全球，建议 English；后面可以加中文翻译）；
   - App or game：App；
   - Free or paid：Free；
   - 勾选声明（Play 政策、美国出口合规等）；
5. 创建后，包名不在这里填；**第一次上传 AAB 后，包名 `com.language.repeater` 就固定了，不能再改**。

创建后先不要急着提交审核，先把下面的 App content 和商店素材填完。

---

## 5. 生成并上传 AAB

### 5.1 生成 AAB

```bash
cd ~/AndroidStudioProjects/LanguageRepeater
./gradlew :app:bundleRelease
```

产物：

```
app/build/outputs/bundle/release/app-release.aab
```

注意：

- **上传 AAB，不要上传 APK**；
- `app/build/outputs/apk/release/app-release.apk` 是 192MB，超过旧 APK 100MB 限制，不要传；
- 当前 AAB 约 96MB，Play 的 base module 限制是 500MB，没问题；
- 如果这个包名以前在 Play Console 上传过任何版本，先把 `versionCode` 改大再重新构建。

### 5.2 上传到内部测试

1. 左侧 **Test and release / 测试和发布 → Testing / 测试 → Internal testing / 内部测试**；
2. 右上角 **Create new release / 创建新版本**；
3. 第一次会提示配置 **Play App Signing**：新应用会自动使用 Google 生成的签名密钥，直接按提示确认即可。你的 `.jks` 是“上传密钥”，不是最终给用户的签名密钥。以后可以在 **Protected with Play → Play Store protection → Manage Play app signing**（旧界面：**Test and release → App integrity → App signing**）查看上传密钥证书；
4. 在 **App bundles** 区域点 **Upload / 上传**，选 `app-release.aab`；
5. 等它处理完；
6. Release name 填 `0.10-internal-1`；
7. Release notes 填 `首次内部测试`；
8. 点 **Save as draft** 或 **Next / 下一步**；
9. 如果出现错误，按提示解决；没有错误就 **Start rollout to Internal testing / 开始发布到内部测试**。

> 如果 **Create new release** 是灰的，说明 Dashboard 还有未完成的设置任务，先把 Dashboard 上列出的项目做完。

### 5.3 重要：上传完 AAB 再回 App content

Play Console 会根据 AAB 里的 Manifest 自动判断你需要哪些声明：

- 有 `AD_ID` → 出现 **Advertising ID** 声明；
- 有 `READ_MEDIA_VIDEO` → 出现 **Photo and video permissions** 声明；
- 有 `foregroundServiceType="mediaPlayback"` → 出现 **Foreground service permissions** 声明。

所以顺序是：**先上传 AAB，再去 App content 填声明**。如果现在打开 App content 没看到这些项，先完成第 5.2 步再刷新。

---

## 6. App content 逐项填写

入口：

> **Monitor and improve / 监控和改进 → Policy and programs / 政策与计划 → App content / 应用内容**
>
> 有些界面直接是 **Monitor and improve → App content**。

进去后看 **Needs attention / 需要注意** 标签。下面按项目写。

### 6.1 Privacy policy / 隐私政策

1. 点 **Start declaration / 开始声明**；
2. 粘贴隐私政策 URL：
   - 英文商店列表：`https://x378320002.github.io/language-repeater-site/privacy-en.html`
   - 中文商店列表：`https://x378320002.github.io/language-repeater-site/privacy.html`
3. 保存。

应用内已经有隐私政策入口（设置页），这一项不用改代码。

### 6.2 Ads / 广告

选择 **No, my app does not contain ads / 否，我的应用不含广告**。

> 应用没有广告 SDK；“Contains ads” 选 No。Firebase Analytics 不算广告。

### 6.3 App access / 应用访问权限

选择 **All functionality is available without special access / 所有功能无需特殊访问权限即可使用**。

如果有 “Any other instructions / 其他说明” 输入框，可以写：

> 核心功能需要用户选择设备上的本地音视频。打开应用后点击右上角“+”，授予媒体库权限或选择本地文件，即可体验播放、A/B 复读、句子编辑。无账号、无登录。

如果它要求你选 “Some functionality is restricted”，不要选；这个应用没有登录门槛。

### 6.4 Content ratings / 内容分级

1. 点 **Start questionnaire / 开始问卷**；
2. 填联系邮箱（可以用 `wangzixu0612@gmail.com`）；
3. 类别选 **Utility, Productivity, Communication, or Other / 工具、效率、通讯或其他**（如果选项里有“All other apps”就选它）；
4. 问卷里关于暴力、性、毒品、赌博、恐怖、广告等，按实际情况选 **No / 否**；
5. 应用本身不提供影视内容，只是播放用户本地文件；没有用户上传/分享功能；
6. 提交后拿到评级（大概率是 Everyone / 3+ 或类似）。

### 6.5 Target audience and content / 目标受众和内容

1. 年龄段：建议选 **13-17** 和 **18+**（或只选 18+）；
2. **不要选 13 岁以下**，否则会触发 Families policy，要求会多很多；
3. 如果问“是否专为儿童设计”，选 **No**；
4. 保存。

> 你现在的截图里有卡通画面，容易被审核认为面向儿童；换成你自己的样例视频后，选 13+ 更稳。

### 6.6 Data safety / 数据安全

完整文字版见 [APP_CONTENT_COPY.md](./APP_CONTENT_COPY.md) 的“Data safety”一节。简单流程：

1. App content → **Data safety → Start**；
2. “Does your app collect or share any of the required user data types?” 选 **Yes**；
3. “Is all of the user data collected by your app encrypted in transit?” 选 **Yes**；
4. “Do you provide a way for users to request that their data is deleted?” 选 **Yes**（隐私政策里有邮箱）；
5. 数据类型按下面勾选：
   - **App activity → App interactions**（Firebase Analytics）
   - **App info and performance → Crash logs**（Crashlytics）
   - **App info and performance → Diagnostics**（Crashlytics）
   - **Device or other IDs**（Firebase Analytics / Crashlytics）
   - **Location → Approximate location**：隐私政策里写了“可能从 IP 获取大致位置”，如果保持这句就勾选；如果你确认 Firebase 没开位置收集，也可以把政策那句删掉再取消勾选；
   - 如果 Advertising ID 声明选了 Yes，广告 ID 归在 **Device or other IDs** 里；
6. 每一项的处理方式：
   - 收集方式：**Collected**（不是 Shared）；
   - 用途：**Analytics** / **App functionality**；
   - 是否必需：**Required**（应用没有关闭统计的开关）；
   - 是否加密传输：**Yes**；
   - 是否可请求删除：**Yes**，通过隐私政策里的邮箱；
7. 提交。

> Data safety 必须和隐私政策、Firebase 实际行为一致。不确定时，以上面的隐私政策文本为准。

### 6.7 Advertising ID / 广告 ID

入口：**App content → Advertising ID**（通常在 **Needs attention** 里；上传含 `AD_ID` 的 AAB 后才出现）。

当前 AAB 的 Manifest 里有 `com.google.android.gms.permission.AD_ID`，所以你必须二选一：

**方案 A：保留（推荐，省事）**

1. “Does your app use advertising ID?” 选 **Yes**；
2. 用途选 **Analytics / 分析**；
3. 如果问“是否用于广告投放/营销”，选 **No**（应用没有广告）；
4. 保存。

这样和“Contains ads = No”并不矛盾：广告 ID 只是被 Firebase Analytics 使用，不代表应用有广告。

**方案 B：移除（需要改代码）**

1. 在 Manifest 里用 `tools:node="remove"` 移除 `AD_ID` 和 Ads Services 权限；
2. 加 `google_analytics_adid_collection_enabled=false`；
3. 重新构建 AAB、上传新版本；
4. 等声明刷新后再选 **No**。

> 不要“权限还在，却选 No”。这是最常见的审核不一致问题。

### 6.8 Photo and video permissions / 照片和视频权限

入口：**App content → Photo and video permissions**（有的界面是 **Permissions → Photo and video**）。

当前 AAB 有 `READ_MEDIA_VIDEO`，而“+”按钮打开的是 MediaStore 媒体库，所以这个声明会出现，必须填。

填写思路：

1. 选择 **READ_MEDIA_VIDEO**；
2. 说明核心功能：
   > 本应用是本地音视频复读播放器，需要把设备上的视频/音频作为媒体库展示，支持按文件夹筛选、多选加入播放列表，并对任意条目做 A/B 复读。Android Photo Picker/SAF 只能单次选择文件，无法提供持续的全库浏览、文件夹结构和跨会话播放列表，因此需要 READ_MEDIA_VIDEO。应用不会上传这些媒体文件。
3. 如果表单问 READ_MEDIA_AUDIO，说明：
   > 用于在媒体库中列出并播放本地音频文件。
4. 按表单要求提供演示视频链接。视频内容：
   - 点击 +；
   - 出现媒体权限弹窗，点允许；
   - 看到媒体库/文件夹，选择视频；
   - 开始播放并展示 A/B 复读。
5. 提交。

**如果你不想做这个声明**：需要把“+”改成 SAF（`OpenMultipleDocuments` + 持久化 URI），然后从 Manifest 移除 `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`，重新上传 AAB。这样表单会消失，但会失去现在的媒体库浏览体验。这个改动我可以帮你做。

完整可复制文本见 [APP_CONTENT_COPY.md](./APP_CONTENT_COPY.md)。

### 6.9 Foreground service permissions / 前台服务权限

入口：**App content → Foreground service permissions**。

当前应用有 `foregroundServiceType="mediaPlayback"`，targetSdk 37，所以必须声明。

1. 选择 **Media playback / 媒体播放**；
2. Description（可复制）：
   > 用户在应用中主动选择本地音视频并开始播放后，应用通过媒体播放前台服务在后台继续播放，以支持锁屏、通知栏、耳机/蓝牙按键的上一句、下一句、暂停和恢复控制。
3. User impact（可复制）：
   > 如果系统延迟启动或中断该前台服务，后台播放会暂停，通知栏/锁屏媒体控制不可用；用户重新打开应用或点击播放即可恢复，不会丢失播放位置。
4. Use case：选择 **Continue audio or video playback from the background / 在后台继续播放音频或视频**；
5. 提供演示视频链接。视频要展示：
   - 用户主动开始播放；
   - 按 Home 键回到桌面；
   - 播放继续（通知栏出现媒体控制）；
   - 最好再展示锁屏/耳机键切上一句。
6. 提交。

完整可复制文本见 [APP_CONTENT_COPY.md](./APP_CONTENT_COPY.md)。

### 6.10 其他声明

以下如果出现，按实际情况选 **No**：

- News app / 新闻应用；
- Government apps / 政府应用；
- Financial features / 金融功能；
- Health apps / 健康应用；
- Full-screen intent / 全屏 Intent（这个应用没有）；
- COVID-19 contact tracing / 疫情接触追踪；
- Data deletion / 账号删除（没有账号体系，不适用）。

全部填完后，App content 里相关项应该显示绿色/已完成。**Data safety、内容分级、目标受众、广告、隐私政策是闭封测试前必须完成的。**

---

## 7. 商店列表（Main store listing）

入口：

> **Grow users / 吸引用户 → Store presence / 商店展示 → Main store listing / 主要商店列表**

### 7.1 图形素材

在 **Graphics / 图形** 区域：

1. **App icon / 应用图标**
   - 上传 `play-store/icon/ic_launcher-512x512.png`；
2. **Feature graphic / 特色图片**
   - 上传 `play-store/feature-graphic/feature-graphic-1024x500.jpg`；
3. **Phone screenshots / 手机屏幕截图**
   - 上传 `play-store/screenshots/phone/` 里的 2–8 张；
   - 第一张是主图，放最能体现“A/B 复读 + 波形”的那张；
   - 顺序可以拖动调整；
4. **Tablet screenshots / 平板屏幕截图**（可选，但建议）
   - 上传 `tablet-7/` 和 `tablet-10/` 里的图，建议各 4 张。

上传方式：点对应的 **Upload / 上传** 按钮，选电脑上的文件；也可以直接把图片拖进浏览器页面。上传后 Play 会显示预览，不合格会提示。

### 7.2 文字

- **App name / 应用名称**：≤30 字符，例如 `Language Repeater - 复读机`；
- **Short description / 简短说明**：≤80 字符；
- **Full description / 完整说明**：≤4000 字符。

可直接用 [store-listing-draft.md](./store-listing-draft.md) 里的草稿，按需修改。

> 完整说明里建议明确写：**应用只播放用户自己选择的本地音视频，不提供、不托管、不下载任何影视内容。** 这能降低版权和“误导”风险。

### 7.3 联系方式和隐私政策

- 联系邮箱：`wangzixu0612@gmail.com`；
- 网站：可以填 `https://x378320002.github.io/language-repeater-site/`，也可以不填；
- 隐私政策：App content 里已经填过；如果这里还要求填，粘贴同一个 URL。

### 7.4 分类和标签

入口：

> **Grow users → Store presence → Store settings / 商店设置**

- App category：**Video Players & Editors / 视频播放器和编辑器** 或 **Education / 教育**（二选一，建议视频播放器）；
- Tags：语言学习、视频播放器、字幕、听力；
- 联系方式：邮箱同上。

### 7.5 多语言（可选）

如果默认语言是英文，想加中文：

- **Main store listing → Manage translations / 管理翻译 → Add translation → 中文（简体）**；
- 填中文标题、短描述、完整描述；
- 上传中文截图（如果截图是中文界面）；
- 保存。

---

## 8. 内部测试（Internal testing）

1. **Test and release → Testing → Internal testing**；
2. **Testers / 测试人员** 标签；
3. **Create email list / 创建电子邮件列表**；
4. 起名，例如 `internal-testers`；
5. 输入你自己的 Gmail 和测试者邮箱（逗号分隔，或上传 CSV，每行一个）；
6. **Save changes → Create**；
7. 选这个列表，**Copy shareable link / 复制可共享链接**；
8. 在手机上用被加入列表的 Google 账号打开这个链接；
9. 接受测试邀请 → 安装应用。

注意：

- 内部测试最多 100 人；
- 第一次发布后，链接可能要几小时才生效；
- 应用在内部测试时可能显示临时名称，最多 48 小时；
- 内部测试阶段 Data safety 可以豁免，但建议一起填；
- 上传后自动生成 **Pre-launch report / 预发布报告**，在轨道页面里查看崩溃、兼容性、无障碍问题。

---

## 9. 封闭测试（Closed testing）12 人 / 14 天

这是新个人开发者账号必须走的一步。企业账号不需要。

1. **Test and release → Testing → Closed testing**；
2. 第一次点 **Create track / 创建轨道**，名字随便，例如 `closed-test`；
3. **Create new release**；
4. 如果 AAB 已经上传过，点 **Add from library / 从库中添加**，选刚才那个 AAB，避免重复上传；也可以上传一个更高 versionCode 的新 AAB；
5. 填 release notes → **Next** → **Start rollout**；
6. **Manage track → Testers**；
7. **Create email list**，加入 **至少 12 个真实 Google 账号**；
8. 选列表，保存；
9. 复制 opt-in link，发给这 12 个人；
10. 每个人都要点链接、接受邀请、安装应用，并**连续 14 天保持 opt-in**。

关键注意：

- **不要用内部测试的同一批人直接做封闭测试。** 文档明确说：一个用户如果已经 opt-in 内部测试，就不再能接收 closed 测试；必须先退出内部测试，再 opt-in closed。
- 12 个人必须是不同 Google 账号；建议找同事/朋友，不要用假账号。
- 14 天是“连续”的，中途有人退出，那个人的天数会断。
- 14 天后，去 **Dashboard → Apply for production / 申请正式版发布权限**，回答问卷；审核通常 7 天内。
- 如果人数不够或活跃度不足，可能被要求继续测试。

---

## 10. 正式发布（Production）

拿到 production access 后：

1. **Test and release → Production**；
2. **Create new release**；
3. **Add from library** 选已通过测试的 AAB，或上传新 AAB；
4. 填 release notes；
5. **Next** → 检查错误 → **Start rollout to production**；
6. 第一次正式版没有百分比选择；之后的更新可以分阶段 10% / 50% / 100%；
7. 首次正式版审核通常也要几天到 7 天，留意邮件和 Play Console 通知；
8. 发布后在 **Android vitals** 看崩溃/ANR，必要时暂停发布。

---

## 11. 常见问题（小白版）

### Q1：截图到底放哪？上传到哪？

- 电脑上放：`play-store/screenshots/phone/`（只是整理）；
- 真正上传：Play Console → **Grow users → Store presence → Main store listing → Graphics → Phone screenshots → Upload**；
- 图片不会打进 AAB，和 APK 大小无关。

### Q2：为什么 App content 里没有 Advertising ID / Photo and video / Foreground service？

因为这些声明是根据 AAB 的 Manifest 生成的。先完成第 5 步上传 AAB，再回 App content 刷新；如果还没有，检查 **Needs attention** 标签，或确认 AAB 里确实有对应权限。

### Q3：我的截图是 1080×2400，能直接传吗？

不能。长边 2400 > 短边 1080 × 2 = 2160。先裁成 1080×1920（或 1920×1080）。

### Q4：现在的腾讯视频/卡通截图能先用吗？

不要。Play 商店素材不能用第三方影视画面；换成你自己的样例视频重新截。

### Q5：AAB 在哪里？

`app/build/outputs/bundle/release/app-release.aab`。

### Q6：能上传 APK 吗？

不能，新应用用 AAB。`app-release.apk` 192MB 也超过旧 APK 100MB 限制。

### Q7：versionCode 要改吗？

如果这个包名第一次上传，`1` 可以；如果之前上传过任何版本，必须在 `app/build.gradle.kts` 里把 `versionCode` 加 1，重新构建。

### Q8：Play App Signing 怎么设置？

新应用第一次上传 AAB 会自动启用 Google 生成的签名密钥；你只需要按提示确认。你的 `.jks` 是上传密钥。查看路径：

> **Protected with Play → Play Store protection → Manage Play app signing**
>
> 旧界面：**Test and release → App integrity → App signing**

备份 `keystore.properties` 和 `.jks`，不要丢。

### Q9：Create new release 是灰的怎么办？

回 **Dashboard**，把未完成的设置任务（App content、商店列表、定价/国家、隐私政策等）做完。

### Q10：测试者找不到应用？

内部/封闭测试应用不会出现在搜索结果。把 **opt-in link** 发给他们，必须用被加入测试列表的 Google 账号打开。第一次链接可能几小时才生效。

### Q11：广告 ID 到底选 Yes 还是 No？

当前 AAB 有 `AD_ID` 权限，说明 Firebase 可能使用它。最省事：选 **Yes → Analytics**，广告投放选 No。想选 No 就必须先移除权限、重新上传 AAB。

### Q12：照片/视频权限能不做吗？

只有把“+”从 MediaStore 改成 SAF 多选，并移除相关权限，才能不做；否则必须填声明。这个改动我可以帮你做。

### Q13：FGS 的演示视频怎么录？

用手机自带的屏幕录制：

1. 打开应用，选一个自己的视频；
2. 开始播放；
3. 按 Home 回桌面；
4. 展示播放继续、通知栏有媒体控制；
5. 结束录制，上传到 YouTube（不公开/仅链接可见）或 Google Drive（任何拿到链接的人可看）；
6. 把链接粘贴到声明表单。

### Q14：Data safety 里“数据删除”怎么填？

选 **Yes**，并在说明里写隐私政策里的邮箱 `wangzixu0612@gmail.com`。应用没有账号，所以不涉及账号删除。

### Q15：隐私政策 URL 用哪个？

- 英文商店列表：`privacy-en.html`；
- 中文商店列表：`privacy.html`；
- 应用内已经按语言分别跳转，不用改。

### Q16：我只面向中文用户，可以不改默认语言吗？

可以。Play 列表用中文、截图用中文即可。但如果面向全球，默认资源是中文会让法语/德语/日语用户看到中文；那就需要把英文设为默认、中文放 `values-zh-rCN`。

### Q17：EU DSA / 美国出口合规？

Play Console 如果弹出 **DSA trader status** 或 **US export laws**，按提示填。分发到欧盟需要 trader/non-trader 声明；美国出口合规按标准 HTTPS 加密申报即可。

---

## 12. 最终检查清单

上传/提交前逐项打勾：

- [ ] 新截图：不含第三方影视内容，2–8 张，1080×1920 / 1920×1080
- [ ] Feature graphic：1024×500 JPEG/24-bit PNG，无透明
- [ ] App icon：512×512 PNG，带 alpha，≤1MB
- [ ] 商店标题 / 短描述 / 完整描述 / 分类 / 联系邮箱
- [ ] AAB 已构建并签名（`app-release.aab`）
- [ ] Play Console 应用已创建
- [ ] App content：隐私政策、Ads、App access、内容分级、目标受众、Data safety
- [ ] Advertising ID 声明（保留就选 Yes/Analytics）
- [ ] Photo and video permissions 声明（如果保留 MediaStore）
- [ ] Foreground service permissions 声明 + 演示视频
- [ ] 内部测试安装成功
- [ ] 封闭测试 12 人连续 14 天
- [ ] 申请 production access 并获批
- [ ] 正式版发布 / 分阶段放量

---

## 13. 官方链接（需要时查）

- 新个人账号测试要求：<https://support.google.com/googleplay/android-developer/answer/14151465>
- App content 总览：<https://support.google.com/googleplay/android-developer/answer/9859455>
- 商店素材尺寸：<https://support.google.com/googleplay/android-developer/answer/9866151>
- Data safety：<https://support.google.com/googleplay/android-developer/answer/10787469>
- 敏感权限：<https://support.google.com/googleplay/android-developer/answer/16558241>
- 前台服务声明：<https://support.google.com/googleplay/android-developer/answer/13392821>
- Play App Signing：<https://support.google.com/googleplay/android-developer/answer/9842756>
- 测试轨道：<https://support.google.com/googleplay/android-developer/answer/9845334>
- 发布版本：<https://support.google.com/googleplay/android-developer/answer/9859348>
