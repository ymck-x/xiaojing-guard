package com.fangchenmi.guard.core.policy

import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.core.usage.MeterState

/**
 * 临时解锁的配额。
 *
 * 密码是唯一的凭证，所以「点一下就有 10 分钟」是不可能的——每次都要输密码。
 * 但密码一旦泄露，没有上限就等于开了一扇任意门，所以这里再设一道闸：
 * 每天最多临时解锁几次、每次给几分钟，都由家长设定。
 */
object UnlockPolicy {

    /** -1 表示不限次数。 */
    /** 免费版固定每天 1 次、每次 10 分钟；付费版听家长的。 */
    fun dailyLimit(config: GuardConfig, limits: GuardLimits): Int =
        if (config.maxUnlocksPerDay <= 0) -1
        else minOf(config.maxUnlocksPerDay, limits.maxUnlocksPerDay)

    fun remainingUnlocks(
        config: GuardConfig,
        state: MeterState,
        limits: GuardLimits = GuardLimits.PRO,
    ): Int {
        val daily = dailyLimit(config, limits)
        return if (daily < 0) -1 else (daily - state.unlocksToday).coerceAtLeast(0)
    }

    fun canUnlock(
        config: GuardConfig,
        state: MeterState,
        limits: GuardLimits = GuardLimits.PRO,
    ): Boolean {
        if (!config.pin.configured) return false
        return remainingUnlocks(config, state, limits) != 0
    }

    fun minutesPerUnlock(config: GuardConfig, limits: GuardLimits = GuardLimits.PRO): Int =
        config.unlockMinutes.coerceIn(1, limits.maxUnlockMinutes)

    fun unlockUntil(
        config: GuardConfig,
        nowMillis: Long,
        limits: GuardLimits = GuardLimits.PRO,
    ): Long = nowMillis + minutesPerUnlock(config, limits) * 60_000L

    /** 记一笔账：次数 +1，累计分钟数累加。 */
    fun applyUnlock(
        config: GuardConfig,
        state: MeterState,
        limits: GuardLimits = GuardLimits.PRO,
    ): MeterState = state.copy(
        unlocksToday = state.unlocksToday + 1,
        unlockMinutesToday = state.unlockMinutesToday + minutesPerUnlock(config, limits),
    )
}
