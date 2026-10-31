package com.rayluxeo.aida.data.ota

import android.content.Context
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.ota.AppUpdate
import com.rayluxeo.aida.domain.ota.UpdateRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class GithubUpdateRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : UpdateRepository {

    override suspend fun checkForUpdate(
        currentVersionCode: Long,
        currentVersionName: String,
        repositoryOwner: String,
        repositoryName: String,
    ): AppResult<AppUpdate> = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$repositoryOwner/$repositoryName/releases/latest"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Aida-Assistant-App")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext failure(
                    MiraErrorCode.NOT_FOUND,
                    "GitHub-এ কোনো রিলিজ পাওয়া যায়নি ($repositoryOwner/$repositoryName)",
                )
            }
            if (responseCode !in 200..299) {
                return@withContext failure(
                    MiraErrorCode.ENGINE_UNAVAILABLE,
                    "GitHub API সাড়া দেয়নি (কোড: $responseCode)",
                )
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val releaseJson = JSONObject(responseBody)

            val rawTag = releaseJson.optString("tag_name", "").trim()
            val releaseTitle = releaseJson.optString("name", rawTag).trim()
            val releaseNotes = releaseJson.optString("body", "").trim()
            val cleanVersionName = rawTag.removePrefix("v").removePrefix("V").trim()

            // Find APK asset
            val assets: JSONArray? = releaseJson.optJSONArray("assets")
            var apkUrl = ""
            var apkName = "Aida-$cleanVersionName.apk"
            var apkSize = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            // Fallback to github releases download URL convention if assets array was empty or direct tag
            if (apkUrl.isBlank() && rawTag.isNotBlank()) {
                apkUrl = "https://github.com/$repositoryOwner/$repositoryName/releases/download/$rawTag/Aida-$cleanVersionName.apk"
            }

            val remoteVersionCode = parseVersionToCode(cleanVersionName)
            val isNewer = compareVersions(cleanVersionName, currentVersionName) > 0 || remoteVersionCode > currentVersionCode

            AppUpdate(
                currentVersionName = currentVersionName,
                currentVersionCode = currentVersionCode,
                latestVersionName = cleanVersionName.ifBlank { rawTag },
                latestVersionCode = remoteVersionCode,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                apkSizeBytes = apkSize,
                hasUpdate = isNewer,
            ).asSuccess()
        } catch (e: Exception) {
            failure(
                MiraErrorCode.ENGINE_UNAVAILABLE,
                "আপডেট চেক করতে ব্যর্থ হয়েছে: ${e.message ?: "ইন্টারনেট সংযোগ চেক করুন"}",
                e,
            )
        } finally {
            connection?.disconnect()
        }
    }

    override suspend fun downloadUpdate(
        downloadUrl: String,
        targetFileName: String,
        onProgress: (Float) -> Unit,
    ): AppResult<File> = withContext(Dispatchers.IO) {
        if (downloadUrl.isBlank()) {
            return@withContext failure(MiraErrorCode.VALIDATION, "ডাউনলোড লিংক পাওয়া যায়নি")
        }

        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, targetFileName.ifBlank { "Aida-update.apk" })
            if (targetFile.exists()) {
                targetFile.delete()
            }

            var currentUrl = downloadUrl
            var connection: HttpURLConnection
            var redirectCount = 0

            // Follow HTTP 301/302 redirects (GitHub release asset downloads redirect to AWS S3)
            while (true) {
                connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Aida-Assistant-App")
                }
                connection.connect()

                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (location.isNullOrBlank() || redirectCount++ > 6) {
                        return@withContext failure(MiraErrorCode.STORAGE, "রিডাইরেক্ট সীমা অতিক্রম করেছে")
                    }
                    currentUrl = location
                } else if (code in 200..299) {
                    break
                } else {
                    connection.disconnect()
                    return@withContext failure(MiraErrorCode.STORAGE, "ডাউনলোড সার্ভার কোড: $code")
                }
            }

            val totalBytes = connection.contentLengthLong.coerceAtLeast(1L)
            var bytesRead = 0L

            connection.inputStream.use { input: InputStream ->
                FileOutputStream(targetFile).use { output: FileOutputStream ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var lastReportTime = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 200) {
                            lastReportTime = now
                            val progress = (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            onProgress(1f)
            targetFile.asSuccess()
        } catch (e: Exception) {
            failure(
                MiraErrorCode.STORAGE,
                "আপডেট ফাইল ডাউনলোড করতে সমস্যা হয়েছে: ${e.message}",
                e,
            )
        }
    }

    private fun parseVersionToCode(versionName: String): Long {
        val parts = versionName.split(".").mapNotNull { it.filter { char -> char.isDigit() }.toLongOrNull() }
        if (parts.isEmpty()) return 0L
        var code = 0L
        for (part in parts) {
            code = code * 100 + part
        }
        return code
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val p2 = v2.split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val length = maxOf(p1.size, p2.size)
        for (i in 0 until length) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }
}
