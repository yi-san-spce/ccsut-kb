package com.ccsut.kb.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.data.UserEdits
import com.ccsut.kb.util.Weeks
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CourseFormSheet(
    title: String,
    weeks: Int,
    maxJc: Int,
    initial: UserEdits.Form,
    showDelete: Boolean,
    onSave: (UserEdits.Form) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var kc by rememberSaveable { mutableStateOf(initial.kc) }
    var teacher by rememberSaveable { mutableStateOf(initial.teacher) }
    var room by rememberSaveable { mutableStateOf(initial.room) }
    var day by rememberSaveable { mutableIntStateOf(initial.day) }
    var startJc by rememberSaveable { mutableIntStateOf(initial.startJc) }
    var span by rememberSaveable { mutableIntStateOf(initial.span) }
    // Set<Int> 用 List 存档 (Bundle 原生类型), 读取时还原
    var sel by rememberSaveable(stateSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })) {
        mutableStateOf(initial.ranges.flatMap { it.toList() }.toSet())
    }
    val sheetScroll = rememberScrollState()

    KbSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                .verticalScroll(sheetScroll)
                .navigationBarsPadding(),
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = kc,
                onValueChange = { kc = it },
                label = { Text("课程名（必填）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("教室") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            FormLabel("星期")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d ->
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (d == day) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable { day = d }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            Weeks.cnDay(d),
                            fontSize = 11.sp,
                            fontWeight = if (d == day) FontWeight.Bold else FontWeight.Medium,
                            color = if (d == day) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            FormLabel("起始节次")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                (1..maxJc).forEach { j ->
                    FormChip("$j", on = j == startJc, onClick = {
                        startJc = j
                        span = span.coerceAtMost(maxJc - j + 1)
                    })
                }
            }

            Spacer(Modifier.height(12.dp))
            FormLabel("连堂节数")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val maxSpan = (maxJc - startJc + 1).coerceAtMost(5)
                (1..maxSpan).forEach { s ->
                    FormChip("${s}节", on = s == span, onClick = { span = s })
                }
            }

            Spacer(Modifier.height(16.dp))
            FormLabel("上课周次（点击勾选）")
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..weeks).forEach { w ->
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (w in sel) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                            .clickable {
                                sel = if (w in sel) sel - w else sel + w
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$w",
                            fontSize = 11.sp,
                            fontWeight = if (w in sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (w in sel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(onClick = {
                sel = if (sel.size == weeks) emptySet() else (1..weeks).toSet()
            }) { Text(if (sel.size == weeks) "清空周次" else "全选周次") }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showDelete && onDelete != null) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("删除") }
                }
                Button(
                    onClick = {
                        val (zc, ranges) = UserEdits.zcOf(sel)
                        onSave(
                            UserEdits.Form(
                                kc = kc.trim(), teacher = teacher.trim(), room = room.trim(),
                                day = day, startJc = startJc, span = span,
                                zc = zc, ranges = ranges,
                            ),
                        )
                    },
                    enabled = kc.isNotBlank() && sel.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("保存") }
            }
        }
    }
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            label,
            Modifier.padding(end = 16.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ==================== 更多面板：分组卡片式设置 ====================
