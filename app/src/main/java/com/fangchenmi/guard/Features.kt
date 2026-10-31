package com.fangchenmi.guard

import com.fangchenmi.guard.core.policy.GuardLimits

/**
 * 当前安装的是免费版还是付费版。
 *
 * 由 Gradle 的 productFlavor 决定（`BuildConfig.PRO`），
 * 两个包**同包名、同签名**，所以付费版可以直接覆盖安装免费版，
 * 家长已设置的规则和累计时长都会保留。
 */
object Features {
    val isPro: Boolean = BuildConfig.PRO
    val limits: GuardLimits = if (isPro) GuardLimits.PRO else GuardLimits.FREE

    /** 界面上用来标「付费功能」的小标签。 */
    val tierLabel: String = if (isPro) "付费版" else "免费版"
}
