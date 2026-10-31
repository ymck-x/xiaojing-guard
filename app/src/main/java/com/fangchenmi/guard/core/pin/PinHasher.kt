package com.fangchenmi.guard.core.pin

import com.fangchenmi.guard.core.model.PinCredential
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 家长 PIN 的存储与校验。只存 PBKDF2 派生值，明文永不落盘。
 * 用 java.util.Base64（API 26+ 可用）而不是 android.util.Base64，这样单元测试能直接跑。
 */
object PinHasher {

    const val MIN_LENGTH = 4

    /**
     * 新建密码的固定位数。
     *
     * 之前做成 4~8 位可选，结果界面上永远画着 8 个点，家长分不清该输几位，
     * 有人顺手输了 8 位，之后解锁时又只输 4 位，就对不上了。现在位数固定、输满即校验。
     * 取 8 位：八位数字有一亿种组合，配上试错锁定，基本不可能被暴力试开。
     * [MIN_LENGTH] 只用于兼容老版本存下来的 4 位密码。
     */
    const val DEFAULT_LENGTH = 8
    const val MAX_LENGTH = 8
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_BITS = 256
    private const val DEFAULT_ITERATIONS = 120_000

    fun isValidPinFormat(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    fun create(pin: String, iterations: Int = DEFAULT_ITERATIONS): PinCredential {
        require(isValidPinFormat(pin)) { "PIN 必须是 $MIN_LENGTH-$MAX_LENGTH 位数字" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt, iterations)
        return PinCredential(
            saltBase64 = Base64.getEncoder().encodeToString(salt),
            hashBase64 = Base64.getEncoder().encodeToString(hash),
            iterations = iterations,
            length = pin.length,
        )
    }

    fun verify(pin: String, credential: PinCredential): Boolean {
        if (!credential.configured) return false
        if (pin.length != credential.length) return false
        val salt = runCatching { Base64.getDecoder().decode(credential.saltBase64) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getDecoder().decode(credential.hashBase64) }.getOrNull() ?: return false
        val actual = derive(pin, salt, credential.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
