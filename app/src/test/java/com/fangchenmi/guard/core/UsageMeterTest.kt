package com.fangchenmi.guard.core

import com.fangchenmi.guard.core.model.AppCategory
import com.fangchenmi.guard.core.model.AppRule
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.core.policy.Counting
import com.fangchenmi.guard.core.time.Days
import com.fangchenmi.guard.core.usage.MeterContext
import com.fangchenmi.guard.core.usage.MeterEvent
import com.fangchenmi.guard.core.usage.MeterState
import com.fangchenmi.guard.core.usage.UsageMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class UsageMeterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 2024-01-15 10:00:00 (Asia/Shanghai)，远离零点，跨日测试用它加减天数。 */
    private val t0 = 1_705_284_000_000L

    private val game = "com.example.game"
    private val study = "com.example.study"

    private fun config(session: Int = 45, breakMin: Int = 10) = GuardConfig(
        enabled = true,
        totalDailyMinutes = 120,
        sessionMinutes = session,
        breakMinutes = breakMin,
        bedtimeEnabled = false,
        appRules = listOf(AppRule(pkg = study, category = AppCategory.STUDY)),
    )

    private fun ctx(cfg: GuardConfig): MeterContext = Counting.meterContext(
        cfg,
        ignoredPackages = setOf("com.android.launcher", "com.android.systemui"),
        selfPackages = setOf("com.fangchenmi.guard"),
    )

    private fun today(now: Long) = Days.dayKey(now, zone, 0)

    private fun fresh(at: Long): MeterState = MeterState(dayKey = today(at), lastAtMillis = at, screenOn = true)

    private fun fore(s: MeterState, pkg: String, at: Long, cfg: GuardConfig) =
        UsageMeter.reduce(s, MeterEvent.Foreground(pkg), at, today(at), ctx(cfg))

    private fun tick(s: MeterState, at: Long, cfg: GuardConfig) =
        UsageMeter.reduce(s, MeterEvent.Tick, at, today(at), ctx(cfg))

    private fun screen(s: MeterState, on: Boolean, at: Long, cfg: GuardConfig) =
        UsageMeter.reduce(s, MeterEvent.Screen(on), at, today(at), ctx(cfg))

    private fun pause(s: MeterState, paused: Boolean, at: Long, cfg: GuardConfig) =
        UsageMeter.reduce(s, MeterEvent.Pause(paused), at, today(at), ctx(cfg))

    @Test
    fun `把时间记到前台应用头上`() {
        val cfg = config()
        var s = fresh(t0)
        s = fore(s, game, t0, cfg)
        s = tick(s, t0 + 30_000, cfg)

        assertEquals(30_000L, s.totalMs)
        assertEquals(30_000L, s.usedOf(game))
        assertEquals(30_000L, s.sessionMs)
    }

    @Test
    fun `学习类应用不占用时长`() {
        val cfg = config()
        var s = fresh(t0)
        s = fore(s, study, t0, cfg)
        s = tick(s, t0 + 60_000, cfg)

        assertEquals(0L, s.totalMs)
        assertEquals(0L, s.usedOf(study))
    }

    @Test
    fun `息屏期间不计时`() {
        val cfg = config()
        var s = fresh(t0)
        s = fore(s, game, t0, cfg)
        s = screen(s, false, t0 + 10_000, cfg)
        s = tick(s, t0 + 70_000, cfg)

        assertEquals(10_000L, s.totalMs)
    }

    @Test
    fun `单次连续到点后触发强制休息`() {
        val cfg = config(session = 45, breakMin = 10)
        var s = fresh(t0)
        s = fore(s, game, t0, cfg)

        var at = t0
        repeat(45) {
            at += 60_000
            s = tick(s, at, cfg)
        }

        assertTrue("应该进入强制休息", s.breakUntilMillis > 0L)
        assertEquals(1, s.breaksToday)
        assertEquals(0L, s.sessionMs)
        assertEquals(at + 10 * 60_000L, s.breakUntilMillis)
    }

    @Test
    fun `单次时长为 0 时不触发休息`() {
        val cfg = config(session = 0, breakMin = 10)
        var s = fresh(t0)
        s = fore(s, game, t0, cfg)
        var at = t0
        repeat(120) {
            at += 60_000
            s = tick(s, at, cfg)
        }
        assertEquals(0L, s.breakUntilMillis)
    }

    @Test
    fun `跨日清零当日额度`() {
        val cfg = config()
        var s = fresh(t0)
        s = fore(s, game, t0, cfg)
        s = tick(s, t0 + 600_000, cfg)
        assertTrue(s.totalMs > 0L)

        val nextDay = t0 + 24 * 3600_000L
        s = tick(s, nextDay, cfg)

        assertEquals(0L, s.totalMs)
        assertEquals(0L, s.sessionMs)
        assertEquals(today(nextDay), s.dayKey)
    }

    @Test
    fun `跨零点的强制休息不会被清零`() {
        val cfg = config()
        var s = fresh(t0).copy(breakUntilMillis = t0 + 5 * 60_000L, dayKey = "2000-01-01")
        s = tick(s, t0, cfg)
        assertEquals(t0 + 5 * 60_000L, s.breakUntilMillis)
    }

    @Test
    fun `心跳中断时按上限计入，不能靠改时间刷额度`() {
        val cfg = config()
        var s = fresh(t0)
        s = fore(s, game, t0, cfg)
        s = tick(s, t0 + 10 * 3600_000L, cfg)

        assertEquals(UsageMeter.MAX_GAP_MS, s.totalMs)
    }

    @Test
    fun `主动离开屏幕够久会重置连续使用时长`() {
        val cfg = config(session = 45, breakMin = 10)
        var s = fresh(t0)
        s = fore(s, game, t0, cfg)
        var at = t0 + 30_000
        s = tick(s, at, cfg)
        assertTrue(s.sessionMs > 0L)

        s = fore(s, "com.android.launcher", at, cfg)
        at += 11 * 60_000L
        s = tick(s, at, cfg)

        assertEquals(0L, s.sessionMs)
    }

    @Test
    fun `休息结束后恢复计时`() {
        val cfg = config(session = 1, breakMin = 1)
        var s = fresh(t0)
        s = fore(s, game, t0, cfg)

        var at = t0
        while (s.breakUntilMillis == 0L) {
            at += 5_000
            s = tick(s, at, cfg)
        }
        assertEquals(1, s.breaksToday)
        val breakEnd = s.breakUntilMillis

        // 锁定遮挡层显示后，休息期间不再计时
        s = pause(s, true, at, cfg)
        s = tick(s, breakEnd, cfg)
        assertEquals(0L, s.sessionMs)

        // 休息结束，恢复计时
        s = pause(s, false, breakEnd, cfg)
        at = breakEnd + 5_000
        s = tick(s, at, cfg)
        assertEquals(5_000L, s.sessionMs)
        assertEquals(breakEnd, s.breakUntilMillis)
    }

    @Test
    fun `锁定遮挡期间不计时`() {
        val cfg = config()
        var s = fresh(t0)
        s = fore(s, game, t0, cfg)
        s = tick(s, t0 + 10_000, cfg)
        s = pause(s, true, t0 + 10_000, cfg)

        var at = t0 + 10_000
        repeat(30) {
            at += 1_000
            s = tick(s, at, cfg)
        }

        assertEquals(10_000L, s.totalMs)
    }
}
