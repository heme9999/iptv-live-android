# My IPTV 开发交接说明

交接基线：`v6.0.21` / commit `b758153`

GitHub：<https://github.com/heme9999/iptv-live-android>

最新 Release：<https://github.com/heme9999/iptv-live-android/releases/tag/v6.0.21>

## 1. 项目目标与当前状态

这是基于用户原有 IPTV APK 内置播放源重写的 Android 应用，统一品牌名为 **My IPTV**，由一个 Android 工程输出三个独立安装包：

- TCL 75T7K：包名 `com.heme.iptvlive.tcl`
- Sony BRAVIA：包名 `com.heme.iptvlive.sony`
- Pixel 手机：包名 `com.heme.iptvlive.pixel`

当前版本码 `621`，版本名 `6.0.21`，最低 Android 6.0（API 23），使用 Java 17、Android Gradle Plugin、AndroidX Media3/ExoPlayer。

当前已实现：

- 主页、直播、分类、设置。
- 内置 M3U 播放源与云端全球 CDN 订阅源，支持「🔄 一键刷新播放清单」无需升级 App 即可热重载。
- 支持手工「自定义添加 / 更换 M3U 播放源」与「一键恢复默认播放源」。
- ExoPlayer 播放内核全面支持跨协议重定向（HTTP ➔ HTTPS）、M3U8 格式自动探测与 iOS/APTV 原生请求头。
- TCL/Sony 遥控器焦点导航；选台后隐藏菜单，全屏播放；确定、菜单或返回键呼回菜单。
- Pixel 竖屏为上方视频、下方频道信息和频道列表；横屏全屏时点击画面呼出左侧频道菜单。
- 仅 Pixel 支持系统画中画（PiP）；退出/关闭小窗时硬件级 0 毫秒立即释放播放器与音频焦点。
- 频道条目异步显示连接延时或超时状态。
- GitHub Releases 在线检查、下载和安装对应设备 APK。
- 三端品牌与极简现代深蓝晶透图标接入。

## 2. 在新电脑恢复项目

推荐方式：

```bash
git clone https://github.com/heme9999/iptv-live-android.git
cd iptv-live-android
git checkout main
```

离线恢复方式（使用交接包内 `my-iptv-full-history.bundle`）：

```bash
git clone my-iptv-full-history.bundle iptv-live-android
cd iptv-live-android
git switch main
```

包内 `my-iptv-source-v6.0.13.zip` 是不含 Git 历史和密钥的纯源码快照，可用于快速审阅。

## 3. 本地构建

准备 JDK 17 和 Android SDK（compileSdk 35），然后执行：

```bash
./gradlew clean assembleDebug
./gradlew lintPixelDebug lintSonyDebug lintTclDebug
```

正式包需要签名环境变量：

```bash
export IPTV_KEYSTORE_PATH=/absolute/path/to/iptv-release.jks
export IPTV_KEYSTORE_PASSWORD='...'
export IPTV_KEY_ALIAS='...'
export IPTV_KEY_PASSWORD='...'
./gradlew assembleRelease
```

不要把 keystore、密码、GitHub token 或任何 Secret 提交到仓库。

当前正式签名证书 SHA-256：

`AA:1C:2F:A6:3F:0F:6E:26:78:4B:24:75:4D:E7:F6:A6:DC:EC:36:E7:A6:18:86:F1:35:94:1E:43:18:B8:C2:E6`

必须继续使用同一签名，否则已安装客户端无法覆盖升级。仓库 GitHub Actions 已配置签名 Secrets；换电脑不需要取得私钥即可通过标签触发云端正式构建。

## 4. 发布与在线更新

每次修改都必须同时递增 `app/build.gradle.kts` 中：

- `versionCode`
- `versionName`

发布流程：

```bash
git add <files>
git commit -m '说明'
git push origin main
git tag -a v6.0.14 -m 'My IPTV 6.0.14'
git push origin v6.0.14
```

推送 `v*` 标签后，`.github/workflows/release.yml` 会签名、构建并上传：

- `IPTV-Live-TCL.apk`
- `IPTV-Live-Sony.apk`
- `IPTV-Live-Pixel.apk`

