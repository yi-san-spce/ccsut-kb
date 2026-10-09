package com.ccsut.kb.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 更新清单解析: 版本/数据包/APK 三段字段与 notes 清洗 */
class UpdaterManifestTest {

    @Test
    fun `无apk节点时apk字段为空`() {
        val m = Updater.parseManifest(
            org.json.JSONObject(
                """{"version":4,"xnxq":"2026-2027-1","file":"dataset_v4.json",
                    "sha256":"abc","bytes":1024,"generatedAt":"2026-10-01 12:00:00"}"""
            )
        )
        assertEquals(4, m.version)
        assertEquals("dataset_v4.json", m.file)
        assertEquals("abc", m.sha256)
        assertEquals(1024L, m.bytes)
        assertEquals(null, m.apkVersionCode)
        assertEquals(emptyList<String>(), m.apkNotes)
    }

    @Test
    fun `apk节点完整解析且notes去空白`() {
        val m = Updater.parseManifest(
            org.json.JSONObject(
                """{"version":3,"xnxq":"x","file":"d.json","sha256":"a","bytes":1,"generatedAt":"g",
                    "apk":{"versionCode":36,"versionName":"2.12.0","file":"ccsut-kb-2.12.0.apk",
                           "sha256":"deadbeef","bytes":2998092,"notes":["第一条","  ","第二条"]}}"""
            )
        )
        assertEquals(36, m.apkVersionCode)
        assertEquals("2.12.0", m.apkVersionName)
        assertEquals("ccsut-kb-2.12.0.apk", m.apkFile)
        assertEquals("deadbeef", m.apkSha256)
        assertEquals(2998092L, m.apkBytes)
        assertEquals(listOf("第一条", "第二条"), m.apkNotes)
    }

    @Test
    fun `resolve相对地址拼接base`() {
        assertEquals("https://host/dir/d.json", Updater.resolve("https://host/dir", "d.json"))
    }

    @Test
    fun `resolve放行https绝对地址`() {
        assertEquals("https://cdn.example.com/d.json", Updater.resolve("https://host", "https://cdn.example.com/d.json"))
    }

    @Test
    fun `resolve拒绝http明文地址`() {
        // 清单本身经 https 拉取, 但清单内容可被改写 —— file 字段给 http 不得降级下载
        var thrown: Throwable? = null
        try {
            Updater.resolve("https://host", "http://evil.example.com/d.json")
        } catch (t: Throwable) {
            thrown = t
        }
        assertEquals(true, thrown != null)
    }
}
