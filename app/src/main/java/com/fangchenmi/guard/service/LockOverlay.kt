package com.fangchenmi.guard.service

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import com.fangchenmi.guard.R
import com.fangchenmi.guard.data.PinAttemptGuard
import com.fangchenmi.guard.core.policy.GuardStatus
import com.fangchenmi.guard.core.policy.LockReason
import java.util.Locale

/**
 * 锁定遮挡层。
 *
 * 用无障碍服务的 TYPE_ACCESSIBILITY_OVERLAY 窗口实现：这个窗口盖在一切应用之上，
 * 不需要额外权限，孩子点不到下面的任何东西，也没有「关掉它」的入口。
 *
 * 画面走原生 View 而不是 Compose——它处在拦截的关键路径上，要建得足够快、足够稳。
 * 小鲸和鱼群用 Compose 的绘图 API 一次性渲染成位图，避免锁屏期间持续重绘耗电。
 */
class LockOverlay(
    private val service: AccessibilityService,
    private val pinVerifier: (String) -> PinAttemptGuard.Result,
    private val pinLength: () -> Int,
    private val onUnlockRequest: () -> UnlockOutcome,
    /** 角落暗门：图标和通知都没了时，家长靠它进应用。 */
    private val onSecretEntry: () -> Unit = {},
) {

    private val windowManager: WindowManager = service.getSystemService(WindowManager::class.java)
    private val density = service.resources.displayMetrics.density

    private var root: FrameLayout? = null
    private var titleView: TextView? = null
    private var detailView: TextView? = null
    private var countdownView: TextView? = null

    private var pinPanel: View? = null
    private var dotsView: TextView? = null
    private var errorView: TextView? = null
    private var pinBuffer = StringBuilder()
    private var secretTaps = 0
    private var secretLastTapAt = 0L
    private var lockedOutUntil = 0L

    private var shownReason: LockReason? = null
    private var lastStatus: GuardStatus? = null

    val isShowing: Boolean get() = root != null

    fun show(status: GuardStatus): Boolean {
        if (root == null) {
            val view = buildLockView(status.reason)
            val params = newParams()
            try {
                windowManager.addView(view, params)
                root = view
            } catch (t: Throwable) {
                root = null
                return false
            }
        }
        shownReason = status.reason
        render(status)
        return true
    }

    fun update(status: GuardStatus) {
        if (root == null) return
        if (status.reason != shownReason) {
            // 原因变了（例如从「休息」变成「吃光了」），整块内容需要重建
            hide()
            show(status)
            return
        }
        render(status)
    }

    fun hide() {
        val view = root ?: return
        root = null
        shownReason = null
        runCatching { windowManager.removeView(view) }
        pinPanel = null
        dotsView = null
        errorView = null
    }

    /** 旋转屏幕后窗口尺寸要重建。 */
    fun onConfigurationChanged() {
        val status = lastStatus ?: return
        if (!isShowing) return
        hide()
        show(status)
    }

    // ---------------------------------------------------------------- 内容

    private fun render(status: GuardStatus) {
        lastStatus = status
        when (status.reason) {
            LockReason.BEDTIME -> {
                titleView?.text = "该睡觉啦"
                detailView?.text = "小鲸也要睡了\n明天早上再来找我玩吧"
                countdownView?.visibility = View.GONE
            }

            LockReason.TOTAL_LIMIT -> {
                titleView?.text = "吃光啦"
                detailView?.text = "今天的时间被我喝光了\n（今天已经用了 ${formatDuration(status.totalUsedMs)}）"
                countdownView?.visibility = View.GONE
            }

            LockReason.SESSION_BREAK -> {
                titleView?.text = "休息一下"
                detailView?.text = "连续玩了很久，眼睛也要吃口饭"
                countdownView?.visibility = View.VISIBLE
                countdownView?.text = "休息剩余 " + formatCountdown(status.breakRemainingMs)
            }

            else -> {
                titleView?.text = "暂停使用"
                detailView?.text = ""
                countdownView?.visibility = View.GONE
            }
        }
    }

    // ---------------------------------------------------------------- 视图

    private fun buildLockView(reason: LockReason): FrameLayout {
        val sleeping = reason == LockReason.BEDTIME || reason == LockReason.TOTAL_LIMIT
        val root = FrameLayout(service).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF070D21.toInt(), 0xFF0B1533.toInt(), 0xFF12224F.toInt()),
            )
        }
        val column = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(36f), dp(24f), dp(36f), dp(24f))
        }
        root.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER,
            ),
        )

        // 小鲸立绘，直接用素材包里的原图
        val scene = ImageView(service).apply {
            setImageResource(
                if (sleeping) R.drawable.whale_sleepy else R.drawable.whale_wave,
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        column.addView(scene, LinearLayout.LayoutParams(dp(190f), dp(262f)))
        // 轻轻浮动，不用每帧重绘
        ObjectAnimator.ofFloat(scene, View.TRANSLATION_Y, -dp(7f).toFloat(), dp(7f).toFloat()).apply {
            duration = 3_600L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            start()
        }

        titleView = TextView(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(14f), 0, 0)
        }
        column.addView(titleView)

        detailView = TextView(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFFAEB9D6.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12f), 0, 0)
            setLineSpacing(dp(5f).toFloat(), 1f)
        }
        column.addView(detailView)

        countdownView = TextView(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 38f)
            setTextColor(0xFF93C5FD.toInt())
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(20f), 0, 0)
        }
        column.addView(countdownView)

        val unlockButton = TextView(service).apply {
            text = "家长验证"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFFC7D2E8.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(30f), dp(13f), dp(30f), dp(13f))
            background = rounded(0x1FFFFFFF, dp(999f).toFloat(), stroke = 0x55607090)
            isClickable = true
            setOnClickListener { openPinPanel() }
        }
        column.addView(
            unlockButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(38f) },
        )

        // 右下角 72dp 的透明热区，连点 5 次（3 秒内）打开应用。
        // 前两下不给反馈（孩子误触看不出来）；第三下起浮出小圆点，
        // 家长能确认自己点对了，第五下直接进应用。
        val secretDots = TextView(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(0x66C7D2E8)
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
        }
        root.addView(
            secretDots,
            FrameLayout.LayoutParams(dp(120f), dp(24f), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = dp(24f)
                bottomMargin = dp(88f)
            },
        )

        val secret = View(service).apply {
            isClickable = true
            setOnClickListener {
                val now = System.currentTimeMillis()
                if (now - secretLastTapAt > 3000L) secretTaps = 0
                secretLastTapAt = now
                secretTaps++
                if (secretTaps >= 5) {
                    secretTaps = 0
                    secretDots.visibility = View.INVISIBLE
                    onSecretEntry()
                } else if (secretTaps >= 3) {
                    secretDots.text = "● ".repeat(secretTaps - 2).trim()
                    secretDots.visibility = View.VISIBLE
                }
            }
        }
        root.addView(
            secret,
            FrameLayout.LayoutParams(dp(72f), dp(72f), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = dp(12f)
                bottomMargin = dp(12f)
            },
        )

        return root
    }


    private fun openPinPanel() {
        val root = root ?: return
        if (pinPanel != null) return
        pinBuffer = StringBuilder()

        val scrim = FrameLayout(service).apply {
            setBackgroundColor(0xFC070D21.toInt())
            isClickable = true
        }
        val column = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32f), dp(24f), dp(32f), dp(24f))
        }
        scrim.addView(
            column,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        val title = TextView(service).apply {
            text = "输入家长密码"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        column.addView(title)

        dotsView = TextView(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(0xFF93C5FD.toInt())
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, dp(20f), 0, dp(8f))
        }
        column.addView(dotsView)

        errorView = TextView(service).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(0xFFFF9A8F.toInt())
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            setPadding(0, 0, 0, dp(8f))
        }
        column.addView(errorView)

        val grid = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "清除", "0", "⌫")
        keys.chunked(3).forEach { rowKeys ->
            val row = LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            rowKeys.forEach { key ->
                val keyView = TextView(service).apply {
                    text = key
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (key.length == 1) 24f else 15f)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    background = rounded(0x1AFFFFFF, dp(16f).toFloat())
                    isClickable = true
                    setOnClickListener { onKey(key) }
                }
                val lp = LinearLayout.LayoutParams(dp(78f), dp(60f))
                lp.setMargins(dp(5f), dp(5f), dp(5f), dp(5f))
                row.addView(keyView, lp)
            }
            grid.addView(row)
        }
        column.addView(grid)

        val cancel = TextView(service).apply {
            text = "取消"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(0xFFAEB9D6.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(20f), dp(18f), dp(20f), dp(6f))
            isClickable = true
            setOnClickListener { closePinPanel() }
        }
        column.addView(cancel)

        root.addView(
            scrim,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        pinPanel = scrim
        renderDots()
    }

    private fun closePinPanel() {
        val panel = pinPanel ?: return
        pinPanel = null
        dotsView = null
        errorView = null
        root?.removeView(panel)
    }

    private fun onKey(key: String) {
        errorView?.visibility = View.INVISIBLE
        when (key) {
            "清除" -> pinBuffer = StringBuilder()
            "⌫" -> if (pinBuffer.isNotEmpty()) pinBuffer.deleteCharAt(pinBuffer.length - 1)
            else -> {
                if (System.currentTimeMillis() < lockedOutUntil) {
                    showError("尝试次数过多，请稍后再试")
                    return
                }
                val length = pinLength().coerceIn(4, 8)
                if (pinBuffer.length < length) pinBuffer.append(key)
                if (pinBuffer.length == length) {
                    val candidate = pinBuffer.toString()
                    pinBuffer = StringBuilder()
                    when (val result = pinVerifier(candidate)) {
                        is PinAttemptGuard.Result.Ok -> {
                            when (val outcome = onUnlockRequest()) {
                                is UnlockOutcome.Granted -> {
                                    closePinPanel()
                                    hide()
                                }

                                UnlockOutcome.QuotaExhausted ->
                                    showError("今天的临时解锁次数用完了，明天再来吧")

                                UnlockOutcome.NoPin ->
                                    showError("还没有设置家长密码")
                            }
                        }

                        is PinAttemptGuard.Result.Wrong -> showError(
                            if (result.remainingFreeAttempts > 0) {
                                "密码不对，还能再试 ${result.remainingFreeAttempts} 次"
                            } else {
                                "密码不对，再错一次就要等一会儿了"
                            },
                        )

                        is PinAttemptGuard.Result.Locked -> {
                            lockedOutUntil = result.untilMillis
                            showError("输错次数太多，请稍后再试")
                        }
                    }
                }
            }
        }
        renderDots()
    }

    private fun renderDots() {
        val length = pinLength().coerceIn(4, 8)
        val filled = pinBuffer.length
        dotsView?.text = buildString {
            repeat(length) { i -> append(if (i < filled) "●" else "○") }
        }
    }

    private fun showError(message: String) {
        errorView?.text = message
        errorView?.visibility = View.VISIBLE
    }

    // ---------------------------------------------------------------- 工具

    private fun newParams(): WindowManager.LayoutParams {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.title = "fangchenmi-lock"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lp.fitInsetsTypes = 0
        }
        return lp
    }

    private fun rounded(fill: Int, radius: Float, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius
            if (stroke != 0) setStroke(dp(1f), stroke)
        }

    private fun dp(value: Float): Int = (value * density).toInt()

    private fun formatCountdown(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.CHINA, "%02d:%02d", total / 60, total % 60)
    }

    private fun formatDuration(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes >= 60) "${minutes / 60} 小时 ${minutes % 60} 分" else "$minutes 分钟"
    }

}
