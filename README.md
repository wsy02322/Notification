# 消息监控

纯本地 Android 应用：勾选微信 / 闲鱼（小红书、抖音可选），按关键词命中后循环震动并播放提醒音乐，直到你在应用内或通知栏点「我知道了」。

## 下载 APK

请装 **v1.3**。手机浏览器打开下面链接即可下载（debug 签名，需允许「未知来源」）：

**[notification-monitor-v1.3-debug.apk](https://github.com/wsy02322/Notification/raw/cursor/android-notification-monitor-6ff1/apk/notification-monitor-v1.3-debug.apk)**

仓库内路径：[apk/notification-monitor-v1.3-debug.apk](apk/notification-monitor-v1.3-debug.apk)

测完后在应用里点 **「查看 / 导出测试日志」** → **导出/分享** 或 **复制全部**，把日志发给我排查。

## 功能

- 勾选要监控的 App（按包名过滤）
- 多个关键词，不区分大小写，用换行或逗号分隔
- 关键词出现在 App 名、发送人或正文任一处即提醒（OR）
- 关键词留空：所选 App 的每条通知都提醒
- 命中后循环震动 + 循环音乐，确认后才停止
- 确认方式：全屏确认页大按钮，或通知栏「我知道了」

## 使用前必开

1. **通知使用权**（设置 → 通知 → 通知使用权）
2. **通知权限**
3. **忽略电池优化 / 无限制**
4. 国产机再开 **自启动、后台运行**，最近任务 **锁定**
5. Android 14+ 建议打开 **全屏意图**，否则熄屏可能弹不出确认页，但仍可通过通知栏确认，声震会继续

被监控的微信、闲鱼也需要打开系统通知。关闭「显示消息详情」后，发送人/正文关键词会失效，勾选该 App 仍会响。

## 构建

需要 JDK 17+ 与 Android SDK（`compileSdk 35`）。

```bash
export ANDROID_HOME=/path/to/Android/sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

生成的安装包在 `app/build/outputs/apk/debug/app-debug.apk`，仓库里已放一份可直接下载的副本：`apk/notification-monitor-v1.3-debug.apk`。本应用不会上架，请侧载安装。

荣耀 / 华为请务必打开「后台弹出界面」和「应用启动管理」，熄屏前确认通知栏一直有「正在监听」。
