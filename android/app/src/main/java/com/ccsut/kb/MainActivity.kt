package com.ccsut.kb

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.BuildVersion
import com.ccsut.kb.data.CasClient
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Channels
import com.ccsut.kb.data.ClassCache
import com.ccsut.kb.data.Mirrors
import com.ccsut.kb.data.NoteSection
import com.ccsut.kb.data.PersonalRepo
import com.ccsut.kb.data.ReleaseInfo
import com.ccsut.kb.data.Releases
import com.ccsut.kb.data.RemoteNotice
import com.ccsut.kb.data.RemoteNotices
import com.ccsut.kb.data.Repo
import com.ccsut.kb.data.UpdateChannels
import com.ccsut.kb.data.Updater
import com.ccsut.kb.data.UserEdits
import com.ccsut.kb.reminder.ReminderScheduler
import com.ccsut.kb.ui.AboutScreen
import com.ccsut.kb.ui.ChooseClassScreen
import com.ccsut.kb.ui.CourseColors
import com.ccsut.kb.ui.CourseFormSheet
import com.ccsut.kb.ui.DemoShowcaseSheet
import com.ccsut.kb.ui.DemoStep
import com.ccsut.kb.ui.DemoSteps
import com.ccsut.kb.ui.DevSheet
import com.ccsut.kb.ui.EditOpResult
import com.ccsut.kb.ui.EditSession
import com.ccsut.kb.ui.Ghost
import com.ccsut.kb.ui.KbTheme
import com.ccsut.kb.ui.LoginScreen
import com.ccsut.kb.ui.MoreSheet
import com.ccsut.kb.ui.NoticeDialog
import com.ccsut.kb.ui.ScheduleScreen
import com.ccsut.kb.ui.UndoEntry
import com.ccsut.kb.ui.UpdateScreen
import com.ccsut.kb.ui.WelcomeScreen
import com.ccsut.kb.util.Block
import com.ccsut.kb.util.DebugLog
import com.ccsut.kb.util.KbClock
import com.ccsut.kb.util.Weeks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    /** 更新通知点按的信号: onCreate extras 与 onNewIntent 都会 +1, 组合层据此直达软件更新页 */
    var openUpdateTick by mutableStateOf(0)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        KbClock.init(this)
        if (intent.getBooleanExtra(UpdateNotifier.EXTRA_OPEN_UPDATE, false)) openUpdateTick++
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(UpdateNotifier.EXTRA_OPEN_UPDATE, false)) openUpdateTick++
    }
}

