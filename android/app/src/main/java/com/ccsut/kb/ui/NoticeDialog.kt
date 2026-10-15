package com.ccsut.kb.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 远程通知与更新提醒共用的弹窗外壳。
 *
 * - critical = true: 阻断式 —— 只显示主按钮, 点外部/返回键不关闭 (onDismiss 不回调);
 * - critical = false: 主/次双按钮, 点外部等同 onDismiss;
 * - dismissText 传 null 时也不显示次按钮 (主按钮单按钮的可关闭形态)。
 * 按钮文案完全由调用方决定, 外壳只管形态与阻断语义。
 */
@Composable
fun NoticeDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String?,
    critical: Boolean,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    AlertDialog(
        onDismissRequest = { if (!critical) onDismiss?.invoke() },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = if (critical || dismissText == null) null else {
            { TextButton(onClick = { onDismiss?.invoke() }) { Text(dismissText) } }
        },
    )
}
