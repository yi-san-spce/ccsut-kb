package com.ccsut.kb.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.data.Cls
import com.ccsut.kb.data.Dataset

@Composable
fun ChooseClassScreen(
    dataset: Dataset,
    currentId: String?,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // rememberSaveable: 旋转/进程重建后学院折叠展开态不丢 (Set 为 Serializable 可存 Bundle)
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }

    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("选择班级", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("搜索班级 / 专业 / 学院") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        LazyColumn(Modifier.fillMaxSize().padding(top = 4.dp)) {
            val q = query.trim()
            if (q.isEmpty()) {
                dataset.colleges.forEach { college ->
                    val key = "yx:${college.name}"
                    val classCount = college.majors.sumOf { it.classIds.size }
                    item(key = key) {
                        CollegeHeader(
                            name = college.name,
                            count = classCount,
                            expanded = key in expanded,
                            onToggle = {
                                expanded = if (key in expanded) expanded - key else expanded + key
                            },
                        )
                    }
                    if (key in expanded) {
                        college.majors.forEach { major ->
                            major.classIds.forEach { id ->
                                dataset.classes[id]?.let { cls ->
                                    item(key = id) { ClassRow(cls, id == currentId, showCollege = false) { onPick(id) } }
                                }
                            }
                        }
                    }
                }
            } else {
                val flat = dataset.classes.entries
                    .filter { (_, c) ->
                        c.bjmc.contains(q) || c.zymc.contains(q) || c.yxmc.contains(q)
                    }
                    .sortedWith(compareBy({ it.value.yxmc }, { it.value.bjmc }))
                if (flat.isEmpty()) {
                    item {
                        Text(
                            "没有找到匹配的班级",
                            Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                items(flat, key = { it.key }) { (id, cls) ->
                    ClassRow(cls, id == currentId) { onPick(id) }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CollegeHeader(name: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            Modifier.weight(1f),
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "$count 个班",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 箭头常驻单图标: 展开向下, 收起旋转 -90° 朝右, 转向 200ms
        val arrowRotation by animateFloatAsState(
            targetValue = if (expanded) 0f else -90f,
            animationSpec = tween(200),
            label = "arrow",
        )
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
        )
    }
}

@Composable
private fun ClassRow(cls: Cls, current: Boolean, showCollege: Boolean = true, onClick: () -> Unit) {
    Surface(
        color = if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            // 比学院行 (start 20dp) 深一级缩进, 层级靠位置不只靠字重
            .padding(start = 26.dp, top = 2.dp, end = 14.dp, bottom = 2.dp)
            .animateContentSize()
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(cls.bjmc, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                // 学院列表展开后父级已示学院名, 副标题只留 专业·年级; 搜索模式无父级, 保留学院名
                Text(
                    (if (showCollege) "${cls.yxmc} · " else "") + cls.zymc +
                        if (cls.sznj.length == 4) " · ${cls.sznj}级" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 当前班级勾标: 换选时旧勾淡出缩小、新勾淡入放大
            AnimatedVisibility(
                visible = current,
                enter = fadeIn(tween(150)) + scaleIn(tween(150)),
                exit = fadeOut(tween(150)) + scaleOut(tween(150)),
            ) {
                Icon(
                    Icons.Filled.Check, contentDescription = "当前班级",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
