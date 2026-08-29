# IPTV Live Android

基于原 APK 内置播放源重写的 Android IPTV 客户端，同时输出 TCL 75T7K、Sony BRAVIA 和 Pixel 三个版本。

## 特性

- AndroidX Media3 / ExoPlayer 播放 HLS、FLV 等直播流
- TCL / Sony 电视遥控器焦点导航，Pixel 手机触控布局
- 主页、直播、频道分类、设置四个一级入口
- 可选择启动页面、自动播放行为，并记忆最近观看频道
- Pixel 选择频道后自动隐藏菜单进入全屏，返回键恢复菜单
- Pixel 全屏播放时轻触画面可从左侧弹出频道菜单并直接切台
- 仅 Pixel 手机版支持播放时按 Home 进入系统画中画
- 频道条目异步显示连接延时或超时状态
- TCL/Sony 选择频道后自动全屏，确定、菜单或返回键恢复频道菜单
- Activity 离开前台时立即停止并释放播放器，避免后台继续播放
- 启动时通过 GitHub Releases 检查更新，按设备下载对应 APK
- 最低 Android 6.0（API 23）

## 构建

```bash
./gradlew assembleRelease
```

构建产物分别来自 `tclRelease`、`sonyRelease`、`pixelRelease`。发布 Release 时请将文件命名为：

- `IPTV-Live-TCL.apk`
- `IPTV-Live-Sony.apk`
- `IPTV-Live-Pixel.apk`

Release 标签使用 `v6.0.1` 形式；应用会将其转换为版本码并与本地版本比较。

GitHub Actions 发布前需配置 `SIGNING_KEY_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 四个仓库 Secret。必须始终使用同一签名，否则 Android 会拒绝覆盖更新。

## 更新仓库

默认更新源为 `heme9999/iptv-live-android`。如仓库名不同，请修改 `app/build.gradle.kts` 中的 `GITHUB_REPO`。
