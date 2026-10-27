package com.ccsut.kb.ui
import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ripple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.Cls
import com.ccsut.kb.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoreSheet(
    cls: Cls?,
    appVersion: String,
    themeMode: Int,
    bgOn: Boolean,
    bgAlpha: Int,
    colorSource: Int,
    bgSeed: Int,
    styleTheme: Int,
    reminderOn: Boolean,
    reminderLead: Int,
    nextReminderAt: Long,
    afterClassOn: Boolean,
    earlyOn: Boolean,
    preclassOn: Boolean,
    preclassWindow: Int,
    devMode: Boolean,
    personalActive: Boolean,
    personalName: String,
    personalSyncedAt: Long,
    onThemeMode: (Int) -> Unit,
    onBgSaved: () -> Unit,
    onRemoveBg: () -> Unit,
    onBgAlpha: (Int) -> Unit,
    onOpenAppearance: () -> Unit = {},
    onOpenShare: () -> Unit = {},
    onColorSource: (Int) -> Unit,
    onStyleTheme: (Int) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onLeadChange: (Int) -> Unit,
    onAfterClassChange: (Boolean) -> Unit,
    onEarlyChange: (Boolean) -> Unit,
    onPreclassToggle: (Boolean) -> Unit,
    onPreclassWindow: (Int) -> Unit,
    onSelectClass: () -> Unit,
    onResetClassEdits: () -> Unit,
    onResetPersonalEdits: () -> Unit,
    onOpenPersonalLogin: () -> Unit,
    onSetTimetable: (Boolean) -> Unit,
    onRefreshPersonal: () -> Unit,
    onLogoutPersonal: () -> Unit,
    onOpenDev: () -> Unit,
    onOpenDemos: () -> Unit = {},
    onDevModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBgDialog by remember { mutableStateOf(false) }

    // 相册选图 (Photo Picker, 免存储权限)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { BgStore.saveFromUri(ctx, uri) }
                        .onFailure { android.util.Log.e("kb-bg", "saveFromUri 异常", it) }
                        .getOrDefault(false)
                }
                if (ok) onBgSaved()
                else Toast.makeText(ctx, "背景图处理失败，请换一张试试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 提醒权限状态 (每次 RESUME 重查: 从系统设置授权/撤销返回后副标题才不会停在旧状态)
    var notifGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // 「禁止后不再询问」: 再 launch 也静默无弹窗, 红行跳系统应用通知设置
    var permDeniedForever by remember { mutableStateOf(false) }
    var exactOk by remember { mutableStateOf(false) }
    // 电池优化白名单: 划掉应用若被 ROM 按「强制停止」清理, 已排闹钟会被一并清空, 自启动也无从生效
    var battOk by remember { mutableStateOf(false) }
    var showKeepAliveGuide by remember { mutableStateOf(false) }
    DisposableEffect(ctx) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pm = ctx.getSystemService(PowerManager::class.java)
        val refresh = {
            notifGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            exactOk = am?.canScheduleExactAlarms() ?: false
            battOk = runCatching { pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false }
                .getOrDefault(false)
        }
        refresh()
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        val lifecycle = (ctx as? ComponentActivity)?.lifecycle
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
        // 拒绝且系统不再弹窗 = 永久拒绝, 之后只引导去系统设置
        permDeniedForever = !it && Build.VERSION.SDK_INT >= 33 &&
            !ActivityCompat.shouldShowRequestPermissionRationale(ctx as Activity, Manifest.permission.POST_NOTIFICATIONS)
    }
    fun requestNotifPerm() {
        if (Build.VERSION.SDK_INT < 33) return
        permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (showBgDialog) {
        AlertDialog(
            onDismissRequest = { showBgDialog = false },
            title = { Text("课表背景") },
            text = { Text("换一张，还是不要背景了？") },
            confirmButton = {
                TextButton(onClick = {
                    showBgDialog = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("换一张") }
            },
            dismissButton = {
                TextButton(onClick = { showBgDialog = false; onRemoveBg() }) { Text("移除") }
            },
        )
    }

    // 后台保活指引: 划掉应用卡片在多数国产 ROM 上 = 强制停止, 已排的系统闹钟会被一并清空,
    // 自启动随之无从生效 —— 白名单申请解决不了的部分靠这几步手动设置兜住
    if (showKeepAliveGuide) {
        AlertDialog(
            onDismissRequest = { showKeepAliveGuide = false },
            title = { Text("退到桌面也照常提醒") },
            text = {
                Text(
                    "上滑划掉应用时，部分系统会连同已排的提醒一起清掉。做完这两步就不会了：\n\n" +
                        "1. 后台权限：系统设置 → 电池 → 长工课表通 → 允许后台高耗电（或「不限制」）\n" +
                        "2. 锁定卡片：最近任务里下拉本应用，点 🔒 锁定\n\n" +
                        "vivo / iQOO：i管家 → 应用管理 → 自启动保持开启\n" +
                        "小米 / 红米：省电策略选「无限制」\n" +
                        "OPPO / 一加 / 真我：最近任务下拉锁定\n" +
                        "华为 / 荣耀：启动管理改「手动管理」，三个开关全开\n\n" +
                        "小习惯：退出时按 Home 键回桌面，别上滑划掉卡片。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showKeepAliveGuide = false }) { Text("知道了") }
            },
        )
    }

    val sheetScroll = rememberScrollState()
    KbSheet(onDismiss = onDismiss, title = "更多") {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // 登录态 = 教务姓名非空; 当前课表卡与账号卡两处共用同一判定
            val personalLoggedIn = personalName.isNotBlank()
            // 重置确认弹窗挂在哪种课表上: "class" / "personal", null=不弹
            var resetConfirmFor by remember { mutableStateOf<String?>(null) }

            // ---- 当前课表 ----
            SectionHeader("当前课表")
            SettingCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    // 与表单同一枚 chip 形态: 未选中=灰底描边
                    FormChip(
                        text = "班级课表",
                        on = !personalActive,
                        // 幂等守卫: 只在「当前不是班级课表」时才触发切换; 点已选中的 Chip 无操作
                        onClick = { if (personalActive) onSetTimetable(false) },
                    )
                    FormChip(
                        text = "个人课表",
                        on = personalActive,
                        enabled = personalLoggedIn,
                        onClick = { if (!personalActive) onSetTimetable(true) },
                    )
                }
                if (!personalActive) {
                    SettingRow(
                        icon = Icons.Rounded.Person,
                        title = cls?.bjmc ?: "还没选班级",
                        subtitle = cls?.let { "${it.yxmc} · ${it.zymc}" } ?: "选好班级就能看课表了",
                        trailing = { RowTrailing("换班") },
                        onClick = onSelectClass,
                    )
                    SettingRow(
                        icon = Icons.Rounded.RestartAlt,
                        title = "重置课表修改",
                        subtitle = "清除你在这份班级课表上的全部本地修改，恢复学校原课表",
                        trailing = { RowTrailing("重置") },
                        onClick = { resetConfirmFor = "class" },
                    )
                } else {
                    // 纯展示行: 同步动作在下方「个人课表」账号卡的身份行上, 不在这里重复 (两处曾各自一个「同步」入口)
                    SettingRow(
                        icon = Icons.Rounded.Person,
                        title = "个人课表",
                        subtitle = "上次同步 " + formatSynced(personalSyncedAt),
                    )
                    // 与班级课表同一套重置: 拉取到的原课表只读, 本地修改是覆盖层, 重置即清空覆盖层
                    SettingRow(
                        icon = Icons.Rounded.RestartAlt,
                        title = "重置课表修改",
                        subtitle = "清除你在这份个人课表上的全部本地修改，恢复拉取时的原课表",
                        trailing = { RowTrailing("重置") },
                        onClick = { resetConfirmFor = "personal" },
                    )
                }
            }

            // ---- 重置课表修改: 二次确认, 不可撤销 (班级/个人共用, 文案随目标切换) ----
            resetConfirmFor?.let { target ->
                val personal = target == "personal"
                AlertDialog(
                    onDismissRequest = { resetConfirmFor = null },
                    title = { Text("重置课表修改") },
                    text = { Text(
                        if (personal) "会清除你在这份个人课表上的全部修改（移动、加课、删课、改色），恢复成拉取时的原课表，之后找不回来。确定继续吗？"
                        else "会清除你在这份班级课表上的全部修改（移动、加课、删课、改色），恢复成学校原课表，之后找不回来。确定继续吗？"
                    ) },
                    confirmButton = {
                        TextButton(onClick = {
                            resetConfirmFor = null
                            if (personal) onResetPersonalEdits() else onResetClassEdits()
                        }) { Text("重置", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetConfirmFor = null }) { Text("取消") }
                    },
                )
            }

            // ---- 个人课表: 账号生命周期 ----
            SectionHeader("个人课表")
            SettingCard {
                // 未登录/已登录两组内容互补展开收起: 高度 + 淡入淡出, 换态不突兀
                AnimatedVisibility(
                    visible = !personalLoggedIn,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    SettingRow(
                        icon = Icons.Rounded.ManageAccounts,
                        title = "登录教务账号",
                        subtitle = "短信验证码直连教务，只拉你自己的课表",
                        trailing = { RowTrailing("登录") },
                        onClick = onOpenPersonalLogin,
                    )
                }
                AnimatedVisibility(
                    visible = personalLoggedIn,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.ManageAccounts,
                            title = personalName,
                            subtitle = "已登录 · 上次同步 " + formatSynced(personalSyncedAt),
                            // 同步的唯一入口 (「当前课表」卡的个人行已改为纯展示): 刷新 = 重新验证码登录
                            trailing = { RowTrailing("同步") },
                            onClick = onRefreshPersonal,
                        )
                        SettingRow(
                            icon = Icons.AutoMirrored.Rounded.Logout,
                            title = "退出登录",
                            subtitle = "清除本机个人课表与登录状态",
                            onClick = onLogoutPersonal,
                        )
                    }
                }
            }

            // ---- 分享课表: 导出 .ics 给 iPhone 日历 / 帮朋友导出 ----
            SectionHeader("分享课表")
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.IosShare,
                    title = "导出日历文件 (.ics)",
                    subtitle = "发给 iPhone 同学导入 Apple 日历；也可帮朋友导出",
                    trailing = { RowTrailing("分享") },
                    onClick = onOpenShare,
                )
            }

            // ---- 外观 ----
            SectionHeader("外观")
            SettingCard {
                // 分段控件独占一行 (标签行 + 全宽分段): 尾槽塞三档会把标题挤竖排, 与 UpdateScreen 同款
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIconBadge(Icons.Rounded.DarkMode)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "深色模式",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                SingleChoiceSegmentedButtonRow(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                ) {
                    val modes = listOf("系统" to 0, "浅色" to 1, "深色" to 2)
                    modes.forEachIndexed { i, (label, v) ->
                        SegmentedButton(
                            selected = themeMode == v,
                            onClick = { onThemeMode(v) },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIconBadge(Icons.Rounded.Palette)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "主题配色",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 19.sp,
                        )
                        // 右侧分段已表达「品牌」档, 副标题只报当前主题名
                        Text(
                            if (styleTheme != 0) (ThemePacks.byId(styleTheme)?.title ?: "品牌配色") else when (colorSource) {
                                1 -> if (bgOn) "跟随背景图取色" else "跟随背景图（未设背景）"
                                2 -> "经典品牌蓝"
                                else -> "跟随系统壁纸"
                            },
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                SingleChoiceSegmentedButtonRow(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                ) {
                    val srcs = listOf("壁纸" to 0, "背景图" to 1, "品牌" to 2)
                    srcs.forEachIndexed { i, (label, v) ->
                        SegmentedButton(
                            // 主题风格归入品牌档: 固定配色, 永不随壁纸/背景变化
                            selected = if (styleTheme != 0) v == 2 else colorSource == v,
                            enabled = v != 1 || bgOn,
                            onClick = {
                                when (v) {
                                    // 品牌 = 固定配色档: 已选主题风格则保持, 否则回经典品牌蓝
                                    2 -> onColorSource(2)
                                    else -> {
                                        if (styleTheme != 0) onStyleTheme(0)
                                        onColorSource(v)
                                    }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, srcs.size),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
                // ---- 主题风格直达: 色卡平铺, 点选即换 ----
                Text(
                    "主题风格",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(start = 14.dp, top = 8.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
                ) {
                    ThemeSwatchCard(
                        title = "经典品牌蓝",
                        selected = styleTheme == 0 && colorSource == 2,
                        preview = listOf(
                            LightColors.background, LightColors.primary, LightColors.primaryContainer,
                            LightColors.secondaryContainer, LightColors.tertiaryContainer,
                        ),
                        onClick = { onStyleTheme(0); onColorSource(2) },
                    )
                    ThemePacks.ALL.forEach { p ->
                        ThemeSwatchCard(
                            title = p.title,
                            selected = styleTheme == p.id,
                            preview = ThemePacks.previewOf(p),
                            onClick = { onStyleTheme(p.id); onColorSource(2) },
                        )
                    }
                }
                SettingRow(
                    icon = Icons.Rounded.Wallpaper,
                    title = "课表背景",
                    subtitle = if (bgOn) "已设置" else "选张图当背景",
                    trailing = { RowTrailing(if (bgOn) "更换" else "选择") },
                    onClick = {
                        if (bgOn) showBgDialog = true
                        else picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
                // 设了背景才出现的透明度调杆: 展开收起带高度过渡
                AnimatedVisibility(
                    visible = bgOn,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    Column(Modifier.padding(start = 62.dp, end = 14.dp, bottom = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "透明度",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "$bgAlpha%",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = bgAlpha / 100f,
                            onValueChange = { onBgAlpha((it * 100).toInt().coerceIn(10, 100)) },
                        )
                    }
                }
                SettingRow(
                    icon = Icons.Rounded.Palette,
                    title = "课程颜色",
                    subtitle = "在课程详情里为单门课自定义颜色",
                )
                // ---- 自定义外观: 独立面板 (状态条透明度等, 之后的新自定义选项都进那个面板) ----
                SettingRow(
                    icon = Icons.Rounded.Tune,
                    title = "自定义外观",
                    subtitle = "状态条透明度等主页个性化",
                    trailing = { RowTrailing("调整") },
                    onClick = onOpenAppearance,
                )
            }

            // ---- 提醒 ----
            SectionHeader("提醒")
            SettingCard {
                // 跳系统里对应通道的设置页 (声音/横幅由用户在通道级控制)
                fun openChannel(id: String) {
                    runCatching {
                        ctx.startActivity(
                            Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, id),
                        )
                    }
                }
                data class RemState(val sub: String, val err: Boolean, val click: (() -> Unit)?)
                val rem = when {
                    !reminderOn -> RemState("每节课开始前按时提醒", false, null)
                    !notifGranted -> if (permDeniedForever) RemState(
                        // 永久拒绝后系统弹窗再也不出现, 只能引导去系统应用通知设置
                        "通知权限被关了，点这里去系统设置", true,
                    ) {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
                            )
                        }
                    } else RemState(
                        "还没开通知权限，点这里去授权", true,
                    ) { requestNotifPerm() }
                    !exactOk -> RemState(
                        "可能不准时，点这里去设置", true,
                    ) {
                        runCatching {
                            ctx.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:${ctx.packageName}")),
                            )
                        }
                    }
                    nextReminderAt > 0 -> RemState(
                        "下次 " + DateTimeFormatter.ofPattern("M月d日 HH:mm")
                            .format(Instant.ofEpochMilli(nextReminderAt).atZone(ZoneId.systemDefault())) +
                            " · 点按调通知样式",
                        false,
                    ) { openChannel(ReminderScheduler.CHANNEL_CLASS) }
                    else -> RemState("已开启 · 点按调通知样式", false) {
                        openChannel(ReminderScheduler.CHANNEL_CLASS)
                    }
                }
                SettingRow(
                    icon = Icons.Rounded.Notifications,
                    title = "课前提醒",
                    subtitle = rem.sub,
                    subtitleColor = if (rem.err) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = {
                        KbSwitch(checked = reminderOn, onCheckedChange = {
                            if (it) requestNotifPerm()
                            onReminderToggle(it)
                        })
                    },
                    onClick = rem.click,
                )
                // 开启提醒才展开的子设置: 提前量分段 + 闹钟模式开关
                AnimatedVisibility(
                    visible = reminderOn,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.Schedule,
                            title = "提前量",
                            subtitle = "提醒早于上课的分钟数",
                        )
                        val leads = listOf(5, 10, 15, 20, 30)
                        SingleChoiceSegmentedButtonRow(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                        ) {
                            leads.forEachIndexed { i, v ->
                                SegmentedButton(
                                    selected = reminderLead == v,
                                    onClick = { onLeadChange(v) },
                                    shape = SegmentedButtonDefaults.itemShape(i, leads.size),
                                ) { Text("$v", fontSize = 12.sp) }
                            }
                        }
                        // 划掉应用在多数国产 ROM 上按强制停止清理, 已排提醒会被清后无从接力:
                        // 白名单在这里一键申请, 其余厂商差异走指引
                        SettingRow(
                            icon = Icons.Rounded.BatterySaver,
                            title = "后台提醒保障",
                            subtitle = if (battOk) "已允许后台运行，退到桌面后提醒照常送达"
                            else "点这里允许后台运行，否则退到桌面后提醒可能被系统推迟或丢弃",
                            subtitleColor = if (battOk) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                            onClick = if (battOk) null else {
                                {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                                .setData(Uri.parse("package:${ctx.packageName}")),
                                        )
                                    }
                                }
                            },
                        )
                        SettingRow(
                            icon = Icons.Rounded.Lock,
                            title = "锁定后台教程",
                            subtitle = "vivo / 小米 / OPPO 等防清理设置指引",
                            onClick = { showKeepAliveGuide = true },
                        )
                    }
                }
                // 与课前提醒各自独立: 关掉课前提醒不影响这两项; 点行进对应通道的设置页
                SettingRow(
                    icon = Icons.Rounded.Celebration,
                    title = "下课后小结",
                    subtitle = "最后一节下课后推送今日课程小结 · 静音通知",
                    onClick = { openChannel(ReminderScheduler.CHANNEL_AFTER) },
                    trailing = {
                        KbSwitch(checked = afterClassOn, onCheckedChange = {
                            if (it) requestNotifPerm()
                            onAfterClassChange(it)
                        })
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.Bedtime,
                    title = "早八前夜",
                    subtitle = "前一晚 21:30，明天真有早八才提醒 · 点按调通知样式",
                    onClick = { openChannel(ReminderScheduler.CHANNEL_EARLY) },
                    trailing = {
                        KbSwitch(checked = earlyOn, onCheckedChange = {
                            if (it) requestNotifPerm()
                            onEarlyChange(it)
                        })
                    },
                )
                // 课前全屏聚焦: 与通知类提醒相互独立, 纯 App 内展示
                SettingRow(
                    icon = Icons.Rounded.Fullscreen,
                    title = "课前全屏聚焦",
                    subtitle = "课前 $preclassWindow 分钟内打开 APP，直接看下节课",
                    trailing = { KbSwitch(checked = preclassOn, onCheckedChange = onPreclassToggle) },
                )
                AnimatedVisibility(
                    visible = preclassOn,
                    enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    Column {
                        SettingRow(
                            icon = Icons.Rounded.Tune,
                            title = "触发窗口 · $preclassWindow 分钟",
                            subtitle = "距下节课多少分钟内打开就自动全屏 (5–60)",
                        )
                        Slider(
                            value = preclassWindow.toFloat(),
                            onValueChange = { onPreclassWindow((it + 0.5f).toInt()) },
                            valueRange = 5f..60f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                        )
                    }
                }
                SettingRow(
                    icon = Icons.Rounded.Widgets,
                    title = "桌面小组件",
                    subtitle = "长按桌面空白处添加",
                )
            }

            // ---- 功能演示 ----
            SectionHeader("功能演示")
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.Slideshow,
                    title = "功能演示",
                    subtitle = "回退、虚影、圆点标记，一次看懂编辑课表的技巧",
                    onClick = onOpenDemos,
                )
            }

            // ---- 开发者模式入口: 仅开启时显示, 关闭后整块隐藏, 如同从未开启 ----
            if (devMode) {
                SectionHeader("开发者")
                SettingCard {
                    SettingRow(
                        icon = Icons.Rounded.BugReport,
                        title = "开发者模式",
                        subtitle = "时间旅行 · 测试通知 · 日志与诊断 · 点行进入面板",
                        trailing = { KbSwitch(checked = devMode, onCheckedChange = onDevModeChange) },
                        // 行主体进面板; 拨开关即整体关闭 (临时版本等开发者选项一并失效)
                        onClick = onOpenDev,
                    )
                }
            }

            // ---- 关于 ----
            // 品牌块 / 软件更新 / 课表数据 / 社区都在独立「关于」页, 这里只留一个入口
            SectionHeader("关于")
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.Info,
                    title = "关于长工课表通",
                    subtitle = "v$appVersion · 软件更新 · 加入社区",
                    trailing = { RowTrailing("详情") },
                    onClick = onOpenAbout,
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

// ==================== 开发者模式面板 ====================

@Composable
internal fun DevActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    SettingRow(title = title, subtitle = subtitle, trailing = { RowTrailing() }, onClick = onClick)
}

// ==================== 主题风格选择 ====================

/** 主题风格色卡: 迷你课表预览(底色+主色条+容器块) + 名称, 选中描边高亮 */
@Composable
private fun ThemeSwatchCard(
    title: String,
    selected: Boolean,
    preview: List<Color>,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
    ) {
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(preview[0])
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(7.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // 主色条 = 顶栏, 三个容器块 = 课程卡
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(preview[1]),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(preview[2], preview[3], preview[4]).forEach { c ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(c),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            title,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) cs.primary else cs.onSurfaceVariant,
        )
    }
}
