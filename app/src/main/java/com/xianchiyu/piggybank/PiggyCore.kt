package com.xianchiyu.piggybank

import android.content.Context

// ── 数据模型 ──────────────────────────────────────────
object PiggyData {
    private const val PREFS = "piggybank"
    private var prefs: android.content.SharedPreferences? = null

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var copper: Int
        get() = prefs?.getInt("copper", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("copper", v)?.apply() }

    var silver: Int
        get() = prefs?.getInt("silver", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("silver", v)?.apply() }

    var gold: Int
        get() = prefs?.getInt("gold", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("gold", v)?.apply() }

    var exerciseStreak: Int
        get() = prefs?.getInt("exStreak", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("exStreak", v)?.apply() }

    var breakfastStreak: Int
        get() = prefs?.getInt("bfStreak", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("bfStreak", v)?.apply() }

    var dinnerStreak: Int
        get() = prefs?.getInt("dnStreak", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("dnStreak", v)?.apply() }

    var lastExerciseDate: String
        get() = prefs?.getString("exDate", "") ?: ""
        set(v) { prefs?.edit()?.putString("exDate", v)?.apply() }

    var lastBreakfastDate: String
        get() = prefs?.getString("bfDate", "") ?: ""
        set(v) { prefs?.edit()?.putString("bfDate", v)?.apply() }

    var lastDinnerDate: String
        get() = prefs?.getString("dnDate", "") ?: ""
        set(v) { prefs?.edit()?.putString("dnDate", v)?.apply() }

    var penaltyCount: Int
        get() = prefs?.getInt("penCount", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("penCount", v)?.apply() }

    var penaltyPeriodStart: String
        get() = prefs?.getString("penStart", "") ?: ""
        set(v) { prefs?.edit()?.putString("penStart", v)?.apply() }

    var autoCheckDate: String
        get() = prefs?.getString("autoCheck", "") ?: ""
        set(v) { prefs?.edit()?.putString("autoCheck", v)?.apply() }

    var socialExemptMonth: String
        get() = prefs?.getString("socMonth", "") ?: ""
        set(v) { prefs?.edit()?.putString("socMonth", v)?.apply() }

    var socialExemptUsed: Int
        get() = prefs?.getInt("socUsed", 0) ?: 0
        set(v) { prefs?.edit()?.putInt("socUsed", v)?.apply() }

    var socialExemptDate: String
        get() = prefs?.getString("socExemptDate", "") ?: ""
        set(v) { prefs?.edit()?.putString("socExemptDate", v)?.apply() }

    var city: String
        get() = prefs?.getString("city", "beijing") ?: "beijing"
        set(v) { prefs?.edit()?.putString("city", v)?.apply() }

    var firstUseDate: String
        get() = prefs?.getString("firstUseDate", "") ?: ""
        set(v) { prefs?.edit()?.putString("firstUseDate", v)?.apply() }

    var transactions: String
        get() = prefs?.getString("txns", "[]") ?: "[]"
        set(v) { prefs?.edit()?.putString("txns", v)?.apply() }

    var quarterIncome: Float
        get() = prefs?.getFloat("qIncome", 0f) ?: 0f
        set(v) { prefs?.edit()?.putFloat("qIncome", v)?.apply() }

    var quarterExpense: Float
        get() = prefs?.getFloat("qExpense", 0f) ?: 0f
        set(v) { prefs?.edit()?.putFloat("qExpense", v)?.apply() }

    var penaltyTotal: Float
        get() = prefs?.getFloat("penTotal", 0f) ?: 0f
        set(v) { prefs?.edit()?.putFloat("penTotal", v)?.apply() }

    var dailyBalance: String
        get() = prefs?.getString("dailyBal", "{}") ?: "{}"
        set(v) { prefs?.edit()?.putString("dailyBal", v)?.apply() }

