package com.ccsut.kb.ui
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun KbSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val panelColor = cs.surfaceContainerLow
    val scope = rememberCoroutineScope()

    var shown by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var panelH by remember { mutableFloatStateOf(0f) }   // 面板高 px, 布局后才有
    var dragY by remember { mutableFloatStateOf(0f) }    // 手势下拉量 px

    LaunchedEffect(Unit) { shown = true }
    // 0 = 展开, 1 = 完全滑出屏幕; 点遮罩/返回键触发的整板退场动画
    val p by animateFloatAsState(
        targetValue = if (shown) 0f else 1f,
        animationSpec = tween(220),
        label = "sheet",
        finishedListener = { if (it >= 1f) onDismiss() },
    )

    // 松手判定: 拉过面板 28% 或向下甩 → 关; 向上甩 → 回; 其余按距离
    fun settle(vy: Float) {
        if (closing || panelH <= 0f) return
        val close = when {
            vy > 900f -> true
            vy < -900f -> false
            else -> dragY > panelH * 0.28f
        }
        if (close) {
            closing = true
            scope.launch {
                animate(dragY, panelH, initialVelocity = vy, animationSpec = tween(200)) { v, _ -> dragY = v }
                onDismiss()
            }
        } else {
            scope.launch {
                animate(dragY, 0f, initialVelocity = vy, animationSpec = spring(stiffness = 1400f)) { v, _ ->
                    dragY = v.coerceAtLeast(0f)
                }
            }
        }
    }

    fun close() {
        if (closing) return
        if (dragY > 0f) settle(10_000f)   // 已被拖离: 从当前位置直接滑出
        else {
            closing = true
            shown = false
        }
    }

    // 内容滚动 leftover 的唯一接管者: 到顶后下拉驱动面板, 被拉下后上推先归位面板
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || dragY <= 0f || available.y >= 0f) return Offset.Zero
                val prev = dragY
                dragY = (dragY + available.y).coerceAtLeast(0f)
                return Offset(0f, dragY - prev)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f || closing) return Offset.Zero
                val prev = dragY
                dragY = (dragY + available.y).coerceAtMost(if (panelH > 0f) panelH else Float.MAX_VALUE)
                return Offset(0f, dragY - prev)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragY <= 0f || closing) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    val grabDrag = rememberDraggableState { delta ->
        if (!closing) dragY = (dragY + delta).coerceAtLeast(0f).coerceAtMost(if (panelH > 0f) panelH else Float.MAX_VALUE)
    }
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Dialog(
        onDismissRequest = { close() },   // 系统返回走这里
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            // 遮罩: 覆满全屏拦截对底层的点击, 只响应「点一下关闭」; 随面板滑出同步变淡
            val frac = if (panelH > 0f) ((p * panelH + dragY) / panelH).coerceIn(0f, 1f) else p
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
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.92f)
                    .imePadding()
                    .onSizeChanged { panelH = it.height.toFloat() }
                    .nestedScroll(connection)
                    .graphicsLayer { translationY = p * size.height + dragY }
                    .shadow(8.dp, shape)
                    .background(panelColor, shape),
            ) {
                // 顶部把手: 整条可拖, 无滚动内容的弹层(如课程详情)靠它下拉关闭
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .draggable(
                            state = grabDrag,
                            orientation = Orientation.Vertical,
                            onDragStopped = { vy -> settle(vy) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(40.dp, 4.dp)
                            .background(cs.outlineVariant.copy(alpha = 0.8f), CircleShape),
                    )
                }
                CompositionLocalProvider(LocalContentColor provides contentColorFor(panelColor)) {
                    content()
                }
            }
        }
    }
}

/** 更新弹窗: 居中卡片, 新版本号 + 更新内容列表 + 下载进度, 启动自动检查/手动检查共用 */
