package com.ccsut.kb.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.data.BuildVersion
import com.ccsut.kb.data.Channels
import com.ccsut.kb.Prefs
import com.ccsut.kb.data.Mirrors
import com.ccsut.kb.data.ReleaseInfo
import com.ccsut.kb.data.Releases
import com.ccsut.kb.data.UpdateChannels
import java.net.URI

/** 更新说明条目的对勾色: 主题语义色 (深浅两档) */
private fun checkGreen(dark: Boolean) = CourseColors.successColor(dark)

/**
 * 软件更新面板: 版本历史 + 更新说明 + 安装包下载。
 * 高版本「更新到此版本」覆盖安装; 低版本「回退安装」(提示数据可能丢失);
 * 内测版走团队私发的清单链接, 不在公开渠道分发。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    source: Int,
    channel: Int,
    releases: List<ReleaseInfo>?,
    loading: Boolean,
    error: String?,
    downloading: String?,
    progress: Int?,
    cachedVersions: Set<Pair<String, String>>,
    onSourceChange: (Int) -> Unit,
    onChannelChange: (Int) -> Unit,
    onBetaUrlSave: (String) -> Unit,
    onRefresh: () -> Unit,
    onInstall: (ReleaseInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val busy = downloading != null   // 同一时间只允许一个下载任务
    // 开发者「无视版本安装」(DevSheet 开关): 开启后当前版本也可重装
    val devIgnore = remember { Prefs.devIgnoreVersion(ctx) }

    var confirmDowngrade by remember { mutableStateOf<ReleaseInfo?>(null) }
    var showMirrorList by remember { mutableStateOf(false) }
    var pendingChannel by remember { mutableStateOf<Int?>(null) }
    var showBetaDialog by remember { mutableStateOf(false) }
    var betaDraft by remember { mutableStateOf("") }

    val sheetScroll = rememberScrollState()
    KbSheet(
        onDismiss = onDismiss,
        title = "软件更新",
        actions = {
            IconButton(onClick = onRefresh, enabled = !loading && !busy) {
                Icon(Icons.Rounded.Refresh, contentDescription = "检查更新", tint = cs.onSurfaceVariant)
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            SectionHeader("软件源")
            val mirrorHost = remember(source, releases) { Mirrors.statusText(ctx) }
            SettingCard {
                SettingRow(
                    icon = Icons.Rounded.Sync,
                    title = "下载通道",
                    subtitle = when (source) {
                        UpdateChannels.SOURCE_OFFICIAL -> "GitHub Releases · 国际网络"
                        else -> mirrorHost?.let { "镜像 $it · 自动测速选最快" } ?: "自动测速选择最快镜像"
                    },
                )
                // 分段按钮独立成行 (仿「提前量」): 塞进 SettingRow 尾槽会按内容占宽, 把标题挤成竖排
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
                ) {
                    val srcs = listOf(
                        "镜像加速" to UpdateChannels.SOURCE_MIRROR,
                        "官方直连" to UpdateChannels.SOURCE_OFFICIAL,
                    )
                    srcs.forEachIndexed { i, (label, v) ->
                        SegmentedButton(
                            selected = source == v,
                            onClick = { if (!busy) onSourceChange(v) },
                            shape = SegmentedButtonDefaults.itemShape(i, srcs.size),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
                // 镜像清单: 点开看全部候选与当前命中的镜像 (只读; 顺序 = 探测优先级, 远程清单在前)
                if (source == UpdateChannels.SOURCE_MIRROR) {
                    val mirrorList = remember(source, releases) { Mirrors.candidates(ctx) }
                    val activeBase = remember(source, releases) { Mirrors.cached(ctx) }
                    SettingRow(
                        icon = Icons.Rounded.Public,
                        title = "镜像源",
                        subtitle = "共 ${mirrorList.size} 个" + (activeBase?.let {
                            runCatching { URI(it).host }.getOrNull()?.let { h -> " · 当前 $h" }
                        } ?: ""),
                        trailing = { RowTrailing() },
                        onClick = { showMirrorList = !showMirrorList },
                    )
                    if (showMirrorList) {
                        Column(Modifier.padding(start = 62.dp, end = 14.dp, bottom = 10.dp)) {
                            mirrorList.forEach { base ->
                                val host = runCatching { URI(base).host }.getOrNull() ?: base
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                ) {
                                    if (base == activeBase) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = "当前镜像",
                                            tint = checkGreen(CourseColors.isDark(cs)),
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    } else {
                                        Spacer(Modifier.width(23.dp))
                                    }
                                    Text(host, fontSize = 12.sp, color = cs.onSurfaceVariant)
                                }
                            }
                            Text(
                                "顺序即测速优先级，自动选择最快可用；远程清单随版本索引更新",
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                color = cs.onSurfaceVariant.copy(alpha = 0.72f),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            // 卡片间距: 软件源 与 版本通道 两卡隔开
            Spacer(Modifier.height(14.dp))

            // ---- 版本通道: 稳定 / 内测 / 抢先 ----
            val betaUrl = remember(channel, releases) { Channels.betaUrl(ctx) }
            SettingCard {
                SettingRow(
                    icon = Icons.AutoMirrored.Rounded.AltRoute,
                    title = "版本通道",
                    subtitle = when (channel) {
                        Channels.BETA -> betaUrl.takeIf { it.isNotBlank() }
                            ?.let { runCatching { URI(it).host }.getOrNull() ?: it }
                            ?.let { "内测清单 $it" } ?: "未配置内测清单地址"
                        Channels.PREVIEW -> "抢先体验 · 最新但不稳定"
                        else -> "稳定版=验证过的发布 · 抢先版=新特性先行 · 内测版=小范围灰度"
                    },
                )
                // 分段按钮独立成行 (同下载通道, 防尾槽挤压)
                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 10.dp),
                ) {
                    val chs = listOf(
                        "稳定版" to Channels.STABLE,
                        "抢先版" to Channels.PREVIEW,
                        "内测版" to Channels.BETA,
                    )
                    chs.forEachIndexed { i, (label, v) ->
                        SegmentedButton(
                            selected = channel == v,
                            onClick = { if (!busy && v != channel) pendingChannel = v },
                            shape = SegmentedButtonDefaults.itemShape(i, chs.size),
                        ) { Text(label, fontSize = 12.sp) }
                    }
                }
                if (channel == Channels.BETA) {
                    // 内测不走公开渠道: 清单地址由团队单独派发, 链接本身就是访问凭证
                    SettingRow(
                        icon = Icons.Rounded.Link,
                        title = "内测清单地址",
                        subtitle = betaUrl.ifBlank { "未设置，点击填入团队派发的链接" },
                        trailing = { RowTrailing() },
                        onClick = { if (!busy) { betaDraft = betaUrl; showBetaDialog = true } },
                    )
                }
            }

            when {
                loading -> Row(
                    Modifier.fillMaxWidth().padding(vertical = 42.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("正在获取版本信息…", fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
                error != null && (releases == null || releases.isEmpty()) -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 42.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error, fontSize = 13.sp, color = cs.onSurfaceVariant, lineHeight = 19.sp)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onRefresh) { Text("重试") }
                }
                releases != null && releases.isEmpty() -> Text(
                    "这个源还没有可安装的版本",
                    fontSize = 13.sp, color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp),
                    textAlign = TextAlign.Center,
                )
            }

            releases.orEmpty().forEach { r ->
                // 与上方「软件源 → 版本通道」卡片间距统一为 14dp, 三卡间隔一致
                Spacer(Modifier.height(14.dp))
                ReleaseCard(
                    r = r,
                    relation = Releases.relation(r),
                    downloading = downloading,
                    progress = progress,
                    busy = busy,
                    devIgnore = devIgnore,
                    cached = (r.channel to r.versionName) in cachedVersions,
                    onInstall = {
                        if (Releases.relation(r) < 0) confirmDowngrade = r else onInstall(r)
                    },
                )
            }
            if (error != null && !releases.isNullOrEmpty()) {
                // 列表还在展示 (上次缓存), 错误只在顶部提示
                Text(
                    "刷新失败: $error",
                    fontSize = 12.sp, color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    // 通道切换风险确认
    pendingChannel?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingChannel = null },
            title = { Text("切换到${Channels.label(target)}") },
            text = {
                Text(
                    when (target) {
                        Channels.BETA ->
                            "内测版仅供团队与受邀用户，切换后需要填入团队派发的内测清单地址才能获取更新；" +
                                "内测功能未充分测试，随时可以切回稳定版。"
                        Channels.PREVIEW ->
                            "抢先版直接来自开发主线，功能未经验证，可能出现崩溃甚至需要卸载重装（数据会丢失）。确定要用吗？"
                        else ->
                            "切回稳定版后，如果当前装的是内测/抢先版本，需要通过「回退安装」降级，本地数据可能丢失。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingChannel = null; onChannelChange(target) }) { Text("切换") }
            },
            dismissButton = {
                TextButton(onClick = { pendingChannel = null }) { Text("取消") }
            },
        )
    }

    // 内测清单地址填入 (团队私发链接)
    if (showBetaDialog) {
        val ok = betaDraft.trim().startsWith("https://")
        AlertDialog(
            onDismissRequest = { showBetaDialog = false },
            title = { Text("内测清单地址") },
            text = {
                OutlinedTextField(
                    value = betaDraft,
                    onValueChange = { betaDraft = it },
                    singleLine = true,
                    placeholder = { Text("https://…/versions-beta.json") },
                    supportingText = { Text(if (ok) "团队派发的内测清单链接" else "要以 https:// 开头") },
                    isError = !ok,
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = ok,
                    onClick = {
                        showBetaDialog = false
                        onBetaUrlSave(betaDraft.trim())
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showBetaDialog = false }) { Text("取消") }
            },
        )
    }

    // 回退低版本: 数据可能不兼容丢失, 二次确认
    confirmDowngrade?.let { r ->
        AlertDialog(
            onDismissRequest = { confirmDowngrade = null },
            title = { Text("回退到 v${r.versionName}") },
            text = {
                Text(
                    "当前是 v${BuildVersion.DISPLAY}。低版本可能无法兼容新版数据，" +
                        "回退后本地课表编辑记录等数据会丢失。\n" +
                        "如果系统拒绝降级安装，需要先卸载本应用再安装（数据同样会清空）。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDowngrade = null; onInstall(r) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("仍要回退") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDowngrade = null }) { Text("取消") }
            },
        )
    }
}

/** 单个版本的卡片: 版本号 + 日期 + 分段更新说明 + 操作按钮 (更新 / 回退 / 当前版本) */
@Composable
private fun ReleaseCard(
    r: ReleaseInfo,
    relation: Int,
    downloading: String?,
    progress: Int?,
    busy: Boolean,
    devIgnore: Boolean,
    cached: Boolean,
    onInstall: (ReleaseInfo) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cs.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "v${r.versionName}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary,
                )
                // 版本类型徽标: 每个条目都标 (稳定/抢先/内测/DEV) —— 版本号与类型都对上才算当前版本
                Spacer(Modifier.width(8.dp))
                val (bg, fg, tag) = when (r.channel) {
                    Channels.TAG_PREVIEW -> Triple(cs.tertiaryContainer, cs.onTertiaryContainer, "抢先")
                    Channels.TAG_DEV -> Triple(cs.primaryContainer, cs.onPrimaryContainer, "DEV")
                    Channels.TAG_BETA -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, "内测")
                    else -> Triple(cs.surfaceVariant, cs.onSurfaceVariant, "稳定")
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bg,
                ) {
                    Text(
                        tag,
                        fontSize = 11.sp,
                        color = fg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                if (relation == 0) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = cs.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            "当前版本",
                            fontSize = 11.sp,
                            color = cs.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                // 日期 + GitHub 资产下载量 (versions.json/Releases API 提供时展示)
                val meta = listOfNotNull(
                    formatDate(r.date).takeIf { it.isNotEmpty() },
                    r.downloadCount?.let { "%,d 次下载".format(it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, fontSize = 13.sp, color = cs.onSurfaceVariant)
                }
            }

            // 更新说明: 小节标题 + 绿色对勾条目 (与发布页一致)。
            // 条目超过 2 条时默认收起只显示前 2 条, 点「展开全部」看完整文案
            var notesExpanded by remember(r.versionName) { mutableStateOf(false) }
            val totalItems = r.sections.sumOf { it.items.size }
            if (r.sections.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("性能优化与问题修复。", fontSize = 14.sp, lineHeight = 20.sp)
            } else {
                var budget = if (notesExpanded) Int.MAX_VALUE else 2
                r.sections.forEach { sec ->
                    val visible = sec.items.take(budget.coerceAtLeast(0))
                    if (visible.isNotEmpty()) {
                        sec.title?.let { t ->
                            Spacer(Modifier.height(14.dp))
                            Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                        } ?: Spacer(Modifier.height(12.dp))
                        visible.forEach { item ->
                            budget--
                            Row(Modifier.padding(vertical = 5.dp)) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = checkGreen(CourseColors.isDark(cs)),
                                    modifier = Modifier.size(17.dp).padding(top = 1.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(item, fontSize = 14.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
                if (totalItems > 2) {
                    Row(
                        Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { notesExpanded = !notesExpanded }
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (notesExpanded) "收起" else "展开全部 $totalItems 条",
                            fontSize = 13.sp,
                            color = cs.primary,
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            if (notesExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // 操作按钮: 高版本覆盖更新, 低版本回退 (回退确认弹窗在页级);
            // 开发者「无视版本安装」开启时, 当前版本也给出「重装此版本」;
            // 点击后按钮位换成该版本的下载进度条; APK 已在下载目录的版本直接亮「立即安装」
            if ((relation != 0 || devIgnore) && r.fileUrl != null) {
                Spacer(Modifier.height(14.dp))
                if (downloading == r.versionName) {
                    Column(Modifier.fillMaxWidth()) {
                        if (progress == null) {
                            // 服务端没给总长度: 不确定进度
                            LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp))
                        } else {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (progress == null) "正在下载安装包…" else "正在下载 $progress%",
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                } else if (relation > 0) {
                    Button(
                        onClick = { onInstall(r) },
                        enabled = !busy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text(if (cached) "立即安装" else "更新到此版本", fontSize = 15.sp)
                    }
                } else if (relation < 0) {
                    OutlinedButton(
                        onClick = { onInstall(r) },
                        enabled = !busy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text(if (cached) "立即安装" else "回退安装", fontSize = 15.sp, color = cs.onSurfaceVariant)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onInstall(r) },
                        enabled = !busy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text(if (cached) "立即安装" else "重装此版本", fontSize = 15.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** ISO 日期 → 2026年10月4日; 解析失败返回空串 */
private fun formatDate(iso: String): String = runCatching {
    val d = java.time.LocalDate.parse(iso.take(10))
    "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
}.getOrDefault("")
