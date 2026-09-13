# 记一笔 · 收支记账

一款完全离线的 Android 记账应用，以**周**为周期管理收支，支持银行卡短信与支付通知自动记账。

基于 Kotlin + Jetpack Compose（Material Design 3）原生开发，不使用任何 WebView 或跨端框架。

---

## 功能特性

### 记账
- **周周期**：以周一至周日为一周，支持左右切换查看历史周（不可浏览未来周）
- **20 个分类**：12 类支出（餐饮、交通、购物、居家等）+ 8 类收入（工资、奖金、理财等），各带独立配色
- **金额输入**：调用系统输入法，实时校验（限两位小数、自动去前导零）
- **账户归属**：微信 / 支付宝 / 银行卡 / 信用卡 / 现金 / 其他

### 预算
- **周预算**与**月预算**双轨并行，进度条实时反映使用比例，超支变色提示

### 自动记账
- **银行卡短信**：解析银行交易短信，自动提取金额、方向、卡号尾号并归类
- **支付通知**：监听微信 / 支付宝 / 银行 App 的推送通知，自动生成记录
- 两者均支持开关控制，去重机制避免重复记账

### 统计
- 分类占比环形图（带补间动画）
- 分类支出排行榜
- 最近 6 周收支柱状图

### 数据
- 本地 JSON 文件存储，**零上传、零网络依赖**（除检查更新外）
- 导入微信 / 支付宝 CSV 账单（自动识别表头、跳过转账与退款）
- 导出 JSON 备份 / CSV 明细，支持跨设备合并去重

### 其他
- **昨日收支提醒**：可自定义每日推送时间
- **应用内更新**：检查 GitHub Releases 并直接下载安装
- Material 3 动态配色、深色模式、毛玻璃模糊效果

---

## 安装

前往 [Releases](https://github.com/lyl-creator/jiyibi/releases/latest) 下载最新的 `app-release.apk`，在手机上安装。

> 首次安装需允许「安装未知来源应用」。
> 系统要求：Android 7.0（API 24）及以上。

---

## 权限说明

| 权限 | 用途 |
|------|------|
| `RECEIVE_SMS` / `READ_SMS` | 解析银行交易短信实现自动记账（可选，需手动授权） |
| 通知使用权 | 读取微信 / 支付宝交易通知实现自动记账（可选，需在系统设置授权） |
| `POST_NOTIFICATIONS` | 每日昨日收支提醒（Android 13+） |
| `INTERNET` | 仅用于检查更新与下载新版 APK |
| `REQUEST_INSTALL_PACKAGES` | 应用内更新时触发安装 |

所有权限均为可选。不授予任何权限时，应用可作为纯手动记账工具正常使用，数据完全保存在本机。

---

## 项目结构

```
.
├── android/                      # 原生 Android 应用
│   ├── app/
│   │   ├── build.gradle
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/jiyibi/ledger/
│   │       │   ├── MainActivity.kt
│   │       │   ├── data/         # 数据模型、分类、存储、账单解析
│   │       │   ├── util/         # 日期、金额工具
│   │       │   ├── ui/           # Compose 界面
│   │       │   │   ├── AppRoot.kt
│   │       │   │   ├── LedgerViewModel.kt
│   │       │   │   ├── components/
│   │       │   │   ├── screens/
│   │       │   │   └── theme/
│   │       │   ├── service/      # 短信与通知监听
│   │       │   ├── worker/       # 每日提醒定时任务
│   │       │   └── update/       # 应用内更新
│   │       └── res/
│   ├── build.gradle
│   └── settings.gradle
├── index.html                    # 网页版（PWA，作为轻量替代）
├── app.css
├── app.js
├── manifest.webmanifest
├── sw.js
├── icons/                        # 应用图标
├── update-site/                  # 更新服务页（历史留存）
├── version.json                  # 版本清单（随 Release 发布）
└── tools/                        # 图标生成、冒烟测试脚本
```

---

## 构建

环境要求：

- JDK 17
- Android SDK（platform 34、build-tools 34.0.0）
- Gradle 8.7

```bash
cd android
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
gradle assembleRelease
```

产出的 APK 位于 `android/app/build/outputs/apk/release/app-release.apk`。

> 注意：Android Gradle Plugin 不支持非 ASCII 项目路径。若工程位于中文目录下，请先复制到纯英文路径再构建。

技术版本：AGP 8.5.2 · Kotlin 1.9.24 · Compose BOM 2024.06.00 · Gradle 8.7 · minSdk 24 · targetSdk 34

---

## 应用内更新

应用的更新源指向本仓库的 Releases：

```
https://github.com/lyl-creator/jiyibi/releases/latest/download/version.json
```

发布新版本时，创建 Release 并上传两个附件即可：

- `version.json` —— 版本清单
- `app-release.apk` —— 安装包

`version.json` 格式：

```json
{
  "versionCode": 26000,
  "versionName": "2.6.0",
  "downloadUrl": "https://github.com/lyl-creator/jiyibi/releases/latest/download/app-release.apk",
  "changelog": "本次更新内容"
}
```

用户打开「设置 → 关于 → 检查更新」即可获取新版本。

---

## 隐私

- 所有账目数据仅保存在设备本地（应用私有目录 `ledger.json`）
- 不上传任何数据到服务器
- 短信与通知内容仅在设备本地解析，解析后立即丢弃原文，仅保留金额、分类等记账必要字段
- 网络权限仅用于检查更新

---

## 许可

个人项目，可自由参考使用。
