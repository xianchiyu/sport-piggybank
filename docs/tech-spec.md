# 技术方案 — 运动存钱罐 MVP

## 一、技术选型

| 项目 | 选择 | 理由 |
|---|---|---|
| 框架 | 原生微信小程序（WXML/WXSS/JS） | MVP 不需要跨平台，原生最轻量 |
| 后端 | 微信云开发（云数据库 + 云函数） | 零运维，微信账号绑定，免费额度够个人用 |
| 天气 | 心知天气免费API / 和风天气 | 20:00查询当天降水 |
| 提醒 | 微信订阅消息 | 原生能力，到点弹窗 |

## 二、页面结构

### 页面1：主页（pages/index）
```
┌─────────────────────────┐
│  今日日期 + 天气状态      │
├─────────────────────────┤
│  打卡区                   │
│  ┌─────┐ ┌─────┐ ┌─────┐│
│  │运动  │ │早餐  │ │晚餐  ││  ← 三个打卡按钮
│  └─────┘ └─────┘ └─────┘│
│  运动子选项：跑步/走路/雨天│
│  运动时长选择：10/20/30min│
├─────────────────────────┤
│  储钱罐区                 │
│  ┌─────────────────────┐│
│  │    🏺 罐子可视化      ││  ← 硬币堆叠动画
│  │  金:3 银:12 铜:5     ││
│  │  余额: ¥43.5         ││
│  └─────────────────────┘│
├─────────────────────────┤
│  今日记录                 │
│  +跑步30min +1.5元       │
│  +早饭 +0.3元            │
│  -奶茶 -1.8元            │
└─────────────────────────┘
```

### 页面2：账本/提现（pages/ledger）
```
┌─────────────────────────┐
│  本季度概览               │
│  总收入: ¥125.3          │
│  总支出: ¥48.0           │
│  当前余额: ¥77.3         │
├─────────────────────────┤
│  消费登记                 │
│  [输入金额] [输入商品名]  │
│  [登记扣币]              │
├─────────────────────────┤
│  流水记录（按日期倒序）    │
│  7/16 +跑步30min +¥1.5  │
│  7/16 -奶茶 ¥-1.8       │
│  7/15 +早饭 +¥0.3       │
│  ...                    │
├─────────────────────────┤
│  季度提现                 │
│  Q3剩余: ¥77.3          │
│  [生成季度报告]          │
│  [标记已提现]            │
└─────────────────────────┘
```

### 页面3：设置（pages/settings）
```
┌─────────────────────────┐
│  城市设置: 北京 ▼        │
├─────────────────────────┤
│  提醒开关                 │
│  早餐提醒 7:30  [ON]     │
│  晚餐提醒 19:30 [ON]     │
│  运动提醒 20:00 [ON]     │
├─────────────────────────┤
│  社交豁免                 │
│  本月已用: 1/3           │
│  [使用社交豁免]          │
├─────────────────────────┤
│  违规惩罚记录             │
│  本周期违规: 0次         │
│  下次惩罚: ¥10           │
│  [查看历史]             │
├─────────────────────────┤
│  季度周期                 │
│  当前: 2026 Q3           │
│  结束: 9/30              │
└─────────────────────────┘
```

## 三、数据表设计（7张）

### 1. users — 用户表
```js
{
  _id: String,          // = openid
  city: String,         // "beijing"
  currentStreak: {
    exercise: Number,   // 运动连续天数
    breakfast: Number,  // 早饭连续天数
    dinner: Number      // 晚饭连续天数
  },
  socialExemptUsed: Number,  // 本月社交豁免已用次数
  penaltyPeriodStart: Date,  // 当前违规周期起始日
  penaltyCount: Number,      // 当前周期违规次数
  createdAt: Date
}
```

### 2. coins — 币余额表
```js
{
  _id: String,          // = openid
  copper: Number,       // 铜币数量
  silver: Number,       // 银币数量
  gold: Number,         // 金币数量
  updatedAt: Date
}
// 余额 = gold*10 + silver + copper*0.1（元）
```

