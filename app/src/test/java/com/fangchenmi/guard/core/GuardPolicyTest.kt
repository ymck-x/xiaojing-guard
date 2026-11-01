package com.fangchenmi.guard.core

import com.fangchenmi.guard.core.model.AppCategory
import com.fangchenmi.guard.core.model.AppRule
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.core.policy.GuardLimits
import com.fangchenmi.guard.core.policy.GuardPolicy
import com.fangchenmi.guard.core.policy.GuardStatus
import com.fangchenmi.guard.core.policy.LockReason
import com.fangchenmi.guard.core.policy.LockScope
import com.fangchenmi.guard.core.usage.MeterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GuardPolicyTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val self = setOf("com.fangchenmi.guard")
    private val ignored = setOf("com.android.systemui")
    private val settings = setOf(
        "com.android.settings",
        "com.coloros.safecenter",
        "com.android.packageinstaller",
    )

    private val game = "com.example.game"
    private val study = "com.example.study"
    private val banned = "com.example.banned"

    private fun at(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2024, 1, 15, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun config(
        total: Int = 120,
        session: Int = 45,
        breakMin: Int = 10,
        bedtime: Boolean = false,
        blockSettings: Boolean = true,
        unlockUntil: Long = 0L,
    ) = GuardConfig(
        enabled = true,
        totalDailyMinutes = total,
        sessionMinutes = session,
        breakMinutes = breakMin,
        bedtimeEnabled = bedtime,
        bedtimeStartMinute = 21 * 60,
        bedtimeEndMinute = 7 * 60,
        blockSettings = blockSettings,
        unlockUntilMillis = unlockUntil,
        appRules = listOf(
            AppRule(pkg = study, category = AppCategory.STUDY),
            AppRule(pkg = banned, category = AppCategory.BLOCK),
            AppRule(pkg = game, category = AppCategory.ENTERTAIN, dailyLimitMinutes = 30),
        ),
    )

    private fun evaluate(
        cfg: GuardConfig,
        state: MeterState = MeterState(),
        pkg: String? = null,
        now: Long = at(12, 0),
        limits: GuardLimits = GuardLimits.PRO,
        installers: Set<String> = setOf("com.android.packageinstaller"),
        settingsGrace: Long = 0L,
    ): GuardStatus = GuardPolicy.evaluate(
        config = cfg,
        state = state,
        subjectPkg = pkg,
        nowMillis = now,
        zone = zone,
        ignoredPackages = ignored,
        selfPackages = self,
        settingsPackages = settings,
        limits = limits,
        installerPackages = installers,
        settingsGraceUntilMillis = settingsGrace,
    )

    @Test
    fun `关闭守护时永远放行`() {
        val cfg = config().copy(enabled = false, totalDailyMinutes = 1)
        val state = MeterState(totalMs = 10 * 3600_000L)
        val r = evaluate(cfg, state, game)
        assertFalse(r.locked)
        assertFalse(r.enforced)
    }

    @Test
    fun `停机时段跨零点生效`() {
        val cfg = config(bedtime = true)
        val night = evaluate(cfg, pkg = game, now = at(23, 30))
        assertTrue(night.locked)
        assertEquals(LockScope.GLOBAL, night.scope)
        assertEquals(LockReason.BEDTIME, night.reason)

        val morning = evaluate(cfg, pkg = game, now = at(9, 0))
        assertFalse(morning.locked)
    }

    @Test
    fun `用完每日总时长后全局锁定`() {
        val cfg = config(total = 60)
        val state = MeterState(totalMs = 60 * 60_000L)
        val r = evaluate(cfg, state, pkg = game)
        assertTrue(r.locked)
        assertEquals(LockScope.GLOBAL, r.scope)
        assertEquals(LockReason.TOTAL_LIMIT, r.reason)
        assertEquals(0L, r.remainingTotalMs)
    }

    @Test
    fun `总时长用完时学习类也停止`() {
        val cfg = config(total = 60)
        val state = MeterState(totalMs = 60 * 60_000L)
        val r = evaluate(cfg, state, pkg = study)
        assertTrue(r.locked)
        assertEquals(LockScope.GLOBAL, r.scope)
    }

    @Test
    fun `学习类平时不计时不拦截`() {
        val cfg = config(total = 60)
        val state = MeterState(totalMs = 59 * 60_000L, perAppMs = mapOf(study to 50 * 60_000L))
        val r = evaluate(cfg, state, pkg = study)
        assertFalse(r.locked)
    }

    @Test
    fun `单个应用限额只拦这个应用`() {
        val cfg = config()
        val state = MeterState(totalMs = 10 * 60_000L, perAppMs = mapOf(game to 30 * 60_000L))
        val r = evaluate(cfg, state, pkg = game)
        assertTrue(r.locked)
        assertEquals(LockScope.APP, r.scope)
        assertEquals(LockReason.APP_LIMIT, r.reason)

        val other = evaluate(cfg, state, pkg = "com.example.other")
        assertFalse(other.locked)
    }

    @Test
    fun `禁止类应用直接拦截`() {
        val r = evaluate(config(), pkg = banned)
        assertTrue(r.locked)
        assertEquals(LockScope.APP, r.scope)
        assertEquals(LockReason.BLOCKED_APP, r.reason)
    }

    @Test
    fun `系统设置默认被拦截，避免孩子关掉守护`() {
        val r = evaluate(config(blockSettings = true), pkg = "com.android.settings")
        assertTrue(r.locked)
        assertEquals(LockReason.BLOCKED_APP, r.reason)

        val open = evaluate(config(blockSettings = false), pkg = "com.android.settings")
        assertFalse(open.locked)
    }

    @Test
    fun `自己的应用只豁免分应用规则，不豁免全局锁`() {
        // 锁定遮挡层一显示，系统就会产生「前台 = 我们自己」的无障碍事件。
        // 如果这里放行全局锁，锁屏会把自己拆掉——所以全局锁对自己的应用也必须生效。
        val locked = evaluate(
            config(total = 1, bedtime = true),
            MeterState(totalMs = 5 * 3600_000L),
            pkg = "com.fangchenmi.guard",
            now = at(23, 0),
        )
        assertTrue(locked.locked)
        assertEquals(LockScope.GLOBAL, locked.scope)

        // 没有全局锁时，自己的应用不会被分应用规则拦住（家长要能进设置）
        val allowed = evaluate(config(total = 600), MeterState(), pkg = "com.fangchenmi.guard")
        assertFalse(allowed.locked)
    }

    @Test
    fun `强制休息期间全局锁定`() {
        val cfg = config()
        val now = at(12, 0)
        val state = MeterState(breakUntilMillis = now + 4 * 60_000L)
        val r = evaluate(cfg, state, pkg = game, now = now)
        assertTrue(r.locked)
        assertEquals(LockReason.SESSION_BREAK, r.reason)
        assertEquals(4 * 60_000L, r.breakRemainingMs)
    }

    @Test
    fun `家长临时解锁期间一律放行`() {
        val cfg = config(bedtime = true, total = 1, unlockUntil = at(12, 10))
        val state = MeterState(totalMs = 5 * 3600_000L)
        val r = evaluate(cfg, state, pkg = game, now = at(12, 0))
        assertFalse(r.locked)
        assertEquals(10 * 60_000L, r.unlockRemainingMs)
    }

    @Test
    fun `设置应用即使同时被当作桌面也要拦住`() {
        // vivo 等 ROM 把 Settings$FallbackHome 注册成桌面活动，导致设置同时出现在忽略名单里。
        // 判定顺序必须是「设置」优先，否则这道最关键的防线会被静默绕过。
        val cfg = config(blockSettings = true)
        val r = GuardPolicy.evaluate(
            config = cfg,
            state = MeterState(),
            subjectPkg = "com.android.settings",
            nowMillis = at(12, 0),
            zone = zone,
            ignoredPackages = ignored + "com.android.settings",
            selfPackages = self,
            settingsPackages = settings,
        )
        assertTrue(r.locked)
        assertEquals(LockScope.APP, r.scope)
        assertEquals(LockReason.BLOCKED_APP, r.reason)
    }

    @Test
    fun `剩余时长计算`() {
        val cfg = config(total = 90)
        val state = MeterState(totalMs = 30 * 60_000L)
        val r = evaluate(cfg, state, pkg = game)
        assertEquals(60 * 60_000L, r.remainingTotalMs)
    }

    @Test
    fun `从应用内跳设置时短暂放行`() {
        // 守护开着 + 禁止进入设置：家长在应用里点「去开启卸载保护」要能进设置，
        // 否则永远开不了，形成死锁。
        val cfg = config(blockSettings = true)
        val now = at(12, 0)

        assertTrue("平时必须拦", evaluate(cfg, pkg = "com.android.settings", now = now).locked)

        val opened = evaluate(cfg, pkg = "com.android.settings", now = now, settingsGrace = now + 60_000)
        assertFalse("放行窗口内要能进设置", opened.locked)

        val expired = evaluate(cfg, pkg = "com.android.settings", now = now, settingsGrace = now - 1)
        assertTrue("窗口过期后恢复拦截", expired.locked)
    }

    @Test
    fun `系统安装器始终被拦`() {
        // 长按桌面图标 → 卸载，走的是系统安装器，不是「设置」。
        // 它必须无条件拦，不能挂在「禁止进入设置」那个开关下面。
        val r = evaluate(config(), pkg = "com.android.packageinstaller")
        assertTrue(r.locked)
        assertEquals(LockReason.BLOCKED_APP, r.reason)

        val off = evaluate(config(blockSettings = false), pkg = "com.android.packageinstaller")
        assertTrue("关掉「禁止进入设置」也不能放开卸载", off.locked)
    }

    // ---------- 免费版 / 付费版边界 ----------

    @Test
    fun `免费版不触发强制休息`() {
        val cfg = config(session = 30, breakMin = 15)
        val now = at(12, 0)
        val resting = MeterState(sessionMs = 31L * 60_000, breakUntilMillis = now + 5 * 60_000L)

        val free = evaluate(cfg, resting, game, now, GuardLimits.FREE)
        assertFalse("免费版不该被强制休息挡住", free.locked)

        val pro = evaluate(cfg, resting, game, now, GuardLimits.PRO)
        assertTrue(pro.locked)
        assertEquals(LockReason.SESSION_BREAK, pro.reason)
    }

    @Test
    fun `免费版忽略单应用限额`() {
        val cfg = config()
        val state = MeterState(perAppMs = mapOf(game to 60L * 60_000))

        val free = evaluate(cfg, state, game, limits = GuardLimits.FREE)
        assertFalse("免费版不看单应用限额", free.locked)

        val pro = evaluate(cfg, state, game, limits = GuardLimits.PRO)
        assertTrue(pro.locked)
        assertEquals(LockReason.APP_LIMIT, pro.reason)
    }

    @Test
    fun `免费版禁止名单只生效前三个`() {
        val blocked = (1..4).map { AppRule(pkg = "com.example.b$it", category = AppCategory.BLOCK) }
        val cfg = GuardConfig(enabled = true, totalDailyMinutes = 0, bedtimeEnabled = false, appRules = blocked)

        assertTrue(evaluate(cfg, pkg = "com.example.b1", limits = GuardLimits.FREE).locked)
        assertTrue(evaluate(cfg, pkg = "com.example.b3", limits = GuardLimits.FREE).locked)
        assertFalse("第四个超出免费额度，不该拦", evaluate(cfg, pkg = "com.example.b4", limits = GuardLimits.FREE).locked)
        assertTrue("付费版四个都拦", evaluate(cfg, pkg = "com.example.b4", limits = GuardLimits.PRO).locked)
    }

    @Test
    fun `免费版学习类仍然不计时`() {
        val cfg = config()
        val state = MeterState(totalMs = 30L * 60_000, perAppMs = mapOf(study to 30L * 60_000))
        // 学习类在免费版也必须放行——不然会误伤网课
        val r = evaluate(cfg, state, study, limits = GuardLimits.FREE)
        assertFalse(r.locked)
    }
}
