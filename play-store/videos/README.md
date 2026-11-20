# 审核演示视频录制说明

Play Console 的 **Foreground service permissions** 和 **Photo and video permissions** 表单会要求提供演示视频链接。视频不用上传到 Play，只要提供一个审核员能访问的链接。

## 1. 用哪个视频

建议录两个短视频（各 30–90 秒）：

1. **FGS / 后台播放视频**
   - 打开应用；
   - 选择自己的样例视频；
   - 主动点击播放；
   - 按 Home 回桌面；
   - 展示播放继续、通知栏媒体控制；
   - 最好再展示锁屏控制或耳机键切上一句。

2. **照片/视频权限视频**（如果保留 MediaStore 媒体库）
   - 点击右上角 “+”
   - 出现媒体权限弹窗；
   - 允许；
   - 看到媒体库/文件夹；
   - 选中一个视频；
   - 播放并展示 A/B 复读。

> 视频里不要出现电影、电视剧、动漫、腾讯视频等第三方内容。用你自拍的样例视频或公有领域素材。

## 2. 怎么录

- iPhone/Mac：QuickTime Player → 文件 → 新建屏幕录制；或手机自带录屏。
- Android：下拉通知栏 → 屏幕录制。
- 录完裁剪掉无关部分，控制在 2 分钟以内。

## 3. 上传到哪里

任选一种，确保审核员拿到链接能直接看：

- **YouTube**：上传为“不公开 / Unlisted”，复制链接；
- **Google Drive**：上传后共享设置为“任何拥有链接的人可查看”，复制链接；
- 不要用需要登录、需要申请权限、会过期的网盘链接。

## 4. 把链接填到哪里

- FGS：Play Console → **App content → Foreground service permissions** → 对应媒体播放声明里的 video link；
- 照片/视频权限：Play Console → **App content → Photo and video permissions** → 对应 READ_MEDIA_VIDEO 声明里的 video link。

## 5. 记录

录好后可以把链接记在这里，方便自己复制：

```text
FGS 演示视频：
照片/视频权限演示视频：
```
