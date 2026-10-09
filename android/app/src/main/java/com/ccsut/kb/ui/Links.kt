package com.ccsut.kb.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/** 源码主站 (GitHub)。引导页与「关于」共用; Gitee 同步镜像为中国区副站。 */
const val OPEN_SOURCE_URL = "https://github.com/yi-san-spce/ccsut-kb"

/** 用系统浏览器打开链接; 无浏览器等异常时静默忽略。 */
fun openUrl(ctx: Context, url: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
