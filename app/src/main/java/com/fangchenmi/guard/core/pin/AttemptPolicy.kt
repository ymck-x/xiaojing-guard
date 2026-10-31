package com.fangchenmi.guard.core.pin

/**
 * 密码试错的退避策略。
 *
 * 光在界面上计数没用——关掉再打开就清零了。所以失败次数必须落盘（见 PinAttemptGuard），
 * 而这个纯函数决定「第 N 次输错之后要等多久」：
 * 前 4 次不惩罚（正常手滑），第 5 次起 30 秒、1 分、2 分……逐次翻倍，封顶 15 分钟。
 *
 * 8 位数字共一亿种组合。按这个节奏，连零头都试不完。
 */
object AttemptPolicy {

    /** 前几次输错不锁，给正常手滑留余量。 */
    const val FREE_ATTEMPTS = 4

    private const val BASE_LOCK_MS = 30_000L
    private const val MAX_LOCK_MS = 15 * 60_000L

    /** 累计输错 [failures] 次之后，需要锁多久（0 表示不锁）。 */
    fun lockoutDurationMillis(failures: Int): Long {
        if (failures <= FREE_ATTEMPTS) return 0L
        val step = (failures - FREE_ATTEMPTS - 1).coerceAtMost(5)
        val multiplier = 1L shl step
        return (BASE_LOCK_MS * multiplier).coerceAtMost(MAX_LOCK_MS)
    }

    /** 再输错一次之后的新状态。 */
    fun onFailure(failures: Int, nowMillis: Long): AttemptState {
        val next = failures + 1
        val lock = lockoutDurationMillis(next)
        return AttemptState(
            failures = next,
            lockedUntilMillis = if (lock > 0L) nowMillis + lock else 0L,
        )
    }

    fun isLocked(state: AttemptState, nowMillis: Long): Boolean =
        state.lockedUntilMillis > nowMillis

    /** 还剩几次「自由试错」机会，锁定期间没有意义。 */
    fun remainingFreeAttempts(state: AttemptState): Int =
        (FREE_ATTEMPTS - state.failures).coerceAtLeast(0)
}

data class AttemptState(
    val failures: Int = 0,
    val lockedUntilMillis: Long = 0L,
) {
    val clean: Boolean get() = failures == 0 && lockedUntilMillis == 0L
}
