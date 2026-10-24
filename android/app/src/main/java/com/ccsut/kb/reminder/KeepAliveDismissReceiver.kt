package com.ccsut.kb.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 用户左滑划掉保活常驻通知的回调 (通知 setDeleteIntent): 记入 KeepAliveService, 本进程内不再复活 */
class KeepAliveDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        KeepAliveService.onUserDismissed()
    }
}
