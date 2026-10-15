package com.ccsut.kb.ui
import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** 宽屏判定 (M3 Expanded 档, KbSheet 同源): ≥840dp 时面板从右侧滑入半屏侧面板, 否则底部弹层 */
internal fun isWideScreen(config: Configuration): Boolean = config.screenWidthDp >= 840

@Composable
fun KbSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val panelColor = cs.surfaceContainerLow
    val scope = rememberCoroutineScope()

    // 平板横屏适配: 屏宽 ≥840dp (M3 Expanded 档) 时为右侧滑入、只占部分宽度的侧面板, 否则底部弹层
    val config = LocalConfiguration.current
    val side = isWideScreen(config)

    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var panelW by remember { mutableFloatStateOf(0f) }   // 面板宽 px, 布局后才有 (侧滑模式闭合轴)
    var panelH by remember { mutableFloatStateOf(0f) }   // 面板高 px, 布局后才有 (底弹模式闭合轴)
    var drag by remember { mutableFloatStateOf(0f) }     // 沿闭合轴的手势拖动量 px (底弹=向下, 侧滑=向右)
    val axis = if (side) panelW else panelH

    LaunchedEffect(Unit) { shown = true }
    // 0 = 展开, 1 = 完全滑出屏幕; 点遮罩/返回键触发的整板退场动画
    val p by animateFloatAsState(
        targetValue = if (shown) 0f else 1f,
        animationSpec = tween(220),
        label = "sheet",
        finishedListener = { if (it >= 1f) onDismiss() },
    )

    // 松手判定: 沿关闭轴甩出或拉过 28% → 关; 反向甩 → 回; 其余按距离
    fun settle(v: Float) {
        if (closing || axis <= 0f) return
        val close = when {
            v > 900f -> true
            v < -900f -> false
            else -> drag > axis * 0.28f
        }
        if (close) {
            closing = true
            scope.launch {
                animate(drag, axis, initialVelocity = v, animationSpec = tween(200)) { x, _ -> drag = x }
                onDismiss()
            }
        } else {
            scope.launch {
                animate(drag, 0f, initialVelocity = v, animationSpec = spring(stiffness = 1400f)) { x, _ ->
                    drag = x.coerceAtLeast(0f)
                }
            }
        }
    }

    fun close() {
        if (closing) return
        if (drag > 0f) settle(10_000f)   // 已被拖离: 从当前位置直接滑出
        else {
            closing = true
            shown = false
        }
    }

    // 内容滚动 leftover 的唯一接管者: 到顶后下拉驱动面板, 被拉下后上推先归位面板 (仅底弹模式)
    val connection = remember(side) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (side || source != NestedScrollSource.UserInput || drag <= 0f || available.y >= 0f) return Offset.Zero
                val prev = drag
                drag = (drag + available.y).coerceAtLeast(0f)
                return Offset(0f, drag - prev)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (side || source != NestedScrollSource.UserInput || available.y <= 0f || closing) return Offset.Zero
                val prev = drag
                drag = (drag + available.y).coerceAtMost(if (panelH > 0f) panelH else Float.MAX_VALUE)
                return Offset(0f, drag - prev)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (side || drag <= 0f || closing) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    val grabDrag = remember(side) {
        DraggableState { delta ->
            if (!closing) {
                val ax = if (side) panelW else panelH
                drag = (drag + delta).coerceAtLeast(0f).coerceAtMost(if (ax > 0f) ax else Float.MAX_VALUE)
            }
        }
    }
    val shape = if (side) RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
    else RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    // 侧面板宽度: 约占屏宽一半, 封顶 480dp, 保证左侧仍能看到课表内容
    val sideWidth = (config.screenWidthDp * 0.5f).dp.coerceAtMost(480.dp)

    Dialog(
        onDismissRequest = { close() },   // 系统返回走这里
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 遮罩: 覆满全屏拦截对底层的点击, 只响应「点一下关闭」; 随面板滑出同步变淡
            val frac = if (axis > 0f) ((p * axis + drag) / axis).coerceIn(0f, 1f) else p
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.45f * (1f - frac) }
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { close() },
            )
            Box(
                Modifier
                    .align(if (side) Alignment.CenterEnd else Alignment.BottomCenter)
                    .then(
                        if (side) Modifier.fillMaxHeight().width(sideWidth)
                        else Modifier.fillMaxWidth().heightIn(max = config.screenHeightDp.dp * 0.92f)
                    )
                    .imePadding()
                    .onSizeChanged { panelW = it.width.toFloat(); panelH = it.height.toFloat() }
                    .nestedScroll(connection)
                    .graphicsLayer {
                        if (side) translationX = p * size.width + drag
                        else translationY = p * size.height + drag
                    }
                    .shadow(8.dp, shape)
                    .background(panelColor, shape)
                    // 松手兜底: 拖动后低速松手时内容区 scrollable 不派发 onPreFling, 面板会停在
                    // 半路; 统一在指针抬起/取消时补一次松手判定
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) break
                            }
                            if (drag > 0f) settle(0f)
                        }
                    }
                    // 侧面板要通到屏幕顶 (状态栏底下也是面板本体), 内边距必须画在背景之后,
                    // 只把内容推到状态栏以下; 放背景之前会把顶边截在状态栏处露出遮罩缝
                    .then(if (side) Modifier.statusBarsPadding() else Modifier),
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColorFor(panelColor)) {
                    Column {
                        if (side) {
                            // 侧面板固定头: 标题 + 动作 + 收起箭头同行, 内容从头部下方才开始滚动。
                            // 箭头是布局的一部分而非悬浮层, 内容永远不会滚进箭头所在的一行
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(start = 20.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                            ) {
                                if (title != null) {
                                    Text(
                                        title,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                actions?.invoke(this)
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .draggable(
                                            state = grabDrag,
                                            orientation = Orientation.Horizontal,
                                            onDragStopped = { v -> settle(v) },
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) { if (drag > 0f) settle(0f) else close() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "收起",
                                        tint = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            // 底部弹层保留把手条
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .draggable(
                                        state = grabDrag,
                                        orientation = Orientation.Vertical,
                                        onDragStopped = { v -> settle(v) },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp, 4.dp)
                                        .background(cs.outlineVariant.copy(alpha = 0.8f), CircleShape),
                                )
                            }
                            // 底部弹层标题行: 无收起箭头 (下滑/遮罩/返回关闭), 动作靠右
                            if (title != null || actions != null) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 20.dp, end = 12.dp, top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    if (title != null) {
                                        Text(
                                            title,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    actions?.invoke(this)
                                }
                            }
                        }
                        content()
                    }
                }
            }
        }
    }
}

