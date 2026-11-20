# Play Console 声明可复制文本（复读机 / Language Repeater）

这个文件里的文字可以直接复制到 Play Console 对应表单里。遇到表单问法不同，按同样的意思填写即可。

---

## 1. Privacy policy / 隐私政策

英文商店列表：

```text
https://x378320002.github.io/language-repeater-site/privacy-en.html
```

中文商店列表：

```text
https://x378320002.github.io/language-repeater-site/privacy.html
```

---

## 2. Ads / 广告

选择：

```text
No, my app does not contain ads.
```

---

## 3. App access / 应用访问权限

选择：

```text
All functionality is available without special access.
```

如果可以在 “Any other instructions / 其他说明” 里填写：

```text
核心功能需要用户选择设备上的本地音视频。打开应用后点击右上角“+”，授予媒体库权限后选择本地视频/音频，即可体验播放、A/B 复读、句子编辑、字幕和倍速。应用无账号、无登录。
```

---

## 4. Content ratings / 内容分级

- 类别：`Utility, Productivity, Communication, or Other`（或 `All other apps`）
- 联系邮箱：`wangzixu0612@gmail.com`
- 暴力、性、毒品、赌博、恐怖、血腥、脏话、广告、用户生成内容分享等：按实际情况选 **No**
- 应用本身不提供影视内容，只播放用户本地文件；没有上传/分享功能

---

## 5. Target audience and content / 目标受众

建议勾选：

```text
13-17
18+
```

不要勾选 13 岁以下（否则触发 Families policy）。如果问“是否专为儿童设计”，选择 **No**。

---

## 6. Data safety / 数据安全

### 总问题

- Does your app collect or share any of the required user data types? → **Yes**
- Is all of the user data collected by your app encrypted in transit? → **Yes**
- Do you provide a way for users to request that their data is deleted? → **Yes**

### 勾选的数据类型

| 类别 | 数据 | 来源 | 用途 | 是否共享 | 是否必需 |
|---|---|---|---|---|---|
| App activity | App interactions | Firebase Analytics | Analytics | 否 | 是 |
| App info and performance | Crash logs | Firebase Crashlytics | App functionality / Analytics | 否 | 是 |
| App info and performance | Diagnostics | Firebase Crashlytics | App functionality / Analytics | 否 | 是 |
| Device or other IDs | Device or other IDs | Firebase Analytics / Crashlytics | Analytics / App functionality | 否 | 是 |
| Location | Approximate location | Firebase Analytics（按隐私政策描述） | Analytics | 否 | 是 |

> 关于 Approximate location：现有隐私政策写了“可能从网络信息（如 IP）获取大致位置”。为保持一致，建议勾选。如果你确认实际不收集，也可以把隐私政策里的这句话删掉，再取消勾选；两者必须一致。
>
> 如果你保留 Advertising ID（见下一节），广告 ID 归在 **Device or other IDs** 里；用途选 Analytics，不选 Advertising/Marketing。

### 每一项的通用回答

- 收集方式：**Collected**（不是 Shared）
- 是否出售：**No**
- 是否用于广告/营销：**No**
- 是否用于 Analytics：**Yes**（Crash logs/Diagnostics 可同时选 App functionality）
- 是否必需：**Required**（应用没有关闭统计的开关）
- 是否加密传输：**Yes**
- 是否可请求删除：**Yes**，通过隐私政策中的邮箱 `wangzixu0612@gmail.com`
- 是否临时处理：**No**
- 是否涉及账号：**No**（应用没有账号体系）

### 不需要勾选的数据

- Personal info（姓名、邮箱、电话、地址等）
- Financial info
- Health and fitness
- Messages
- Photos and videos（媒体文件只在本地处理，不上传）
- Audio files（同上）
- Files and docs（同上）
- Calendar
- Contacts
- Web browsing
- App clips
- In-app search history
- Installed apps
- Other user-generated content
- Precise location

---

## 7. Advertising ID / 广告 ID

当前 AAB 的 Manifest 里有 `com.google.android.gms.permission.AD_ID`。二选一：

