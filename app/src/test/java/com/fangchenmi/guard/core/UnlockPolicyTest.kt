package com.fangchenmi.guard.core

import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.core.model.PinCredential
import com.fangchenmi.guard.core.pin.AttemptPolicy
import com.fangchenmi.guard.core.pin.AttemptState
import com.fangchenmi.guard.core.policy.GuardPolicy
import com.fangchenmi.guard.core.policy.UnlockPolicy
import com.fangchenmi.guard.core.usage.MeterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class UnlockPolicyTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now = 1_705_284_000_000L

    private fun config(maxPerDay: Int = 3, minutes: Int = 10) = GuardConfig(
        enabled = true,
        pin = PinCredential(saltBase64 = "c2FsdA==", hashBase64 = "aGFzaA==", iterations = 1),
        maxUnlocksPerDay = maxPerDay,
        unlockMinutes = minutes,
    )

    @Test
    fun `次数用完之后不能再解锁`() {
        val cfg = config(maxPerDay = 3)
        var state = MeterState()
        repeat(3) {
            assertTrue(UnlockPolicy.canUnlock(cfg, state))
            state = UnlockPolicy.applyUnlock(cfg, state)
        }
        assertEquals(3, state.unlocksToday)
        assertEquals(30, state.unlockMinutesToday)
        assertEquals(0, UnlockPolicy.remainingUnlocks(cfg, state))
        assertFalse("用完 3 次后必须拒绝", UnlockPolicy.canUnlock(cfg, state))
    }

    @Test
    fun `次数上限为 0 表示不限`() {
        val cfg = config(maxPerDay = 0)
        val state = MeterState(unlocksToday = 99)
        assertEquals(-1, UnlockPolicy.remainingUnlocks(cfg, state))
        assertTrue(UnlockPolicy.canUnlock(cfg, state))
    }

    @Test
    fun `没设密码时不允许解锁`() {
        val cfg = config().copy(pin = PinCredential())
        assertFalse(UnlockPolicy.canUnlock(cfg, MeterState()))
    }

    @Test
    fun `解锁截止时间按配置的分钟数计算`() {
        assertEquals(now + 10 * 60_000L, UnlockPolicy.unlockUntil(config(minutes = 10), now))
        assertEquals(now + 30 * 60_000L, UnlockPolicy.unlockUntil(config(minutes = 30), now))
    }

    @Test
    fun `每日重置会把解锁次数清零`() {
        // 跨日时 UsageMeter 会重建状态，次数自然归零
        val cfg = config(maxPerDay = 3)
        var state = MeterState(dayKey = "2026-10-31")
        repeat(3) { state = UnlockPolicy.applyUnlock(cfg, state) }
        assertFalse(UnlockPolicy.canUnlock(cfg, state))

        val nextDay = com.fangchenmi.guard.core.usage.UsageMeter.reduce(
            state,
            com.fangchenmi.guard.core.usage.MeterEvent.Tick,
            now + 24 * 3600_000L,
            com.fangchenmi.guard.core.time.Days.dayKey(now + 24 * 3600_000L, zone, 0),
            com.fangchenmi.guard.core.usage.MeterContext(
                isCounted = { false },
                sessionLimitMinutes = 45,
                breakMinutes = 10,
            ),
        )
        assertEquals(0, nextDay.unlocksToday)
        assertTrue(UnlockPolicy.canUnlock(cfg, nextDay))
    }

    @Test
    fun `状态里带上剩余次数给界面用`() {
        val cfg = config(maxPerDay = 3)
        val status = GuardPolicy.evaluate(
            config = cfg,
            state = MeterState(unlocksToday = 1),
            subjectPkg = null,
            nowMillis = now,
            zone = zone,
            ignoredPackages = emptySet(),
            selfPackages = emptySet(),
            settingsPackages = emptySet(),
        )
        assertEquals(2, status.unlocksLeftToday)
    }
}

class AttemptPolicyTest {

    private val now = 1_000_000L

    @Test
    fun `前四次输错不锁`() {
        var failures = 0
        repeat(AttemptPolicy.FREE_ATTEMPTS) {
            val next = AttemptPolicy.onFailure(failures, now)
            failures = next.failures
            assertEquals(0L, next.lockedUntilMillis)
            assertFalse(AttemptPolicy.isLocked(next, now))
        }
    }

    @Test
    fun `第五次输错开始锁 30 秒`() {
        val next = AttemptPolicy.onFailure(AttemptPolicy.FREE_ATTEMPTS, now)
        assertEquals(30_000L, next.lockedUntilMillis - now)
        assertTrue(AttemptPolicy.isLocked(next, now))
        assertFalse(AttemptPolicy.isLocked(next, now + 30_001L))
    }

    @Test
    fun `继续输错会翻倍，但封顶 15 分钟`() {
        assertEquals(60_000L, AttemptPolicy.lockoutDurationMillis(6))
        assertEquals(120_000L, AttemptPolicy.lockoutDurationMillis(7))
        assertEquals(240_000L, AttemptPolicy.lockoutDurationMillis(8))
        assertEquals(15 * 60_000L, AttemptPolicy.lockoutDurationMillis(20))
        assertEquals(15 * 60_000L, AttemptPolicy.lockoutDurationMillis(999))
    }

    @Test
    fun `剩余自由次数不会为负`() {
        assertEquals(4, AttemptPolicy.remainingFreeAttempts(AttemptState()))
        assertEquals(1, AttemptPolicy.remainingFreeAttempts(AttemptState(failures = 3)))
        assertEquals(0, AttemptPolicy.remainingFreeAttempts(AttemptState(failures = 4)))
        assertEquals(0, AttemptPolicy.remainingFreeAttempts(AttemptState(failures = 40)))
    }
}
