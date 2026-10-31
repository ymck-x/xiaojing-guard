package com.fangchenmi.guard.core.usage

import kotlinx.serialization.Serializable

/**
 * 计时器的完整可持久化状态。它只由 [UsageMeter] 通过事件推移，
 * 任何时刻读出来都能继续接着算——包括进程被杀之后。
 */
@Serializable
data class MeterState(
    /** 该状态属于哪一天的配额。 */
    val dayKey: String = "",
    /** 上一次结算时间，用于把 [lastAtMillis, now] 这段时长记到当前前台应用头上。 */
    val lastAtMillis: Long = 0L,
    /** 当前前台应用。null 表示没有前台应用信息。 */
    val foregroundPkg: String? = null,
    val screenOn: Boolean = true,
    /**
     * 锁定遮挡层正在显示。此时孩子并没有在「玩」，所以一律不计时；
     * 否则强制休息的那几分钟会自己把休息额度又消耗掉，形成死循环。
     */
    val paused: Boolean = false,
    /** 何时进入「没有在玩（屏幕关 / 桌面 / 被忽略的应用）」的状态；0 表示当前正在计时。 */
    val idleSinceMillis: Long = 0L,
    /** 当天累计使用毫秒。 */
    val totalMs: Long = 0L,
    /** 当天每个应用累计使用毫秒。 */
    val perAppMs: Map<String, Long> = emptyMap(),
    /** 本轮连续使用毫秒，到阈值触发强制休息。 */
    val sessionMs: Long = 0L,
    /** 强制休息截止时间。 */
    val breakUntilMillis: Long = 0L,
    /** 今天被强制休息过几次（给家长看）。 */
    val breaksToday: Int = 0,
    /** 今天临时解锁过几次。跨日自动归零。 */
    val unlocksToday: Int = 0,
    /** 今天临时解锁累计给了多少分钟（给家长看）。 */
    val unlockMinutesToday: Int = 0,
) {
    fun usedOf(pkg: String?): Long = if (pkg == null) 0L else perAppMs[pkg] ?: 0L
}

/** 推动计时器的事件。 */
sealed interface MeterEvent {
    /** 前台应用变化。 */
    data class Foreground(val pkg: String?) : MeterEvent

    /** 亮屏 / 息屏。 */
    data class Screen(val on: Boolean) : MeterEvent

    /** 锁定遮挡层显示 / 收起。 */
    data class Pause(val paused: Boolean) : MeterEvent

    /** 周期性心跳，用于让计时随时间推进。 */
    data object Tick : MeterEvent
}

/** 计时器需要的外部信息，打包传入以保持 reduce 为纯函数。 */
data class MeterContext(
    /** 当前应用是否会占用时长（学习类、桌面、系统界面都不占）。 */
    val isCounted: (String?) -> Boolean,
    /** 单次连续时长上限（分钟），0 = 不限。 */
    val sessionLimitMinutes: Int,
    /** 强制休息时长（分钟）。 */
    val breakMinutes: Int,
)
