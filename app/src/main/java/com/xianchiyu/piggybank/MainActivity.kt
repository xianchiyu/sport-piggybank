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

    /**
     * JS 桥接层：前端通过 Android.xxx() 调用原生功能
     * 所有方法必须加 @JavascriptInterface 注解
     */
    inner class JsBridge {

        // ── 余额 ──────────────────────────────────────

        @JavascriptInterface
        fun getBalance(): String {
            val c = PiggyData.copper
            val s = PiggyData.silver
            val g = PiggyData.gold
            val yuan = CoinUtils.totalYuan(c, s, g)
            return JSONObject().apply {
                put("copper", c)
                put("silver", s)
                put("gold", g)
                put("yuan", yuan)
            }.toString()
        }

        // ── 连续天数 ──────────────────────────────────

        @JavascriptInterface
        fun getStreaks(): String {
            return JSONObject().apply {
                put("exercise", PiggyData.exerciseStreak)
                put("breakfast", PiggyData.breakfastStreak)
                put("dinner", PiggyData.dinnerStreak)
            }.toString()
        }

        // ── 打卡 ──────────────────────────────────────

        @JavascriptInterface
        fun checkin(type: String, exerciseType: String, duration: Int, distance: Float): String {
            val today = todayStr()

            // 判断是否今天已打过此类型卡
            val lastDate = when (type) {
                "exercise" -> PiggyData.lastExerciseDate
                "breakfast" -> PiggyData.lastBreakfastDate
                "dinner" -> PiggyData.lastDinnerDate
                else -> return err("未知任务类型: $type")
            }
            if (lastDate == today) return err("今天已经打过此卡")

            // 判断是否昨天打过（决定连续天数+1还是重置为1）
            val yesterday = yesterdayStr()
            val prevStreak = when (type) {
                "exercise" -> PiggyData.exerciseStreak
                "breakfast" -> PiggyData.breakfastStreak
                "dinner" -> PiggyData.dinnerStreak
                else -> 0
            }
            val newStreak = if (lastDate == yesterday) prevStreak + 1 else 1

            // 计算币
            val baseCoins = when (type) {
                "exercise" -> CoinUtils.exerciseCoins(exerciseType, duration, distance, false)
                "breakfast" -> 3
                "dinner" -> 3
                else -> 0
            }
            val mult = CoinUtils.multiplier(newStreak)
            val coinsEarned = (baseCoins * mult).toInt()

            // 更新余额 + 自动合成
            val (c, s, g) = CoinUtils.autoMerge(
                PiggyData.copper + coinsEarned,
                PiggyData.silver,
                PiggyData.gold
            )
            PiggyData.copper = c
            PiggyData.silver = s
            PiggyData.gold = g

            // 更新连续天数 + 日期
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

            // 季度收入
            val yuan = coinsEarned * 0.1f
            PiggyData.quarterIncome += yuan

            // 记录流水
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

        // ── 消费 ──────────────────────────────────────

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

        // ── 违规 ──────────────────────────────────────

        @JavascriptInterface
        fun reportViolation(type: String, description: String): String {
            val today = todayStr()

            // 违规周期检查（15天）
            val penStart = PiggyData.penaltyPeriodStart
            if (penStart.isEmpty() || daysBetween(penStart, today) >= 15) {
                PiggyData.penaltyPeriodStart = today
                PiggyData.penaltyCount = 0
            }
            PiggyData.penaltyCount += 1
            val cashPenalty = CoinUtils.cashPenalty(PiggyData.penaltyCount)

            // 积分惩罚：连带扣回上一笔同项收入 + 连续清零
            val (c, s, g) = when (type) {
                "exercise" -> {
                    PiggyData.exerciseStreak = 0
                    Triple(PiggyData.copper, PiggyData.silver, PiggyData.gold)
                }
                "breakfast" -> {
                    PiggyData.breakfastStreak = 0
                    Triple(PiggyData.copper, PiggyData.silver, PiggyData.gold)
                }
                "dinner" -> {
                    PiggyData.dinnerStreak = 0
                    Triple(PiggyData.copper, PiggyData.silver, PiggyData.gold)
                }
                else -> Triple(PiggyData.copper, PiggyData.silver, PiggyData.gold)
            }
            // MVP暂不实现连带扣回精确金额，只清零连续天数

            addTransaction("penalty", "penalty_cash", -cashPenalty.toFloat(), "违规: $description (¥$cashPenalty)")

            return ok(JSONObject().apply {
                put("cashPenalty", cashPenalty)
                put("penaltyCount", PiggyData.penaltyCount)
                put("streakCleared", type)
            })
        }

        // ── 社交豁免 ──────────────────────────────────

        @JavascriptInterface
        fun useSocialExempt(): String {
            val month = todayStr().substring(0, 7) // "2026-07"
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

        // ── 违规状态 ──────────────────────────────────

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

        // ── 季度数据 ──────────────────────────────────

        @JavascriptInterface
        fun getQuarterSummary(): String {
            return ok(JSONObject().apply {
                put("income", PiggyData.quarterIncome)
                put("expense", PiggyData.quarterExpense)
                put("balance", PiggyData.quarterIncome - PiggyData.quarterExpense)
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
            // 清零
            PiggyData.quarterIncome = 0f
            PiggyData.quarterExpense = 0f
            PiggyData.copper = 0
            PiggyData.silver = 0
            PiggyData.gold = 0
            addTransaction("withdraw", "quarter_withdraw", balance, "季度提现: ¥$balance")
            return ok(report)
        }

        // ── 流水记录 ──────────────────────────────────

        @JavascriptInterface
        fun getTransactions(): String {
            return PiggyData.transactions
        }

        // ── 设置 ──────────────────────────────────────

        @JavascriptInterface
        fun getCity(): String {
            return PiggyData.city
        }

        @JavascriptInterface
        fun setCity(city: String): String {
            PiggyData.city = city
            return ok(JSONObject().put("city", city))
        }

        // ── 内部工具 ──────────────────────────────────

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
            // 保留最近200条
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

        private fun ok(data: JSONObject): String =
            JSONObject().put("ok", true).put("data", data).toString()

        private fun err(msg: String): String =
            JSONObject().put("ok", false).put("error", msg).toString()
    }
}
