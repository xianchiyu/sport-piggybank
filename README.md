<<<<<<< HEAD
# 运动存钱罐 Sport Piggybank 🏃💰

一个安卓 App，把运动和健康饮食行为转化为虚拟货币。

## 规则概要

### 任务与奖励
| 任务 | 基础奖励 | 说明 |
|---|---|---|
| 运动（跑步/走路） | 1~15铜 | 跑步>走路，距离≥3km额外+5铜 |
| 做早饭 | 3铜 | 自己做且相对清淡 |
| 简单吃晚饭 | 3铜 | 非正餐/轻食/无负担 |

### 连续加成
连续天数 → 倍率：≤2天(×1.0) → 3~6天(×1.2) → 7~13天(×1.5) → ≥14天(×1.8)

### 币值体系
- 10铜 = 1银（¥0.1→¥1）
- 10银 = 1金（¥1→¥10）
- 自动合成：铜≥15 合10留5，银≥15 合10留5

### 双轨惩罚（违规）
- 现金罚金：三角数递进 ¥10→30→60→100→150（15天周期）
- 积分惩罚：连清该任务连续天数+连带扣回
- 每月3次社交豁免

### 季度提现
季度末生成余额报告，手动转入余额宝/货基。

## 技术栈
- Kotlin WebView壳（~3MB APK）
- 纯 HTML/CSS/JS 前端（无框架）
- SQLite（SharedPreferences 持久化）
- AlarmManager 本地通知

## 构建
```bash
# 本地构建
./gradlew assembleDebug

# 或：push 到 GitHub，Actions 自动编译
# 下载 apk：Actions → Build APK → Artifacts
```

## 目录结构
```
sport-piggybank/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/xianchiyu/piggybank/
│       │   ├── MainActivity.kt    # WebView壳 + JS桥接
│       │   └── PiggyCore.kt       # 数据模型 + 工具类 + 通知
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
=======
# sport-piggybank
运动存钱罐 - 用自己的运动赚取金币存入储蓄罐
>>>>>>> 4cac5fb6ef116860aa85650ea84a8d374ec9cb05