    /**
     * 保存当天余额快照，自动只保留最近30天。
     * 在每次余额变化（打卡/消费/违规/提现）及每天首次打开app时调用。
     */
    fun saveDailyBalance() {
        val cal = java.util.Calendar.getInstance()
        val today = String.format(java.util.Locale.US, "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
        try {
            val json = org.json.JSONObject(dailyBalance)
            json.put(today, org.json.JSONObject().apply {
                put("gold", gold)
                put("silver", silver)
                put("copper", copper)
            })
            // 只保留最近30天，按日期键排序后删除最早的
            val keys = mutableListOf<String>()
            val iter = json.keys()
            while (iter.hasNext()) keys.add(iter.next())
            keys.sort()
            while (keys.size > 30) {
                json.remove(keys.removeAt(0))
            }
            dailyBalance = json.toString()
        } catch (e: Exception) { }
    }

}

// ── 币值计算工具 ──────────────────────────────────────
object CoinUtils {
    fun totalYuan(copper: Int, silver: Int, gold: Int): Float {
        return gold * 10f + silver * 1f + copper * 0.1f
    }

    fun autoMerge(c: Int, s: Int, g: Int): Triple<Int, Int, Int> {
        var copper = c
        var silver = s
        var gold = g
        if (copper >= 15) {
            val n = (copper - 5) / 10
            silver += n
            copper -= n * 10
        }
        if (silver >= 15) {
            val n = (silver - 5) / 10
            gold += n
            silver -= n * 10
        }
        return Triple(copper, silver, gold)
    }

    fun spend(c: Int, s: Int, g: Int, copperNeeded: Int): Triple<Int, Int, Int>? {
        var copper = c
        var silver = s
        var gold = g
        var need = copperNeeded

        if (copper >= need) {
            copper -= need
            return Triple(copper, silver, gold)
        }
        need -= copper
        copper = 0

        val silverNeed = (need + 9) / 10
        if (silver >= silverNeed) {
            silver -= silverNeed
            copper += silverNeed * 10 - need
            return Triple(copper, silver, gold)
        }

        val silverShort = silverNeed - silver
        val goldNeed = (silverShort + 9) / 10
        if (gold >= goldNeed) {
            gold -= goldNeed
            silver = goldNeed * 10 - silverShort
            copper = silverNeed * 10 - need
            return Triple(copper, silver, gold)
        }
        return null
    }

    fun cashPenalty(count: Int): Int {
        return 10 * count * (count + 1) / 2
    }

    fun multiplier(streak: Int): Float {
        return when {
            streak <= 2 -> 1.0f
            streak <= 6 -> 1.2f
            streak <= 13 -> 1.5f
            else -> 1.8f
        }
    }

    // duration 语义按 type 区分：run/walk 为分钟（10/20/30），rope 为个数（200/500/800）
    fun exerciseCoins(type: String, duration: Int, distance: Float, isRainy: Boolean): Int {
        if (isRainy || type == "indoor") return 3
        if (type == "run") {
            val timeCoins = when (duration) { 10 -> 3; 20 -> 6; 30 -> 10; else -> 0 }
            val distCoins = if (distance >= 3f) 5 else 0
            return timeCoins + distCoins
        }
        if (type == "walk") {
            return when (duration) { 10 -> 1; 20 -> 3; 30 -> 5; else -> 0 }
        }
        if (type == "rope") {
            return when (duration) { 200 -> 2; 500 -> 4; 800 -> 7; else -> 0 }
        }
        return 0
    }
}

// ── 自动违规检测 ─────────────────────────────────────
object AutoPenalty {
    data class Violation(
        val type: String,
        val desc: String,
        val cashPenalty: Int,
        val exempted: Boolean
    )

