package com.nuvio.tv.updater

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Not enough free space on the device for the update: the APK is not downloaded. */
class InsufficientStorageException(val neededBytes: Long, val availableBytes: Long) :
    Exception("Not enough free space: needs $neededBytes bytes, has $availableBytes")

@Singleton
class ApkDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    suspend fun download(
        url: String,
        destinationFile: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit
    ): Result<File> {
        return runCatching {
            destinationFile.parentFile?.mkdirs()
            if (destinationFile.exists()) destinationFile.delete()

            val request = Request.Builder()
                .url(url)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed: HTTP ${response.code}")
                }

                val body = response.body ?: error("Empty download body")
                val total = body.contentLength().takeIf { it > 0 }
                // Install needs the file plus room to work: a TV box with a nearly full disk would otherwise fail
                // half way through the download, or at install, with no explanation.
                val free = destinationFile.parentFile?.usableSpace
                if (total != null && free != null && free < total + SPACE_MARGIN_BYTES) {
                    throw InsufficientStorageException(neededBytes = total + SPACE_MARGIN_BYTES, availableBytes = free)
                }

                body.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                        output.flush()
                    }
                }
            }

            destinationFile
        }.onFailure {
            // A half-downloaded APK is of no use and would stay in the cache.
            runCatching { destinationFile.delete() }
        }
    }

    private companion object {
        const val SPACE_MARGIN_BYTES = 50L * 1024 * 1024
    }
}
