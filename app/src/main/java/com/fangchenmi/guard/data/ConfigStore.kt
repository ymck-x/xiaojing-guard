package com.fangchenmi.guard.data

import android.content.Context
import com.fangchenmi.guard.core.model.GuardConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * 家长规则的唯一来源。所有读写都走这里，进程内用 StateFlow 广播变更，
 * 保证界面、无障碍服务、悬浮层看到的是同一份配置。
 */
class ConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _config = MutableStateFlow(read())
    val config: StateFlow<GuardConfig> = _config.asStateFlow()

    fun current(): GuardConfig = _config.value

    fun update(transform: (GuardConfig) -> GuardConfig) {
        write(transform(_config.value))
    }

    fun write(config: GuardConfig) {
        _config.value = config
        // 配置丢失等于守护失效，因此用 commit 同步落盘，宁可慢一点。
        prefs.edit().putString(KEY_CONFIG, json.encodeToString(GuardConfig.serializer(), config)).commit()
    }

    private fun read(): GuardConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return GuardConfig()
        return runCatching { json.decodeFromString(GuardConfig.serializer(), raw) }
            .getOrElse { GuardConfig() }
    }

    private companion object {
        const val PREFS = "guard_config"
        const val KEY_CONFIG = "config_v1"
    }
}
