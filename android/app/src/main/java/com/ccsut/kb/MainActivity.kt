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
import com.ccsut.kb.data.CheckResult
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.data.Manifest
import com.ccsut.kb.data.Repo
import com.ccsut.kb.data.Updater
import com.ccsut.kb.reminder.ReminderScheduler
import com.ccsut.kb.ui.ChooseClassScreen
import com.ccsut.kb.ui.CourseColors
import com.ccsut.kb.ui.DevSheet
import com.ccsut.kb.ui.KbTheme
import com.ccsut.kb.ui.MoreSheet
import com.ccsut.kb.ui.ScheduleScreen
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
    var showMore by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Block?>(null) }
    var apkManifest by remember { mutableStateOf<Manifest?>(null) }
    var apkDownloading by remember { mutableStateOf(false) }

    // ---- 二版功能状态: 课程颜色 / 背景图 / 课前提醒 ----
    var colorMap by remember { mutableStateOf(Prefs.courseColors(ctx)) }
    var bgOn by remember { mutableStateOf(Prefs.bgOn(ctx)) }
    var bgTick by remember { mutableIntStateOf(0) }
    var bgAlpha by remember { mutableIntStateOf(Prefs.bgAlpha(ctx)) }
    var colorSource by remember { mutableIntStateOf(Prefs.colorSource(ctx)) }
    var bgSeed by remember { mutableIntStateOf(Prefs.bgSeed(ctx)) }
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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { if (Repo.dataset == null) Repo.load(ctx) }
        ready = true
        if (clsId == null) screenChoose = true
        withContext(Dispatchers.IO) {
            runCatching {
                val r = Updater.check(ctx)
                if (r is CheckResult.DataUpdate && Updater.applyDataUpdate(ctx, r.m) != null) {
                    dataTick++
                    ClassCache.save(ctx)
                    runCatching { if (Prefs.reminderOn(ctx)) ReminderScheduler.reschedule(ctx) }
                    Toast.makeText(ctx, "课表数据已自动更新到 v${r.m.version}", Toast.LENGTH_SHORT).show()
                }
            }
            resync()
        }
    }

    KbTheme(themeMode, colorSource, bgSeed) {
        if (!ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@KbTheme
        }

        // Repo.dataset / Prefs.bjid 非快照状态, 直接在局部变量里读会被 Surface 内容 lambda
        // 捕获旧值(局部重组不重算外层变量), 必须经 dataTick/clsId 在本作用域观察
        val dataset = remember(dataTick) { Repo.dataset } ?: return@KbTheme
        val cls: Cls? = remember(dataTick, clsId) { clsId?.let { dataset.classes[it] } }

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (screenChoose) {
                BackHandler { screenChoose = false }
                ChooseClassScreen(
                    dataset = dataset,
                    currentId = clsId,
                    onPick = { id ->
                        Prefs.setBjid(ctx, id)
                        clsId = id
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
                        onOpenMore = { showMore = true },
                        onCourseClick = { detailCourse = it },
                    )
                }
            }
        }

        // ---------- 课程详情 (含自定义颜色) ----------
        detailCourse?.let { block ->
            val seed = CourseColors.seedOf(block.course.kc, block.course.fx)
            com.ccsut.kb.ui.CourseDetailSheet(
                block = block,
                className = clsId?.let { dataset.classes[it]?.bjmc } ?: "",
                customIdx = colorMap[seed],
                onPickColor = { idx ->
                    Prefs.setCourseColor(ctx, seed, idx)
                    colorMap = Prefs.courseColors(ctx)
                    runCatching { com.ccsut.kb.widget.WidgetRenderer.updateAll(ctx) }
                },
                onDismiss = { detailCourse = null },
            )
        }

        // ---------- 更多面板 ----------
        if (showMore) {
            MoreSheet(
                dataset = dataset,
                cls = cls,
                fromUpdate = Repo.fromUpdate,
                appVersion = BuildVersion.NAME,
                themeMode = themeMode,
                bgOn = bgOn,
                bgAlpha = bgAlpha,
                colorSource = colorSource,
                bgSeed = bgSeed,
                reminderOn = reminderOn,
                reminderLead = reminderLead,
                nextReminderAt = nextReminderAt,
                afterClassOn = afterClassOn,
                earlyOn = earlyOn,
                devMode = devMode,
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
                                    onResult("已更新到数据 v${r.m.version}")
                                } else onResult("下载或校验失败，请稍后再试")
                            }
                            is CheckResult.ApkUpdate -> {
                                onResult(null)
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
                dataset = dataset,
                offset = dbgOffset,
                nextReminderAt = nextReminderAt,
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
                            ctx.deleteFile("dataset.json")
                            ctx.deleteFile("class_cache.json")
                            runCatching { BgStore.delete(ctx) }
                            Repo.load(ctx)
                        }
                        dataTick++
                        clsId = null
                        colorMap = emptyMap()
                        bgOn = false
                        bgTick++
                        themeMode = 0
                        colorSource = 0
                        bgSeed = 0
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

        // ---------- APK 自更新 ----------
        apkManifest?.let { m ->
            AlertDialog(
                onDismissRequest = { apkManifest = null },
                title = { Text("发现新版本") },
                text = { Text("APP 有新版本 v${m.apkVersionCode}（当前 v${BuildVersion.CODE}），下载并安装吗？") },
                confirmButton = {
                    Button(
                        enabled = !apkDownloading,
                        onClick = {
                            apkDownloading = true
                            scope.launch {
                                val f: File? = withContext(Dispatchers.IO) {
                                    runCatching { Updater.downloadApk(ctx, m) }.getOrNull()
                                }
                                apkDownloading = false
                                apkManifest = null
                                if (f != null) installApk(ctx, f)
                                else Toast.makeText(ctx, "下载失败，请稍后再试", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) { Text(if (apkDownloading) "下载中…" else "下载安装") }
                },
                dismissButton = { TextButton({ apkManifest = null }) { Text("以后再说") } },
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
