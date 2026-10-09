package com.ccsut.kb

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.BuildVersion
import com.ccsut.kb.data.CasClient
import com.ccsut.kb.data.CheckResult
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.data.Manifest
import com.ccsut.kb.data.PersonalRepo
import com.ccsut.kb.data.Repo
import com.ccsut.kb.data.Updater
import com.ccsut.kb.data.UserEdits
import com.ccsut.kb.reminder.ReminderScheduler
import com.ccsut.kb.ui.ChooseClassScreen
import com.ccsut.kb.ui.CourseColors
import com.ccsut.kb.ui.CourseFormSheet
import com.ccsut.kb.ui.DevSheet
import com.ccsut.kb.ui.KbTheme
import com.ccsut.kb.ui.LoginScreen
import com.ccsut.kb.ui.MoreSheet
import com.ccsut.kb.ui.ScheduleScreen
import com.ccsut.kb.ui.UpdateDialog
import com.ccsut.kb.ui.WelcomeScreen
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.DebugLog
import com.ccsut.kb.util.KbClock
import com.ccsut.kb.util.Weeks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        KbClock.init(this)
        setContent { App() }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var themeMode by remember { mutableIntStateOf(Prefs.themeMode(ctx)) }
    var dataTick by remember { mutableIntStateOf(0) }
    var clsId by remember { mutableStateOf(Prefs.bjid(ctx)) }
    var ready by remember { mutableStateOf(Repo.dataset != null) }
    var screenChoose by remember { mutableStateOf(false) }

    // ---- v2.7 功能状态: 个人课表 ----
    // 当前生效课表 ("class"/"personal"); 个人课表用伪 bjid=PERSONAL, 渲染/编辑/小组件全链路复用
    var active by remember { mutableStateOf(Prefs.activeTimetable(ctx)) }
    var showLogin by remember { mutableStateOf(false) }
    var studentName by remember { mutableStateOf(Prefs.studentName(ctx)) }
    var lastSyncAt by remember { mutableLongStateOf(Prefs.lastSyncAt(ctx)) }
    // remember 不能写在 && 右侧(短路会跳过组合), 先单独取
    // 个人课表数据来自教务透传: 作息(节次)必须 ≥2 个大节且 jc 从 1 连续, 否则渲染层 SlotModel
    // 的 first{} / 空槽位表会直接崩 —— 校验不过按「无个人数据」处理, 回退班级课表/引导选班, 不崩不白屏
    val hasPersonal = remember(dataTick) {
        val jcs = if (PersonalRepo.hasData()) PersonalRepo.dataset()?.periods?.map { it.jc }?.sorted().orEmpty()
        else emptyList()
        jcs.size >= 4 && jcs == (1..jcs.size).toList()
    }
    val usePersonal = active == PersonalRepo.PERSONAL_KEY && hasPersonal
    // 全新安装首次启动: 欢迎引导 (老用户已选班级不弹)
    var showWelcome by remember { mutableStateOf(!Prefs.onboardingDone(ctx) && Prefs.bjid(ctx) == null) }
    var showMore by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Block?>(null) }
    var apkManifest by remember { mutableStateOf<Manifest?>(null) }
    var apkDownloading by remember { mutableStateOf(false) }
    var apkProgress by remember { mutableStateOf<Int?>(null) }   // 下载进度 0..100, null=未知

    // ---- 五版功能状态: 本地课表编辑 ----
    var addAt by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }   // 空格子加课 (day, jc, week)
    var editBlock by remember { mutableStateOf<Block?>(null) }        // 编辑课程
    var delBlock by remember { mutableStateOf<Block?>(null) }         // 删除确认

    // ---- 二版功能状态: 课程颜色 / 背景图 / 课前提醒 ----
    var colorMap by remember { mutableStateOf(Prefs.courseColors(ctx)) }
    var bgOn by remember { mutableStateOf(Prefs.bgOn(ctx)) }
    var bgTick by remember { mutableIntStateOf(0) }
    var bgAlpha by remember { mutableIntStateOf(Prefs.bgAlpha(ctx)) }
    var colorSource by remember { mutableIntStateOf(Prefs.colorSource(ctx)) }
    var bgSeed by remember { mutableIntStateOf(Prefs.bgSeed(ctx)) }
    var styleTheme by remember { mutableIntStateOf(Prefs.styleTheme(ctx)) }
    var reminderOn by remember { mutableStateOf(Prefs.reminderOn(ctx)) }
    var reminderLead by remember { mutableIntStateOf(Prefs.reminderLead(ctx)) }
    var nextReminderAt by remember { mutableLongStateOf(Prefs.nextReminderAt(ctx)) }
    var afterClassOn by remember { mutableStateOf(Prefs.afterClassOn(ctx)) }
    var earlyOn by remember { mutableStateOf(Prefs.earlyOn(ctx)) }

    // ---- 开发者模式 ----
    var devMode by remember { mutableStateOf(Prefs.devMode(ctx)) }
    var showDev by remember { mutableStateOf(false) }
    var dbgOffset by remember { mutableIntStateOf(Prefs.dbgOffsetDays(ctx)) }

    val bgFile = remember(bgOn, bgTick) {
        if (bgOn) BgStore.file(ctx).takeIf { it.exists() } else null
    }

    // 数据/班级/设置变化后的统一收尾: 刷班级快照 + 重排提醒 + 刷小组件
    fun resync() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ClassCache.save(ctx) }
                runCatching { if (Prefs.reminderOn(ctx)) ReminderScheduler.reschedule(ctx) }
                runCatching { com.ccsut.kb.widget.WidgetRenderer.updateAll(ctx) }
            }
            nextReminderAt = Prefs.nextReminderAt(ctx)
            DebugLog.log("app", "resync 完成 (offset=${KbClock.offsetDays})")
        }
    }

    /** 数据更新后重新套用本地修改覆盖层, 生成提示后缀 (保留/失效条数) */
    fun editSyncSuffix(): String {
        val bid = Prefs.bjid(ctx) ?: return ""
        val pristine = Repo.dataset?.classes?.get(bid)?.courses ?: return ""
        UserEdits.ensureLoaded(ctx)
        UserEdits.effective(bid, pristine)
        val kept = UserEdits.lastApplied
        val dropped = UserEdits.lastDropped
        return when {
            kept + dropped <= 0 -> ""
            dropped > 0 -> "；你的 ${dropped} 条本地修改因课表变动失效，其余已保留"
            else -> "；本地修改已全部保留"
        }
    }

    /** 本地课表编辑统一收尾: IO 里写覆盖层, 回主线程重组 + 刷快照/小组件/提醒 */
    fun applyEdit(op: suspend () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { op() } }
            dataTick++
            resync()
        }
    }

    // 统一切换当前课表 (班级↔个人): MoreSheet「当前课表」与顶栏徽标共用这一个入口
    val setTimetable: (Boolean) -> Unit = { toPersonal ->
        if (toPersonal && !PersonalRepo.hasData()) {
            showMore = false
            showLogin = true
        } else if (!toPersonal && Prefs.bjid(ctx) == null) {
            showMore = false
            screenChoose = true
        } else {
            val v = if (toPersonal) PersonalRepo.PERSONAL_KEY else "class"
            Prefs.setActiveTimetable(ctx, v)
            active = v
            showMore = false
            dataTick++
            resync()
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (Repo.dataset == null) Repo.load(ctx)
            PersonalRepo.load(ctx)
        }
        ready = true
        // 未选班级时默认引导选班; 但仅用个人课表的用户 (个人数据在且正生效) 直接进个人课表,
        // 不再每次冷启动都强制弹选班页
        if (clsId == null && !showWelcome &&
            !(PersonalRepo.hasData() && Prefs.activeTimetable(ctx) == PersonalRepo.PERSONAL_KEY)
        ) screenChoose = true
        var dataUpdatedTo = 0
        withContext(Dispatchers.IO) {
            runCatching {
                val r = Updater.check(ctx)
                if (r is CheckResult.DataUpdate && Updater.applyDataUpdate(ctx, r.m) != null) {
                    dataTick++
                    ClassCache.save(ctx)
                    runCatching { if (Prefs.reminderOn(ctx)) ReminderScheduler.reschedule(ctx) }
                    dataUpdatedTo = r.m.version
                }
                // 启动自动检查: 有新 APK → 弹更新页 (欢迎页/选班级时不弹, 进主界面后再弹)
                if (r is CheckResult.ApkUpdate) {
                    // 同手动检查: APK 更新不吞数据更新
                    if (r.m.version > (Repo.dataset?.version ?: 0) &&
                        withContext(Dispatchers.IO) {
                            runCatching { Updater.applyDataUpdate(ctx, r.m) }.getOrNull()
                        } != null
                    ) {
                        dataTick++
                        dataUpdatedTo = r.m.version
                    }
                    apkManifest = r.m
                }
            }
            resync()
        }
        if (dataUpdatedTo > 0) {
            Toast.makeText(ctx, "课表数据已自动更新到 v$dataUpdatedTo${editSyncSuffix()}", Toast.LENGTH_LONG).show()
        }
    }

    KbTheme(themeMode, colorSource, bgSeed, styleTheme) {
        if (!ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@KbTheme
        }

        // Repo.dataset / Prefs.bjid 非快照状态, 直接在局部变量里读会被 Surface 内容 lambda
        // 捕获旧值(局部重组不重算外层变量), 必须经 dataTick/clsId 在本作用域观察
        val dataset = remember(dataTick, usePersonal) {
            // 个人课表缓存损坏等极端情况回退班级数据, 避免白屏 (cls==null 分支会引导去选班级)
            if (usePersonal) PersonalRepo.dataset() ?: Repo.dataset else Repo.dataset
        } ?: return@KbTheme
        // 班级数据集: 选班页/引导班级抽屉/班级统计永远用它 —— 个人伪数据集 classes 为空, 传过去选班页就是空的
        val classDataset = remember(dataTick) { Repo.dataset } ?: dataset
        // 生效课表 = 原始数据 + 用户本地修改 (dataTick 变化即重算)
        val cls: Cls? = remember(dataTick, clsId, usePersonal) {
            if (usePersonal) PersonalRepo.effectiveCls() else clsId?.let { Repo.effectiveCls(it) }
        }
        // 本地编辑覆盖层的键: 班级课表用班级 id, 个人课表用伪 id
        val editTarget: String? = if (usePersonal) PersonalRepo.PERSONAL_BJID else clsId

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (showWelcome) {
                WelcomeScreen(
                    dataset = classDataset,
                    personalLoggedIn = PersonalRepo.hasData(),
                    studentName = studentName,
                    onOpenLogin = { showLogin = true },
                    onDone = { name, id ->
                        Prefs.setOnboardingDone(ctx, true)
                        Prefs.setNickname(ctx, name)
                        // 引导页课表方式并列选择: 只选个人课表时 id 为 null, 不落班级
                        if (id != null) {
                            Prefs.setBjid(ctx, id)
                            clsId = id
                        }
                        showWelcome = false
                        resync()
                    },
                    onSkip = {
                        Prefs.setOnboardingDone(ctx, true)
                        showWelcome = false
                        screenChoose = true
                    },
                )
            } else if (screenChoose) {
                BackHandler { screenChoose = false }
                ChooseClassScreen(
                    dataset = classDataset,
                    currentId = clsId,
                    onPick = { id ->
                        Prefs.setBjid(ctx, id)
                        clsId = id
                        if (active != "class") {
                            // 换班即看班: 从个人课表进来选完班, 直接回到班级课表
                            Prefs.setActiveTimetable(ctx, "class")
                            active = "class"
                        }
                        screenChoose = false
                        resync()
                    },
                    onBack = { screenChoose = false },
                )
            } else {
                if (cls == null) {
                    LaunchedEffect(dataTick) { screenChoose = true }
                    Box(Modifier.fillMaxSize())
                } else {
                    ScheduleScreen(
                        dataset = dataset,
                        cls = cls,
                        initialWeek = Weeks.currentWeek(dataset, KbClock.today()),
                        colorMap = colorMap,
                        bgFile = bgFile,
                        bgVersion = bgTick,
                        bgAlpha = bgAlpha / 100f,
                        dbgOffset = dbgOffset,
                        personalBadge = usePersonal,
                        onToggleTimetable = { setTimetable(!usePersonal) },
                        onOpenMore = { showMore = true },
                        onCourseClick = { detailCourse = it },
                        onAddAt = { d, j, w -> addAt = Triple(d, j, w) },
                        onMoveBlock = { b, d, j ->
                            val id = editTarget
                            if (id != null) applyEdit { UserEdits.moveBlock(ctx, id, b, d, j) }
                        },
                    )
                }
            }
        }

        // ---------- 课程详情 (含自定义颜色 / 还原 / 编辑 / 删除) ----------
        detailCourse?.let { block ->
            val seed = CourseColors.seedOf(block.course.kc, block.course.fx)
            val editKind = UserEdits.editOf(block.course.editId)?.kind
            com.ccsut.kb.ui.CourseDetailSheet(
                block = block,
                className = if (usePersonal) "个人课表" else clsId?.let { dataset.classes[it]?.bjmc } ?: "",
                customIdx = colorMap[seed],
                onPickColor = { idx ->
                    Prefs.setCourseColor(ctx, seed, idx)
                    colorMap = Prefs.courseColors(ctx)
                    runCatching { com.ccsut.kb.widget.WidgetRenderer.updateAll(ctx) }
                },
                onRevert = if (editKind == 1) {
                    {
                        detailCourse = null
                        val id = editTarget
                        if (id != null) applyEdit { UserEdits.revertBlock(ctx, id, block) }
                        Toast.makeText(ctx, "已还原为学校课表", Toast.LENGTH_SHORT).show()
                    }
                } else null,
                onEdit = {
                    editBlock = block
                    detailCourse = null
                },
                onDelete = {
                    delBlock = block
                    detailCourse = null
                },
                onDismiss = { detailCourse = null },
            )
        }

        // 目标时段占用判定: 同天 + 节次区间重叠 + 周次有交集 → 拒绝保存
        // (周次错开的单双周同槽是合法安排, 放行); selfEditId 供编辑时排除自身各行
        val slotConflict: (UserEdits.Form, String?) -> Boolean = { f, selfEditId ->
            cls?.courses.orEmpty().any { c ->
                (c.editId != null && c.editId == selfEditId) ||
                    c.day == f.day &&
                    c.jc < f.startJc + f.span && f.startJc < c.jc + c.djs &&
                    UserEdits.rangesOverlap(f.ranges, c.ranges)
            }
        }

        // ---------- 添加课程 (点击课表空白格) ----------
        // 默认勾选点击时正在查看的那一周 (而不是全学期)
        addAt?.let { (d, j, w) ->
            val wk = w.coerceIn(1, dataset.weeks)
            val preJc = j.coerceIn(1, dataset.periods.size)
            CourseFormSheet(
                title = "添加课程",
                weeks = dataset.weeks,
                maxJc = dataset.periods.size,
                initial = UserEdits.Form(
                    kc = "", teacher = "", room = "",
                    day = d, startJc = preJc,
                    span = 2.coerceAtMost(dataset.periods.size - preJc + 1),
                    zc = "$wk", ranges = listOf(wk..wk),
                ),
                showDelete = false,
                onSave = { f ->
                    if (slotConflict(f, null)) {
                        Toast.makeText(ctx, "该时段已有课程（周次有重叠），换天/节次或周次试试", Toast.LENGTH_SHORT).show()
                        return@CourseFormSheet
                    }
                    addAt = null
                    val id = editTarget
                    if (id != null) applyEdit { UserEdits.add(ctx, id, f) }
                },
                onDismiss = { addAt = null },
            )
        }

        // ---------- 编辑课程 ----------
        editBlock?.let { b ->
            CourseFormSheet(
                title = "编辑课程",
                weeks = dataset.weeks,
                maxJc = dataset.periods.size,
                initial = UserEdits.Form(
                    kc = b.course.kc, teacher = b.course.teacher, room = b.course.room,
                    day = b.course.day, startJc = b.startJc, span = b.span,
                    zc = b.course.zc, ranges = b.course.ranges,
                ),
                showDelete = true,
                onSave = { f ->
                    if (slotConflict(f, b.course.editId)) {
                        Toast.makeText(ctx, "该时段已有课程（周次有重叠），换天/节次或周次试试", Toast.LENGTH_SHORT).show()
                        return@CourseFormSheet
                    }
                    editBlock = null
                    val id = editTarget
                    if (id != null) applyEdit { UserEdits.saveBlock(ctx, id, b, f) }
                },
                onDelete = {
                    editBlock = null
                    delBlock = b
                },
                onDismiss = { editBlock = null },
            )
        }

        // ---------- 删除课程确认 ----------
        delBlock?.let { b ->
            AlertDialog(
                onDismissRequest = { delBlock = null },
                title = { Text("删除课程") },
                text = { Text("确定从你的课表移除「${b.course.kc}」吗？只影响你自己，不影响其他同学。") },
                confirmButton = {
                    TextButton({
                        val id = editTarget
                        if (id != null) applyEdit { UserEdits.removeBlock(ctx, id, b) }
                        delBlock = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton({ delBlock = null }) { Text("取消") } },
            )
        }

        // ---------- 教务登录 (登录成功后默认切到个人课表) ----------
        if (showLogin) {
            LoginScreen(
                initialAccount = Prefs.casAccount(ctx),
                onDismiss = { showLogin = false },
                onSuccess = { name ->
                    showLogin = false
                    studentName = name
                    lastSyncAt = Prefs.lastSyncAt(ctx)
                    Prefs.setActiveTimetable(ctx, PersonalRepo.PERSONAL_KEY)
                    active = PersonalRepo.PERSONAL_KEY
                    dataTick++
                    resync()
                    Toast.makeText(ctx, "已切换到${name}的个人课表", Toast.LENGTH_SHORT).show()
                },
            )
        }

        // ---------- 更多面板 ----------
        if (showMore) {
            MoreSheet(
                dataset = classDataset,
                cls = cls,
                fromUpdate = Repo.fromUpdate,
                appVersion = BuildVersion.NAME,
                themeMode = themeMode,
                bgOn = bgOn,
                bgAlpha = bgAlpha,
                colorSource = colorSource,
                bgSeed = bgSeed,
                styleTheme = styleTheme,
                reminderOn = reminderOn,
                reminderLead = reminderLead,
                nextReminderAt = nextReminderAt,
                afterClassOn = afterClassOn,
                earlyOn = earlyOn,
                devMode = devMode,
                personalActive = usePersonal,
                personalName = studentName,
                personalSyncedAt = lastSyncAt,
                onThemeMode = {
                    Prefs.setThemeMode(ctx, it)
                    themeMode = it
                },
                onBgSaved = {
                    bgOn = true
                    bgTick++
                    // 设置背景图后: 全局配色(含桌面小组件)自动跟随背景图取色, 不再跟随壁纸
                    scope.launch {
                        val seed = withContext(Dispatchers.IO) {
                            runCatching { BgStore.extractSeed(ctx) }.getOrDefault(0)
                        }
                        if (seed != 0) {
                            Prefs.setBgSeed(ctx, seed)
                            Prefs.setColorSource(ctx, 1)
                            bgSeed = seed
                            colorSource = 1
                            runCatching { com.ccsut.kb.widget.WidgetRenderer.updateAll(ctx) }
                            DebugLog.log("app", "背景取色 seed=#${Integer.toHexString(seed)}")
                        }
                    }
                },
                onRemoveBg = {
                    scope.launch {
                        withContext(Dispatchers.IO) { BgStore.delete(ctx) }
                        bgOn = false
                        bgTick++
                        Prefs.setColorSource(ctx, 0)
                        colorSource = 0
                        runCatching { com.ccsut.kb.widget.WidgetRenderer.updateAll(ctx) }
                    }
                },
                onBgAlpha = {
                    Prefs.setBgAlpha(ctx, it)
                    bgAlpha = it
                },
                onColorSource = { v ->
                    Prefs.setColorSource(ctx, v)
                    colorSource = v
                    runCatching { com.ccsut.kb.widget.WidgetRenderer.updateAll(ctx) }
                },
                onStyleTheme = { v ->
                    Prefs.setStyleTheme(ctx, v)
                    styleTheme = v
                },
                onReminderToggle = { on ->
                    Prefs.setReminderOn(ctx, on)
                    reminderOn = on
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { if (on) ReminderScheduler.reschedule(ctx) else ReminderScheduler.cancel(ctx) }
                        }
                        nextReminderAt = Prefs.nextReminderAt(ctx)
                    }
                },
                onLeadChange = { v ->
                    Prefs.setReminderLead(ctx, v)
                    reminderLead = v
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { ReminderScheduler.reschedule(ctx) }
                        }
                        nextReminderAt = Prefs.nextReminderAt(ctx)
                    }
                },
                onAfterClassChange = { v ->
                    Prefs.setAfterClassOn(ctx, v)
                    afterClassOn = v
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { ReminderScheduler.reschedule(ctx) }
                        }
                        nextReminderAt = Prefs.nextReminderAt(ctx)
                    }
                },
                onEarlyChange = { v ->
                    Prefs.setEarlyOn(ctx, v)
                    earlyOn = v
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching { ReminderScheduler.reschedule(ctx) }
                        }
                        nextReminderAt = Prefs.nextReminderAt(ctx)
                    }
                },
                onSelectClass = {
                    showMore = false
                    screenChoose = true
                },
                onOpenPersonalLogin = {
                    showMore = false
                    showLogin = true
                },
                onSetTimetable = setTimetable,
                onRefreshPersonal = {
                    // 教务会话短时效: 刷新 = 重新验证码登录, 登录页里有说明
                    showMore = false
                    showLogin = true
                },
                onLogoutPersonal = {
                    showMore = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            CasClient.clearCookies()
                            PersonalRepo.clear(ctx)
                            Prefs.setActiveTimetable(ctx, "class")
                            Prefs.setStudentName(ctx, "")
                            Prefs.setLastSyncAt(ctx, 0L)
                            runCatching { ClassCache.save(ctx) }
                            runCatching { if (Prefs.reminderOn(ctx)) ReminderScheduler.reschedule(ctx) }
                            runCatching { com.ccsut.kb.widget.WidgetRenderer.updateAll(ctx) }
                        }
                        // 退出教务且没有班级可回退时, 直接引导选班
                        if (Prefs.bjid(ctx) == null) screenChoose = true
                        active = "class"
                        studentName = ""
                        lastSyncAt = 0L
                        dataTick++
                        DebugLog.log("personal", "已退出教务账号")
                        Toast.makeText(ctx, "已退出教务账号", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenDev = {
                    showMore = false
                    showDev = true
                },
                onDevModeChange = { v ->
                    Prefs.setDevMode(ctx, v)
                    devMode = v
                    DebugLog.log("dev", "开发者模式 → $v")
                },
                onDismiss = { showMore = false },
                onCheckUpdate = { onResult ->
                    scope.launch {
                        onResult("正在检查…")
                        val r = withContext(Dispatchers.IO) { runCatching { Updater.check(ctx) } }
                            .getOrElse { CheckResult.Error(it.message ?: "未知错误") }
                        when (r) {
                            is CheckResult.UpToDate -> onResult("已是最新版本 v${dataset.version}")
                            is CheckResult.DataUpdate -> {
                                onResult("发现数据 v${r.m.version}，下载中…")
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching { Updater.applyDataUpdate(ctx, r.m) }.getOrNull()
                                }
                                if (ok != null) {
                                    dataTick++
                                    resync()
                                    onResult("已更新到数据 v${r.m.version}${editSyncSuffix()}")
                                } else onResult("下载或校验失败，请稍后再试")
                            }
                            is CheckResult.ApkUpdate -> {
                                // 数据与 APK 独立托管: APK 更新期间课表数据也要顺带热更,
                                // 否则用户推迟装 APK 的整段时间数据一直落后
                                if (r.m.version > (Repo.dataset?.version ?: 0)) {
                                    val ok = withContext(Dispatchers.IO) {
                                        runCatching { Updater.applyDataUpdate(ctx, r.m) }.getOrNull()
                                    }
                                    if (ok != null) {
                                        dataTick++
                                        resync()
                                        onResult("课表数据已更新到 v${r.m.version}${editSyncSuffix()}")
                                    }
                                }
                                onResult(null)
                                showMore = false
                                apkManifest = r.m
                            }
                            is CheckResult.Error -> onResult(r.msg)
                            CheckResult.Disabled -> onResult("未配置更新地址")
                        }
                    }
                },
                onClearUpdate = {
                    scope.launch {
                        withContext(Dispatchers.IO) { Repo.clearUpdate(ctx) }
                        dataTick++
                        resync()
                        Toast.makeText(ctx, "已恢复内置数据", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        // ---------- 开发者模式 ----------
        if (showDev) {
            DevSheet(
                dataset = classDataset,
                offset = dbgOffset,
                nextReminderAt = nextReminderAt,
                personalActive = usePersonal,
                personalName = studentName,
                onOffsetChange = { v ->
                    Prefs.setDbgOffsetDays(ctx, v)
                    dbgOffset = v
                    KbClock.offsetDays = v
                    DebugLog.log("dev", "时间旅行 → $v 天 (今天=${KbClock.today()})")
                    resync()
                },
                onRebuildCache = {
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { ClassCache.save(ctx) } }
                        DebugLog.log("dev", "手动重建快照")
                    }
                },
                onRefreshWidget = {
                    com.ccsut.kb.widget.WidgetRenderer.updateAll(ctx)
                    DebugLog.log("dev", "手动刷新小组件")
                },
                onReschedule = {
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { ReminderScheduler.reschedule(ctx) } }
                        nextReminderAt = Prefs.nextReminderAt(ctx)
                    }
                },
                onTestNotification = {
                    val ok = ReminderScheduler.notifyTest(ctx, "测试通知", "渠道 / 图标 / 权限都正常 ✅")
                    if (!ok) Toast.makeText(ctx, "没有通知权限，先到提醒里打开开关", Toast.LENGTH_SHORT).show()
                },
                onTestAlarm = {
                    ReminderScheduler.scheduleTestAlarm(ctx)
                    Toast.makeText(ctx, "已排测试闹钟，10 秒后应弹出通知", Toast.LENGTH_SHORT).show()
                },
                onReloadData = {
                    scope.launch {
                        withContext(Dispatchers.IO) { Repo.load(ctx) }
                        dataTick++
                        resync()
                    }
                },
                onToggleTimetable = { setTimetable(!usePersonal) },
                onReplayOnboarding = {
                    showDev = false
                    showWelcome = true   // 直接回到向导第一步, 设置与数据全部保留
                },
                onMockApkUpdate = {
                    apkManifest = Manifest(
                        version = dataset.version,
                        xnxq = dataset.xnxq,
                        file = "",
                        sha256 = "",
                        bytes = 0,
                        generatedAt = "",
                        apkVersionCode = BuildVersion.CODE + 1,
                        apkFile = "mock.apk",
                        apkSha256 = null,
                        apkVersionName = "9.9.9",
                        apkBytes = 13_527_019,
                        apkNotes = listOf(
                            "预览更新弹窗效果（开发者模式模拟，不会真下载）",
                            "新增本地课表编辑：点空格加课、长按拖拽换课",
                            "修复深色模式下弹层文字看不清的问题",
                        ),
                    )
                },
                onResetPersonal = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            Prefs.clearPersonal(ctx)
                            runCatching { BgStore.delete(ctx) }
                        }
                        colorMap = emptyMap()
                        bgOn = false
                        bgTick++
                        themeMode = 0
                        colorSource = 0
                        bgSeed = 0
                        styleTheme = 0
                        DebugLog.log("dev", "已重置个性化设置")
                        Toast.makeText(ctx, "个性化设置已重置", Toast.LENGTH_SHORT).show()
                    }
                },
                onFullReset = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching { ReminderScheduler.cancel(ctx) }
                                Prefs.clearAll(ctx)
                                Prefs.setDevMode(ctx, true)
                                UserEdits.clear(ctx)
                                ctx.deleteFile("dataset.json")
                                ctx.deleteFile("class_cache.json")
                                runCatching { PersonalRepo.clear(ctx) }
                                runCatching { BgStore.delete(ctx) }
                                Repo.load(ctx)
                            }
                            dataTick++
                            clsId = null
                            active = "class"
                            studentName = ""
                            lastSyncAt = 0L
                        colorMap = emptyMap()
                        bgOn = false
                        bgTick++
                        themeMode = 0
                        colorSource = 0
                        bgSeed = 0
                        styleTheme = 0
                        reminderOn = false
                        nextReminderAt = 0
                        dbgOffset = 0
                        KbClock.offsetDays = 0
                        showDev = false
                        screenChoose = true
                        DebugLog.log("dev", "完全重置完成, 回到选班级")
                    }
                },
                onDismiss = { showDev = false },
            )
        }

        // ---------- APK 自更新 (启动自动检查 / 更多里手动检查 / 开发者模式模拟) ----------
        // 欢迎页与选班级阶段不弹, 进主界面后自动补弹
        apkManifest?.takeIf { !showWelcome && !screenChoose }?.let { m ->
            UpdateDialog(
                newVersion = m.apkVersionName ?: m.apkVersionCode?.toString() ?: BuildVersion.NAME,
                sizeBytes = m.apkBytes,
                notes = m.apkNotes,
                downloading = apkDownloading,
                progress = apkProgress,
                onUpdate = {
                    if (!apkDownloading) {
                        apkDownloading = true
                        apkProgress = null
                        scope.launch {
                            val f: File? = withContext(Dispatchers.IO) {
                                runCatching {
                                    Updater.downloadApk(ctx, m) { done, total ->
                                        if (total > 0) apkProgress = ((done * 100) / total).toInt()
                                    }
                                }.getOrNull()
                            }
                            apkDownloading = false
                            apkProgress = null
                            if (f != null) {
                                apkManifest = null
                                installApk(ctx, f)
                            } else {
                                // 弹窗保持打开, 用户可直接重试
                                Toast.makeText(ctx, "下载失败，请检查网络后再试", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onDismiss = { apkManifest = null },
            )
        }
    }
}

private fun installApk(ctx: Context, file: File) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
        .onFailure { Toast.makeText(ctx, "无法启动安装: ${it.message}", Toast.LENGTH_SHORT).show() }
}
