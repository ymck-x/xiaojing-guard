package com.fangchenmi.guard.core.usage

/**
 * 把「时间」记到应用头上。它是一个纯函数 reducer：
 * 同样的 state + 同样的事件序列，一定得到同样的结果，因此可以完整地做单元测试。
 *
 * 结算模型：状态里记着 [MeterState.lastAtMillis]（上次结算时刻）与当前前台应用。
 * 每次事件到来时，先把 (now - lastAt) 这段时间记到「上一次的前台应用」头上，再应用事件本身。
 *
 * 只有同时满足「亮屏、没在锁屏遮挡、前台是娱乐类应用」时才计时。
 */
object UsageMeter {

    /**
     * 单次结算的最大可信间隔。正常心跳是 1 秒，超过这个值说明心跳被暂停过
     * （进程被杀、系统冻结、或有人改了系统时间），此时只按上限计入，
     * 避免「把系统时间往前拨」就能一次性耗尽或重置额度。
     */
    const val MAX_GAP_MS = 120_000L

    fun reduce(
        state: MeterState,
        event: MeterEvent,
        now: Long,
        todayKey: String,
        ctx: MeterContext,
    ): MeterState {
        var s = rollOverIfNeeded(state, now, todayKey)
        s = settleElapsed(s, now, ctx)
        s = maybeTriggerBreak(s, now, ctx)
        return applyEvent(s, event, now, ctx)
    }

    /** 跨日时清零当日额度，但跨零点的强制休息要继续生效。 */
    private fun rollOverIfNeeded(state: MeterState, now: Long, todayKey: String): MeterState {
        if (state.dayKey == todayKey) return state
        return MeterState(
            dayKey = todayKey,
            lastAtMillis = now,
            foregroundPkg = state.foregroundPkg,
            screenOn = state.screenOn,
            paused = state.paused,
            idleSinceMillis = state.idleSinceMillis,
            breakUntilMillis = if (state.breakUntilMillis > now) state.breakUntilMillis else 0L,
        )
    }

    private fun settleElapsed(state: MeterState, now: Long, ctx: MeterContext): MeterState {
        if (state.lastAtMillis <= 0L) return state.copy(lastAtMillis = now)
        val elapsed = (now - state.lastAtMillis).coerceIn(0L, MAX_GAP_MS)
        val counting = !state.paused && state.screenOn && ctx.isCounted(state.foregroundPkg)
        if (counting) {
            val pkg = state.foregroundPkg!!
            return state.copy(
                totalMs = state.totalMs + elapsed,
                perAppMs = addTo(state.perAppMs, pkg, elapsed),
                sessionMs = state.sessionMs + elapsed,
                idleSinceMillis = 0L,
                lastAtMillis = now,
            )
        }
        val idleSince = if (state.idleSinceMillis != 0L) state.idleSinceMillis else state.lastAtMillis
        var sessionMs = state.sessionMs
        // 自己主动离开屏幕（或一直在锁屏上）够久，也算休息过了。
        if (ctx.breakMinutes > 0 && now - idleSince >= ctx.breakMinutes * 60_000L) {
            sessionMs = 0L
        }
        return state.copy(sessionMs = sessionMs, idleSinceMillis = idleSince, lastAtMillis = now)
    }

    private fun maybeTriggerBreak(state: MeterState, now: Long, ctx: MeterContext): MeterState {
        if (ctx.sessionLimitMinutes <= 0 || ctx.breakMinutes <= 0) return state
        val limitMs = ctx.sessionLimitMinutes * 60_000L
        if (state.sessionMs < limitMs || now < state.breakUntilMillis) return state
        return state.copy(
            breakUntilMillis = now + ctx.breakMinutes * 60_000L,
            sessionMs = 0L,
            breaksToday = state.breaksToday + 1,
        )
    }

    private fun applyEvent(state: MeterState, event: MeterEvent, now: Long, ctx: MeterContext): MeterState =
        when (event) {
            is MeterEvent.Foreground -> withIdle(
                state.copy(foregroundPkg = event.pkg),
                active = isActive(state, event.pkg, ctx),
                now = now,
            )

            is MeterEvent.Screen -> {
                val next = state.copy(screenOn = event.on)
                withIdle(next, active = isActive(next, next.foregroundPkg, ctx), now = now)
            }

            is MeterEvent.Pause -> {
                val next = state.copy(paused = event.paused)
                withIdle(next, active = isActive(next, next.foregroundPkg, ctx), now = now)
            }

            MeterEvent.Tick -> state
        }

    private fun isActive(state: MeterState, pkg: String?, ctx: MeterContext): Boolean =
        !state.paused && state.screenOn && ctx.isCounted(pkg)

    private fun withIdle(state: MeterState, active: Boolean, now: Long): MeterState {
        val idleSince = when {
            active -> 0L
            state.idleSinceMillis != 0L -> state.idleSinceMillis
            else -> now
        }
        return state.copy(idleSinceMillis = idleSince)
    }

    private fun addTo(map: Map<String, Long>, key: String, delta: Long): Map<String, Long> {
        val old = map[key] ?: 0L
        return map + (key to old + delta)
    }
}
