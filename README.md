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
- **明细排序**：同一天内按记账时间倒序排列，最新记录显示在最上方

### 预算
- **周预算**与**月预算**双轨并行，进度条实时反映使用比例，超支变色提示

### 自动记账
- **银行卡短信**：解析银行交易短信，自动提取金额、方向、卡号尾号并归类
- **支付通知**：监听微信 / 支付宝 / 云闪付 / 京东金融 / 美团 / 数字人民币及各大银行 App 的推送通知，自动生成记录
  - 内置 20+ 常用应用白名单，并对未收录的应用按名称特征（银行、信用社、农商、银联、支付、钱包、信用卡等）启发式识别
  - 金额提取支持四级匹配（货币符号 / 「元」后缀 / 关键词前缀 / 小数兜底），并排除卡号尾号等非金额数字
- **实时刷新**：后台写入记录后通过进程内事件总线通知界面，返回应用或停留在应用内时数据立即更新，无需手动刷新
- 两者均支持开关控制，去重机制避免重复记账

### 统计
- 分类占比环形图（带补间动画）
- 分类支出排行榜
- 最近 6 周收支柱状图

### 数据
- 本地 JSON 文件存储，**零上传、零网络依赖**（除检查更新外）
- 采用「临时文件 + 原子重命名」写入，避免后台自动记账与界面读写并发导致的数据损坏
- 导入微信 / 支付宝 CSV 账单（自动识别表头、跳过转账与退款）
- 导出 JSON 备份 / CSV 明细，支持跨设备合并去重

### 其他
- **记账时间**：新增记录时自动附带记账时刻，明细列表与记账面板均显示 `HH:mm`
- **外观切换**：支持「跟随系统 / 白天 / 夜间」三种模式，并可切换 7 套主题色（薰衣草、晴空蓝、青草绿、暖阳橙、蔷薇粉、静谧青、咖啡棕）
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
| 通知使用权 | 读取微信 / 支付宝 / 银行 App 交易通知实现自动记账（可选，需在系统设置授权） |
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
│   │       │   ├── data/         # 数据模型、分类、存储、事件总线、账单解析
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
├── update-site/                  # 旧版更新中转站（已弃用，仅为 ≤2.7.0 过渡保留）
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

该地址是应用内唯一默认更新源（常量 `DEFAULT_UPDATE_URL`）。历史版本若存有旧的过渡桥地址（`weekly-ledger.app.workbuddy.host`），会在读取配置时自动迁移为上述 GitHub 地址。

发布新版本时，创建 Release 并上传两个附件即可：

- `version.json` —— 版本清单
- `app-release.apk` —— 安装包

`version.json` 格式：

```json
{
  "versionCode": 28000,
  "versionName": "2.8.0",
  "downloadUrl": "https://github.com/lyl-creator/jiyibi/releases/latest/download/app-release.apk",
  "changelog": "本次更新内容"
}
```

用户打开「设置 → 关于 → 检查更新」即可获取新版本。

> 过渡站：`update-site/` 曾作为 weekly-ledger 更新源的部署目录，现已弃用。为让存量旧版本（≤2.7.0）仍能收到升级提示，可将该目录留作中转，但其内容仅需保持指向 GitHub Releases 的最新版本号。

---

## 隐私

- 所有账目数据仅保存在设备本地（应用私有目录 `ledger.json`）
- 不上传任何数据到服务器
- 短信与通知内容仅在设备本地解析，解析后立即丢弃原文，仅保留金额、分类等记账必要字段
- 网络权限仅用于检查更新

---

## 许可

个人项目，可自由参考使用。
