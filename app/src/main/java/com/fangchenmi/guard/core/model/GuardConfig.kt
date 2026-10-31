package com.fangchenmi.guard.core.model

import kotlinx.serialization.Serializable

/** 应用分类：决定该应用是否计入时长、是否完全禁止。 */
@Serializable
enum class AppCategory {
    /** 学习/工具类：不计入总时长，也不触发休息，永不拦截。 */
    STUDY,

    /** 娱乐类：计入总时长，可单独设置每日限额。 */
    ENTERTAIN,

    /** 完全禁止使用。 */
    BLOCK,
}

@Serializable
data class AppRule(
    val pkg: String,
    val label: String = "",
    val category: AppCategory = AppCategory.ENTERTAIN,
    /**
     * 该应用单独的每日限额（分钟）。
     * 0 表示不单独限制，只计入全局总时长。仅对 [AppCategory.ENTERTAIN] 生效。
     */
    val dailyLimitMinutes: Int = 0,
)

@Serializable
data class PinCredential(
    val saltBase64: String = "",
    val hashBase64: String = "",
    val iterations: Int = 120_000,
    /** PIN 位数。新建固定 8 位，4 位只用于兼容老数据。 */
    val length: Int = 4,
) {
    val configured: Boolean get() = hashBase64.isNotEmpty() && saltBase64.isNotEmpty()
}

/**
 * 家长的全部守护规则。整个对象作为一个 JSON 落盘，读取时整体替换。
 */
@Serializable
data class GuardConfig(
    /** 守护总开关。关闭时应用不做任何拦截。 */
    val enabled: Boolean = false,

    val pin: PinCredential = PinCredential(),

    /** 每日累计可用时长（分钟）。0 = 不限。 */
    val totalDailyMinutes: Int = 120,

    /** 单次连续可用时长（分钟）。0 = 不限。 */
    val sessionMinutes: Int = 45,

    /** 每次到点后强制休息的时长（分钟）。 */
    val breakMinutes: Int = 10,

    /** 停机时段开关（睡觉、上课）。 */
    val bedtimeEnabled: Boolean = true,

    /** 停机开始时间，当天第几分钟。 */
    val bedtimeStartMinute: Int = 21 * 60,

    /** 停机结束时间，当天第几分钟。 */
    val bedtimeEndMinute: Int = 7 * 60,

    /** 每日额度的重置时刻（0 = 零点）。 */
    val dayStartHour: Int = 0,

    val appRules: List<AppRule> = emptyList(),

    /** 未单独分类的应用按什么处理。 */
    val uncategorizedCategory: AppCategory = AppCategory.ENTERTAIN,

    /** 系统「设置」是否禁止（否则孩子可以自己去关掉守护）。 */
    val blockSettings: Boolean = true,

    /** 家长临时解锁的截止时间；0 表示没有临时解锁。 */
    val unlockUntilMillis: Long = 0L,

    /** 每次临时解锁给多少分钟。 */
    val unlockMinutes: Int = 10,

    /** 每天最多临时解锁几次。0 = 不限（不建议）。 */
    val maxUnlocksPerDay: Int = 3,
) {
    fun ruleFor(pkg: String?): AppRule? =
        if (pkg == null) null else appRules.firstOrNull { it.pkg == pkg }

    /** 某应用的实际分类（未配置时用默认值）。 */
    fun categoryOf(pkg: String?): AppCategory =
        ruleFor(pkg)?.category ?: uncategorizedCategory
}
