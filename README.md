# 运动存钱罐 Sport Piggybank 🏃💰

一个安卓 App，把运动和健康饮食行为转化为虚拟货币。

## 规则概要

### 任务与奖励
| 任务 | 基础奖励 | 说明 |
|---|---|---|
| 运动（跑步/走路/跳绳） | 1~15铜 | 跑步>走路，距离≥3km额外+5铜；跳绳200个=2铜/500个=4铜/800个=7铜 |
| 做早饭 | 3铜 | 自己做且相对清淡 |
| 简单吃晚饭 | 3铜 | 非正餐/轻食/无负担 |
| 雨天室内运动 | 3铜 | 自动检测雨天或手动切换，固定6000步 |

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
- SharedPreferences 持久化（MVP阶段，后续可升级 SQLite）
- GitHub Actions CI 自动编译

## 构建
```bash
# 本地构建
./gradlew assembleDebug

# 或：push 到 GitHub，Actions 自动编译
# 下载 apk：Actions → Build APK → Artifacts
```

## 已知限制
- 卸载重装不保留历史数据（SharedPreferences 随卸载清除），更新 APK 请卸载重装后重新开始记录
- 季度统计中收入/支出/罚金均为独立统计，提现金额只看币值余额，不含罚金

## 目录结构
```
sport-piggybank/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/xianchiyu/piggybank/
│       │   ├── MainActivity.kt    # WebView壳 + JS桥接
│       │   └── PiggyCore.kt       # 数据模型 + 币值计算 + 自动违规检测 + 天气查询
│       ├── assets/www/
│       │   ├── index.html         # SPA前端
│       │   ├── style.css
│       │   └── app.js
│       └── res/values/themes.xml
├── build.gradle
├── settings.gradle
├── gradle.properties
└── .github/workflows/build.yml    # CI编译
```
