package com.ccsut.kb

import com.ccsut.kb.data.Course

/** 测试夹具: Course 全字段给默认值, 单测里只覆盖关心的字段 */
fun course(
    kc: String = "高等数学",
    teacher: String = "张老师",
    room: String = "A101",
    day: Int = 1,
    jc: Int = 1,
    djs: Int = 1,
    zc: String = "1-16",
    ranges: List<IntRange> = listOf(1..16),
    fx: String = "",
    jxb: String = "",
    jxbzc: String = "",
    type: Int = 1,
    editId: String? = null,
): Course = Course(
    kc = kc, teacher = teacher, room = room,
    day = day, jc = jc, djs = djs,
    zc = zc, ranges = ranges,
    fx = fx, jxb = jxb, jxbzc = jxbzc,
    type = type, editId = editId,
)