### 3. checkins — 打卡记录表
```js
{
  _id: String,
  userId: String,       // openid
  date: String,         // "2026-07-16" 当天日期
  type: String,         // "exercise" | "breakfast" | "dinner"
  exerciseType: String, // "run" | "walk" | "rainy"（仅exercise）
  duration: Number,     // 10 | 20 | 30（仅exercise）
  distance: Number,     // km（仅run，可选）
  coinsEarned: Number,  // 实际获得铜币数（加成后）
  multiplier: Number,   // 当次倍率 1.0/1.2/1.5/1.8
  createdAt: Date
}
// 唯一约束: userId + date + type 不可重复
```

### 4. transactions — 交易流水表
```js
{
  _id: String,
  userId: String,
  date: String,
  type: String,         // "income" | "expense" | "penalty" | "withdraw"
  subtype: String,      // "exercise"|"breakfast"|"dinner"|"purchase"|"penalty_cash"|"penalty_coin"|"quarter_withdraw"
  amount: Number,       // 元（正数收入，负数支出）
  coinChange: {         // 币变动明细
    copper: Number,
    silver: Number,
    gold: Number
  },
  note: String,         // "奶茶" / "跑步30min" / "违规罚金"
  createdAt: Date
}
```

### 5. penalties — 违规记录表
```js
{
  _id: String,
  userId: String,
  date: String,
  violationType: String,  // "exercise" | "breakfast" | "dinner"
  description: String,    // "未运动" / "叫了外卖"
  cashPenalty: Number,    // 现金罚金（元），0如果只是积分惩罚
  coinPenalty: {          // 积分扣回
    copper: Number,
    silver: Number,
    gold: Number
  },
  executed: Boolean,      // 现金罚金是否已转账执行
  createdAt: Date
}
```

### 6. weather — 天气记录表
```js
{
  _id: String,
  userId: String,
  date: String,
  city: String,
  isRainy: Boolean,      // API判断结果
  manualOverride: Boolean, // 用户手动晴转雨
  checkedAt: Date        // 20:00查询时间
}
```

### 7. quarterly — 季度提现表
```js
{
  _id: String,
  userId: String,
  quarter: String,       // "2026-Q3"
  totalIncome: Number,   // 季度总收入（元）
  totalExpense: Number,  // 季度总支出（元）
  finalBalance: Number,  // 季度末余额（元）
  reportGenerated: Boolean,
  withdrawn: Boolean,    // 是否已手动提现
  withdrawnAt: Date,
  createdAt: Date
}
```

## 四、云函数设计

### 1. checkin(taskType, exerciseType?, duration?, distance?)
打卡核心逻辑：
1. 验证当天是否已打过此类型卡
2. 查询当前连续天数 → 确定倍率
3. 计算应得铜币（基础×倍率）
4. 更新 coins 表（含自动合成逻辑）
5. 更新 users 表连续天数 +1
6. 写入 checkins + transactions 记录
7. 返回新余额 + 动画数据

### 2. consume(amount, note)
消费扣币：
1. 验证余额是否足够
2. 执行拆币（优先扣铜→不够拆银→不够拆金）
3. 更新 coins 表
4. 写入 transactions 记录
5. 返回新余额

### 3. checkWeather()
天气判断（云函数定时触发或手动调用）：
1. 查用户城市
2. 调天气API查降水
3. 写入 weather 表
4. 返回晴/雨状态

### 4. reportViolation(type, description)
违规上报：
1. 读取当前违规周期 + 违规次数
2. 计算现金罚金（三角数递进）
3. 执行积分扣回（连带扣上笔同项收入）
4. 清零该任务连续天数
5. 写入 penalties + transactions
6. 更新 users 违规计数
7. 返回罚金金额 + 扣币明细

### 5. quarterEnd()
季度结算（定时触发）：
1. 计算季度余额
2. 生成季度报告
3. 余额清零
4. 写入 quarterly 表
5. 连续天数保留，币清零

