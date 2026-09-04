package com.wsy.notification.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wsy.notification.alert.AlertForegroundService
import com.wsy.notification.debug.DebugLog
import com.wsy.notification.debug.LogActivity
import com.wsy.notification.match.MonitoredApp
import com.wsy.notification.oem.OemSettings
import com.wsy.notification.oem.PermissionChecker
import com.wsy.notification.prefs.MonitorPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute() {
    val context = LocalContext.current
    val prefs = remember { MonitorPrefs(context) }
    var selected by remember { mutableStateOf(prefs.selectedPackages) }
    var keywords by remember { mutableStateOf(prefs.keywordsRaw) }
    var monitoring by remember { mutableStateOf(prefs.monitoringEnabled) }
    var listenerOn by remember { mutableStateOf(false) }
    var canNotify by remember { mutableStateOf(true) }
    var batteryOk by remember { mutableStateOf(false) }
    var fsiOk by remember { mutableStateOf(true) }

    fun refreshPermissions() {
        listenerOn = PermissionChecker.isNotificationListenerEnabled(context)
        canNotify = PermissionChecker.canPostNotifications(context)
        batteryOk = PermissionChecker.isIgnoringBatteryOptimizations(context)
        fsiOk = PermissionChecker.canUseFullScreenIntent(context)
        monitoring = prefs.monitoringEnabled
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPermissions() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("消息监控") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(monitoring = monitoring, listenerOn = listenerOn)

            Text("系统设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ChecklistRow("通知使用权", listenerOn) {
                PermissionChecker.openNotificationListenerSettings(context)
            }
            ChecklistRow("通知权限", canNotify) {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    PermissionChecker.openAppNotificationSettings(context)
                }
            }
            ChecklistRow("忽略电池优化", batteryOk) {
                PermissionChecker.requestIgnoreBatteryOptimizations(context)
            }
            ChecklistRow("全屏提醒（熄屏弹出确认页）", fsiOk) {
                PermissionChecker.openFullScreenIntentSettings(context)
            }

            Text(
                "当前机型：${OemSettings.brandLabel()}。再打开厂商后台设置，并在最近任务里锁定本应用。",
                style = MaterialTheme.typography.bodySmall,
            )
            OemSettings.guidanceLines().forEach { line ->
                Text("· $line", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { OemSettings.openAutostartOrBattery(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("打开厂商自启动 / 电池设置") }

            Text("监控的 App", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MonitoredApp.all.forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = app.packageName in selected,
                        onCheckedChange = { checked ->
                            selected = if (checked) {
                                selected + app.packageName
                            } else {
                                selected - app.packageName
                            }
                            prefs.selectedPackages = selected
                        },
                    )
                    Text(
                        text = if (app.required) app.displayName else "${app.displayName}（可选）",
                    )
                }
            }

            Text("关键词（OR 命中）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "不区分大小写。多个词用换行或逗号分隔。命中 App 名、发送人或正文任一字段即提醒。留空 = 所选 App 每条都提醒。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = keywords,
                onValueChange = {
                    keywords = it
                    prefs.keywordsRaw = it
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = { Text("例如：已付款, 张三") },
            )

            Button(
                onClick = {
                    prefs.selectedPackages = selected
                    prefs.keywordsRaw = keywords
                    if (selected.isEmpty()) {
                        Toast.makeText(context, "请至少勾选一个 App", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!listenerOn) {
                        Toast.makeText(context, "请先开启通知使用权", Toast.LENGTH_LONG).show()
                        PermissionChecker.openNotificationListenerSettings(context)
                        return@Button
                    }
                    if (Build.VERSION.SDK_INT >= 33 && !canNotify) {
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (!batteryOk) {
                        PermissionChecker.requestIgnoreBatteryOptimizations(context)
                    }
                    prefs.monitoringEnabled = true
                    monitoring = true
                    DebugLog.writeSnapshot(context)
                    DebugLog.i("UI", "start monitoring apps=$selected keywords='${keywords.replace("\n", " | ")}'")
                    PermissionChecker.bounceNotificationListener(context)
                    DebugLog.i("UI", "bounced notification listener component")
                    AlertForegroundService.start(context)
                    Toast.makeText(context, "已开始监听", Toast.LENGTH_SHORT).show()
                },
                enabled = !monitoring,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("开始监听") }

            OutlinedButton(
                onClick = {
                    prefs.monitoringEnabled = false
                    monitoring = false
                    DebugLog.i("UI", "stop monitoring")
                    AlertForegroundService.stop(context)
                    Toast.makeText(context, "已停止监听", Toast.LENGTH_SHORT).show()
                },
                enabled = monitoring,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("停止监听") }

            OutlinedButton(
                onClick = {
                    DebugLog.i("UI", "tap test alert")
                    AlertForegroundService.start(context)
                    AlertForegroundService.test(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("测试提醒（循环震动+音乐，点我知道了才停）") }

            Button(
                onClick = {
                    context.startActivity(Intent(context, LogActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("查看 / 导出测试日志") }

            Text(
                "测完后打开「查看 / 导出测试日志」，点导出或复制，把文件发给我排查。",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                "被监控的微信 / 闲鱼也需要打开系统通知，并尽量允许自启动。若关闭消息详情，发送人和正文关键词会失效，但勾选了该 App 仍会提醒。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun StatusCard(monitoring: Boolean, listenerOn: Boolean) {
    val ok = monitoring && listenerOn
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = when {
                    ok -> "正在监听"
                    monitoring && !listenerOn -> "监听已开，但通知使用权未授予"
                    else -> "未监听"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (ok) "熄屏后也会尝试捕获通知并循环提醒，直到你确认。"
                else "先完成下方系统设置，再点开始监听。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ChecklistRow(label: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = if (ok) "已开启 · $label" else "未开启 · $label",
            modifier = Modifier.weight(1f),
        )
        if (!ok) {
            OutlinedButton(onClick = onClick) { Text("去开启") }
        }
    }
}
