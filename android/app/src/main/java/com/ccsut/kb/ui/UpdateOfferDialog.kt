package com.ccsut.kb.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.data.ReleaseInfo
import com.ccsut.kb.data.Updater
import com.ccsut.kb.isBugfixUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 更新提醒弹窗 (进入软件检查到新版本时必弹, 不再区分修复/普通):
 * 显示目标版本 + 简短说明, 「立即更新」在弹窗内走下载进度条, 落盘后自动拉起系统安装。
 * 下载目录里已有该通道该版本的安装包时, 按钮直接变「立即安装」。
 * force (强制更新) 时不可取消 —— 无取消按钮, 点外部/返回键也不关闭; 老版本不解析该字段, 天然兼容。
 */
@Composable
fun UpdateOfferDialog(
    release: ReleaseInfo,
    downloading: String?,
    progress: Int?,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var cached by remember(release.versionName) { mutableStateOf(false) }
    // 下载目录实时查 (cachedApks 全集只在更新页加载后刷新, 弹窗不能依赖它)
    LaunchedEffect(release.versionName) {
        cached = withContext(Dispatchers.IO) {
            release.fileUrl != null &&
                Updater.cachedApk(ctx, release.channel, release.versionName, release.sha256) != null
        }
    }
    val busy = downloading == release.versionName

    AlertDialog(
        onDismissRequest = { if (!release.force) onCancel() },
        title = { Text(dialogTitle(release)) },
        text = {
            Column {
                // 更新说明: 绿色对勾条目 (与更新页同款), 条目多时默认只显示前两条, 可展开
                var notesExpanded by remember(release.versionName) { mutableStateOf(false) }
                val allItems = release.sections.flatMap { it.items }
                val items = if (notesExpanded) allItems else allItems.take(2)
                if (items.isEmpty()) {
                    Text("性能优化与问题修复。", fontSize = 14.sp, lineHeight = 20.sp)
                } else {
                    items.forEach { item ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = CourseColors.successColor(CourseColors.isDark(cs)),
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 1.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(item, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                    if (allItems.size > 2) {
                        Row(
                            Modifier
                                .padding(top = 2.dp)
                                .clickable { notesExpanded = !notesExpanded }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (notesExpanded) "收起" else "展开全部 ${allItems.size} 条",
                                fontSize = 13.sp,
                                color = cs.primary,
                            )
                            Spacer(Modifier.width(2.dp))
                            Icon(
                                if (notesExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = cs.primary,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
                if (busy) {
                    Spacer(Modifier.height(14.dp))
                    if (progress == null) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp))
                    } else {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (progress == null) "正在下载安装包…" else "正在下载 $progress%",
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (!busy) {
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(if (cached) "立即安装" else "立即更新", fontSize = 14.sp)
                }
            } else {
                // 下载中不显示按钮, 进度条即当前状态
                Spacer(Modifier.width(1.dp))
            }
        },
        dismissButton = if (release.force || busy) null else {
            { TextButton(onClick = onCancel) { Text("取消") } }
        },
    )
}

/** 弹窗标题: 强制更新 / 修复类 (说明带「修复」小节) / 普通新版本 */
private fun dialogTitle(r: ReleaseInfo): String = when {
    r.force -> "需要更新 v${r.versionName}"
    r.isBugfixUpdate() -> "发现修复更新 v${r.versionName}"
    else -> "发现新版本 v${r.versionName}"
}

/**
 * 开发者测试专用固定数据 (10 秒测试通知/弹窗、页面预览共用同一套):
 * 版本与文案固定; fileUrl 为空 —— 弹窗按钮可点但不会真实下载, 调用方按需提示。
 * 说明里带「修复」小节, 可同时验证通知/弹窗标题的修复性质分支。
 */
internal fun devTestRelease(force: Boolean = false): ReleaseInfo = ReleaseInfo(
    versionName = "9.9.9-test",
    versionCode = 99999,
    channel = com.ccsut.kb.data.Channels.TAG_STABLE,
    force = force,
    sections = listOf(
        com.ccsut.kb.data.NoteSection(
            "Fixed / 修复",
            listOf("【测试数据】这是固定的修复类说明，通知与弹窗标题应显示「发现修复更新」"),
        ),
        com.ccsut.kb.data.NoteSection(
            "Added / 新功能",
            listOf("【测试数据】这是固定的新功能说明，验证简介区展示"),
        ),
    ),
)
