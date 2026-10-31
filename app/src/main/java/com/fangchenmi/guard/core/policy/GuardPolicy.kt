package com.fangchenmi.guard.core.policy

import com.fangchenmi.guard.core.model.AppCategory
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.core.time.Days
import com.fangchenmi.guard.core.usage.MeterContext
import com.fangchenmi.guard.core.usage.MeterState
import java.time.ZoneId

/** 拦截作用范围。APP 只挡这一个应用，GLOBAL 挡住整台平板。 */
enum class LockScope { NONE, APP, GLOBAL }

enum class LockReason { NONE, BEDTIME, SESSION_BREAK, TOTAL_LIMIT, APP_LIMIT, BLOCKED_APP }

data class GuardStatus(
    val locked: Boolean = false,
    val scope: LockScope = LockScope.NONE,
    val reason: LockReason = LockReason.NONE,
    val totalLimitMinutes: Int = 0,
    val totalUsedMs: Long = 0L,
    val appUsedMs: Long = 0L,
    val appLimitMinutes: Int = 0,
    val breakRemainingMs: Long = 0L,
    val bedtimeEndsAtMillis: Long = 0L,
    val unlockRemainingMs: Long = 0L,
    val enforced: Boolean = false,
    /** 今天还能临时解锁几次；-1 表示不限。 */
    val unlocksLeftToday: Int = 0,
) {
    /** 今日剩余总时长；-1 表示不限。 */
    val remainingTotalMs: Long
        get() = if (totalLimitMinutes <= 0) -1L
        else (totalLimitMinutes * 60_000L - totalUsedMs).coerceAtLeast(0L)

    val globalLocked: Boolean get() = locked && scope == LockScope.GLOBAL
    val appLocked: Boolean get() = locked && scope == LockScope.APP
}

/** 应用是否占用时长：只有「娱乐类」应用才计时。学习、工具、桌面、系统界面都不计。 */
object Counting {

    fun isCounted(
        config: GuardConfig,
        pkg: String?,
        ignoredPackages: Set<String>,
        selfPackages: Set<String>,
        limits: GuardLimits = GuardLimits.PRO,
    ): Boolean {
        if (pkg == null) return false
        if (pkg in selfPackages) return false
        if (pkg in ignoredPackages) return false
        return effectiveCategory(config, pkg, limits) == AppCategory.ENTERTAIN
    }

    /**
     * 免费版不提供「学习类不计时」的细分？—— 提供。
     * 但免费版不提供「单独限额」，所以这里只做分类归一化。
     */
    fun effectiveCategory(config: GuardConfig, pkg: String?, limits: GuardLimits): AppCategory {
        val raw = config.categoryOf(pkg)
        return if (!limits.allowStudyCategory && raw == AppCategory.STUDY) AppCategory.ENTERTAIN else raw
    }

    fun meterContext(
        config: GuardConfig,
        ignoredPackages: Set<String>,
        selfPackages: Set<String>,
        limits: GuardLimits = GuardLimits.PRO,
    ): MeterContext = MeterContext(
        isCounted = { pkg -> isCounted(config, pkg, ignoredPackages, selfPackages, limits) },
        // 免费版没有强制休息：连续时长直接放空，免得休息倒计时把免费用户挡在外面。
        sessionLimitMinutes = if (limits.allowBreak) config.sessionMinutes else 0,
        breakMinutes = if (limits.allowBreak) config.breakMinutes else 0,
    )
}

/**
 * 唯一的判定入口。UI、无障碍服务、悬浮层都只认这里给出的结论，
 * 避免「界面上显示还能玩、实际上已经被拦」这类不一致。
 */
object GuardPolicy {

