package com.ccsut.kb.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 内置数据集解析: 学期元数据 / 作息 / 班级树 / 课程表 */
class DatasetParserTest {

    private val minimal = """
        {"version":3,"generatedAt":"2026-10-01 12:00:00","xnxq":"2026-2027-1",
         "term":{"startDate":"2026-09-07","weeks":20,"weekRanges":[{"range":"9.7-9.13"},{"range":"9.14-9.20"}]},
         "periods":[{"jc":1,"start":"8:20","end":"9:05"},{"jc":2,"start":"9:10","end":"9:55"}],
         "colleges":[{"name":"计算机学院","majors":[{"name":"计算机科学","classes":["c1","c2"]}]}],
         "classes":{"c1":{"bjmc":"2026计科1班","yxmc":"计算机学院","zymc":"计算机科学","sznj":"2026",
           "courses":[{"kc":"高等数学","teacher":"王老师","room":"A101","day":1,"jc":1,"djs":2,
                       "zc":"1-16","zcRanges":[[1,16]],"fx":"","jxb":"","jxbzc":"","type":1}]}}}
    """.trimIndent()

    @Test
    fun `解析学期与班级课程`() {
        val d = DatasetParser.parse(minimal)
        assertEquals(3, d.version)
        assertEquals("2026-2027-1", d.xnxq)
        assertEquals("2026-09-07", d.startDate)
        assertEquals(20, d.weeks)
        assertEquals(listOf("9.7-9.13", "9.14-9.20"), d.weekRanges)
        assertEquals(listOf(Period(1, "8:20", "9:05"), Period(2, "9:10", "9:55")), d.periods)
        val c1 = d.classes["c1"]!!
        assertEquals("2026计科1班", c1.bjmc)
        assertEquals(1, c1.courses.size)
        assertEquals(listOf(1..16), c1.courses[0].ranges)
    }

    @Test
    fun `解析学院专业班级树`() {
        val d = DatasetParser.parse(minimal)
        assertEquals(1, d.colleges.size)
        assertEquals("计算机学院", d.colleges[0].name)
        assertEquals("计算机科学", d.colleges[0].majors[0].name)
        assertEquals(listOf("c1", "c2"), d.colleges[0].majors[0].classIds)
    }

    @Test
    fun `缺省字段给安全默认值`() {
        val d = DatasetParser.parse("""{"term":{},"classes":{}}""")
        assertEquals(0, d.version)
        assertEquals(20, d.weeks)
        assertEquals(emptyList<Period>(), d.periods)
        assertEquals(emptyList<College>(), d.colleges)
    }
}
