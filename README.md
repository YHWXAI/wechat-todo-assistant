# 微信待办助手

Android 13 及以上适用。V0.2 保留微信通知采集、中文待办提取和本地提醒，并加入主动读取当前聊天、应用内检查更新。

## 安装和使用

从 [GitHub Releases](https://github.com/YHWXAI/wechat-todo-assistant/releases/latest) 下载 `WeChatTodo-debug.apk`。更新 V0.1 时直接覆盖安装，待办数据库会保留。

- **微信通知**　开启通知读取权限。助手用通知里包含的文字识别待办。
- **读取当前聊天**　在助手里开启聊天界面读取。点「准备读取」后，90 秒内切到微信聊天页，点右上方「读取当前聊天」，再返回助手勾选要保存的待办。微信必须向 Android 提供该页文字。相对日期按读取当天计算。
- **到期提醒**　开启通知权限。安卓省电模式可能推迟提醒。
- **应用更新**　点「检查并下载更新」。应用从 GitHub 下载正式版本，校验文件和签名，再打开安卓安装界面。首次更新需要授权本应用安装更新。检查更新时不会发送聊天或待办内容。

## 构建与发布

本项目使用 JDK 17、Gradle 8.9 和 Android SDK 35。配置 Android SDK 路径后运行 `gradlew.bat :app:assembleDebug :app:lintDebug`。

发布时提高 `versionCode` 和 `versionName`，使用与已安装版本相同的本地签名密钥。将正式 Release 的标签设为与应用版本一致的格式（例如 `v0.2.1`），上传名为 `WeChatTodo-debug.apk` 的签名文件，并将该 Release 标为最新。请在本地妥善保存签名密钥。
