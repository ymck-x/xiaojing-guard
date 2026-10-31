package com.fangchenmi.guard.data

import android.content.Context
import com.fangchenmi.guard.core.usage.MeterState
import kotlinx.serialization.json.Json

/** 计时状态落盘。进程被杀后能接着算，不能让孩子靠「重启应用」清零。 */
class UsageStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): MeterState {
        val raw = prefs.getString(KEY_STATE, null) ?: return MeterState()
        return runCatching { json.decodeFromString(MeterState.serializer(), raw) }
            .getOrElse { MeterState() }
    }

    fun save(state: MeterState) {
        prefs.edit().putString(KEY_STATE, json.encodeToString(MeterState.serializer(), state)).apply()
    }

    private companion object {
        const val PREFS = "guard_usage"
        const val KEY_STATE = "meter_v1"
    }
}
