package com.xianchiyu.piggybank

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

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
            val allCopper = copper + silver * 10
            silver = allCopper / 10
            copper = allCopper % 10
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

    fun exerciseCoins(type: String, duration: Int, distance: Float, isRainy: Boolean): Int {
        if (isRainy) return 3
        if (type == "run") {
            val timeCoins = when (duration) { 10 -> 3; 20 -> 6; 30 -> 10; else -> 0 }
            val distCoins = if (distance >= 3f) 5 else 0
            return timeCoins + distCoins
        }
        if (type == "walk") {
            return when (duration) { 10 -> 1; 20 -> 3; 30 -> 5; else -> 0 }
        }
        return 0
    }
}

// ── 自动违规检测 ─────────────────────────────────────
object AutoPenalty {
    fun check(today: String, yesterday: String): List<String> {
        val violations = mutableListOf<String>()

        if (PiggyData.autoCheckDate == today) return violations
        PiggyData.autoCheckDate = today

        if (PiggyData.lastExerciseDate.isEmpty() &&
            PiggyData.lastBreakfastDate.isEmpty() &&
            PiggyData.lastDinnerDate.isEmpty()) return violations

        checkOne("exercise", yesterday, "未运动", violations)
        checkOne("breakfast", yesterday, "没做早饭", violations)
        checkOne("dinner", yesterday, "晚餐不达标", violations)

        return violations
    }

    private fun checkOne(type: String, yesterday: String, desc: String, out: MutableList<String>) {
        val lastDate = when (type) {
            "exercise" -> PiggyData.lastExerciseDate
            "breakfast" -> PiggyData.lastBreakfastDate
            "dinner" -> PiggyData.lastDinnerDate
            else -> return
        }
        if (lastDate >= yesterday) return

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val periodStart = PiggyData.penaltyPeriodStart

        if (periodStart.isEmpty()) {
            PiggyData.penaltyPeriodStart = today
            PiggyData.penaltyCount = 0
        } else {
            val daysSince = try {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                ((fmt.parse(today)!!.time - fmt.parse(periodStart)!!.time) / (1000 * 60 * 60 * 24)).toInt()
            } catch (e: Exception) { 0 }
            if (daysSince >= 15) {
                PiggyData.penaltyPeriodStart = today
                PiggyData.penaltyCount = 0
            }
        }
        PiggyData.penaltyCount += 1
        val cashPenalty = CoinUtils.cashPenalty(PiggyData.penaltyCount)

        when (type) {
            "exercise" -> PiggyData.exerciseStreak = 0
            "breakfast" -> PiggyData.breakfastStreak = 0
            "dinner" -> PiggyData.dinnerStreak = 0
        }

        out.add("$desc 违规(现金罚金¥$cashPenalty) 连续天数已清零")
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
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

        // 同一天只查一次
        if (cachedRainy != null && cacheDate == today) return cachedRainy!!

        cachedRainy = try {
            val url = java.net.URL("https://uapis.cn/api/v1/misc/weather?city=$city")
            val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return false
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            // 解析 JSON 中的 weather 字段
            val json = org.json.JSONObject(body)
            val weather = json.optString("weather", "")
            // 天气文本含“雨”即为雨天
            weather.contains("雨")
        } catch (e: Exception) {
            false
        }

        cacheDate = today
        return cachedRainy!!
    }
}

// ── 提醒通知 ──────────────────────────────────────────
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val type = intent.getStringExtra("type") ?: "exercise"
        val msg = when (type) {
            "breakfast" -> "该吃早饭了！打卡赚铜币"
            "dinner" -> "晚餐时间！记得简单吃"
            "exercise" -> "该运动了！去跑步或走路"
            else -> "该打卡了！"
        }

        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("piggy", "运动存钱罐提醒", NotificationManager.IMPORTANCE_HIGH)
            mgr.createNotificationChannel(channel)
        }
        val notif = NotificationCompat.Builder(context, "piggy")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("运动存钱罐")
            .setContentText(msg)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()
        mgr.notify(type.hashCode(), notif)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleAll(context)
        }
    }
}

object ReminderScheduler {
    fun scheduleAll(context: Context) {
        schedule(context, 7, 30, "breakfast")
        schedule(context, 19, 30, "dinner")
        schedule(context, 20, 0, "exercise")
    }

    fun schedule(context: Context, hour: Int, minute: Int, type: String) {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("type", type)
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(context, type.hashCode(), intent, pendingFlags)

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pi
        )
    }
}