package com.ccsut.kb.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 软件更新: GitHub Releases / versions.json 解析, 语义化版本比较, 镜像前缀, Release 正文分段 */
class ReleasesTest {

    // ---- 版本比较 ----

    @Test
    fun `版本比较_逐段比大小`() {
        assertEquals(-1, Releases.compareVersion("1.0.0", "1.0.1"))
        assertEquals(1, Releases.compareVersion("1.2.0", "1.1.9"))
        assertEquals(0, Releases.compareVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun `版本比较_忽略v前缀与位数不足`() {
        assertEquals(-1, Releases.compareVersion("v1.0", "1.0.1"))
        assertEquals(1, Releases.compareVersion("V2.0", "v1.9.9"))
        assertEquals(0, Releases.compareVersion("v1.0.0", "1.0.0"))
    }

    @Test
    fun `版本比较_预发布低于正式版且序号递增`() {
        // 同主版本内: 抢先 < 内测 < 稳定, 通道内按序号递增 (三通道安装不倒挂的基础)
        assertEquals(-1, Releases.compareVersion("1.3.0-pre.20261013", "1.3.0-beta.1"))
        assertEquals(-1, Releases.compareVersion("1.3.0-beta.1", "1.3.0-beta.2"))
        assertEquals(-1, Releases.compareVersion("1.3.0-beta.2", "1.3.0"))
        assertEquals(1, Releases.compareVersion("1.3.0", "1.3.0-beta.2"))
        assertEquals(0, Releases.compareVersion("1.3.0-beta.1", "1.3.0-beta.1"))
    }

    @Test
    fun `版本比较_跨主版本预发布不影响序比较`() {
        assertEquals(-1, Releases.compareVersion("1.3.0-pre.20261013", "1.4.0"))
        assertEquals(1, Releases.compareVersion("1.4.0-pre.20261013", "1.3.0"))
        // 非数字尾缀取数字前缀 (未知格式保守按稳定比较)
        assertEquals(1, Releases.compareVersion("1.0.1x", "1.0.0"))
    }

    @Test
    fun `版本比较_stable与dev后缀`() {
        // 同主版本内: 抢先 < 内测 < 稳定 < 开发者; 历史裸版本号 == 稳定
        assertEquals(-1, Releases.compareVersion("1.3.0-stable", "1.3.0-dev.2"))
        assertEquals(1, Releases.compareVersion("1.3.0-stable", "1.3.0-beta.2"))
        assertEquals(0, Releases.compareVersion("1.3.0", "1.3.0-stable"))
        assertEquals(1, Releases.compareVersion("1.4.0-stable", "1.3.0-dev.9"))
    }

    @Test
    fun `relation_有versionCode走code比较`() {
        // 非开发者版当前版本 (stable), 条目类型一致
        assertEquals(1, Releases.relationTo(ReleaseInfo(versionName = "0.0.1", versionCode = 41, channel = Channels.TAG_STABLE), "1.0.0-stable", 40))
        assertEquals(0, Releases.relationTo(ReleaseInfo(versionName = "1.0.0-stable", versionCode = 40, channel = Channels.TAG_STABLE), "1.0.0-stable", 40))
        assertEquals(-1, Releases.relationTo(ReleaseInfo(versionName = "9.9.9", versionCode = 39, channel = Channels.TAG_STABLE), "1.0.0-stable", 40))
    }

    @Test
    fun `relation_无versionCode走版本名比较`() {
        assertEquals(1, Releases.relationTo(ReleaseInfo(versionName = "1.0.1"), "1.0.0-stable", 40))
        assertEquals(0, Releases.relationTo(ReleaseInfo(versionName = "1.0.0-stable", channel = Channels.TAG_STABLE), "1.0.0-stable", 40))
        assertEquals(-1, Releases.relationTo(ReleaseInfo(versionName = "0.9.0"), "1.0.0-stable", 40))
    }

    @Test
    fun `relation_版本号相同但类型不同不算当前版本`() {
        // 同号异类: versionCode 撞号 / 版本名相同但类型不同 → 一律判更低, 不误标当前版本
        assertEquals(-1, Releases.relationTo(ReleaseInfo(versionName = "1.0.0-stable", versionCode = 40, channel = Channels.TAG_BETA), "1.0.0-stable", 40))
        assertEquals(-1, Releases.relationTo(ReleaseInfo(versionName = "1.0.0", channel = Channels.TAG_BETA), "1.0.0-stable", 40))
        assertEquals(-1, Releases.relationTo(ReleaseInfo(versionName = "1.0.0-beta.1", versionCode = 40, channel = Channels.TAG_BETA), "1.0.0-stable", 40))
    }

    @Test
    fun `relation_开发者版同主版本不催更不被标当前`() {
        // dev 构建: 同主版本的 stable 条目 → 更低 (不算更新也不算当前); 更高主版本 → 可更新
        assertEquals(-1, Releases.relationTo(ReleaseInfo(versionName = "1.0.0", versionCode = 40, channel = Channels.TAG_STABLE), "1.0.0-dev", 40))
        assertEquals(1, Releases.relationTo(ReleaseInfo(versionName = "1.1.0-stable", versionCode = 100019000, channel = Channels.TAG_STABLE), "1.0.0-dev", 40))
    }

    // ---- 通道识别与可见性 ----

    @Test
    fun `通道识别_按版本名后缀`() {
        assertEquals(Channels.TAG_STABLE, Channels.ofVersion("1.3.0"))
        assertEquals(Channels.TAG_STABLE, Channels.ofVersion("v1.3.0"))
        assertEquals(Channels.TAG_BETA, Channels.ofVersion("1.3.0-beta.1"))
        assertEquals(Channels.TAG_PREVIEW, Channels.ofVersion("1.3.0-pre.20261013"))
    }

    @Test
    fun `可见性_稳定只看稳定_抢先看稳定加抢先_内测条目不走公开渠道`() {
        // 稳定通道: 只看稳定
        assertEquals(true, Channels.visible(Channels.TAG_STABLE, Channels.STABLE))
        assertEquals(false, Channels.visible(Channels.TAG_PREVIEW, Channels.STABLE))
        assertEquals(false, Channels.visible(Channels.TAG_BETA, Channels.STABLE))
        // 抢先通道: 稳定 + 抢先; 内测条目即使出现在公开数据里也不展示 (仅私发清单可见)
        assertEquals(true, Channels.visible(Channels.TAG_STABLE, Channels.PREVIEW))
        assertEquals(true, Channels.visible(Channels.TAG_PREVIEW, Channels.PREVIEW))
        assertEquals(false, Channels.visible(Channels.TAG_BETA, Channels.PREVIEW))
    }

    // ---- GitHub Releases 解析 ----

    @Test
    fun `GitHubRelease解析_tag资产日期与下载量`() {
        val r = Releases.parseGitHubRelease(
            JSONObject(
                """{"tag_name":"v1.0.1","name":"长工课表通 v1.0.1","draft":false,
                    "published_at":"2026-10-12T08:00:00Z",
                    "body":"## 新功能\n- 支持软件更新页\n## 修复\n- 若干",
                    "assets":[{"name":"ccsut-kb-1.0.1.apk","size":123456,"download_count":321,
                               "browser_download_url":"https://github.com/x/y/releases/download/v1.0.1/ccsut-kb-1.0.1.apk"}]}"""
            ),
        )!!
        assertEquals("1.0.1", r.versionName)
        assertEquals("2026-10-12", r.date)
        assertEquals(123456L, r.bytes)
        assertEquals(321L, r.downloadCount)
        assertTrue(r.fileUrl!!.startsWith("https://github.com"))
        assertEquals(2, r.sections.size)
        assertEquals("新功能", r.sections[0].title)
        assertEquals(listOf("支持软件更新页"), r.sections[0].items)
    }

    @Test
    fun `GitHubRelease_draft跳过_无apk资产时fileUrl为空`() {
        assertNull(Releases.parseGitHubRelease(JSONObject("""{"tag_name":"v1","draft":true}""")))
        val r = Releases.parseGitHubRelease(JSONObject("""{"tag_name":"v1.2.3","draft":false,"assets":[]}"""))
        assertEquals("1.2.3", r!!.versionName)
        assertNull(r.fileUrl)
        assertNull(r.downloadCount)
    }

    // ---- versions.json 解析 ----

    @Test
    fun `versionsJson解析_条目与sections与下载量`() {
        val list = Releases.parseVersionsJson(
            JSONObject(
                """{"generatedAt":"2026-10-12T00:00:00","mirrors":["https://a.example","bad","https://b.example"],"versions":[
                    {"versionCode":41,"versionName":"1.0.1","date":"2026-10-12","file":"ccsut-kb-1.0.1.apk",
                     "sha256":"ab","bytes":9,"downloads":77,"channel":"preview","sections":[{"title":"新功能","items":["A","B"]}]},
                    {"versionCode":40,"versionName":"1.0.0","date":"2026-10-04","file":"ccsut-kb-1.0.0.apk",
                     "sha256":"cd","bytes":8,"notes":["旧版扁平条目"]}]}"""
            ),
            "https://example.com/dir",
        )
        assertEquals(2, list.size)
        assertEquals("1.0.1", list[0].versionName)
        assertEquals(41, list[0].versionCode)
        assertEquals(77L, list[0].downloadCount)
        assertEquals(Channels.TAG_PREVIEW, list[0].channel)
        assertEquals("https://example.com/dir/ccsut-kb-1.0.1.apk", list[0].fileUrl)
        assertEquals(listOf("A", "B"), list[0].sections[0].items)
        // 旧格式 notes 兜底转成无标题小节; 无 channel 字段按版本名后缀推导 (1.0.0 → 稳定)
        assertEquals(null, list[1].sections[0].title)
        assertEquals(listOf("旧版扁平条目"), list[1].sections[0].items)
        assertEquals(Channels.TAG_STABLE, list[1].channel)
        assertNull(list[1].downloadCount)
    }

    @Test
    fun `versionsJson_空对象返回空列表`() {
        assertTrue(Releases.parseVersionsJson(JSONObject("{}"), "https://host").isEmpty())
    }

    @Test
    fun `mirrorsOf_只保留https完整地址`() {
        assertEquals(
            listOf("https://a.example", "https://b.example"),
            Releases.mirrorsOf(JSONObject("""{"mirrors":["https://a.example","bad","  https://b.example "]}""")),
        )
        assertEquals(emptyList<String>(), Releases.mirrorsOf(JSONObject("{}")))
    }

    // ---- 镜像前缀与资产直链 ----

    @Test
    fun `mirrorize_加前缀_空镜像与已带前缀时原样返回`() {
        assertEquals("https://m.example/https://github.com/a/b", Mirrors.mirrorize("https://m.example", "https://github.com/a/b"))
        assertEquals("https://github.com/a/b", Mirrors.mirrorize(null, "https://github.com/a/b"))
        assertEquals("https://github.com/a/b", Mirrors.mirrorize("", "https://github.com/a/b"))
        // 已经是镜像前缀地址 (如缓存数据) 不二次包裹
        assertEquals("https://m.example/https://github.com/a/b", Mirrors.mirrorize("https://m.example", "https://m.example/https://github.com/a/b"))
    }

    @Test
    fun `GitHub资产直链_按版本号拼接_文件名缺省补默认`() {
        assertEquals(
            "https://github.com/yi-san-spce/ccsut-kb/releases/download/v1.0.1/ccsut-kb-1.0.1.apk",
            Releases.ghAssetUrl("1.0.1", "ccsut-kb-1.0.1.apk"),
        )
        assertEquals(
            "https://github.com/yi-san-spce/ccsut-kb/releases/download/v2.0.0/ccsut-kb-2.0.0.apk",
            Releases.ghAssetUrl("2.0.0", null),
        )
    }

    // ---- Release 正文 (Markdown) 分段 ----

    @Test
    fun `markdown分段_小节标题与列表条目`() {
        val body = """
            # 长工课表通 v1.0.1 · 修复版

            - 开场散条

            ## 新功能

            - 支持**软件更新**页
            - 支持多镜像加速

            ## 问题修复

            - 修复 [闪退](https://example.com/a) 问题
        """.trimIndent()
        val sections = Releases.parseMarkdownSections(body)
        assertEquals(3, sections.size)
        assertEquals(null, sections[0].title)
        assertEquals(listOf("开场散条"), sections[0].items)
        assertEquals("新功能", sections[1].title)
        assertEquals(listOf("支持软件更新页", "支持多镜像加速"), sections[1].items)
        assertEquals("问题修复", sections[2].title)
        assertEquals(listOf("修复 闪退 问题"), sections[2].items)
    }

    @Test
    fun `markdown分段_空正文与小节空条目`() {
        assertTrue(Releases.parseMarkdownSections("").isEmpty())
        // 只有小节标题没有条目 → 该小节不产出
        assertTrue(Releases.parseMarkdownSections("## 只有标题\n").isEmpty())
    }

    @Test
    fun `markdown分段_引用块前缀剥掉`() {
        val sections = Releases.parseMarkdownSections("> 摘要行\n\n> - 引用列表条目\n")
        assertEquals(1, sections.size)
        assertEquals(listOf("摘要行", "引用列表条目"), sections[0].items)
    }
}
