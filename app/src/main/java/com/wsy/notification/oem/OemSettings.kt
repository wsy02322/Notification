package com.wsy.notification.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.Locale

object OemSettings {
    fun manufacturer(): String = Build.MANUFACTURER.lowercase(Locale.ROOT)

    fun brandLabel(): String {
        val m = manufacturer()
        return when {
            m in listOf("xiaomi", "redmi", "poco") -> "小米 / 红米"
            m in listOf("oppo") -> "OPPO"
            m in listOf("realme") -> "realme"
            m in listOf("oneplus") -> "一加"
            m in listOf("vivo", "iqoo") -> "vivo"
            m in listOf("samsung") -> "三星"
            m in listOf("huawei", "honor") -> "华为 / 荣耀"
            else -> Build.MANUFACTURER
        }
    }

    fun guidanceLines(): List<String> {
        val m = manufacturer()
        return when {
            m in listOf("xiaomi", "redmi", "poco") -> listOf(
                "打开「自启动」",
                "应用权限里打开「后台自动启动」",
                "省电策略设为「无限制」",
                "最近任务下拉锁定本应用",
                "不要开启超强省电",
            )
            m in listOf("oppo", "realme", "oneplus") -> listOf(
                "允许「自启动」",
                "允许后台运行 / 前台活动",
                "打开「后台弹出界面」（确认页需要）",
                "关闭该应用的智能限制 / 深度休眠",
                "最近任务锁定；一加旧系统请关闭高级优化",
            )
            m in listOf("vivo", "iqoo") -> listOf(
                "设置 → 应用 → 打开「自启动」",
                "电池 → 允许「后台高耗电」",
                "应用电池设为「无限制」",
                "最近任务锁定",
                "不要放入应用速冻",
            )
            m in listOf("huawei", "honor") -> listOf(
                "应用启动管理设为「手动管理」，允许自启动 / 关联启动 / 后台活动",
                "必须打开「后台弹出界面」",
                "电池设为「不允许」优化；关闭休眠暂停应用",
                "最近任务锁定；不要开超级省电",
                "熄屏测前先确认通知栏一直有「正在监听」",
            )
            m in listOf("samsung") -> listOf(
                "应用电池设为「不受限」",
                "从休眠 / 深度休眠列表中移除本应用",
                "关闭「自动将未使用的应用置于休眠」",
                "可加入「从不休眠的应用」",
                "系统更新后复查，三星可能会重新加入休眠",
            )
            else -> listOf(
                "关闭本应用的电池优化",
                "不要从最近任务划掉本应用",
                "保持前台通知「正在监听」常驻",
            )
        }
    }

    fun openAutostartOrBattery(context: Context): Boolean {
        val intents = autostartIntents() + batteryIntents()
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: Exception) {
                    // try next
                }
            }
        }
        PermissionChecker.openAppDetails(context)
        return false
    }

    private fun autostartIntents(): List<Intent> {
        val m = manufacturer()
        val list = mutableListOf<Intent>()
        if (m in listOf("xiaomi", "redmi", "poco")) {
            list += component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            list += Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        }
        if (m in listOf("oppo", "realme")) {
            list += component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            list += component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            list += component("com.color.safecenter", "com.color.safecenter.permission.startup.StartupAppListActivity")
        }
        if (m == "oneplus") {
            list += component("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        }
        if (m in listOf("vivo", "iqoo")) {
            list += component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
            list += component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")
        }
        if (m in listOf("huawei", "honor")) {
            list += component("com.hihonor.systemmanager", "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity")
            list += component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")
            list += component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            list += component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        }
        if (m == "samsung") {
            list += component("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")
            list += component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
            list += Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        return list
    }

    private fun batteryIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))
}
