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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ripple
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Dataset
import kotlinx.coroutines.delay

/** 四屏欢迎向导: Slogan → 三件事 → 认识一下(昵称 + 班级/个人课表并列选择) → 欢迎。全新安装首次启动展示, 右上角可跳过。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeScreen(
    dataset: Dataset,
    personalLoggedIn: Boolean,
    studentName: String,
    onOpenLogin: () -> Unit,
    onDone: (nickname: String, classId: String?) -> Unit,
    onSkip: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var nickname by rememberSaveable { mutableStateOf("") }
    var classId by rememberSaveable { mutableStateOf<String?>(null) }
    val picked = remember(dataset, classId) { classId?.let { dataset.classes[it] } }

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
                    dataset = dataset,
                    nickname = nickname,
                    onNickname = { nickname = it },
                    picked = picked,
                    onPick = { classId = it },
                    personalLoggedIn = personalLoggedIn,
                    studentName = studentName,
                    onOpenLogin = onOpenLogin,
                )
                else -> DoneBody(nickname = nickname.trim())
            }
        }

        Button(
            onClick = {
                when (step) {
                    0, 1 -> step++
                    2 -> step = 3
                    else -> onDone(nickname.trim(), classId)
                }
            },
            enabled = step != 2 || classId != null || personalLoggedIn,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
                .height(52.dp),
        ) {
            Text(
                if (step == 3) "进入课表" else "继续",
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
            Text("你好，我是长工课表通。", fontSize = 15.sp, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Rise(shown >= 2) {
            Text(
                "让查看课表这件事，",
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
        Text("用之前，想跟你说三件事。", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(22.dp))
        HonestCard(Icons.Filled.WifiOff, "课表全存在你手机里", "没网也能看，我们不收集任何信息", shown >= 1)
        HonestCard(Icons.Filled.Campaign, "老师临时换课，APP 反应不过来", "记得以老师的通知为准", shown >= 2)
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

// ---------------- 第 3 屏: 认识一下(昵称 + 班级/个人课表并列选择) ----------------

@Composable
private fun MeetBody(
    dataset: Dataset,
    nickname: String,
    onNickname: (String) -> Unit,
    picked: Cls?,
    onPick: (String) -> Unit,
    personalLoggedIn: Boolean,
    studentName: String,
    onOpenLogin: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("最后，认识一下？", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = onNickname,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("姓名昵称都行，也可以不填", fontSize = 14.sp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(20.dp))

        // 课表方式并列选择: 班级课表 / 个人课表, 可任选或都选, 之后在「更多」里随时切换
        Text("用哪种课表？", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeCard(
                icon = Icons.Filled.School,
                title = "班级课表",
                sub = picked?.let { "${it.sznj}级 · ${it.zymc} · ${it.bjmc}" }
                    ?: "全班统一课表，点这选班级",
                selected = picked != null,
                onClick = { showPicker = true },
                modifier = Modifier.weight(1f),
            )
            ModeCard(
                icon = Icons.Filled.Badge,
                title = "个人课表",
                sub = if (personalLoggedIn) {
                    if (studentName.isNotBlank()) "已登录 · $studentName" else "已登录"
                } else {
                    "选课、重修都在，验证码登录"
                },
                selected = personalLoggedIn,
                onClick = onOpenLogin,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "两个都可以选，之后在「更多」里随时切换。",
            fontSize = 11.sp, color = cs.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))

        // 个人课表实现方式与信息安全说明 + 开源链接
        Surface(
            color = cs.onSurfaceVariant.copy(alpha = 0.06f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("个人课表怎么来的？", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "用你收到的短信验证码登录学校教务网站，只拉取你自己的课表。" +
                        "验证码用完即弃，登录凭据只存在本机内存，不上传任何第三方服务器，也不写入手机存储。" +
                        "这段代码已全部开源，欢迎随时审查。",
                    fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 19.sp,
                )
                Spacer(Modifier.height(8.dp))
                val ctx = LocalContext.current
                Text(
                    "查看源代码 →",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = cs.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp)).clickable { openUrl(ctx, OPEN_SOURCE_URL) }
                        .padding(vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showPicker) {
        ClassPickerSheet(
            dataset = dataset,
            onPick = { onPick(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

/** 课表方式选择卡: 选中态主色描边 + 右上角对勾 */
@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (selected) cs.primary.copy(alpha = 0.10f) else cs.surfaceColorAtElevation(2.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) cs.primary.copy(alpha = 0.55f) else cs.outlineVariant.copy(alpha = 0.45f),
        ),
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(cs.primary.copy(alpha = 0.10f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "已选择",
                        tint = cs.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
            Spacer(Modifier.height(3.dp))
            Text(
                sub,
                fontSize = 11.sp, color = cs.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
            )
        }
    }
}

