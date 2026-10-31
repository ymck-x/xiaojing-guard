package com.fangchenmi.guard.core.policy

/**
 * 免费版与付费版的能力边界。
 *
 * 这里的限制必须在**判定层**生效，而不只是界面里把按钮藏起来——
 * 否则改一下本地配置就能绕过去。
 *
 * 划分原则：免费版要能真的「管住」（总时长、停机时段、整屏锁、密码、防卸载都完整），
 * 付费版卖的是「管得细」和「省心」。
 */
data class GuardLimits(
    val pro: Boolean,
) {
    /** 免费版最多能把几个应用拉进「禁止」名单。 */
    val maxBlockedApps: Int get() = if (pro) Int.MAX_VALUE else 3

    /** 能否把应用设为「学习类不计时」。免费版保留——不然会误伤网课，家长立刻骂。 */
    val allowStudyCategory: Boolean get() = true

    /** 能否给单个应用设独立限额。 */
    val allowPerAppLimit: Boolean get() = pro

    /** 单次连续时长 + 强制休息（护眼）。 */
    val allowBreak: Boolean get() = pro

    /** 每天最多临时解锁几次；付费版由家长配置决定。 */
    val maxUnlocksPerDay: Int get() = if (pro) Int.MAX_VALUE else 1

    /** 每次临时解锁最长多少分钟；付费版由家长配置决定。 */
    val maxUnlockMinutes: Int get() = if (pro) 120 else 10

    /** 使用报告。 */
    val allowReport: Boolean get() = pro

    companion object {
        val PRO = GuardLimits(pro = true)
        val FREE = GuardLimits(pro = false)
    }
}
