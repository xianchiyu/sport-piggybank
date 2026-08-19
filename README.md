# 运动存钱罐 Sport Piggybank 🏃💰

一个安卓 App，把运动和健康饮食行为转化为累积的金额。

## 规则概要

### 任务与奖励
| 任务 | 基础奖励 | 说明 |
|---|---|---|
| 运动（跑步/走路/跳绳） | 1~15铜 | 跑步>走路；**跑步**距离≥3km额外+5铜（走路无距离奖励）；跳绳200个=2铜/500个=4铜/800个=7铜 |
| 做早饭 | 3铜 | 自己做且相对清淡 |
| 简单吃晚饭 | 3铜 | 非正餐/轻食/无负担 |

### 连续加成
连续天数 → 倍率：≥14天(×1.8) ／ 7-13天(×1.5) ／ 3-6天(×1.2) ／ ≤2天(×1.0)

> 倍率计算向下取整（如 3铜×1.2=3.6→3铜）

### 币值体系
- 10铜 = 1银（¥0.1→¥1）
- 10银 = 1金（¥1→¥10）
- 自动合成：铜≥15 合10留5，银≥15 合10留5

### 惩罚（违规）
- 罚金：三角数递进 ¥10→30→60→100→150（15天周期重置），独立于币值，仅统计季度罚金总额
- 连续天数清零：违规任务的连续天数归零
- 自动检测：打开 App 时逐日检查，缺几天罚几天
- 每月3次社交豁免（仅限晚餐，免罚金且保留连续天数）

### 季度提现
季度末可提现，金额以当前币值余额为准（金×10+银×1+铜×0.1），不含罚金。提现后币值清零，收入/支出/罚金统计也清零。

## 技术栈
- Kotlin WebView壳（~3MB APK）
- 纯 HTML/CSS/JS 前端（无框架）
- 纯离线，无网络请求
- SharedPreferences 持久化（MVP阶段，后续可升级 SQLite）
- GitHub Actions CI 自动编译

## 构建
本仓库**本地零工具链**（不装 Android Studio / JDK / SDK），编译通过 GitHub Actions 自动完成：

```bash
# 1. 把代码 push 到 GitHub
git add . && git commit -m "..." && git push

# 2. 在仓库 Actions → Build APK 页面等待编译完成，下载 Artifacts（app-debug.apk）
```

> 如需在本地编译：先执行 `gradle wrapper`（生成 gradle-wrapper.jar），再 `./gradlew assembleDebug`。
> 注意：仓库未提交 `gradle-wrapper.jar`，由 CI 在构建时现场生成，不影响云端编译。

## 已知限制
- 卸载重装不保留历史数据（SharedPreferences 随卸载清除），更新 APK 请卸载重装后重新开始记录
- 季度统计中收入/支出/罚金均为独立统计，提现金额只看币值余额，不含罚金

## 仓库说明
- 仓库仅含可编译源码；开发辅助文档（项目总结.md、校核报告*.md）与 preview/ 已由 .gitignore 忽略，不进 GitHub。
- 未提交 gradle-wrapper.jar（CI 构建时现场生成），不影响云端编译。

## 目录结构
```
sport-piggybank/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/xianchiyu/piggybank/
│       │   ├── MainActivity.kt    # WebView壳 + JS桥接
│       │   └── PiggyCore.kt       # 数据模型 + 币值计算 + 自动违规检测
│       ├── assets/www/
│       │   ├── index.html         # SPA前端
│       │   ├── style.css
│       │   └── app.js
│       └── res/
│           ├── drawable/          # 启动页背景 splash_bg.jpg + splash_window_bg.xml
│           ├── mipmap-*/           # 各密度 ic_launcher.png 图标
│           └── values/            # colors.xml + themes.xml（含启动页 windowBackground）
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties   # CI 构建必需
├── .github/workflows/build.yml                # CI 编译脚本
└── .gitignore
```
> 注：`app/` 下另有一个 `settings.gradle` 冗余副本（Gradle 仅读取根目录那份），不参与构建。
