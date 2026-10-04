package com.ccsut.kb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Dataset
import kotlinx.coroutines.delay

/** 四屏欢迎向导: Slogan → 三件事 → 认识一下 → 欢迎。全新安装首次启动展示, 右上角可跳过。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(
    dataset: Dataset,
    onDone: (nickname: String, classId: String) -> Unit,
    onSkip: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var nickname by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf<String?>(null) }
    var major by remember { mutableStateOf<String?>(null) }
    var classId by remember { mutableStateOf<String?>(null) }

    // 年级→专业→班级索引: 数据树按学院组织且跨年级混合, 这里按班级属性动态重建, 保证与数据完全匹配
    val grades = remember(dataset) {
        dataset.classes.values.map { it.sznj }.filter { it.length == 4 }.distinct().sorted()
    }
    val majors = remember(dataset, grade) {
        grade?.let { g -> dataset.classes.values.filter { it.sznj == g }.map { it.zymc }.distinct().sorted() }
    }
    val classes: List<Pair<String, Cls>> = remember(dataset, grade, major) {
        if (grade == null || major == null) emptyList()
        else dataset.classes.entries
            .filter { it.value.sznj == grade && it.value.zymc == major }
            .sortedWith { a, b -> naturalCmp(a.value.bjmc, b.value.bjmc) }
            .map { it.key to it.value }
    }

    BackHandler(enabled = step > 0) { step-- }

    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 顶栏: 上一步 / 跳过
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step in 1..2) {
                IconButton(onClick = { step-- }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一步", tint = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.weight(1f))
            if (step < 3) {
                TextButton(onClick = onSkip) { Text("跳过", fontSize = 14.sp, color = cs.onSurfaceVariant) }
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val fwd = targetState > initialState
                (
                    slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { if (fwd) it / 4 else -it / 4 } +
                        fadeIn(tween(350))
                    ) togetherWith (
                    slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { if (fwd) -it / 4 else it / 4 } +
                        fadeOut(tween(150))
                    )
            },
            modifier = Modifier.weight(1f),
            label = "welcomeStep",
        ) { s ->
            when (s) {
                0 -> SloganBody()
                1 -> HonestBody()
                2 -> MeetBody(
                    nickname = nickname,
                    onNickname = { nickname = it },
                    grades = grades,
                    grade = grade,
                    majors = majors ?: emptyList(),
                    major = major,
                    classes = classes,
                    classId = classId,
                    onGrade = { grade = it; major = null; classId = null },
                    onMajor = { major = it; classId = null },
                    onClass = { classId = it },
                )
                else -> DoneBody(nickname = nickname.trim())
            }
        }

        Button(
            onClick = {
                when (step) {
                    0, 1 -> step++
                    else -> classId?.let { onDone(nickname.trim(), it) }
                }
            },
            enabled = step != 2 || classId != null,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .height(52.dp),
        ) {
            Text(
                when (step) {
                    2 -> "完成"
                    3 -> "进入课表"
                    else -> "继续"
                },
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ---------------- 第 1 屏: Slogan ----------------

@Composable
private fun SloganBody() {
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(300); shown = 1
        delay(450); shown = 2
        delay(450); shown = 3
    }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Rise(shown >= 1) {
            Text("你好,我是长工课表通。", fontSize = 15.sp, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Rise(shown >= 2) {
            Text(
                "让查看课表这件事,",
                fontSize = 30.sp, fontWeight = FontWeight.Bold,
                color = cs.onSurface, lineHeight = 40.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Rise(shown >= 3) {
            Text(
                buildAnnotatedString {
                    append("不再那么")
                    withStyle(SpanStyle(color = cs.primary)) { append("狼狈") }
                    append("。")
                },
                fontSize = 30.sp, fontWeight = FontWeight.Bold,
                lineHeight = 40.sp,
            )
        }
    }
}

// ---------------- 第 2 屏: 三件想坦白的事 ----------------

@Composable
private fun HonestBody() {
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(250); shown = 1
        delay(200); shown = 2
        delay(200); shown = 3
    }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("用之前,想跟你说三件事。", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(22.dp))
        HonestCard(Icons.Filled.WifiOff, "课表全存在你手机里", "没网也能看,我们不收集任何信息", shown >= 1)
        HonestCard(Icons.Filled.Campaign, "老师临时换课,APP 反应不过来", "记得以老师的通知为准", shown >= 2)
        HonestCard(Icons.Filled.EditCalendar, "国庆、五一这类调休", "我们会提前统一更新课表", shown >= 3)
    }
}

@Composable
private fun HonestCard(icon: ImageVector, title: String, sub: String, visible: Boolean) {
    Rise(visible, Modifier.padding(bottom = 10.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------- 第 3 屏: 认识一下(昵称 + 年级/专业/班级) ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MeetBody(
    nickname: String,
    onNickname: (String) -> Unit,
    grades: List<String>,
    grade: String?,
    majors: List<String>,
    major: String?,
    classes: List<Pair<String, Cls>>,
    classId: String?,
    onGrade: (String) -> Unit,
    onMajor: (String) -> Unit,
    onClass: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("最后,认识一下?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = onNickname,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("姓名昵称都行,也可以不填", fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(22.dp))
        GroupLabel("年级")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            grades.forEach { g ->
                FilterChip(
                    selected = g == grade,
                    onClick = { onGrade(g) },
                    label = { Text("${g}级", fontSize = 14.sp) },
                )
            }
        }

        AnimatedVisibility(majors.isNotEmpty()) {
            Column {
                Spacer(Modifier.height(16.dp))
                GroupLabel("专业")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    majors.forEach { m ->
                        FilterChip(
                            selected = m == major,
                            onClick = { onMajor(m) },
                            label = { Text(m, fontSize = 14.sp) },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(classes.isNotEmpty()) {
            Column {
                Spacer(Modifier.height(16.dp))
                GroupLabel("班级")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    classes.forEach { (id, c) ->
                        FilterChip(
                            selected = id == classId,
                            onClick = { onClass(id) },
                            label = { Text(c.bjmc, fontSize = 14.sp) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

// ---------------- 第 4 屏: 欢迎你 ----------------

@Composable
private fun DoneBody(nickname: String) {
    var shown by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(200); shown = 1
        delay(500); shown = 2
    }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Rise(shown >= 1) {
            Text(
                "欢迎你,${nickname.ifBlank { "同学" }}。",
                fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        Rise(shown >= 2) {
            Text("你的使用,是我们的荣幸。", fontSize = 16.sp, color = cs.onSurfaceVariant)
        }
    }
}

// ---------------- 通用小件 ----------------

/** 逐行浮现: 淡入 + 轻微上移 */
@Composable
private fun Rise(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a by animateFloatAsState(if (visible) 1f else 0f, tween(550, easing = FastOutSlowInEasing), label = "riseA")
    val y by animateDpAsState(if (visible) 0.dp else 20.dp, tween(550, easing = FastOutSlowInEasing), label = "riseY")
    Box(modifier.graphicsLayer { alpha = a; translationY = y.toPx() }) { content() }
}

/** 班级名自然排序: 数字段按数值比较, "2班" < "10班" */
private fun naturalCmp(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a[i]
        val cb = b[j]
        if (ca.isDigit() && cb.isDigit()) {
            var i2 = i
            while (i2 < a.length && a[i2].isDigit()) i2++
            var j2 = j
            while (j2 < b.length && b[j2].isDigit()) j2++
            val na = a.substring(i, i2).toLong()
            val nb = b.substring(j, j2).toLong()
            if (na != nb) return if (na < nb) -1 else 1
            i = i2
            j = j2
        } else {
            if (ca != cb) return ca.compareTo(cb)
            i++
            j++
        }
    }
    return (a.length - i).compareTo(b.length - j)
}