/** 班级选择抽屉: 搜索框置顶(直接输班名/专业/学院), 下方按 年级→学院→专业→班级 逐级点选 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ClassPickerSheet(
    dataset: Dataset,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf<String?>(null) }
    var college by remember { mutableStateOf<String?>(null) }
    var major by remember { mutableStateOf<String?>(null) }

    val grades = remember(dataset) {
        dataset.classes.values.map { it.sznj }.filter { it.length == 4 }.distinct().sorted()
    }
    val colleges = remember(dataset, grade) {
        grade?.let { g -> dataset.classes.values.filter { it.sznj == g }.map { it.yxmc }.distinct().sorted() }
            ?: emptyList()
    }
    val majors = remember(dataset, grade, college) {
        if (grade == null || college == null) emptyList()
        else dataset.classes.values
            .filter { it.sznj == grade && it.yxmc == college }
            .map { it.zymc }.distinct().sorted()
    }
    val classes: List<Pair<String, Cls>> = remember(dataset, grade, college, major) {
        if (grade == null || college == null || major == null) emptyList()
        else dataset.classes.entries
            .filter { it.value.sznj == grade && it.value.yxmc == college && it.value.zymc == major }
            .sortedWith { a, b -> naturalCmp(a.value.bjmc, b.value.bjmc) }
            .map { it.key to it.value }
    }

    // 自绘 KbSheet: 弹层本体不吃手势, 列表滚动 100% 跟手 (修滑动抽搐)
    val sheetScroll = rememberScrollState()
    KbSheet(onDismiss = onDismiss) {
        val cs = MaterialTheme.colorScheme
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜班级 / 专业 / 学院，比如：计科3班", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.height(16.dp))

            val q = query.trim()
            if (q.isEmpty()) {
                GroupLabel("年级")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    grades.forEach { g ->
                        FilterChip(
                            selected = g == grade,
                            onClick = { grade = g; college = null; major = null },
                            label = { Text("${g}级", fontSize = 14.sp) },
                        )
                    }
                }

                AnimatedVisibility(colleges.isNotEmpty()) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        GroupLabel("学院")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            colleges.forEach { c ->
                                FilterChip(
                                    selected = c == college,
                                    onClick = { college = c; major = null },
                                    label = { Text(c, fontSize = 14.sp) },
                                )
                            }
                        }
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
                                    onClick = { major = m },
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
                                    selected = false,
                                    onClick = { onPick(id) },
                                    label = { Text(c.bjmc, fontSize = 14.sp) },
                                )
                            }
                        }
                    }
                }
            } else {
                // 搜索: 支持 "计科3班" / "25级计科3班" / "2025计科3班" / "软件工程" / "软件工程学院"
                val shortQ = q.replace("级", "")
                val longQ = q.replace(Regex("20(\\d\\d)"), "$1")
                val hits = dataset.classes.entries
                    .filter { (_, c) ->
                        c.bjmc.contains(shortQ) || (longQ != shortQ && c.bjmc.contains(longQ)) ||
                            c.zymc.contains(q) || c.yxmc.contains(q)
                    }
                    .sortedWith { a, b -> naturalCmp(a.value.bjmc, b.value.bjmc) }
                    .take(30)
                if (hits.isEmpty()) {
                    Text(
                        "没有找到，换个词试试",
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        fontSize = 14.sp,
                        color = cs.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    hits.forEach { (id, c) -> PickerHitRow(c) { onPick(id) } }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PickerHitRow(cls: Cls, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceColorAtElevation(2.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(vertical = 3.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cls.bjmc, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
                Text(
                    "${cls.yxmc} · ${cls.zymc}" + if (cls.sznj.length == 4) " · ${cls.sznj}级" else "",
                    fontSize = 11.sp, color = cs.onSurfaceVariant,
                )
            }
        }
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
                "欢迎你，${nickname.ifBlank { "同学" }}。",
                fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cs.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))
        Rise(shown >= 2) {
            Text("你的使用，是我们的荣幸。", fontSize = 16.sp, color = cs.onSurfaceVariant)
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
