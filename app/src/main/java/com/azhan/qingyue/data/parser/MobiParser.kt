package com.azhan.qingyue.data.parser

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * MOBI 解析。
 *
 * MOBI 本质是 Palm Database（PDB）容器：
 *   78 字节文件头 → record 索引表（每条 8 字节）→ 各 record 数据。
 *   record 0 是 PalmDOC + MOBI 头（含压缩方式、文本长度、编码），
 *   record 1..N 是正文，图片/索引排在后面。
 *
 * 正文有两种压缩：
 *   - 2      PalmDOC LZ77（老书、Gutenberg 下载的常见）
 *   - 17480  HUFF/CDIC（Amazon 的优化压缩，商业书常见）
 *
 * 两个必须注意的点（都是拿真实文件试出来的）：
 * 1. **输出缓冲要跨 record 累积** —— LZ77 的 dist 会引用前一个 record 的字节，
 *    每个 record 单独起缓冲会在第一个跨块引用处越界。
 * 2. **每个 record 最多产出 recordSize（通常 4096）字节就该停** ——
 *    压缩数据尾部还有 padding，继续解压会把 padding 当指令，
 *    吐出 `\x00\x83\x80\x80\x85` 这种杂散字节，总量也会比 textLength 多出两千多字节。
 */
object MobiParser {

    private const val COMPRESSION_NONE = 1
    private const val COMPRESSION_PALMDOC = 2
    private const val COMPRESSION_HUFF = 17480

    private const val ENCODING_CP1252 = 1252
    private const val ENCODING_UTF8 = 65001

    fun parse(bytes: ByteArray, fallbackTitle: String): BookContent {
        val records = readPdbRecords(bytes)
        if (records.isEmpty()) error("MOBI 文件为空或已损坏")

        val header = records[0]
        val compression = readUShort(header, 0)
        val textLength = readInt(header, 4)
        val recordCount = readUShort(header, 8)
        val recordSize = readUShort(header, 10)
        val encryption = readUShort(header, 12)

        if (encryption != 0) {
            error("这本 MOBI 带 DRM 保护，无法解析")
        }

        val text = when (compression) {
            COMPRESSION_NONE -> readPlainText(records, recordCount, textLength)
            COMPRESSION_PALMDOC -> readPalmDocText(records, recordCount, recordSize, textLength)
            COMPRESSION_HUFF -> readHuffText(records, recordCount, textLength)
            else -> error("不支持的 MOBI 压缩方式：$compression")
        }

        val charset = charsetOf(readInt(header, 28))
        val html = String(text, charset)
        val exth = parseExth(header)

        val plain = HtmlText.toPlainText(html).trim()
        val chapters = TxtParser.splitChapters(plain)

        return BookContent(
            title = exth.title?.takeIf { it.isNotBlank() } ?: fallbackTitle,
            author = exth.author?.takeIf { it.isNotBlank() },
            chapters = chapters,
            coverBytes = null, // 封面要读 firstImageIndex，本轮未实现
        )
    }

    // ------------------------------------------------------------- PDB

    private fun readPdbRecords(bytes: ByteArray): List<ByteArray> {
        if (bytes.size < 78) return emptyList()
        val count = readUShort(bytes, 76)
        if (count <= 0) return emptyList()

        val offsets = IntArray(count + 1)
        for (i in 0 until count) {
            offsets[i] = readInt(bytes, 78 + i * 8)
        }
        offsets[count] = bytes.size

        val records = ArrayList<ByteArray>(count)
        for (i in 0 until count) {
            val from = offsets[i].coerceIn(0, bytes.size)
            val to = offsets[i + 1].coerceIn(from, bytes.size)
            records += bytes.copyOfRange(from, to)
        }
        return records
    }

    // ------------------------------------------------------- 解压

    private fun readPlainText(records: List<ByteArray>, count: Int, textLength: Int): ByteArray {
        val out = ByteArrayOutputStream(textLength.coerceAtLeast(0))
        for (i in 1..count) {
            val r = records.getOrNull(i) ?: break
            out.write(r)
            if (out.size() >= textLength) break
        }
        return out.toByteArray()
    }

