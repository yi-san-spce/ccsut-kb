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
 * 现有: 「今天」状态条 (含「回到本周」药丸) 底色透明度;
 * 之后的自定义选项都往这个面板里加。
 */
@Composable
fun CustomAppearanceSheet(
    todayBarAlpha: Int,
    onTodayBarAlpha: (Int) -> Unit,
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
                    subtitle = "「今天共几节课」大卡片, 「回到本周」药丸跟随",
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
                            "$todayBarAlpha%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = todayBarAlpha / 100f,
                        onValueChange = { onTodayBarAlpha((it * 100).toInt().coerceIn(10, 100)) },
                    )
                }
            }
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}
