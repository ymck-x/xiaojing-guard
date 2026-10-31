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

    /**
     * 付费版的购买页地址。
     * 上线前把这里换成真实的爱发电 / 小商店链接即可，不用改别的代码。
     */
    const val PURCHASE_URL: String = "https://afdian.com/a/ymck1"

    /**
     * 付费版打包时注入的买家标识（`-Plicensee="张三"`）。
     * 免费版为空字符串。用它做水印：泄露出去的包会带着买家的名字。
     */
    val licensee: String = BuildConfig.LICENSEE
}
