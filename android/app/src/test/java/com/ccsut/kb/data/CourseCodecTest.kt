package com.ccsut.kb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** CourseCodec 往返编解码: 三个消费方 (数据集/班级快照/个人缓存) 共用同一字段格式 */
class CourseCodecTest {

    @Test
    fun `toJson后fromJson字段无损`() {
        val c = Course(
            kc = "大学英语", teacher = "李老师", room = "B203",
            day = 4, jc = 6, djs = 2, zc = "1-8,10-16",
            ranges = listOf(1..8, 10..16),
            fx = "篮球", jxb = "英语1班", jxbzc = "26计科1,26计科2", type = 1,
        )
        assertEquals(c, CourseCodec.fromJson(CourseCodec.toJson(c)))
    }

    @Test
    fun `缺字段走默认值且djs至少为1`() {
        val c = CourseCodec.fromJson(org.json.JSONObject("""{"kc":"自建课"}"""))
        assertEquals("自建课", c.kc)
        assertEquals(1, c.day)
        assertEquals(1, c.jc)
        assertEquals(1, c.djs)
        assertEquals(emptyList<IntRange>(), c.ranges)
        assertNull(c.editId)   // editId 仅生效视图有值, 不参与序列化
    }

    @Test
    fun `zcRanges区间对解码`() {
        val c = CourseCodec.fromJson(
            org.json.JSONObject("""{"kc":"x","zcRanges":[[1,4],[6,16]]}"""))
        assertEquals(listOf(1..4, 6..16), c.ranges)
    }
}
