package com.azhan.qingyue.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本号比较。
 *
 * 这段逻辑判错，要么「有新版本」永远不出现（用户以为停止维护），
 * 要么天天提示更新（点进去发现还是同一版）。所以边界都锁住：
 * 位数不等、tag 前缀、字符串比较陷阱（1.3.100 vs 1.3.99）、预发布后缀。
 */
class VersionCompareTest {

    @Test
    fun newerPatchIsDetected() {
        assertTrue(isNewer("1.3.19", "1.3.18"))
        assertTrue(isNewer("1.4.0", "1.3.99"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(isNewer("1.3.18", "1.3.18"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(isNewer("1.3.17", "1.3.18"))
        assertFalse(isNewer("1.2.99", "1.3.0"))
    }

    @Test
    fun tagPrefixIsIgnored() {
        assertTrue(isNewer("v1.3.19", "1.3.18"))
        assertTrue(isNewer("V1.3.19", "1.3.18"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertTrue(isNewer("1.4", "1.3.18"))
        assertFalse(isNewer("1.3", "1.3.0"))
        assertFalse(isNewer("1.3.0", "1.3"))
    }

    @Test
    fun comparesNumericallyNotLexically() {
        assertTrue(isNewer("1.3.100", "1.3.99"))
        assertFalse(isNewer("1.3.9", "1.3.100"))
    }

    @Test
    fun preReleaseSuffixDoesNotBeatSameRelease() {
        assertFalse(isNewer("1.3.18-beta", "1.3.18"))
        assertFalse(isNewer("1.3.18+3", "1.3.18"))
    }
}
