package com.ccsut.kb.util

/**
 * 作息时间 "H:mm" 解析, 全项目唯一定制实现 —— 教务作息字段脏(空串 / "8:20:00" / 非数字),
 * 非法格式返回 null, 调用方按「无时间」处理(跳过该节/该块), 避免 NumberFormatException 崩在组合期或闹钟调度线程。
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
