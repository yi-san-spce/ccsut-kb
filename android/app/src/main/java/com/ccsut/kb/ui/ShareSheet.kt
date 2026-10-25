package com.ccsut.kb.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.R

/**
 * 分享课表面板 (更多 → 分享课表): 两个出口 + iPhone 导入指南。
 * ① 分享当前课表: 把正在生效的课表(个人/班级)导出 .ics 弹系统分享;
 * ② 帮朋友导出: 登录对方的教务账号临时拉取, 只生成文件不落盘 (登录页内有同意提示)。
 * 指南区 = 六步真机截图 (drawable-nodpi/ics_guide_1..6, 已打码) + B 站视频 + 可转发文本。
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
    val ctx = LocalContext.current
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

            SectionHeader("iPhone 怎么导入？")
            // 六步真机截图横滑 (已打码): 每张下带序号+要点。KbSheet 只接管纵向嵌套滚动, 横滑不冲突
            GuideStepsRow()
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.OndemandVideo,
                    title = "视频教程：课表导入 iPhone 日历",
                    subtitle = "哔哩哔哩 · 6 分钟 · 课程表 ICS 专讲",
                    trailing = { RowTrailing("打开") },
                    onClick = { openUrl(ctx, IOS_ICS_GUIDE_VIDEO_URL) },
                )
                SettingRow(
                    icon = Icons.Rounded.Share,
                    title = "把导入教程发给朋友",
                    subtitle = "文字步骤 + 视频链接，微信 / QQ 直接发",
                    trailing = { RowTrailing("分享") },
                    onClick = { shareText(ctx, "分享导入教程", ICS_GUIDE_SHARE_TEXT) },
                )
            }

            Spacer(Modifier.padding(top = 10.dp))
            Text(
                "以上即 iPhone 的完整导入步骤；电脑端也可在 Google 日历「设置 → 导入」上传 .ics。单周调休（如周日补上周一的课）暂不支持，以学校校历为准。",
                fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 18.sp,
            )
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

/** 六步截图 + 序号要点 (高 230dp, 宽按各截图宽高比还原) */
@Composable
private fun GuideStepsRow() {
    // (drawable, 宽高比 w/h = 720/原图高, 要点; 全部控制在一行内 —— 行数不一致会让横滑时
    //  行高随可见项变化, 下方内容上下抽动)
    val steps = listOf(
        Triple(R.drawable.ics_guide_1, 720f / 889f, "微信点「用其他应用打开」"),
        Triple(R.drawable.ics_guide_2, 720f / 703f, "选「保存到“文件”」"),
        Triple(R.drawable.ics_guide_3, 720f / 999f, "「文件」里长按举起"),
        Triple(R.drawable.ics_guide_4, 720f / 1019f, "按住回主屏拖进日历"),
        Triple(R.drawable.ics_guide_5, 720f / 838f, "点「添加全部」"),
        Triple(R.drawable.ics_guide_6, 720f / 916f, "选一个日历确认"),
    )
    val marks = listOf("①", "②", "③", "④", "⑤", "⑥")
    val cs = MaterialTheme.colorScheme
    LazyRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        itemsIndexed(steps) { i, (res, ratio, caption) ->
            Column(Modifier.width(230.dp * ratio)) {
                Image(
                    painter = painterResource(res),
                    contentDescription = "iPhone 导入步骤${i + 1}:$caption",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    marks[i] + " " + caption,
                    fontSize = 11.sp, lineHeight = 15.sp,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
