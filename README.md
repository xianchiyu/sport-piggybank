# 运动存钱罐 Sport Piggybank 🏃💰

一个安卓 App，把运动和健康生活习惯转化为累积的金额。

## 规则概要

### 五项日常任务

| 任务 | 基础奖励 | 说明 |
|---|---|---|
| 运动（跑步/走路/跳绳） | 1~15铜 | 跑步>走路；**跑步**距离≥3km额外+5铜（走路无距离奖励）；跳绳200个=2铜/500个=4铜/800个=7铜 |
| 做早饭 | 3铜 | 自己做且相对清淡 |
| 简单吃晚饭 | 3铜 | 非正餐/轻食/无负担 |
| 金刚功 | 3铜 | 不计入违规检测，不计连续清零，只累计次数 |
| 长寿功 | 3铜 | 与早饭同规则，计入违规检测 |

### 连续加成
连续天数 → 倍率：≥14天(×1.8) ／ 7-13天(×1.5) ／ 3-6天(×1.2) ／ ≤2天(×1.0)

> 倍率计算向下取整（如 3铜×1.2=3.6→3铜）

### 卡片等级（打卡边框）

每项任务独立计数，一轮从 0 开始，每天最多累计 1 次。卡片边框用颜色和流动效果表示当前等级：

| 等级 | 累计次数 | 边框表现 |
|---|---|---|
| 未打卡 | — | 9px 灰色边框 |
| 白卡 | 0 – 2 | 冷银白，缓慢明暗流动 |
| 铜卡 | 3 – 6 | 暖铜哑光流动 |
| 银卡 | 7 – 14 | 冷蓝银金属反光 |
| 金卡 | 15 – 24 | 暖金明暗起伏 |
| 闪卡 | 25 + | 全息彩虹，顺时针绕行 |

- **满一轮**：单项累计满 30 次，运动/早饭/晚饭/长寿功各 +1 金币，计数归零重来；金刚功满 30 次同样重来，但不兑金
- **断签**：除金刚功外，漏一天该项计数归零
- **展示**：边框在卡片内侧 9px，卡面保持各任务的身份底色；图标与字号随屏幕自适应

### 币值体系
- 10铜 = 1银（¥0.1→¥1）
- 10银 = 1金（¥1→¥10）
- 自动合成：铜≥15 合10留5，银≥15 合10留5

### 惩罚（违规）
- 罚金：三角数递进 ¥10→30→60→100→150（15天周期重置），独立于币值，仅统计季度罚金总额
- 连续天数清零：违规任务的连续天数归零
- 自动检测：打开 App 时逐日检查，缺几天罚几天
- 每月3次社交豁免（仅限晚餐，免罚金且保留连续天数）
- 金刚功不进违规检测

### 季度提现
季度末可提现，金额以当前币值余额为准（金×10+银×1+铜×0.1），不含罚金。提现后币值清零，收入/支出/罚金统计也清零。

### 导入历史数据
重装或更新后余额清零时使用。在「我的」页输入铜币总数和罚金总额，系统按自动合并规则拆分成金币/银币/铜币。**当前已有任何非零数据时导入入口自动锁定**，避免误操作冲掉现有记录。

## 技术栈
- Kotlin WebView壳（~3MB APK）
- 纯 HTML/CSS/JS 前端（无框架）
- 纯离线，无网络请求
- SharedPreferences 持久化
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

## 前端实现要点

- **正方形卡片**：不用 `aspect-ratio` 或 `padding-top`（老 WebView 不支持或计算基准不对），改由 JS 在页面加载、窗口缩放、图片加载完成时读取卡片实际宽度，同步设置高度
- **边框旋转**：底层放一个 300% 尺寸的 `conic-gradient` 圆，中心对齐卡片后整体旋转，内层用不透明面板遮住中间，只露出四周 9px
- **自适应图标文字**：图标按内层宽度百分比缩放，字号用 `clamp(10px, 4vw, 17px)`，小屏收缩大屏封顶
- **兼容性**：不使用 `inset` 简写、`mask-composite`、`aspect-ratio` 等较新特性，避免在老版本 WebView 上失效

## 已知限制
- 卸载重装不保留历史数据（SharedPreferences 随卸载清除），可用「导入历史数据」手动恢复余额与罚金
- 季度统计中收入/支出/罚金均为独立统计，提现金额只看币值余额，不含罚金

## 仓库说明
- 仓库仅含可编译源码；开发辅助文档（项目总结.md、校核报告*.md）已由 .gitignore 忽略，不进 GitHub。
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
│       │   └── PiggyCore.kt       # 数据模型 + 币值计算 + 卡片等级 + 自动违规检测
│       ├── assets/www/
│       │   ├── index.html         # SPA前端
│       │   ├── style.css
│       │   ├── app.js
│       │   └── img/               # 图标、硬币、主视觉
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