    fun check(today: String, yesterday: String): List<Violation> {
        val violations = mutableListOf<Violation>()

        val lastCheck = PiggyData.autoCheckDate
        if (lastCheck == today) return violations
        PiggyData.autoCheckDate = today

        if (PiggyData.lastExerciseDate.isEmpty() &&
            PiggyData.lastBreakfastDate.isEmpty() &&
            PiggyData.lastDinnerDate.isEmpty()) return violations

        // 从上次检测日的下一天开始，到昨天为止逐日检查（缺几天罚几天）
        var startDay: String
        if (lastCheck.isEmpty()) {
            // 首次检测：从安装日（firstUseDate）的下一天开始
            startDay = if (PiggyData.firstUseDate.isEmpty()) yesterday
                       else addDays(PiggyData.firstUseDate, 1)
        } else {
            startDay = addDays(lastCheck, 1)
        }
        if (startDay > yesterday) return violations

        var day = startDay
        while (day <= yesterday) {
            checkOne("exercise", day, "未运动", violations)
            checkOne("breakfast", day, "没做早饭", violations)
            checkOne("dinner", day, "晚餐不达标", violations)
            day = addDays(day, 1)
        }
        return violations
    }

    private fun addDays(dateStr: String, n: Int): String {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val d = fmt.parse(dateStr) ?: return dateStr
        val cal = java.util.Calendar.getInstance()
        cal.time = d
        cal.add(java.util.Calendar.DAY_OF_MONTH, n)
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
    }

    private fun daysBetween(start: String, end: String): Int {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val d1 = fmt.parse(start) ?: return 0
        val d2 = fmt.parse(end) ?: return 0
        return ((d2.time - d1.time) / (1000 * 60 * 60 * 24)).toInt()
    }

    private fun checkOne(type: String, checkDay: String, desc: String, out: MutableList<Violation>) {
        val lastDate = when (type) {
            "exercise" -> PiggyData.lastExerciseDate
            "breakfast" -> PiggyData.lastBreakfastDate
            "dinner" -> PiggyData.lastDinnerDate
            else -> return
        }
        if (lastDate >= checkDay) return

        // 晚餐社交豁免：当天用了豁免，检测跳过
        if (type == "dinner" && PiggyData.socialExemptDate == checkDay) {
            out.add(Violation(type, desc, 0, exempted = true))
            return
        }

        val periodStart = PiggyData.penaltyPeriodStart
        if (periodStart.isEmpty()) {
            PiggyData.penaltyPeriodStart = checkDay
            PiggyData.penaltyCount = 0
        } else {
            val daysSince = daysBetween(periodStart, checkDay)
            if (daysSince >= 15) {
                PiggyData.penaltyPeriodStart = checkDay
                PiggyData.penaltyCount = 0
            }
        }
        PiggyData.penaltyCount += 1
        val cashPenalty = CoinUtils.cashPenalty(PiggyData.penaltyCount)
        PiggyData.penaltyTotal += cashPenalty.toFloat()

        when (type) {
            "exercise" -> PiggyData.exerciseStreak = 0
            "breakfast" -> PiggyData.breakfastStreak = 0
            "dinner" -> PiggyData.dinnerStreak = 0
        }

        out.add(Violation(type, desc, cashPenalty, exempted = false))
    }
}

// ── 天气查询 ──────────────────────────────────────────
object WeatherHelper {
    private var cachedRainy: Boolean? = null
    private var cacheDate: String = ""

    fun clearCache() {
        cachedRainy = null
        cacheDate = ""
    }

    fun isRainy(city: String): Boolean {
        val cal = java.util.Calendar.getInstance()
        val today = String.format(java.util.Locale.US, "%04d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))

        // 同一天只查一次
        if (cachedRainy != null && cacheDate == today) return cachedRainy!!

        cachedRainy = try {
            val encodedCity = java.net.URLEncoder.encode(city, "UTF-8")
            val url = java.net.URL("https://uapis.cn/api/v1/misc/weather?city=$encodedCity")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                false
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                // 解析 JSON 中的 weather 字段
                val json = org.json.JSONObject(body)
                val weather = json.optString("weather", "")
                // 天气文本含“雨”即为雨天
                weather.contains("雨")
            }
        } catch (e: Exception) {
            false
        }

        cacheDate = today
        return cachedRainy!!
    }
}
