package com.ccsut.kb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 数据热更完整性防线: 清单缺 sha256 / 校验不符一律拒绝 (fail-closed);
 * 作息全脏的数据包拒绝套用; GitHub API 资产 digest 提取。
 */
class UpdaterDataGuardTest {

    private fun manifest(sha256: String) = Manifest(
        version = 4, xnxq = "2026-2027-1", file = "dataset_v4.json",
        sha256 = sha256, bytes = 0, generatedAt = "2026-10-23 12:00:00",
        apkVersionCode = null, apkFile = null, apkSha256 = null,
    )

    private val payload = "数据包内容".toByteArray(Charsets.UTF_8)

    @Test
    fun `清单缺sha256时拒绝应用`() {
        // 清单与数据包同源同通道, 缺校验值时唯一防线只剩 TLS —— 必须 fail-closed
        assertNotNull(Updater.dataSha256Error(manifest(""), payload))
        assertNotNull(Updater.dataSha256Error(manifest("   "), payload))
    }

    @Test
    fun `sha256不符时拒绝应用`() {
        assertNotNull(Updater.dataSha256Error(manifest("deadbeef"), payload))
    }

    @Test
    fun `sha256相符时放行`() {
        assertNull(Updater.dataSha256Error(manifest(Updater.sha256Hex(payload)), payload))
    }

    private fun dataset(periods: List<Period>) = Dataset(
        version = 0, generatedAt = "", xnxq = "2026-2027-1", startDate = "2026-09-07",
        weeks = 20, weekRanges = emptyList(), periods = periods,
        colleges = emptyList(), classes = emptyMap(),
    )

    @Test
    fun `作息全部不可解析的数据包判为不可用`() {
        // 教务作息脏格式: 空串/缺段/越界, 全脏会让课表页排版塌掉
        val d = dataset(listOf(Period(1, "", ""), Period(2, "8:20:00", "abc"), Period(3, "25:00", "9:00")))
        assertEquals(false, Repo.periodsUsable(d))
        assertEquals(false, Repo.periodsUsable(dataset(emptyList())))
    }

    @Test
    fun `至少一个节次可解析即判为可用`() {
        val d = dataset(listOf(Period(1, "", ""), Period(2, "8:00", "9:50")))
        assertEquals(true, Repo.periodsUsable(d))
    }

    private fun releaseJson(digest: String?) = org.json.JSONObject(
        buildString {
            append("""{"tag_name":"v1.2.1","published_at":"2026-10-23T10:00:00Z","assets":[""")
            append("""{"name":"ccsut-kb-1.2.1.apk","browser_download_url":"https://github.com/x/y/releases/download/v1.2.1/a.apk","size":1024,"download_count":3""")
            if (digest != null) append(""","digest":"$digest"""")
            append("}]}")
        },
    )

    @Test
    fun `GitHub资产digest字段提取为sha256`() {
        val hex = "a".repeat(64)
        val r = Releases.parseGitHubRelease(releaseJson("sha256:$hex"))
        assertNotNull(r)
        assertEquals(hex, r?.sha256)
    }

    @Test
    fun `GitHub资产无digest或缺字段时sha256为null`() {
        // 旧版 API 响应没有 digest 字段: 维持 null (跳过校验), 不能误用其他字段
        assertNull(Releases.parseGitHubRelease(releaseJson(null))?.sha256)
        // 格式不对 (非64位hex) 不采信
        assertNull(Releases.parseGitHubRelease(releaseJson("sha256:xyz"))?.sha256)
        assertNull(Releases.parseGitHubRelease(releaseJson("md5:" + "a".repeat(32)))?.sha256)
    }
}