应用按照 flavor 的 `UPDATE_ASSET` 选择安装包，资产名称不可随意改变。更新接口读取 `heme9999/iptv-live-android` 的 latest release。

发布后必须确认 GitHub Actions 成功、latest release 指向新标签、三份资产均存在。不要只推源码而不检查 Release。

## 5. 关键代码

- `app/build.gradle.kts`：版本、三个 flavor、包名、更新资产名、签名配置。
- `app/src/main/java/com/heme/iptvlive/MainActivity.java`：布局适配、导航、播放、横竖屏、电视遥控器、画中画。
- `app/src/main/java/com/heme/iptvlive/UpdateChecker.java`：GitHub Releases 检查、下载、安装。
- `app/src/main/java/com/heme/iptvlive/LatencyTester.java`：频道连接延时测试。
- `app/src/main/assets/channels.m3u`：当前内置播放源。
- `app/src/main/res/layout/activity_main.xml`：电视公用布局。
- `app/src/pixel/res/layout/activity_main.xml`：Pixel 专用布局。
- `app/src/pixel/AndroidManifest.xml`：仅 Pixel 开启画中画。
- `app/src/main/res/drawable-nodpi/my_iptv_icon.png`：当前应用图标。
- `.github/workflows/release.yml`：云端签名与 Release 发布。

## 6. 必须保持的产品约束

- TCL 和 Sony 使用电视交互；Pixel 使用手机交互。
- 画中画只能出现在 Pixel，不能添加到 TCL/Sony。
- 电视选台后菜单必须隐藏；遥控器确定、菜单或返回键可恢复。
- Pixel 横屏点击播放画面呼出左侧频道菜单。
- Pixel 竖屏在视频下方显示频道菜单。
- 退出应用不能继续后台播放，画中画除外。
- 分类优先顺序：国际资讯、日本放送、台湾主流、港澳中文/粤语、纪实自然、体育赛事、影视影院、央视核心；其他分类置后。
- 名称统一为 My IPTV。
- 在线更新必须使用相同包名、相同签名和对应 APK 资产名。

## 7. 测试清单

每次发布前至少完成：

1. `assembleDebug` 三 flavor 成功。
2. `lintPixelDebug`、`lintSonyDebug`、`lintTclDebug` 成功。
3. TCL/Sony 遥控器可清楚看见主菜单、频道、分类、设置控件焦点。
4. 电视选台后菜单隐藏；按确定/菜单/返回恢复。
5. Pixel 竖屏视频在上、频道菜单在下。
6. Pixel 横屏点击画面显示/隐藏左侧频道菜单。
7. Pixel Home 键进入画中画；电视无画中画。
8. 正常退出后声音停止。
9. 手动“检查软件更新”有即时反馈并能找到正确 flavor APK。
10. GitHub latest release、版本号、三份资产、签名证书均正确。

## 8. 已知注意事项

- `UpdateChecker.parseVersionCode()` 将语义版本映射成整数；继续沿用 `6.0.x` 时应让 `versionCode` 与解析结果一致，例如 `6.0.14 -> 614`。
- 部分直播源会失效或变慢，这是源站状态，不应直接判断为播放器 UI 缺陷。
- 项目曾在 macOS 增量构建缓存中出现重复 dex 临时文件；遇到类似错误先运行 `./gradlew clean` 再构建。
- GitHub Actions 当前会提示部分 action 的 Node 20 弃用警告，但工作流仍成功；后续可升级 action 版本，升级后必须重新验证签名发布。
- 原始参考 APK 不在仓库；当前播放源已经保存在 `channels.m3u`。

## 9. 给下一位 AI 助理的建议开场提示

> 继续开发 GitHub 仓库 heme9999/iptv-live-android。先完整阅读 HANDOFF.md、README.md、app/build.gradle.kts、MainActivity.java、UpdateChecker.java 和 release.yml。保持三个 flavor 的包名、签名与更新资产名不变。任何修改都要递增版本、从零编译三个 flavor、运行三个 Lint、推送 main 和新标签，并等待 GitHub Release 成功后再报告完成。不要在 TCL/Sony 增加画中画。不要提交密钥或 token。

