package com.ccsut.kb.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.ccsut.kb.Prefs
import com.materialkolor.ktx.themeColors
import java.io.File

/** 课表背景图: 选择时压缩转存到 filesDir/bg.jpg, 避免反复解码大图 */
object BgStore {

    private const val NAME = "bg.jpg"
    private const val MAX_DIM = 1600

    fun file(ctx: Context): File = File(ctx.filesDir, NAME)

    /** 从相册 URI 压缩转存; 成功后自动置 bgOn */
    fun saveFromUri(ctx: Context, uri: Uri): Boolean {
        fun fail(why: String): Boolean {
            android.util.Log.e("kb-bg", "saveFromUri 失败: $why")
            return false
        }
        android.util.Log.i("kb-bg", "picked uri=$uri")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val ins = ctx.contentResolver.openInputStream(uri)
        if (ins == null) {
            // 部分选择器条目 openInputStream 会返回 null, 退而尝试显式打开 fd。
            // AutoCloseInputStream: 流关闭即释放 pfd, 且两次解码各自独立打开 (复用已关 fd 必然失败)
            return runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(
                    ctx.contentResolver.openFileDescriptor(uri, "r")
                        ?: return fail("openInputStream=null 且 pfd=null type=${ctx.contentResolver.getType(uri)}"),
                ).use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0) return fail("pfd 路径 bounds=${bounds.outWidth}x${bounds.outHeight}")
                decodeAndSave(ctx, {
                    val p2 = ctx.contentResolver.openFileDescriptor(uri, "r")
                    if (p2 == null) null else ParcelFileDescriptor.AutoCloseInputStream(p2)
                }, bounds)
            }.getOrElse { fail("pfd 路径异常: $it") }
        }
        ins.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return fail("bounds=${bounds.outWidth}x${bounds.outHeight} type=${runCatching { ctx.contentResolver.getType(uri) }.getOrNull()}")
        }
        return decodeAndSave(ctx, { ctx.contentResolver.openInputStream(uri) }, bounds)
    }

    private inline fun decodeAndSave(ctx: Context, open: () -> java.io.InputStream?, bounds: BitmapFactory.Options): Boolean {
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2
        val bmp = open()?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: run {
            android.util.Log.e("kb-bg", "decodeAndSave: 二次打开流为 null")
            return false
        }
        return runCatching {
            file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            Prefs.setBgOn(ctx, true)
            true
        }.getOrElse {
            android.util.Log.e("kb-bg", "compress/write 失败", it)
            false
        }.also { bmp.recycle() }
    }

    /** 解码为不超过 maxDim 的位图, 供 Compose 显示 */
    fun decode(path: String, maxDim: Int = MAX_DIM): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        return runCatching {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    fun delete(ctx: Context) {
        file(ctx).delete()
        Prefs.setBgOn(ctx, false)
    }

    /**
     * 从背景图提取主题种子色 (与系统壁纸取色同一套 HCT 量化打分算法)。
     * IO 线程调用; 失败或无背景图返回 0。
     */
    fun extractSeed(ctx: Context): Int {
        if (!file(ctx).exists()) return 0
        val bmp = decode(file(ctx).absolutePath, 128) ?: return 0
        return runCatching {
            bmp.asImageBitmap().themeColors(fallback = Color(0xFF3B64D8)).first().toArgb()
        }.onFailure {
            android.util.Log.w("kb-bg", "extractSeed 失败", it)
        }.getOrDefault(0).also { bmp.recycle() }
    }
}
