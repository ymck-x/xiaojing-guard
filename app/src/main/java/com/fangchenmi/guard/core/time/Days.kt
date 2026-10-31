package com.fangchenmi.guard.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 日界与时间窗计算。所有跨日逻辑集中在这里，避免各处重复实现导致边界不一致。 */
object Days {

    /** 当前属于「哪一天」的配额。dayStartHour = 4 时，凌晨 3 点仍算前一天。 */
    fun dayKey(nowMillis: Long, zone: ZoneId, dayStartHour: Int): String {
        val shifted = Instant.ofEpochMilli(nowMillis).atZone(zone).minusHours(dayStartHour.toLong())
        return shifted.toLocalDate().toString()
    }

    fun dayKey(nowMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toString()

    /** 当天第几分钟（0..1439）。 */
    fun minuteOfDay(nowMillis: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalTime()
        return t.hour * 60 + t.minute
    }

    /**
     * 判断某个时刻是否落在 [startMinute, endMinute) 的时间窗内。
     * 支持跨零点（start > end 时表示 20:00 → 07:00）。
     * start == end 视为空窗口，返回 false。
     */
    fun inWindow(minuteOfDay: Int, startMinute: Int, endMinute: Int): Boolean {
        if (startMinute == endMinute) return false
        return if (startMinute < endMinute) {
            minuteOfDay >= startMinute && minuteOfDay < endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }

    /** 下一次 [endMinute] 发生的时刻（毫秒）。用于显示「几点解禁」。 */
    fun nextOccurrenceOfMinute(nowMillis: Long, minuteOfDay: Int, zone: ZoneId): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var candidate = now.toLocalDate().atStartOfDay(zone)
            .plusMinutes(minuteOfDay.toLong())
        if (!candidate.isAfter(now)) {
            candidate = candidate.plusDays(1)
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun today(zone: ZoneId): LocalDate = LocalDate.now(zone)
}
