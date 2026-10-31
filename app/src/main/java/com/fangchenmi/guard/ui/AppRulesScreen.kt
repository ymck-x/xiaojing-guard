package com.fangchenmi.guard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fangchenmi.guard.Features
import com.fangchenmi.guard.core.model.AppCategory
import com.fangchenmi.guard.core.model.AppRule
import com.fangchenmi.guard.core.model.GuardConfig
import com.fangchenmi.guard.data.AppCatalog
import com.fangchenmi.guard.data.InstalledApp
import com.fangchenmi.guard.ui.components.MinuteStepper
import com.fangchenmi.guard.ui.components.StatusGood
import com.fangchenmi.guard.ui.components.StatusWarn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppRulesScreen(
    config: GuardConfig,
    onConfigChange: (GuardConfig) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<InstalledApp?>(null) }
    var paywall by remember { mutableStateOf<String?>(null) }
    val limits = Features.limits

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            AppCatalog(context).load { partial ->
                withContext(Dispatchers.Main) {
                    apps = partial
                    loading = false
                }
            }
        }
        loading = false
    }

    fun setCategory(app: InstalledApp, category: AppCategory) {
        val existing = config.ruleFor(app.pkg)
        // 免费版最多 3 个「禁止」：超了就提示升级，而不是让家长设了却不生效。
        if (category == AppCategory.BLOCK && existing?.category != AppCategory.BLOCK) {
            val blocked = config.appRules.count { it.category == AppCategory.BLOCK }
            if (blocked >= limits.maxBlockedApps) {
                paywall = "免费版最多把 ${limits.maxBlockedApps} 个应用设为「禁止」。付费版不限数量。"
                return
            }
        }
        val updated = existing?.copy(category = category, label = app.label)
            ?: AppRule(pkg = app.pkg, label = app.label, category = category)
        onConfigChange(config.copy(appRules = config.appRules.filterNot { it.pkg == app.pkg } + updated))
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true) }
    }
    val limitAllowed = limits.allowPerAppLimit

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Spacer(Modifier.weight(1f))
            Text("应用规则", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "学习 = 不计时也不拦；娱乐 = 计入每日额度；禁止 = 打开就弹回桌面。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("搜索应用") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "正在读取应用列表…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                "共 ${apps.size} 个应用，已单独设置 ${config.appRules.size} 个",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.pkg }) { app ->
                AppRow(
                    app = app,
                    config = config,
                    allowLimit = limitAllowed,
                    onCategory = { setCategory(app, it) },
                    onEditLimit = {
                        if (limitAllowed) editing = app
                        else paywall = "单应用限额是付费版功能：抖音每天只能玩 20 分钟这种设置，免费版没有。"
                    },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    val target = editing
    val wall = paywall
    if (wall != null) {
        AlertDialog(
            onDismissRequest = { paywall = null },
            title = { Text("付费版功能") },
            text = { Text(wall) },
            confirmButton = { TextButton(onClick = { paywall = null }) { Text("知道了") } },
        )
    }
    if (target != null) {
        val rule = config.ruleFor(target.pkg)
        var minutes by remember(target.pkg) {
            mutableStateOf(rule?.dailyLimitMinutes ?: 30)
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("「${target.label}」每日限额") },
            text = {
                Column {
                    Text(
                        "0 表示不单独限制，只计入每日总额度。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    MinuteStepper(
                        value = minutes,
                        onChange = { minutes = it },
                        step = 5,
                        max = 240,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val existing = config.ruleFor(target.pkg)
                    val updated = (existing ?: AppRule(pkg = target.pkg, label = target.label))
                        .copy(dailyLimitMinutes = minutes)
                    onConfigChange(
                        config.copy(
                            appRules = config.appRules.filterNot { it.pkg == target.pkg } + updated,
                        ),
                    )
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    config: GuardConfig,
    allowLimit: Boolean,
    onCategory: (AppCategory) -> Unit,
    onEditLimit: () -> Unit,
) {
    val rule = config.ruleFor(app.pkg)
    val category = rule?.category ?: config.uncategorizedCategory
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = app.icon
                if (icon != null) {
                    Image(
                        bitmap = icon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = when (category) {
                            AppCategory.STUDY -> "不计时"
                            AppCategory.BLOCK -> "禁止使用"
                            AppCategory.ENTERTAIN -> if ((rule?.dailyLimitMinutes ?: 0) > 0) {
                                "每天限 ${rule?.dailyLimitMinutes} 分钟"
                            } else {
                                "计入每日额度"
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when (category) {
                            AppCategory.STUDY -> StatusGood
                            AppCategory.BLOCK -> StatusWarn
                            AppCategory.ENTERTAIN -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryChip("学习", category == AppCategory.STUDY) { onCategory(AppCategory.STUDY) }
                CategoryChip("娱乐", category == AppCategory.ENTERTAIN) { onCategory(AppCategory.ENTERTAIN) }
                CategoryChip("禁止", category == AppCategory.BLOCK) { onCategory(AppCategory.BLOCK) }
                Spacer(Modifier.weight(1f))
                if (category == AppCategory.ENTERTAIN) {
                    Text(
                        if (allowLimit) "限额" else "限额 🔒",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (allowLimit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onEditLimit)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
