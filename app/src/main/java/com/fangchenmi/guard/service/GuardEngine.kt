package com.fangchenmi.guard.service

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.fangchenmi.guard.BuildConfig
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.core.policy.Counting
import com.fangchenmi.guard.core.policy.GuardPolicy
import com.fangchenmi.guard.core.policy.GuardStatus
import com.fangchenmi.guard.core.policy.UnlockPolicy
import com.fangchenmi.guard.core.policy.LockReason
import com.fangchenmi.guard.core.time.Days
import com.fangchenmi.guard.core.usage.MeterEvent
import com.fangchenmi.guard.core.usage.MeterState
import com.fangchenmi.guard.core.usage.UsageMeter
import com.fangchenmi.guard.data.ConfigStore
import com.fangchenmi.guard.data.PackageFacts
import com.fangchenmi.guard.data.UsageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

/**
 * 守护的执行中枢。
 *
 * 职责只有三件事：把事件推给纯逻辑层（[UsageMeter] / [GuardPolicy]）、
 * 按结论去操作系统（悬浮锁屏 / 弹回桌面）、把状态落盘。
 * 所有判定都在 [GuardPolicy] 里，这里不做任何「如果…就…」的业务判断。
 */
class GuardEngine(
    private val context: Application,
    private val configStore: ConfigStore,
    private val usageStore: UsageStore,
    private val facts: PackageFacts,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val zone: ZoneId = ZoneId.systemDefault()

    private var state: MeterState = usageStore.load().let {
        // 上一次运行可能是几天前；把 lastAt 拉回现在，避免把中断期算成使用时间。
        if (it.lastAtMillis > 0L) it.copy(lastAtMillis = 0L) else it
    }

    private val _usage = MutableStateFlow(state)
    val usage: StateFlow<MeterState> = _usage.asStateFlow()

    private val _status = MutableStateFlow(GuardStatus())
    val status: StateFlow<GuardStatus> = _status.asStateFlow()

    private var service: AccessibilityService? = null
    private var overlay: LockOverlay? = null

    private var attachedAt = 0L
    private var foregroundSeenAt = 0L
    private var lastSaveAt = 0L
    private var lastBouncePkg: String? = null
    private var lastBounceAt = 0L
    private var lastDegradedToastAt = 0L
    private var ticking = false
    private var screenOn = state.screenOn

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            if (ticking) handler.postDelayed(this, 1_000L)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> onScreenChanged(true)
                Intent.ACTION_SCREEN_OFF -> onScreenChanged(false)
            }
        }
    }

    // ------------------------------------------------------------------ 生命周期

    fun start() {
        ContextCompat.registerReceiver(
            context,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ensureTicker()
    }

    fun attach(service: AccessibilityService, overlay: LockOverlay) {
        this.service = service
        this.overlay = overlay
        attachedAt = System.currentTimeMillis()
        foregroundSeenAt = 0L
        ensureTicker()
        // 刚连上时立刻结算一次，避免错过一个心跳。
        tick()
    }

    fun detach(service: AccessibilityService) {
        if (this.service === service) {
            // 服务被系统关掉时，遮挡层窗口不会自己消失——必须在这里主动摘掉，
            // 否则会留下一块点不掉、也没人负责收起的「时间到」屏。
            overlay?.hide()
            this.service = null
            this.overlay = null
        }
    }

    fun onConfigurationChanged() {
        overlay?.onConfigurationChanged()
    }

    // ------------------------------------------------------------------ 事件入口

    /** 无障碍服务报告：前台换成了 pkg。 */
    fun onForeground(pkg: String?) {
        val now = System.currentTimeMillis()
        foregroundSeenAt = now
        val config = configStore.current()
        state = UsageMeter.reduce(state, MeterEvent.Foreground(pkg), now, dayKey(now, config), meterContext(config))
        publish(now)
    }

    private fun onScreenChanged(on: Boolean) {
        screenOn = on
        val now = System.currentTimeMillis()
        val config = configStore.current()
        state = UsageMeter.reduce(state, MeterEvent.Screen(on), now, dayKey(now, config), meterContext(config))
        if (!on) {
            // 息屏是最安全的落盘点
            usageStore.save(state)
            lastSaveAt = now
        }
        publish(now)
        ensureTicker()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val config = configStore.current()
        clearBlindWindowIfNeeded(now, config)
        state = UsageMeter.reduce(state, MeterEvent.Tick, now, dayKey(now, config), meterContext(config))
        if (now / 1000 % 30 == 0L) {
            Log.v(
                TAG,
                "tick fg=${state.foregroundPkg} used=${state.totalMs / 1000}s " +
                    "session=${state.sessionMs / 1000}s paused=${state.paused}",
            )
        }
        publish(now)
    }

    /** 供界面调用：读一次最新状态并顺带刷新。 */
    fun refresh() = tick()

    // ------------------------------------------------------------------ 判定与执行

    private fun publish(now: Long) {
        val config = configStore.current()
        val status = GuardPolicy.evaluate(
            config = config,
            state = state,
            subjectPkg = state.foregroundPkg,
            nowMillis = now,
            zone = zone,
            ignoredPackages = facts.ignoredPackages,
            selfPackages = facts.selfPackages,
            settingsPackages = facts.settingsPackages,
        )
        if (BuildConfig.DEBUG && state.foregroundPkg != lastLoggedPkg) {
            lastLoggedPkg = state.foregroundPkg
            val pkg = state.foregroundPkg
            Log.i(
                TAG,
                "fg=$pkg enabled=${config.enabled} blockSettings=${config.blockSettings} " +
                    "inSettings=${pkg != null && pkg in facts.settingsPackages} " +
                    "inIgnored=${pkg != null && pkg in facts.ignoredPackages} " +
                    "cat=${config.categoryOf(pkg)} " +
                    "decision=${status.locked}/${status.scope}/${status.reason}",
            )
        }
        _usage.value = state
        _status.value = status
        enforce(status, now, config)
        persistIfDue(now)
    }

    private var lastLoggedPkg: String? = null

    private fun enforce(status: GuardStatus, now: Long, config: GuardConfig) {
        if (status.locked != lastLoggedLocked || status.reason != lastLoggedReason) {
            lastLoggedLocked = status.locked
            lastLoggedReason = status.reason
            Log.i(
                TAG,
                "enforce locked=${status.locked} scope=${status.scope} reason=${status.reason} " +
                    "fg=${state.foregroundPkg} used=${state.totalMs / 1000}s " +
                    "limit=${config.totalDailyMinutes}min overlay=${overlay != null}",
            )
        }
        if (!status.enforced) {
            setPaused(false, now, config)
            overlay?.hide()
            return
        }
        if (status.globalLocked) {
            setPaused(true, now, config)
            val ov = overlay
            val attached = when {
                ov == null -> false
                ov.isShowing -> { ov.update(status); true }
                else -> ov.show(status)
            }
            if (!attached) {
                // 悬浮层在某些 ROM 上可能被拒绝；退化成「不停弹回桌面 + 提示」，
                // 虽然体验差，但总比默默失效强。
                degradedBounce(now, status)
            }
            return
        }
        // 家长临时解锁期间同样不计时：这十分钟是家长在用，不该烧孩子的额度。
        setPaused(status.unlockRemainingMs > 0L, now, config)
        overlay?.hide()
        if (status.appLocked) bounceOut(now, status.reason)
    }

    private var lastLoggedLocked = false
    private var lastLoggedReason: LockReason? = null

    /**
     * 不计时的两种情形：锁定遮挡层显示中、家长临时解锁中。
     *
     * 锁屏期间必须暂停，否则强制休息的十分钟会把自己又"玩"掉一次，形成死循环；
     * 家长解锁期间暂停，是因为这十分钟不属于孩子在玩。
     * 这个事件必须在判定之后、下一次心跳之前进入状态。
     */
    private fun setPaused(paused: Boolean, now: Long, config: GuardConfig) {
        if (state.paused == paused) return
        state = UsageMeter.reduce(state, MeterEvent.Pause(paused), now, dayKey(now, config), meterContext(config))
        _usage.value = state
    }

    private fun bounceOut(now: Long, reason: LockReason) {
        val svc = service ?: return
        val pkg = state.foregroundPkg ?: return
        if (pkg == lastBouncePkg && now - lastBounceAt < 800L) return
        lastBouncePkg = pkg
        lastBounceAt = now

        val label = facts.labelOf(pkg).toString()
        val message = when (reason) {
            LockReason.APP_LIMIT -> "「$label」今天的时长已经用完啦"
            LockReason.BLOCKED_APP ->
                if (facts.isSettings(pkg)) "设置需要家长密码才能进入" else "「$label」不能使用"
            else -> "现在还不能使用"
        }
        runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        runCatching { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    /** 悬浮层加不上（个别系统会拒绝）时的降级：不停弹回桌面。 */
    private fun degradedBounce(now: Long, status: GuardStatus) {
        val svc = service ?: return
        if (now - lastDegradedToastAt > 5_000L) {
            lastDegradedToastAt = now
            val message = when (status.reason) {
                LockReason.BEDTIME -> "现在是休息时间"
                LockReason.TOTAL_LIMIT -> "今天的时长用完啦"
                LockReason.SESSION_BREAK -> "该休息一下了"
                else -> "现在不能使用"
            }
            runCatching { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
        if (now - lastBounceAt > 1_500L) {
            lastBounceAt = now
            runCatching { svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
        }
    }

    private fun persistIfDue(now: Long) {
        if (now - lastSaveAt < 5_000L) return
        lastSaveAt = now
        usageStore.save(state)
    }

    private fun ensureTicker() {
        val shouldTick = configStore.current().enabled && (screenOn || service != null)
        if (shouldTick && !ticking) {
            ticking = true
            handler.postDelayed(tickRunnable, 1_000L)
        } else if (!shouldTick && ticking) {
            ticking = false
            handler.removeCallbacks(tickRunnable)
        }
    }

    /**
     * 无障碍服务刚连上时，我们并不知道前台是哪个应用——如果孩子这时正开着被禁的应用，
     * 在下次切换窗口之前守护是「瞎」的。超过 3 秒还没收到任何事件，就当作盲区，
     * 先弹回桌面一次，把状态拉回已知。
     */
    private fun clearBlindWindowIfNeeded(now: Long, config: GuardConfig) {
        if (!config.enabled) return
        if (foregroundSeenAt != 0L) return
        if (attachedAt <= 0L || now - attachedAt < 3_000L) return
        foregroundSeenAt = now
        Log.i(TAG, "blind window: no accessibility event in 3s, forcing home")
        runCatching { service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    /** 家长改了配置后调用，让开关立刻生效。 */
    fun onConfigChanged() {
        ensureTicker()
        tick()
    }

    /**
     * 临时解锁。三个入口（首页按钮、设置页、锁屏密码）都走这里，
     * 保证次数配额只有一份账。
     */
    fun requestUnlock(): UnlockOutcome {
        val now = System.currentTimeMillis()
        val config = configStore.current()
        if (!config.pin.configured) return UnlockOutcome.NoPin
        if (!UnlockPolicy.canUnlock(config, state)) {
            return UnlockOutcome.QuotaExhausted
        }
        state = UnlockPolicy.applyUnlock(config, state)
        usageStore.save(state)
        configStore.update { it.copy(unlockUntilMillis = UnlockPolicy.unlockUntil(it, now)) }
        onConfigChanged()
        _usage.value = state
        Log.i(TAG, "unlock granted, unlocksToday=${state.unlocksToday} minutes=${config.unlockMinutes}")
        return UnlockOutcome.Granted(UnlockPolicy.minutesPerUnlock(config))
    }

    private fun dayKey(now: Long, config: GuardConfig): String =
        Days.dayKey(now, zone, config.dayStartHour)

    private fun meterContext(config: GuardConfig) =
        Counting.meterContext(config, facts.ignoredPackages, facts.selfPackages)

    private companion object {
        const val TAG = "FangChenMi"
    }
}

/** 临时解锁的结果。次数用完时孩子点多少下都拿不到时间。 */
sealed interface UnlockOutcome {
    data class Granted(val minutes: Int) : UnlockOutcome
    data object QuotaExhausted : UnlockOutcome
    data object NoPin : UnlockOutcome
}