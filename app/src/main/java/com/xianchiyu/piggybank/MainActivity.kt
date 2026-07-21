package com.xianchiyu.piggybank

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PiggyData.init(this)
        ReminderScheduler.scheduleAll(this)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(JsBridge(), "Android")

        webView.loadUrl("file:///android_asset/www/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    inner class JsBridge {

        @JavascriptInterface
        fun getBalance(): String {
            val c = PiggyData.copper
            val s = PiggyData.silver
            val g = PiggyData.gold
            val yuan = CoinUtils.totalYuan(c, s, g)
            return ok(JSONObject().apply {
                put("copper", c)
                put("silver", s)
                put("gold", g)
                put("yuan", yuan)
            })
        }

        @JavascriptInterface
        fun getStreaks(): String {
            return ok(JSONObject().apply {
                put("exercise", PiggyData.exerciseStreak)
                put("breakfast", PiggyData.breakfastStreak)
                put("dinner", PiggyData.dinnerStreak)
            })
        }

        @JavascriptInterface
        fun checkin(type: String, exerciseType: String, duration: Int, distance: Float, manualRainy: Boolean): String {
            val today = todayStr()

            val lastDate = when (type) {
                "exercise" -> PiggyData.lastExerciseDate
                "breakfast" -> PiggyData.lastBreakfastDate
                "dinner" -> PiggyData.lastDinnerDate
                else -> return err("未知任务类型: $type")
            }
            if (lastDate == today) return err("今天已经打过此卡")

            // 手动覆盖优先于 API 判断
            val isRainyToday = manualRainy || WeatherHelper.isRainy(PiggyData.city)

            val yesterday = yesterdayStr()
            val prevStreak = when (type) {
                "exercise" -> PiggyData.exerciseStreak
                "breakfast" -> PiggyData.breakfastStreak
                "dinner" -> PiggyData.dinnerStreak
                else -> 0
            }
            val newStreak = if (lastDate == yesterday) prevStreak + 1 else 1

            val baseCoins = when (type) {
                "exercise" -> CoinUtils.exerciseCoins(exerciseType, duration, distance, isRainyToday)
                "breakfast" -> 3
                "dinner" -> 3
                else -> 0
            }
            val mult = CoinUtils.multiplier(newStreak)
            val coinsEarned = (baseCoins * mult).toInt()

            val (c, s, g) = CoinUtils.autoMerge(
                PiggyData.copper + coinsEarned,
                PiggyData.silver,
                PiggyData.gold
            )
            PiggyData.copper = c
            PiggyData.silver = s
            PiggyData.gold = g

            when (type) {
                "exercise" -> {
                    PiggyData.exerciseStreak = newStreak
                    PiggyData.lastExerciseDate = today
                }
                "breakfast" -> {
                    PiggyData.breakfastStreak = newStreak
                    PiggyData.lastBreakfastDate = today
                }
                "dinner" -> {
                    PiggyData.dinnerStreak = newStreak
                    PiggyData.lastDinnerDate = today
                }
            }

            val yuan = coinsEarned * 0.1f
            PiggyData.quarterIncome += yuan

            addTransaction("income", type, yuan, "${typeLabel(type)} +${coinsEarned}铜(x${mult})")

            return ok(JSONObject().apply {
                put("coins", coinsEarned)
                put("multiplier", mult)
                put("streak", newStreak)
                put("copper", c)
                put("silver", s)
                put("gold", g)
                put("yuan", CoinUtils.totalYuan(c, s, g))
            })
        }

        @JavascriptInterface
        fun consume(amount: Float, note: String): String {
            val copperNeeded = (amount * 10).toInt()
            val result = CoinUtils.spend(
                PiggyData.copper, PiggyData.silver, PiggyData.gold, copperNeeded
            )
            if (result == null) return err("余额不足，去运动！")

            val (c, s, g) = result
            PiggyData.copper = c
            PiggyData.silver = s
            PiggyData.gold = g

            PiggyData.quarterExpense += amount
            addTransaction("expense", "purchase", -amount, note)

            return ok(JSONObject().apply {
                put("copper", c)
                put("silver", s)
                put("gold", g)
                put("yuan", CoinUtils.totalYuan(c, s, g))
            })
        }

        @JavascriptInterface
        fun reportViolation(type: String, description: String): String {
            val today = todayStr()

            val penStart = PiggyData.penaltyPeriodStart
            if (penStart.isEmpty() || daysBetween(penStart, today) >= 15) {
                PiggyData.penaltyPeriodStart = today
                PiggyData.penaltyCount = 0
            }
            PiggyData.penaltyCount += 1
            val cashPenalty = CoinUtils.cashPenalty(PiggyData.penaltyCount)

            when (type) {
                "exercise" -> PiggyData.exerciseStreak = 0
                "breakfast" -> PiggyData.breakfastStreak = 0
                "dinner" -> PiggyData.dinnerStreak = 0
            }

            addTransaction("penalty", "penalty_cash", -cashPenalty.toFloat(), "违规: $description (¥$cashPenalty)")

            return ok(JSONObject().apply {
                put("cashPenalty", cashPenalty)
                put("penaltyCount", PiggyData.penaltyCount)
                put("streakCleared", type)
            })
        }

        @JavascriptInterface
        fun useSocialExempt(): String {
            val month = todayStr().substring(0, 7)
            if (PiggyData.socialExemptMonth != month) {
                PiggyData.socialExemptMonth = month
                PiggyData.socialExemptUsed = 0
            }
            if (PiggyData.socialExemptUsed >= 3) return err("本月社交豁免次数已用完")
            PiggyData.socialExemptUsed += 1
            return ok(JSONObject().apply {
                put("used", PiggyData.socialExemptUsed)
                put("remaining", 3 - PiggyData.socialExemptUsed)
            })
        }

        @JavascriptInterface
        fun getSocialExemptStatus(): String {
            val month = todayStr().substring(0, 7)
            if (PiggyData.socialExemptMonth != month) {
                PiggyData.socialExemptMonth = month
                PiggyData.socialExemptUsed = 0
            }
            return ok(JSONObject().apply {
                put("used", PiggyData.socialExemptUsed)
                put("remaining", 3 - PiggyData.socialExemptUsed)
            })
        }

        @JavascriptInterface
        fun getPenaltyStatus(): String {
            val today = todayStr()
            val penStart = PiggyData.penaltyPeriodStart
            if (penStart.isEmpty() || daysBetween(penStart, today) >= 15) {
                return ok(JSONObject().apply {
                    put("count", 0)
                    put("nextPenalty", 10)
                    put("daysLeft", 15)
                })
            }
            val daysLeft = 15 - daysBetween(penStart, today)
            val nextCount = PiggyData.penaltyCount + 1
            return ok(JSONObject().apply {
                put("count", PiggyData.penaltyCount)
                put("nextPenalty", CoinUtils.cashPenalty(nextCount))
                put("daysLeft", daysLeft)
            })
        }

        @JavascriptInterface
        fun getQuarterSummary(): String {
            val incomeCopper = (PiggyData.quarterIncome * 10).toInt()
            val expenseCopper = (PiggyData.quarterExpense * 10).toInt()
            return ok(JSONObject().apply {
                put("income", PiggyData.quarterIncome)
                put("expense", PiggyData.quarterExpense)
                put("balance", PiggyData.quarterIncome - PiggyData.quarterExpense)
                put("incomeCopper", incomeCopper)
                put("expenseCopper", expenseCopper)
                put("balanceCopper", incomeCopper - expenseCopper)
            })
        }

        @JavascriptInterface
        fun quarterWithdraw(): String {
            val balance = PiggyData.quarterIncome - PiggyData.quarterExpense
            val report = JSONObject().apply {
                put("income", PiggyData.quarterIncome)
                put("expense", PiggyData.quarterExpense)
                put("balance", balance)
                put("date", todayStr())
            }
            PiggyData.quarterIncome = 0f
            PiggyData.quarterExpense = 0f
            PiggyData.copper = 0
            PiggyData.silver = 0
            PiggyData.gold = 0
            addTransaction("withdraw", "quarter_withdraw", balance, "季度提现: ¥$balance")
            return ok(report)
        }

        @JavascriptInterface
        fun getTransactions(): String {
            return ok(JSONArray(PiggyData.transactions))
        }

        @JavascriptInterface
        fun getAutoViolations(): String {
            val today = todayStr()

            // 同一天只检测一次
            if (PiggyData.autoCheckDate == today) {
                return ok(JSONObject().put("violations", JSONArray()))
            }
            PiggyData.autoCheckDate = today

            // 首次使用：记录安装日，不触发任何违规
            val firstUse = PiggyData.firstUseDate
            if (firstUse.isEmpty()) {
                PiggyData.firstUseDate = today
                return ok(JSONObject().put("violations", JSONArray()))
            }

            // 安装当天不检测（给用户一个缓冲日）
            if (today == firstUse) {
                return ok(JSONObject().put("violations", JSONArray()))
            }

            val yesterday = yesterdayStr()
            val violations = JSONArray()

            // 只检测曾经打过卡的项：lastDate 非空但昨天和今天都没打 → 违规
            if (PiggyData.lastExerciseDate.isNotEmpty()
                && PiggyData.lastExerciseDate != yesterday
                && PiggyData.lastExerciseDate != today) {
                violations.put("昨天未运动打卡")
            }
            if (PiggyData.lastBreakfastDate.isNotEmpty()
                && PiggyData.lastBreakfastDate != yesterday
                && PiggyData.lastBreakfastDate != today) {
                violations.put("昨天未早餐打卡")
            }
            if (PiggyData.lastDinnerDate.isNotEmpty()
                && PiggyData.lastDinnerDate != yesterday
                && PiggyData.lastDinnerDate != today) {
                violations.put("昨天未晚餐打卡")
            }

            return ok(JSONObject().put("violations", violations))
        }

        @JavascriptInterface
        fun getCity(): String {
            return PiggyData.city
        }

        @JavascriptInterface
        fun setCity(city: String): String {
            PiggyData.city = city
            // 城市变了，清空天气缓存
            WeatherHelper.clearCache()
            return ok(JSONObject().put("city", city))
        }

        @JavascriptInterface
        fun getWeatherStatus(): String {
            val rainy = WeatherHelper.isRainy(PiggyData.city)
            return ok(JSONObject().put("rainy", rainy as Any))
        }

        private fun addTransaction(type: String, subtype: String, amount: Float, note: String) {
            val arr = JSONArray(PiggyData.transactions)
            arr.put(JSONObject().apply {
                put("date", todayStr())
                put("type", type)
                put("subtype", subtype)
                put("amount", amount)
                put("note", note)
                put("time", System.currentTimeMillis())
            })
            val result = JSONArray()
            val start = maxOf(0, arr.length() - 200)
            for (i in start until arr.length()) result.put(arr[i])
            PiggyData.transactions = result.toString()
        }

        private fun todayStr(): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        }

        private fun yesterdayStr(): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, -1)
            return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }

        private fun daysBetween(start: String, end: String): Int {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val d1 = fmt.parse(start) ?: return 0
            val d2 = fmt.parse(end) ?: return 0
            return ((d2.time - d1.time) / (1000 * 60 * 60 * 24)).toInt()
        }

        private fun typeLabel(type: String): String = when (type) {
            "exercise" -> "运动"
            "breakfast" -> "早饭"
            "dinner" -> "晚饭"
            else -> type
        }

        private fun ok(data: Any): String {
            val obj = JSONObject()
            obj.put("ok", true)
            when (data) {
                is JSONObject -> obj.put("data", data)
                is JSONArray -> obj.put("data", data)
            }
            return obj.toString()
        }

        private fun err(msg: String): String =
            JSONObject().put("ok", false).put("error", msg).toString()
    }
}
