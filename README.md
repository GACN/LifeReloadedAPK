# 人生重开器 APK

一个轻量 Android WebView APK，把本地 HTML 人生模拟器打包成独立应用。

## 最新版本

- versionCode: 15
- versionName: 15.0-final-icon-tested
- 包名: `com.gaclove.lifereloadedapk`
- 应用名: 人生重开器

## 本版重点

- 新增多密度应用图标：`@mipmap/ic_launcher`
- 液态玻璃风格游戏界面
- 底部导航：游戏 / 存档 / 设置 / 日志
- 选择区改为一排数字按钮：1 / 2 / 3 / 4 / 5
- 自动从 AI 正文里抽取嵌入式 1-5 选项
- 开挂区改为页面内折叠块，不再悬浮遮挡
- 本地开挂支持“马上给我100万 / 一百万 / 百万 / 智力+3”等指令
- 存档、设置、日志、本地 fallback 均可用

## 已验证

本地做了 10 轮用户视角自动回归测试，覆盖：

- 新人生
- 1-5 选项提取
- 数字选择点击
- 选择后 fallback 事件推进
- 快速开挂：马上给我100万
- 快速开挂：智力+3
- 命名存档
- 设置页/游戏页切换
- 自动保存
- 渲染回归

APK 验证：

- v1/v2/v3 签名通过
- badging 可读取 versionCode/versionName/minSdk/targetSdk/icon
- APK 内包含多密度 `ic_launcher.png`
- APK 内 `assets/index.html` 检查通过

## 构建说明

当前项目是在 Android/Termux 环境下用最小 Android 工具链手工构建：

- `javac`
- `dx`
- `aapt`
- `zipalign`
- `apksigner`
- `patch_axml_modern.py`

`patch_axml_modern.py` 用于修补旧 Termux aapt 生成的二进制 Manifest，让现代 Android 属性如 versionCode、targetSdkVersion、exported、icon、roundIcon 能被系统正确识别。

## 当前 APK

最终产物已生成到本机：

- `/storage/emulated/0/Download/lifereloaded-final-icon-tested-v15.apk`
- `/storage/emulated/0/Music/每日归档/APK/lifereloaded-final-icon-tested-v15.apk`

SHA256:

`59114064194d9d71ef6591418fd6c2ac3485ccff641f40132b705f79842578a8`
