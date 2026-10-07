package com.ccsut.kb.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ccsut.kb.Prefs
import com.ccsut.kb.data.CasClient
import com.ccsut.kb.data.PersonalRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 教务账号登录页 (手机号/学号 + 短信验证码, 走 CAS 统一身份认证)。
 * 登录成功立即拉取个人课表并落缓存; Cookie 只在内存, 会话失效后需重新验证码登录。
 *
 * 打开时先探测 CAS 会话: 若登录状态仍有效 (上次登录的 CAS TGC 还在), 免短信直接拉课表。
 */
@Composable
fun LoginScreen(
    initialAccount: String,
    onDismiss: () -> Unit,
    onSuccess: (studentName: String) -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    var account by remember { mutableStateOf(initialAccount) }
    var code by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }    // 非 null = 进行中的文案
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }    // 成功提示(如"验证码已发送")
    var execution by remember { mutableStateOf<String?>(null) }
    var probing by remember { mutableStateOf(true) }          // 首次 CAS 状态探测中

    BackHandler(enabled = busy == null) { onDismiss() }

    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    fun friendly(e: Throwable): String = when (e) {
        is CasClient.CasException -> e.message ?: "登录失败，请稍后再试"
        else -> "网络错误: ${e.message ?: e.javaClass.simpleName}"
    }

    // 拉取个人课表并收尾 (登录成功或免短信直连共用)
    fun fetchAndFinish(acct: String) {
        scope.launch {
            busy = "正在拉取个人课表…"
            error = null
            try {
                val data = withContext(Dispatchers.IO) { PersonalRepo.fetchAll(ctx, acct) }
                Prefs.setCasAccount(ctx, acct)
                Prefs.setStudentName(ctx, data.studentName)
                Prefs.setLastSyncAt(ctx, data.syncedAt)
                onSuccess(data.studentName)
            } catch (e: Throwable) {
                error = friendly(e)
            } finally {
                busy = null
            }
        }
    }

    // 打开即探测: CAS 会话有效 → 免短信直接拉课表; 否则拿到 execution 等用户发码
    LaunchedEffect(Unit) {
        busy = "正在检查登录状态…"
        try {
            val exec = withContext(Dispatchers.IO) { CasClient.openLoginPage() }
            if (exec == null) {
                fetchAndFinish(account.trim().ifBlank { Prefs.casAccount(ctx) })
            } else {
                execution = exec
            }
        } catch (e: Throwable) {
            error = friendly(e)
        } finally {
            probing = false
            if (busy == "正在检查登录状态…") busy = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (busy == null) onDismiss() }, enabled = busy == null) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = cs.onSurfaceVariant)
            }
            Text("登录教务账号", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(52.dp).background(cs.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = cs.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "用统一身份认证的手机号或学号登录，\n拉取你的选课、重修等完整个人课表。",
                fontSize = 14.sp, color = cs.onSurfaceVariant, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(22.dp))

            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("手机号 / 学号", fontSize = 14.sp) },
                singleLine = true,
                enabled = busy == null,
                shape = RoundedCornerShape(14.dp),
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("短信验证码", fontSize = 14.sp) },
                    singleLine = true,
                    enabled = busy == null,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        val acct = account.trim()
                        if (acct.isEmpty()) {
                            error = "先填写手机号或学号"
                            return@Button
                        }
                        if (execution == null) return@Button   // 探测未完成
                        scope.launch {
                            busy = "正在发送验证码…"
                            error = null
                            note = null
                            try {
                                val msg = withContext(Dispatchers.IO) { CasClient.sendSms(acct) }
                                countdown = 60
                                note = msg
                            } catch (e: Throwable) {
                                error = friendly(e)
                            } finally {
                                busy = null
                            }
                        }
                    },
                    enabled = busy == null && countdown == 0 && execution != null,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        when {
                            busy == "正在发送验证码…" -> "发送中"
                            countdown > 0 -> "${countdown}s"
                            else -> "获取验证码"
                        },
                        fontSize = 14.sp,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val acct = account.trim()
                    val cd = code.trim()
                    if (cd.length != 6) {
                        error = "请输入 6 位短信验证码"
                        return@Button
                    }
                    scope.launch {
                        busy = "正在登录…"
                        error = null
                        note = null
                        try {
                            withContext(Dispatchers.IO) {
                                // execution 可能因超时失效: 为空则重取; 重取返回 null = CAS 已直接放行
                                val exec = execution ?: CasClient.openLoginPage()
                                if (exec != null) {
                                    execution = exec
                                    CasClient.submitCode(acct, cd, exec)
                                }
                            }
                            fetchAndFinish(acct)
                        } catch (e: Throwable) {
                            execution = null   // execution 一次性: 失败后强制重新取
                            error = friendly(e)
                            busy = null
                        }
                    }
                },
                enabled = busy == null && !probing,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy != null) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    busy ?: "登录",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }

            error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, fontSize = 13.sp, color = cs.error, lineHeight = 19.sp)
            }
            if (error == null) note?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, fontSize = 13.sp, color = cs.primary, lineHeight = 19.sp)
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Sms, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "验证码只用于本次登录；登录后立即拉取并保存在你手机上，账号密码不经手、不存储。",
                    fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 18.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "教务会话有时效：显示个人课表不需要一直在线，之后刷新课表才需要重新验证码登录。" +
                    "如果提示「账号已在线」，说明账号刚在别处登录或退出，等约 3 分钟再试即可。",
                fontSize = 12.sp, color = cs.onSurfaceVariant, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
