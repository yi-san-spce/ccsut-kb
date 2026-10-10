package com.ccsut.kb.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 源码主站 (GitHub)。引导页与「关于」共用。 */
const val OPEN_SOURCE_URL = "https://github.com/yi-san-spce/ccsut-kb"

/** 中国区镜像 (Gitee), GitHub 直连困难时的一键后备通道。 */
const val OPEN_SOURCE_MIRROR_URL = "https://gitee.com/yisanspce/ccsut-kb"

// ---------------- 社区 (关于页「加入社区」卡片) ----------------

/** QQ 群号 (纯数字)。留空 = 关于页不显示 QQ 群入口。 */
const val QQ_GROUP_UIN = "1129626080"

/** QQ 群名 (入口副标题展示) */
const val QQ_GROUP_NAME = "长工课程通社区"

/** QQ 频道邀请链接。留空 = 不显示 QQ 频道入口。 */
const val QQ_CHANNEL_URL = "https://pd.qq.com/s/9pnez0un5?b=9"

/** QQ 频道名 (入口副标题展示) */
const val QQ_CHANNEL_NAME = "长沙工业学院校园论坛"

/** GitHub Discussions: 想法与问答 (Gitee 无对应物) */
const val DISCUSSIONS_URL = "$OPEN_SOURCE_URL/discussions"

/** GitHub Issue 分类选择页 (bug / 功能建议 / 课表数据 三套模板直达) */
const val ISSUES_URL = "$OPEN_SOURCE_URL/issues/new/choose"

/** Gitee Issue 页 (国内直连; 无分类模板) */
const val ISSUES_GITEE_URL = "$OPEN_SOURCE_MIRROR_URL/issues"

/** 隐私政策 */
const val PRIVACY_URL = "$OPEN_SOURCE_URL/blob/master/PRIVACY.md"

/** 更新日志 */
const val CHANGELOG_URL = "$OPEN_SOURCE_URL/blob/master/CHANGELOG.md"

/** 用系统浏览器打开链接; 无浏览器等异常时静默忽略。 */
fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