    internal fun readPalmDocText(
        records: List<ByteArray>,
        count: Int,
        recordSize: Int,
        textLength: Int,
    ): ByteArray {
        val target = textLength.coerceAtLeast(0)

        // 输出缓冲**贯穿所有 record**，绝不能在 record 边界清空 ——
        // LZ77 的 dist 会引用前面 record 的字节，清空后回抄会越界。
        //
        // 必须是原生 ByteArray 而不是 ArrayList<Byte>：一本书解压出来是
        // 千万级字节，装箱版本等于分配千万个小对象，25MB 的书能把 GC 拖到
        // 像是永久卡死（这是 1.3.11 及以前大 MOBI「一直解析中」的真正原因）。
        val out = ByteOut(minOf(target, 16 * 1024 * 1024).coerceAtLeast(64))

        for (i in 1..count) {
            val src = records.getOrNull(i) ?: break
            if (out.size >= target) break

            // 本 record 的产出配额：常规 recordSize，最后一块取剩余
            val quota = minOf(recordSize, target - out.size)
            var produced = 0
            var p = 0

            while (p < src.size && produced < quota) {
                val c = src[p].toInt() and 0xFF
                p++

                when {
                    c == 0x00 -> {
                        out.add(0)
                        produced++
                    }

                    c <= 0x08 -> {
                        // 接下来 c 个字节原样输出
                        val take = minOf(c, quota - produced, src.size - p)
                        out.append(src, p, take)
                        p += take
                        produced += take
                    }

                    c <= 0x7F -> {
                        out.add(c)
                        produced++
                    }

                    c <= 0xBF -> {
                        // 两字节：距离 + 长度，从已输出内容里回抄
                        if (p >= src.size) break
                        val c2 = src[p].toInt() and 0xFF
                        p++
                        val dist = (((c shl 8) or c2) shr 3) and 0x07FF
                        if (dist <= 0 || dist > out.size) break
                        val len = minOf((c2 and 0x07) + 3, quota - produced)
                        out.copyFromSelf(out.size - dist, len)
                        produced += len
                    }

                    else -> {
                        // 0xC0-0xFF：空格 + (c ^ 0x80)
                        out.add(0x20)
                        produced++
                        if (produced < quota) {
                            out.add(c xor 0x80)
                            produced++
                        }
                    }
                }
            }
        }

        return out.toArray()
    }

    /**
     * 可增长的原生字节数组。PalmDOC 解压专用 —— 每字节一次 add，
     * 装箱版本会产生千万级小对象，这里必须零分配（扩容除外）。
     */
    private class ByteOut(initialCapacity: Int) {
        private var data = ByteArray(initialCapacity.coerceAtLeast(64))
        var size = 0
            private set

        fun add(value: Int) {
            ensure(1)
            data[size++] = value.toByte()
        }

        fun append(src: ByteArray, offset: Int, count: Int) {
            if (count <= 0) return
            ensure(count)
            System.arraycopy(src, offset, data, size, count)
            size += count
        }

        /** 从自身 [from] 处回抄 [count] 字节（LZ77 回引用）。arraycopy 自带重叠安全语义。 */
        fun copyFromSelf(from: Int, count: Int) {
            if (count <= 0) return
            ensure(count)
            System.arraycopy(data, from, data, size, count)
            size += count
        }

        fun toArray(): ByteArray = data.copyOf(size)

        private fun ensure(extra: Int) {
            if (size + extra <= data.size) return
            var cap = data.size
            while (cap < size + extra) cap = cap shl 1
            data = data.copyOf(cap)
        }
    }