### 6. sendReminder(taskType)
订阅消息推送（定时触发）：
1. 查用户订阅状态
2. 发送对应提醒（早餐7:30/晚餐19:30/运动20:00）

## 五、核心算法

### 5.1 连续天数 → 倍率
```js
function getMultiplier(streak) {
  if (streak <= 2) return 1.0;
  if (streak <= 6) return 1.2;
  if (streak <= 13) return 1.5;
  return 1.8;
}
```

### 5.2 运动计币
```js
function calcExerciseCoins(type, duration, distance, isRainy) {
  if (isRainy) return 3; // 雨天6000步=3铜

  if (type === 'run') {
    const timeCoins = { 10: 3, 20: 6, 30: 10 }[duration] || 0;
    const distCoins = distance >= 3 ? 5 : 0;
    return timeCoins + distCoins;
  }
  if (type === 'walk') {
    return { 10: 1, 20: 3, 30: 5 }[duration] || 0;
  }
  return 0;
}
```

### 5.3 自动合成
```js
function autoMerge(coins) {
  let { copper, silver, gold } = coins;
  // 铜→银：≥15时合10枚
  if (copper >= 15) {
    const mergeCount = Math.floor((copper - 5) / 10);
    silver += mergeCount;
    copper -= mergeCount * 10;
  }
  // 银→金：≥15时合10枚
  if (silver >= 15) {
    const mergeCount = Math.floor((silver - 5) / 10);
    gold += mergeCount;
    silver -= mergeCount * 10;
  }
  return { copper, silver, gold };
}
```

### 5.4 拆币消费
```js
function spendCoins(coins, copperNeeded) {
  let { copper, silver, gold } = coins;
  // 优先扣铜
  if (copper >= copperNeeded) {
    copper -= copperNeeded;
    return { copper, silver, gold };
  }
  // 不够拆银：1银=10铜
  copperNeeded -= copper;
  copper = 0;
  const silverNeeded = Math.ceil(copperNeeded / 10);
  if (silver >= silverNeeded) {
    silver -= silverNeeded;
    copper += silverNeeded * 10 - copperNeeded;
    return { copper, silver, gold };
  }
  // 不够拆金：1金=10银
  copperNeeded -= silver * 10;
  const silverShort = silverNeeded - silver;
  silver = 0;
  const goldNeeded = Math.ceil(silverShort / 10);
  if (gold >= goldNeeded) {
    gold -= goldNeeded;
    silver += goldNeeded * 10 - silverShort;
    copper += silver * 10 - (copperNeeded - (silverNeeded - silverShort) * 10);
    // 简化：找零计算需要更精确处理
    return { copper, silver, gold };
  }
  return null; // 余额不足
}
```

### 5.5 违规罚金（三角数递进）
```js
function calcCashPenalty(violationCount) {
  // 10→30→60→100→150→210...
  // 公式: 10 * n*(n+1)/2
  const n = violationCount;
  return 10 * n * (n + 1) / 2;
}
// 验证: n=1→10, n=2→30, n=3→60, n=4→100, n=5→150 ✓
```

## 六、MVP 范围（第一版）

### 必做（MVP核心）
- [x] 三页布局骨架
- [x] 三项打卡（运动/早饭/晚饭）+ 连续天数 + 倍率
- [x] 储钱罐显示 + 合成/拆币
- [x] 消费登记扣币
- [x] 违规上报 + 双轨惩罚
- [x] 天气判断（晴/雨）
- [x] 季度提现报告

### 后续迭代
- [ ] 订阅消息提醒（需要小程序上线后配置）
- [ ] 自定义打卡任务
- [ ] 储钱罐动画（硬币落入堆叠）
- [ ] 统计图表
- [ ] 社交豁免逻辑细化

## 七、开发顺序

1. 初始化小程序项目 + 云开发环境
2. 建数据表 + 写云函数骨架
3. 主页：打卡按钮 + 基础逻辑
4. 储钱罐：余额显示 + 合成/拆币
5. 账本页：消费登记 + 流水记录
6. 违规惩罚逻辑
7. 天气判断
8. 季度提现
