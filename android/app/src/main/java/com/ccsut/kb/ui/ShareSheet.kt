package com.ccsut.kb.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 分享课表面板 (更多 → 分享课表): 两个出口。
 * ① 分享当前课表: 把正在生效的课表(个人/班级)导出 .ics 弹系统分享;
 * ② 帮朋友导出: 登录对方的教务账号临时拉取, 只生成文件不落盘 (登录页内有同意提示)。
 */
@Composable
fun ShareSheet(
    hasCurrent: Boolean,
    activeLabel: String,
    onShareCurrent: () -> Unit,
    onShareFriend: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val sheetScroll = rememberScrollState()
    KbSheet(onDismiss = onDismiss, title = "分享课表") {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader("导出")
            SettingCard {
                if (hasCurrent) {
                    SettingRow(
                        icon = Icons.Rounded.IosShare,
                        title = "分享当前课表",
                        subtitle = "正在生效：$activeLabel",
                        trailing = { RowTrailing("导出") },
                        onClick = onShareCurrent,
                    )
                } else {
                    // 没有任何课表数据 (内置数据未加载完/全空): 置灰不可点
                    SettingRow(
                        icon = Icons.Rounded.IosShare,
                        title = "分享当前课表",
                        subtitle = "还没有可分享的课表数据",
                        titleColor = cs.onSurface.copy(alpha = 0.38f),
                        subtitleColor = cs.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                SettingRow(
                    icon = Icons.Rounded.Group,
                    title = "帮朋友导出",
                    subtitle = "登录对方的教务账号拉取 TA 的课表，只在内存生成文件",
                    trailing = { RowTrailing("登录") },
                    onClick = onShareFriend,
                )
            }

            Spacer(Modifier.padding(top = 10.dp))
            Text(
                ".ics 是日历通用格式：对方在 iPhone 上把文件存到「文件」后点开，选「添加全部」即可导入 Apple 日历（Google 日历等同样支持）。单周调休（如周日补上周一的课）暂不支持，以学校校历为准。",
                fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 18.sp,
            )
            Spacer(Modifier.padding(top = 8.dp))
            Text(
                "帮朋友导出会用对方的账号登录：导出完成后你自己的教务登录会话会被让出，下次同步自己的课表需重新验证码登录。请确保已获得对方同意。",
                fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 18.sp,
            )
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}
