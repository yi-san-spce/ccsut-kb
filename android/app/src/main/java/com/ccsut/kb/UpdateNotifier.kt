package com.ccsut.kb

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ccsut.kb.data.ReleaseInfo
import com.ccsut.kb.util.DebugLog

/**
 * 软件更新的系统消息通知。展示形态分流 (见 MainActivity.checkUpdatesAndNotice):
 * 进入软件检查到新版本一律弹应用内更新弹窗; 这里负责后台侧的提醒 ——
 * 通知文案带版本性质: 强制更新 / 修复类 (说明带「修复」小节) / 普通功能更新。
 * 点按进软件更新页; 划掉通知 = 跳过该版本 (UpdateDismissReceiver 记录), 出现更高版本时重新提醒。
 */
object UpdateNotifier {

    const val EXTRA_OPEN_UPDATE = "open_update"
    const val EXTRA_VERSION = "version"
    private const val CHANNEL_ID = "update_notify"
    private const val NOTIFY_ID = 2001

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "更新提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "发现新版本时在通知栏提醒"
            },
        )
    }

    private fun hasPermission(ctx: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** 投递可更新通知 (每次前台检查重复投递, 同 id 静默替换不重复打扰); 无通知权限时跳过 (关于页仍有徽标) */
    fun notifyAvailable(ctx: Context, r: ReleaseInfo) {
        if (!hasPermission(ctx)) {
            DebugLog.log("update", "发现新版本但缺 POST_NOTIFICATIONS 权限, 通知未发")
            return
        }
        ensureChannel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val tapPi = PendingIntent.getActivity(
            ctx, 6,
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deletePi = PendingIntent.getBroadcast(
            ctx, 7,
            Intent(ctx, UpdateDismissReceiver::class.java).putExtra(EXTRA_VERSION, r.versionName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val first = r.sections.firstOrNull()?.items?.firstOrNull().orEmpty().ifBlank { "点按查看更新内容并安装" }
        val text = "v${r.versionName}：$first"
        val title = when {
            r.force -> "新版本 v${r.versionName} · 需要更新"
            r.isBugfixUpdate() -> "发现修复更新 v${r.versionName}"
            else -> "发现新版本 v${r.versionName}"
        }
        nm.notify(
            NOTIFY_ID,
            NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(tapPi)
                .setDeleteIntent(deletePi)
                .build(),
        )
        DebugLog.log("update", "更新通知已发: v${r.versionName}")
    }

    /** 清掉历史更新通知 (版本已读/已跳过/开关关闭/无更新时调用) */
    fun cancel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFY_ID)
    }
}

/** 发布说明带「修复」小节 = 修复类更新 → 阻断弹窗强提醒; 其余 = 普通更新 → 通知栏 */
fun ReleaseInfo.isBugfixUpdate(): Boolean = sections.any { it.title?.contains("修复") == true }

/** 用户划掉更新通知 = 跳过该版本: 记录后同版本不再提醒 (出现更高版本时重新提醒) */
class UpdateDismissReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val ver = intent.getStringExtra(UpdateNotifier.EXTRA_VERSION)?.takeIf { it.isNotBlank() } ?: return
        Prefs.setUpdateDismissedVersion(ctx, ver)
        DebugLog.log("update", "更新通知被划掉, 跳过版本 v$ver")
    }
}
