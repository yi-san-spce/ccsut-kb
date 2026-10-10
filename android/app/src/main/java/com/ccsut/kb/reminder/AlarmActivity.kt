package com.ccsut.kb.reminder

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.lifecycleScope
import com.ccsut.kb.ui.KbTheme
import com.ccsut.kb.util.DebugLog
import com.ccsut.kb.widget.WidgetProvider
import com.ccsut.kb.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟模式全屏响铃页: setAlarmClock 直启 (USE_EXACT_ALARM 已授予时豁免后台启动限制),
 * 锁屏之上点亮屏幕; 循环播放系统默认闹钟铃声 (USAGE_ALARM 走闹钟音量) + 波形震动。
 * 响铃不依赖通知权限 —— 通知权限被关/被自动撤销时, 这是课前提醒的保底通路。
 * 「停止响铃」或返回键结束; 结束时重排下一场事件并刷小组件 (不经过 ReminderReceiver, 需自理)。
 */
class AlarmActivity : ComponentActivity() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val ringTimeout = Runnable { stopAndFinish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 锁屏之上点亮屏幕 (Activity 级 API, 替代已废弃的 WindowManager flag)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        startRinging()
        val lead = intent.getIntExtra(ReminderScheduler.EXTRA_LEAD, -1)
        setContent {
            // 响铃场景恒用深色: 早八的闹钟大概率在暗环境里炸响, 不随系统浅色主题
            KbTheme(themeMode = 2) {
                AlarmRingScreen(
                    kc = intent.getStringExtra(ReminderScheduler.EXTRA_KC).orEmpty().ifBlank { "上课啦" },
                    room = intent.getStringExtra(ReminderScheduler.EXTRA_ROOM).orEmpty(),
                    start = intent.getStringExtra(ReminderScheduler.EXTRA_START).orEmpty(),
                    lead = lead,
                    onStop = { stopAndFinish() },
                )
            }
        }
        // 兜底: 用户始终不处理 (手机在包里/教室里来不及看) 最多响 2 分钟自动停
        Handler(Looper.getMainLooper()).postDelayed(ringTimeout, RING_TIMEOUT_MS)
    }

    // singleTop: 响铃中又到期一条闹钟不重放, 保持当前响铃即可
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    private fun startRinging() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(applicationContext, uri)
                isLooping = true
                prepare()
                start()
            }.also { player = it }
        }.onFailure { DebugLog.log("remind", "响铃播放失败: ${it.message}") }
        runCatching {
            vibrator = getSystemService<Vibrator>()
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 600), 0))
        }.onFailure { DebugLog.log("remind", "震动失败: ${it.message}") }
    }

    private fun stopAndFinish() {
        runCatching {
            player?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Handler(Looper.getMainLooper()).removeCallbacks(ringTimeout)
        runCatching { player?.release() }
        runCatching { vibrator?.cancel() }
        player = null
        vibrator = null
        // 响铃页不走 ReminderReceiver: 下一场闹钟/通知在这里重排, 上下课边界顺带刷小组件
        val appCtx = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { ReminderScheduler.reschedule(appCtx) }
            runCatching {
                val mgr = AppWidgetManager.getInstance(appCtx)
                val ids = mgr?.getAppWidgetIds(ComponentName(appCtx, WidgetProvider::class.java))
                if (mgr != null && ids != null && ids.isNotEmpty()) WidgetRenderer.render(appCtx, mgr, ids)
            }
        }
    }

    companion object {
        /** 最长响铃时长: 到点无人处理也自动停, 避免上课后在包里一直响 */
        private const val RING_TIMEOUT_MS = 2 * 60_000L
    }
}

@Composable
private fun AlarmRingScreen(kc: String, room: String, start: String, lead: Int, onStop: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Rounded.Alarm,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(84.dp),
            )
            Spacer(Modifier.height(28.dp))
            Text(
                if (lead > 0) "还有 $lead 分钟上课" else "快上课啦",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                buildString {
                    if (start.isNotBlank()) append("$start · ")
                    append(kc)
                },
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (room.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(room, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(56.dp))
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
            ) {
                Text("停止响铃", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "不处理的话最多响 2 分钟",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
