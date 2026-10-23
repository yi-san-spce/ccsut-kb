package com.ccsut.kb.data

import com.ccsut.kb.course
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ICS 日历导出: 周次折叠/连堂合并/转义/CRLF/脏数据防御 */
class IcsTest {

    private val periods = listOf(Period(1, "8:00", "8:45"), Period(2, "8:55", "9:40"))

    private fun src(
        courses: List<Course>,
        title: String = "测试课表",
        weeks: Int = 6,
        startDate: String = "2026-09-07",
        uidSeed: String = "2026-2027-1",
    ) = Ics.Source(title = title, courses = courses, periods = periods, startDate = startDate, weeks = weeks, uidSeed = uidSeed)

    @Test
    fun `连续周折叠成两条VEVENT且RRULE计数正确`() {
        // 第 1-3 周 + 第 5-6 周 (第 4 周放假): 折叠为两条 VEVENT, 中间断档不进 RRULE
        val c = course(jc = 1, day = 1, zc = "1-3,5-6", ranges = listOf(1..3, 5..6))
        val out = Ics.build(src(listOf(c)))!!
        assertEquals(2, Regex("BEGIN:VEVENT").findAll(out).count())
        assertTrue(out.contains("RRULE:FREQ=WEEKLY;COUNT=3"))
        assertTrue(out.contains("RRULE:FREQ=WEEKLY;COUNT=2"))
        // 第 1 周周一 = startDate 本身 2026-09-07 08:00; 第 5 周周一 = +28 天 = 2026-10-05
        assertTrue(out.contains("DTSTART;TZID=Asia/Shanghai:20260907T080000"))
        assertTrue(out.contains("DTSTART;TZID=Asia/Shanghai:20261005T080000"))
    }

    @Test
    fun `连堂两节合并为一条VEVENT且跨到末节结束时刻`() {
        val a = course(jc = 1, day = 2, zc = "1-2", ranges = listOf(1..2))
        val b = course(jc = 2, day = 2, zc = "1-2", ranges = listOf(1..2), kc = "高等数学")
        val out = Ics.build(src(listOf(a, b)))!!
        assertEquals(1, Regex("BEGIN:VEVENT").findAll(out).count())
        assertTrue(out.contains("DTSTART;TZID=Asia/Shanghai:20260908T080000"))
        assertTrue(out.contains("DTEND;TZID=Asia/Shanghai:20260908T094000"))
        assertTrue(out.contains("RRULE:FREQ=WEEKLY;COUNT=2"))
    }

    @Test
    fun `文本转义与CRLF行尾`() {
        val c = course(kc = "高等数学(一),下;测试", jc = 1, day = 1, zc = "1-2", ranges = listOf(1..2))
        val out = Ics.build(src(listOf(c), title = "我的,课表"))!!
        assertTrue(out.contains("SUMMARY:高等数学(一)\\,下\\;测试"))
        assertTrue(out.contains("X-WR-CALNAME:我的\\,课表"))
        // 字段间一律 CRLF, 不允许裸 \n 行尾
        assertFalse(Regex("(?<!\r)\n").containsMatchIn(out))
    }

    @Test
    fun `描述带教师与周次原文`() {
        val c = course(teacher = "张老师", jc = 1, day = 3, zc = "1-4", ranges = listOf(1..4))
        val out = Ics.build(src(listOf(c)))!!
        assertTrue(out.contains("DESCRIPTION:教师: 张老师\\n周次: 1-4"))
        assertTrue(out.contains("LOCATION:A101"))
    }

    @Test
    fun `脏节次与空课程防御`() {
        // 节次时间表里没有 jc=5: 该课无法定时刻, 整体跳过
        val c = course(jc = 5, day = 1, zc = "1-2", ranges = listOf(1..2))
        assertNull(Ics.build(src(listOf(c))))
        // 全空课表 → null (上层不生成空文件)
        assertNull(Ics.build(src(emptyList())))
    }

    @Test
    fun `UID确定性且互不冲突`() {
        val a = course(kc = "课程A", jc = 1, day = 1, zc = "1-2", ranges = listOf(1..2))
        val b = course(kc = "课程B", jc = 2, day = 1, zc = "1-2", ranges = listOf(1..2))
        val out = Ics.build(src(listOf(a, b)))!!
        val uids = Regex("UID:(.+)@ccsut-kb").findAll(out).map { it.groupValues[1] }.toList()
        assertEquals(2, uids.size)
        assertEquals(uids.size, uids.toSet().size)
        assertTrue(uids.all { it.startsWith("2026-2027-1-") })
        // 确定性: 同输入重跑 UID 不变 (重导入不产生重复日历事件的基础)
        val again = Ics.build(src(listOf(a, b)))!!
        assertEquals(out, again)
    }

    @Test
    fun `日历头含时区与产品标识`() {
        val out = Ics.build(src(listOf(course(jc = 1, day = 1, zc = "1-2", ranges = listOf(1..2)))))!!
        assertTrue(out.startsWith("BEGIN:VCALENDAR\r\n"))
        assertTrue(out.contains("PRODID:-//ccsut-kb//长工课表通//CN"))
        assertTrue(out.contains("TZID:Asia/Shanghai"))
        assertTrue(out.trimEnd().endsWith("END:VCALENDAR"))
    }
}
