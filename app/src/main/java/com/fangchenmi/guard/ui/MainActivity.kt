package com.fangchenmi.guard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.fangchenmi.guard.Guard
import com.fangchenmi.guard.admin.GuardDeviceAdminReceiver
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.core.pin.PinHasher
import com.fangchenmi.guard.service.GuardAccessibilityService
import com.fangchenmi.guard.service.GuardNotifier
import com.fangchenmi.guard.service.UnlockOutcome
import com.fangchenmi.guard.ui.components.CreatePinDialog
import com.fangchenmi.guard.ui.components.OceanBackground
import com.fangchenmi.guard.ui.components.VerifyPinDialog
import com.fangchenmi.guard.ui.theme.GuardTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Guard.install(application)
        setContent {
            GuardTheme {
                OceanBackground {
                    // 平板上限制内容宽度并居中，避免一行拉得太长
                    Box(
                        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().widthIn(max = 760.dp)) {
                            GuardRoot()
                        }
                    }
                }
            }
        }
    }
}

private enum class Route { SETUP, HOME, SETTINGS, APPS }

private enum class PinTarget { SETTINGS, UNLOCK }

@Composable
private fun GuardRoot() {
    val context = LocalContext.current
    val config by Guard.config.config.collectAsStateWithLifecycle()
    val usage by Guard.engine.usage.collectAsStateWithLifecycle()

    var route by rememberSaveable {
        mutableStateOf(if (Guard.config.current().pin.configured) Route.HOME.name else Route.SETUP.name)
    }
    var accessibilityEnabled by remember { mutableStateOf(GuardAccessibilityService.isEnabled(context)) }
    var deviceAdminActive by remember { mutableStateOf(GuardDeviceAdminReceiver.isActive(context)) }
    var deviceOwner by remember { mutableStateOf(GuardDeviceAdminReceiver.isDeviceOwner(context)) }

    var pinTarget by remember { mutableStateOf<PinTarget?>(null) }
    var showCreatePin by remember { mutableStateOf(false) }

    // 守护靠通知才喊得醒家长（无障碍被系统关掉时），所以开启守护后要拿到通知权限。
    var askedNotification by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(config.enabled) {
        if (config.enabled && !askedNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                askedNotification = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun refreshPermissions() {
        accessibilityEnabled = GuardAccessibilityService.isEnabled(context)
        deviceAdminActive = GuardDeviceAdminReceiver.isActive(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
                Guard.engine.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val updateConfig: (GuardConfig) -> Unit = { new ->
        Guard.config.write(new)
        Guard.engine.onConfigChanged()
        // 图标显隐、常驻通知跟着配置走（内部会检查通知权限，没权限就不隐藏）。
        GuardNotifier.sync(context, new)
    }

    var notice by remember { mutableStateOf<String?>(null) }
    val doUnlock = {
        when (val outcome = Guard.engine.requestUnlock()) {
            is UnlockOutcome.Granted -> notice = "已解锁 ${outcome.minutes} 分钟"
            UnlockOutcome.QuotaExhausted ->
                notice = "今天的临时解锁次数已经用完了（每天 ${
                    Guard.config.current().maxUnlocksPerDay
                } 次）。明天清零，或在家长设置里调整上限。"
            UnlockOutcome.NoPin -> notice = "还没有设置家长密码"
        }
    }

    fun openAccessibilitySettings() {
        Guard.engine.allowSystemSettings()
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openDeviceAdmin() {
        Guard.engine.allowSystemSettings()
        runCatching {
            context.startActivity(
                GuardDeviceAdminReceiver.activationIntent(context)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // 装新版、系统升级之后，系统会把无障碍授权清掉，守护静默失效。
    // 家长不会主动去看徽标，所以在首页直接用对话框挡住，不给忽略的机会。
    var a11yWarned by remember { mutableStateOf(false) }
    // 系统重新绑定无障碍服务要 1~2 秒，刚启动就判会误报「已失效」。
    // 等状态稳定下来再决定要不要弹。
    var a11yChecked by remember { mutableStateOf(false) }
    LaunchedEffect(accessibilityEnabled) {
        if (accessibilityEnabled) {
            a11yChecked = true
        } else {
            kotlinx.coroutines.delay(2000)
            a11yChecked = true
        }
    }
    if (a11yChecked && config.enabled && !accessibilityEnabled && route != Route.SETUP.name && !a11yWarned) {
        AlertDialog(
            onDismissRequest = { a11yWarned = true },
            title = { Text("守护已经失效了") },
            text = {
                Text(
                    "无障碍权限被系统关掉了（安装新版、系统升级之后都会这样）。\n\n" +
                        "现在所有规则都不生效，应用也能被直接卸载。重新打开一次就好。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    a11yWarned = true
                    openAccessibilitySettings()
                }) { Text("去开启") }
            },
            dismissButton = {
                TextButton(onClick = { a11yWarned = true }) { Text("稍后") }
            },
        )
    }

    when (Route.valueOf(route)) {
        Route.SETUP -> SetupScreen(
            config = config,
            accessibilityEnabled = accessibilityEnabled,
            deviceAdminActive = deviceAdminActive,
            onConfigChange = updateConfig,
            onSetPin = { showCreatePin = true },
            onOpenAccessibility = ::openAccessibilitySettings,
            onEnableDeviceAdmin = ::openDeviceAdmin,
            onFinish = {
                updateConfig(config.copy(enabled = true))
                route = Route.HOME.name
            },
        )

        Route.HOME -> DashboardScreen(
            config = config,
            usage = usage,
            accessibilityEnabled = accessibilityEnabled,
            deviceAdminActive = deviceAdminActive,
            labelOf = { pkg -> Guard.facts.labelOf(pkg).toString() },
            onOpenSettings = { pinTarget = PinTarget.SETTINGS },
            onTempUnlock = { pinTarget = PinTarget.UNLOCK },
            // 首页这两个入口不直接跳系统设置——先过密码，否则孩子点一下就进了设置。
            onOpenAccessibility = { pinTarget = PinTarget.SETTINGS },
            onEnableDeviceAdmin = { pinTarget = PinTarget.SETTINGS },
        )

        Route.SETTINGS -> SettingsScreen(
            config = config,
            accessibilityEnabled = accessibilityEnabled,
            deviceAdminActive = deviceAdminActive,
            deviceOwner = deviceOwner,
            onConfigChange = updateConfig,
            onBack = { route = Route.HOME.name },
            onOpenApps = { route = Route.APPS.name },
            onOpenAccessibility = ::openAccessibilitySettings,
            onEnableDeviceAdmin = ::openDeviceAdmin,
            onDeactivate = {
                GuardDeviceAdminReceiver.deactivate(context)
                updateConfig(config.copy(enabled = false, unlockUntilMillis = 0L))
                refreshPermissions()
                route = Route.HOME.name
            },
            onTempUnlock = doUnlock,
            onChangePin = { showCreatePin = true },
            usage = usage,
        )

        Route.APPS -> AppRulesScreen(
            config = config,
            onConfigChange = updateConfig,
            onBack = { route = Route.SETTINGS.name },
        )
    }

    val target = pinTarget
    if (target != null) {
        VerifyPinDialog(
            title = if (target == PinTarget.SETTINGS) "家长验证" else "临时解锁需要验证",
            pinLength = config.pin.length,
            verify = { pin -> Guard.pinGuard.verify(pin, Guard.config.current().pin) },
            initialLockedUntil = { Guard.pinGuard.stateSnapshot().lockedUntilMillis },
            onSuccess = {
                pinTarget = null
                when (target) {
                    PinTarget.SETTINGS -> route = Route.SETTINGS.name
                    PinTarget.UNLOCK -> doUnlock()
                }
            },
            onDismiss = { pinTarget = null },
        )
    }

    if (showCreatePin) {
        CreatePinDialog(
            onDone = { pin ->
                updateConfig(Guard.config.current().copy(pin = PinHasher.create(pin)))
                Guard.pinGuard.reset()
                showCreatePin = false
            },
            onDismiss = { showCreatePin = false },
        )
    }
    val message = notice
    if (message != null) {
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text("临时解锁") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { notice = null }) { Text("好") }
            },
        )
    }
}