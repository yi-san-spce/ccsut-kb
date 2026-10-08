package com.ccsut.kb.ui
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ccsut.kb.data.BuildVersion
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun UpdateDialog(
    newVersion: String,
    sizeBytes: Long?,
    notes: List<String>,
    downloading: Boolean,
    progress: Int?,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val cardColor = cs.surfaceContainerHigh
    Dialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColorFor(cardColor)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 340.dp)
                        .background(cardColor, RoundedCornerShape(26.dp))
                        .padding(26.dp),
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(cs.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.SystemUpdateAlt,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("发现新版本", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append("v$newVersion · 当前 v${BuildVersion.NAME}")
                            sizeBytes?.let { append(" · ${"%.1f".format(it / 1048576.0)} MB") }
                        },
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("更新内容", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = cs.primary)
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier
                            .heightIn(max = 230.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (notes.isEmpty()) {
                            Text(
                                "性能优化与问题修复。",
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        } else {
                            notes.forEach { line ->
                                Row(Modifier.padding(vertical = 4.dp)) {
                                    Box(
                                        Modifier
                                            .padding(top = 7.dp)
                                            .size(5.dp)
                                            .background(cs.primary, CircleShape),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(line, fontSize = 14.sp, lineHeight = 20.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onUpdate,
                        enabled = !downloading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (downloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(if (progress == null) "下载中…" else "下载中 $progress%")
                        } else {
                            Text("立即更新", fontSize = 15.sp)
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        enabled = !downloading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("以后再说", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