### 方案 A：保留（推荐，不用改代码）

```text
Does your app use advertising ID? → Yes
Purpose → Analytics
Is it used for advertising or marketing? → No
```

这和 “Contains ads = No” 不矛盾：广告 ID 只被 Firebase Analytics 使用，应用没有广告。

### 方案 B：移除（需要改代码）

先移除 Manifest 中的 AD_ID 权限和 Ads Services 权限，关闭 Firebase Analytics 的广告 ID 收集，重新构建 AAB 并上传；等声明刷新后再选 **No**。

当前不要直接选 No，否则权限和声明不一致。

---

## 8. Photo and video permissions / 照片和视频权限

如果保留现在的 MediaStore 媒体库（“+”按钮打开媒体库），选 **READ_MEDIA_VIDEO**，然后填：

### 核心功能说明

```text
本应用是本地音视频复读播放器。核心功能需要把设备上的视频作为媒体库展示，支持按文件夹筛选、多选加入播放列表，并对任意条目进行 A/B 复读。Android Photo Picker 只能返回一次选择的文件，无法提供持续的全库浏览、文件夹结构和跨会话播放列表；Storage Access Framework 需要用户逐个文件夹选择，无法实现应用内的媒体库体验。因此需要 READ_MEDIA_VIDEO。

应用只在本地读取和播放这些媒体文件，不会上传视频、音频或字幕到服务器。
```

### 如果表单问 READ_MEDIA_AUDIO

```text
READ_MEDIA_AUDIO 用于在媒体库中列出并播放本地音频文件。应用只在本地读取和播放，不会上传音频文件。
```

### 演示视频要展示

1. 点击右上角 “+”
2. 出现照片/视频权限弹窗
3. 允许
4. 看到媒体库/文件夹
5. 选中一个视频
6. 开始播放并展示 A/B 复读

> 如果你不想做这个声明：把 “+” 改成 SAF 的 `OpenMultipleDocuments`，移除 `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`，重新上传 AAB。这样表单会消失，但媒体库浏览会变成系统文件选择器。这个改动可以让我帮你做。

---

## 9. Foreground service permissions / 前台服务权限

选择 **Media playback / 媒体播放**。

### Description / 功能描述

```text
用户在应用中主动选择本地音视频并开始播放后，应用通过媒体播放前台服务在后台继续播放，以支持锁屏、通知栏、耳机/蓝牙按键的上一句、下一句、暂停和恢复控制。
```

### User impact / 用户影响

```text
如果系统延迟启动或中断该前台服务，后台播放会暂停，通知栏/锁屏媒体控制不可用；用户重新打开应用或点击播放即可恢复，不会丢失播放位置。
```

### Use case

```text
Continue audio or video playback from the background.
（在后台继续播放音频或视频）
```

### 演示视频要展示

1. 打开应用，选择自己的视频
2. 主动点击播放
3. 按 Home 键回到桌面
4. 播放继续，通知栏出现媒体控制
5. 最好再展示锁屏控制或耳机键切上一句/下一句

视频上传到 YouTube（不公开/仅链接可见）或 Google Drive（任何拿到链接的人可看），把链接粘贴到表单。

---

## 10. 其他声明

如果出现以下项目，按实际情况选择 **No**：

```text
News app → No
Government apps → No
Financial features → No
Health apps → No
Full-screen intent → No
COVID-19 contact tracing → No
Data deletion (account) → Not applicable / No
```

---

## 11. 提交前检查

- [ ] Privacy policy 已保存
- [ ] Ads = No
- [ ] App access = 所有功能可访问
- [ ] Content rating 问卷完成
- [ ] Target audience = 13+
- [ ] Data safety 提交
- [ ] Advertising ID = Yes/Analytics（或已移除权限并选 No）
- [ ] Photo and video permissions = READ_MEDIA_VIDEO 已声明 + 视频链接
- [ ] Foreground service permissions = Media playback 已声明 + 视频链接
- [ ] 其他声明 = No
