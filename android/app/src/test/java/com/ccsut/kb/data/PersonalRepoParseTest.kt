package com.ccsut.kb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** PersonalRepo 的教务响应解析 (HTML 隐藏域 / zclist / sdpkkbList), 样例均化名 */
class PersonalRepoParseTest {

    @Test
    fun `parseZcstr离散周次合并`() {
        val (text, ranges) = PersonalRepo.parseZcstr("11,13,14,15")
        assertEquals("11,13-15", text)
        assertEquals(listOf(11..11, 13..15), ranges)
    }

    @Test
    fun `parseZcstr脏数据过滤`() {
        // 非数字段忽略 / 超出1..30忽略 / 去重
        val (text, ranges) = PersonalRepo.parseZcstr("abc,0,31,3,3,1")
        assertEquals("1,3", text)
        assertEquals(listOf(1..1, 3..3), ranges)
        assertEquals("" to emptyList<IntRange>(), PersonalRepo.parseZcstr(""))
    }

    @Test
    fun `parseKbPage取隐藏域与姓名`() {
        val html = """
            <input type="hidden" id="xhid" value="WGTESTTOKEN123">
            <input type="hidden" id="xnxq" value="2026-2027-1">
            <input type="hidden" id="xqdm" value="01">
            <title>2026-2027学年第1学期测试同学(化名)的课表</title>
        """.trimIndent()
        val q = PersonalRepo.parseKbPage(html)!!
        assertEquals("WGTESTTOKEN123", q.xhid)
        assertEquals("2026-2027-1", q.xnxq)
        assertEquals("01", q.campus)
        assertEquals("测试同学(化名)", q.name)
    }

    @Test
    fun `parseKbPage缺xhid返回null且校区缺省01`() {
        assertNull(PersonalRepo.parseKbPage("<html>别的页面</html>"))
        val q = PersonalRepo.parseKbPage(
            """<input type="hidden" id="xhid" value="X"><input type="hidden" id="xnxq" value="2026-2027-1">""")
        assertEquals("01", q!!.campus)
    }

    @Test
    fun `parseZclist取首周日期周数与作息`() {
        val json = """
            {"ret":0,"data":{
              "zclist":[{"minrq":"2026-09-07 00:00:00"},{"minrq":"2026-09-14 00:00:00"}],
              "jcsjszList":[{"jc":1,"kssj":"8:20","jssj":"9:05"},{"jc":2,"kssj":"9:10","jssj":"9:55"}]
            }}
        """.trimIndent()
        val (start, weeks, periods) = PersonalRepo.parseZclist(json)
        assertEquals("2026-09-07", start)
        assertEquals(2, weeks)
        assertEquals(listOf(Period(1, "8:20", "9:05"), Period(2, "9:10", "9:55")), periods)
    }

    @Test
    fun `parseZclist非JSON或结构缺失安全回落`() {
        assertEquals("" to 0, PersonalRepo.parseZclist("not json").let { it.first to it.second })
        assertEquals(0, PersonalRepo.parseZclist("""{"ret":1}""").third.size)
    }

    @Test
    fun `parseSdpkkb剥HTML标签并规范字段`() {
        val json = """
            {"ret":0,"data":[
              {"kcmc":"<a>大学物理</a>&nbsp;","tmc":"李老师","croommc":"实验楼 302",
               "xingqi":3,"djc":2,"zcstr":"1,2,3,5"},
              {"kcmc":"空周次课","zcstr":""},
              {"kcmc":"周次全脏","zcstr":"abc"},
              {"kcmc":"越界字段","xingqi":9,"djc":99,"zcstr":"2,4,6"}
            ]}
        """.trimIndent()
        val out = PersonalRepo.parseSdpkkb(json)
        assertEquals(2, out.size)                 // 空周次/脏周次的记录被跳过
        assertEquals("大学物理", out[0].kc)
        assertEquals(3, out[0].day)
        assertEquals(2, out[0].jc)
        assertEquals(1, out[0].djs)               // 每节一行, 连堂交给 Merger
        assertEquals(listOf(1..3, 5..5), out[0].ranges)
        assertEquals("1-3,5", out[0].zc)
        assertEquals(7, out[1].day)               // 越界夹回 1..7 / 1..15
        assertEquals(15, out[1].jc)
        assertEquals(listOf(2..2, 4..4, 6..6), out[1].ranges)
    }

    @Test
    fun `guessXnxq按月份推算学期`() {
        assertEquals("2026-2027-1", PersonalRepo.guessXnxq(LocalDate.of(2026, 10, 9)))  // 8月及以后
        assertEquals("2026-2027-1", PersonalRepo.guessXnxq(LocalDate.of(2026, 8, 1)))
        assertEquals("2025-2026-2", PersonalRepo.guessXnxq(LocalDate.of(2026, 3, 1)))
        assertEquals("2025-2026-1", PersonalRepo.guessXnxq(LocalDate.of(2026, 1, 15)))
    }
}
