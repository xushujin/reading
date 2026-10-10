package com.azhan.qingyue.data.parser

import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * EPUB 解析。刻意零第三方依赖：
 *
 * 1. ZipInputStream 一次性把条目读进内存（一本普通 EPUB 几 MB，够用）；
 * 2. META-INF/container.xml → OPF 路径；
 * 3. OPF 里取 metadata（书名/作者）、manifest、spine 顺序、封面项；
 * 4. 按 spine 顺序把 XHTML 剥成纯文本，每个 spine 项作为一章。
 */
object EpubParser {

    private val HEADING = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]\\s*>")
    private val TITLE_TAG = Regex("(?is)<title[^>]*>(.*?)</title\\s*>")
    private val HTML_EXTENSIONS = setOf("html", "xhtml", "htm")

    fun parse(bytes: ByteArray, fallbackTitle: String): BookContent {
        val entries = readEntries(bytes)
        if (entries.isEmpty()) error("EPUB 内容为空或已损坏")

        val opfPath = findOpfPath(entries) ?: error("EPUB 缺少 META-INF/container.xml")
        val opfBytes = lookup(entries, opfPath) ?: error("EPUB 缺少 OPF 清单文件")

        val basePath = opfPath.substringBeforeLast('/', "")
        val opf = parseOpf(opfBytes)

        val spineIds = opf.spine.ifEmpty {
            opf.manifest.values.filter { isHtml(it) }.map { it.id }
        }

        val chapters = ArrayList<Chapter>(spineIds.size)
        for (idref in spineIds) {
            val item = opf.manifest[idref] ?: continue
            if (!isHtml(item)) continue
            if (item.properties.split(' ').any { it.equals("nav", ignoreCase = true) }) continue

            val raw = lookup(entries, resolvePath(basePath, item.href)) ?: continue
            val html = TextDecoding.decode(raw)
            val text = HtmlText.toPlainText(html).trim()
            if (text.length < 2) continue

            chapters += Chapter(title = extractHeading(html).orEmpty(), text = text)
        }

        val coverBytes = opf.coverItem?.let { item ->
            lookup(entries, resolvePath(basePath, item.href))
        }

        return BookContent(
            title = opf.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            author = opf.author?.takeIf { it.isNotBlank() },
            chapters = chapters.ifEmpty {
                listOf(Chapter("", "（这本 EPUB 里没有可解析的正文）"))
            },
            coverBytes = coverBytes,
        )
    }

    // ---------------------------------------------------------------- zip

    private fun readEntries(bytes: ByteArray): Map<String, ByteArray> {
        val map = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && !name.isNullOrBlank()) {
                    map[name] = zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        return map
    }

    private fun lookup(entries: Map<String, ByteArray>, path: String): ByteArray? =
        entries[path]
            ?: entries.entries.firstOrNull { it.key.equals(path, ignoreCase = true) }?.value

    // ----------------------------------------------------------- container

    private fun findOpfPath(entries: Map<String, ByteArray>): String? {
        val container = lookup(entries, "META-INF/container.xml") ?: return null
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(container), null)

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "rootfile") {
                val path = parser.getAttributeValue(null, "full-path")
                if (!path.isNullOrBlank()) return path
            }
            event = parser.next()
        }
        return null
    }

    // ----------------------------------------------------------------- opf

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
    )

    private class OpfData(
        val title: String?,
        val author: String?,
        val manifest: Map<String, ManifestItem>,
        val spine: List<String>,
        val coverItem: ManifestItem?,
    )

    private fun parseOpf(bytes: ByteArray): OpfData {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(ByteArrayInputStream(bytes), null)

        val manifest = LinkedHashMap<String, ManifestItem>()
        val spine = ArrayList<String>()
        var title: String? = null
        var author: String? = null
        var metaCoverId: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> if (title == null) title = safeNextText(parser)

                    "creator" -> if (author == null) author = safeNextText(parser)

                    "meta" -> {
                        val attrName = parser.getAttributeValue(null, "name")
                        if (attrName.equals("cover", ignoreCase = true)) {
                            metaCoverId = parser.getAttributeValue(null, "content")
                        }
                    }

                    "item" -> {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) {
                            manifest[id] = ManifestItem(
                                id = id,
                                href = href,
                                mediaType = parser.getAttributeValue(null, "media-type").orEmpty(),
                                properties = parser.getAttributeValue(null, "properties").orEmpty(),
                            )
                        }
                    }

                    "itemref" -> {
                        val linear = parser.getAttributeValue(null, "linear")
                        val idref = parser.getAttributeValue(null, "idref")
                        if (idref != null && !linear.equals("no", ignoreCase = true)) {
                            spine += idref
                        }
                    }
                }
            }
            event = parser.next()
        }

        // EPUB3 用 properties="cover-image"，EPUB2 用 <meta name="cover" content="id"/>。
        val coverItem = manifest.values.firstOrNull { item ->
            item.properties.split(' ').any { it.equals("cover-image", ignoreCase = true) }
        } ?: metaCoverId?.let { manifest[it] }

        return OpfData(
            title = title,
            author = author,
            manifest = manifest,
            spine = spine,
            coverItem = coverItem,
        )
    }

    private fun safeNextText(parser: XmlPullParser): String? =
        runCatching { parser.nextText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    // -------------------------------------------------------------- helpers

    private fun isHtml(item: ManifestItem): Boolean {
        if (item.mediaType.contains("html", ignoreCase = true)) return true
        return item.href.substringAfterLast('.', "").lowercase() in HTML_EXTENSIONS
    }

    private fun resolvePath(basePath: String, href: String): String {
        val raw = href.substringBefore('#')
        val decoded = runCatching { Uri.decode(raw) }.getOrDefault(raw)
        val joined = if (basePath.isEmpty()) decoded else "$basePath/$decoded"
        return normalize(joined)
    }

    private fun normalize(path: String): String {
        val stack = ArrayList<String>()
        for (part in path.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack += part
            }
        }
        return stack.joinToString("/")
    }

    private fun extractHeading(html: String): String? {
        HEADING.find(html)?.groupValues?.getOrNull(1)?.let { inner ->
            val text = HtmlText.toPlainText(inner).trim()
            if (text.isNotEmpty() && text.length <= 60) return text
        }
        TITLE_TAG.find(html)?.groupValues?.getOrNull(1)?.let { inner ->
            val text = HtmlText.toPlainText(inner).trim()
            if (text.isNotEmpty() && text.length <= 60) return text
        }
        return null
    }
}
