package com.ccsut.kb.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.R
import com.ccsut.kb.data.Dataset

/**
 * 关于面板: 品牌块 (连点 7 次进开发者模式) + 软件更新 + 课表数据 + 加入社区 + 项目链接。
 * 容器与「更多」同款 KbSheet: 宽屏从右侧滑入半屏侧面板, 窄屏底部弹层。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(
    dataset: Dataset,
    appVersion: String,
    devMode: Boolean,
    updateAvailable: String?,
    autoUpdateOn: Boolean,
    onAutoUpdateChange: (Boolean) -> Unit,
    fromUpdate: Boolean,
    onOpenUpdate: () -> Unit,
    onDevModeChange: (Boolean) -> Unit,
    onClearUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var showResetConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复内置数据") },
            text = { Text("会丢掉在线更新的那份课表，回到 app 自带的那份。") },
            confirmButton = {
                TextButton(
                    onClick = { showResetConfirm = false; onClearUpdate() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("先不用") }
            },
        )
    }
    val sheetScroll = rememberScrollState()
    KbSheet(onDismiss = onDismiss, title = "关于") {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // ---- 品牌块 ----
            var devClicks by remember { mutableIntStateOf(0) }
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 品牌块: 与启动器图标同款 (品牌蓝底 + 白色日历前景), 连点 7 次进开发者模式
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(LightColors.primary)
                        .combinedClickable(
                            onClick = {
                                if (devMode) return@combinedClickable
                                devClicks++
                                when {
                                    devClicks >= 7 -> {
                                        devClicks = 0
                                        onDevModeChange(true)
                                        Toast.makeText(ctx, "开发者模式已开启", Toast.LENGTH_SHORT).show()
                                    }
                                    devClicks >= 3 ->
                                        Toast.makeText(ctx, "再点 ${7 - devClicks} 次进入开发者模式", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = if (devMode) {
                                {
                                    onDevModeChange(false)
                                    Toast.makeText(ctx, "开发者模式已关闭", Toast.LENGTH_SHORT).show()
                                }
                            } else null,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_fg),
                        contentDescription = "长工课表通",
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "长工课表通",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (devMode) "v$appVersion · 开发者模式" else "v$appVersion",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "让查看课表这件事，不再那么狼狈。",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "课表来自教务系统公开课表，只保存在你手机上",
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }

            // ---- 软件更新 ----
            Spacer(Modifier.height(18.dp))
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.SystemUpdateAlt,
                    title = "软件更新",
                    subtitle = if (updateAvailable != null) "发现新版本 v$updateAvailable，点此查看"
                    else "当前 v$appVersion · 版本历史与回退",
                    subtitleColor = if (updateAvailable != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = { RowTrailing() },
                    onClick = onOpenUpdate,
                )
                SettingRow(
                    icon = Icons.Rounded.Refresh,
                    title = "更新提醒",
                    subtitle = "每次打开 App 自动检查新版本，发现更新时弹窗提醒",
                    trailing = { KbSwitch(checked = autoUpdateOn, onCheckedChange = onAutoUpdateChange) },
                    onClick = { onAutoUpdateChange(!autoUpdateOn) },
                )
            }

            // ---- 课表数据 ----
            // 数据包独立热更 (启动/刷新时自动), 手动检查在「软件更新」页
            SectionHeader("课表数据")
            // runCatching: 远端数据 generatedAt 字段畸形(非数字段)时不让关于页组合期崩溃
            val genDate = runCatching {
                dataset.generatedAt.substringBefore('T').split('-').let { d ->
                    if (d.size == 3) "${d[1].trim().toInt()}月${d[2].trim().toInt()}日" else ""
                }
            }.getOrDefault("")
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.CloudSync,
                    title = "课表数据包",
                    subtitle = buildString {
                        if (genDate.isNotEmpty()) append("${genDate}更新 · ")
                        append("${dataset.classes.size} 个班")
                    },
                )
                // 数据热更后偶发的回退口: 只在发生过在线数据更新时出现
                if (fromUpdate) {
                    SettingRow(
                        title = "恢复内置数据",
                        titleColor = MaterialTheme.colorScheme.error,
                        subtitle = "回退本次在线课表数据更新",
                        trailing = { RowTrailing() },
                        onClick = { showResetConfirm = true },
                    )
                }
            }

            // ---- 加入社区 ----
            // QQ 群/频道入口由 ui/Links.kt 的常量驱动, 留空即自动隐藏 (群号变动无需发版之外的操作)
            SectionHeader("加入社区")
            SettingCard {
                if (QQ_GROUP_UIN.isNotBlank()) {
                    SettingRow(
                        icon = Icons.Rounded.Groups,
                        title = "QQ 交流群",
                        subtitle = "$QQ_GROUP_NAME · 群号 $QQ_GROUP_UIN",
                        trailing = { RowTrailing() },
                        onClick = {
                            openQQGroup(ctx, QQ_GROUP_UIN) { uin ->
                                copyText(ctx, uin)
                                Toast.makeText(ctx, "群号 $uin 已复制，请在 QQ 中搜索加群", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                }
                if (QQ_CHANNEL_URL.isNotBlank()) {
                    SettingRow(
                        icon = Icons.Rounded.Forum,
                        title = "QQ 频道",
                        subtitle = "$QQ_CHANNEL_NAME · 公告与讨论",
                        trailing = { RowTrailing() },
                        onClick = { openUrl(ctx, QQ_CHANNEL_URL) },
                    )
                }
                SettingRow(
                    icon = Icons.Rounded.Star,
                    title = "给项目点个 Star",
                    subtitle = "GitHub，你的 Star 是更新的动力",
                    trailing = { RowTrailing() },
                    onClick = { openUrl(ctx, OPEN_SOURCE_URL) },
                )
                SettingRow(
                    icon = Icons.Rounded.Groups,
                    title = "想法与问答",
                    subtitle = "GitHub Discussions · 也欢迎先来 QQ 群聊",
                    trailing = { RowTrailing() },
                    onClick = { openUrl(ctx, DISCUSSIONS_URL) },
                )
                SettingRow(
                    icon = Icons.Rounded.BugReport,
                    title = "反馈问题",
                    subtitle = "Bug / 功能建议 / 课表数据",
                    trailing = { RowTrailing() },
                    onClick = { openUrl(ctx, ISSUES_URL) },
                )
            }

            // ---- 项目 ----
            SectionHeader("项目")
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.Code,
                    title = "源代码 · GitHub",
                    subtitle = "开源仓库，欢迎点亮 Star",
                    trailing = { RowTrailing() },
                    onClick = { openUrl(ctx, OPEN_SOURCE_URL) },
                )
                SettingRow(
                    icon = Icons.Rounded.PrivacyTip,
                    title = "隐私政策",
                    subtitle = "数据如何保存与使用",
                    trailing = { RowTrailing() },
                    onClick = { openUrl(ctx, PRIVACY_URL) },
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
