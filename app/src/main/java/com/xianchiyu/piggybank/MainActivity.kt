package com.xianchiyu.piggybank

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启动阶段：显式声明应用管理状态栏背景 + 清除半透明标志（模拟器必需），
        // 底色与启动图 #FFF8F0 一致，深色图标
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = Color.parseColor("#FFF8F0")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        PiggyData.init(this)

        webView = WebView(this)
        // 起始透明，冷启动期间透出系统 windowBackground 启动图，加载完淡入覆盖
        webView.alpha = 0f
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
        }
        // 透明背景：让 windowBackground（splash_window_bg 照片）在冷启动期间透出
        webView.setBackgroundColor(Color.TRANSPARENT)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // 页面加载完成：恢复白色状态栏 + 深色图标，并淡入 WebView 覆盖启动图
                restoreStatusBar()
                webView.animate().alpha(1f).setDuration(150).start()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                // 异常路径：同样恢复状态栏（不依赖 alpha 守卫），再淡入 WebView
                restoreStatusBar()
                if (webView.alpha < 1f) webView.animate().alpha(1f).setDuration(150).start()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result?.confirm() }
                    .setNegativeButton("取消") { _, _ -> result?.cancel() }
                    .show()
                return true
            }
        }
        webView.addJavascriptInterface(JsBridge(), "Android")

        webView.loadUrl("file:///android_asset/www/index.html")

        // 兜底：若 2s 内 onPageFinished 未触发（极端加载失败），强制恢复状态栏并淡入，避免永久卡在启动图
        Handler(mainLooper).postDelayed({
            restoreStatusBar()
            if (webView.alpha < 1f) webView.animate().alpha(1f).setDuration(150).start()
        }, 2000)
    }

    /**
     * 恢复状态栏为正常白底 + 深色图标。
     * 在 onPageFinished / onReceivedError / 2 秒超时兜底三处均调用，
     * 确保任意加载路径最终状态栏都正常显示。
     */
    private fun restoreStatusBar() {
        // 恢复正常白底状态栏 + 深色图标
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = Color.WHITE
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

            // 晚餐已用社交豁免则不可打卡
            if (type == "dinner" && PiggyData.socialExemptDate == today) return err("今天已用社交豁免，晚餐免打卡")

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

            val oldCopper = PiggyData.copper
            val oldSilver = PiggyData.silver
            val oldGold = PiggyData.gold

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

            addTransaction("income", type, yuan, "${typeLabel(type)} +${coinsEarned}铜(x${mult})", Triple(g - oldGold, s - oldSilver, c - oldCopper))

            return ok(JSONObject().apply {
                put("coins", coinsEarned)
                put("multiplier", mult)
                put("streak", newStreak)
                put("copper", c)
                put("silver", s)
                put("gold", g)
                put("yuan", CoinUtils.totalYuan(c, s, g))
                if (type == "exercise") {
                    put("isRainy", isRainyToday)
                    put("exerciseType", exerciseType)
                    put("duration", duration)
                }
            })
        }

        @JavascriptInterface
        fun consume(amount: Float, note: String): String {
            val copperNeeded = (amount * 10).toInt()
            val oldCopper = PiggyData.copper
            val oldSilver = PiggyData.silver
            val oldGold = PiggyData.gold
            val result = CoinUtils.spend(
                PiggyData.copper, PiggyData.silver, PiggyData.gold, copperNeeded
            )
            if (result == null) return err("余额不足，去运动！")

            val (c, s, g) = result
            PiggyData.copper = c
            PiggyData.silver = s
            PiggyData.gold = g

            PiggyData.quarterExpense += amount
            addTransaction("expense", "purchase", -amount, note, Triple(g - oldGold, s - oldSilver, c - oldCopper))

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

            // 晚餐社交豁免：当天用了豁免，手动上报也跳过
            if (type == "dinner" && PiggyData.socialExemptDate == today) {
                return err("今天已用社交豁免，晚餐免罚")
            }

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

            addTransaction("penalty", "penalty_cash", -cashPenalty.toFloat(), "违规: $description (¥$cashPenalty)", Triple(0, 0, 0))

            return ok(JSONObject().apply {
                put("cashPenalty", cashPenalty)
                put("penaltyCount", PiggyData.penaltyCount)
                put("streakCleared", type)
            })
        }

        @JavascriptInterface
        fun useSocialExempt(): String {
            val today = todayStr()
            val month = today.substring(0, 7)
            if (PiggyData.socialExemptMonth != month) {
                PiggyData.socialExemptMonth = month
                PiggyData.socialExemptUsed = 0
            }
            if (PiggyData.socialExemptUsed >= 3) return err("本月社交豁免次数已用完")
            if (PiggyData.socialExemptDate == today) return err("今天已经用过社交豁免了")
            // 晚餐已打卡则不可用豁免
            if (PiggyData.lastDinnerDate == today) return err("今天晚餐已经打卡，无需社交豁免")
            PiggyData.socialExemptUsed += 1
            PiggyData.socialExemptDate = today
            addTransaction("exempt", "social_exempt", 0f, "晚餐社交豁免(保留连续天数)")
            return ok(JSONObject().apply {
                put("used", PiggyData.socialExemptUsed)
                put("remaining", 3 - PiggyData.socialExemptUsed)
            })
        }

        @JavascriptInterface
        fun getSocialExemptStatus(): String {
            val today = todayStr()
            val month = today.substring(0, 7)
            if (PiggyData.socialExemptMonth != month) {
                PiggyData.socialExemptMonth = month
                PiggyData.socialExemptUsed = 0
            }
            return ok(JSONObject().apply {
                put("used", PiggyData.socialExemptUsed)
                put("remaining", 3 - PiggyData.socialExemptUsed)
                put("todayUsed", PiggyData.socialExemptDate == today)
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
            val withdrawCoin = Triple(-PiggyData.gold, -PiggyData.silver, -PiggyData.copper)
            PiggyData.copper = 0
            PiggyData.silver = 0
            PiggyData.gold = 0
            addTransaction("withdraw", "quarter_withdraw", balance, "季度提现: ¥$balance", withdrawCoin)
            return ok(report)
        }

        @JavascriptInterface
        fun getTransactions(): String {
            return try {
                ok(JSONArray(PiggyData.transactions))
            } catch (e: Exception) {
                ok(JSONArray())
            }
        }

        @JavascriptInterface
        fun getAutoViolations(): String {
            val today = todayStr()

            // 同一天只检测一次
            if (PiggyData.autoCheckDate == today) {
                return ok(JSONObject().put("violations", JSONArray()))
            }

            // 首次使用：记录安装日，不触发任何违规
            val firstUse = PiggyData.firstUseDate
            if (firstUse.isEmpty()) {
                PiggyData.firstUseDate = today
                PiggyData.autoCheckDate = today
                return ok(JSONObject().put("violations", JSONArray()))
            }

            // 安装当天不检测（给用户一个缓冲日）
            if (today == firstUse) {
                PiggyData.autoCheckDate = today
                return ok(JSONObject().put("violations", JSONArray()))
            }

            val yesterday = yesterdayStr()

            // 调用 AutoPenalty 执行检测 + 惩罚（含社交豁免逻辑）
            val violations = AutoPenalty.check(today, yesterday)

            val arr = JSONArray()
            violations.forEach { v ->
                if (v.exempted) {
                    arr.put("${v.desc} 已用社交豁免，免罚金，连续天数保留")
                } else {
                    arr.put("${v.desc} 违规(现金罚金¥${v.cashPenalty}) 连续天数已清零")
                    addTransaction("penalty", "penalty_cash", -v.cashPenalty.toFloat(),
                        "自动违规: ${v.desc} (¥${v.cashPenalty})", Triple(0, 0, 0))
                }
            }
            return ok(JSONObject().put("violations", arr))
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

        private fun addTransaction(type: String, subtype: String, amount: Float, note: String, coinChange: Triple<Int, Int, Int>? = null) {
            val arr = JSONArray(PiggyData.transactions)
            arr.put(JSONObject().apply {
                put("date", todayStr())
                put("type", type)
                put("subtype", subtype)
                put("amount", amount)
                put("note", note)
                put("time", System.currentTimeMillis())
                if (coinChange != null) {
                    put("coinChange", JSONObject().apply {
                        put("gold", coinChange.first)
                        put("silver", coinChange.second)
                        put("copper", coinChange.third)
                    })
                }
            })
            val result = JSONArray()
            val start = maxOf(0, arr.length() - 200)
            for (i in start until arr.length()) result.put(arr[i])
            PiggyData.transactions = result.toString()
        }

        private fun todayStr(): String {
            val cal = Calendar.getInstance()
            return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }

        private fun yesterdayStr(): String {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, -1)
            return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }

        private fun daysBetween(start: String, end: String): Int {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
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
