package com.azhan.qingyue.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipSelectionTest {
    @Test
    fun extractsExactlyHighlightedCharactersFromCurrentSlice() {
        // 同一个起始 offset，翻页重新排版后切片内容发生变化。
        // 必须使用当前文字，而不是旧切片或按偏移重新从段落取文本。
        val oldSlice = "吟一般的巨响，扶摇上天"
        val currentSlice = "苏茹的嘴唇失去了血色，看着半空中那几乎已成了一个血人的小徒弟"
        val startOffset = 120
        val selection = selectedClip(currentSlice, startOffset, 4 until 15)

        assertEquals(currentSlice.substring(4, 15), selection?.text)
        assertEquals(startOffset + 4, selection?.offset)
        assertEquals(false, selection?.text == oldSlice.substring(0, 11))
    }

    @Test
    fun differentPageSliceKeepsItsOwnSourceTextAndAbsoluteOffset() {
        val paragraph = "第一部分阅读内容，第二部分才是选中文字"
        val secondSlice = paragraph.substring(paragraph.indexOf("第二部分"))
        val sliceStart = 500 + paragraph.indexOf("第二部分")
        val result = selectedClip(secondSlice, sliceStart, 0 until 4)

        assertEquals("第二部分", result?.text)
        assertEquals(sliceStart, result?.offset)
    }

    @Test
    fun highlightAndSavedTextAlwaysUseTheSameSnapshot() {
        val oldSlice = "吟一般的巨响，扶摇上天，陆雪琪竟似与天琊人剑合一"
        val currentSlice = "苏茹的嘴唇失去了血色，看着半空中那几乎已成了一个血人的小徒弟"
        val selection = selectedClip(currentSlice, 120, 4 until 20)!!

        assertEquals(4 until 20, selectedRange(currentSlice, 120, selection))
        // 旧切片和当前选中文字不同，不能高亮旧文却保存新文。
        assertNull(selectedRange(oldSlice, 120, selection))
        assertNull(selectedRange(currentSlice, 121, selection))
        assertNull(selectedRange(currentSlice, 120, null))
    }

    @Test
    fun preservesWhitespaceAndRejectsInvalidRanges() {
        assertEquals(" 血色 ", selectedClip("啊 血色 啊", 0, 1 until 5)?.text)
        assertNull(selectedClip("文字", 0, 0 until 0))
        assertNull(selectedClip("文字", 0, 0 until 3))
        assertNull(selectedClip("文字", 0, -1..0))
        assertNull(selectedClip("  ", 0, 0 until 2))
    }
}
