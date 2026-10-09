package com.ccsut.kb.ui
import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ripple
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ccsut.kb.R
import com.ccsut.kb.data.BgStore
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Dataset
import com.ccsut.kb.data.Updater
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
    dataset: Dataset,
    cls: Cls?,
    fromUpdate: Boolean,
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
    devMode: Boolean,
    personalActive: Boolean,
    personalName: String,
    personalSyncedAt: Long,
    onThemeMode: (Int) -> Unit,
    onBgSaved: () -> Unit,
    onRemoveBg: () -> Unit,
    onBgAlpha: (Int) -> Unit,
    onColorSource: (Int) -> Unit,
    onStyleTheme: (Int) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onLeadChange: (Int) -> Unit,
    onAfterClassChange: (Boolean) -> Unit,
    onEarlyChange: (Boolean) -> Unit,
    onSelectClass: () -> Unit,
    onOpenPersonalLogin: () -> Unit,
    onSetTimetable: (Boolean) -> Unit,
    onRefreshPersonal: () -> Unit,
    onLogoutPersonal: () -> Unit,
    onOpenDev: () -> Unit,
    onDevModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onCheckUpdate: ((String?) -> Unit) -> Unit,
    onClearUpdate: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Updater.manifestUrl(ctx)) }
    var urlDraft by remember { mutableStateOf("") }
    var showBgDialog by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkNote by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme

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
    var exactOk by remember { mutableStateOf(false) }
    DisposableEffect(ctx) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val refresh = {
            notifGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            exactOk = am?.canScheduleExactAlarms() ?: false
        }
        refresh()
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        val lifecycle = (ctx as? ComponentActivity)?.lifecycle
        lifecycle?.addObserver(obs)
        onDispose { lifecycle?.removeObserver(obs) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifGranted = it
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
    // ---------------- 主题风格选择 ----------------
    if (showStylePicker) {
        KbSheet(onDismiss = { showStylePicker = false }) {
            Text(
                "主题风格",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
            )
            // 品牌档第一项: 经典品牌蓝 (固定, 不随壁纸/背景变化)
            StyleOptionRow(
                title = "经典品牌蓝",
                tagline = "固定蓝色 · 默认品牌配色",
                selected = styleTheme == 0 && colorSource == 2,
                swatches = listOf(
                    LightColors.background, LightColors.primary, LightColors.primaryContainer,
                    LightColors.secondary, LightColors.tertiary,
                ),
                onClick = { onStyleTheme(0); onColorSource(2); showStylePicker = false },
            )
            ThemePacks.ALL.forEach { p ->
                StyleOptionRow(
                    title = p.title,
                    tagline = p.tagline,
                    selected = styleTheme == p.id,
                    swatches = ThemePacks.previewOf(p),
                    onClick = { onStyleTheme(p.id); onColorSource(2); showStylePicker = false },
                )
            }
            Spacer(Modifier.height(18.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (showUrlDialog) {
        val ok = urlDraft.trim().startsWith("https://")
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("更新地址") },
            text = {
                OutlinedTextField(
                    value = urlDraft,
                    onValueChange = { urlDraft = it },
                    singleLine = true,
                    placeholder = { Text("https://…/latest.json") },
                    supportingText = { Text(if (ok) "指向 latest.json 的地址" else "要以 https:// 开头") },
                    isError = !ok,
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = ok,
                    onClick = {
                        val v = urlDraft.trim()
                        Updater.setManifestUrl(ctx, v)
                        url = v
                        showUrlDialog = false
                        Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show()
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("取消") }
            },
        )
    }
    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("恢复内置数据") },
            text = { Text("会丢掉在线更新的那份课表，回到 app 自带的那份。") },
            confirmButton = {
                TextButton(
                    onClick = { showResetConfirm = false; onClearUpdate() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("先不用") }
            },
        )
    }

    val sheetScroll = rememberScrollState()
    KbSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                "更多",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Spacer(Modifier.height(14.dp))

            // ---------------- 当前课表 ----------------
            SectionHeader("当前课表")
            SettingCard {
                val loggedIn = personalName.isNotBlank()
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    FilterChip(
                        selected = !personalActive,
                        // 幂等守卫: 只在「当前不是班级课表」时才触发切换; 点已选中的 Chip 无操作
                        onClick = { if (personalActive) onSetTimetable(false) },
                        label = { Text("班级课表") },
                    )
                    FilterChip(
                        selected = personalActive,
                        enabled = loggedIn,
                        onClick = { if (!personalActive) onSetTimetable(true) },
                        label = { Text("个人课表") },
                    )
                }
                Text(
                    if (personalActive) "当前显示：${personalName}的个人课表"
                    else "当前显示：${cls?.bjmc ?: "未选班级"}的班级课表",
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
                )
            }

            // ---------------- 班级 (仅班级课表模式显示: 个人课表模式下没有「换班」语义) ----------------
            if (!personalActive) {
                SettingCard {
                    SettingRow(
                        icon = Icons.Rounded.Person,
                        title = cls?.bjmc ?: "还没选班级",
                        subtitle = cls?.let { "${it.yxmc} · ${it.zymc}" } ?: "选好班级就能看课表了",
                        trailing = { RowTrailing("换班") },
                        onClick = onSelectClass,
                    )
                }
            }

            // ---------------- 个人课表账号 ----------------
            SectionHeader("个人课表账号")
            SettingCard {
                val loggedIn = personalName.isNotBlank()
                SettingRow(
                    icon = Icons.Rounded.ManageAccounts,
                    title = if (loggedIn) personalName else "登录教务账号",
                    subtitle = if (!loggedIn) "选课、重修等专属课表，需短信验证码登录"
                    else "已登录 · 上次同步 " + formatSynced(personalSyncedAt),
                    trailing = { if (!loggedIn) RowTrailing("登录") },
                    onClick = if (!loggedIn) onOpenPersonalLogin else null,
                )
                if (loggedIn) {
                    SettingRow(
                        icon = Icons.Rounded.Refresh,
                        title = "刷新个人课表",
                        subtitle = "教务会话有时效，刷新需重新验证码登录",
                        trailing = { RowTrailing("去刷新") },
                        onClick = onRefreshPersonal,
                    )
                    SettingRow(
                        icon = Icons.Rounded.Logout,
                        title = "退出教务账号",
                        subtitle = "清除手机上的个人课表数据",
                        onClick = onLogoutPersonal,
                    )
                }
            }

            // ---------------- 外观 ----------------
            SectionHeader("外观")
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "深色模式",
                    trailing = {
                        SingleChoiceSegmentedButtonRow {
                            val modes = listOf("系统" to 0, "浅色" to 1, "深色" to 2)
                            modes.forEachIndexed { i, (label, v) ->
                                SegmentedButton(
                                    selected = themeMode == v,
                                    onClick = { onThemeMode(v) },
                                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                                ) { Text(label, fontSize = 12.sp) }
                            }
                        }
                    },
                )
                SettingRow(
                    icon = Icons.Rounded.Palette,
                    title = "主题配色",
                    subtitle = if (styleTheme != 0) "品牌 · ${ThemePacks.byId(styleTheme)?.title}" else when (colorSource) {
                        1 -> if (bgOn) "跟随背景图取色" else "跟随背景图（未设背景）"
                        2 -> "经典品牌蓝"
                        else -> "跟随系统壁纸"
                    },
                    trailing = {
                        SingleChoiceSegmentedButtonRow {
                            val srcs = listOf("壁纸" to 0, "背景图" to 1, "品牌" to 2)
                            srcs.forEachIndexed { i, (label, v) ->
                                SegmentedButton(
                                    // 主题风格归入品牌档: 固定配色, 永不随壁纸/背景变化
                                    selected = if (styleTheme != 0) v == 2 else colorSource == v,
                                    enabled = v != 1 || bgOn,
                                    onClick = {
                                        when (v) {
                                            2 -> showStylePicker = true
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
                    },
                )
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
                if (bgOn) {
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
                    subtitle = "点课程块就能换色",
                )
            }

            // ---------------- 提醒 ----------------
            SectionHeader("提醒")
            SettingCard {
                data class RemState(val sub: String, val err: Boolean, val click: (() -> Unit)?)
                val rem = when {
                    !reminderOn -> RemState("上课前喊你一声", false, null)
                    !notifGranted -> RemState(
                        "先去开通知权限", true,
                    ) { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
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
                            .format(Instant.ofEpochMilli(nextReminderAt).atZone(ZoneId.systemDefault())),
                        false, null,
                    )
                    else -> RemState("已开启", false, null)
                }
                SettingRow(
                    icon = Icons.Rounded.Notifications,
                    title = "课前提醒",
                    subtitle = rem.sub,
                    subtitleColor = if (rem.err) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    trailing = {
                        Switch(checked = reminderOn, onCheckedChange = {
                            if (it && Build.VERSION.SDK_INT >= 33 && !notifGranted) {
                                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            onReminderToggle(it)
                        })
                    },
                    onClick = rem.click,
                )
                if (reminderOn) {
                    SettingRow(title = "提前量", subtitle = "提醒早于上课的分钟数")
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
                    SettingRow(
                        icon = Icons.Rounded.Celebration,
                        title = "下课后小结",
                        subtitle = "最后一节下课时夸夸你",
                        trailing = { Switch(checked = afterClassOn, onCheckedChange = onAfterClassChange) },
                    )
                    SettingRow(
                        icon = Icons.Rounded.Bedtime,
                        title = "早八前夜",
                        subtitle = "前一晚 21:30，明天真有早八才提醒",
                        trailing = { Switch(checked = earlyOn, onCheckedChange = onEarlyChange) },
                    )
                }
                SettingRow(
                    icon = Icons.Rounded.Widgets,
                    title = "桌面小组件",
                    subtitle = "长按桌面空白处添加",
                )
            }

            // ---------------- 数据 ----------------
            SectionHeader("数据")
            SettingCard {
                val genDate = dataset.generatedAt.substringBefore('T').split('-').let { d ->
                    if (d.size == 3) "${d[1].trim().toInt()}月${d[2].trim().toInt()}日" else ""
                }
                SettingRow(
                    title = "检查更新",
                    subtitle = when {
                        checking -> "看看有没有新的…"
                        checkNote != null -> checkNote
                        else -> "$genDate 更新 · ${dataset.classes.size} 个班"
                    },
                    trailing = {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            RowTrailing()
                        }
                    },
                    onClick = if (checking) {
                        null
                    } else {
                        {
                            checking = true
                            checkNote = null
                            onCheckUpdate { r ->
                                checking = false
                                checkNote = r
                            }
                        }
                    },
                )
                if (fromUpdate) {
                    SettingRow(
                        title = "恢复内置数据",
                        titleColor = MaterialTheme.colorScheme.error,
                        trailing = { RowTrailing() },
                        onClick = { showResetConfirm = true },
                    )
                }
            }

            // ---------------- 开发者模式入口 ----------------
            if (devMode) {
                SectionHeader("开发者")
                SettingCard {
                    SettingRow(
                        icon = Icons.Rounded.BugReport,
                        title = "开发者模式",
                        subtitle = "时间旅行 · 测试通知 · 日志与诊断",
                        trailing = { RowTrailing() },
                        onClick = onOpenDev,
                    )
                }
            }

            // ---------------- 关于 ----------------
            SectionHeader("关于")
            var devClicks by remember { mutableIntStateOf(0) }
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 品牌块: 与启动器图标同款 (品牌蓝底 + 白色日历前景), 连点 7 次进开发者模式
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF3B64D8))
                        .combinedClickable(
                            onClick = {
                                if (devMode) return@combinedClickable
                                devClicks++
                                when {
                                    devClicks >= 7 -> {
                                        devClicks = 0
                                        onDevModeChange(true)
                                        Toast.makeText(ctx, "开发者模式已开启", Toast.LENGTH_SHORT).show()
                                    }
                                    devClicks >= 3 ->
                                        Toast.makeText(ctx, "再点 ${7 - devClicks} 次进入开发者模式", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = if (devMode) {
                                {
                                    onDevModeChange(false)
                                    Toast.makeText(ctx, "开发者模式已关闭", Toast.LENGTH_SHORT).show()
                                }
                            } else null,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_fg),
                        contentDescription = "长工课表通",
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "长工课表通",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (devMode) "v$appVersion · 开发者模式" else "v$appVersion",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "让查看课表这件事，不再那么狼狈。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "课表来自教务系统公开课表，只保存在你手机上",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Text(
                    "更新源 ${Uri.parse(url).host ?: "默认地址"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp)).clickable {
                            urlDraft = url
                            showUrlDialog = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
                Text(
                    "源代码已开源 · GitHub",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp)).clickable { openUrl(ctx, OPEN_SOURCE_URL) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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

/** 主题风格选项行: 名称+标语 + 色卡预览圆点 + 选中勾 */
@Composable
private fun StyleOptionRow(
    title: String,
    tagline: String,
    selected: Boolean,
    swatches: List<Color>,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = cs.onSurface)
            Text(tagline, fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 16.sp)
        }
        swatches.forEach { c ->
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), CircleShape),
            )
        }
        Spacer(Modifier.size(12.dp))
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.size(20.dp))
        }
    }
}
