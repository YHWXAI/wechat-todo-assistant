# 微信待办助手

Android 13 及以上适用。微信待办助手支持从微信通知和当前可见聊天中提取待办，并提供本地提醒和应用内更新。

## 安装和使用

从 [GitHub 下载最新版 APK](https://raw.githubusercontent.com/YHWXAI/wechat-todo-assistant/updates/WeChatTodo-debug.apk)。更新时直接覆盖安装，待办数据库会保留。

点首页右上角的「设置」，可以查看权限、管理消息监测和聊天自动识别，以及安装应用更新。

- **消息监测**　开启通知读取权限，在「消息监测」选择微信、企业微信、QQ、钉钉、飞书、Telegram 或 WhatsApp。微信默认开启，其余应用需手动开启。添加要监测的群并填写自己在群里的昵称；有重名时可设为先确认。只有明确给本人或全员的有效群任务才从通知保存。可添加可信私聊，以便识别缺少可靠会话类型的通知。通知需包含消息正文，规则以中文指令为主。
- **自动识别微信聊天**　在助手中开启「自动识别」并授予无障碍权限。打开或滚动已添加的微信会话时，助手会提示你确认从可见消息中识别出的候选待办。点「加入待办」保存，或点「跳过」。同一聊天里已处理的相同内容会去重。缺少明确年月日的屏幕消息可收录为待办，截止时间留空。其他通讯应用通过通知监测。
- **待办分类**　首页按日常待办和工作待办分组。分类在手机上根据待办内容判断，内容模糊时参考明确的工作群名，其余归入日常待办。已有待办也会分组显示。
- **到期提醒**　开启通知权限。安卓省电模式可能推迟提醒。
- **应用更新**　打开应用时会检查 GitHub 上的版本。发现新版后，点「检查并下载更新」，应用校验 APK 后打开安卓安装界面。首次更新需要授权本应用安装更新。检查更新时不会发送聊天或待办内容。

## 构建与发布

本项目使用 JDK 17、Gradle 8.9 和 Android SDK 35。配置 Android SDK 路径后运行 `gradlew.bat :app:assembleDebug :app:lintDebug`。

发布时提高 `versionCode` 和 `versionName`，使用与已安装版本相同的签名密钥。将签名 APK 发布到 `updates` 分支的 `v<版本>/WeChatTodo-debug.apk`，并在同一个提交更新分支根目录的 `WeChatTodo-debug.apk` 和 `updates.json`。版本文件包含 `version`（例如 `v0.2.1`）、`size`（字节数）及 `digest`（以 `sha256:` 开头的 SHA-256）。旧版本的文件路径应保留，供仍拿到缓存版本信息的手机使用。请妥善备份原签名密钥。

从 v0.2.8 起，更新渠道使用与测试机上重新安装的 v0.2.7 一致的签名。仍使用旧签名的 v0.2.6 及更早版本需要在迁移数据后重新安装。旧版下载路径继续保留。

已有本地签名 APK 时，可在配置 Android SDK 后运行 `python3 scripts/prepare-update.py <已签名的APK> <updates分支检出目录> --already-signed`。脚本核对 APK 后生成更新文件，将这三个变更文件在同一个提交推送到 `updates` 分支即可。

## 云端签名与发布

GitHub Actions 中的 **Publish signed Android update** 工作流可在 `main` 分支手动运行，使用仓库的加密 Actions Secrets 签名，再更新 `updates` 分支。工作流先构建并运行 Lint，随后检查当前更新渠道的签名指纹、应用标识和递增的版本号。任一检查失败时不会推送更新，也不会替换历史版本。

需要启用云端签名时，可以在持有当前更新渠道签名密钥的电脑上用 PowerShell 运行下面的上传脚本。脚本会下载并校验 GitHub 官方便携版工具，必要时引导你在浏览器中登录 GitHub。

```powershell
powershell -NoProfile -File .\scripts\upload-signing-key.ps1
```

脚本自动查找 `.android-tools` 下唯一的 `debug.keystore`。若有多个文件或文件在其他位置，用 `-KeystorePath '原签名文件的完整路径'` 指定。脚本通过 GitHub CLI 将以下值加密后上传至仓库 Actions Secrets，不将密钥提交到仓库，也不将密码放在命令参数或输出中：

- `ANDROID_KEYSTORE_BASE64`：当前更新渠道签名文件的 Base64 内容。
- `ANDROID_KEYSTORE_PASSWORD`：密钥库密码。
- `ANDROID_KEY_ALIAS`：签名条目别名。
- `ANDROID_KEY_PASSWORD`：该条目的密码。

标准 Android 调试密钥的别名通常为 `androiddebugkey`，密钥库和条目密码通常均为 `android`；自定义密钥应使用原来设置的值。不要在聊天、Issue、日志或普通仓库文件中粘贴这些值。Secrets 设置页位于仓库 **Settings → Secrets and variables → Actions**；不要使用普通 Variables 存储密钥。

独立下载运行脚本时会弹出文件选择框。已经确认是标准调试密钥的，可添加 `-UseStandardDebugKey` 使用上述默认值。上传成功后脚本会启动 **Verify hosted Android signing key** 工作流，实际用托管密钥签署 APK 并核对原始证书，不发布新版本。只有该验证运行成功才报告托管验证成功；权限、密码或签名不符时会报错。此操作仍需要使用拥有仓库 Secrets 管理权限的 GitHub 账号完成一次浏览器授权。

如果云端的 GitHub 集成可以推送代码或运行 Actions，但 Secrets API 返回 `Resource not accessible by integration`，仍缺少管理 Secrets 的权限；调整工作流的 `contents` 权限无法补齐这一权限。可由仓库管理员升级 GitHub App 授权，或创建仅允许访问本仓库的 fine-grained PAT，授予仓库 `Secrets: Read and write`、`Actions: Read and write` 和 `Contents: Read-only` 权限。Token 应通过云环境的安全 HTTP 密钥设置提供（例如变量名 `REPO_ACTIONS_TOKEN`，目标域名 `api.github.com`），不得发送到聊天或写入仓库。使用前先检查现有绑定和权限，上传时通过 stdin 传递秘密值。

两个工作流均通过 `scripts/setup-android.sh` 初始化 SDK，不依赖镜像预先将 `sdkmanager` 加入 PATH。命令行工具缺失时从 Google 官方下载固定版本并校验 SHA-256。工作流修复后应从最新 `main` 发起一次新的验证运行；重跑旧记录可能继续使用旧版工作流。

密钥只在工作流签名阶段恢复到临时目录，步骤结束后清理，不上传为构建产物。云端流程完成并确认手机可以覆盖更新之后，可移除原电脑上的副本；另在密码管理器或加密离线存储中保留可恢复的备份，避免丢失后无法继续更新。上传脚本不会自动删除本地文件。