    fun evaluate(
        config: GuardConfig,
        state: MeterState,
        subjectPkg: String?,
        nowMillis: Long,
        zone: ZoneId,
        ignoredPackages: Set<String>,
        selfPackages: Set<String>,
        settingsPackages: Set<String>,
        limits: GuardLimits = GuardLimits.PRO,
    ): GuardStatus {
        val rule = config.ruleFor(subjectPkg)
        val base = GuardStatus(
            totalLimitMinutes = config.totalDailyMinutes,
            totalUsedMs = state.totalMs,
            appUsedMs = state.usedOf(subjectPkg),
            appLimitMinutes = rule?.dailyLimitMinutes ?: 0,
            enforced = config.enabled,
            unlocksLeftToday = UnlockPolicy.remainingUnlocks(config, state, limits),
        )

        // 注意：自己的应用不在这里提前放行。
        // 锁定遮挡层本身会触发「前台 = 我们自己」的无障碍事件，如果这里直接放行，
        // 锁屏会在显示的第一秒就把自己拆掉。所以全局锁（停机/额度/休息）对自己的应用同样生效，
        // 家长通过锁屏上的「家长验证」进设置。下面只豁免「分应用」这一层。
        if (!config.enabled) return base

        val unlockRemaining = (config.unlockUntilMillis - nowMillis).coerceAtLeast(0L)
        if (unlockRemaining > 0L) return base.copy(unlockRemainingMs = unlockRemaining)

        if (config.bedtimeEnabled) {
            val minute = Days.minuteOfDay(nowMillis, zone)
            if (Days.inWindow(minute, config.bedtimeStartMinute, config.bedtimeEndMinute)) {
                return base.copy(
                    locked = true,
                    scope = LockScope.GLOBAL,
                    reason = LockReason.BEDTIME,
                    bedtimeEndsAtMillis = Days.nextOccurrenceOfMinute(nowMillis, config.bedtimeEndMinute, zone),
                )
            }
        }

        if (limits.allowBreak && state.breakUntilMillis > nowMillis) {
            return base.copy(
                locked = true,
                scope = LockScope.GLOBAL,
                reason = LockReason.SESSION_BREAK,
                breakRemainingMs = state.breakUntilMillis - nowMillis,
            )
        }

        if (config.totalDailyMinutes > 0 && state.totalMs >= config.totalDailyMinutes * 60_000L) {
            return base.copy(locked = true, scope = LockScope.GLOBAL, reason = LockReason.TOTAL_LIMIT)
        }

        if (subjectPkg == null) return base
        if (subjectPkg in selfPackages) return base

        // 必须先判「设置」再判「忽略名单」：个别 ROM 把 FallbackHome 挂在 Settings 包里，
        // 导致设置同时属于桌面类。顺序反了的话，孩子只要进设置就能自己关掉守护。
        if (config.blockSettings && subjectPkg in settingsPackages) {
            return base.copy(locked = true, scope = LockScope.APP, reason = LockReason.BLOCKED_APP)
        }

        if (subjectPkg in ignoredPackages) return base

        when (Counting.effectiveCategory(config, subjectPkg, limits)) {
            AppCategory.STUDY -> return base
            AppCategory.BLOCK -> {
                // 免费版最多生效 3 个「禁止」，超出的不拦（家长设置时也会被挡住）。
                val blocked = config.appRules.filter { it.category == AppCategory.BLOCK }
                val index = blocked.indexOfFirst { it.pkg == subjectPkg }
                if (index in 0 until limits.maxBlockedApps) {
                    return base.copy(
                        locked = true, scope = LockScope.APP, reason = LockReason.BLOCKED_APP,
                    )
                }
                return base
            }
            AppCategory.ENTERTAIN -> Unit
        }

        val appLimit = if (limits.allowPerAppLimit) rule?.dailyLimitMinutes ?: 0 else 0
        if (appLimit > 0 && state.usedOf(subjectPkg) >= appLimit * 60_000L) {
            return base.copy(locked = true, scope = LockScope.APP, reason = LockReason.APP_LIMIT)
        }

        return base
    }
}
