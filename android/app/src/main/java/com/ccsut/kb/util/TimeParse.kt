package com.ccsut.kb.util

/**
 * 作息时间 "H:mm" 解析 —— 全项目唯一定制实现。
 *
 * 历史上 ScheduleScreen / TodayStatus / Reminder 各自 split(":").toInt(),
 * 教务作息字段脏(空串 / "8:20:00" / 非数字)时 NumberFormatException 会直接崩在
 * 组合期或闹钟调度线程; ClassCache 的防御版又没被这三处复用。
 * 收编到这里统一防御: 非法格式返回 null, 调用方按「无时间」处理(跳过该节/该块)。
 */
object TimeParse {

    /** "8:20" / "08:20" → 当天分钟数; 空串/缺段/多段/非数字/越界返回 null */
    fun minutesOf(t: String): Int? {
        val parts = t.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val m = parts[1].trim().toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }
}
