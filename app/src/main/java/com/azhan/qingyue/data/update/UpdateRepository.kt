package com.azhan.qingyue.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 一个可用的更新。只有真的比当前版本新时才会构造出来。 */
data class UpdateInfo(
    val versionName: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

/** 下载进度。速度是 500ms 窗口的均值，[etaSeconds] 为负表示还算不出来。 */
data class DownloadProgress(
    val downloaded: Long,
    val total: Long,
    val bytesPerSecond: Long,
) {
    val fraction: Float
        get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f

    val etaSeconds: Long
        get() = if (bytesPerSecond > 0 && total > downloaded) {
            (total - downloaded) / bytesPerSecond
        } else {
            -1
        }
}

/**
 * 应用内更新。
 *
 * 只做「查最新 Release → 下载 APK → 交给系统安装器」这一条最短路径：
 * 不引入任何网络/JSON 库（`HttpURLConnection` + 平台自带的 `org.json`），
 * 保持这个 App 一贯的「零第三方」。
 *
 * 两个必须处理的现实问题：
 *
 * 1. **签名一致性**。GitHub 的包由本仓库的密钥签名，F-Droid 的包由 F-Droid 签名，
 *    两者不能互相覆盖安装。直接装只会得到系统一句「应用未安装」，用户根本不知道为什么。
 *    所以下载后先比对签名，不一致就给出明确说明。
 * 2. **安装未知来源**。Android 8 起需要 `REQUEST_INSTALL_PACKAGES` 且用户手动允许，
 *    未允许时跳到本应用的授权页，而不是让安装静默失败。
 */
class UpdateRepository(private val context: Context) {

    suspend fun checkForUpdate(currentVersion: String): Result<UpdateInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = JSONObject(httpGet(LATEST_RELEASE_URL))
                val tag = json.optString("tag_name").trim()
                if (tag.isEmpty()) error("服务器没有返回版本号")

                val version = tag.removePrefix("v").removePrefix("V")
                if (!isNewer(version, currentVersion)) return@runCatching null

                val assets = json.optJSONArray("assets")
                val apk = (0 until (assets?.length() ?: 0))
                    .mapNotNull { assets?.optJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                    // 只有源码包（CI 还没传完附件）时不算可更新，避免点了下载拿到 404
                    ?: return@runCatching null

                UpdateInfo(
                    versionName = version,
                    tag = tag,
                    notes = json.optString("body").trim(),
                    apkUrl = apk.optString("browser_download_url"),
                    apkSize = apk.optLong("size"),
                )
            }
        }

    /**
     * 下载到 cacheDir/updates/。每次先清空目录，避免把上一版的包当成本次结果。
     * [onProgress] 在 IO 线程回调，0..1。
     */
    /**
     * 下载到 cacheDir/updates/。每次先清空目录，避免把上一版的包当成本次结果。
     *
     * 取消：循环里每块都 `ensureActive()`，协程一取消就抛 CancellationException，
     * 半截文件在 catch 里删掉 —— 否则用户取消后重下会拿到一个「看起来存在」的坏包。
     */
    suspend fun download(info: UpdateInfo, onProgress: (DownloadProgress) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "update-${info.versionName}.apk")

            var connection: HttpURLConnection? = null
            try {
                connection = openFollowingRedirects(info.apkUrl)
                if (connection.responseCode !in 200..299) {
                    error("下载失败：HTTP ${connection.responseCode}")
                }
                val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.apkSize

                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L

                        // 速度用 500ms 滑动窗口算，避免每块都抖动
                        var windowStart = System.nanoTime()
                        var windowBytes = 0L
                        var speed = 0L

                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            windowBytes += read

                            val now = System.nanoTime()
                            val elapsed = now - windowStart
                            if (elapsed >= SPEED_WINDOW_NANOS) {
                                speed = windowBytes * 1_000_000_000L / elapsed
                                windowStart = now
                                windowBytes = 0L
                            }
                            onProgress(DownloadProgress(copied, total, speed))
                        }
                        if (total > 0 && copied != total) {
                            error("下载不完整（$copied / $total 字节）")
                        }
                    }
                }
            } catch (t: Throwable) {
                // 失败/取消都清掉半截文件
                runCatching { target.delete() }
                throw t
            } finally {
                connection?.disconnect()
            }
            target
        }

    /** 下载包的签名是否和已安装版本一致 —— 不一致就别让用户对着「应用未安装」发愣。 */
    fun isSameSignature(apk: File): Boolean {
        val pm = context.packageManager
        val remote = runCatching {
            signerHashes(pm.getPackageArchiveInfo(apk.absolutePath, signingFlag()) ?: return false)
        }.getOrNull() ?: return false
        val local = runCatching {
            signerHashes(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(signingFlag().toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(context.packageName, signingFlag())
                },
            )
        }.getOrNull() ?: return false
        return remote.isNotEmpty() && remote == local
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 跳到本应用的「安装未知应用」授权页。 */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ---------------------------------------------------------------- 内部

    private fun signingFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

    @Suppress("DEPRECATION")
    private fun signerHashes(info: PackageInfo): Set<Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
                ?.map { it.toByteArray().contentHashCode() }
                ?.toSet()
                .orEmpty()
        } else {
            info.signatures?.map { it.toByteArray().contentHashCode() }?.toSet().orEmpty()
        }

    private fun httpGet(url: String): String {
        val connection = openFollowingRedirects(url)
        try {
            if (connection.responseCode !in 200..299) {
                error(
                    when (connection.responseCode) {
                        403, 429 -> "请求过于频繁，请稍后再试"
                        404 -> "还没有发布过任何版本"
                        else -> "服务器返回 HTTP ${connection.responseCode}"
                    },
                )
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** 手动跟 3xx：GitHub 的下载会跳到 objects.githubusercontent.com，默认不跟跨域跳转。 */
    private fun openFollowingRedirects(url: String): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) error("服务器返回了空的重定向地址")
                current = location
            } else {
                return connection
            }
        }
        error("下载地址重定向次数过多")
    }

    private companion object {
        const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/xushujin/reading/releases/latest"
        const val USER_AGENT = "QingYue-Android"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 5

        /** 速度统计窗口：太短会抖，太长反应迟钝。 */
        const val SPEED_WINDOW_NANOS = 500_000_000L
    }
}

/**
 * 版本号比较：`1.3.19` > `1.3.18`，缺位按 0 补齐，非数字段忽略。
 * 单独提出来是为了能用单测锁住 —— 这里判错会让「有新版本」永远不出现。
 */
internal fun isNewer(remote: String, current: String): Boolean {
    // 预发布后缀（-beta）和构建元数据（+3）都不参与优先级比较，
    // 否则 1.3.18+3 会被算成比 1.3.18 新 —— 那只是同一版的重打包。
    fun parts(version: String): List<Int> =
        version.trim()
            .removePrefix("v").removePrefix("V")
            .substringBefore('+')
            .substringBefore('-')
            .split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    val r = parts(remote)
    val c = parts(current)
    for (i in 0 until maxOf(r.size, c.size)) {
        val a = r.getOrElse(i) { 0 }
        val b = c.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
