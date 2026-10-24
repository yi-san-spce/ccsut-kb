package com.ccsut.kb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 源码主站 (GitHub)。引导页与「关于」共用。 */
const val OPEN_SOURCE_URL = "https://github.com/yi-san-spce/ccsut-kb"

// ---- 社区 (关于页「加入社区」卡片) ----

/** QQ 群号 (纯数字)。留空 = 关于页不显示 QQ 群入口。 */
const val QQ_GROUP_UIN = "1129626080"

/** QQ 群名 (入口副标题展示) */
const val QQ_GROUP_NAME = "长工课表通社区"

/** QQ 频道邀请链接。留空 = 不显示 QQ 频道入口。 */
const val QQ_CHANNEL_URL = "https://pd.qq.com/s/9pnez0un5?b=9"

/** QQ 频道名 (入口副标题展示) */
const val QQ_CHANNEL_NAME = "长沙工业学院校园论坛"

/** GitHub Discussions: 想法与问答 */
const val DISCUSSIONS_URL = "$OPEN_SOURCE_URL/discussions"

/** GitHub Issue 分类选择页 (bug / 功能建议 / 课表数据 三套模板直达) */
const val ISSUES_URL = "$OPEN_SOURCE_URL/issues/new/choose"

/** 隐私政策 */
const val PRIVACY_URL = "$OPEN_SOURCE_URL/blob/master/docs/PRIVACY.md"

// ---- 分享课表 · iPhone 导入教程 (分享面板) ----

/** B 站视频教程:课程表 ICS 导入 iOS 系统日历 (6940 播放 / 6 分 23 秒,与 App 导出场景一致) */
const val IOS_ICS_GUIDE_VIDEO_URL = "https://www.bilibili.com/video/BV1k84y1J7vL/"

/** 可直接转发给朋友的导入教程文本 (文字步骤 + 视频链接;帮朋友导出后自动复制进剪贴板) */
const val ICS_GUIDE_SHARE_TEXT = "【长工课表通】课表导入 iPhone 日历教程:\n" +
    "微信收到 .ics 文件后 → 点「用其他应用打开」→ 选「保存到“文件”」→ 打开「文件」长按文件举起不动,\n" +
    "另一只手回主屏打开「日历」,把文件拖到日历上松手 → 点「添加全部」→ 选一个日历确认。\n" +
    "视频教程(B站):$IOS_ICS_GUIDE_VIDEO_URL"

/** 用系统浏览器打开链接; 无浏览器等异常时静默忽略。 */
fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** 分享纯文本 (微信/QQ/邮件选择器)。失败静默 —— 调用方如有需要自行兜底提示。 */
fun shareText(ctx: Context, chooserTitle: String, text: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * 打开 QQ 群资料卡 (需手机装有 QQ)。
 * 无 QQ 时回调 [onFallback] —— 调用方通常复制群号并提示手动搜索。
 */
fun openQQGroup(ctx: Context, uin: String, onFallback: (String) -> Unit) {
    val ok = runCatching {
        ctx.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("mqqapi://card/show_pslcard?src_type=internal&version=1&uin=$uin&card_type=group&source=qrcode"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
    if (!ok) onFallback(uin)
}

/** 复制文本到剪贴板 */
fun copyText(ctx: Context, text: String) {
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("ccsut-kb", text))
    }
}
