# 微信待办助手

Android 13 及以上适用。V0.2 保留微信通知采集、中文待办提取和本地提醒，并加入主动读取当前聊天、应用内检查更新。

## 安装和使用

从 [GitHub 下载最新版 APK](https://raw.githubusercontent.com/YHWXAI/wechat-todo-assistant/updates/WeChatTodo-debug.apk)。更新时直接覆盖安装，待办数据库会保留。

- **微信通知**　开启通知读取权限。助手用通知里包含的文字识别待办。
- **读取当前聊天**　在助手里开启聊天界面读取。点「准备读取」后，90 秒内切到微信聊天页，点右上方「读取当前聊天」，再返回助手勾选要保存的待办。微信必须向 Android 提供该页文字。相对日期按读取当天计算。
- **到期提醒**　开启通知权限。安卓省电模式可能推迟提醒。
- **应用更新**　点「检查并下载更新」。应用读取 GitHub 上的静态版本文件，校验下载的 APK 后打开安卓安装界面。首次更新需要授权本应用安装更新。检查更新时不会发送聊天或待办内容。

## 构建与发布

本项目使用 JDK 17、Gradle 8.9 和 Android SDK 35。配置 Android SDK 路径后运行 `gradlew.bat :app:assembleDebug :app:lintDebug`。

发布时提高 `versionCode` 和 `versionName`，使用与已安装版本相同的本地签名密钥。将签名 APK 发布到 `updates` 分支的 `v<版本>/WeChatTodo-debug.apk`，并在同一个提交更新分支根目录的 `WeChatTodo-debug.apk` 和 `updates.json`。版本文件包含 `version`（例如 `v0.2.1`）、`size`（字节数）及 `digest`（以 `sha256:` 开头的 SHA-256）。旧版本的文件路径应保留，供仍拿到缓存版本信息的手机使用。请在本地妥善保存签名密钥。