    /**
     * HUFF/CDIC。
     *
     * record 1 是 HUFF 表（256 项码长 + 256 项码），record 2 是 CDIC 字典，
     * record 3 起才是数据位流。解码时逐位读 Huffman 码得到符号：
     * 小于 256 直接输出字节，否则低位是字典条目索引，需再读若干位从字典取短语。
     *
     * **这条路径没有真实样本验证过** —— 无 DRM 的 HUFF/CDIC 文件很难找，
     * Gutenberg 下载的都是 PalmDOC。所以这里的选择是：解码失败就抛明确错误，
     * 绝不把半成品当正文吐出去（乱码比报错更难排查）。
     */
    private fun readHuffText(records: List<ByteArray>, count: Int, textLength: Int): ByteArray {
        if (count < 4) error("HUFF/CDIC 数据不完整")

        val huff = records.getOrNull(1) ?: error("HUFF/CDIC 缺少 HUFF 表")
        if (huff.size < 24 || String(huff, 0, 4, Charsets.US_ASCII) != "HUFF") {
            error("HUFF 表头损坏")
        }
        val table1 = readInt(huff, 8)
        val table2 = readInt(huff, 12)

        // 符号 i 的码长与码
        val codeLen = IntArray(256)
        val codeVal = IntArray(256)
        for (i in 0 until 256) {
            codeLen[i] = readInt(huff, table1 + i * 4)
            codeVal[i] = readInt(huff, table2 + i * 4)
        }

        // 按码长建索引：mincode[len] / maxcode[len]，以及 (len, code) -> symbol
        val maxLen = 32
        val minCode = IntArray(maxLen + 1)
        val maxCode = IntArray(maxLen + 1) { -1 }
        val symbolTable = HashMap<Long, Int>()
        for (len in 1..maxLen) {
            var lo = Int.MAX_VALUE
            var hi = -1
            for (sym in 0 until 256) {
                if (codeLen[sym] != len) continue
                val code = codeVal[sym]
                if (code < lo) lo = code
                if (code > hi) hi = code
                symbolTable[(len.toLong() shl 32) or (code.toLong() and 0xFFFFFFFFL)] = sym
            }
            if (hi >= 0) {
                minCode[len] = lo
                maxCode[len] = hi
            }
        }

        val cdic = records.getOrNull(2) ?: error("HUFF/CDIC 缺少 CDIC 字典")
        if (cdic.size < 16 || String(cdic, 0, 4, Charsets.US_ASCII) != "CDIC") {
            error("CDIC 字典头损坏")
        }
        val phraseCount = readInt(cdic, 8)
        val codeBits = readInt(cdic, 12).coerceIn(1, 16)
        val phraseOffsets = IntArray(phraseCount) { readInt(cdic, 16 + it * 4) }

        val out = ByteArrayOutputStream(textLength.coerceAtLeast(0))

        for (r in 3..count) {
            val data = records.getOrNull(r) ?: break
            if (out.size() >= textLength) break

            var bitPos = 0
            val totalBits = data.size * 8

            while (bitPos < totalBits && out.size() < textLength) {
                var code = 0
                var len = 0
                var sym = -1

                while (len < maxLen && bitPos < totalBits) {
                    code = (code shl 1) or ((data[bitPos shr 3].toInt() shr (7 - (bitPos and 7))) and 1)
                    bitPos++
                    len++
                    if (maxCode[len] >= 0 && code >= minCode[len] && code <= maxCode[len]) {
                        val hit = symbolTable[(len.toLong() shl 32) or (code.toLong() and 0xFFFFFFFFL)]
                        if (hit != null) {
                            sym = hit
                            break
                        }
                    }
                }
                if (sym < 0) break

                if (sym < 256) {
                    out.write(sym)
                } else {
                    // 字典条目：再读 codeBits 位拼出索引
                    var extra = 0
                    repeat(codeBits) {
                        if (bitPos >= totalBits) return@repeat
                        extra = (extra shl 1) or
                            ((data[bitPos shr 3].toInt() shr (7 - (bitPos and 7))) and 1)
                        bitPos++
                    }
                    val entry = (sym - 256) * (1 shl codeBits) + extra
                    if (entry in 0 until phraseCount) {
                        var q = phraseOffsets[entry]
                        while (q in 0 until cdic.size && cdic[q].toInt() != 0) {
                            out.write(cdic[q].toInt())
                            q++
                        }
                    }
                }
            }
        }

        if (out.size() == 0) {
            error("这本 MOBI 用的是 HUFF/CDIC 压缩，当前实现无法解析。建议用 Calibre 转成 EPUB 再阅读。")
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------- EXTH

    private class ExthMeta(val title: String?, val author: String?)

    private fun parseExth(header: ByteArray): ExthMeta {
        val headerLength = readInt(header, 20)
        val start = 16 + headerLength
        if (start + 12 > header.size) return ExthMeta(null, null)
        if (String(header, start, 4, Charsets.US_ASCII) != "EXTH") return ExthMeta(null, null)

        val count = readInt(header, start + 8)
        var p = start + 12
        var title: String? = null
        var author: String? = null

        repeat(count) {
            if (p + 8 > header.size) return@repeat
            val type = readInt(header, p)
            val len = readInt(header, p + 4)
            if (len < 8 || p + len > header.size) return@repeat
            val value = String(header, p + 8, len - 8, Charsets.UTF_8).trim()
            when (type) {
                100 -> if (author == null) author = value          // author
                503 -> if (title == null) title = value            // updated title
            }
            p += len
        }
        return ExthMeta(title, author)
    }

    // ------------------------------------------------------- 工具

    private fun charsetOf(code: Int): Charset = when (code) {
        ENCODING_UTF8 -> Charsets.UTF_8
        ENCODING_CP1252 -> runCatching { Charset.forName("windows-1252") }
            .getOrElse { Charsets.ISO_8859_1 }

        else -> runCatching { Charset.forName("GB18030") }.getOrElse { Charsets.UTF_8 }
    }

    private fun readUShort(b: ByteArray, at: Int): Int {
        if (at + 2 > b.size) return 0
        return ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
    }

    private fun readInt(b: ByteArray, at: Int): Int {
        if (at + 4 > b.size) return 0
        return ((b[at].toInt() and 0xFF) shl 24) or
            ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or
            (b[at + 3].toInt() and 0xFF)
    }
}
