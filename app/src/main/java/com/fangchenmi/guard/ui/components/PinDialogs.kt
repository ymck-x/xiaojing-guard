package com.fangchenmi.guard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fangchenmi.guard.core.pin.PinHasher
import com.fangchenmi.guard.data.PinAttemptGuard
import kotlinx.coroutines.delay

@Composable
private fun PinDots(length: Int, filled: Int) {
    Text(
        text = buildString { repeat(length) { append(if (it < filled) "●" else "○") } },
        style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("清空", "0", "⌫"),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.Center) {
                row.forEach { key ->
                    val bg = if (enabled) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                    Box(
                        modifier = Modifier
                            .padding(5.dp)
                            .size(width = 84.dp, height = 58.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .clickable(enabled = enabled) {
                                when (key) {
                                    "清空" -> onClear()
                                    "⌫" -> onBackspace()
                                    else -> onDigit(key[0])
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            key,
                            style = if (key.length == 1) {
                                MaterialTheme.typography.headlineMedium
                            } else {
                                MaterialTheme.typography.labelLarge
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogShell(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { content() }
        }
    }
}

/**
 * 校验型密码框。失败次数由调用方（[PinAttemptGuard]）统一计数并落盘，
 * 所以关掉这个弹窗再打开，锁定时长不会重置。
 */
@Composable
fun VerifyPinDialog(
    title: String,
    pinLength: Int,
    verify: (String) -> PinAttemptGuard.Result,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    initialLockedUntil: () -> Long = { 0L },
) {
    val length = pinLength.coerceIn(PinHasher.MIN_LENGTH, PinHasher.MAX_LENGTH)
    var buffer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var lockedUntil by remember { mutableLongStateOf(initialLockedUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lockedUntil) {
        while (lockedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val locked = lockedUntil > now
    val lockedSeconds = ((lockedUntil - now) / 1000).coerceAtLeast(0)

    fun submit(candidate: String) {
        when (val result = verify(candidate)) {
            is PinAttemptGuard.Result.Ok -> onSuccess()

            is PinAttemptGuard.Result.Wrong -> {
                buffer = ""
                error = if (result.remainingFreeAttempts > 0) {
                    "密码不对，还能再试 ${result.remainingFreeAttempts} 次"
                } else {
                    "密码不对，再错一次就要等一会儿了"
                }
            }

            is PinAttemptGuard.Result.Locked -> {
                buffer = ""
                error = null
                lockedUntil = result.untilMillis
            }
        }
    }

    DialogShell(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "需要输入 $length 位数字",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        PinDots(length = length, filled = buffer.length)
        Spacer(Modifier.height(6.dp))
        Text(
            text = error
                ?: if (locked) "输错次数太多，请 $lockedSeconds 秒后再试" else " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.height(20.dp),
        )
        Keypad(
            enabled = !locked,
            onDigit = { digit ->
                if (!locked) {
                    error = null
                    if (buffer.length < length) {
                        buffer += digit
                        if (buffer.length == length) submit(buffer)
                    }
                }
            },
            onBackspace = { buffer = buffer.dropLast(1) },
            onClear = { buffer = "" },
        )
        TextButton(onClick = onDismiss) { Text("取消") }
    }
}

/**
 * 设置型密码框：固定 [PinHasher.DEFAULT_LENGTH] 位，输满自动进入下一步、再输满自动比对完成。
 *
 * 以前做成「4~8 位可选 + 一个『下一步』按钮」，界面上永远画 8 个点，
 * 家长根本分不清该输几位——有人顺手输了 8 位，之后解锁只输 4 位就对不上了。
 * 现在位数固定、没有歧义，也没有多余按钮。
 */
@Composable
fun CreatePinDialog(
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val length = PinHasher.DEFAULT_LENGTH
    var buffer by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val confirming = first != null

    fun onFilled(value: String) {
        val previous = first
        if (previous == null) {
            first = value
            buffer = ""
        } else if (previous == value) {
            onDone(value)
        } else {
            error = "两次输入不一致，请重新设置"
            first = null
            buffer = ""
        }
    }

    DialogShell(onDismiss = onDismiss) {
        Text(
            if (confirming) "再输入一次" else "设置家长密码",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (confirming) "确认刚才输入的 $length 位密码" else "$length 位数字，请记住它",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        PinDots(length = length, filled = buffer.length)
        Spacer(Modifier.height(6.dp))
        Text(
            text = error ?: " ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.height(20.dp),
        )
        Keypad(
            enabled = true,
            onDigit = { digit ->
                error = null
                if (buffer.length < length) {
                    buffer += digit
                    if (buffer.length == length) onFilled(buffer)
                }
            },
            onBackspace = { buffer = buffer.dropLast(1) },
            onClear = { buffer = "" },
        )
        TextButton(onClick = onDismiss) { Text("取消") }
    }
}