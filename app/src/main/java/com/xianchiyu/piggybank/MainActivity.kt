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
    private var lastActiveDate: String = ""

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

        val cal0 = Calendar.getInstance()
        lastActiveDate = String.format(Locale.US, "%04d-%02d-%02d",
            cal0.get(Calendar.YEAR), cal0.get(Calendar.MONTH) + 1, cal0.get(Calendar.DAY_OF_MONTH))

        webView = WebView(this)
        // 起始透明，冷启动期间透出系统 windowBackground 启动图，加载完淡入覆盖
        webView.alpha = 0f
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        // 透明背景：让 windowBackground（splash_window_bg 照片）在冷启动期间透出
        webView.setBackgroundColor(Color.TRANSPARENT)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // 导航白名单：只允许加载本地 asset 页面，其余 URL 一律拦截
                return !url.startsWith("file:///android_asset/")
            }
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

    override fun onResume() {
        super.onResume()
        // 检测日期是否变化（后台过夜等场景），变了就刷新页面触发自动违规检测
        val cal = Calendar.getInstance()
        val now = String.format(Locale.US, "%04d-%02d-%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        if (now != lastActiveDate) {
            lastActiveDate = now
            webView.loadUrl("file:///android_asset/www/index.html")
        }
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
        fun checkin(type: String, exerciseType: String, duration: Int, distance: Float, manualRainy: Boolean): String {
            val today = todayStr()

            val lastDate = when (type) {
                "exercise" -> PiggyData.lastExerciseDate
                "breakfast" -> PiggyData.lastBreakfastDate
                "dinner" -> PiggyData.lastDinnerDate
                "jingang" -> PiggyData.lastJingangDate
                "changshou" -> PiggyData.lastChangshouDate
                else -> return err("未知任务类型: $type")
            }
            if (lastDate == today) return err("今天已经打过此卡")

            // 晚餐已用社交豁免则不可打卡
            if (type == "dinner" && PiggyData.socialExemptDate == today) return err("今天已用社交豁免，晚餐免打卡")

            // 室内运动：手动勾选时固定3铜
            val isRainyToday = manualRainy

            val yesterday = yesterdayStr()
            val prevStreak = when (type) {
                "exercise" -> PiggyData.exerciseStreak
                "breakfast" -> PiggyData.breakfastStreak
                "dinner" -> PiggyData.dinnerStreak
                "jingang" -> PiggyData.jingangStreak
                "changshou" -> PiggyData.changshouStreak
                else -> 0
            }
            // 晚餐社交豁免日算作“跳过”：既不打卡也不断签，连续次数得以延续
            val twoDaysAgo = addDaysStr(yesterday, -1)
            val continued = lastDate == yesterday ||
                (type == "dinner" && lastDate == twoDaysAgo && PiggyData.socialExemptDate == yesterday)
            // 金刚功不进违规检测，次数不清零，每次打卡都往后累加
            val newStreak = if (type == "jingang") prevStreak + 1
                            else if (continued) prevStreak + 1 else 1

            val baseCoins = when (type) {
                "exercise" -> CoinUtils.exerciseCoins(exerciseType, duration, distance, isRainyToday)
                "breakfast" -> 3
                "dinner" -> 3
                "jingang" -> 3
                "changshou" -> 3
                else -> 0
            }
            val mult = CoinUtils.multiplier(newStreak)
            val coinsEarned = (baseCoins * mult).toInt()

            // 卡册：本轮累计次数（断签时已在违规检测中归零）
            val cardCountAfter = newStreak
            val cardCompleted = cardCountAfter >= CoinUtils.CARD_GOAL
            // 金刚功满一轮不兑金，只重来
            val cardGoldBonus = if (cardCompleted && type != "jingang") 1 else 0
            val cardNextCount = if (cardCompleted) 0 else cardCountAfter

            val oldCopper = PiggyData.copper
            val oldSilver = PiggyData.silver
            val oldGold = PiggyData.gold

            val (c, s, g) = CoinUtils.autoMerge(
                PiggyData.copper + coinsEarned,
                PiggyData.silver,
                PiggyData.gold + cardGoldBonus
            )
            PiggyData.copper = c
            PiggyData.silver = s
            PiggyData.gold = g

            when (type) {
                "exercise" -> {
                    PiggyData.exerciseStreak = cardNextCount
                    PiggyData.lastExerciseDate = today
                }
                "breakfast" -> {
                    PiggyData.breakfastStreak = cardNextCount
                    PiggyData.lastBreakfastDate = today
                }
                "dinner" -> {
                    PiggyData.dinnerStreak = cardNextCount
                    PiggyData.lastDinnerDate = today
                }
                "jingang" -> {
                    PiggyData.jingangStreak = cardNextCount
                    PiggyData.lastJingangDate = today
                }
                "changshou" -> {
                    PiggyData.changshouStreak = cardNextCount
                    PiggyData.lastChangshouDate = today
                }
            }

            val yuan = coinsEarned * 0.1f
            PiggyData.quarterIncome += coinsEarned

            addTransaction("income", type, yuan, "${typeLabel(type)} +${coinsEarned}铜(x${mult})", Triple(g - oldGold, s - oldSilver, c - oldCopper))

            // 卡册满勤兑金：只进交易流水，不弹提示（金刚功不兑金）
            if (cardCompleted && type != "jingang") {
                PiggyData.quarterIncome += 100
                addTransaction("card_bonus", type, 1f, "${typeLabel(type)}卡满勤 +1金", Triple(1, 0, 0))
            }

            PiggyData.saveDailyBalance()
            return ok(JSONObject().apply {
                put("coins", coinsEarned)
                put("multiplier", mult)
                put("streak", newStreak)
                put("cardCount", cardNextCount)
                put("cardTier", CoinUtils.cardTierKey(if (cardCompleted) CoinUtils.CARD_GOAL else cardCountAfter))
                put("cardCompleted", cardCompleted)
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
            val copperNeeded = Math.round(amount * 10)
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

            PiggyData.quarterExpense += copperNeeded
            addTransaction("expense", "purchase", -amount, note, Triple(g - oldGold, s - oldSilver, c - oldCopper))

            PiggyData.saveDailyBalance()
            return ok(JSONObject().apply {
                put("copper", c)
                put("silver", s)
                put("gold", g)
                put("yuan", CoinUtils.totalYuan(c, s, g))
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
            val balanceCopper = PiggyData.copper + PiggyData.silver * 10 + PiggyData.gold * 100
            return ok(JSONObject().apply {
                put("incomeCopper", PiggyData.quarterIncome)
                put("expenseCopper", PiggyData.quarterExpense)
                put("balanceCopper", balanceCopper)
                put("penaltyTotal", PiggyData.penaltyTotal)
            })
        }

        @JavascriptInterface
        fun quarterWithdraw(): String {
            val balance = CoinUtils.totalYuan(PiggyData.copper, PiggyData.silver, PiggyData.gold)
            val penalty = PiggyData.penaltyTotal
            val report = JSONObject().apply {
                put("balance", balance)
                put("penaltyTotal", penalty)
                put("date", todayStr())
            }
            PiggyData.quarterIncome = 0
            PiggyData.quarterExpense = 0
            PiggyData.penaltyTotal = 0
            val withdrawCoin = Triple(-PiggyData.gold, -PiggyData.silver, -PiggyData.copper)
            PiggyData.copper = 0
            PiggyData.silver = 0
            PiggyData.gold = 0
            addTransaction("withdraw", "quarter_withdraw", balance, "季度提现: ¥$balance (罚金¥$penalty)", withdrawCoin)
            PiggyData.saveDailyBalance()
            return ok(report)
        }

        @JavascriptInterface
        fun getCardStatus(): String {
            val today = todayStr()
            fun card(count: Int, checked: Boolean, exempt: Boolean = false) = JSONObject().apply {
                put("count", count)
                put("tier", CoinUtils.cardTierKey(count))
                put("checkedToday", checked)
                put("exemptToday", exempt)
            }
            return ok(JSONObject().apply {
                put("exercise", card(PiggyData.exerciseStreak, PiggyData.lastExerciseDate == today))
                put("breakfast", card(PiggyData.breakfastStreak, PiggyData.lastBreakfastDate == today))
                put("dinner", card(PiggyData.dinnerStreak, PiggyData.lastDinnerDate == today,
                    PiggyData.socialExemptDate == today))
                put("jingang", card(PiggyData.jingangStreak, PiggyData.lastJingangDate == today))
                put("changshou", card(PiggyData.changshouStreak, PiggyData.lastChangshouDate == today))
            })
        }

        @JavascriptInterface
        fun importLegacy(totalCopper: Int, penaltyTotal: Int): String {
            if (totalCopper < 0 || penaltyTotal < 0) return err("数值不能为负")
            // 已有非零数据则拒绝导入，避免误操作冲掉现有数据
            if (PiggyData.copper != 0 || PiggyData.silver != 0 || PiggyData.gold != 0 || PiggyData.penaltyTotal != 0) {
                return err("已有数据，不能导入")
            }
            if (totalCopper == 0 && penaltyTotal == 0) return err("没有要导入的数据")

            // 按自动合并规则拆分：铜≥15 进银，银≥15 进金
            var copper = totalCopper
            var silver = 0
            var gold = 0
            if (copper >= 15) {
                val n = (copper - 5) / 10
                silver = n
                copper -= n * 10
            }
            if (silver >= 15) {
                val n = (silver - 5) / 10
                gold = n
                silver -= n * 10
            }

            PiggyData.copper = copper
            PiggyData.silver = silver
            PiggyData.gold = gold
            PiggyData.penaltyTotal = penaltyTotal
            PiggyData.quarterIncome = totalCopper
            PiggyData.saveDailyBalance()
            return ok(JSONObject().apply {
                put("copper", copper)
                put("silver", silver)
                put("gold", gold)
                put("penaltyTotal", penaltyTotal)
                put("yuan", CoinUtils.totalYuan(copper, silver, gold))
            })
        }

        @JavascriptInterface
        fun canImportLegacy(): String {
            val hasData = PiggyData.copper != 0 || PiggyData.silver != 0 ||
                PiggyData.gold != 0 || PiggyData.penaltyTotal != 0
            return ok(JSONObject().apply {
                put("hasData", hasData)
                put("copper", PiggyData.copper)
                put("silver", PiggyData.silver)
                put("gold", PiggyData.gold)
                put("penaltyTotal", PiggyData.penaltyTotal)
            })
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

            // 首次使用：记录安装日
            if (PiggyData.firstUseDate.isEmpty()) {
                PiggyData.firstUseDate = today
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
            PiggyData.saveDailyBalance()
            return ok(JSONObject().put("violations", arr))
        }

        @JavascriptInterface
        fun getDailyBalances(): String {
            return try {
                ok(JSONObject(PiggyData.dailyBalance))
            } catch (e: Exception) {
                ok(JSONObject())
            }
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

        private fun addDaysStr(dateStr: String, n: Int): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d = fmt.parse(dateStr) ?: return dateStr
            val cal = Calendar.getInstance()
            cal.time = d
            cal.add(Calendar.DAY_OF_MONTH, n)
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
            "jingang" -> "金刚功"
            "changshou" -> "长寿功"
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
