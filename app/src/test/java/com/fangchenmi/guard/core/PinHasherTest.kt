package com.fangchenmi.guard.core

import com.fangchenmi.guard.core.pin.PinHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val fast = 1_000 // 测试里用低迭代次数，逻辑与生产一致

    @Test
    fun `设置密码后能用同样的密码验证通过`() {
        val cred = PinHasher.create("1234", iterations = fast)
        assertTrue(cred.configured)
        assertTrue(PinHasher.verify("1234", cred))
    }

    @Test
    fun `错误密码验证失败`() {
        val cred = PinHasher.create("1234", iterations = fast)
        assertFalse(PinHasher.verify("4321", cred))
        assertFalse(PinHasher.verify("12345", cred))
        assertFalse(PinHasher.verify("", cred))
    }

    @Test
    fun `不存明文，两次加密同一密码结果不同`() {
        val a = PinHasher.create("123456", iterations = fast)
        val b = PinHasher.create("123456", iterations = fast)
        assertFalse(a.hashBase64 == b.hashBase64)
        assertFalse(a.saltBase64 == b.saltBase64)
        assertFalse(a.hashBase64.contains("123456"))
    }

    @Test
    fun `未设置密码时验证一律失败`() {
        assertFalse(PinHasher.verify("1234", com.fangchenmi.guard.core.model.PinCredential()))
    }

    @Test
    fun `密码格式校验`() {
        assertTrue(PinHasher.isValidPinFormat("1234"))
        assertTrue(PinHasher.isValidPinFormat("12345678"))
        assertFalse(PinHasher.isValidPinFormat("123"))
        assertFalse(PinHasher.isValidPinFormat("123456789"))
        assertFalse(PinHasher.isValidPinFormat("12a4"))
        assertFalse(PinHasher.isValidPinFormat(""))
    }
}
