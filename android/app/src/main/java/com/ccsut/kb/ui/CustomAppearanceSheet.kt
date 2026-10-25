package com.ccsut.kb.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 自定义外观面板: 主页个性化的集中地。
 * 现有: 「今天」状态条 (含「回到本周」药丸) 透明度 (0=完全不透明, 100=纯文字) 与
 * 顶栏透明度 (0=毛玻璃, 100=纯文字玻璃字); 之后的自定义选项都往这个面板里加。
 */
@Composable
fun CustomAppearanceSheet(
    todayBarTransparency: Int,
    onTodayBarTransparency: (Int) -> Unit,
    topbarTransparency: Int,
    onTopbarTransparency: (Int) -> Unit,
    widgetGlass: Int,
    onWidgetGlass: (Int) -> Unit,
    onWidgetGlassCommit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetScroll = rememberScrollState()
    KbSheet(onDismiss = onDismiss, title = "自定义外观") {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader("主页")
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.Tune,
                    title = "状态条透明度",
                    subtitle = "0=完全不透明, 100=纯文字(无底无磨砂); 「回到本周」药丸跟随",
                )
                Column(Modifier.padding(start = 62.dp, end = 14.dp, bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "透明度",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$todayBarTransparency%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = todayBarTransparency / 100f,
                        onValueChange = { onTodayBarTransparency((it * 100).toInt().coerceIn(0, 100)) },
                    )
                }
                SettingRow(
                    icon = Icons.Rounded.Tune,
                    title = "顶栏透明度",
                    subtitle = "0=毛玻璃, 100=纯文字玻璃字(无底无磨砂); 设置背景图后生效",
                )
                Column(Modifier.padding(start = 62.dp, end = 14.dp, bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "透明度",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$topbarTransparency%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = topbarTransparency / 100f,
                        onValueChange = { onTopbarTransparency((it * 100).toInt().coerceIn(0, 100)) },
                    )
                }
            }
            Spacer(Modifier.padding(bottom = 12.dp))
            SectionHeader("桌面")
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.Widgets,
                    title = "小组件玻璃强度",
                    subtitle = "桌面小组件的磨砂玻璃浓度; 0=关玻璃回实色卡片",
                )
                Column(Modifier.padding(start = 62.dp, end = 14.dp, bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "强度",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$widgetGlass%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = widgetGlass / 100f,
                        // 松手才真正重渲染 (磨砂快照现算), 拖动过程只更新数字
                        onValueChange = { onWidgetGlass((it * 100).toInt().coerceIn(0, 100)) },
                        onValueChangeFinished = onWidgetGlassCommit,
                    )
                }
            }
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}
