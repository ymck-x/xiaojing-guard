package com.fangchenmi.guard.data

import android.content.Context
import com.fangchenmi.guard.core.model.PinCredential
import com.fangchenmi.guard.core.pin.AttemptPolicy
import com.fangchenmi.guard.core.pin.AttemptState
import com.fangchenmi.guard.core.pin.PinHasher

/**
 * 密码校验 + 失败次数落盘。
 *
 * 之所以要单独一个类：界面上（Compose 的密码框）和锁屏上（无障碍悬浮层的密码键盘）
 * 都要校验密码，如果各自计数，孩子只要换一个入口重试就能绕过去。这里只有一份计数。
 */
class PinAttemptGuard(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    sealed interface Result {
        data object Ok : Result

        /** 密码错，还剩 [remainingFreeAttempts] 次自由尝试机会。 */
        data class Wrong(val remainingFreeAttempts: Int) : Result

        /** 试错太多次，锁到 [untilMillis]。 */
        data class Locked(val untilMillis: Long) : Result
    }


    fun stateSnapshot(): AttemptState = AttemptState(
        failures = prefs.getInt(KEY_FAILURES, 0),
        lockedUntilMillis = prefs.getLong(KEY_LOCKED_UNTIL, 0L),
    )

    fun verify(
        pin: String,
        credential: PinCredential,
        nowMillis: Long = System.currentTimeMillis(),
    ): Result {
        val current = stateSnapshot()
        if (AttemptPolicy.isLocked(current, nowMillis)) {
            return Result.Locked(current.lockedUntilMillis)
        }
        if (PinHasher.verify(pin, credential)) {
            reset()
            return Result.Ok
        }
        val next = AttemptPolicy.onFailure(current.failures, nowMillis)
        prefs.edit()
            .putInt(KEY_FAILURES, next.failures)
            .putLong(KEY_LOCKED_UNTIL, next.lockedUntilMillis)
            .commit()
        return if (AttemptPolicy.isLocked(next, nowMillis)) {
            Result.Locked(next.lockedUntilMillis)
        } else {
            Result.Wrong(AttemptPolicy.remainingFreeAttempts(next))
        }
    }

    /** 家长成功解锁或改了密码之后调用。 */
    fun reset() {
        prefs.edit().putInt(KEY_FAILURES, 0).putLong(KEY_LOCKED_UNTIL, 0L).commit()
    }

    private companion object {
        const val PREFS = "guard_pin_attempts"
        const val KEY_FAILURES = "failures"
        const val KEY_LOCKED_UNTIL = "locked_until"
    }
}