/** 13+ 通知权限: 启动/引导完成/开提醒开关时都会调用, 无权限时通知会被静默丢弃 */
private fun ensureNotifPermission(activity: Activity) {
    if (Build.VERSION.SDK_INT < 33) return
    if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    ) return
    activity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2001)
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

    // ---- 个人课表 ----
    // 当前生效课表 ("class"/"personal"); 个人课表用伪 bjid=PERSONAL, 渲染/编辑/小组件全链路复用
    var active by remember { mutableStateOf(Prefs.activeTimetable(ctx)) }
    var showLogin by remember { mutableStateOf(false) }
    var studentName by remember { mutableStateOf(Prefs.studentName(ctx)) }
    var lastSyncAt by remember { mutableLongStateOf(Prefs.lastSyncAt(ctx)) }
    // remember 不能写在 && 右侧(短路会跳过组合), 先单独取
    // 个人课表数据要求作息 ≥2 个大节且 jc 从 1 连续, 否则渲染层会崩, 校验不过按「无个人数据」处理
    // ready 必须作为 key: 首次组合时 PersonalRepo 尚未加载完, 只挂 dataTick 会永远回退班级课表
    val hasPersonal = remember(dataTick, ready) {
        val jcs = if (PersonalRepo.hasData()) PersonalRepo.dataset()?.periods?.map { it.jc }?.sorted().orEmpty()
        else emptyList()
        jcs.size >= 4 && jcs == (1..jcs.size).toList()
    }
    val usePersonal = active == PersonalRepo.PERSONAL_KEY && hasPersonal
    // 全新安装首次启动: 欢迎引导 (老用户已选班级不弹)
    var showWelcome by remember { mutableStateOf(!Prefs.onboardingDone(ctx) && Prefs.bjid(ctx) == null) }
    var showMore by remember { mutableStateOf(false) }
    var detailCourse by remember { mutableStateOf<Block?>(null) }

    // ---- 软件更新页: 版本历史 / 切换软件源 / 下载安装 ----
    var showUpdate by remember { mutableStateOf(false) }
    // 关于页: 品牌块 / 软件更新 / 课表数据 / 社区 (更多面板入口)
    var showAbout by remember { mutableStateOf(false) }
    var updateSource by remember { mutableIntStateOf(UpdateChannels.source(ctx)) }
    var updateChannel by remember { mutableIntStateOf(Channels.current(ctx)) }
    var releases by remember { mutableStateOf<List<ReleaseInfo>?>(null) }
    var releasesLoading by remember { mutableStateOf(false) }
    var releasesError by remember { mutableStateOf<String?>(null) }
    var latestKnown by remember { mutableStateOf<ReleaseInfo?>(null) }   // 启动/刷新检查发现的更高版本 (入口徽标)
    var autoUpdateOn by remember { mutableStateOf(Prefs.autoUpdateOn(ctx)) }   // 设置页「更新提醒」: 发现新版时提醒
    var dialogQueue by remember { mutableStateOf<List<PendingDialog>>(emptyList()) }  // 弹窗队列: 一次只弹一个, 处理完自动弹下一个
    var handledDialogKeys by remember { mutableStateOf(setOf<String>()) }     // 本会话已处理过的弹窗键 (防前台复查时复活)
    var downloadingVer by remember { mutableStateOf<String?>(null) }     // 正在下载的版本名, null=空闲
    var apkProgress by remember { mutableStateOf<Int?>(null) }           // 下载进度 0..100, null=未知
    var cachedApks by remember { mutableStateOf<Set<Pair<String, String>>>(emptySet()) } // 下载目录里已就绪的 (通道, 版本名), 卡片直接亮「立即安装」

    // ---- 本地课表编辑 ----
    var addAt by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }   // 空格子加课 (day, jc, week)
    var editBlock by remember { mutableStateOf<Block?>(null) }        // 编辑课程
    var delBlock by remember { mutableStateOf<Block?>(null) }         // 删除确认

    // ---- 课程颜色 / 背景图 / 课前提醒 ----
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
    var alarmMode by remember { mutableStateOf(Prefs.alarmMode(ctx)) }

    // ---- 编辑会话: 回退栈 + 虚影 (内存态; 换班级/重置修改即整场作废) ----
    var undoStack by remember { mutableStateOf(listOf<UndoEntry>()) }
    var ghosts by remember { mutableStateOf(listOf<Ghost>()) }
    var sessionBefore by remember { mutableStateOf<List<com.ccsut.kb.data.UserEdits.Edit>?>(null) }
    var sessionBjid by remember { mutableStateOf<String?>(null) }
    var lastOpStamp by remember { mutableIntStateOf(0) }
    var undoVisible by remember { mutableStateOf(false) }
    // 功能演示: 非空时弹出演示弹层 (首次编辑触发 / 进表整组 / 更多面板手动入口)
    var showDemo by remember { mutableStateOf<List<DemoStep>?>(null) }

    // ---- 开发者模式 ----
    var devMode by remember { mutableStateOf(Prefs.devMode(ctx)) }
    var showDev by remember { mutableStateOf(false) }
    var dbgOffset by remember { mutableIntStateOf(Prefs.dbgOffsetDays(ctx)) }

    val bgFile = remember(bgOn, bgTick) {
        if (bgOn) BgStore.file(ctx).takeIf { it.exists() } else null
    }

    // 主题/外观相关状态的一键还原 (重置个性化 / 完全重置共用, 勿在单处散写漏项)
    fun resetVisualState() {
        colorMap = emptyMap()
        bgOn = false
        bgTick++
        themeMode = 0
        colorSource = 0
        bgSeed = 0
        styleTheme = 0
    }

    // 数据/班级/设置变化后的统一收尾: 刷班级快照 + 重排提醒 + 刷小组件
    fun resync() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ClassCache.save(ctx) }
                runCatching { ReminderScheduler.reschedule(ctx) }   // reschedule 自查三开关, 全关时内部 cancel
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

    /** 短震动反馈 (回退按钮长按还原全部用) */
    fun vibrateTap() {
        runCatching {
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
            v.vibrate(android.os.VibrationEffect.createOneShot(60, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
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

    // ---- 软件更新页: 版本列表加载 / 数据热更复查 / APK 下载安装 ----

    /** 版本列表就绪后扫一遍下载目录: 标出哪些通道的哪些版本 APK 已在手, 卡片按钮直接变「立即安装」 */
    fun refreshCachedApks(list: List<ReleaseInfo>) {
        scope.launch {
            val names = withContext(Dispatchers.IO) {
                list.filter { it.fileUrl != null }.mapNotNull { r ->
                    (r.channel to r.versionName).takeIf {
                        Updater.cachedApk(ctx, r.channel, r.versionName, r.sha256) != null
                    }
                }.toSet()
            }
            cachedApks = names
        }
    }

    fun loadReleases() {
        scope.launch {
            releasesLoading = true
            releasesError = null
            val src = updateSource
            val ch = updateChannel
            val r = withContext(Dispatchers.IO) { runCatching { Releases.fetchAll(ctx, src, ch) } }
            releasesLoading = false
            r.fold(
                onSuccess = { list ->
                    releases = list
                    // 列表里的最高版本同时喂给「更多」入口的「发现新版本」徽标
                    latestKnown = list.firstOrNull { Releases.relation(it) > 0 }
                    refreshCachedApks(list)
                },
                onFailure = { releasesError = it.message ?: "获取版本信息失败" },
            )
        }
    }

    /** 更新页顶部刷新: 数据热更也顺带复查一遍 (数据与 APK 独立版本线), 然后重拉版本列表 */
    fun refreshUpdatePage() {
        scope.launch {
            val dataMsg = withContext(Dispatchers.IO) {
                runCatching {
                    val m = Updater.fetchManifest(ctx)
                    if (m != null && m.version > (Repo.dataset?.version ?: 0) &&
                        Updater.applyDataUpdate(ctx, m) != null
                    ) {
                        dataTick++
                        "课表数据已更新到 v${m.version}${editSyncSuffix()}"
                    } else null
                }.getOrNull()
            }
            resync()
            if (dataMsg != null) Toast.makeText(ctx, dataMsg, Toast.LENGTH_LONG).show()
            loadReleases()
        }
    }

    /** 下载 APK 并在落盘后自动拉起安装; onReady 在安装意图发出前回调 (更新弹窗用它收掉自己) */
    fun startDownload(r: ReleaseInfo, onReady: (() -> Unit)? = null) {
        val url = r.fileUrl ?: return
        if (downloadingVer != null) return   // 同一时间只允许一个下载任务
        scope.launch {
            // 下载前先查下载目录: 这个通道的这个版本 APK 已经在手就直接安装, 不再重复下载
            val cached = withContext(Dispatchers.IO) { Updater.cachedApk(ctx, r.channel, r.versionName, r.sha256) }
            if (cached != null) {
                onReady?.invoke()
                installApk(ctx, cached)
                return@launch
            }
            if (downloadingVer != null) return@launch   // 缓存未命中时的竞态兜底: 另一个下载已开跑
            downloadingVer = r.versionName
            apkProgress = null
            val f: File? = withContext(Dispatchers.IO) {
                runCatching {
                    Updater.downloadApk(ctx, r.channel, r.versionName, url, r.sha256) { done, total ->
                        if (total > 0) apkProgress = ((done * 100) / total).toInt()
                    }
                }.getOrNull()
            }
            downloadingVer = null
            apkProgress = null
            if (f != null) {
                // 装包留在下载目录: 用户取消安装或没给授权时, 重开页面直接「立即安装」
                cachedApks = cachedApks + (r.channel to r.versionName)
                onReady?.invoke()
                installApk(ctx, f)
            } else {
                // 镜像可能中途挂了: 清掉缓存让下次更新重新测速
                if (UpdateChannels.source(ctx) == UpdateChannels.SOURCE_MIRROR) Mirrors.invalidate(ctx)
                Toast.makeText(ctx, "下载失败，请检查网络后再试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- 更新提醒 + 远程通知检查: 冷启动与每次回前台共用 ----
    val checkInFlight = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var lastCheckAt by remember { mutableLongStateOf(0L) }

    /** 拉取「最新版本」与「远程通知」并按形态分流 (修复类=阻断弹窗 / 普通=通知栏); 2 秒内重复触发去重, 单飞防并发重叠 */
    fun checkUpdatesAndNotice() {
        val now = System.currentTimeMillis()
        if (now - lastCheckAt < 2_000 || !checkInFlight.compareAndSet(false, true)) return
        lastCheckAt = now
        scope.launch {
            try {
                val latest = withContext(Dispatchers.IO) {
                    runCatching { Releases.fetchLatest(ctx, UpdateChannels.source(ctx), Channels.current(ctx)) }.getOrNull()
                }?.takeIf { Releases.relation(it) > 0 }
                latestKnown = latest
                // 更新分流 (设置页「更新提醒」开关):
                // 通知栏 = 后台侧提醒, 文案带版本性质 (强制/修复/普通), 划掉即跳过该版本;
                // 弹窗 = 进入软件检查到新版本一律弹 (force 时无视开关且不可取消), 已下载直接「立即安装」。
                // 2 秒内重复触发去重, 单飞防并发重叠
                val queue = mutableListOf<PendingDialog>()
                val eligible = latest?.takeIf { it.force || it.versionName != Prefs.updateDismissedVersion(ctx) }
                when {
                    eligible == null -> UpdateNotifier.cancel(ctx)
                    else -> {
                        val autoOn = Prefs.autoUpdateOn(ctx)
                        if (autoOn) UpdateNotifier.notifyAvailable(ctx, eligible)
                        if (autoOn || eligible.force) queue += PendingDialog.UpdateOffer(eligible)
                        if (!autoOn) UpdateNotifier.cancel(ctx)   // 通知被关: 收掉历史通知, 弹窗只留 force
                    }
                }
                // 远程通知: 全部命中排期/定向/去重的都进队列 (critical 在前, 同级按文件顺序)
                val notices = withContext(Dispatchers.IO) { RemoteNotices.fetch(ctx) }
                queue += RemoteNotices
                    .selectAll(notices, KbClock.now(), BuildVersion.DISPLAY, RemoteNotices.seenIds(ctx))
                    .map { PendingDialog.Remote(it) }
                // 合并进队列: 丢掉本会话已处理过的键, 按优先级降序; 正在展示但未处理的条目自然保留
                dialogQueue = queue.filter { it.key !in handledDialogKeys }.sortedByDescending { it.priority }
            } finally {
                checkInFlight.set(false)
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (Repo.dataset == null) Repo.load(ctx)
            PersonalRepo.load(ctx)
            // 启动清理过期安装包: 装完/过期的 APK 留在下载目录会让版本页误亮「立即安装」
            Updater.pruneApks(ctx, BuildVersion.DISPLAY)
        }
        ready = true
        // 启动时就拿通知权限 (13+ 无权限时通知被静默丢弃)
        (ctx as? Activity)?.let { ensureNotifPermission(it) }
        // 未选班级时引导选班; 仅用个人课表的用户 (数据在且生效) 直接进个人课表
        if (clsId == null && !showWelcome &&
            !(PersonalRepo.hasData() && Prefs.activeTimetable(ctx) == PersonalRepo.PERSONAL_KEY)
        ) screenChoose = true
        var dataUpdatedTo = 0
        withContext(Dispatchers.IO) {
            runCatching {
                // 数据热更先行: 数据与 APK 是独立版本线, 清单有新版就先套上 (用户无需重装)
                val m = Updater.fetchManifest(ctx)
                if (m != null && m.version > (Repo.dataset?.version ?: 0) &&
                    Updater.applyDataUpdate(ctx, m) != null
                ) {
                    dataTick++
                    ClassCache.save(ctx)
                    runCatching { ReminderScheduler.reschedule(ctx) }   // reschedule 自查三开关, 全关时内部 cancel
                    dataUpdatedTo = m.version
                }
            }
            resync()
        }
        // 更新提醒 (分流: 修复类弹窗 / 普通通知栏) + 远程通知检查
        // 冷启动在这里查; 之后每次回到前台由 ON_START 观察者再查 (同一函数, 2s 去重防双触发)
        checkUpdatesAndNotice()
        if (dataUpdatedTo > 0) {
            Toast.makeText(ctx, "课表数据已自动更新到 v$dataUpdatedTo${editSyncSuffix()}", Toast.LENGTH_LONG).show()
        }
    }

    // 每次回到前台都重新检查更新与远程通知 (冷启动首回 ON_START 由上面启动块覆盖, 去重兜底)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                checkUpdatesAndNotice()
                // 后台期间接收器/响铃页可能已把「下次提醒」重排成新事件, 设置页若沿用旧状态
                // 会显示一个已过去的时刻; 回前台重排一次并重读 (顺带自愈被系统吞掉的闹钟)
                scope.launch {
                    withContext(Dispatchers.IO) { runCatching { ReminderScheduler.reschedule(ctx) } }
                    nextReminderAt = Prefs.nextReminderAt(ctx)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 更新通知点按 → 直达软件更新页 (onCreate extras 与 onNewIntent 都会推 tick)
    val openUpdateTick = (ctx as? MainActivity)?.openUpdateTick ?: 0
    LaunchedEffect(openUpdateTick) {
        if (openUpdateTick > 0) {
            showUpdate = true
            if (releases == null && !releasesLoading) loadReleases()
        }
    }

    KbTheme(themeMode, colorSource, bgSeed, styleTheme) {
        if (!ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@KbTheme
        }

        // Repo.dataset / Prefs.bjid 非快照状态, 局部读会被内容 lambda 捕获旧值, 须经 dataTick/clsId 观察
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

        // ---- 编辑会话 (回退栈 + 虚影): 状态与动作 ----
        // 换课表(编辑键变化)即整场作废: 虚影和回退栈都只对当前课表有意义
        LaunchedEffect(editTarget) {
            sessionBjid = editTarget
            sessionBefore = null
            undoStack = emptyList()
            ghosts = emptyList()
            undoVisible = false
        }
        // 虚影生命周期: 一次性 delay 到最早到期点销毁, 不轮询
        LaunchedEffect(ghosts) {
            val wait = EditSession.nextExpiryIn(ghosts, System.currentTimeMillis()) ?: return@LaunchedEffect
            delay(wait)
            ghosts = EditSession.prune(ghosts, System.currentTimeMillis())
        }
        // 回退按钮生命周期: 栈非空时出现, 最后一次编辑/回退后 UNDO_VISIBLE_MS 到点隐去
        LaunchedEffect(lastOpStamp, undoStack) {
            if (undoStack.isEmpty()) {
                undoVisible = false
                return@LaunchedEffect
            }
            undoVisible = true
            delay(EditSession.UNDO_VISIBLE_MS)
            undoVisible = false
        }
        // 开屏引导后首次进表: 完整演示组只放一次 (老用户升级后也会看到这一遍)
        LaunchedEffect(editTarget, cls != null) {
            val t = editTarget
            if (t != null && cls != null && !Prefs.demoIntroDone(ctx)) {
                Prefs.setDemoIntroDone(ctx, true)
                showDemo = DemoSteps.all
            }
        }

        /** 编辑会话记账: 记逆操作 + 收虚影; 首笔操作记下整层快照供「还原全部」 */
        fun recordOp(result: EditOpResult, target: String?) {
            if (target == null) return
            if (sessionBjid != target) {
                sessionBjid = target
                undoStack = emptyList()
                ghosts = emptyList()
                sessionBefore = result.beforeSnapshot
            } else if (sessionBefore == null) {
                sessionBefore = result.beforeSnapshot
            }
            result.entry?.let { undoStack = undoStack + it }
            result.ghost?.let { ghosts = ghosts + it }
            lastOpStamp++
        }

        /** 带会话记账的编辑收尾: IO 里执行操作(产出逆操作与虚影), 回主线程重组 + 刷快照/小组件/提醒;
         *  trigger = delete/modify/add 时, 首次操作顺带弹出对应功能演示 */
        fun applyEditTracked(target: String?, trigger: String, op: suspend () -> EditOpResult) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { op() }.getOrNull() }
                if (result != null) {
                    recordOp(result, target)
                    val steps = DemoSteps.forTrigger(trigger, Prefs.demoSeen(ctx))
                    if (steps.isNotEmpty()) showDemo = steps
                }
                dataTick++
                resync()
            }
        }

        /** 回退一步: 弹出栈顶逆操作精确执行; 该步产生的虚影一并撤掉 */
        fun undoOne() {
            val entry = undoStack.lastOrNull() ?: return
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        when (entry) {
                            is UndoEntry.Added -> UserEdits.removeById(ctx, entry.editId)
                            is UndoEntry.Modified ->
                                if (entry.before != null) UserEdits.putEdit(ctx, entry.before)
                                else UserEdits.removeById(ctx, entry.editId)
                            is UndoEntry.Removed -> {
                                entry.hideEditId?.let { UserEdits.removeById(ctx, it) }
                                entry.prior?.let { UserEdits.putEdit(ctx, it) }
                            }
                            is UndoEntry.Reverted -> UserEdits.putEdit(ctx, entry.before)
                        }
                    }
                }
                dataTick++
                resync()
            }
            undoStack = undoStack.dropLast(1)
            ghosts = ghosts.filter { it.opId != entry.opId }
            lastOpStamp++
        }

        /** 长按回退: 震动后把覆盖层整层还原到本会话第一笔操作之前 */
        fun restoreAll() {
            val target = editTarget
            val snap = sessionBefore
            if (target == null || snap == null) return
            vibrateTap()
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { UserEdits.restore(ctx, target, snap) } }
                dataTick++
                resync()
            }
            undoStack = emptyList()
            ghosts = emptyList()
            sessionBefore = null
            lastOpStamp++
            Toast.makeText(ctx, "已还原全部修改", Toast.LENGTH_SHORT).show()
        }

        /** 点虚影恢复: 精确撤销产生它的那笔操作 (不动之后的其它修改), 虚影与其回退条目同步摘除 */
        fun restoreGhost(g: Ghost) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        g.hideEditId?.let { UserEdits.removeById(ctx, it) }
                        if (g.priorEdit != null) UserEdits.putEdit(ctx, g.priorEdit)
                        else g.moveEditId?.let { UserEdits.removeById(ctx, it) }
                    }
                }
                dataTick++
                resync()
            }
            ghosts = ghosts.filter { it.id != g.id }
            undoStack = undoStack.filter { it.opId != g.opId }
            lastOpStamp++
            Toast.makeText(ctx, "已恢复「${g.block.course.kc}」", Toast.LENGTH_SHORT).show()
        }

        /** 重置类操作后的会话清场 (覆盖层已整体变化, 回退栈与虚影全部作废) */
        fun resetEditSession() {
            sessionBefore = null
            undoStack = emptyList()
            ghosts = emptyList()
            undoVisible = false
            lastOpStamp++
        }

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
                        (ctx as? Activity)?.let { ensureNotifPermission(it) }
                        resync()
                    },
                    onSkip = {
                        Prefs.setOnboardingDone(ctx, true)
                        showWelcome = false
                        (ctx as? Activity)?.let { ensureNotifPermission(it) }
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
                        undoVisible = undoVisible,
                        undoCount = undoStack.size,
                        onUndo = { undoOne() },
                        onUndoAll = { restoreAll() },
                        ghosts = ghosts,
                        onGhostRestore = { restoreGhost(it) },
                    )
                }
            }
        }

        // ---- 课程详情 (含自定义颜色 / 还原 / 编辑 / 删除) ----
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
                        if (id != null) applyEditTracked(id, "revert") {
                            val snap = UserEdits.snapshot(id)
                            val prior = UserEdits.editOf(block.course.editId)
                            UserEdits.revertBlock(ctx, id, block)
                            EditOpResult(
                                entry = prior?.let { UndoEntry.Reverted(EditSession.newOpId(), it) },
                                ghost = null,
                                beforeSnapshot = snap,
                            )
                        }
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

        // 时段占用判定: 同天 + 节次重叠 + 周次有交集 → 拒绝 (单双周同槽合法放行)。
        // self 块自己的行不算冲突: 保存会把它们整体替换, 不排除的话对学校原始卡
        // 的任何编辑(改节数/改名/改时间)都会被自己的行误判成「该时段已有课程」拦下;
        // 新建(add)没有自身行, 传 null。
        // 生效视图全部是逐节行(学校数据每节一行、自建课展开后 djs=1), 单行占用宽度恒为 1 节 ——
        // 不能用 c.djs: 学校数据里它是大节序号(jc5→3, jc7→4), 当连堂节数用会把占用范围虚胖一大截
        val slotConflict: (UserEdits.Form, Block?) -> Boolean = { f, self ->
            cls?.courses.orEmpty().any { c ->
                val ownRow = self != null &&
                    c.kc == self.course.kc && c.fx == self.course.fx && c.zc == self.course.zc &&
                    c.day == self.course.day && c.jc in self.startJc..(self.startJc + self.span - 1)
                !ownRow && c.day == f.day &&
                    c.jc < f.startJc + f.span && f.startJc < c.jc + 1 &&
                    UserEdits.rangesOverlap(f.ranges, c.ranges)
            }
        }

        // ---- 添加课程 (点击空白格) ----
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
                        Toast.makeText(ctx, "这个时段已经有课了，换个时间或周次试试", Toast.LENGTH_SHORT).show()
                        return@CourseFormSheet
                    }
                    addAt = null
                    val id = editTarget
                    if (id != null) applyEditTracked(id, "add") {
                        val snap = UserEdits.snapshot(id)
                        val editId = UserEdits.add(ctx, id, f)
                        EditOpResult(
                            entry = UndoEntry.Added(EditSession.newOpId(), editId),
                            ghost = null,   // 新增没有"原位置", 不留虚影
                            beforeSnapshot = snap,
                        )
                    }
                },
                onDismiss = { addAt = null },
            )
        }

        // ---- 编辑课程 ----
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
                    if (slotConflict(f, b)) {
                        Toast.makeText(ctx, "这个时段已经有课了，换个时间或周次试试", Toast.LENGTH_SHORT).show()
                        return@CourseFormSheet
                    }
                    editBlock = null
                    val id = editTarget
                    if (id != null) applyEditTracked(id, "modify") {
                        val snap = UserEdits.snapshot(id)
                        val prior = UserEdits.editOf(b.course.editId)
                        val editId = UserEdits.saveBlock(ctx, id, b, f)
                        val opId = EditSession.newOpId()
                        // 时间/周次被改动: 原位置留移位虚影, 新位置是带点的正常卡片
                        val moved = editId != null && EditSession.touchedTimeOrWeek(f, b)
                        EditOpResult(
                            entry = editId?.let { UndoEntry.Modified(opId, it, prior) },
                            ghost = if (moved) Ghost(
                                id = EditSession.newGhostId(),
                                opId = opId,
                                block = b,
                                kind = EditSession.GHOST_MOVED,
                                priorEdit = prior,
                                moveEditId = editId,
                            ) else null,
                            beforeSnapshot = snap,
                        )
                    }
                },
                onDelete = {
                    editBlock = null
                    delBlock = b
                },
                onDismiss = { editBlock = null },
            )
        }

        // ---- 删除课程确认 ----
        delBlock?.let { b ->
            AlertDialog(
                onDismissRequest = { delBlock = null },
                title = { Text("删除课程") },
                text = { Text("确定移除「${b.course.kc}」吗？") },
                confirmButton = {
                    TextButton({
                        val id = editTarget
                        if (id != null) applyEditTracked(id, "delete") {
                            val snap = UserEdits.snapshot(id)
                            val prior = UserEdits.editOf(b.course.editId)
                            val hideId = UserEdits.removeBlock(ctx, id, b)
                            val opId = EditSession.newOpId()
                            EditOpResult(
                                entry = UndoEntry.Removed(opId, hideId, prior),
                                // 原位置虚影: 信息保留、变淡、生存期内可点按恢复
                                ghost = if (hideId != null || prior != null) Ghost(
                                    id = EditSession.newGhostId(),
                                    opId = opId,
                                    block = b,
                                    kind = EditSession.GHOST_DELETED,
                                    priorEdit = prior,
                                    hideEditId = hideId,
                                ) else null,
                                beforeSnapshot = snap,
                            )
                        }
                        delBlock = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton({ delBlock = null }) { Text("取消") } },
            )
        }

        // ---- 功能演示 (首次编辑触发 / 进表整组 / 更多面板手动入口) ----
        showDemo?.let { steps ->
            DemoShowcaseSheet(
                steps = steps,
                onDismiss = {
                    Prefs.markDemoShown(ctx, steps.map { it.id })
                    showDemo = null
                },
            )
        }

        // ---- 教务登录 ----
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

        // ---- 更多面板 ----
        if (showMore) {
            MoreSheet(
                cls = cls,
                appVersion = BuildVersion.DISPLAY,
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
                alarmMode = alarmMode,
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
                onAlarmModeChange = { v ->
                    Prefs.setAlarmMode(ctx, v)
                    alarmMode = v
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
                onResetClassEdits = {
                    showMore = false
                    clsId?.let { id ->
                        applyEdit { UserEdits.clearClass(ctx, id) }
                        resetEditSession()
                        Toast.makeText(ctx, "班级课表修改已重置，已恢复学校原课表", Toast.LENGTH_SHORT).show()
                    }
                },
                onResetPersonalEdits = {
                    showMore = false
                    applyEdit { UserEdits.clearClass(ctx, PersonalRepo.PERSONAL_BJID) }
                    resetEditSession()
                    Toast.makeText(ctx, "个人课表修改已重置，已恢复原课表", Toast.LENGTH_SHORT).show()
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
                            runCatching { ReminderScheduler.reschedule(ctx) }   // reschedule 自查三开关, 全关时内部 cancel
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
                onOpenDemos = {
                    showMore = false
                    showDemo = DemoSteps.all
                },
                onDevModeChange = { v ->
                    Prefs.setDevMode(ctx, v)
                    devMode = v
                    if (v) {
                        DebugLog.log("dev", "开发者模式已开启 (更多面板开关)")
                        Toast.makeText(ctx, "开发者模式已开启", Toast.LENGTH_SHORT).show()
                    } else {
                        // 关闭即彻底失效: 临时版本覆盖一并清掉, 按真实版本重新复查更新
                        if (BuildVersion.displayOverride != null || BuildVersion.codeOverride != null) {
                            BuildVersion.displayOverride = null
                            BuildVersion.codeOverride = null
                            checkUpdatesAndNotice()
                        }
                        DebugLog.log("dev", "开发者模式已关闭 (更多面板开关, 开发者选项全部失效)")
                        Toast.makeText(ctx, "开发者模式已关闭", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showMore = false },
                onOpenAbout = {
                    showMore = false
                    showAbout = true
                },
            )
        }

        // ---- 关于面板 ----
        if (showAbout) {
            AboutScreen(
                dataset = classDataset,
                appVersion = BuildVersion.DISPLAY,
                devMode = devMode,
                updateAvailable = latestKnown?.versionName,
                autoUpdateOn = autoUpdateOn,
                onAutoUpdateChange = { v ->
                    Prefs.setAutoUpdateOn(ctx, v)
                    autoUpdateOn = v
                    DebugLog.log("update", "更新提醒 → $v")
                },
                fromUpdate = Repo.fromUpdate,
                onOpenUpdate = {
                    // 更新面板与关于面板同为 KbSheet, 直接叠开, 关闭后回到关于面板
                    showUpdate = true
                    if (releases == null && !releasesLoading) loadReleases()
                },
                onDevModeChange = { v ->
                    Prefs.setDevMode(ctx, v)
                    devMode = v
                    DebugLog.log("dev", "开发者模式 → $v")
                },
                onClearUpdate = {
                    scope.launch {
                        withContext(Dispatchers.IO) { Repo.clearUpdate(ctx) }
                        dataTick++
                        resync()
                        Toast.makeText(ctx, "已恢复内置数据", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showAbout = false },
            )
        }

        // ---- 开发者模式 ----
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
                onTestRing = {
                    ReminderScheduler.scheduleTestRing(ctx)
                    Toast.makeText(ctx, "已排测试响铃，10 秒后应全屏响铃（可锁屏验证）", Toast.LENGTH_SHORT).show()
                },
                onTestUpdateNotify = {
                    Toast.makeText(ctx, "已排测试更新通知，10 秒后发出（固定测试数据）", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        kotlinx.coroutines.delay(10_000)
                        com.ccsut.kb.UpdateNotifier.notifyAvailable(ctx, com.ccsut.kb.ui.devTestRelease())
                        DebugLog.log("dev", "测试更新通知已发 (v9.9.9-test)")
                    }
                },
                onTestUpdateDialog = {
                    Toast.makeText(ctx, "已排测试更新弹窗，10 秒后排队（回到课表主界面弹出）", Toast.LENGTH_SHORT).show()
                    scope.launch {
                        kotlinx.coroutines.delay(10_000)
                        dialogQueue = (dialogQueue + PendingDialog.UpdateOffer(com.ccsut.kb.ui.devTestRelease()))
                            .distinctBy { it.key }
                        DebugLog.log("dev", "测试更新弹窗已排队 (v9.9.9-test)")
                    }
                },
                onVersionOverride = { name, code ->
                    BuildVersion.displayOverride = name
                    BuildVersion.codeOverride = code
                    DebugLog.log("dev", "临时版本 → v${BuildVersion.DISPLAY} (code=${BuildVersion.EFFECTIVE_CODE})")
                    // 立即按新版本基准复查: 真实的更新通知与弹窗会按线上数据触发
                    checkUpdatesAndNotice()
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
                    // 模拟版本列表: 高版本给一个不存在的下载地址, 点「更新」可走完失败重试链路
                    releases = listOf(
                        ReleaseInfo(
                            versionName = "9.9.9",
                            versionCode = BuildVersion.CODE + 1,
                            fileUrl = "https://mock.invalid/ccsut-kb-9.9.9.apk",
                            bytes = 13_527_019,
                            sections = listOf(
                                NoteSection(
                                    null,
                                    listOf(
                                        "预览软件更新页效果（开发者模式模拟，下载必然失败）",
                                        "新增本地课表编辑：点空格加课、长按拖拽换课",
                                        "修复深色模式下弹层文字看不清的问题",
                                    ),
                                ),
                            ),
                        ),
                        ReleaseInfo(versionName = BuildVersion.DISPLAY, versionCode = BuildVersion.CODE),
                    )
                    releasesError = null
                    showMore = false
                    showUpdate = true
                },
                onResetPersonal = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            Prefs.clearPersonal(ctx)
                            runCatching { BgStore.delete(ctx) }
                        }
                        resetVisualState()
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
                        resetVisualState()
                        resetEditSession()
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

        // ---- 弹窗队列: 更新提醒 > critical 通知 > 普通通知, 一次只弹一个, 处理完自动弹下一个 ----
        // 只在课表主界面空闲时插入: 引导页/选班页/设置与关于等面板/登录/课程编辑流程中不弹。
        // 检查照常进行, 待弹条目留在队列里, 用户回到课表界面后自动出现。
        val mainIdle = !showWelcome && !screenChoose && !showMore && !showAbout && !showDev &&
            !showUpdate && !showLogin && detailCourse == null && addAt == null &&
            editBlock == null && delBlock == null
        if (mainIdle) dialogQueue.firstOrNull()?.let { item ->
            fun finish() {
                handledDialogKeys = handledDialogKeys + item.key
                dialogQueue = dialogQueue.filterNot { it.key == item.key }
                if (item is PendingDialog.Remote && item.notice.once) RemoteNotices.markSeen(ctx, item.notice.id)
            }
            when (item) {
                is PendingDialog.UpdateOffer -> com.ccsut.kb.ui.UpdateOfferDialog(
                    release = item.release,
                    downloading = downloadingVer,
                    progress = apkProgress,
                    // 立即更新/立即安装统一走 startDownload (内部自带缓存命中直装); 安装意图发出后收掉弹窗。
                    // 测试数据 (fileUrl 为空) 不会真实下载, 点按钮只提示
                    onInstall = {
                        if (item.release.fileUrl == null) {
                            Toast.makeText(ctx, "测试数据没有真实安装包，仅供预览", Toast.LENGTH_SHORT).show()
                        } else {
                            startDownload(item.release) { finish() }
                        }
                    },
                    onCancel = { finish() },
                )
                is PendingDialog.Remote -> NoticeDialog(
                    title = item.notice.title,
                    message = item.notice.message,
                    critical = item.notice.critical,
                    confirmText = item.notice.confirmText,
                    dismissText = item.notice.dismissText,
                    onConfirm = {
                        finish()
                        item.notice.url?.let { com.ccsut.kb.ui.openUrl(ctx, it) }
                    },
                    onDismiss = { finish() },
                )
            }
        }

        // ---- 软件更新页 ----
        if (showUpdate) {
            UpdateScreen(
                source = updateSource,
                channel = updateChannel,
                releases = releases,
                loading = releasesLoading,
                error = releasesError,
                downloading = downloadingVer,
                progress = apkProgress,
                cachedVersions = cachedApks,
                onSourceChange = { s ->
                    UpdateChannels.setSource(ctx, s)
                    updateSource = s
                    loadReleases()
                },
                onChannelChange = { c ->
                    Channels.set(ctx, c)
                    updateChannel = c
                    loadReleases()
                },
                onBetaUrlSave = { u ->
                    Channels.setBetaUrl(ctx, u)
                    loadReleases()
                },
                onRefresh = { refreshUpdatePage() },
                onInstall = { startDownload(it) },
                onDismiss = { showUpdate = false },
            )
        }
    }
}

/** 弹窗队列条目: 修复类更新与远程通知统一成带优先级的展示单元, priority 数字越大越先弹 */
sealed interface PendingDialog {
    val key: String
    val priority: Int

    /** 更新提醒弹窗: 进入软件检查到新版本一律弹; force (强制更新) 优先级最高且不可取消 */
    data class UpdateOffer(val release: ReleaseInfo) : PendingDialog {
        override val key: String = "update:${release.versionName}"
        override val priority: Int = if (release.force) 40 else 30
    }

    /** 远程通知: critical 在普通通知之前, 同优先级保持文件里给的顺序 */
    data class Remote(val notice: RemoteNotice) : PendingDialog {
        override val key: String = "notice:${notice.id}"
        override val priority: Int = if (notice.critical) 20 else 10
    }
}

private fun installApk(ctx: Context, file: File) {
    // Android 8+ 需用户逐应用授予「安装未知应用」: 未授权时引导去设置页, 不再静默失败
    if (!ctx.packageManager.canRequestPackageInstalls()) {
        Toast.makeText(ctx, "请先允许「安装未知应用」，授权后重新点「立即更新」", Toast.LENGTH_LONG).show()
        runCatching {
            ctx.startActivity(
                Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            Toast.makeText(ctx, "请在系统设置 → 应用里为本应用开启「安装未知应用」", Toast.LENGTH_LONG).show()
        }
        return
    }
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
        .onFailure { Toast.makeText(ctx, "无法启动安装: ${it.message}", Toast.LENGTH_SHORT).show() }
}
