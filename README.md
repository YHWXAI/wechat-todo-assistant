# 微信待办助手

Android 13 及以上适用。微信待办助手支持从微信通知和当前可见聊天中提取待办，并提供本地提醒和应用内更新。

## 安装和使用

从 [GitHub 下载最新版 APK](https://raw.githubusercontent.com/YHWXAI/wechat-todo-assistant/updates/WeChatTodo-debug.apk)。更新时直接覆盖安装，待办数据库会保留。

- **微信通知**　开启通知读取权限。助手用通知里包含的文字识别待办。
- **自动识别微信聊天**　在助手中开启「自动识别」并授予无障碍权限。打开或滚动微信聊天时，助手从可见的收到消息中提取待办。微信须向 Android 提供页面文字。同一聊天里已收录的相同内容会去重。缺少明确年月日的屏幕消息可收录为待办，截止时间留空。
- **到期提醒**　开启通知权限。安卓省电模式可能推迟提醒。
- **应用更新**　点「检查并下载更新」。应用读取 GitHub 上的静态版本文件，校验下载的 APK 后打开安卓安装界面。首次更新需要授权本应用安装更新。检查更新时不会发送聊天或待办内容。

## 构建与发布

本项目使用 JDK 17、Gradle 8.9 和 Android SDK 35。配置 Android SDK 路径后运行 `gradlew.bat :app:assembleDebug :app:lintDebug`。

发布时提高 `versionCode` 和 `versionName`，使用与已安装版本相同的本地签名密钥。将签名 APK 发布到 `updates` 分支的 `v<版本>/WeChatTodo-debug.apk`，并在同一个提交更新分支根目录的 `WeChatTodo-debug.apk` 和 `updates.json`。版本文件包含 `version`（例如 `v0.2.1`）、`size`（字节数）及 `digest`（以 `sha256:` 开头的 SHA-256）。旧版本的文件路径应保留，供仍拿到缓存版本信息的手机使用。请在本地妥善保存签名密钥。
